import { app, BrowserWindow, ipcMain, screen, shell, type Rectangle } from 'electron'
import { join } from 'node:path'
import type { IpcResult, PendingAttachment } from './types'
import { databaseManager } from './database/databaseManager'
import { authService } from './services/authService'
import { friendService } from './services/friendService'
import { groupService } from './services/groupService'
import { messageService } from './services/messageService'
import { notificationService } from './services/notificationService'
import { profileService } from './services/profileService'
import { realtimeService } from './services/realtimeService'
import { fileService } from './services/fileService'
import { walletService } from './services/walletService'
import { aiService } from './services/aiService'
import { callService } from './services/callService'
import { avatarCacheService } from './services/avatarCacheService'

let mainWindow: BrowserWindow | null = null
let minimizedBounds: Rectangle | null = null
let quittingAfterLogout = false
let logoutInProgress = false

const windowSizes = {
  login: { width: 300, height: 270 },
  register: { width: 300, height: 380 },
  main: { width: 891, height: 691 },
}

if (process.env.CHATCLIENT_DEV === '1') {
  app.commandLine.appendSwitch('disable-gpu')
}

function registerIpc() {
  ipcMain.handle('auth:login', (_event, username: string, password: string) => asIpcResult(async () => {
    const user = await authService.login(username, password)
    initializeUserDatabase(user)
    avatarCacheService.cacheCurrentUser(user)
    realtimeService.connect(user.id)
    return user
  }))
  ipcMain.handle('auth:register', (_event, nickname: string, username: string, password: string) => asIpcResult(async () => {
    return authService.register(nickname, username, password)
  }))
  ipcMain.handle('auth:logout', () => asIpcResult(async () => {
    realtimeService.disconnect()
    try {
      await authService.logout()
    } finally {
      databaseManager.close()
    }
    return true
  }))
  ipcMain.handle('app:bootstrap', async () => {
    const user = await authService.refreshCurrentUser()
    initializeUserDatabase(user)
    avatarCacheService.cacheCurrentUser(user)
    const [friendData, groupData, notifications] = await Promise.all([
      friendService.loadAndSync().catch(() => ({ friends: databaseManager.loadCachedFriends(), requests: databaseManager.loadCachedFriendRequests() })),
      groupService.loadAndSync(user.id).catch(() => ({ groups: databaseManager.loadCachedGroups(), requests: [] })),
      notificationService.list().catch(() => databaseManager.loadCachedNotifications()),
    ])
    realtimeService.connect(user.id)
    const chatData = databaseManager.loadSessionState()
    return {
      user: { ...user, avatar: databaseManager.getDisplayAvatar('user', user.id) ?? user.avatar },
      friends: friendData.friends,
      friendRequests: [...friendData.requests, ...groupData.requests],
      groups: groupData.groups,
      conversations: chatData.conversations,
      messages: chatData.messages,
      notifications,
    }
  })
  ipcMain.handle('sessions:localState', () => databaseManager.loadSessionState())
  ipcMain.handle('sessions:messages', (_event, chatKey: string) => databaseManager.loadConversationMessages(chatKey))
  ipcMain.handle('sessions:setPinned', (_event, chatKey: string, pinned: boolean) => {
    databaseManager.setSessionPinned(chatKey, pinned)
    return databaseManager.loadConversations()
  })
  ipcMain.handle('sessions:markRead', (_event, chatKey: string) => databaseManager.markSessionRead(chatKey))
  ipcMain.handle('sessions:setActive', (_event, chatKey: string | null) => databaseManager.setActiveChat(chatKey))
  ipcMain.handle('sessions:hide', (_event, chatKey: string) => databaseManager.hideSession(chatKey))
  ipcMain.handle('chat:sendMessage', (_event, message) => messageService.sendText(message))
  ipcMain.handle('files:selectAttachments', () => fileService.selectAttachments())
  ipcMain.handle('files:stagePaths', (_event, paths: string[]) => fileService.stagePaths(paths))
  ipcMain.handle('files:capture', () => fileService.captureScreen())
  ipcMain.handle('chat:sendAttachment', async (_event, conversationId: string, attachment: PendingAttachment) => {
    const resource = await fileService.upload(attachment.filePath, attachment.resourceType)
    return messageService.sendFile(conversationId, resource)
  })
  ipcMain.handle('chat:sendVoice', async (_event, conversationId: string, bytes: Uint8Array, mimeType: string, duration: number) => {
    const filePath = await fileService.saveVoice(bytes, mimeType)
    const resource = await fileService.upload(filePath, 2, Math.max(1, Math.round(duration)), mimeType)
    return messageService.sendFile(conversationId, resource)
  })
  ipcMain.handle('files:open', (_event, resourceId: string) => fileService.openResource(resourceId))
  ipcMain.handle('files:loadVoice', (_event, resourceId: string) => fileService.loadVoice(resourceId))
  ipcMain.handle('files:loadImage', (_event, resourceId: string) => fileService.loadImage(resourceId))
  ipcMain.handle('files:downloadDocument', async (event, resourceId: string) => {
    let lastSentAt = 0
    let lastPercent = -1
    const path = await fileService.downloadDocument(resourceId, (received, total) => {
      const percent = total > 0 ? Math.min(100, Math.round((received / total) * 100)) : 0
      const now = Date.now()
      if (percent !== 100 && percent === lastPercent && now - lastSentAt < 100) return
      if (percent !== 100 && now - lastSentAt < 100) return
      lastSentAt = now
      lastPercent = percent
      if (!event.sender.isDestroyed()) {
        event.sender.send('files:downloadProgress', { resourceId, received, total, percent })
      }
    })
    shell.showItemInFolder(path)
    return true
  })
  ipcMain.handle('chat:openConversation', (_event, type: 'direct' | 'group', targetId: string) => messageService.openConversation(type, targetId))
  ipcMain.handle('chat:recall', (_event, messageId: string) => messageService.recall(messageId))
  ipcMain.handle('chat:retry', (_event, clientMessageId: string) => messageService.retry(clientMessageId))
  ipcMain.handle('profile:update', (_event, patch) => profileService.update(patch))
  ipcMain.handle('profile:updateAvatar', async () => {
    const resource = await fileService.selectAndUpload(0)
    return resource ? profileService.updateAvatar(resource.id) : null
  })
  ipcMain.handle('profile:resetTransferPassword', (_event, oldPassword: string, newPassword: string) => walletService.setPayPassword(oldPassword, newPassword))
  ipcMain.handle('wallet:account', () => walletService.account())
  ipcMain.handle('wallet:transfer', (_event, recipientUserId: string, amount: string, payPassword: string) => walletService.transfer(recipientUserId, amount, payPassword))
  ipcMain.handle('wallet:sendRedPacket', async (_event, conversationId: string, input) => {
    const packet = await walletService.createRedPacket(input)
    databaseManager.upsertRedPacket(packet)
    return messageService.sendRedPacket(conversationId, packet)
  })
  ipcMain.handle('wallet:claimRedPacket', async (_event, packetId: string) => {
    const result = await walletService.claim(packetId)
    databaseManager.upsertRedPacket(result.redPacket)
    return result
  })
  ipcMain.handle('ai:translate', (_event, text: string, targetLanguage?: string) => aiService.translate(text, targetLanguage))
  ipcMain.handle('ai:smartReplies', (_event, messages) => aiService.smartReplies(messages))
  ipcMain.handle('asr:transcribe', (_event, resourceId: string) => aiService.transcribe(resourceId))
  ipcMain.handle('calls:create', (_event, calleeId: string, callType: number) => callService.create(calleeId, callType))
  ipcMain.handle('calls:update', (_event, callId: string, action) => callService.update(callId, action))
  ipcMain.handle('calls:iceServers', () => callService.iceServers())
  ipcMain.handle('calls:signal', (_event, callId: string, targetUserId: string, signalType, payload) => callService.signal(callId, targetUserId, signalType, payload))
  ipcMain.handle('contacts:acceptRequest', (_event, id: string, type: 'friend' | 'group') => type === 'group'
    ? groupService.accept(id, authService.getCurrentUserId())
    : friendService.accept(id))
  ipcMain.handle('contacts:rejectRequest', (_event, id: string, type: 'friend' | 'group') => type === 'group'
    ? groupService.reject(id, authService.getCurrentUserId())
    : friendService.reject(id))
  ipcMain.handle('contacts:updateRemark', (_event, kind: 'friend' | 'group', id: string, remark: string) => kind === 'friend' ? friendService.updateRemark(id, remark) : groupService.updateRemark(id, remark))
  ipcMain.handle('contacts:searchFriend', (_event, username: string) => friendService.search(username))
  ipcMain.handle('contacts:applyFriend', (_event, username: string, reason: string) => friendService.apply(username, reason))
  ipcMain.handle('contacts:listFriends', () => friendService.listFriends())
  ipcMain.handle('contacts:deleteFriend', (_event, id: string) => friendService.delete(id))
  ipcMain.handle('contacts:searchGroup', (_event, groupNumber: string) => groupService.search(groupNumber))
  ipcMain.handle('contacts:applyGroup', (_event, groupNumber: string, reason: string) => groupService.apply(groupNumber, reason, authService.getCurrentUserId()))
  ipcMain.handle('contacts:listGroups', () => groupService.listGroups())
  ipcMain.handle('contacts:listGroupMembers', (_event, groupId: string) => groupService.listMembers(groupId))
  ipcMain.handle('groups:updateProfile', (_event, groupId: string, name: string) => groupService.updateProfile(groupId, name))
  ipcMain.handle('groups:updateAvatar', async (_event, groupId: string) => {
    const resource = await fileService.selectAndUpload(0)
    return resource ? groupService.updateAvatar(groupId, resource.id) : null
  })
  ipcMain.handle('groups:updateMyNickname', (_event, groupId: string, value: string) => groupService.updateMyNickname(groupId, value))
  ipcMain.handle('groups:updateRole', (_event, groupId: string, userId: string, role: number) => groupService.updateRole(groupId, userId, role))
  ipcMain.handle('groups:kick', (_event, groupId: string, userId: string) => groupService.kick(groupId, userId))
  ipcMain.handle('groups:leave', (_event, groupId: string) => groupService.leave(groupId))
  ipcMain.handle('groups:dissolve', (_event, groupId: string) => groupService.dissolve(groupId))
  ipcMain.handle('contacts:createGroup', (_event, name: string) => groupService.create(name))
  ipcMain.handle('contacts:refresh', async () => {
    const currentUserId = authService.getCurrentUserId()
    const [friendData, groupData] = await Promise.all([
      friendService.loadAndSync(),
      groupService.loadAndSync(currentUserId),
    ])
    return {
      friends: friendData.friends,
      friendRequests: [...friendData.requests, ...groupData.requests],
      groups: groupData.groups,
    }
  })
  ipcMain.handle('notifications:markRead', (_event, id: string) => notificationService.markRead(id))
  ipcMain.on('window:setMode', (event, mode: keyof typeof windowSizes) => {
    const win = BrowserWindow.fromWebContents(event.sender)
    const size = windowSizes[mode] ?? windowSizes.main
    if (!win) return
    minimizedBounds = null
    win.setResizable(mode === 'main')
    win.setMinimumSize(mode === 'main' ? 691 : 0, mode === 'main' ? 540 : 0)
    win.setSize(size.width, size.height)
    const display = screen.getDisplayNearestPoint(win.getBounds())
    win.setPosition(Math.round(display.workArea.x + (display.workArea.width - size.width) / 2), Math.round(display.workArea.y + (display.workArea.height - size.height) / 2))
  })
  ipcMain.handle('window:minimize', (event) => {
    const win = BrowserWindow.fromWebContents(event.sender)
    if (!win || win.isDestroyed()) return false
    if (win.isMinimized()) return true
    if (!win.isMaximized() && !win.isFullScreen()) minimizedBounds = win.getBounds()
    win.minimize()
    return true
  })
  ipcMain.handle('window:toggleMaximize', (event) => {
    const win = BrowserWindow.fromWebContents(event.sender)
    if (!win || win.isDestroyed()) return false
    if (win.isMaximized()) win.unmaximize()
    else win.maximize()
    return true
  })
  ipcMain.handle('window:isMaximized', (event) => BrowserWindow.fromWebContents(event.sender)?.isMaximized() ?? false)
  ipcMain.handle('window:close', (event) => {
    const win = BrowserWindow.fromWebContents(event.sender)
    if (!win || win.isDestroyed()) return false
    // Let the invoke response reach the renderer before logout closes the process.
    setTimeout(() => void logoutAndQuit(), 0)
    return true
  })
}

async function logoutAndQuit() {
  if (quittingAfterLogout || logoutInProgress) return
  logoutInProgress = true
  realtimeService.disconnect()
  try {
    if (authService.hasActiveSession()) await authService.logout()
  } catch {
    // Local credentials must still be discarded and the application must exit.
  } finally {
    databaseManager.close()
    quittingAfterLogout = true
    app.quit()
  }
}

function initializeUserDatabase(user: import('./types').User) {
  databaseManager.openForUser(user.id)
  databaseManager.upsertCurrentUser(user)
}

async function asIpcResult<T>(action: () => Promise<T>): Promise<IpcResult<T>> {
  try {
    return { ok: true, data: await action() }
  } catch (error) {
    return { ok: false, error: error instanceof Error ? error.message : '操作失败' }
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: windowSizes.login.width,
    height: windowSizes.login.height,
    minWidth: 0,
    minHeight: 0,
    center: true,
    resizable: false,
    frame: false,
    transparent: true,
    titleBarStyle: 'hidden',
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  mainWindow.on('ready-to-show', () => {
    mainWindow?.unmaximize()
    mainWindow?.setFullScreen(false)
    mainWindow?.center()
    mainWindow?.show()
  })
  mainWindow.on('close', (event) => {
    if (quittingAfterLogout) return
    event.preventDefault()
    void logoutAndQuit()
  })
  mainWindow.on('restore', () => {
    if (minimizedBounds && mainWindow && !mainWindow.isDestroyed() && !mainWindow.isFullScreen()) {
      mainWindow.setBounds(minimizedBounds, false)
      minimizedBounds = null
    }
  })
  mainWindow.on('maximize', () => mainWindow?.webContents.send('window:maximizedChanged', true))
  mainWindow.on('unmaximize', () => mainWindow?.webContents.send('window:maximizedChanged', false))
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  mainWindow.webContents.session.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(permission === 'media')
  })
  realtimeService.setEventSink((event) => {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('chat:event', event)
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(async () => {
  await authService.startFreshSession()
  registerIpc()
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  realtimeService.disconnect()
  databaseManager.close()
})
