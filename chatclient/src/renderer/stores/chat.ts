import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Conversation, Message } from '../types'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const messagesByConversation = ref<Record<string, Message[]>>({})
  const currentConversationId = ref('')
  const currentConversation = computed(() => conversations.value.find((item) => item.id === currentConversationId.value) ?? null)
  const currentMessages = computed(() => messagesByConversation.value[currentConversationId.value] ?? [])

  function setData(items: Conversation[], messages: Record<string, Message[]>) {
    conversations.value = items
    messagesByConversation.value = messages
    currentConversationId.value = items[0]?.id ?? ''
  }
  function selectConversation(id: string) {
    currentConversationId.value = id
    const conversation = conversations.value.find((item) => item.id === id)
    if (conversation) conversation.unread = 0
  }
  function replaceConversations(items: Conversation[]) {
    conversations.value = items
    for (const item of items) messagesByConversation.value[item.id] ??= []
    if (!items.some((item) => item.id === currentConversationId.value)) currentConversationId.value = items[0]?.id ?? ''
  }
  function removeConversation(id: string) {
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (currentConversationId.value === id) currentConversationId.value = conversations.value[0]?.id ?? ''
  }
  function appendMessage(message: Message) {
    messagesByConversation.value[message.conversationId] ??= []
    const messages = messagesByConversation.value[message.conversationId]
    const index = messages.findIndex((item) =>
      item.id === message.id
      || Boolean(message.clientMessageId && item.clientMessageId === message.clientMessageId))
    if (index >= 0) messages[index] = { ...messages[index], ...message }
    else messages.push(message)
    const conversation = conversations.value.find((item) => item.id === message.conversationId)
    if (conversation) {
      conversation.preview = message.type === 'text'
        ? message.content
        : message.type === 'file'
          ? `[${message.fileKind === 'image' ? '图片' : message.fileKind === 'video' ? '视频' : '文件'}] ${message.fileName ?? ''}`.trim()
          : message.type === 'voice'
            ? '[语音]'
            : '[红包]'
      conversation.time = '刚刚'
    }
  }
  function markMessageFailed(clientMessageId: string) {
    for (const messages of Object.values(messagesByConversation.value)) {
      const message = messages.find((item) => item.clientMessageId === clientMessageId)
      if (message) message.sendStatus = 'failed'
    }
  }
  function addConversation(conversation: Conversation) {
    if (!conversations.value.some((item) => item.id === conversation.id)) conversations.value.unshift(conversation)
    messagesByConversation.value[conversation.id] ??= []
  }
  function setConversationMessages(conversationId: string, messages: Message[]) { messagesByConversation.value[conversationId] = messages }
  function reset() {
    conversations.value = []
    messagesByConversation.value = {}
    currentConversationId.value = ''
  }
  return { conversations, messagesByConversation, currentConversationId, currentConversation, currentMessages, setData, selectConversation, replaceConversations, removeConversation, appendMessage, markMessageFailed, addConversation, setConversationMessages, reset }
})
