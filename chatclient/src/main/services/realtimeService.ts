import { randomUUID } from 'node:crypto'
import type { Message, ServerMessage } from '../types'
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
  | { type: 'MESSAGE_UPSERT', message: Message }
  | { type: 'MESSAGE_FAILED', clientMessageId: string, error: string }
  | { type: 'CONTACTS_CHANGED' }
  | { type: 'CONNECTION_CHANGED', connected: boolean }
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
  private reconnectAttempt = 0
  private syncRunning = false
  private readonly pendingMessages = new Map<string, { payload: Record<string, unknown>, attempts: number, timer: ReturnType<typeof setTimeout> | null }>()
  private sink: (event: RealtimeRendererEvent) => void = () => undefined

  setEventSink(sink: (event: RealtimeRendererEvent) => void) {
    this.sink = sink
  }

  connect(userId: string) {
    this.userId = userId
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) return
    const token = apiClient.getToken()
    if (!token) return
    const url = new URL(normalizeWsUrl(process.env.WS_URL))
    url.searchParams.set(token.tokenName, token.tokenValue)
    url.searchParams.set('deviceId', this.deviceId)
    const socket = new WebSocket(url)
    this.socket = socket
    socket.addEventListener('open', () => {
      if (this.socket !== socket) return
      this.reconnectAttempt = 0
      this.sink({ type: 'CONNECTION_CHANGED', connected: true })
      for (const clientMessageId of this.pendingMessages.keys()) this.transmitPending(clientMessageId)
      void this.syncInbox()
    })
    socket.addEventListener('message', (event) => {
      if (this.socket !== socket) return
      void this.handleFrame(String(event.data))
    })
    socket.addEventListener('close', () => {
      if (this.socket !== socket) return
      this.socket = null
      this.sink({ type: 'CONNECTION_CHANGED', connected: false })
      this.scheduleReconnect()
    })
    socket.addEventListener('error', () => socket.close())
  }

  disconnect() {
    this.userId = ''
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
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
    this.pendingMessages.set(clientMessageId, { payload, attempts: 0, timer: null })
    this.transmitPending(clientMessageId)
  }

  sendSignal(payload: Record<string, unknown>) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) throw new Error('当前未连接消息服务器')
    this.socket.send(JSON.stringify({ type: 'SIGNAL', data: payload }))
  }

  private async handleFrame(raw: string) {
    let frame: RealtimeFrame
    try {
      frame = JSON.parse(raw) as RealtimeFrame
    } catch {
      return
    }
    if ((frame.type === 'MESSAGE' || frame.type === 'MESSAGE_ACK' || frame.type === 'MESSAGE_RECALLED') && this.isServerMessage(frame.data)) {
      this.clearPending(frame.data.clientMessageId)
      await this.prepareMessageResource(frame.data)
      const message = databaseManager.applyRealtimeMessage(frame.data)
      this.sink({ type: 'MESSAGE_UPSERT', message })
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

  private async syncInbox() {
    if (this.syncRunning || !this.userId) return
    this.syncRunning = true
    try {
      let hasMore = true
      while (hasMore && this.userId) {
        const afterSequence = databaseManager.getLastSequence()
        const batch = await apiClient.get<InboxBatch>(`/sync/inbox?afterSequence=${afterSequence}&limit=100`)
        for (const item of batch.items) {
          if ((item.eventType === 0 || item.eventType === 1) && this.isServerMessage(item.data)) {
            await this.prepareMessageResource(item.data)
            const message = databaseManager.applyInboxMessage(item.data, item.sequence)
            this.sink({ type: 'MESSAGE_UPSERT', message })
          } else {
            databaseManager.advanceLastSequence(item.sequence)
            if (item.eventType >= 2 && item.eventType <= 5) this.sink({ type: 'CONTACTS_CHANGED' })
          }
        }
        hasMore = batch.hasMore && batch.items.length > 0
      }
    } catch {
      // The next reconnect or realtime event retries from the unchanged local sequence.
    } finally {
      this.syncRunning = false
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

  private transmitPending(clientMessageId: string) {
    const pending = this.pendingMessages.get(clientMessageId)
    if (!pending || !this.socket || this.socket.readyState !== WebSocket.OPEN) return
    if (pending.timer) clearTimeout(pending.timer)
    pending.attempts++
    this.socket.send(JSON.stringify({ type: 'SEND_MESSAGE', data: pending.payload }))
    pending.timer = setTimeout(() => {
      const current = this.pendingMessages.get(clientMessageId)
      if (!current) return
      if (current.attempts < 3 && this.socket?.readyState === WebSocket.OPEN) {
        this.transmitPending(clientMessageId)
        return
      }
      if (this.socket?.readyState !== WebSocket.OPEN) {
        current.timer = null
        return
      }
      this.pendingMessages.delete(clientMessageId)
      databaseManager.markMessageFailed(clientMessageId)
      this.sink({ type: 'MESSAGE_FAILED', clientMessageId, error: '消息确认超时，请稍后重试' })
    }, 8_000)
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
      } catch {
        // Red packet metadata can be retried when the user opens it.
      }
      return
    }
    if (message.messageType !== 1 || !message.referenceId || databaseManager.getFileResource(message.referenceId)) return
    try {
      const resource = await fileService.get(message.referenceId)
      if (resource.resourceType === 0 || resource.resourceType === 2) await fileService.download(resource)
    } catch {
      // Resource metadata/download failures do not block reliable message consumption.
    }
  }
}

export const realtimeService = new RealtimeService()
