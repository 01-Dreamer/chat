<script setup lang="ts">
import { useNotificationsStore } from '../stores/notifications'
const notificationsStore = useNotificationsStore()
const icons: Record<string, string> = { friend_request: '♧', friend_accepted: '✓', group_joined: '⌘', red_packet: '◈', system: 'i' }
async function markRead(id: string) { await window.chatApi.markNotificationRead(id); notificationsStore.markRead(id) }
</script>

<template>
  <section class="module-page notification-page">
    <div class="notification-main">
      <header class="module-heading notification-heading"><div><h2>通知</h2><span class="muted-label">{{ notificationsStore.unreadCount }} 条未读消息</span></div><button class="text-action" @click="notificationsStore.notifications.forEach((item) => markRead(item.id))">全部已读</button></header>
      <div class="notification-list">
        <button v-for="item in notificationsStore.notifications" :key="item.id" class="notification-item" :class="{ unread: !item.read }" @click="markRead(item.id)"><span class="notification-icon" :class="item.type">{{ icons[item.type] }}</span><span class="notification-copy"><strong>{{ item.title }}</strong><span>{{ item.content }}</span></span><time>{{ item.time }}</time><i v-if="!item.read" class="unread-dot" /></button>
      </div>
    </div>
    <aside class="notification-aside"><div class="notice-art">◒</div><h3>重要消息不再错过</h3><p>好友申请、红包和系统提醒都会在这里集中展示。</p></aside>
  </section>
</template>
