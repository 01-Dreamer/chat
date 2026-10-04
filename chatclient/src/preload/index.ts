import { contextBridge, ipcRenderer } from 'electron'
import type { IpcResult, Message, ProfilePatch } from '../main/types'

async function invokeAuth<T>(channel: string, ...args: unknown[]): Promise<T> {
  const result = await ipcRenderer.invoke(channel, ...args) as IpcResult<T>
  if (!result.ok) throw new Error(result.error || '操作失败')
  return result.data as T
}

const api = {
  login: (username: string, password: string) => invokeAuth('auth:login', username, password),
  register: (nickname: string, username: string, password: string) => invokeAuth('auth:register', nickname, username, password),
  logout: () => invokeAuth<boolean>('auth:logout'),
  bootstrap: () => ipcRenderer.invoke('app:bootstrap'),
  loadLocalChatState: () => ipcRenderer.invoke('sessions:localState'),
  setSessionPinned: (chatKey: string, pinned: boolean) => ipcRenderer.invoke('sessions:setPinned', chatKey, pinned),
  markSessionRead: (chatKey: string) => ipcRenderer.invoke('sessions:markRead', chatKey),
  hideSession: (chatKey: string) => ipcRenderer.invoke('sessions:hide', chatKey),
  sendMessage: (message: Omit<Message, 'id' | 'createdAt'>) => ipcRenderer.invoke('chat:sendMessage', message),
  selectAndSendFile: (conversationId: string, resourceType?: number) => ipcRenderer.invoke('chat:selectAndSendFile', conversationId, resourceType),
  captureAndSend: (conversationId: string) => ipcRenderer.invoke('chat:captureAndSend', conversationId),
  openFile: (resourceId: string) => ipcRenderer.invoke('files:open', resourceId),
  openConversation: (type: 'direct' | 'group', targetId: string) => ipcRenderer.invoke('chat:openConversation', type, targetId),
  recallMessage: (messageId: string) => ipcRenderer.invoke('chat:recall', messageId),
  updateProfile: (patch: ProfilePatch) => ipcRenderer.invoke('profile:update', patch),
  updateAvatar: () => ipcRenderer.invoke('profile:updateAvatar'),
  resetTransferPassword: (oldPassword: string, newPassword: string) => ipcRenderer.invoke('profile:resetTransferPassword', oldPassword, newPassword),
  getWallet: () => ipcRenderer.invoke('wallet:account'),
  transfer: (recipientUserId: string, amount: string, payPassword: string) => ipcRenderer.invoke('wallet:transfer', recipientUserId, amount, payPassword),
  sendRedPacket: (conversationId: string, input: unknown) => ipcRenderer.invoke('wallet:sendRedPacket', conversationId, input),
  claimRedPacket: (packetId: string) => ipcRenderer.invoke('wallet:claimRedPacket', packetId),
  translateText: (value: string, targetLanguage?: string) => ipcRenderer.invoke('ai:translate', value, targetLanguage),
  smartReplies: (messages: Array<{ role: 'user' | 'assistant', content: string }>) => ipcRenderer.invoke('ai:smartReplies', messages),
  transcribeVoice: (resourceId: string) => ipcRenderer.invoke('asr:transcribe', resourceId),
  createCall: (calleeId: string, callType: number) => ipcRenderer.invoke('calls:create', calleeId, callType),
  updateCall: (callId: string, action: string) => ipcRenderer.invoke('calls:update', callId, action),
  getIceServers: () => ipcRenderer.invoke('calls:iceServers'),
  sendCallSignal: (callId: string, targetUserId: string, signalType: string, payload: unknown) => ipcRenderer.invoke('calls:signal', callId, targetUserId, signalType, payload),
  acceptFriendRequest: (id: string, type: 'friend' | 'group') => ipcRenderer.invoke('contacts:acceptRequest', id, type),
  rejectFriendRequest: (id: string, type: 'friend' | 'group') => ipcRenderer.invoke('contacts:rejectRequest', id, type),
  updateContactRemark: (kind: 'friend' | 'group', id: string, remark: string) => ipcRenderer.invoke('contacts:updateRemark', kind, id, remark),
  searchFriendByUsername: (username: string) => ipcRenderer.invoke('contacts:searchFriend', username),
  applyAddFriend: (username: string, reason: string) => ipcRenderer.invoke('contacts:applyFriend', username, reason),
  listFriends: () => ipcRenderer.invoke('contacts:listFriends'),
  deleteFriend: (id: string) => ipcRenderer.invoke('contacts:deleteFriend', id),
  searchGroupByNumber: (groupNumber: string) => ipcRenderer.invoke('contacts:searchGroup', groupNumber),
  applyJoinGroup: (groupNumber: string, reason: string) => ipcRenderer.invoke('contacts:applyGroup', groupNumber, reason),
  listGroups: () => ipcRenderer.invoke('contacts:listGroups'),
  listGroupMembers: (groupId: string) => ipcRenderer.invoke('contacts:listGroupMembers', groupId),
  updateGroupProfile: (groupId: string, name: string) => ipcRenderer.invoke('groups:updateProfile', groupId, name),
  updateGroupAvatar: (groupId: string) => ipcRenderer.invoke('groups:updateAvatar', groupId),
  updateMyGroupNickname: (groupId: string, value: string) => ipcRenderer.invoke('groups:updateMyNickname', groupId, value),
  updateGroupRole: (groupId: string, userId: string, role: number) => ipcRenderer.invoke('groups:updateRole', groupId, userId, role),
  kickGroupMember: (groupId: string, userId: string) => ipcRenderer.invoke('groups:kick', groupId, userId),
  leaveGroup: (groupId: string) => ipcRenderer.invoke('groups:leave', groupId),
  dissolveGroup: (groupId: string) => ipcRenderer.invoke('groups:dissolve', groupId),
  createGroup: (name: string) => ipcRenderer.invoke('contacts:createGroup', name),
  refreshContacts: () => ipcRenderer.invoke('contacts:refresh'),
  markNotificationRead: (id: string) => ipcRenderer.invoke('notifications:markRead', id),
  setWindowMode: (mode: 'login' | 'register' | 'main') => ipcRenderer.send('window:setMode', mode),
  minimizeWindow: () => ipcRenderer.invoke('window:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('window:toggleMaximize'),
  closeWindow: () => ipcRenderer.invoke('window:close'),
  isWindowMaximized: () => ipcRenderer.invoke('window:isMaximized'),
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
