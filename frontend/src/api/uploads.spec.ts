import { beforeEach, describe, expect, it, vi } from 'vitest'

const { postMock, getMock, putMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
  putMock: vi.fn(),
}))

vi.mock('./http', () => ({
  http: {
    post: postMock,
    get: getMock,
    put: putMock,
  },
}))

import { completeUpload, getUpload, inferVideoContentType, initUpload, uploadChunk } from './uploads'

describe('uploads API', () => {
  beforeEach(() => {
    postMock.mockReset()
    getMock.mockReset()
    putMock.mockReset()
  })

  it('inits, reads status, uploads a chunk, and completes', async () => {
    postMock.mockResolvedValueOnce({ data: { uploadId: 'u-1' } })
    getMock.mockResolvedValueOnce({ data: { uploadId: 'u-1', uploadedChunks: [0] } })
    putMock.mockResolvedValueOnce({})
    postMock.mockResolvedValueOnce({ data: { video: { id: 3 }, deduplicated: false } })

    await initUpload({
      fileName: 'demo.mp4',
      fileSizeBytes: 12,
      contentType: 'video/mp4',
      fileSha256: 'ab'.repeat(32),
    })
    await getUpload('u-1')
    const blob = new Blob([new Uint8Array([1, 2])])
    await uploadChunk('u-1', 0, blob)
    await completeUpload('u-1', 'Demo', 'desc')

    expect(postMock).toHaveBeenNthCalledWith(1, '/uploads/init', expect.any(Object))
    expect(getMock).toHaveBeenCalledWith('/uploads/u-1')
    expect(putMock.mock.calls[0][0]).toBe('/uploads/u-1/chunks/0')
    expect(putMock.mock.calls[0][1]).toBe(blob)
    expect(postMock).toHaveBeenNthCalledWith(
      2,
      '/uploads/u-1/complete',
      { title: 'Demo', description: 'desc' },
      expect.objectContaining({ timeout: expect.any(Number) }),
    )
    expect(inferVideoContentType(new File([], 'clip.webm'))).toBe('video/webm')
  })
})
