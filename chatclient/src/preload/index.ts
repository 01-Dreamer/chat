import { contextBridge, ipcRenderer } from 'electron'
import type { Message, ProfilePatch } from '../main/types'

const api = {
  login: (username: string, password: string) => ipcRenderer.invoke('auth:login', username, password),
  register: (nickname: string, username: string, password: string) => ipcRenderer.invoke('auth:register', nickname, username, password),
  bootstrap: () => ipcRenderer.invoke('app:bootstrap'),
  sendMessage: (message: Omit<Message, 'id' | 'createdAt'>) => ipcRenderer.invoke('chat:sendMessage', message),
  updateProfile: (patch: ProfilePatch) => ipcRenderer.invoke('profile:update', patch),
  resetTransferPassword: (oldPassword: string, newPassword: string) => ipcRenderer.invoke('profile:resetTransferPassword', oldPassword, newPassword),
  acceptFriendRequest: (id: string) => ipcRenderer.invoke('contacts:acceptRequest', id),
  rejectFriendRequest: (id: string) => ipcRenderer.invoke('contacts:rejectRequest', id),
  updateContactRemark: (kind: 'friend' | 'group', id: string, remark: string) => ipcRenderer.invoke('contacts:updateRemark', kind, id, remark),
  searchFriendByUsername: (username: string) => ipcRenderer.invoke('contacts:searchFriend', username),
  applyAddFriend: (username: string, reason: string) => ipcRenderer.invoke('contacts:applyFriend', username, reason),
  searchGroupByNumber: (groupNumber: string) => ipcRenderer.invoke('contacts:searchGroup', groupNumber),
  applyJoinGroup: (groupNumber: string, reason: string) => ipcRenderer.invoke('contacts:applyGroup', groupNumber, reason),
  addFriendByUsername: (username: string) => ipcRenderer.invoke('contacts:addFriend', username),
  joinGroupByNumber: (groupNumber: string) => ipcRenderer.invoke('contacts:joinGroup', groupNumber),
  createGroup: (name: string) => ipcRenderer.invoke('contacts:createGroup', name),
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
}

contextBridge.exposeInMainWorld('chatApi', api)
