import axios, { AxiosError, type AxiosInstance } from 'axios'

interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

class TokenStore {
  private tokenName = ''
  private tokenValue = ''

  set(tokenName: string, tokenValue: string) {
    this.tokenName = tokenName
    this.tokenValue = tokenValue
  }

  clear() {
    this.tokenName = ''
    this.tokenValue = ''
  }

  apply(headers: Record<string, unknown>) {
    if (this.tokenName && this.tokenValue) headers[this.tokenName] = this.tokenValue
  }

  get() {
    return this.tokenName && this.tokenValue
      ? { tokenName: this.tokenName, tokenValue: this.tokenValue }
      : null
  }
}

const tokenStore = new TokenStore()

function getPositiveNumber(value: string | undefined, fallback: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function normalizeBaseUrl(value: string | undefined) {
  const configured = value?.trim()
  if (!configured) throw new Error('缺少 API_BASE_URL 环境配置')
  return configured.replace(/\/+$/, '')
}

class ApiClient {
  private readonly client: AxiosInstance

  constructor() {
    this.client = axios.create({
      baseURL: `${normalizeBaseUrl(process.env.API_BASE_URL)}/api`,
      timeout: getPositiveNumber(process.env.HTTP_TIMEOUT_MS, 10_000),
      headers: { 'Content-Type': 'application/json' },
    })
    this.client.interceptors.request.use((config) => {
      tokenStore.apply(config.headers as unknown as Record<string, unknown>)
      return config
    })
  }

  async get<T>(path: string): Promise<T> {
    return this.unwrap(this.client.get<ApiEnvelope<T>>(path))
  }

  async post<T>(path: string, body?: unknown): Promise<T> {
    return this.unwrap(this.client.post<ApiEnvelope<T>>(path, body))
  }

  async patch<T>(path: string, body?: unknown): Promise<T> {
    return this.unwrap(this.client.patch<ApiEnvelope<T>>(path, body))
  }

  async delete<T>(path: string): Promise<T> {
    return this.unwrap(this.client.delete<ApiEnvelope<T>>(path))
  }

  async postForm<T>(path: string, body: FormData): Promise<T> {
    return this.unwrap(this.client.post<ApiEnvelope<T>>(path, body, {
      headers: { 'Content-Type': undefined },
    }))
  }

  setToken(tokenName: string, tokenValue: string) {
    tokenStore.set(tokenName, tokenValue)
  }

  clearToken() {
    tokenStore.clear()
  }

  getToken() {
    return tokenStore.get()
  }

  private async unwrap<T>(request: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
    try {
      const response = await request
      if (response.data.code !== 'OK') throw new Error(response.data.message || '请求失败')
      return response.data.data
    } catch (error) {
      if (error instanceof AxiosError) {
        const envelope = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined
        if (envelope?.message) throw new Error(envelope.message)
        if (error.code === 'ECONNABORTED') throw new Error('请求超时，请稍后重试')
        if (!error.response) throw new Error('无法连接服务器，请检查网络和服务端状态')
      }
      throw error
    }
  }
}

export const apiClient = new ApiClient()
