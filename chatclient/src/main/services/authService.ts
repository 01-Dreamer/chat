import type { User } from '../types'
import { apiClient } from './apiClient'
import { app } from 'electron'
import { unlink } from 'node:fs/promises'
import { join } from 'node:path'

export interface ServerUserProfile {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  status: number
  balance: string
  createdTime: number
  updatedTime: number
}

interface AuthSession {
  tokenName: string
  tokenValue: string
  user: ServerUserProfile
}

export function toClientUser(user: ServerUserProfile): User {
  return {
    id: user.id,
    username: user.username,
    nickname: user.nickname,
    avatar: user.avatarUrl,
    balance: user.balance,
    status: user.status,
    createdTime: user.createdTime,
    updatedTime: user.updatedTime,
  }
}

class AuthService {
  private currentUser: User | null = null

  async startFreshSession() {
    apiClient.clearToken()
    this.currentUser = null
    // Remove the encrypted session written by versions that supported automatic login.
    await unlink(this.legacySessionPath()).catch(() => undefined)
  }

  async login(username: string, password: string) {
    const session = await apiClient.post<AuthSession>('/auth/login', { username, password })
    return this.acceptSession(session)
  }

  async register(nickname: string, username: string, password: string) {
    const user = await apiClient.post<ServerUserProfile>('/auth/register', { nickname, username, password })
    return toClientUser(user)
  }

  async refreshCurrentUser() {
    if (!this.currentUser) throw new Error('当前没有已登录会话')
    try {
      const profile = await apiClient.get<ServerUserProfile>('/auth/me')
      this.currentUser = toClientUser(profile)
    } catch (error) {
      const message = error instanceof Error ? error.message : ''
      if (!message.includes('无法连接服务器') && !message.includes('请求超时')) throw error
    }
    return structuredClone(this.currentUser)
  }

  async logout() {
    try {
      await apiClient.post<null>('/auth/logout')
    } finally {
      apiClient.clearToken()
      this.currentUser = null
      await unlink(this.legacySessionPath()).catch(() => undefined)
    }
  }

  updateCurrentUser(user: User) {
    this.currentUser = structuredClone(user)
  }

  getCurrentUserId() {
    if (!this.currentUser) throw new Error('当前没有已登录用户')
    return this.currentUser.id
  }

  getCurrentUser() {
    if (!this.currentUser) throw new Error('当前没有已登录用户')
    return structuredClone(this.currentUser)
  }

  hasActiveSession() {
    return Boolean(this.currentUser && apiClient.getToken())
  }

  private async acceptSession(session: AuthSession) {
    apiClient.setToken(session.tokenName, session.tokenValue)
    this.currentUser = toClientUser(session.user)
    return structuredClone(this.currentUser)
  }

  private legacySessionPath() { return join(app.getPath('userData'), 'session', 'auth.json') }
}

export const authService = new AuthService()
