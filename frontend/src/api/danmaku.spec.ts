import { describe, expect, it } from 'vitest'
import { historyWindow } from './danmaku'

describe('danmaku history window', () => {
  it('aligns playback to a 60 second bucket', () => {
    expect(historyWindow(0)).toEqual({ fromMs: 0, toMs: 60_000 })
    expect(historyWindow(42_000)).toEqual({ fromMs: 30_000, toMs: 90_000 })
    expect(historyWindow(300_000)).toEqual({ fromMs: 300_000, toMs: 360_000 })
  })
})
