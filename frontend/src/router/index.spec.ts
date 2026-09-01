import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { authGuard } from './index'

vi.mock('../api/auth', () => ({
  login: vi.fn(),
  register: vi.fn(),
  getCurrentUser: vi.fn(),
}))

describe('route protection', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('redirects unauthenticated users from profile to login', async () => {
    const result = await authGuard({
      name: 'profile',
      fullPath: '/profile',
      meta: { requiresAuth: true },
    })

    expect(result).toEqual({ name: 'login', query: { redirect: '/profile' } })
  })

  it('redirects unauthenticated users from upload to login', async () => {
    const result = await authGuard({
      name: 'video-upload',
      fullPath: '/videos/upload',
      meta: { requiresAuth: true },
    })

    expect(result).toEqual({ name: 'login', query: { redirect: '/videos/upload' } })
  })

  it('redirects unauthenticated users from notifications to login', async () => {
    const result = await authGuard({
      name: 'notifications',
      fullPath: '/notifications',
      meta: { requiresAuth: true },
    })

    expect(result).toEqual({ name: 'login', query: { redirect: '/notifications' } })
  })

  it('redirects unauthenticated users from my videos to login', async () => {
    const result = await authGuard({
      name: 'my-videos',
      fullPath: '/my/videos',
      meta: { requiresAuth: true },
    })

    expect(result).toEqual({ name: 'login', query: { redirect: '/my/videos' } })
  })

  it('allows unauthenticated users to open search', async () => {
    const result = await authGuard({
      name: 'search',
      fullPath: '/search?q=trailer',
      meta: {},
    })

    expect(result).toBe(true)
  })

  it('allows unauthenticated users to open a public video detail page', async () => {
    const result = await authGuard({
      name: 'video-detail',
      fullPath: '/videos/1',
      meta: {},
    })

    expect(result).toBe(true)
  })

  it('allows authenticated users to open profile', async () => {
    const auth = useAuthStore()
    auth.accessToken = 'token-1'
    auth.user = {
      id: 1,
      username: 'alice',
      email: 'alice@example.com',
      displayName: 'alice',
      role: 'USER',
    }
    auth.initialized = true

    const result = await authGuard({
      name: 'profile',
      fullPath: '/profile',
      meta: { requiresAuth: true },
    })

    expect(result).toBe(true)
  })
})
