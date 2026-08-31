import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SearchView from './SearchView.vue'

const { searchVideosMock } = vi.hoisted(() => ({
  searchVideosMock: vi.fn(),
}))

vi.mock('../api/search', async () => {
  const actual = await vi.importActual<typeof import('../api/search')>('../api/search')
  return {
    ...actual,
    searchVideos: searchVideosMock,
  }
})

function item(overrides: Record<string, unknown> = {}) {
  return {
    videoId: 12,
    title: 'GTA Trailer',
    descriptionSnippet: 'Official trailer',
    owner: { id: 3, username: 'alice', displayName: 'Alice' },
    createdAt: '2026-08-31T01:00:00Z',
    durationSeconds: 120,
    thumbnailUrl: '/api/videos/12/thumbnail',
    processingStatus: 'READY',
    highlights: {
      title: [
        { text: 'GTA ', highlighted: false },
        { text: 'Trailer', highlighted: true },
      ],
      description: [],
      ownerUsername: [],
      ownerDisplayName: [],
    },
    ...overrides,
  }
}

async function mountSearch(query: Record<string, string>) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/search', name: 'search', component: SearchView },
      { path: '/videos/:id', name: 'video-detail', component: { template: '<div />' } },
    ],
  })
  await router.push({ name: 'search', query })
  await router.isReady()
  return mount(SearchView, {
    global: {
      plugins: [router],
    },
  })
}

describe('SearchView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    searchVideosMock.mockReset()
  })

  it('renders results and links to video detail', async () => {
    searchVideosMock.mockResolvedValue({
      data: { items: [item()], page: 0, size: 20, total: 1, tookMs: 6 },
    })

    const wrapper = await mountSearch({ q: 'trailer' })
    await flushPromises()

    expect(searchVideosMock).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'trailer', page: 0 }),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
    expect(wrapper.text()).toContain('Trailer')
    expect(wrapper.get('a').attributes('href')).toBe('/videos/12')
    expect(wrapper.html()).not.toContain('<em>')
  })

  it('shows an empty state', async () => {
    searchVideosMock.mockResolvedValue({
      data: { items: [], page: 0, size: 20, total: 0, tookMs: 2 },
    })

    const wrapper = await mountSearch({ q: 'nothing' })
    await flushPromises()

    expect(wrapper.text()).toContain('No videos matched that search.')
  })

  it('shows a clear unavailable message', async () => {
    searchVideosMock.mockRejectedValue({
      name: 'ApiError',
      status: 503,
      code: 'SEARCH_UNAVAILABLE',
      message: 'Search is temporarily unavailable',
    })

    const wrapper = await mountSearch({ q: 'trailer' })
    await flushPromises()

    expect(wrapper.text()).toContain('Search is temporarily unavailable.')
  })
})
