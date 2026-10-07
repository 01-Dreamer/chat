import { app, protocol } from 'electron'
import { readFile } from 'node:fs/promises'
import { extname, join } from 'node:path'

export const AVATAR_PROTOCOL = 'chat-avatar'
export const AVATAR_CACHE_HOST = 'cache'

protocol.registerSchemesAsPrivileged([{
  scheme: AVATAR_PROTOCOL,
  privileges: {
    standard: true,
    secure: true,
    supportFetchAPI: true,
    corsEnabled: true,
  },
}])

export function avatarCacheUrl(fileName: string) {
  return `${AVATAR_PROTOCOL}://${AVATAR_CACHE_HOST}/${encodeURIComponent(fileName)}`
}

export function registerAvatarProtocol() {
  const cacheDirectory = join(app.getPath('userData'), 'avatar-cache')
  protocol.handle(AVATAR_PROTOCOL, async (request) => {
    const url = new URL(request.url)
    const fileName = decodeURIComponent(url.pathname.replace(/^\/+/, ''))
    if (url.hostname !== AVATAR_CACHE_HOST || !/^[a-f\d]{64}\.[a-z\d]{1,8}$/i.test(fileName)) {
      return new Response('Not Found', { status: 404 })
    }
    try {
      const bytes = await readFile(join(cacheDirectory, fileName))
      return new Response(new Uint8Array(bytes), {
        headers: {
          'Content-Type': avatarContentType(fileName),
          'Cache-Control': 'public, max-age=31536000, immutable',
        },
      })
    } catch {
      return new Response('Not Found', { status: 404 })
    }
  })
}

function avatarContentType(fileName: string) {
  switch (extname(fileName).toLowerCase()) {
    case '.png': return 'image/png'
    case '.gif': return 'image/gif'
    case '.webp': return 'image/webp'
    case '.svg': return 'image/svg+xml'
    case '.bmp': return 'image/bmp'
    case '.avif': return 'image/avif'
    case '.jpg':
    case '.jpeg': return 'image/jpeg'
    default: return 'application/octet-stream'
  }
}
