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
  function appendMessage(message: Message) {
    messagesByConversation.value[message.conversationId] ??= []
    messagesByConversation.value[message.conversationId].push(message)
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
  function addConversation(conversation: Conversation) {
    if (!conversations.value.some((item) => item.id === conversation.id)) conversations.value.unshift(conversation)
    messagesByConversation.value[conversation.id] ??= []
  }
  return { conversations, messagesByConversation, currentConversationId, currentConversation, currentMessages, setData, selectConversation, appendMessage, addConversation }
})
