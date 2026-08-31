import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}))

vi.mock('./http', () => ({
  http: {
    get: getMock,
    post: postMock,
  },
}))

import {
  getNotificationUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from './notifications'

describe('notifications API', () => {
  beforeEach(() => {
    getMock.mockReset()
    postMock.mockReset()
  })

  it('lists notifications and unread count', async () => {
    getMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })
    await listNotifications(1, 10)
    expect(getMock).toHaveBeenCalledWith('/notifications', { params: { page: 1, size: 10 } })

    getMock.mockResolvedValue({ data: { unreadCount: 4 } })
    await getNotificationUnreadCount()
    expect(getMock).toHaveBeenCalledWith('/notifications/unread-count')
  })

  it('marks one and all notifications read', async () => {
    postMock.mockResolvedValue({ data: { unreadCount: 0 } })
    await markNotificationRead(12)
    await markAllNotificationsRead()
    expect(postMock).toHaveBeenCalledWith('/notifications/12/read')
    expect(postMock).toHaveBeenCalledWith('/notifications/read-all')
  })
})
