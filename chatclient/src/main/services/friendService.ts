import type { Friend, FriendRequest } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient } from './apiClient'
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
  }
}

class FriendService {
  async loadAndSync() {
    try {
      const [friends, requests] = await Promise.all([this.listFriends(), this.listRequests()])
      databaseManager.syncFriendData(friends, requests)
      avatarCacheService.cacheFriends(friends)
      return { friends, requests }
    } catch (error) {
      const friends = databaseManager.loadCachedFriends()
      const requests = databaseManager.loadCachedFriendRequests()
      if (!friends.length && !requests.length) throw error
      return { friends, requests }
    }
  }

  async listFriends() {
    const items = await apiClient.get<ServerFriend[]>('/friends')
    return items.map(toFriend)
  }

  async listRequests() {
    const items = await apiClient.get<ServerFriendRequest[]>('/friend-requests')
    return items.map(toRequest)
  }

  async search(username: string) {
    return toFriend(await apiClient.get<ServerUserSummary>(`/users/search?username=${encodeURIComponent(username)}`))
  }

  async apply(username: string, reason: string) {
    const request = await apiClient.post<ServerFriendRequest>('/friend-requests', { username, message: reason })
    const mapped = toRequest(request)
    const currentRequests = await this.listRequests()
    databaseManager.syncFriendRequests(currentRequests)
    return mapped
  }

  async accept(requestId: string) {
    const request = await apiClient.post<ServerFriendRequest>(`/friend-requests/${requestId}/accept`)
    await this.loadAndSync()
    return toRequest(request)
  }

  async reject(requestId: string) {
    const request = await apiClient.post<ServerFriendRequest>(`/friend-requests/${requestId}/reject`)
    const currentRequests = await this.listRequests()
    databaseManager.syncFriendRequests(currentRequests)
    return toRequest(request)
  }

  async updateRemark(friendUserId: string, remark: string) {
    const friend = toFriend(await apiClient.patch<ServerFriend>(`/friends/${friendUserId}/remark`, { remark }))
    const friends = await this.listFriends()
    databaseManager.syncFriends(friends)
    return friend
  }

  async delete(friendUserId: string) {
    await apiClient.delete<null>(`/friends/${friendUserId}`)
    const friends = await this.listFriends()
    databaseManager.syncFriends(friends)
  }
}

export const friendService = new FriendService()
