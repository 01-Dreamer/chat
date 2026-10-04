import { app, desktopCapturer, dialog, screen } from 'electron'
import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { mkdir, open, stat, writeFile } from 'node:fs/promises'
import { basename, extname, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import type { FileResource } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient } from './apiClient'

interface UploadSession {
  instant: boolean
  uploadSessionId: string | null
  partSize: number
  uploadedParts: number[]
  resource: FileResource | null
}

const mimeTypes: Record<string, string> = {
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.webp': 'image/webp', '.mp3': 'audio/mpeg', '.wav': 'audio/wav', '.ogg': 'audio/ogg',
  '.mp4': 'video/mp4', '.webm': 'video/webm', '.pdf': 'application/pdf', '.txt': 'text/plain',
}

class FileService {
  async selectAndUpload(resourceType?: number) {
    const result = await dialog.showOpenDialog({ properties: ['openFile'] })
    if (result.canceled || !result.filePaths[0]) return null
    const path = result.filePaths[0]
    const mimeType = mimeTypes[extname(path).toLowerCase()] ?? 'application/octet-stream'
    const inferredType = mimeType.startsWith('image/') ? 0 : mimeType.startsWith('video/') ? 1 : mimeType.startsWith('audio/') ? 2 : 3
    return this.upload(path, resourceType ?? inferredType)
  }

  async upload(filePath: string, resourceType: number) {
    const info = await stat(filePath)
    const fileHash = await this.sha256(filePath)
    const mimeType = mimeTypes[extname(filePath).toLowerCase()] ?? 'application/octet-stream'
    const session = await apiClient.post<UploadSession>('/files/uploads', {
      fileName: basename(filePath), fileSize: info.size, mimeType, fileHash, resourceType,
    })
    let resource = session.resource
    if (!session.instant) {
      if (!session.uploadSessionId) throw new Error('服务端未返回上传任务')
      const status = await apiClient.get<UploadSession>(`/files/uploads/${session.uploadSessionId}`)
      const uploaded = new Set(status.uploadedParts)
      const handle = await open(filePath, 'r')
      try {
        const totalParts = Math.ceil(info.size / session.partSize)
        for (let partNumber = 1; partNumber <= totalParts; partNumber++) {
          if (uploaded.has(partNumber)) continue
          const offset = (partNumber - 1) * session.partSize
          const length = Math.min(session.partSize, info.size - offset)
          const buffer = Buffer.allocUnsafe(length)
          await handle.read(buffer, 0, length, offset)
          await this.uploadPartWithRetry(session.uploadSessionId, partNumber, buffer)
        }
      } catch (error) {
        throw error
      } finally {
        await handle.close()
      }
      resource = await apiClient.post<FileResource>(`/files/uploads/${session.uploadSessionId}/complete`)
    }
    if (!resource) throw new Error('文件上传未返回资源')
    resource.localPath = filePath
    databaseManager.upsertFileResource(resource, 2)
    return resource
  }

  async captureScreen() {
    const display = screen.getPrimaryDisplay()
    const width = Math.round(display.size.width * display.scaleFactor)
    const height = Math.round(display.size.height * display.scaleFactor)
    const sources = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: { width, height } })
    const source = sources[0]
    if (!source || source.thumbnail.isEmpty()) throw new Error('无法捕获当前屏幕')
    const directory = join(app.getPath('userData'), 'captures')
    await mkdir(directory, { recursive: true })
    const path = join(directory, `capture-${Date.now()}.png`)
    await writeFile(path, source.thumbnail.toPNG())
    return this.upload(path, 0)
  }

  async download(resource: FileResource) {
    if (resource.localPath) return resource.localPath
    const response = await apiClient.get<{ url: string }>(`/files/${resource.id}/download-url`)
    const data = await fetch(response.url).then((value) => {
      if (!value.ok) throw new Error('文件下载失败')
      return value.arrayBuffer()
    })
    const directory = join(app.getPath('userData'), 'file-cache', resource.fileHash.slice(0, 2))
    await mkdir(directory, { recursive: true })
    const path = join(directory, `${resource.fileHash}${extname(resource.fileName)}`)
    await writeFile(path, Buffer.from(data))
    resource.localPath = path
    databaseManager.upsertFileResource(resource, 2)
    return path
  }

  async get(resourceId: string, autoDownload = false) {
    const resource = await apiClient.get<FileResource>(`/files/${resourceId}`)
    databaseManager.upsertFileResource(resource, 0)
    if (autoDownload) await this.download(resource)
    return resource
  }

  async openResource(resourceId: string) {
    const cached = databaseManager.getFileResource(resourceId)
    const resource = cached ?? await this.get(resourceId)
    const path = await this.download(resource)
    return pathToFileURL(path).toString()
  }

  private async uploadPartWithRetry(sessionId: string, partNumber: number, buffer: Buffer) {
    let lastError: unknown
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const form = new FormData()
        form.append('file', new Blob([Uint8Array.from(buffer)]), `part-${partNumber}`)
        await apiClient.postForm(`/files/uploads/${sessionId}/parts/${partNumber}`, form)
        return
      } catch (error) {
        lastError = error
      }
    }
    throw lastError
  }

  private sha256(filePath: string) {
    return new Promise<string>((resolve, reject) => {
      const hash = createHash('sha256')
      const stream = createReadStream(filePath)
      stream.on('data', (chunk) => hash.update(chunk))
      stream.on('error', reject)
      stream.on('end', () => resolve(hash.digest('hex')))
    })
  }
}

export const fileService = new FileService()
