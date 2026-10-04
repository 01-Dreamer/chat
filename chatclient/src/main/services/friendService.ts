import type { Friend, FriendRequest, VersionedSync } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient, isOfflineError } from './apiClient'
import { avatarCacheService } from './avatarCacheService'

interface ServerUserSummary {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  status: number
  createdTime: number
  updatedTime: number
}

interface ServerFriend {
  relationId: string
  userId: string
  username: string
  nickname: string
  avatarUrl: string | null
  remark: string | null
  status: number
  createdTime: number
  updatedTime: number
  userUpdatedTime: number
}

interface ServerFriendRequest {
  id: string
  senderId: string
  receiverId: string
  direction: 'incoming' | 'outgoing'
  user: ServerUserSummary
  message: string | null
  status: number
  createdTime: number
  updatedTime: number
}

function toFriend(item: ServerFriend | ServerUserSummary): Friend {
  const serverFriend = 'userId' in item ? item : null
  return {
    id: 'userId' in item ? item.userId : item.id,
    relationId: serverFriend?.relationId,
    nickname: item.nickname,
    username: item.username,
    avatar: item.avatarUrl,
    signature: '',
    status: 'offline',
    remark: serverFriend?.remark ?? '',
    createdTime: serverFriend?.createdTime ?? item.createdTime,
    updatedTime: serverFriend?.updatedTime ?? item.updatedTime,
    userUpdatedTime: serverFriend?.userUpdatedTime ?? item.updatedTime,
  }
}

function toRequest(item: ServerFriendRequest): FriendRequest {
  return {
    id: item.id,
    requestType: 'friend',
    direction: item.direction,
    nickname: item.user.nickname,
    username: item.user.username,
    avatar: item.user.avatarUrl,
    message: item.message ?? '',
    time: new Date(item.createdTime).toLocaleString('zh-CN', { hour12: false }),
    status: item.status === 0 ? 'pending' : item.status === 1 ? 'accepted' : 'rejected',
    senderId: item.senderId,
    receiverId: item.receiverId,
    createdTime: item.createdTime,
    updatedTime: item.updatedTime,
    userUpdatedTime: item.user.updatedTime,
  }
}

class FriendService {
  async loadAndSync() {
    try {
      const [friends, requests] = await Promise.all([
        this.syncFriends(),
        this.syncRequests(),
      ])
      return { friends, requests }
    } catch (error) {
      if (!isOfflineError(error)) throw error
      const friends = databaseManager.loadCachedFriends()
      const requests = databaseManager.loadCachedFriendRequests()
      return { friends, requests }
    }
  }

  async listFriends() {
    try { return await this.syncFriends() }
    catch (error) {
      if (!isOfflineError(error)) throw error
      return databaseManager.loadCachedFriends()
    }
  }

  async listRequests() {
    try { return await this.syncRequests() }
    catch (error) {
      if (!isOfflineError(error)) throw error
      return databaseManager.loadCachedFriendRequests()
    }
  }

  async search(username: string) {
    return toFriend(await apiClient.get<ServerUserSummary>(`/users/search?username=${encodeURIComponent(username)}`))
  }

  async apply(username: string, reason: string) {
    const request = await apiClient.post<ServerFriendRequest>('/friend-requests', { username, message: reason })
    const mapped = toRequest(request)
    const requests = await this.syncRequests()
    return requests.find((item) => item.id === mapped.id) ?? mapped
  }

  async accept(requestId: string) {
    const request = await apiClient.post<ServerFriendRequest>(`/friend-requests/${requestId}/accept`)
    const data = await this.loadAndSync()
    return data.requests.find((item) => item.id === requestId) ?? toRequest(request)
  }

  async reject(requestId: string) {
    const request = await apiClient.post<ServerFriendRequest>(`/friend-requests/${requestId}/reject`)
    const requests = await this.syncRequests()
    return requests.find((item) => item.id === requestId) ?? toRequest(request)
  }

  async updateRemark(friendUserId: string, remark: string) {
    const friend = toFriend(await apiClient.patch<ServerFriend>(`/friends/${friendUserId}/remark`, { remark }))
    const friends = await this.syncFriends()
    return friends.find((item) => item.id === friendUserId) ?? friend
  }

  async delete(friendUserId: string) {
    await apiClient.delete<null>(`/friends/${friendUserId}`)
    await this.syncFriends()
  }

  private async syncFriends() {
    const version = databaseManager.getDirectoryVersion('friends')
    const result = await apiClient.get<VersionedSync<ServerFriend>>(
      `/sync/friends?version=${encodeURIComponent(version)}`,
    )
    if (result.changed) {
      const friends = result.items.map(toFriend)
      databaseManager.syncFriends(friends, result.version)
      avatarCacheService.cacheFriends(friends)
    }
    return databaseManager.loadCachedFriends()
  }

  private async syncRequests() {
    const version = databaseManager.getDirectoryVersion('friend-requests')
    const result = await apiClient.get<VersionedSync<ServerFriendRequest>>(
      `/sync/friend-requests?version=${encodeURIComponent(version)}`,
    )
    if (result.changed) databaseManager.syncFriendRequests(result.items.map(toRequest), result.version)
    return databaseManager.loadCachedFriendRequests()
  }
}

export const friendService = new FriendService()
