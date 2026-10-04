import { randomUUID } from 'node:crypto'
import type { Conversation, Message, ServerMessage } from '../types'
import { databaseManager } from '../database/databaseManager'
import { apiClient } from './apiClient'
import { fileService } from './fileService'
import { walletService } from './walletService'

interface RealtimeFrame {
  type: string
  sequence?: number | null
  data?: unknown
  errorCode?: string | null
  errorMessage?: string | null
  clientMessageId?: string | null
}

interface InboxItem {
  sequence: number
  eventType: number
  data: unknown
}

interface InboxBatch {
  items: InboxItem[]
  lastSequence: number
  hasMore: boolean
}

export type RealtimeRendererEvent =
  | { type: 'MESSAGE_UPSERT', message: Message, conversation: Conversation | null, localEcho: boolean }
  | { type: 'MESSAGE_FAILED', clientMessageId: string, error: string }
  | { type: 'CONTACTS_CHANGED' }
  | { type: 'CONNECTION_CHANGED', connected: boolean }
  | { type: 'FORCED_LOGOUT', reason: string }
  | { type: 'CALL_EVENT', eventType: string, data: unknown }

function normalizeWsUrl(value: string | undefined) {
  const configured = value?.trim()
  if (!configured) throw new Error('缺少 WS_URL 环境配置')
  return configured.replace(/\/+$/, '')
}

class RealtimeService {
  private socket: WebSocket | null = null
  private userId = ''
  private readonly deviceId = randomUUID()
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private connectTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private reconnectAttempt = 0
  private lastInboundAt = 0
  private sessionEpoch = 0
  private syncTask: { epoch: number, promise: Promise<void> } | null = null
  private readonly pendingMessages = new Map<string, {
    payload: Record<string, unknown>
    attempts: number
    recoveryAttempts: number
    timer: ReturnType<typeof setTimeout> | null
  }>()
  private sink: (event: RealtimeRendererEvent) => void = () => undefined

  setEventSink(sink: (event: RealtimeRendererEvent) => void) {
    this.sink = sink
  }

  connect(userId: string) {
    if (this.userId && this.userId !== userId) this.disconnect()
    this.userId = userId
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return
    const token = apiClient.getToken()
    if (!token) return
    const url = new URL(normalizeWsUrl(process.env.WS_URL))
    url.searchParams.set(token.tokenName, token.tokenValue)
    url.searchParams.set('deviceId', this.deviceId)
    const socket = new WebSocket(url)
    this.socket = socket
    this.clearConnectTimer()
    this.connectTimer = setTimeout(() => this.resetSocket(socket), 10_000)
    socket.addEventListener('open', () => {
      if (this.socket !== socket) return
      this.clearConnectTimer()
      this.reconnectAttempt = 0
      this.lastInboundAt = Date.now()
      this.startHeartbeat(socket)
      this.sink({ type: 'CONNECTION_CHANGED', connected: true })
      for (const clientMessageId of this.pendingMessages.keys()) this.transmitPending(clientMessageId)
      void this.syncInbox()
    })
    socket.addEventListener('message', (event) => {
      if (this.socket !== socket) return
      this.lastInboundAt = Date.now()
      void this.handleFrame(String(event.data))
    })
    socket.addEventListener('close', () => this.handleSocketClosed(socket))
    socket.addEventListener('error', () => this.resetSocket(socket))
  }

  disconnect() {
    this.sessionEpoch++
    this.userId = ''
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
    this.stopSocketTimers()
    const socket = this.socket
    this.socket = null
    socket?.close()
    for (const pending of this.pendingMessages.values()) if (pending.timer) clearTimeout(pending.timer)
    this.pendingMessages.clear()
    this.sink({ type: 'CONNECTION_CHANGED', connected: false })
  }

  sendMessage(payload: Record<string, unknown>) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error('当前未连接消息服务器')
    }
    const clientMessageId = String(payload.clientMessageId ?? '')
    if (!clientMessageId) throw new Error('消息缺少客户端幂等ID')
    this.pendingMessages.set(clientMessageId, { payload, attempts: 0, recoveryAttempts: 0, timer: null })
    this.transmitPending(clientMessageId)
  }

  sendSignal(payload: Record<string, unknown>) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) throw new Error('当前未连接消息服务器')
    this.socket.send(JSON.stringify({ type: 'SIGNAL', data: payload }))
  }

  private async handleFrame(raw: string) {
    const epoch = this.sessionEpoch
    const userId = this.userId
    let frame: RealtimeFrame
    try {
      frame = JSON.parse(raw) as RealtimeFrame
    } catch {
      return
    }
    if (frame.type === 'PONG' || frame.type === 'CONNECTED') return
    if (frame.type === 'FORCED_LOGOUT') {
      const data = frame.data && typeof frame.data === 'object' ? frame.data as { message?: unknown } : null
      const message = typeof data?.message === 'string' ? data.message : '你的账号已在另一台设备登录'
      this.disconnect()
      this.sink({ type: 'FORCED_LOGOUT', reason: message })
      return
    }
    if ((frame.type === 'MESSAGE' || frame.type === 'MESSAGE_ACK' || frame.type === 'MESSAGE_RECALLED') && this.isServerMessage(frame.data)) {
      const localEcho = this.pendingMessages.has(frame.data.clientMessageId)
      this.clearPending(frame.data.clientMessageId)
      if (!this.isCurrentSession(epoch, userId)) return
      // Persist and render first. Downloading file/voice/red-packet metadata is
      // background work and must never delay the session list or text messages.
      const message = databaseManager.applyRealtimeMessage(frame.data)
      const conversation = databaseManager.getConversation(message.conversationId)
      this.sink({ type: 'MESSAGE_UPSERT', message, conversation, localEcho })
      void this.hydrateMessageResource(frame.data, epoch, userId)
      if (frame.sequence) await this.syncInbox()
      return
    }
    if (frame.type === 'CALL_INVITE' || frame.type === 'CALL_STATUS' || frame.type === 'WEBRTC_SIGNAL') {
      this.sink({ type: 'CALL_EVENT', eventType: frame.type, data: frame.data })
      return
    }
    if (frame.type === 'SEND_FAILED' && frame.clientMessageId) {
      this.clearPending(frame.clientMessageId)
      databaseManager.markMessageFailed(frame.clientMessageId)
      this.sink({
        type: 'MESSAGE_FAILED',
        clientMessageId: frame.clientMessageId,
        error: frame.errorMessage || '消息发送失败',
      })
    }
  }

  private syncInbox() {
    if (!this.userId) return Promise.resolve()
    const epoch = this.sessionEpoch
    if (this.syncTask?.epoch === epoch) return this.syncTask.promise
    const userId = this.userId
    const promise = this.performInboxSync(epoch, userId)
    const task = { epoch, promise }
    this.syncTask = task
    void promise.finally(() => {
      if (this.syncTask === task) this.syncTask = null
    })
    return promise
  }

  private async performInboxSync(epoch: number, userId: string) {
    try {
      let hasMore = true
      while (hasMore && this.isCurrentSession(epoch, userId)) {
        const afterSequence = databaseManager.getLastSequence()
        const batch = await apiClient.get<InboxBatch>(`/sync/inbox?afterSequence=${afterSequence}&limit=100`)
        if (!this.isCurrentSession(epoch, userId)) return
        let contactsChanged = false
        for (const item of batch.items) {
          if (!this.isCurrentSession(epoch, userId)) return
          if ((item.eventType === 0 || item.eventType === 1) && this.isServerMessage(item.data)) {
            const localEcho = this.pendingMessages.has(item.data.clientMessageId)
            this.clearPending(item.data.clientMessageId)
            const message = databaseManager.applyInboxMessage(item.data, item.sequence)
            const conversation = databaseManager.getConversation(message.conversationId)
            this.sink({
              type: 'MESSAGE_UPSERT',
              message,
              conversation,
              localEcho: localEcho || item.data.senderId === userId,
            })
            void this.hydrateMessageResource(item.data, epoch, userId)
          } else {
            databaseManager.advanceLastSequence(item.sequence)
            if (item.eventType >= 2 && item.eventType <= 5) contactsChanged = true
          }
        }
        if (contactsChanged) this.sink({ type: 'CONTACTS_CHANGED' })
        hasMore = batch.hasMore && batch.items.length > 0
      }
    } catch {
      // Heartbeat polling and reconnects retry from the unchanged local sequence.
    }
  }

  private scheduleReconnect() {
    if (!this.userId || this.reconnectTimer) return
    const delay = Math.min(30_000, 1_000 * 2 ** Math.min(this.reconnectAttempt++, 5))
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (this.userId) this.connect(this.userId)
    }, delay)
  }

  private isCurrentSession(epoch: number, userId: string) {
    return this.sessionEpoch === epoch && this.userId === userId
  }

  private clearConnectTimer() {
    if (this.connectTimer) clearTimeout(this.connectTimer)
    this.connectTimer = null
  }

  private stopSocketTimers() {
    this.clearConnectTimer()
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = null
  }

  private startHeartbeat(socket: WebSocket) {
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    this.heartbeatTimer = setInterval(() => {
      if (this.socket !== socket) return
      if (socket.readyState !== WebSocket.OPEN || Date.now() - this.lastInboundAt > 25_000) {
        this.resetSocket(socket)
        return
      }
      try {
        socket.send(JSON.stringify({ type: 'PING' }))
      } catch {
        this.resetSocket(socket)
        return
      }
      // Reliable inbox polling also covers a temporarily unavailable realtime
      // RabbitMQ consumer while the websocket itself is still healthy.
      void this.syncInbox()
    }, 10_000)
  }

  private handleSocketClosed(socket: WebSocket) {
    if (this.socket !== socket) return
    this.socket = null
    this.stopSocketTimers()
    this.sink({ type: 'CONNECTION_CHANGED', connected: false })
    void this.verifySessionBeforeReconnect()
  }

  private resetSocket(socket: WebSocket) {
    if (this.socket !== socket) return
    this.socket = null
    this.stopSocketTimers()
    try { socket.close() } catch { /* The socket is already unusable. */ }
    this.sink({ type: 'CONNECTION_CHANGED', connected: false })
    void this.verifySessionBeforeReconnect()
  }

  private async verifySessionBeforeReconnect() {
    const epoch = this.sessionEpoch
    const userId = this.userId
    if (!userId) return
    try {
      await apiClient.get('/auth/me')
      if (this.isCurrentSession(epoch, userId)) this.scheduleReconnect()
    } catch (error) {
      if (!this.isCurrentSession(epoch, userId)) return
      const message = error instanceof Error ? error.message : ''
      if (message.includes('登录状态已失效') || message.includes('未登录')) {
        this.disconnect()
        this.sink({ type: 'FORCED_LOGOUT', reason: '你的账号已在另一台设备登录' })
      } else {
        this.scheduleReconnect()
      }
    }
  }

  private transmitPending(clientMessageId: string) {
    const pending = this.pendingMessages.get(clientMessageId)
    if (!pending || !this.socket || this.socket.readyState !== WebSocket.OPEN) return
    if (pending.timer) clearTimeout(pending.timer)
    pending.attempts++
    try {
      this.socket.send(JSON.stringify({ type: 'SEND_MESSAGE', data: pending.payload }))
    } catch {
      pending.timer = null
      this.resetSocket(this.socket)
      return
    }
    pending.timer = setTimeout(() => {
      void this.handlePendingTimeout(clientMessageId)
    }, 8_000)
  }

  private async handlePendingTimeout(clientMessageId: string) {
    const pending = this.pendingMessages.get(clientMessageId)
    if (!pending) return
    pending.timer = null
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return
    if (pending.attempts < 3) {
      this.transmitPending(clientMessageId)
      return
    }

    // The command may have been persisted while only the realtime ACK was
    // lost. Check the reliable inbox before declaring the send failed.
    await this.syncInbox()
    const unresolved = this.pendingMessages.get(clientMessageId)
    if (!unresolved) return
    if (unresolved.recoveryAttempts < 2) {
      unresolved.recoveryAttempts++
      unresolved.attempts = 0
      const socket = this.socket
      if (socket) this.resetSocket(socket)
      else this.scheduleReconnect()
      return
    }

    this.pendingMessages.delete(clientMessageId)
    databaseManager.markMessageFailed(clientMessageId)
    this.sink({ type: 'MESSAGE_FAILED', clientMessageId, error: '消息确认超时，请检查网络后重试' })
  }

  private clearPending(clientMessageId: string) {
    const pending = this.pendingMessages.get(clientMessageId)
    if (pending?.timer) clearTimeout(pending.timer)
    this.pendingMessages.delete(clientMessageId)
  }

  private isServerMessage(value: unknown): value is ServerMessage {
    if (!value || typeof value !== 'object') return false
    const item = value as Partial<ServerMessage>
    return typeof item.id === 'string'
      && typeof item.clientMessageId === 'string'
      && typeof item.chatKey === 'string'
  }

  private async prepareMessageResource(message: ServerMessage) {
    if (message.messageType === 2 && message.referenceId && !databaseManager.getRedPacket(message.referenceId)) {
      try {
        databaseManager.upsertRedPacket(await walletService.getRedPacket(message.referenceId))
        return true
      } catch {
        // Red packet metadata can be retried when the user opens it.
        return false
      }
    }
    if (message.messageType !== 1 || !message.referenceId || databaseManager.getFileResource(message.referenceId)) return false
    try {
      const resource = await fileService.get(message.referenceId)
      if (resource.resourceType === 0 || resource.resourceType === 2) await fileService.download(resource)
      return true
    } catch {
      // Resource metadata/download failures do not block reliable message consumption.
      return false
    }
  }

  private async hydrateMessageResource(message: ServerMessage, epoch: number, userId: string) {
    if (!await this.prepareMessageResource(message)) return
    if (!this.isCurrentSession(epoch, userId)) return
    const hydrated = databaseManager.applyRealtimeMessage(message)
    const conversation = databaseManager.getConversation(hydrated.conversationId)
    // This is only a metadata refresh for an already persisted message. It
    // must not affect unread counts in either SQLite or the renderer.
    this.sink({ type: 'MESSAGE_UPSERT', message: hydrated, conversation, localEcho: true })
  }
}

export const realtimeService = new RealtimeService()
