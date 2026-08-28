import { beforeEach, describe, expect, it, vi } from 'vitest'

const { postMock, getMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
}))

vi.mock('./http', () => ({
  http: {
    post: postMock,
    get: getMock,
  },
}))

import { formatFileSize, getMyVideos, getVideo, uploadVideo, videoContentUrl } from './videos'

describe('videos API', () => {
  beforeEach(() => {
    postMock.mockReset()
    getMock.mockReset()
  })

  it('uploads multipart video metadata and file', async () => {
    const file = new File([new Uint8Array([1, 2, 3])], 'demo.mp4', { type: 'video/mp4' })
    postMock.mockResolvedValue({ data: { id: 7, title: 'Demo video' } })

    const response = await uploadVideo({
      title: 'Demo video',
      description: 'First upload',
      file,
    })

    expect(response.data.id).toBe(7)
    expect(postMock).toHaveBeenCalledTimes(1)
    const [path, body, config] = postMock.mock.calls[0]
    expect(path).toBe('/videos')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('title')).toBe('Demo video')
    expect((body as FormData).get('description')).toBe('First upload')
    expect((body as FormData).get('file')).toBe(file)
    expect(config.timeout).toBeGreaterThan(8000)
  })

  it('loads video detail and current-user videos', async () => {
    getMock.mockResolvedValueOnce({ data: { id: 1, title: 'Demo' } })
    getMock.mockResolvedValueOnce({ data: { items: [], page: 0, size: 20, total: 0 } })

    await getVideo(1)
    await getMyVideos()

    expect(getMock).toHaveBeenNthCalledWith(1, '/videos/1')
    expect(getMock).toHaveBeenNthCalledWith(2, '/users/me/videos', { params: { page: 0, size: 20 } })
  })

  it('builds a same-origin playback URL', () => {
    expect(videoContentUrl(12)).toBe('/api/videos/12/content')
  })

  it('formats file sizes', () => {
    expect(formatFileSize(500)).toBe('500 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(2 * 1024 * 1024)).toBe('2.0 MB')
  })
})
