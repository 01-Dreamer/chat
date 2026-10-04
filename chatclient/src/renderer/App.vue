<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
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

interface RealtimeEvent {
  type: 'MESSAGE_UPSERT' | 'MESSAGE_FAILED' | 'CONTACTS_CHANGED' | 'CONNECTION_CHANGED' | 'CALL_EVENT'
  message?: import('./types').Message
  clientMessageId?: string
  error?: string
  eventType?: string
  data?: unknown
}

async function handleRealtimeEvent(value: unknown) {
  const event = value as RealtimeEvent
  if (event.type === 'MESSAGE_UPSERT' && event.message) {
    chatStore.appendMessage(event.message)
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
}

async function loadWorkspace(silent = false) {
  try {
    const data = await window.chatApi.bootstrap()
    appStore.setUser(data.user)
    chatStore.setData(data.conversations, data.messages)
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
  window.chatApi.setWindowMode('main')
  void loadWorkspace()
}
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
