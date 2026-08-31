import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDanmakuSocket } from './danmakuSocket'

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  url: string
  readyState = 0
  sent: string[] = []
  onopen: ((event?: unknown) => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.readyState = 3
    this.onclose?.()
  }

  open() {
    this.readyState = 1
    this.onopen?.()
  }
}

describe('danmakuSocket', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    FakeWebSocket.instances = []
  })

  function install() {
    vi.stubGlobal('WebSocket', FakeWebSocket)
    Object.assign(FakeWebSocket, { CONNECTING: 0, OPEN: 1, CLOSING: 2, CLOSED: 3 })
  }

  it('authenticates on connect and sends the current timestamp', () => {
    install()
    const onMessage = vi.fn()
    const socket = createDanmakuSocket({
      videoId: 9,
      token: 'access-token',
      onMessage,
    })
    const ws = FakeWebSocket.instances[0]
    expect(ws.url).toContain('/ws/videos/9/danmaku')
    ws.open()
    expect(JSON.parse(ws.sent[0])).toEqual({ type: 'AUTH', token: 'access-token' })
    socket.send('hello', 12345, 'cid-1')
    expect(JSON.parse(ws.sent[1])).toEqual({
      type: 'DANMAKU_SEND',
      clientMessageId: 'cid-1',
      content: 'hello',
      videoTimeMs: 12345,
    })
    socket.close()
  })

  it('reconnects with backoff and does not keep two sockets', () => {
    install()
    vi.useFakeTimers()
    const socket = createDanmakuSocket({
      videoId: 4,
      token: null,
      onMessage: vi.fn(),
    })
    expect(FakeWebSocket.instances).toHaveLength(1)
    FakeWebSocket.instances[0].open()
    FakeWebSocket.instances[0].close()
    expect(FakeWebSocket.instances).toHaveLength(1)
    vi.advanceTimersByTime(1000)
    expect(FakeWebSocket.instances).toHaveLength(2)
    FakeWebSocket.instances[1].close()
    vi.advanceTimersByTime(1000)
    expect(FakeWebSocket.instances).toHaveLength(2)
    vi.advanceTimersByTime(1000)
    expect(FakeWebSocket.instances).toHaveLength(3)
    socket.close()
    const before = FakeWebSocket.instances.length
    vi.advanceTimersByTime(20_000)
    expect(FakeWebSocket.instances).toHaveLength(before)
  })

  it('does not send AUTH when the viewer is anonymous', () => {
    install()
    createDanmakuSocket({ videoId: 1, token: null, onMessage: vi.fn() }).close()
    FakeWebSocket.instances[0].open()
    expect(FakeWebSocket.instances[0].sent).toEqual([])
  })
})
