<script setup lang="ts">
import SidebarNav from '../components/SidebarNav.vue'
import ConversationList from '../components/ConversationList.vue'
import ChatPanel from '../components/ChatPanel.vue'
import ContactsView from '../components/ContactsView.vue'
import NotificationsView from '../components/NotificationsView.vue'
import SettingsDialog from '../components/SettingsDialog.vue'
import WindowControls from '../components/WindowControls.vue'
import CallOverlay from '../components/CallOverlay.vue'
import { useAppStore } from '../stores/app'
const appStore = useAppStore()
</script>

<template>
  <main class="workspace">
    <div class="workspace-drag-surface" aria-hidden="true" />
    <WindowControls />
    <SidebarNav />
    <section v-if="appStore.activeModule === 'chat'" class="chat-workspace"><ConversationList /><ChatPanel /></section>
    <ContactsView v-else />
    <el-drawer v-model="appStore.showNotifications" direction="rtl" size="500px" title="通知" class="side-drawer no-drag" destroy-on-close><NotificationsView /></el-drawer>
    <SettingsDialog />
    <CallOverlay />
  </main>
</template>
