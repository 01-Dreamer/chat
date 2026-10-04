import type { Conversation, FriendRequest, GroupActionResult, GroupChat, GroupMember } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient } from './apiClient'
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
  }
}

class GroupService {
  async loadAndSync(currentUserId: string) {
    try {
      const [groups, requests] = await Promise.all([
        this.listGroups(),
        this.listRequests(currentUserId),
      ])
      databaseManager.syncGroupData(groups, requests)
      avatarCacheService.cacheGroups(groups)
      return { groups, requests }
    } catch (error) {
      const groups = databaseManager.loadCachedGroups()
      if (!groups.length) throw error
      return { groups, requests: [] }
    }
  }

  async listGroups() {
    return (await apiClient.get<ServerGroup[]>('/groups')).map(toGroup)
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
    const requests = await this.listRequests(currentUserId)
    databaseManager.syncGroupJoinRequests(requests)
    return toRequest(request, currentUserId)
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
    return { group, conversation }
  }

  async updateRemark(groupId: string, remark: string) {
    const group = toGroup(await apiClient.patch<ServerGroup>(`/groups/${groupId}/remark`, { value: remark }))
    databaseManager.upsertGroup(group)
    return group
  }

  async listMembers(groupId: string) {
    const members = (await apiClient.get<ServerGroupMember[]>(`/groups/${groupId}/members`)).map(toMember)
    databaseManager.syncGroupMembers(groupId, members)
    avatarCacheService.cacheMembers(members)
    return members
  }

  async updateProfile(groupId: string, name: string) {
    const group = toGroup(await apiClient.patch<ServerGroup>(`/groups/${groupId}`, { name }))
    databaseManager.upsertGroup(group)
    return group
  }

  async updateAvatar(groupId: string, resourceId: string) {
    const group = toGroup(await apiClient.patch<ServerGroup>(`/groups/${groupId}/avatar`, { resourceId }))
    databaseManager.upsertGroup(group)
    return group
  }

  async updateMyNickname(groupId: string, value: string) {
    const member = toMember(await apiClient.patch<ServerGroupMember>(`/groups/${groupId}/members/me/nickname`, { value }))
    await this.listMembers(groupId)
    return member
  }

  async updateRole(groupId: string, userId: string, role: number) {
    const member = toMember(await apiClient.patch<ServerGroupMember>(`/groups/${groupId}/members/${userId}/role`, { role }))
    await this.listMembers(groupId)
    return member
  }

  async kick(groupId: string, userId: string) {
    await apiClient.delete(`/groups/${groupId}/members/${userId}`)
    return this.listMembers(groupId)
  }

  async leave(groupId: string) {
    await apiClient.post(`/groups/${groupId}/leave`)
  }

  async dissolve(groupId: string) {
    await apiClient.delete(`/groups/${groupId}`)
  }

  async listRequests(currentUserId: string) {
    return (await apiClient.get<ServerGroupJoinRequest[]>('/group-join-requests'))
      .map((item) => toRequest(item, currentUserId))
  }

  async accept(requestId: string, currentUserId: string) {
    const request = toRequest(
      await apiClient.post<ServerGroupJoinRequest>(`/group-join-requests/${requestId}/accept`),
      currentUserId,
    )
    await this.loadAndSync(currentUserId)
    return request
  }

  async reject(requestId: string, currentUserId: string) {
    const request = toRequest(
      await apiClient.post<ServerGroupJoinRequest>(`/group-join-requests/${requestId}/reject`),
      currentUserId,
    )
    databaseManager.syncGroupJoinRequests(await this.listRequests(currentUserId))
    return request
  }
}

export const groupService = new GroupService()
