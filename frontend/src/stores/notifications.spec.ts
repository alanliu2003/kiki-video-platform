import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useNotificationsStore } from './notifications'

const {
  listNotificationsMock,
  getNotificationUnreadCountMock,
  markNotificationReadMock,
  markAllNotificationsReadMock,
} = vi.hoisted(() => ({
  listNotificationsMock: vi.fn(),
  getNotificationUnreadCountMock: vi.fn(),
  markNotificationReadMock: vi.fn(),
  markAllNotificationsReadMock: vi.fn(),
}))

vi.mock('../api/notifications', () => ({
  listNotifications: listNotificationsMock,
  getNotificationUnreadCount: getNotificationUnreadCountMock,
  markNotificationRead: markNotificationReadMock,
  markAllNotificationsRead: markAllNotificationsReadMock,
}))

const sample = {
  id: 7,
  type: 'VIDEO_LIKED',
  read: false,
  createdAt: '2026-08-31T01:00:00Z',
  actor: { id: 2, username: 'bob', displayName: 'Bob' },
  video: { id: 9, title: 'Clip', thumbnailUrl: '/api/videos/9/thumbnail' },
  comment: null,
}

describe('notifications store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
    listNotificationsMock.mockReset()
    getNotificationUnreadCountMock.mockReset()
    markNotificationReadMock.mockReset()
    markAllNotificationsReadMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads inbox items and unread count', async () => {
    listNotificationsMock.mockResolvedValue({
      data: { items: [sample], page: 0, size: 20, total: 1 },
    })
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 1 } })

    const store = useNotificationsStore()
    await store.loadInbox(true)

    expect(store.items).toHaveLength(1)
    expect(store.unreadCount).toBe(1)
    expect(store.hasMore).toBe(false)
  })

  it('marks one and all notifications read', async () => {
    listNotificationsMock.mockResolvedValue({
      data: { items: [sample, { ...sample, id: 8 }], page: 0, size: 20, total: 2 },
    })
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 2 } })
    markNotificationReadMock.mockResolvedValue({ data: { unreadCount: 1 } })
    markAllNotificationsReadMock.mockResolvedValue({ data: { unreadCount: 0 } })

    const store = useNotificationsStore()
    await store.loadInbox(true)
    await store.markRead(7)
    expect(store.items[0].read).toBe(true)
    expect(store.unreadCount).toBe(1)

    await store.markAllRead()
    expect(store.items.every((item) => item.read)).toBe(true)
    expect(store.unreadCount).toBe(0)
  })

  it('records an error and does not keep stale items on a failed reset load', async () => {
    listNotificationsMock.mockRejectedValue(Object.assign(new Error('down'), { name: 'ApiError' }))
    const store = useNotificationsStore()
    await store.loadInbox(true)
    expect(store.error).toBe('down')
    expect(store.items).toEqual([])
  })

  it('polls unread count only after startPolling', async () => {
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 3 } })
    const store = useNotificationsStore()
    expect(getNotificationUnreadCountMock).not.toHaveBeenCalled()

    store.startPolling()
    await Promise.resolve()
    expect(getNotificationUnreadCountMock).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(30_000)
    expect(getNotificationUnreadCountMock).toHaveBeenCalledTimes(2)

    store.stopPolling()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(getNotificationUnreadCountMock).toHaveBeenCalledTimes(2)
    expect(store.unreadCount).toBe(0)
  })
})
