<script setup lang="ts">
import { Bell, ChatDotRound, Setting, User } from '@element-plus/icons-vue'
import { useAppStore } from '../stores/app'
import { useNotificationsStore } from '../stores/notifications'

const appStore = useAppStore()
const notificationsStore = useNotificationsStore()
</script>

<template>
  <aside class="sidebar-nav drag-region">
    <div class="sidebar-top"><img class="self-avatar no-drag" :src="appStore.currentUser?.avatar" alt="avatar" /></div>
    <nav class="sidebar-middle">
      <button class="sidebar-item" :class="{ active: appStore.activeModule === 'chat' }" title="聊天" @click="appStore.setModule('chat')"><el-icon><ChatDotRound /></el-icon></button>
      <button class="sidebar-item" :class="{ active: appStore.activeModule === 'contacts' }" title="通讯录" @click="appStore.setModule('contacts')"><el-icon><User /></el-icon></button>
      <button class="sidebar-item" title="通知" @click="appStore.openNotifications"><el-badge :hidden="notificationsStore.unreadCount === 0" :value="notificationsStore.unreadCount" :max="9"><el-icon><Bell /></el-icon></el-badge></button>
    </nav>
    <div class="sidebar-bottom"><button class="sidebar-item" title="设置" @click="appStore.openSettings"><el-icon><Setting /></el-icon></button></div>
  </aside>
</template>
