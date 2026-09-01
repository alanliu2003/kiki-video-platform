export interface PlaybackRefreshOptions<T> {
  load: () => Promise<T>
  isRetryable: (error: unknown) => boolean
}

export async function loadWithSingleRetry<T>(options: PlaybackRefreshOptions<T>): Promise<T> {
  try {
    return await options.load()
  } catch (error) {
    if (!options.isRetryable(error)) {
      throw error
    }
    return options.load()
  }
}

export function isDeliveryRefreshError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }
  const status = 'status' in error ? Number((error as { status?: number }).status) : NaN
  return status === 401 || status === 403 || status === 404
}
