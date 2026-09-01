import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationsView from './NotificationsView.vue'
import { useAuthStore } from '../stores/auth'
import { useNotificationsStore } from '../stores/notifications'

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

function item(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    type: 'VIDEO_LIKED',
    read: false,
    createdAt: '2026-08-31T01:00:00Z',
    actor: { id: 5, username: 'alice', displayName: 'Alice' },
    video: { id: 10, title: 'Clip', thumbnailUrl: '/api/videos/10/thumbnail' },
    comment: null,
    ...overrides,
  }
}

function authenticate() {
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = {
    id: 1,
    username: 'owner',
    email: 'owner@example.com',
    displayName: 'Owner',
    role: 'USER',
  }
}

async function mountInbox() {
  const pinia = createPinia()
  setActivePinia(pinia)
  authenticate()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/notifications', name: 'notifications', component: NotificationsView },
      { path: '/videos/:id', name: 'video-detail', component: { template: '<div>Video</div>' } },
      { path: '/users/:id', name: 'user-profile', component: { template: '<div>Profile</div>' } },
    ],
  })
  await router.push('/notifications')
  await router.isReady()
  const wrapper = mount(NotificationsView, {
    global: { plugins: [router, pinia] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('NotificationsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listNotificationsMock.mockReset()
    getNotificationUnreadCountMock.mockReset()
    markNotificationReadMock.mockReset()
    markAllNotificationsReadMock.mockReset()
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 1 } })
  })

  it('shows a loading state then an empty inbox', async () => {
    listNotificationsMock.mockResolvedValue({
      data: { items: [], page: 0, size: 20, total: 0 },
    })
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 0 } })
    const { wrapper } = await mountInbox()
    expect(wrapper.text()).toContain('No notifications yet.')
  })

  it('renders unread and read items without interpreting HTML', async () => {
    listNotificationsMock.mockResolvedValue({
      data: {
        items: [
          item({
            id: 1,
            read: false,
            type: 'VIDEO_COMMENTED',
            comment: { id: 20, contentSnippet: '<img src=x onerror=alert(1)>Nice' },
          }),
          item({ id: 2, read: true, type: 'USER_FOLLOWED', video: null, comment: null }),
        ],
        page: 0,
        size: 20,
        total: 2,
      },
    })
    const { wrapper } = await mountInbox()
    expect(wrapper.text()).toContain('Alice commented on your video "Clip"')
    expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>Nice')
    expect(wrapper.find('img[src="x"]').exists()).toBe(false)
    expect(wrapper.findAll('.notification-item.unread')).toHaveLength(1)
    expect(wrapper.findAll('.notification-item')).toHaveLength(2)
  })

  it('marks a notification read and navigates to the video', async () => {
    listNotificationsMock.mockResolvedValue({
      data: { items: [item()], page: 0, size: 20, total: 1 },
    })
    markNotificationReadMock.mockResolvedValue({ data: { unreadCount: 0 } })
    const { wrapper, router } = await mountInbox()
    const push = vi.spyOn(router, 'push')
    await wrapper.get('.notification-button').trigger('click')
    await flushPromises()
    expect(markNotificationReadMock).toHaveBeenCalledWith(1)
    expect(push).toHaveBeenCalledWith({ name: 'video-detail', params: { id: '10' } })
    expect(useNotificationsStore().unreadCount).toBe(0)
  })

  it('navigates follow notifications to the actor profile', async () => {
    listNotificationsMock.mockResolvedValue({
      data: { items: [item({ type: 'USER_FOLLOWED', video: null })], page: 0, size: 20, total: 1 },
    })
    markNotificationReadMock.mockResolvedValue({ data: { unreadCount: 0 } })
    const { wrapper, router } = await mountInbox()
    const push = vi.spyOn(router, 'push')
    await wrapper.get('.notification-button').trigger('click')
    await flushPromises()
    expect(push).toHaveBeenCalledWith({ name: 'user-profile', params: { id: '5' } })
  })

  it('marks all notifications read', async () => {
    listNotificationsMock.mockResolvedValue({
      data: { items: [item(), item({ id: 2 })], page: 0, size: 20, total: 2 },
    })
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 2 } })
    markAllNotificationsReadMock.mockResolvedValue({ data: { unreadCount: 0 } })
    const { wrapper } = await mountInbox()
    await wrapper.get('.notifications-header button').trigger('click')
    await flushPromises()
    expect(markAllNotificationsReadMock).toHaveBeenCalled()
    expect(wrapper.findAll('.notification-item.unread')).toHaveLength(0)
  })

  it('shows an error state when the inbox fails to load', async () => {
    listNotificationsMock.mockRejectedValue(Object.assign(new Error('Inbox down'), { name: 'ApiError' }))
    const { wrapper } = await mountInbox()
    expect(wrapper.text()).toContain('Inbox down')
  })

  it('loads more pages', async () => {
    listNotificationsMock
      .mockResolvedValueOnce({
        data: { items: [item()], page: 0, size: 20, total: 2 },
      })
      .mockResolvedValueOnce({
        data: { items: [item({ id: 2, type: 'USER_FOLLOWED', video: null })], page: 1, size: 20, total: 2 },
      })
    const { wrapper } = await mountInbox()
    const loadMore = wrapper.findAll('button').find((button) => button.text() === 'Load more')
    expect(loadMore).toBeTruthy()
    await loadMore!.trigger('click')
    await flushPromises()
    expect(listNotificationsMock).toHaveBeenCalledWith(1, 20)
    expect(wrapper.text()).toContain('followed you')
  })
})
