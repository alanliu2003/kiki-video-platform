import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import UserProfileView from './UserProfileView.vue'

const { getPublicProfileMock, getUserVideosMock, followUserMock, unfollowUserMock } = vi.hoisted(() => ({
  getPublicProfileMock: vi.fn(),
  getUserVideosMock: vi.fn(),
  followUserMock: vi.fn(),
  unfollowUserMock: vi.fn(),
}))

vi.mock('../api/users', () => ({
  getPublicProfile: getPublicProfileMock,
  getUserVideos: getUserVideosMock,
  followUser: followUserMock,
  unfollowUser: unfollowUserMock,
}))

function profile(overrides: Record<string, unknown> = {}) {
  return {
    id: 3,
    username: 'alice',
    displayName: 'Alice',
    createdAt: '2026-08-01T12:00:00Z',
    followerCount: 2,
    followingCount: 1,
    publicVideoCount: 1,
    totalViews: 12,
    ...overrides,
  }
}

function video() {
  return {
    id: 12,
    title: 'City walk',
    owner: { id: 3, username: 'alice', displayName: 'Alice' },
    createdAt: '2026-08-20T10:00:00Z',
    durationSeconds: 42,
    thumbnailUrl: '/api/videos/12/thumbnail',
    processingStatus: 'READY',
    viewCount: 12,
    likeCount: 1,
  }
}

function authenticate(id = 2) {
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = {
    id,
    username: id === 3 ? 'alice' : 'bob',
    email: 'user@example.com',
    displayName: id === 3 ? 'Alice' : 'Bob',
    role: 'USER',
  }
}

async function mountProfile(id = '3', signedIn = false, self = false) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (signedIn) {
    authenticate(self ? 3 : 2)
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/users/:id', name: 'user-profile', component: UserProfileView },
      { path: '/videos/:id', name: 'video-detail', component: { template: '<div />' } },
    ],
  })
  await router.push(`/users/${id}`)
  await router.isReady()
  const wrapper = mount(UserProfileView, {
    global: { plugins: [router, pinia] },
  })
  await flushPromises()
  return wrapper
}

describe('UserProfileView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getPublicProfileMock.mockReset()
    getUserVideosMock.mockReset()
    followUserMock.mockReset()
    unfollowUserMock.mockReset()
  })

  it('renders a public profile and videos', async () => {
    getPublicProfileMock.mockResolvedValue({ data: profile() })
    getUserVideosMock.mockResolvedValue({ data: { items: [video()], page: 0, size: 20, total: 1 } })

    const wrapper = await mountProfile()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('@alice')
    expect(wrapper.text()).toContain('2 followers')
    expect(wrapper.text()).toContain('City walk')
    expect(wrapper.text()).not.toContain('Follow')
    expect(wrapper.text()).not.toContain('alice@example.com')
    expect(wrapper.findAll('a').some((link) => link.attributes('href') === '/videos/12')).toBe(true)
  })

  it('shows follow for signed-in visitors and updates counts', async () => {
    getPublicProfileMock.mockResolvedValue({ data: profile({ followedByCurrentUser: false }) })
    getUserVideosMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })
    followUserMock.mockResolvedValue({ data: { followerCount: 3, followedByCurrentUser: true } })

    const wrapper = await mountProfile('3', true)
    expect(wrapper.text()).toContain('This creator has not uploaded any videos yet.')
    await wrapper.get('button[aria-label="Follow this creator"]').trigger('click')
    await flushPromises()
    expect(followUserMock).toHaveBeenCalledWith(3)
    expect(wrapper.text()).toContain('3 followers')
    expect(wrapper.text()).toContain('Unfollow')
  })

  it('hides follow on self profile', async () => {
    getPublicProfileMock.mockResolvedValue({ data: profile({ followedByCurrentUser: false }) })
    getUserVideosMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })

    const wrapper = await mountProfile('3', true, true)
    expect(wrapper.text()).not.toContain('Follow')
    expect(wrapper.text()).not.toContain('Unfollow')
  })

  it('shows loading then error states', async () => {
    getPublicProfileMock.mockReturnValue(new Promise(() => undefined))
    getUserVideosMock.mockReturnValue(new Promise(() => undefined))
    const loading = await mountProfile()
    expect(loading.text()).toContain('Loading profile')
    loading.unmount()

    getPublicProfileMock.mockRejectedValue(new ApiError(404, 'USER_NOT_FOUND', 'User was not found'))
    getUserVideosMock.mockRejectedValue(new ApiError(404, 'USER_NOT_FOUND', 'User was not found'))
    const missing = await mountProfile()
    expect(missing.text()).toContain('User was not found')
  })

  it('rejects a non-numeric profile id without calling the API', async () => {
    const wrapper = await mountProfile('not-a-user')
    expect(wrapper.text()).toContain('This profile was not found.')
    expect(getPublicProfileMock).not.toHaveBeenCalled()
  })
})
