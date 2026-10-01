export type MessageType = 'text' | 'voice' | 'file' | 'red_packet'
export type MessageFileKind = 'document' | 'image' | 'video'

export interface User {
  id: string
  nickname: string
  username: string
  avatar: string
  balance: number
}

export interface ProfilePatch {
  nickname?: string
  avatar?: string
}

export interface Friend {
  id: string
  nickname: string
  username: string
  avatar: string
  signature: string
  status: 'online' | 'offline'
  remark: string
}

export interface FriendRequest {
  id: string
  nickname: string
  username: string
  avatar: string
  message: string
  time: string
  status: 'pending' | 'accepted' | 'rejected'
}

export interface GroupChat {
  id: string
  groupNumber: string
  name: string
  avatar: string
  memberCount: number
  description: string
  owner: string
  remark: string
}

export interface GroupActionResult {
  group: GroupChat
  conversation: Conversation
}

export interface Message {
  id: string
  conversationId: string
  senderId: string
  senderName: string
  senderAvatar: string
  type: MessageType
  content: string
  createdAt: number
  amount?: number
  fileKind?: MessageFileKind
  fileName?: string
  fileSize?: string
  thumbnail?: string
  duration?: number
  transcript?: string
  read?: boolean
}

export interface Conversation {
  id: string
  type: 'direct' | 'group'
  name: string
  avatar: string
  preview: string
  time: string
  unread: number
  targetId: string
  pinned?: boolean
}

export type NotificationType = 'friend_request' | 'friend_accepted' | 'group_joined' | 'red_packet' | 'transfer' | 'system'

export interface AppNotification {
  id: string
  type: NotificationType
  title: string
  content: string
  time: string
  read: boolean
}

export interface BootstrapData {
  user: User
  friends: Friend[]
  friendRequests: FriendRequest[]
  groups: GroupChat[]
  conversations: Conversation[]
  messages: Record<string, Message[]>
  notifications: AppNotification[]
}
