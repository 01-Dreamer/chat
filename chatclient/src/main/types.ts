export type MessageType = 'text' | 'voice' | 'file' | 'red_packet'
export type MessageFileKind = 'document' | 'image' | 'video'

export interface User {
  id: string
  nickname: string
  username: string
  avatar: string | null
  balance: string
  status?: number
  createdTime?: number
  updatedTime?: number
}

export interface IpcResult<T> {
  ok: boolean
  data?: T
  error?: string
}

export interface ProfilePatch {
  nickname?: string
  avatar?: string | null
}

export interface Friend {
  id: string
  relationId?: string
  nickname: string
  username: string
  avatar: string | null
  signature: string
  status: 'online' | 'offline'
  remark: string
  createdTime?: number
  updatedTime?: number
  userUpdatedTime?: number
}

export interface FriendRequest {
  id: string
  requestType: 'friend' | 'group'
  direction: 'incoming' | 'outgoing'
  nickname: string
  username: string
  groupName?: string
  groupNumber?: string
  applicantNickname?: string
  applicantAvatar?: string | null
  avatar: string | null
  message: string
  time: string
  status: 'pending' | 'accepted' | 'rejected'
  senderId?: string
  receiverId?: string
  createdTime?: number
  updatedTime?: number
}

export interface GroupChat {
  id: string
  groupNumber: string
  name: string
  avatar: string | null
  memberCount: number
  description: string
  owner: string
  ownerId: string
  remark: string
  status: number
  currentUserRole: number
  currentUserNickname: string
  createdTime: number
  updatedTime: number
}

export interface GroupMember {
  id: string
  groupId: string
  userId: string
  role: number
  groupNickname: string
  username: string
  nickname: string
  avatar: string | null
  createdTime: number
  updatedTime: number
}

export interface GroupActionResult {
  group: GroupChat
  conversation: Conversation
}

export interface Message {
  id: string
  clientMessageId?: string
  chatKey?: string
  conversationId: string
  senderId: string
  senderName: string
  senderAvatar: string | null
  type: MessageType
  content: string
  referenceId?: string | null
  replyMessageId?: string | null
  createdAt: number
  amount?: string
  fileKind?: MessageFileKind
  fileName?: string
  fileSize?: string
  thumbnail?: string
  duration?: number
  transcript?: string
  read?: boolean
  status?: number
  recallOperatorId?: string | null
  recallOperatorName?: string | null
  sendStatus?: 'sending' | 'success' | 'failed'
}

export interface ServerMessage {
  id: string
  clientMessageId: string
  chatKey: string
  senderId: string
  chatType: number
  targetId: string
  messageType: number
  content: string | null
  referenceId: string | null
  replyMessageId: string | null
  status: number
  recallOperatorId: string | null
  recalledTime: number | null
  createdTime: number
  updatedTime: number
}

export interface FileResource {
  id: string
  uploaderId: string
  resourceType: number
  fileName: string
  fileSize: number
  mimeType: string
  fileHash: string
  fileUrl: string
  duration: number | null
  localPath?: string | null
  createdTime: number
  updatedTime: number
}

export interface WalletAccount {
  id: string
  userId: string
  balance: string
  payPasswordSet: boolean
  status: number
}

export interface RedPacket {
  id: string
  senderId: string
  chatType: number
  targetId: string
  packetType: number
  totalAmount: string
  totalCount: number
  remainAmount: string
  remainCount: number
  message: string
  status: number
  expireTime: number
  createdTime: number
  updatedTime: number
}

export interface TransferResult {
  id: string
  clientTransactionId: string
  fromUserId: string
  toUserId: string
  transactionType: number
  amount: string
  referenceId: string | null
  status: number
  createdTime: number
}

export interface CallRecord {
  id: string
  callerId: string
  calleeId: string
  callType: number
  status: number
  startTime: number | null
  endTime: number | null
  duration: number
  createdTime: number
  updatedTime: number
}

export interface Conversation {
  id: string
  type: 'direct' | 'group'
  name: string
  avatar: string | null
  preview: string
  time: string
  unread: number
  targetId: string
  pinned?: boolean
}

export interface OpenConversationResult {
  conversation: Conversation
  messages: Message[]
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
