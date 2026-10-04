import type { Conversation, FriendRequest, GroupActionResult, GroupChat, GroupMember, VersionedSync } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient, isOfflineError } from './apiClient'
import { avatarCacheService } from './avatarCacheService'

interface ServerGroup {
  id: string
  name: string
  avatarUrl: string | null
  ownerId: string
  ownerName: string
  status: number
  memberCount: number
  currentUserRole: number
  currentUserNickname: string | null
  remark: string | null
  createdTime: number
  updatedTime: number
}

interface ServerGroupMember {
  id: string
  groupId: string
  userId: string
  role: number
  groupNickname: string | null
  username: string
  nickname: string
  avatarUrl: string | null
  createdTime: number
  updatedTime: number
  userUpdatedTime: number
}

interface ServerGroupJoinRequest {
  id: string
  groupId: string
  groupName: string
  userId: string
  username: string
  nickname: string
  avatarUrl: string | null
  message: string | null
  status: number
  reviewerId: string | null
  createdTime: number
  updatedTime: number
  userUpdatedTime: number
}

function toGroup(item: ServerGroup): GroupChat {
  return {
    id: item.id,
    groupNumber: item.id,
    name: item.name,
    avatar: item.avatarUrl,
    memberCount: item.memberCount,
    description: '',
    owner: item.ownerName,
    ownerId: item.ownerId,
    remark: item.remark ?? '',
    status: item.status,
    currentUserRole: item.currentUserRole,
    currentUserNickname: item.currentUserNickname ?? '',
    createdTime: item.createdTime,
    updatedTime: item.updatedTime,
  }
}

function toMember(item: ServerGroupMember): GroupMember {
  return {
    id: item.id,
    groupId: item.groupId,
    userId: item.userId,
    role: item.role,
    groupNickname: item.groupNickname ?? '',
    username: item.username,
    nickname: item.nickname,
    avatar: item.avatarUrl,
    createdTime: item.createdTime,
    updatedTime: item.updatedTime,
    userUpdatedTime: item.userUpdatedTime,
  }
}

function toRequest(item: ServerGroupJoinRequest, currentUserId: string): FriendRequest {
  return {
    id: item.id,
    requestType: 'group',
    direction: item.userId === currentUserId ? 'outgoing' : 'incoming',
    nickname: item.nickname,
    username: item.username,
    groupName: item.groupName,
    groupNumber: item.groupId,
    applicantNickname: item.nickname,
    applicantAvatar: item.avatarUrl,
    avatar: item.avatarUrl,
    message: item.message ?? '',
    time: new Date(item.createdTime).toLocaleString('zh-CN', { hour12: false }),
    status: item.status === 0 ? 'pending' : item.status === 1 ? 'accepted' : 'rejected',
    senderId: item.userId,
    receiverId: item.reviewerId ?? undefined,
    createdTime: item.createdTime,
    updatedTime: item.updatedTime,
    userUpdatedTime: item.userUpdatedTime,
  }
}

class GroupService {
  async loadAndSync(currentUserId: string) {
    try {
      const [groups, requests] = await Promise.all([
        this.syncGroups(),
        this.syncRequests(currentUserId),
      ])
      return { groups, requests }
    } catch (error) {
      if (!isOfflineError(error)) throw error
      const groups = databaseManager.loadCachedGroups()
      const requests = databaseManager.loadCachedGroupJoinRequests()
      return { groups, requests }
    }
  }

  async listGroups() {
    try { return await this.syncGroups() }
    catch (error) {
      if (!isOfflineError(error)) throw error
      return databaseManager.loadCachedGroups()
    }
  }

  async search(groupNumber: string) {
    const group = await apiClient.get<ServerGroup>(`/groups/search?groupNumber=${encodeURIComponent(groupNumber)}`)
    return toGroup(group)
  }

  async apply(groupId: string, reason: string, currentUserId: string) {
    const request = await apiClient.post<ServerGroupJoinRequest>('/group-join-requests', {
      groupId,
      message: reason,
    })
    const requests = await this.syncRequests(currentUserId)
    return requests.find((item) => item.id === request.id) ?? toRequest(request, currentUserId)
  }

  async create(name: string): Promise<GroupActionResult> {
    const group = toGroup(await apiClient.post<ServerGroup>('/groups', { name, memberIds: [] }))
    const conversation: Conversation = {
      id: `G:${group.id}`,
      type: 'group',
      name: group.name,
      avatar: group.avatar,
      preview: '群聊创建成功',
      time: '刚刚',
      unread: 0,
      targetId: group.id,
      lastActiveTime: Date.now(),
      createdTime: Date.now(),
    }
    databaseManager.saveCreatedGroup(group, conversation)
    const cachedGroup = databaseManager.loadCachedGroups().find((item) => item.id === group.id) ?? group
    return { group: cachedGroup, conversation }
  }

  async updateRemark(groupId: string, remark: string) {
    const group = toGroup(await apiClient.patch<ServerGroup>(`/groups/${groupId}/remark`, { value: remark }))
    databaseManager.upsertGroup(group)
    return databaseManager.loadCachedGroups().find((item) => item.id === groupId) ?? group
  }

  async listMembers(groupId: string) {
    try {
      const syncKey = `group-members:${groupId}`
      const version = databaseManager.getDirectoryVersion(syncKey)
      const result = await apiClient.get<VersionedSync<ServerGroupMember>>(
        `/sync/groups/${groupId}/members?version=${encodeURIComponent(version)}`,
      )
      if (result.changed) {
        const members = result.items.map(toMember)
        databaseManager.syncGroupMembers(groupId, members, result.version)
        avatarCacheService.cacheMembers(members)
      }
      return databaseManager.loadCachedGroupMembers(groupId)
    } catch (error) {
      if (!isOfflineError(error)) throw error
      return databaseManager.loadCachedGroupMembers(groupId)
    }
  }

  async updateProfile(groupId: string, name: string) {
    const group = toGroup(await apiClient.patch<ServerGroup>(`/groups/${groupId}`, { name }))
    databaseManager.upsertGroup(group)
    return databaseManager.loadCachedGroups().find((item) => item.id === groupId) ?? group
  }

  async updateAvatar(groupId: string, resourceId: string) {
    const group = toGroup(await apiClient.patch<ServerGroup>(`/groups/${groupId}/avatar`, { resourceId }))
    databaseManager.upsertGroup(group)
    return databaseManager.loadCachedGroups().find((item) => item.id === groupId) ?? group
  }

  async updateMyNickname(groupId: string, value: string) {
    const member = toMember(await apiClient.patch<ServerGroupMember>(`/groups/${groupId}/members/me/nickname`, { value }))
    const [members] = await Promise.all([this.listMembers(groupId), this.syncGroups()])
    return members.find((item) => item.userId === member.userId) ?? member
  }

  async updateRole(groupId: string, userId: string, role: number) {
    const member = toMember(await apiClient.patch<ServerGroupMember>(`/groups/${groupId}/members/${userId}/role`, { role }))
    const members = await this.listMembers(groupId)
    return members.find((item) => item.userId === userId) ?? member
  }

  async kick(groupId: string, userId: string) {
    await apiClient.delete(`/groups/${groupId}/members/${userId}`)
    const [members] = await Promise.all([this.listMembers(groupId), this.syncGroups()])
    return members
  }

  async leave(groupId: string) {
    await apiClient.post(`/groups/${groupId}/leave`)
    await this.syncGroups()
  }

  async dissolve(groupId: string) {
    await apiClient.delete(`/groups/${groupId}`)
    await this.syncGroups()
  }

  async listRequests(currentUserId: string) {
    try { return await this.syncRequests(currentUserId) }
    catch (error) {
      if (!isOfflineError(error)) throw error
      return databaseManager.loadCachedGroupJoinRequests()
    }
  }

  async accept(requestId: string, currentUserId: string) {
    const request = toRequest(
      await apiClient.post<ServerGroupJoinRequest>(`/group-join-requests/${requestId}/accept`),
      currentUserId,
    )
    const data = await this.loadAndSync(currentUserId)
    return data.requests.find((item) => item.id === requestId) ?? request
  }

  async reject(requestId: string, currentUserId: string) {
    const request = toRequest(
      await apiClient.post<ServerGroupJoinRequest>(`/group-join-requests/${requestId}/reject`),
      currentUserId,
    )
    const requests = await this.syncRequests(currentUserId)
    return requests.find((item) => item.id === requestId) ?? request
  }

  private async syncGroups() {
    const version = databaseManager.getDirectoryVersion('groups')
    const result = await apiClient.get<VersionedSync<ServerGroup>>(
      `/sync/groups?version=${encodeURIComponent(version)}`,
    )
    if (result.changed) {
      const groups = result.items.map(toGroup)
      databaseManager.syncGroups(groups, result.version)
      avatarCacheService.cacheGroups(groups)
    }
    return databaseManager.loadCachedGroups()
  }

  private async syncRequests(currentUserId: string) {
    const version = databaseManager.getDirectoryVersion('group-requests')
    const result = await apiClient.get<VersionedSync<ServerGroupJoinRequest>>(
      `/sync/group-requests?version=${encodeURIComponent(version)}`,
    )
    if (result.changed) {
      databaseManager.syncGroupJoinRequests(
        result.items.map((item) => toRequest(item, currentUserId)),
        result.version,
      )
    }
    return databaseManager.loadCachedGroupJoinRequests()
  }
}

export const groupService = new GroupService()
