import { ApiError } from '../api/http'

export function isRetryableStatus(status: number): boolean {
  return status === 0 || status === 408 || status === 429 || status >= 500
}

export function backoffMs(attempt: number): number {
  return 200 * 2 ** (attempt - 1)
}

export async function withRetries<T>(fn: () => Promise<T>, maxAttempts = 3): Promise<T> {
  let lastError: unknown
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      const retryable = !(error instanceof ApiError) || isRetryableStatus(error.status)
      if (!retryable || attempt === maxAttempts) {
        throw error
      }
      await sleep(backoffMs(attempt))
    }
  }
  throw lastError
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}
