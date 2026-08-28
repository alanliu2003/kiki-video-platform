import { beforeEach, describe, expect, it, vi } from 'vitest'

const { initUploadMock, uploadChunkMock, completeUploadMock } = vi.hoisted(() => ({
  initUploadMock: vi.fn(),
  uploadChunkMock: vi.fn(),
  completeUploadMock: vi.fn(),
}))

vi.mock('../api/uploads', async () => {
  const actual = await vi.importActual<typeof import('../api/uploads')>('../api/uploads')
  return {
    ...actual,
    initUpload: initUploadMock,
    uploadChunk: uploadChunkMock,
    completeUpload: completeUploadMock,
  }
})

import { uploadResumable } from './uploadManager'

describe('uploadResumable', () => {
  beforeEach(() => {
    localStorage.clear()
    initUploadMock.mockReset()
    uploadChunkMock.mockReset()
    completeUploadMock.mockReset()
  })

  it('uploads only missing chunks, reports byte progress, and completes', async () => {
    const file = new File([new Uint8Array([1, 2, 3, 4, 5])], 'demo.mp4', { type: 'video/mp4' })
    initUploadMock.mockResolvedValue({
      data: {
        uploadId: 'u-1',
        chunkSizeBytes: 2,
        totalChunks: 3,
        uploadedChunks: [0],
        deduplicated: false,
        uploadRequired: true,
        mediaObjectId: null,
        status: 'UPLOADING',
        expiresAt: '2026-08-29T00:00:00Z',
      },
    })
    uploadChunkMock.mockResolvedValue({})
    completeUploadMock.mockResolvedValue({
      data: { video: { id: 9, title: 'Demo' }, deduplicated: false },
    })
    const messages: string[] = []

    const result = await uploadResumable({
      file,
      title: 'Demo',
      description: '',
      concurrency: 2,
      onProgress(progress) {
        messages.push(progress.message)
      },
    })

    expect(result.video.id).toBe(9)
    expect(uploadChunkMock).toHaveBeenCalledTimes(2)
    expect(uploadChunkMock.mock.calls.map((call: unknown[]) => call[1])).toEqual(expect.arrayContaining([1, 2]))
    expect(completeUploadMock).toHaveBeenCalledWith('u-1', 'Demo', '')
    expect(messages.some((message) => message.startsWith('Resuming'))).toBe(true)
    expect(messages).toContain('Finalizing...')
    expect(messages).toContain('Complete')
  })

  it('skips chunk uploads when the server reports a physical dedupe', async () => {
    const file = new File([new Uint8Array([9, 8, 7])], 'demo.mp4', { type: 'video/mp4' })
    initUploadMock.mockResolvedValue({
      data: {
        uploadId: 'u-2',
        chunkSizeBytes: 8,
        totalChunks: 1,
        uploadedChunks: [],
        deduplicated: true,
        uploadRequired: false,
        mediaObjectId: 4,
        status: 'INITIATED',
        expiresAt: '2026-08-29T00:00:00Z',
      },
    })
    completeUploadMock.mockResolvedValue({
      data: { video: { id: 11, title: 'Copy' }, deduplicated: true },
    })

    const result = await uploadResumable({
      file,
      title: 'Copy',
      description: 'second',
    })

    expect(result.deduplicated).toBe(true)
    expect(uploadChunkMock).not.toHaveBeenCalled()
    expect(completeUploadMock).toHaveBeenCalledTimes(1)
  })
})
