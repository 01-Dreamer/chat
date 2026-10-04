<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from './stores/app'
import { useChatStore } from './stores/chat'
import { useContactsStore } from './stores/contacts'
import { useNotificationsStore } from './stores/notifications'
import { useCallStore } from './stores/call'
import LoginView from './views/LoginView.vue'
import MainLayout from './views/MainLayout.vue'

const appStore = useAppStore()
const chatStore = useChatStore()
const contactsStore = useContactsStore()
const notificationsStore = useNotificationsStore()
const callStore = useCallStore()
const loading = ref(true)
let removeRealtimeListener: (() => void) | undefined
let forcedLogoutPromptVisible = false
const loadingConversationIds = new Set<string>()

interface RealtimeEvent {
  type: 'MESSAGE_UPSERT' | 'MESSAGE_FAILED' | 'CONTACTS_CHANGED' | 'CONNECTION_CHANGED' | 'CALL_EVENT' | 'FORCED_LOGOUT'
  message?: import('./types').Message
  conversation?: import('./types').Conversation | null
  localEcho?: boolean
  clientMessageId?: string
  error?: string
  eventType?: string
  data?: unknown
  reason?: string
}

async function handleRealtimeEvent(value: unknown) {
  const event = value as RealtimeEvent
  if (event.type === 'MESSAGE_UPSERT' && event.message) {
    const isCurrentConversation = appStore.activeModule === 'chat'
      && chatStore.currentConversationId === event.message.conversationId
    const existingUnread = chatStore.conversations.find(
      (item) => item.id === event.message!.conversationId,
    )?.unread ?? 0
    const conversation = event.conversation
      ? {
          ...event.conversation,
          unread: isCurrentConversation
            ? 0
            : event.localEcho
              ? existingUnread
              : event.conversation.unread,
        }
      : null
    chatStore.appendMessage(event.message, conversation)
    if (isCurrentConversation) {
      chatStore.selectConversation(event.message.conversationId)
    }
  } else if (event.type === 'MESSAGE_FAILED' && event.clientMessageId) {
    chatStore.markMessageFailed(event.clientMessageId)
    ElMessage.error(event.error || '消息发送失败')
  } else if (event.type === 'CONTACTS_CHANGED') {
    try {
      const data = await window.chatApi.refreshContacts()
      contactsStore.setData(data.friends, data.friendRequests, data.groups)
    } catch {
      // A later reconnect will retry reliable inbox synchronization.
    }
  } else if (event.type === 'CALL_EVENT' && event.eventType) callStore.receive(event.eventType, event.data)
  else if (event.type === 'FORCED_LOGOUT' && !forcedLogoutPromptVisible) {
    forcedLogoutPromptVisible = true
    const message = event.reason || '你的账号已在另一台设备登录'
    try {
      await ElMessageBox.alert(message, '登录提示', {
        confirmButtonText: '确定',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        type: 'warning',
      })
    } finally {
      await window.chatApi.closeWindow()
    }
  }
}

async function persistVisibleSessionRead() {
  if (appStore.activeModule !== 'chat' || !chatStore.currentConversationId) return
  chatStore.selectConversation(chatStore.currentConversationId)
  try {
    await window.chatApi.setActiveSession(chatStore.currentConversationId)
  } catch {
    // Selecting the conversation remains usable; a later click/message retries persistence.
  }
}

async function hydrateConversationMessages(conversationId: string) {
  if (!conversationId || chatStore.hasLoadedMessages(conversationId) || loadingConversationIds.has(conversationId)) return
  loadingConversationIds.add(conversationId)
  try {
    const messages = await window.chatApi.loadConversationMessages(conversationId)
    chatStore.setConversationMessages(conversationId, messages)
  } catch {
    // Realtime messages remain usable; selecting the session again retries SQLite loading.
  } finally {
    loadingConversationIds.delete(conversationId)
  }
}

async function loadWorkspace(silent = false) {
  try {
    const data = await window.chatApi.bootstrap()
    appStore.setUser(data.user)
    chatStore.replaceConversations(data.conversations)
    await persistVisibleSessionRead()
    contactsStore.setData(data.friends, data.friendRequests, data.groups)
    notificationsStore.setData(data.notifications)
    return true
  } catch (error) {
    if (!silent) ElMessage.error(error instanceof Error ? error.message : '初始化失败')
    return false
  } finally {
    loading.value = false
    appStore.ready = true
  }
}

async function handleAuthenticated() {
  try {
    const local = await window.chatApi.loadLocalChatState()
    chatStore.setData(local.conversations, local.messages)
  } catch {
    chatStore.reset()
  }
  await persistVisibleSessionRead()
  window.chatApi.setWindowMode('main')
  void hydrateConversationMessages(chatStore.currentConversationId)
  void loadWorkspace()
}

watch(
  [() => appStore.activeModule, () => chatStore.currentConversationId],
  ([activeModule, conversationId]) => {
    const activeChatKey = activeModule === 'chat' && conversationId ? conversationId : null
    if (activeChatKey) chatStore.selectConversation(activeChatKey)
    void window.chatApi.setActiveSession(activeChatKey).catch(() => undefined)
    if (activeChatKey) void hydrateConversationMessages(activeChatKey)
  },
  { immediate: true },
)
onMounted(() => {
  removeRealtimeListener = window.chatApi.onRealtimeEvent(handleRealtimeEvent)
  loading.value = false
  appStore.ready = true
  window.chatApi.setWindowMode('login')
})
onBeforeUnmount(() => removeRealtimeListener?.())
</script>

<template>
  <div v-if="loading" class="app-loading"><div class="loading-mark">C</div><span>正在准备你的工作台</span></div>
  <LoginView v-else-if="!appStore.isAuthenticated" @authenticated="handleAuthenticated" />
  <MainLayout v-else />
</template>
