import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { Conversation, Friend, GroupMember, Message, MessagePage } from '../types'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const messagesByConversation = ref<Record<string, Message[]>>({})
  const currentConversationId = ref('')
  const messagePages = ref<Record<string, Pick<MessagePage, 'nextCursor' | 'hasMore'>>>({})
  const loadedConversationIds = new Set<string>()
  const queuedClientMessageIds = new Set<string>()
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
  }

  function setData(items: Conversation[], messages: Record<string, Message[]>) {
    const selectedId = currentConversationId.value
    const liveConversations = conversations.value
    conversations.value = [...items]
    for (const live of liveConversations) {
      const index = conversations.value.findIndex((item) => item.id === live.id)
      if (index < 0) conversations.value.push(live)
      else conversations.value[index] = {
        ...conversations.value[index],
        ...live,
        unread: Math.max(conversations.value[index].unread, live.unread),
      }
    }
    sortConversations()
    const liveMessages = messagesByConversation.value
    messagesByConversation.value = { ...messages }
    for (const [conversationId, items] of Object.entries(liveMessages)) {
      messagesByConversation.value[conversationId] = mergeMessages(
        messagesByConversation.value[conversationId] ?? [],
        items,
      )
    }
    loadedConversationIds.clear()
    messagePages.value = {}
    for (const id of Object.keys(messages)) loadedConversationIds.add(id)
    // Loading local data must not implicitly read the first conversation.
    currentConversationId.value = conversations.value.some((item) => item.id === selectedId)
      ? selectedId
      : ''
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
    if (!items.some((item) => item.id === currentConversationId.value)) currentConversationId.value = ''
  }
  function removeConversation(id: string) {
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (currentConversationId.value === id) currentConversationId.value = ''
  }
  function appendMessage(message: Message, conversationState?: Conversation | null) {
    if (message.clientMessageId && message.sendStatus === 'sending' && queuedClientMessageIds.has(message.clientMessageId)) {
      message = { ...message, sendStatus: 'queued' }
    }
    if (message.clientMessageId && (message.sendStatus === 'success' || message.sendStatus === 'failed')) {
      queuedClientMessageIds.delete(message.clientMessageId)
    }
    messagesByConversation.value[message.conversationId] ??= []
    const messages = messagesByConversation.value[message.conversationId]
    for (const existing of messages) {
      if (existing.senderId !== message.senderId) continue
      existing.senderName = message.senderName
      existing.senderAvatar = message.senderAvatar
    }
    const index = messages.findIndex((item) =>
      item.id === message.id
      || Boolean(message.clientMessageId && item.clientMessageId === message.clientMessageId))
    if (index >= 0) {
      const existing = messages[index]
      // A fast server acknowledgement can reach the renderer before the IPC
      // call that created the optimistic message returns. Never let that stale
      // local "sending" result overwrite an already confirmed server message.
      messages[index] = existing.sendStatus === 'success'
        && (message.sendStatus === 'sending' || message.sendStatus === 'queued')
        ? existing
        : { ...existing, ...message }
    }
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
  function applyFriendProfiles(friends: Friend[]) {
    const byId = new Map(friends.map((friend) => [friend.id, friend]))
    for (const conversation of conversations.value) {
      if (conversation.type !== 'direct') continue
      const friend = byId.get(conversation.targetId)
      if (!friend) continue
      for (const message of messagesByConversation.value[conversation.id] ?? []) {
        if (message.senderId !== friend.id) continue
        message.senderName = friend.nickname
        message.senderAvatar = friend.avatar
      }
    }
  }
  function applyGroupMemberProfiles(groupId: string, members: GroupMember[]) {
    const byId = new Map(members.map((member) => [member.userId, member]))
    for (const conversation of conversations.value) {
      if (conversation.type !== 'group' || conversation.targetId !== groupId) continue
      for (const message of messagesByConversation.value[conversation.id] ?? []) {
        const member = byId.get(message.senderId)
        if (!member) continue
        message.senderName = member.groupNickname || member.nickname
        message.senderAvatar = member.avatar
      }
    }
  }
  function markMessageFailed(clientMessageId: string) {
    queuedClientMessageIds.delete(clientMessageId)
    for (const messages of Object.values(messagesByConversation.value)) {
      const message = messages.find((item) => item.clientMessageId === clientMessageId)
      // A delayed timeout/failure event must not downgrade a message which was
      // already confirmed through WebSocket or reliable inbox synchronization.
      if (message && message.sendStatus !== 'success') message.sendStatus = 'failed'
    }
  }
  function markMessageQueued(clientMessageId: string) {
    queuedClientMessageIds.add(clientMessageId)
    for (const messages of Object.values(messagesByConversation.value)) {
      const message = messages.find((item) => item.clientMessageId === clientMessageId)
      if (message?.sendStatus === 'sending') message.sendStatus = 'queued'
    }
  }
  function removePreviewMessage(senderId: string, clientMessageId: string) {
    for (const [conversationId, messages] of Object.entries(messagesByConversation.value)) {
      const index = messages.findIndex((item) =>
        item.senderId === senderId
        && item.clientMessageId === clientMessageId
        && item.sendStatus === 'queued')
      if (index < 0) continue
      messages.splice(index, 1)
      const conversation = conversations.value.find((item) => item.id === conversationId)
      if (conversation && currentConversationId.value !== conversationId) {
        conversation.unread = Math.max(0, conversation.unread - 1)
      }
    }
  }
  function addConversation(conversation: Conversation) {
    upsertConversation(conversation)
  }
  function mergeMessages(existing: Message[], incoming: Message[]) {
    const merged = [...existing]
    for (const message of incoming) {
      const index = merged.findIndex((item) => item.id === message.id
        || Boolean(message.clientMessageId && item.clientMessageId === message.clientMessageId))
      if (index >= 0) merged[index] = { ...merged[index], ...message }
      else merged.push(message)
    }
    return merged.sort((left, right) => left.createdAt - right.createdAt || left.id.localeCompare(right.id))
  }
  function setConversationMessagePage(conversationId: string, page: MessagePage) {
    messagesByConversation.value[conversationId] = mergeMessages(
      messagesByConversation.value[conversationId] ?? [],
      page.messages,
    )
    messagePages.value[conversationId] = { nextCursor: page.nextCursor, hasMore: page.hasMore }
    loadedConversationIds.add(conversationId)
  }
  function prependConversationMessagePage(conversationId: string, page: MessagePage) {
    messagesByConversation.value[conversationId] = mergeMessages(
      page.messages,
      messagesByConversation.value[conversationId] ?? [],
    )
    messagePages.value[conversationId] = { nextCursor: page.nextCursor, hasMore: page.hasMore }
  }
  function getMessagePageState(conversationId: string) {
    return messagePages.value[conversationId] ?? { nextCursor: null, hasMore: false }
  }
  function hasLoadedMessages(conversationId: string) { return loadedConversationIds.has(conversationId) }
  function reset() {
    conversations.value = []
    messagesByConversation.value = {}
    currentConversationId.value = ''
    loadedConversationIds.clear()
    messagePages.value = {}
    queuedClientMessageIds.clear()
  }
  return { conversations, messagesByConversation, currentConversationId, currentConversation, currentMessages, setData, selectConversation, replaceConversations, removeConversation, appendMessage, applyFriendProfiles, applyGroupMemberProfiles, markMessageQueued, markMessageFailed, removePreviewMessage, addConversation, setConversationMessagePage, prependConversationMessagePage, getMessagePageState, hasLoadedMessages, reset }
})
