import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import HomeView from './HomeView.vue'

const { getTrendingMock, getRecentMock } = vi.hoisted(() => ({
  getTrendingMock: vi.fn(),
  getRecentMock: vi.fn(),
}))

vi.mock('../api/discovery', () => ({
  getTrendingVideos: getTrendingMock,
  getRecentVideos: getRecentMock,
}))

vi.mock('../api/health', () => ({
  getHealth: vi.fn().mockResolvedValue({ data: { status: 'UP' } }),
}))

function card(id: number, title: string) {
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
  }
}

async function mountHome() {
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
    global: { plugins: [router, createPinia()] },
  })
}

describe('HomeView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getTrendingMock.mockReset()
    getRecentMock.mockReset()
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
  })
})
