import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDanmakuStore } from './danmaku'

const { getVideoDanmakuMock, createDanmakuSocketMock, sendMock, closeMock } = vi.hoisted(() => ({
  getVideoDanmakuMock: vi.fn(),
  createDanmakuSocketMock: vi.fn(),
  sendMock: vi.fn(),
  closeMock: vi.fn(),
}))

vi.mock('../api/danmaku', async () => {
  const actual = await vi.importActual<typeof import('../api/danmaku')>('../api/danmaku')
  return {
    ...actual,
    getVideoDanmaku: getVideoDanmakuMock,
  }
})

vi.mock('../services/danmakuSocket', () => ({
  createDanmakuSocket: createDanmakuSocketMock,
}))

function item(id: number, videoTimeMs: number) {
  return {
    id,
    videoId: 9,
    user: { id: 1, username: 'alice', displayName: 'Alice' },
    content: `c${id}`,
    videoTimeMs,
    style: 'NORMAL',
    createdAt: '2026-08-28T08:00:00Z',
  }
}

describe('danmaku store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    getVideoDanmakuMock.mockReset()
    createDanmakuSocketMock.mockReset()
    sendMock.mockReset()
    closeMock.mockReset()
    createDanmakuSocketMock.mockReturnValue({ send: sendMock, close: closeMock })
    getVideoDanmakuMock.mockResolvedValue({ data: [item(1, 1000)] })
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('loads a bounded historical window and dedupes live repeats', async () => {
    const store = useDanmakuStore()
    store.start(9, 'token')
    await store.ensureWindow(12_000)
    expect(getVideoDanmakuMock).toHaveBeenCalledWith(9, 0, 60_000)
    await store.ensureWindow(15_000)
    expect(getVideoDanmakuMock).toHaveBeenCalledTimes(1)

    const onMessage = createDanmakuSocketMock.mock.calls[0][0].onMessage
    onMessage({ type: 'DANMAKU', danmaku: item(1, 1000) })
    store.onTime(1500, false)
    expect(store.visible.map((row) => row.id)).toEqual([1])
  })

  it('clears visible items on seek and hides them when disabled', async () => {
    const store = useDanmakuStore()
    store.start(9, null)
    await store.ensureWindow(0)
    store.onTime(1500, false)
    expect(store.visible).toHaveLength(1)
    getVideoDanmakuMock.mockResolvedValue({ data: [item(2, 91_000)] })
    store.onSeek(90_000)
    expect(store.visible).toHaveLength(0)
    await store.ensureWindow(90_000)
    store.onTime(91_500, false)
    expect(store.visible.map((row) => row.id)).toEqual([2])
    store.setEnabled(false)
    expect(store.visible).toHaveLength(0)
    expect(localStorage.getItem('kiki.danmaku.enabled')).toBe('0')
  })

  it('does not spawn while paused and sends the supplied timestamp', () => {
    const store = useDanmakuStore()
    store.start(9, 'token')
    store.onTime(2000, true)
    expect(store.visible).toHaveLength(0)
    store.send('hello', 4321)
    expect(sendMock).toHaveBeenCalledWith('hello', 4321, expect.any(String))
  })

  it('closes the socket on stop', () => {
    const store = useDanmakuStore()
    store.start(9, 'token')
    store.stop()
    expect(closeMock).toHaveBeenCalledTimes(1)
    expect(store.connectedVideoId).toBeNull()
  })
})
