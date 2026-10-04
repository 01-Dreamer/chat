import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Conversation, Message } from '../types'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const messagesByConversation = ref<Record<string, Message[]>>({})
  const currentConversationId = ref('')
  const loadedConversationIds = new Set<string>()
  const currentConversation = computed(() => conversations.value.find((item) => item.id === currentConversationId.value) ?? null)
  const currentMessages = computed(() => messagesByConversation.value[currentConversationId.value] ?? [])

  function sortConversations() {
    conversations.value.sort((left, right) => {
      const pinnedDifference = Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      if (pinnedDifference !== 0) return pinnedDifference
      return (right.createdTime ?? 0) - (left.createdTime ?? 0)
    })
  }

  function upsertConversation(conversation: Conversation, preserveHigherUnread = false) {
    const index = conversations.value.findIndex((item) => item.id === conversation.id)
    if (index >= 0) {
      const previousUnread = conversations.value[index].unread
      conversations.value[index] = { ...conversations.value[index], ...conversation }
      if (preserveHigherUnread) conversations.value[index].unread = Math.max(previousUnread, conversation.unread)
    }
    else conversations.value.push(conversation)
    messagesByConversation.value[conversation.id] ??= []
    sortConversations()
    // On a fresh login the reliable inbox may recreate the session list after
    // the initial local read. Keep this consistent with setData(): select the
    // first available session so the watcher persists it as read immediately.
    if (!currentConversationId.value && conversations.value.length) {
      currentConversationId.value = conversations.value[0].id
    }
  }

  function setData(items: Conversation[], messages: Record<string, Message[]>) {
    conversations.value = [...items]
    sortConversations()
    messagesByConversation.value = messages
    loadedConversationIds.clear()
    for (const id of Object.keys(messages)) loadedConversationIds.add(id)
    currentConversationId.value = conversations.value[0]?.id ?? ''
  }
  function selectConversation(id: string) {
    currentConversationId.value = id
    const conversation = conversations.value.find((item) => item.id === id)
    if (conversation) conversation.unread = 0
  }
  function replaceConversations(items: Conversation[]) {
    conversations.value = [...items]
    sortConversations()
    for (const item of items) messagesByConversation.value[item.id] ??= []
    if (!items.some((item) => item.id === currentConversationId.value)) currentConversationId.value = conversations.value[0]?.id ?? ''
  }
  function removeConversation(id: string) {
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (currentConversationId.value === id) currentConversationId.value = conversations.value[0]?.id ?? ''
  }
  function appendMessage(message: Message, conversationState?: Conversation | null) {
    messagesByConversation.value[message.conversationId] ??= []
    const messages = messagesByConversation.value[message.conversationId]
    const index = messages.findIndex((item) =>
      item.id === message.id
      || Boolean(message.clientMessageId && item.clientMessageId === message.clientMessageId))
    if (index >= 0) messages[index] = { ...messages[index], ...message }
    else messages.push(message)
    if (conversationState) upsertConversation(conversationState, true)
    const conversation = conversations.value.find((item) => item.id === message.conversationId)
    if (conversation) {
      if (!conversationState) {
        conversation.preview = message.status === 1
          ? '[消息已撤回]'
          : message.type === 'text'
            ? message.content
          : message.type === 'file'
            ? `[${message.fileKind === 'image' ? '图片' : message.fileKind === 'video' ? '视频' : '文件'}] ${message.fileName ?? ''}`.trim()
            : message.type === 'voice'
              ? '[语音]'
              : '[红包]'
        conversation.time = '刚刚'
        conversation.lastActiveTime = Math.max(conversation.lastActiveTime ?? 0, message.createdAt)
      }
      sortConversations()
    }
  }
  function markMessageFailed(clientMessageId: string) {
    for (const messages of Object.values(messagesByConversation.value)) {
      const message = messages.find((item) => item.clientMessageId === clientMessageId)
      if (message) message.sendStatus = 'failed'
    }
  }
  function addConversation(conversation: Conversation) {
    upsertConversation(conversation)
  }
  function setConversationMessages(conversationId: string, messages: Message[]) {
    messagesByConversation.value[conversationId] = messages
    loadedConversationIds.add(conversationId)
  }
  function hasLoadedMessages(conversationId: string) { return loadedConversationIds.has(conversationId) }
  function reset() {
    conversations.value = []
    messagesByConversation.value = {}
    currentConversationId.value = ''
    loadedConversationIds.clear()
  }
  return { conversations, messagesByConversation, currentConversationId, currentConversation, currentMessages, setData, selectConversation, replaceConversations, removeConversation, appendMessage, markMessageFailed, addConversation, setConversationMessages, hasLoadedMessages, reset }
})
