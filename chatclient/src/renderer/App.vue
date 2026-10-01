<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from './stores/app'
import { useChatStore } from './stores/chat'
import { useContactsStore } from './stores/contacts'
import { useNotificationsStore } from './stores/notifications'
import LoginView from './views/LoginView.vue'
import MainLayout from './views/MainLayout.vue'

const appStore = useAppStore()
const chatStore = useChatStore()
const contactsStore = useContactsStore()
const notificationsStore = useNotificationsStore()
const loading = ref(true)

async function loadWorkspace() {
  try {
    const data = await window.chatApi.bootstrap()
    appStore.setUser(data.user)
    chatStore.setData(data.conversations, data.messages)
    contactsStore.setData(data.friends, data.friendRequests, data.groups)
    notificationsStore.setData(data.notifications)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '初始化失败')
  } finally {
    loading.value = false
    appStore.ready = true
  }
}

function handleAuthenticated() {
  window.chatApi.setWindowMode('main')
  loadWorkspace()
}
onMounted(() => {
  window.chatApi.setWindowMode('login')
  loading.value = false
})
</script>

<template>
  <div v-if="loading" class="app-loading"><div class="loading-mark">C</div><span>正在准备你的工作台</span></div>
  <LoginView v-else-if="!appStore.isAuthenticated" @authenticated="handleAuthenticated" />
  <MainLayout v-else />
</template>
