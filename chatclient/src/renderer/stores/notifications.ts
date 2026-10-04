import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AppNotification } from '../types'

export const useNotificationsStore = defineStore('notifications', () => {
  const notifications = ref<AppNotification[]>([])
  const unreadCount = computed(() => notifications.value.filter((item) => !item.read).length)
  function setData(items: AppNotification[]) { notifications.value = items }
  function markRead(id: string) {
    const item = notifications.value.find((notification) => notification.id === id)
    if (item) item.read = true
  }
  function reset() { notifications.value = [] }
  return { notifications, unreadCount, setData, markRead, reset }
})
