import { describe, expect, it } from 'vitest'
import { missingChunks, sliceChunk, totalChunks, uploadedBytes } from './chunks'

describe('chunk helpers', () => {
  it('slices a file without loading the whole file as one blob', () => {
    const bytes = new Uint8Array([1, 2, 3, 4, 5, 6, 7])
    const file = new File([bytes], 'demo.mp4', { type: 'video/mp4' })
    const chunk = sliceChunk(file, 1, 3)
    expect(totalChunks(file.size, 3)).toBe(3)
    expect(chunk.size).toBe(3)
  })

  it('calculates missing chunks and uploaded bytes including a short final chunk', () => {
    expect(missingChunks(5, [0, 1, 3])).toEqual([2, 4])
    expect(uploadedBytes(10, 4, [0, 2])).toBe(6)
  })
})
