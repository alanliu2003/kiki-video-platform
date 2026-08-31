import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'
import HomeView from './HomeView.vue'

const { getTrendingMock, getRecentMock, getRecommendedMock } = vi.hoisted(() => ({
  getTrendingMock: vi.fn(),
  getRecentMock: vi.fn(),
  getRecommendedMock: vi.fn(),
}))

vi.mock('../api/discovery', () => ({
  getTrendingVideos: getTrendingMock,
  getRecentVideos: getRecentMock,
  getRecommendedVideos: getRecommendedMock,
}))

vi.mock('../api/health', () => ({
  getHealth: vi.fn().mockResolvedValue({ data: { status: 'UP' } }),
}))

function card(id: number, title: string, reason?: string) {
  return {
    id,
    title,
    owner: { id: 1, username: 'alice', displayName: 'Alice' },
    createdAt: '2026-08-31T01:00:00Z',
    durationSeconds: 90,
    thumbnailUrl: `/api/videos/${id}/thumbnail`,
    processingStatus: 'READY',
    viewCount: 1200,
    likeCount: 3,
    recommendationReason: reason ?? null,
  }
}

function authenticate() {
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = {
    id: 2,
    username: 'bob',
    email: 'bob@example.com',
    displayName: 'Bob',
    role: 'USER',
  }
}

async function mountHome(signedIn = false) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (signedIn) {
    authenticate()
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: HomeView },
      { path: '/videos/:id', name: 'video-detail', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  return mount(HomeView, {
    global: { plugins: [router, pinia] },
  })
}

describe('HomeView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getTrendingMock.mockReset()
    getRecentMock.mockReset()
    getRecommendedMock.mockReset()
  })

  it('renders trending and new upload sections', async () => {
    getTrendingMock.mockResolvedValue({ data: { items: [card(1, 'Hot clip')], page: 0, size: 20, total: 1 } })
    getRecentMock.mockResolvedValue({ data: { items: [card(2, 'Just uploaded')], page: 0, size: 20, total: 1 } })

    const wrapper = await mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('Trending')
    expect(wrapper.text()).toContain('New uploads')
    expect(wrapper.text()).toContain('Hot clip')
    expect(wrapper.text()).toContain('Just uploaded')
    expect(wrapper.text()).toContain('1.2K views')
    expect(wrapper.get('a').attributes('href')).toBe('/videos/1')
    expect(wrapper.text()).not.toContain('Recommended for you')
    expect(getRecommendedMock).not.toHaveBeenCalled()
  })

  it('requests recommendations for authenticated users and shows reasons', async () => {
    getTrendingMock.mockResolvedValue({ data: { items: [card(1, 'Hot clip')], page: 0, size: 20, total: 1 } })
    getRecentMock.mockResolvedValue({ data: { items: [card(2, 'Just uploaded')], page: 0, size: 20, total: 1 } })
    getRecommendedMock.mockResolvedValue({
      data: {
        items: [card(9, 'For you', 'Because you follow this creator')],
        page: 0,
        size: 20,
        total: 1,
        coldStart: false,
      },
    })

    const wrapper = await mountHome(true)
    await flushPromises()

    expect(getRecommendedMock).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Recommended for you')
    expect(wrapper.text()).toContain('For you')
    expect(wrapper.text()).toContain('Because you follow this creator')
    expect(wrapper.text()).toContain('Hot clip')
    expect(wrapper.findAll('a').some((link) => link.attributes('href') === '/videos/9')).toBe(true)
  })

  it('keeps trending and recent when recommendations fail', async () => {
    getTrendingMock.mockResolvedValue({ data: { items: [card(1, 'Still trending')], page: 0, size: 20, total: 1 } })
    getRecentMock.mockResolvedValue({ data: { items: [card(2, 'Still recent')], page: 0, size: 20, total: 1 } })
    getRecommendedMock.mockRejectedValue(new ApiError(503, 'INTERNAL_ERROR', 'recs failed'))

    const wrapper = await mountHome(true)
    await flushPromises()

    expect(wrapper.text()).toContain('recs failed')
    expect(wrapper.text()).toContain('Still trending')
    expect(wrapper.text()).toContain('Still recent')
  })

  it('shows cold-start copy when the API says so', async () => {
    getTrendingMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })
    getRecentMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })
    getRecommendedMock.mockResolvedValue({
      data: { items: [card(3, 'Popular fallback', 'Trending now')], page: 0, size: 20, total: 1, coldStart: true },
    })

    const wrapper = await mountHome(true)
    await flushPromises()

    expect(wrapper.text()).toContain('Not enough activity yet')
    expect(wrapper.text()).toContain('Popular fallback')
    expect(wrapper.text()).toContain('Trending now')
  })

  it('shows loading then empty states', async () => {
    getTrendingMock.mockReturnValue(new Promise(() => undefined))
    getRecentMock.mockReturnValue(new Promise(() => undefined))
    const loading = await mountHome()
    expect(loading.text()).toContain('Loading trending videos')
    expect(loading.text()).toContain('Loading new uploads')
    loading.unmount()

    getTrendingMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })
    getRecentMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0 } })
    const empty = await mountHome()
    await flushPromises()
    expect(empty.text()).toContain('No trending videos yet.')
    expect(empty.text()).toContain('No uploads yet.')
  })

  it('shows backend error states independently', async () => {
    getTrendingMock.mockRejectedValue(new ApiError(503, 'INTERNAL_ERROR', 'trending failed'))
    getRecentMock.mockRejectedValue(new ApiError(503, 'INTERNAL_ERROR', 'recent failed'))

    const wrapper = await mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('trending failed')
    expect(wrapper.text()).toContain('recent failed')
    expect(getRecommendedMock).not.toHaveBeenCalled()
  })
})
