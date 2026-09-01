import { describe, expect, it, vi } from 'vitest'
import { isDeliveryRefreshError, loadWithSingleRetry } from './playbackRefresh'

describe('loadWithSingleRetry', () => {
  it('returns the first successful load', async () => {
    const load = vi.fn().mockResolvedValue({ url: '/a' })
    await expect(loadWithSingleRetry({ load, isRetryable: () => true })).resolves.toEqual({ url: '/a' })
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('retries once on a retryable failure and then succeeds', async () => {
    const load = vi.fn()
      .mockRejectedValueOnce({ status: 403, message: 'expired' })
      .mockResolvedValueOnce({ url: '/b' })
    await expect(loadWithSingleRetry({
      load,
      isRetryable: isDeliveryRefreshError,
    })).resolves.toEqual({ url: '/b' })
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('does not loop when the retry also fails', async () => {
    const load = vi.fn().mockRejectedValue({ status: 403, message: 'expired' })
    await expect(loadWithSingleRetry({
      load,
      isRetryable: isDeliveryRefreshError,
    })).rejects.toMatchObject({ status: 403 })
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('does not retry non-auth failures', async () => {
    const load = vi.fn().mockRejectedValue({ status: 500, message: 'boom' })
    await expect(loadWithSingleRetry({
      load,
      isRetryable: isDeliveryRefreshError,
    })).rejects.toMatchObject({ status: 500 })
    expect(load).toHaveBeenCalledTimes(1)
  })
})
