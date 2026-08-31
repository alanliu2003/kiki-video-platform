import { describe, expect, it } from 'vitest'
import { formatDuration, formatViewCount } from './formatters'

describe('formatViewCount', () => {
  it('uses singular and compact labels', () => {
    expect(formatViewCount(0)).toBe('0 views')
    expect(formatViewCount(1)).toBe('1 view')
    expect(formatViewCount(12)).toBe('12 views')
    expect(formatViewCount(1200)).toBe('1.2K views')
    expect(formatViewCount(12_000)).toBe('12K views')
    expect(formatViewCount(1_200_000)).toBe('1.2M views')
    expect(formatViewCount(-3)).toBe('0 views')
  })
})

describe('formatDuration', () => {
  it('formats minutes and hours', () => {
    expect(formatDuration(5)).toBe('0:05')
    expect(formatDuration(62)).toBe('1:02')
    expect(formatDuration(3723)).toBe('1:02:03')
    expect(formatDuration(null)).toBe('')
  })
})
