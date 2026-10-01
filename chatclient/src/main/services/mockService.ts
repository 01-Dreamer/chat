import { createMockBootstrap } from './mockData'
import type { BootstrapData, Conversation, Friend, GroupActionResult, GroupChat, Message, ProfilePatch } from '../types'

const state = createMockBootstrap()
let transferPassword = '123456'

export const mockService = {
  login(username: string, _password: string) {
    if (!username.trim()) throw new Error('请输入用户名')
    return state.user
  },
  register(nickname: string, username: string, _password: string) {
    if (!nickname.trim() || !username.trim()) throw new Error('请完整填写注册信息')
    state.user = { ...state.user, nickname: nickname.trim(), username: username.trim() }
    return state.user
  },
  bootstrap(): BootstrapData {
    return structuredClone(state)
  },
  sendMessage(message: Omit<Message, 'id' | 'createdAt'>): Message {
    const created: Message = { ...message, id: `m-${Date.now()}`, createdAt: Date.now() }
    state.messages[message.conversationId] ??= []
    state.messages[message.conversationId].push(created)
    const conversation = state.conversations.find((item) => item.id === message.conversationId)
    if (conversation) {
      conversation.preview = message.type === 'text'
        ? message.content
        : message.type === 'file'
          ? `[${message.fileKind === 'image' ? '图片' : message.fileKind === 'video' ? '视频' : '文件'}] ${message.fileName ?? ''}`.trim()
          : message.type === 'voice'
            ? '[语音]'
            : '[红包]'
      conversation.time = '刚刚'
    }
    return structuredClone(created)
  },
  updateProfile(patch: ProfilePatch) {
    state.user = { ...state.user, ...patch }
    return structuredClone(state.user)
  },
  resetTransferPassword(oldPassword: string, newPassword: string) {
    if (!/^\d{6}$/.test(oldPassword) || !/^\d{6}$/.test(newPassword)) throw new Error('转账密码必须是 6 位数字')
    if (oldPassword !== transferPassword) throw new Error('旧转账密码错误')
    transferPassword = newPassword
    return true
  },
  updateContactRemark(kind: 'friend' | 'group', id: string, remark: string) {
    const target = kind === 'friend' ? state.friends.find((item) => item.id === id) : state.groups.find((item) => item.id === id)
    if (!target) throw new Error('联系人不存在')
    target.remark = remark.trim()
    return structuredClone(target)
  },
  searchFriendByUsername(username: string): Friend {
    const normalized = username.trim()
    if (!normalized) throw new Error('请输入用户名')
    if (normalized === state.user.username) throw new Error('不能添加自己为好友')
    const existing = state.friends.find((item) => item.username === normalized)
    if (existing) return structuredClone(existing)
    const requestUser = state.friendRequests.find((item) => item.username === normalized)
    return structuredClone({ id: requestUser?.id ?? `search-user-${normalized}`, username: normalized, nickname: requestUser?.nickname ?? normalized, avatar: requestUser?.avatar ?? state.friends[0]?.avatar ?? state.user.avatar, signature: '通过用户名搜索到的 Mock 用户', status: 'offline', remark: '' })
  },
  applyAddFriend(username: string, reason: string) {
    const normalized = username.trim()
    if (state.friends.some((item) => item.username === normalized)) throw new Error('对方已经是你的好友')
    state.notifications.unshift({ id: `n-${Date.now()}`, type: 'system', title: '好友申请已发送', content: `已向 ${normalized} 发送好友申请${reason.trim() ? `：${reason.trim()}` : ''}`, time: '刚刚', read: false })
    return true
  },
  searchGroupByNumber(groupNumber: string): GroupChat {
    const normalized = groupNumber.trim()
    if (!/^\d+$/.test(normalized)) throw new Error('群聊号只能包含数字')
    const existing = state.groups.find((item) => item.groupNumber === normalized)
    if (existing) return structuredClone(existing)
    return structuredClone({ id: `search-group-${normalized}`, groupNumber: normalized, name: `群聊 ${normalized}`, avatar: state.groups[0]?.avatar ?? state.user.avatar, memberCount: 36, description: '通过群聊号搜索到的 Mock 群聊', owner: '群主', remark: '' })
  },
  applyJoinGroup(groupNumber: string, reason: string) {
    const normalized = groupNumber.trim()
    if (state.groups.some((item) => item.groupNumber === normalized)) throw new Error('你已经在该群聊中')
    state.notifications.unshift({ id: `n-${Date.now()}`, type: 'system', title: '入群申请已发送', content: `已向群聊 ${normalized} 发送申请${reason.trim() ? `：${reason.trim()}` : ''}`, time: '刚刚', read: false })
    return true
  },
  addFriendByUsername(username: string): Friend {
    const normalized = username.trim()
    if (!normalized) throw new Error('请输入用户名')
    if (normalized === state.user.username) throw new Error('不能添加自己为好友')
    const existing = state.friends.find((item) => item.username === normalized)
    if (existing) return structuredClone(existing)
    const friend: Friend = { id: `u-${Date.now()}`, username: normalized, nickname: normalized, avatar: state.friends[0]?.avatar ?? state.user.avatar, signature: '这个人很神秘，还没有填写个性签名', status: 'offline', remark: '' }
    state.friends.push(friend)
    return structuredClone(friend)
  },
  joinGroupByNumber(groupNumber: string): GroupActionResult {
    const normalized = groupNumber.trim()
    if (!/^\d+$/.test(normalized)) throw new Error('群聊号只能包含数字')
    let group = state.groups.find((item) => item.groupNumber === normalized)
    if (!group) {
      group = { id: `g-${Date.now()}`, groupNumber: normalized, name: `群聊 ${normalized}`, avatar: state.groups[0]?.avatar ?? state.user.avatar, memberCount: 8, description: '通过群聊号加入的 Mock 群聊', owner: '群主', remark: '' }
      state.groups.push(group)
    }
    let conversation = state.conversations.find((item) => item.targetId === group!.id)
    if (!conversation) {
      conversation = { id: `c-${group.id}`, type: 'group', name: group.name, avatar: group.avatar, preview: '你已加入群聊', time: '刚刚', unread: 0, targetId: group.id }
      state.conversations.unshift(conversation)
      state.messages[conversation.id] = []
    }
    return structuredClone({ group, conversation })
  },
  createGroup(name: string): GroupActionResult {
    const normalized = name.trim()
    if (!normalized) throw new Error('请输入群聊名称')
    const stamp = Date.now()
    const group: GroupChat = { id: `g-${stamp}`, groupNumber: String(stamp).slice(-8), name: normalized, avatar: state.groups[0]?.avatar ?? state.user.avatar, memberCount: 1, description: '新创建的 Mock 群聊', owner: state.user.nickname, remark: '' }
    const conversation: Conversation = { id: `c-${group.id}`, type: 'group', name: group.name, avatar: group.avatar, preview: '群聊创建成功', time: '刚刚', unread: 0, targetId: group.id }
    state.groups.unshift(group)
    state.conversations.unshift(conversation)
    state.messages[conversation.id] = []
    return structuredClone({ group, conversation })
  },
  acceptFriendRequest(id: string) {
    const request = state.friendRequests.find((item) => item.id === id)
    if (!request) throw new Error('好友申请不存在')
    request.status = 'accepted'
    state.notifications.unshift({ id: `n-${Date.now()}`, type: 'friend_accepted', title: '好友申请通过', content: `你已接受 ${request.nickname} 的好友申请`, time: '刚刚', read: false })
    return structuredClone(request)
  },
  rejectFriendRequest(id: string) {
    const request = state.friendRequests.find((item) => item.id === id)
    if (!request) throw new Error('好友申请不存在')
    request.status = 'rejected'
    return structuredClone(request)
  },
  markNotificationRead(id: string) {
    const notification = state.notifications.find((item) => item.id === id)
    if (notification) notification.read = true
    return notification ? structuredClone(notification) : null
  },
}
