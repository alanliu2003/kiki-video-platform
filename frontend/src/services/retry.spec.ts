import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import { backoffMs, isRetryableStatus, withRetries } from './retry'

describe('retry helpers', () => {
  it('retries transient failures with exponential backoff and stops after max attempts', async () => {
    vi.useFakeTimers()
    let attempts = 0
    const operation = vi.fn(async () => {
      attempts += 1
      if (attempts < 3) {
        throw new ApiError(503, 'UPLOAD_STORAGE_ERROR', 'temporarily down')
      }
      return 'ok'
    })

    const pending = withRetries(operation, 3)
    await vi.runAllTimersAsync()
    await expect(pending).resolves.toBe('ok')
    expect(operation).toHaveBeenCalledTimes(3)
    expect(backoffMs(1)).toBe(200)
    expect(backoffMs(2)).toBe(400)
    vi.useRealTimers()
  })

  it('does not retry permanent 4xx validation failures', async () => {
    const operation = vi.fn(async () => {
      throw new ApiError(400, 'UPLOAD_CHUNK_SIZE_INVALID', 'bad size')
    })
    await expect(withRetries(operation, 3)).rejects.toMatchObject({ code: 'UPLOAD_CHUNK_SIZE_INVALID' })
    expect(operation).toHaveBeenCalledTimes(1)
    expect(isRetryableStatus(400)).toBe(false)
  })
})
