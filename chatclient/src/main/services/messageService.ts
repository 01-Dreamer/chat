import { randomUUID } from 'node:crypto'
import type { FileResource, Message, OpenConversationResult, RedPacket, ServerMessage } from '../types'
import { databaseManager } from '../database/databaseManager'
import { authService } from './authService'
import { realtimeService } from './realtimeService'
import { apiClient } from './apiClient'

interface SendTextInput {
  conversationId: string
  content: string
  replyMessageId?: string | null
}

class MessageService {
  sendText(input: SendTextInput): Message {
    return this.send(input.conversationId, 0, input.content.trim(), undefined, undefined, input.replyMessageId)
  }

  sendFile(conversationId: string, resource: FileResource): Message {
    return this.send(conversationId, 1, resource.fileName, resource)
  }

  sendRedPacket(conversationId: string, packet: RedPacket): Message {
    return this.send(conversationId, 2, packet.message || '恭喜发财，大吉大利', undefined, packet)
  }

  private send(conversationId: string, messageType: number, content: string, resource?: FileResource, packet?: RedPacket, replyMessageId?: string | null): Message {
    const currentUser = authService.getCurrentUser()
    const currentUserId = currentUser.id
    const chat = this.parseChatKey(conversationId, currentUserId)
    const clientMessageId = randomUUID()
    const createdAt = Date.now()
    const message: Message = {
      id: `local:${clientMessageId}`,
      clientMessageId,
      chatKey: conversationId,
      conversationId,
      senderId: currentUserId,
      senderName: currentUser.nickname,
      senderAvatar: databaseManager.getDisplayAvatar('user', currentUserId) ?? currentUser.avatar,
      type: resource?.resourceType === 2 ? 'voice' : messageType === 1 ? 'file' : 'text',
      content,
      referenceId: resource?.id ?? packet?.id ?? null,
      replyMessageId: replyMessageId ?? null,
      amount: packet?.totalAmount,
      fileKind: resource ? this.fileKind(resource.resourceType) : undefined,
      fileName: resource?.fileName,
      fileSize: resource ? this.formatSize(resource.fileSize) : undefined,
      duration: resource?.duration ?? undefined,
      createdAt,
      sendStatus: 'sending',
      status: 0,
    }
    databaseManager.saveOutgoingMessage(message, chat.chatType, chat.targetId)
    try {
      realtimeService.sendMessage({
        clientMessageId,
        chatType: chat.chatType,
        targetId: chat.targetId,
        messageType,
        content: message.content,
        referenceId: message.referenceId,
        replyMessageId: message.replyMessageId,
      })
    } catch {
      databaseManager.markMessageFailed(clientMessageId)
      message.sendStatus = 'failed'
    }
    return message
  }

  private fileKind(resourceType: number) {
    return resourceType === 0 ? 'image' as const : resourceType === 1 ? 'video' as const : 'document' as const
  }

  private formatSize(bytes: number) {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  }

  async openConversation(type: 'direct' | 'group', targetId: string): Promise<OpenConversationResult> {
    const conversation = databaseManager.openConversation(type, targetId)
    if (!conversation) throw new Error('无法创建本地会话')
    databaseManager.markSessionRead(conversation.id)
    return {
      conversation: databaseManager.getConversation(conversation.id) ?? conversation,
      ...databaseManager.loadConversationMessages(conversation.id),
    }
  }

  async recall(messageId: string) {
    if (!/^\d+$/.test(messageId)) throw new Error('消息尚未发送成功，无法撤回')
    const message = await apiClient.post<ServerMessage>(`/messages/${messageId}/recall`)
    return databaseManager.applyRealtimeMessage(message)
  }

  retry(clientMessageId: string) {
    const retry = databaseManager.prepareMessageRetry(clientMessageId)
    try {
      realtimeService.sendMessage(retry.payload)
    } catch {
      databaseManager.markMessageFailed(clientMessageId)
      retry.message.sendStatus = 'failed'
    }
    return retry.message
  }

  private parseChatKey(chatKey: string, currentUserId: string) {
    if (chatKey.startsWith('G:') && /^G:\d+$/.test(chatKey)) {
      return { chatType: 1, targetId: chatKey.substring(2) }
    }
    const match = /^P:(\d+):(\d+)$/.exec(chatKey)
    if (!match || (match[1] !== currentUserId && match[2] !== currentUserId)) {
      throw new Error('会话标识不正确')
    }
    return { chatType: 0, targetId: match[1] === currentUserId ? match[2] : match[1] }
  }
}

export const messageService = new MessageService()
