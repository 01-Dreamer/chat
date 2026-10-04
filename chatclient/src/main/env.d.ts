interface ImportMetaEnv {
  readonly MAIN_VITE_API_BASE_URL: string
  readonly MAIN_VITE_WS_URL: string
  readonly MAIN_VITE_HTTP_TIMEOUT_MS: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
