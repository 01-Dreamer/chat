import { app, BrowserWindow, desktopCapturer, dialog, nativeImage, screen } from 'electron'
import { createHash, randomUUID } from 'node:crypto'
import { execFile } from 'node:child_process'
import { createReadStream } from 'node:fs'
import { mkdir, open, readFile, rename, stat, unlink, writeFile } from 'node:fs/promises'
import { basename, extname, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { promisify } from 'node:util'
import type { FileResource, PendingAttachment } from '../types'
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
const execFileAsync = promisify(execFile)

class FileService {
  async selectAttachments() {
    const result = await dialog.showOpenDialog({ properties: ['openFile', 'multiSelections'] })
    if (result.canceled) return []
    return this.stagePaths(result.filePaths)
  }

  async stagePaths(paths: string[]) {
    const uniquePaths = [...new Set(paths)].slice(0, 20)
    const attachments: PendingAttachment[] = []
    for (const filePath of uniquePaths) {
      const info = await stat(filePath)
      if (!info.isFile()) continue
      const mimeType = this.mimeType(filePath)
      const resourceType = mimeType.startsWith('image/') ? 0 : mimeType.startsWith('video/') ? 1 : 3
      const preview = resourceType === 0 ? nativeImage.createFromPath(filePath) : null
      attachments.push({
        id: randomUUID(),
        filePath,
        fileName: basename(filePath),
        fileSize: info.size,
        mimeType,
        resourceType,
        previewUrl: preview && !preview.isEmpty()
          ? preview.resize({ width: 120, height: 120, quality: 'good' }).toDataURL()
          : null,
      })
    }
    return attachments
  }

  /** Kept for profile/group avatar selection, whose existing flow uploads immediately. */
  async selectAndUpload(resourceType?: number) {
    const attachment = (await this.selectAttachments())[0]
    if (!attachment) return null
    return this.upload(attachment.filePath, resourceType ?? attachment.resourceType)
  }

  async saveVoice(data: Uint8Array, mimeType: string) {
    if (!data.byteLength) throw new Error('录音内容为空')
    if (data.byteLength > 20 * 1024 * 1024) throw new Error('录音文件不能超过 20MB')
    const directory = join(app.getPath('userData'), 'voice-cache')
    await mkdir(directory, { recursive: true })
    const extension = mimeType.includes('ogg') ? '.ogg' : mimeType.includes('mp4') ? '.m4a' : '.webm'
    const path = join(directory, `voice-${Date.now()}-${randomUUID()}${extension}`)
    await writeFile(path, data)
    return path
  }

  async upload(filePath: string, resourceType: number, duration?: number, mimeTypeOverride?: string) {
    const info = await stat(filePath)
    const fileHash = await this.sha256(filePath)
    const mimeType = this.normalizeMimeType(mimeTypeOverride) ?? this.mimeType(filePath, resourceType)
    const session = await apiClient.post<UploadSession>('/files/uploads', {
      fileName: basename(filePath), fileSize: info.size, mimeType, fileHash, resourceType, duration,
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

  async captureScreen(): Promise<PendingAttachment | null> {
    const directory = join(app.getPath('userData'), 'captures')
    await mkdir(directory, { recursive: true })
    const path = join(directory, `capture-${Date.now()}.png`)
    if (process.platform === 'linux') {
      const command = await this.findCaptureCommand()
      if (command) {
        const window = BrowserWindow.getFocusedWindow()
        window?.hide()
        try {
          await new Promise((resolve) => setTimeout(resolve, 150))
          const args = command === 'gnome-screenshot'
            ? ['-a', '-f', path]
            : ['-r', '-b', '-n', '-o', path]
          await execFileAsync(command, args)
          return (await this.stagePaths([path]))[0] ?? null
        } catch {
          return null
        } finally {
          window?.show()
          window?.focus()
        }
      }
    }
    const window = BrowserWindow.getFocusedWindow()
    const display = window
      ? screen.getDisplayMatching(window.getBounds())
      : screen.getPrimaryDisplay()
    const width = Math.round(display.size.width * display.scaleFactor)
    const height = Math.round(display.size.height * display.scaleFactor)
    window?.hide()
    try {
      await new Promise((resolve) => setTimeout(resolve, 120))
      const sources = await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: { width, height } })
      const source = sources.find((item) => item.display_id === String(display.id)) ?? sources[0]
      if (!source || source.thumbnail.isEmpty()) throw new Error('无法捕获当前屏幕')
      const selection = await this.selectCaptureRegion(display.bounds, source.thumbnail.toDataURL())
      if (!selection) return null
      const scaleX = source.thumbnail.getSize().width / display.bounds.width
      const scaleY = source.thumbnail.getSize().height / display.bounds.height
      const crop = {
        x: Math.max(0, Math.round(selection.x * scaleX)),
        y: Math.max(0, Math.round(selection.y * scaleY)),
        width: Math.max(1, Math.round(selection.width * scaleX)),
        height: Math.max(1, Math.round(selection.height * scaleY)),
      }
      await writeFile(path, source.thumbnail.crop(crop).toPNG())
    } finally {
      window?.show()
      window?.focus()
    }
    return (await this.stagePaths([path]))[0] ?? null
  }

  async download(resource: FileResource) {
    if (resource.localPath && await this.fileExists(resource.localPath)) return resource.localPath
    const directory = join(app.getPath('userData'), 'file-cache', resource.fileHash.slice(0, 2))
    await mkdir(directory, { recursive: true })
    const path = join(directory, `${resource.fileHash}${extname(resource.fileName)}`)
    await this.downloadTo(resource, path)
    resource.localPath = path
    databaseManager.upsertFileResource(resource, 2)
    return path
  }

  async downloadDocument(resourceId: string, onProgress?: (received: number, total: number) => void) {
    const cached = databaseManager.getFileResource(resourceId)
    const resource = cached ?? await this.get(resourceId)
    if (resource.localPath && await this.fileExists(resource.localPath)) {
      onProgress?.(resource.fileSize, resource.fileSize)
      return resource.localPath
    }
    const directory = app.getPath('downloads')
    await mkdir(directory, { recursive: true })
    const path = await this.availableDownloadPath(directory, resource.fileName)
    await this.downloadTo(resource, path, onProgress)
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

  async loadVoice(resourceId: string) {
    const cached = databaseManager.getFileResource(resourceId)
    const resource = cached ?? await this.get(resourceId)
    if (resource.resourceType !== 2) throw new Error('该资源不是语音消息')
    const path = await this.download(resource)
    const data = await readFile(path)
    return {
      bytes: Uint8Array.from(data),
      mimeType: this.voiceMimeType(resource.mimeType, resource.fileName),
    }
  }

  async loadImage(resourceId: string) {
    const cached = databaseManager.getFileResource(resourceId)
    const resource = cached ?? await this.get(resourceId)
    if (resource.resourceType !== 0) throw new Error('该资源不是图片消息')
    const path = await this.download(resource)
    const data = await readFile(path)
    return {
      bytes: Uint8Array.from(data),
      mimeType: this.imageMimeType(resource.mimeType, resource.fileName),
    }
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

  private async downloadTo(
    resource: FileResource,
    destination: string,
    onProgress?: (received: number, total: number) => void,
  ) {
    const response = await apiClient.get<{ url: string }>(`/files/${resource.id}/download-url`)
    const download = await fetch(response.url)
    if (!download.ok) throw new Error('文件下载失败')
    const total = Number(download.headers.get('content-length')) || resource.fileSize
    const temporaryPath = `${destination}.${randomUUID()}.download`
    const handle = await open(temporaryPath, 'w')
    let received = 0
    try {
      if (!download.body) {
        const data = new Uint8Array(await download.arrayBuffer())
        await handle.write(data, 0, data.byteLength, 0)
        received = data.byteLength
        onProgress?.(received, total || received)
      } else {
        const reader = download.body.getReader()
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          let chunkOffset = 0
          while (chunkOffset < value.byteLength) {
            const { bytesWritten } = await handle.write(
              value,
              chunkOffset,
              value.byteLength - chunkOffset,
              received,
            )
            chunkOffset += bytesWritten
            received += bytesWritten
          }
          onProgress?.(received, total || resource.fileSize || received)
        }
      }
      await handle.sync()
      await handle.close()
      await rename(temporaryPath, destination)
      onProgress?.(received, total || received)
      return destination
    } catch (error) {
      await handle.close().catch(() => undefined)
      await unlink(temporaryPath).catch(() => undefined)
      throw error
    }
  }

  private async availableDownloadPath(directory: string, fileName: string) {
    const safeName = basename(fileName).trim() || '聊天文件'
    const extension = extname(safeName)
    const stem = safeName.slice(0, Math.max(0, safeName.length - extension.length)) || '聊天文件'
    for (let index = 0; index < 10_000; index++) {
      const candidate = join(directory, index === 0 ? safeName : `${stem} (${index})${extension}`)
      if (!await this.fileExists(candidate)) return candidate
    }
    throw new Error('无法生成可用的下载文件名')
  }

  private async fileExists(path: string) {
    try {
      return (await stat(path)).isFile()
    } catch {
      return false
    }
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

  private mimeType(filePath: string, resourceType?: number) {
    if (resourceType === 2 && extname(filePath).toLowerCase() === '.webm') return 'audio/webm;codecs=opus'
    return mimeTypes[extname(filePath).toLowerCase()] ?? 'application/octet-stream'
  }

  private normalizeMimeType(value?: string) {
    const normalized = value?.trim().toLowerCase()
    return normalized && /^[\w.+-]+\/[\w.+-]+(?:\s*;\s*[\w.+-]+=[\w.+-]+)*$/.test(normalized)
      ? normalized
      : null
  }

  private voiceMimeType(value: string, fileName: string) {
    const normalized = this.normalizeMimeType(value)
    if (normalized?.startsWith('audio/')) return normalized
    const extension = extname(fileName).toLowerCase()
    if (extension === '.ogg') return 'audio/ogg;codecs=opus'
    if (extension === '.m4a' || extension === '.mp4') return 'audio/mp4'
    if (extension === '.mp3') return 'audio/mpeg'
    if (extension === '.wav') return 'audio/wav'
    return 'audio/webm;codecs=opus'
  }

  private imageMimeType(value: string, fileName: string) {
    const normalized = this.normalizeMimeType(value)
    if (normalized?.startsWith('image/')) return normalized
    const extension = extname(fileName).toLowerCase()
    if (extension === '.jpg' || extension === '.jpeg') return 'image/jpeg'
    if (extension === '.gif') return 'image/gif'
    if (extension === '.webp') return 'image/webp'
    return 'image/png'
  }

  private async findCaptureCommand() {
    for (const command of ['gnome-screenshot', 'spectacle']) {
      try {
        await execFileAsync('which', [command])
        return command
      } catch {
        // Try the next desktop screenshot utility.
      }
    }
    return null
  }

  private async selectCaptureRegion(
    bounds: Electron.Rectangle,
    screenshotDataUrl: string,
  ): Promise<{ x: number, y: number, width: number, height: number } | null> {
    const overlay = new BrowserWindow({
      x: bounds.x,
      y: bounds.y,
      width: bounds.width,
      height: bounds.height,
      frame: false,
      resizable: false,
      movable: false,
      alwaysOnTop: true,
      skipTaskbar: true,
      show: false,
      webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
    })
    await overlay.loadURL('data:text/html;charset=utf-8,<html><body></body></html>')
    overlay.show()
    overlay.focus()
    try {
      return await overlay.webContents.executeJavaScript(`
        new Promise((resolve) => {
          const imageUrl = ${JSON.stringify(screenshotDataUrl)};
          document.documentElement.style.cssText = 'width:100%;height:100%;overflow:hidden';
          document.body.style.cssText = 'width:100%;height:100%;margin:0;overflow:hidden;cursor:crosshair;user-select:none;background:#111';
          const image = document.createElement('img');
          image.src = imageUrl;
          image.draggable = false;
          image.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;object-fit:fill';
          const shade = document.createElement('div');
          shade.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.28);pointer-events:none';
          const box = document.createElement('div');
          box.style.cssText = 'display:none;position:fixed;border:2px solid #07c160;box-shadow:0 0 0 9999px rgba(0,0,0,.28);pointer-events:none';
          const hint = document.createElement('div');
          hint.textContent = '拖动选择截图区域，Esc 取消';
          hint.style.cssText = 'position:fixed;top:18px;left:50%;transform:translateX(-50%);padding:7px 14px;border-radius:5px;color:white;background:rgba(0,0,0,.62);font:13px sans-serif;pointer-events:none';
          document.body.append(image, shade, box, hint);
          let startX = 0;
          let startY = 0;
          let dragging = false;
          const finish = (value) => {
            window.removeEventListener('keydown', onKey);
            resolve(value);
          };
          const onKey = (event) => { if (event.key === 'Escape') finish(null); };
          window.addEventListener('keydown', onKey);
          window.addEventListener('contextmenu', (event) => { event.preventDefault(); finish(null); }, { once: true });
          window.addEventListener('mousedown', (event) => {
            if (event.button !== 0) return;
            dragging = true;
            startX = event.clientX;
            startY = event.clientY;
            shade.style.display = 'none';
            box.style.display = 'block';
          });
          window.addEventListener('mousemove', (event) => {
            if (!dragging) return;
            const x = Math.min(startX, event.clientX);
            const y = Math.min(startY, event.clientY);
            const width = Math.abs(event.clientX - startX);
            const height = Math.abs(event.clientY - startY);
            box.style.left = x + 'px';
            box.style.top = y + 'px';
            box.style.width = width + 'px';
            box.style.height = height + 'px';
          });
          window.addEventListener('mouseup', (event) => {
            if (!dragging || event.button !== 0) return;
            dragging = false;
            const x = Math.min(startX, event.clientX);
            const y = Math.min(startY, event.clientY);
            const width = Math.abs(event.clientX - startX);
            const height = Math.abs(event.clientY - startY);
            if (width < 3 || height < 3) {
              box.style.display = 'none';
              shade.style.display = 'block';
              return;
            }
            finish({ x, y, width, height });
          });
        })
      `, true) as { x: number, y: number, width: number, height: number } | null
    } finally {
      if (!overlay.isDestroyed()) overlay.close()
    }
  }
}

export const fileService = new FileService()
