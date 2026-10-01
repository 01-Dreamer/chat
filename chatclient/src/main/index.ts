import { app, BrowserWindow, ipcMain, screen, type Rectangle } from 'electron'
import { join } from 'node:path'
import { mockService } from './services/mockService'

let mainWindow: BrowserWindow | null = null
let minimizedBounds: Rectangle | null = null

const windowSizes = {
  login: { width: 300, height: 270 },
  register: { width: 300, height: 380 },
  main: { width: 891, height: 691 },
}

if (process.env.CHATCLIENT_DEV === '1') {
  app.commandLine.appendSwitch('disable-gpu')
}

function registerIpc() {
  ipcMain.handle('auth:login', (_event, username: string, password: string) => mockService.login(username, password))
  ipcMain.handle('auth:register', (_event, nickname: string, username: string, password: string) => mockService.register(nickname, username, password))
  ipcMain.handle('app:bootstrap', () => mockService.bootstrap())
  ipcMain.handle('chat:sendMessage', (_event, message) => mockService.sendMessage(message))
  ipcMain.handle('profile:update', (_event, patch) => mockService.updateProfile(patch))
  ipcMain.handle('profile:resetTransferPassword', (_event, oldPassword: string, newPassword: string) => mockService.resetTransferPassword(oldPassword, newPassword))
  ipcMain.handle('contacts:acceptRequest', (_event, id: string) => mockService.acceptFriendRequest(id))
  ipcMain.handle('contacts:rejectRequest', (_event, id: string) => mockService.rejectFriendRequest(id))
  ipcMain.handle('contacts:updateRemark', (_event, kind: 'friend' | 'group', id: string, remark: string) => mockService.updateContactRemark(kind, id, remark))
  ipcMain.handle('contacts:searchFriend', (_event, username: string) => mockService.searchFriendByUsername(username))
  ipcMain.handle('contacts:applyFriend', (_event, username: string, reason: string) => mockService.applyAddFriend(username, reason))
  ipcMain.handle('contacts:searchGroup', (_event, groupNumber: string) => mockService.searchGroupByNumber(groupNumber))
  ipcMain.handle('contacts:applyGroup', (_event, groupNumber: string, reason: string) => mockService.applyJoinGroup(groupNumber, reason))
  ipcMain.handle('contacts:addFriend', (_event, username: string) => mockService.addFriendByUsername(username))
  ipcMain.handle('contacts:joinGroup', (_event, groupNumber: string) => mockService.joinGroupByNumber(groupNumber))
  ipcMain.handle('contacts:createGroup', (_event, name: string) => mockService.createGroup(name))
  ipcMain.handle('notifications:markRead', (_event, id: string) => mockService.markNotificationRead(id))
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
    // Let the invoke response reach the renderer before its webContents is destroyed.
    setTimeout(() => {
      if (!win.isDestroyed()) win.close()
    }, 0)
    return true
  })
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
  mainWindow.on('restore', () => {
    if (minimizedBounds && mainWindow && !mainWindow.isDestroyed() && !mainWindow.isFullScreen()) {
      mainWindow.setBounds(minimizedBounds, false)
      minimizedBounds = null
    }
  })
  mainWindow.on('maximize', () => mainWindow?.webContents.send('window:maximizedChanged', true))
  mainWindow.on('unmaximize', () => mainWindow?.webContents.send('window:maximizedChanged', false))
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))

  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    mainWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

app.whenReady().then(() => {
  registerIpc()
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
