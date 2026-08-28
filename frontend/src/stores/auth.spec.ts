import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './auth'

const { loginMock, registerMock, getCurrentUserMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  registerMock: vi.fn(),
  getCurrentUserMock: vi.fn(),
}))

vi.mock('../api/auth', () => ({
  login: loginMock,
  register: registerMock,
  getCurrentUser: getCurrentUserMock,
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    loginMock.mockReset()
    registerMock.mockReset()
    getCurrentUserMock.mockReset()
  })

  it('stores token and user after login', async () => {
    loginMock.mockResolvedValue({
      data: {
        accessToken: 'token-1',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: {
          id: 1,
          username: 'alice',
          email: 'alice@example.com',
          displayName: 'alice',
          role: 'USER',
        },
      },
    })

    const auth = useAuthStore()
    await auth.login('alice', 'StrongPassword123')

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.accessToken).toBe('token-1')
    expect(auth.user?.username).toBe('alice')
    expect(localStorage.getItem('kiki.accessToken')).toBe('token-1')
  })

  it('clears auth state on logout', async () => {
    loginMock.mockResolvedValue({
      data: {
        accessToken: 'token-1',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: {
          id: 1,
          username: 'alice',
          email: 'alice@example.com',
          displayName: 'alice',
          role: 'USER',
        },
      },
    })

    const auth = useAuthStore()
    await auth.login('alice', 'StrongPassword123')
    auth.logout()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('kiki.accessToken')).toBeNull()
  })

  it('restores a valid stored token', async () => {
    localStorage.setItem('kiki.accessToken', 'stored-token')
    getCurrentUserMock.mockResolvedValue({
      data: {
        id: 1,
        username: 'alice',
        email: 'alice@example.com',
        displayName: 'alice',
        role: 'USER',
      },
    })

    const auth = useAuthStore()
    await auth.initializeAuth()

    expect(auth.isAuthenticated).toBe(true)
    expect(auth.user?.username).toBe('alice')
    expect(auth.initialized).toBe(true)
  })

  it('clears auth when stored token is invalid', async () => {
    localStorage.setItem('kiki.accessToken', 'expired-token')
    getCurrentUserMock.mockRejectedValue(new Error('unauthorized'))

    const auth = useAuthStore()
    await auth.initializeAuth()

    expect(auth.isAuthenticated).toBe(false)
    expect(localStorage.getItem('kiki.accessToken')).toBeNull()
    expect(auth.initialized).toBe(true)
  })
})
