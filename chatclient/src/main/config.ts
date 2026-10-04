function requiredValue(value: string | undefined, name: string) {
  const configured = value?.trim()
  if (!configured) throw new Error(`缺少 ${name} 构建配置`)
  return configured.replace(/\/+$/, '')
}

function positiveNumber(value: string | undefined, fallback: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export const clientConfig = Object.freeze({
  apiBaseUrl: requiredValue(import.meta.env.MAIN_VITE_API_BASE_URL, 'MAIN_VITE_API_BASE_URL'),
  wsUrl: requiredValue(import.meta.env.MAIN_VITE_WS_URL, 'MAIN_VITE_WS_URL'),
  httpTimeoutMs: positiveNumber(import.meta.env.MAIN_VITE_HTTP_TIMEOUT_MS, 10_000),
})
