export interface DanmakuSocketMessage {
  v?: number
  type: string
  token?: string
  clientMessageId?: string
  content?: string
  videoTimeMs?: number
  danmakuId?: number
  code?: string
  message?: string
  danmaku?: {
    id: number
    videoId: number
    user: { id: number; username: string; displayName: string }
    content: string
    videoTimeMs: number
    style: string
    createdAt: string
  }
}

export const DANMAKU_RECONNECT_MAX_DELAY_MS = 10_000

export interface DanmakuSocket {
  send(content: string, videoTimeMs: number, clientMessageId: string): void
  close(): void
}

export function createDanmakuSocket(options: {
  videoId: number
  token: string | null
  onMessage: (message: DanmakuSocketMessage) => void
}): DanmakuSocket {
  let socket: WebSocket | null = null
  let closed = false
  let delayMs = 1000
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function url(): string {
    const configured = import.meta.env.VITE_WS_BASE_URL as string | undefined
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const base = configured && configured.length > 0 ? configured.replace(/\/$/, '') : `${protocol}//${location.host}`
    return `${base}/ws/videos/${options.videoId}/danmaku`
  }

  function clearReconnect() {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function connect() {
    if (closed) {
      return
    }
    if (socket && (socket.readyState === WebSocket.CONNECTING || socket.readyState === WebSocket.OPEN)) {
      return
    }
    const next = new WebSocket(url())
    socket = next
    next.onopen = () => {
      delayMs = 1000
      if (options.token) {
        next.send(JSON.stringify({ type: 'AUTH', token: options.token }))
      }
    }
    next.onmessage = (event) => {
      try {
        options.onMessage(JSON.parse(String(event.data)) as DanmakuSocketMessage)
      } catch {
        // ignore malformed frames
      }
    }
    next.onerror = () => {
      if (next.readyState === WebSocket.OPEN || next.readyState === WebSocket.CONNECTING) {
        next.close()
      }
    }
    next.onclose = () => {
      if (socket === next) {
        socket = null
      }
      scheduleReconnect()
    }
  }

  function scheduleReconnect() {
    if (closed || reconnectTimer !== null) {
      return
    }
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      delayMs = Math.min(delayMs * 2, DANMAKU_RECONNECT_MAX_DELAY_MS)
      connect()
    }, delayMs)
  }

  function send(content: string, videoTimeMs: number, clientMessageId: string) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return
    }
    socket.send(JSON.stringify({
      type: 'DANMAKU_SEND',
      clientMessageId,
      content,
      videoTimeMs,
    }))
  }

  function close() {
    closed = true
    clearReconnect()
    const current = socket
    socket = null
    current?.close()
  }

  connect()
  return { send, close }
}
