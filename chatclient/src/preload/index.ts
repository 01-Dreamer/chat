import { contextBridge, ipcRenderer, webUtils } from 'electron'
import type { IpcResult, Message, MessagePageCursor, PendingAttachment, ProfilePatch } from '../main/types'

async function invokeAuth<T>(channel: string, ...args: unknown[]): Promise<T> {
  const result = await ipcRenderer.invoke(channel, ...args) as IpcResult<T>
  if (!result.ok) throw new Error(result.error || '操作失败')
  return result.data as T
}

function readableError(error: unknown) {
  let message = error instanceof Error ? error.message : String(error ?? '')
  message = message
    .replace(/^Error invoking remote method '[^']+':\s*/i, '')
    .replace(/^Error:\s*/i, '')
    .trim()
  if (/ENOENT/i.test(message)) return '文件不存在或已被移动'
  if (/EACCES|EPERM/i.test(message)) return '没有权限执行此操作'
  if (!message) return '操作失败，请稍后重试'
  return /[\u3400-\u9fff]/.test(message) ? message : '操作失败，请稍后重试'
}

async function invokeRemote<T>(channel: string, ...args: unknown[]): Promise<T> {
  try {
    return await ipcRenderer.invoke(channel, ...args) as T
  } catch (error) {
    throw new Error(readableError(error))
  }
}

const api = {
  login: (username: string, password: string) => invokeAuth('auth:login', username, password),
  register: (nickname: string, username: string, password: string) => invokeAuth('auth:register', nickname, username, password),
  logout: () => invokeAuth<boolean>('auth:logout'),
  bootstrap: () => invokeRemote('app:bootstrap'),
  loadLocalChatState: () => invokeRemote('sessions:localState'),
  loadConversationMessages: (chatKey: string, cursor: MessagePageCursor | null = null, pageSize = 50) => invokeRemote('sessions:messages', chatKey, cursor, pageSize),
  setSessionPinned: (chatKey: string, pinned: boolean) => invokeRemote('sessions:setPinned', chatKey, pinned),
  markSessionRead: (chatKey: string) => invokeRemote('sessions:markRead', chatKey),
  setActiveSession: (chatKey: string | null) => invokeRemote('sessions:setActive', chatKey),
  hideSession: (chatKey: string) => invokeRemote('sessions:hide', chatKey),
  sendMessage: (message: Omit<Message, 'id' | 'createdAt'>) => invokeRemote('chat:sendMessage', message),
  selectAttachments: () => invokeRemote('files:selectAttachments') as Promise<PendingAttachment[]>,
  stageDroppedFiles: (files: File[]) => {
    const paths = files.map((file) => webUtils.getPathForFile(file)).filter(Boolean)
    return invokeRemote('files:stagePaths', paths) as Promise<PendingAttachment[]>
  },
  captureScreen: () => invokeRemote('files:capture') as Promise<PendingAttachment | null>,
  sendAttachment: (conversationId: string, attachment: PendingAttachment) => invokeRemote('chat:sendAttachment', conversationId, attachment),
  sendVoice: (conversationId: string, bytes: Uint8Array, mimeType: string, duration: number) => invokeRemote('chat:sendVoice', conversationId, bytes, mimeType, duration),
  openFile: (resourceId: string) => invokeRemote('files:open', resourceId),
  loadVoice: (resourceId: string) => invokeRemote('files:loadVoice', resourceId),
  loadImage: (resourceId: string) => invokeRemote('files:loadImage', resourceId),
  downloadFile: (resourceId: string) => invokeRemote('files:downloadDocument', resourceId),
  onFileDownloadProgress: (callback: (value: { resourceId: string, received: number, total: number, percent: number }) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, value: { resourceId: string, received: number, total: number, percent: number }) => callback(value)
    ipcRenderer.on('files:downloadProgress', listener)
    return () => ipcRenderer.removeListener('files:downloadProgress', listener)
  },
  openConversation: (type: 'direct' | 'group', targetId: string) => invokeRemote('chat:openConversation', type, targetId),
  recallMessage: (messageId: string) => invokeRemote('chat:recall', messageId),
  retryMessage: (clientMessageId: string) => invokeRemote('chat:retry', clientMessageId),
  updateProfile: (patch: ProfilePatch) => invokeRemote('profile:update', patch),
  updateAvatar: () => invokeRemote('profile:updateAvatar'),
  resetPayPassword: (oldPassword: string, newPassword: string) => invokeRemote('profile:resetPayPassword', oldPassword, newPassword),
  getWallet: () => invokeRemote('wallet:account'),
  sendRedPacket: (conversationId: string, input: unknown) => invokeRemote('wallet:sendRedPacket', conversationId, input),
  claimRedPacket: (packetId: string) => invokeRemote('wallet:claimRedPacket', packetId),
  translateText: (value: string, targetLanguage?: string) => invokeRemote('ai:translate', value, targetLanguage),
  smartReplies: (messages: Array<{ role: 'user' | 'assistant', content: string }>) => invokeRemote('ai:smartReplies', messages),
  transcribeVoice: (resourceId: string) => invokeRemote('asr:transcribe', resourceId),
  createCall: (calleeId: string, callType: number) => invokeRemote('calls:create', calleeId, callType),
  updateCall: (callId: string, action: string) => invokeRemote('calls:update', callId, action),
  getCall: (callId: string) => invokeRemote('calls:get', callId),
  getIceServers: () => invokeRemote('calls:iceServers'),
  sendCallSignal: (callId: string, targetUserId: string, signalType: string, payload: unknown) => invokeRemote('calls:signal', callId, targetUserId, signalType, payload),
  acceptFriendRequest: (id: string, type: 'friend' | 'group') => invokeRemote('contacts:acceptRequest', id, type),
  rejectFriendRequest: (id: string, type: 'friend' | 'group') => invokeRemote('contacts:rejectRequest', id, type),
  updateContactRemark: (kind: 'friend' | 'group', id: string, remark: string) => invokeRemote('contacts:updateRemark', kind, id, remark),
  searchFriendByUsername: (username: string) => invokeRemote('contacts:searchFriend', username),
  applyAddFriend: (username: string, reason: string) => invokeRemote('contacts:applyFriend', username, reason),
  listFriends: () => invokeRemote('contacts:listFriends'),
  listContactRequests: () => invokeRemote('contacts:listRequests'),
  deleteFriend: (id: string) => invokeRemote('contacts:deleteFriend', id),
  searchGroupByNumber: (groupNumber: string) => invokeRemote('contacts:searchGroup', groupNumber),
  applyJoinGroup: (groupNumber: string, reason: string) => invokeRemote('contacts:applyGroup', groupNumber, reason),
  listGroups: () => invokeRemote('contacts:listGroups'),
  listGroupMembers: (groupId: string) => invokeRemote('contacts:listGroupMembers', groupId),
  updateGroupProfile: (groupId: string, name: string) => invokeRemote('groups:updateProfile', groupId, name),
  updateGroupAvatar: (groupId: string) => invokeRemote('groups:updateAvatar', groupId),
  updateMyGroupNickname: (groupId: string, value: string) => invokeRemote('groups:updateMyNickname', groupId, value),
  updateGroupRole: (groupId: string, userId: string, role: number) => invokeRemote('groups:updateRole', groupId, userId, role),
  kickGroupMember: (groupId: string, userId: string) => invokeRemote('groups:kick', groupId, userId),
  leaveGroup: (groupId: string) => invokeRemote('groups:leave', groupId),
  dissolveGroup: (groupId: string) => invokeRemote('groups:dissolve', groupId),
  createGroup: (name: string) => invokeRemote('contacts:createGroup', name),
  refreshContacts: () => invokeRemote('contacts:refresh'),
  markNotificationRead: (id: string) => invokeRemote('notifications:markRead', id),
  listNotifications: () => invokeRemote('notifications:list'),
  copyText: (value: string) => invokeRemote('clipboard:writeText', value),
  setWindowMode: (mode: 'login' | 'register' | 'main') => ipcRenderer.send('window:setMode', mode),
  minimizeWindow: () => invokeRemote('window:minimize'),
  toggleMaximize: () => invokeRemote('window:toggleMaximize'),
  closeWindow: () => invokeRemote('window:close'),
  isWindowMaximized: () => invokeRemote('window:isMaximized'),
  onWindowMaximizedChanged: (callback: (value: boolean) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, value: boolean) => callback(value)
    ipcRenderer.on('window:maximizedChanged', listener)
    return () => ipcRenderer.removeListener('window:maximizedChanged', listener)
  },
  onRealtimeEvent: (callback: (value: unknown) => void) => {
    const listener = (_event: Electron.IpcRendererEvent, value: unknown) => callback(value)
    ipcRenderer.on('chat:event', listener)
    return () => ipcRenderer.removeListener('chat:event', listener)
  },
}

contextBridge.exposeInMainWorld('chatApi', api)
