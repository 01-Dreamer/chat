import { app } from 'electron'
import { createHash } from 'node:crypto'
import { mkdir, stat, writeFile } from 'node:fs/promises'
import { extname, join } from 'node:path'
import { pathToFileURL } from 'node:url'
import type { Friend, GroupChat, GroupMember, User } from '../types'
import { databaseManager } from '../database/databaseManager'

class AvatarCacheService {
  cacheFriends(items: Friend[]) {
    for (const item of items) if (item.avatar) void this.cache('user', item.id, item.avatar)
  }

  cacheGroups(items: GroupChat[]) {
    for (const item of items) if (item.avatar) void this.cache('group', item.id, item.avatar)
  }

  cacheMembers(items: GroupMember[]) {
    for (const item of items) if (item.avatar) void this.cache('user', item.userId, item.avatar)
  }

  cacheCurrentUser(item: User) {
    if (item.avatar) void this.cache('user', item.id, item.avatar)
  }

  private async cache(kind: 'user' | 'group', id: string, remoteUrl: string) {
    if (!/^https?:\/\//.test(remoteUrl)) return
    try {
      const hash = createHash('sha256').update(remoteUrl).digest('hex')
      const extension = extname(new URL(remoteUrl).pathname).slice(0, 8) || '.img'
      const directory = join(app.getPath('userData'), 'avatar-cache')
      const path = join(directory, `${hash}${extension}`)
      await mkdir(directory, { recursive: true })
      try {
        await stat(path)
      } catch {
        const response = await fetch(remoteUrl)
        if (!response.ok) return
        await writeFile(path, Buffer.from(await response.arrayBuffer()))
      }
      const localUrl = pathToFileURL(path).toString()
      if (kind === 'user') databaseManager.setUserAvatarLocal(id, remoteUrl, localUrl)
      else databaseManager.setGroupAvatarLocal(id, remoteUrl, localUrl)
    } catch {
      // Avatar cache failures do not block rendering or profile synchronization.
    }
  }
}

export const avatarCacheService = new AvatarCacheService()
