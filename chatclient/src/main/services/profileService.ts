import type { ProfilePatch } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient } from './apiClient'
import { authService, toClientUser, type ServerUserProfile } from './authService'

class ProfileService {
  async update(patch: ProfilePatch) {
    if (patch.avatar !== undefined) {
      throw new Error('头像上传将在文件资源模块接入 OSS 后开放')
    }
    if (patch.nickname === undefined) throw new Error('没有可更新的资料')
    const profile = await apiClient.patch<ServerUserProfile>('/users/me/profile', {
      nickname: patch.nickname,
    })
    const user = toClientUser(profile)
    authService.updateCurrentUser(user)
    databaseManager.upsertCurrentUser(user)
    return user
  }

  async updateAvatar(resourceId: string) {
    const profile = await apiClient.patch<ServerUserProfile>('/users/me/avatar', { resourceId })
    const user = toClientUser(profile)
    authService.updateCurrentUser(user)
    databaseManager.upsertCurrentUser(user)
    return user
  }
}

export const profileService = new ProfileService()
