import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}))

vi.mock('./http', () => ({
  http: {
    get: getMock,
  },
}))

import { searchVideos } from './search'

describe('search API', () => {
  beforeEach(() => {
    getMock.mockReset()
  })

  it('requests videos through the Spring search API', async () => {
    getMock.mockResolvedValue({ data: { items: [], page: 0, size: 20, total: 0, tookMs: 4 } })
    const controller = new AbortController()

    await searchVideos({ q: 'trailer', page: 1, sort: 'NEWEST' }, { signal: controller.signal })

    expect(getMock).toHaveBeenCalledWith('/search/videos', {
      signal: controller.signal,
      params: {
        q: 'trailer',
        page: 1,
        size: undefined,
        sort: 'NEWEST',
        ownerId: undefined,
        processingStatus: undefined,
        createdAfter: undefined,
        createdBefore: undefined,
      },
    })
  })
})
