import type { AppNotification } from '../types'
import { apiClient, isOfflineError } from './apiClient'
import { databaseManager } from '../database/databaseManager'

interface ServerNotification {
  id: string
  notificationType: number
  title: string | null
  content: string | null
  read: boolean
  createdTime: number
}

function mapNotification(item: ServerNotification): AppNotification {
  const types: AppNotification['type'][] = ['friend_request', 'group_joined', 'red_packet', 'system']
  return {
    id: item.id,
    type: types[item.notificationType] ?? 'system',
    title: item.title ?? '通知',
    content: item.content ?? '',
    time: new Date(item.createdTime).toLocaleString('zh-CN', { hour12: false }),
    read: item.read,
  }
}

class NotificationService {
  async list() {
    try {
      const items = (await apiClient.get<ServerNotification[]>('/notifications?limit=100')).map(mapNotification)
      databaseManager.replaceNotifications(items)
      return databaseManager.loadCachedNotifications()
    } catch (error) {
      if (!isOfflineError(error)) throw error
      const cached = databaseManager.loadCachedNotifications()
      return cached
    }
  }
  async markRead(id: string) {
    const item = mapNotification(await apiClient.patch<ServerNotification>(`/notifications/${id}/read`))
    const cached = databaseManager.loadCachedNotifications().map((value) => value.id === id ? item : value)
    databaseManager.replaceNotifications(cached)
    return databaseManager.loadCachedNotifications().find((value) => value.id === id) ?? item
  }
}

export const notificationService = new NotificationService()
