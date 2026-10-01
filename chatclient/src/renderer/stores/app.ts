import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { User } from '../types'

export type AppModule = 'chat' | 'contacts'

export const useAppStore = defineStore('app', () => {
  const currentUser = ref<User | null>(null)
  const activeModule = ref<AppModule>('chat')
  const ready = ref(false)
  const showSettings = ref(false)
  const showNotifications = ref(false)
  const isAuthenticated = computed(() => Boolean(currentUser.value))

  function setUser(user: User) { currentUser.value = user }
  function setModule(module: AppModule) { activeModule.value = module }
  function openSettings() { showSettings.value = true }
  function closeSettings() { showSettings.value = false }
  function openNotifications() { showNotifications.value = true }
  function closeNotifications() { showNotifications.value = false }

  return { currentUser, activeModule, ready, showSettings, showNotifications, isAuthenticated, setUser, setModule, openSettings, closeSettings, openNotifications, closeNotifications }
})
