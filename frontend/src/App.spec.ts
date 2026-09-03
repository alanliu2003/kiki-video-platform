import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'
import { useAuthStore } from './stores/auth'

const { getNotificationUnreadCountMock, listNotificationsMock } = vi.hoisted(() => ({
  getNotificationUnreadCountMock: vi.fn(),
  listNotificationsMock: vi.fn(),
}))

vi.mock('./api/notifications', () => ({
  getNotificationUnreadCount: getNotificationUnreadCountMock,
  listNotifications: listNotificationsMock,
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

vi.mock('./api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  getCurrentUser: vi.fn(),
  isApiError: () => false,
}))

vi.mock('./api/health', () => ({
  getHealth: vi.fn().mockResolvedValue({ data: { status: 'ok' } }),
}))

async function mountApp() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<p>Home</p>' } },
      { path: '/login', name: 'login', component: { template: '<p>Login</p>' } },
      { path: '/notifications', name: 'notifications', component: { template: '<p>Inbox</p>' } },
      { path: '/videos/upload', name: 'video-upload', component: { template: '<p>Upload</p>' } },
      { path: '/my/videos', name: 'my-videos', component: { template: '<p>Mine</p>' } },
      { path: '/profile', name: 'profile', component: { template: '<p>Profile</p>' } },
      { path: '/users/:id', name: 'user-profile', component: { template: '<p>Public</p>' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, {
    global: { plugins: [router, pinia] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('App navigation notifications', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    getNotificationUnreadCountMock.mockReset()
    listNotificationsMock.mockReset()
    getNotificationUnreadCountMock.mockResolvedValue({ data: { unreadCount: 2 } })
  })

  it('does not request notifications for anonymous users', async () => {
    const { wrapper } = await mountApp()
    expect(wrapper.text()).not.toContain('Notifications')
    expect(getNotificationUnreadCountMock).not.toHaveBeenCalled()
    expect(listNotificationsMock).not.toHaveBeenCalled()
  })

  it('shows a bell badge for authenticated users and polls unread count', async () => {
    const { wrapper } = await mountApp()
    const auth = useAuthStore()
    auth.accessToken = 'token'
    auth.user = {
      id: 1,
      username: 'alice',
      email: 'alice@example.com',
      displayName: 'Alice',
      role: 'USER',
    }
    await flushPromises()
    expect(wrapper.text()).toContain('Notifications')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.get('.notification-link').attributes('aria-label')).toBe('Notifications, 2 unread')
    expect(wrapper.text()).toContain('Upload')
    expect(wrapper.text()).toContain('My Videos')
    expect(wrapper.text()).toContain('Public profile')
    expect(wrapper.text()).toContain('Account')
    expect(getNotificationUnreadCountMock).toHaveBeenCalled()
    expect(listNotificationsMock).not.toHaveBeenCalled()
  })

  it('shows anonymous navigation without the notification bell', async () => {
    const { wrapper } = await mountApp()
    expect(wrapper.text()).toContain('Home')
    expect(wrapper.text()).toContain('Login')
    expect(wrapper.text()).toContain('Register')
    expect(wrapper.get('nav').attributes('aria-label')).toBe('Main')
    expect(wrapper.get('input[aria-label="Search videos"]').exists()).toBe(true)
  })
})
