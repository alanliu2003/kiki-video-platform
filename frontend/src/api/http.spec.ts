import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, requestIdFromHeaders } from './http'

describe('http request IDs', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('reads X-Request-ID from response headers', () => {
    expect(requestIdFromHeaders({ 'x-request-id': 'abc-123-def' })).toBe('abc-123-def')
    expect(requestIdFromHeaders({ 'X-Request-ID': ['550e8400-e29b-41d4-a716-446655440000'] }))
      .toBe('550e8400-e29b-41d4-a716-446655440000')
    expect(requestIdFromHeaders({})).toBeUndefined()
    expect(requestIdFromHeaders(null)).toBeUndefined()
  })

  it('keeps request IDs on API errors without logging secrets', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const error = new ApiError(503, 'SEARCH_UNAVAILABLE', 'Search is temporarily unavailable', 'req-local-1')
    expect(error.requestId).toBe('req-local-1')
    expect(error.code).toBe('SEARCH_UNAVAILABLE')
    expect(warn).not.toHaveBeenCalled()
  })
})
