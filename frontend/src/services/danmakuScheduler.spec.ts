import { describe, expect, it } from 'vitest'
import { createDanmakuScheduler } from './danmakuScheduler'
import type { DanmakuItem } from '../api/danmaku'

function item(id: number, videoTimeMs: number, content = `m${id}`): DanmakuItem {
  return {
    id,
    videoId: 7,
    user: { id: 1, username: 'alice', displayName: 'Alice' },
    content,
    videoTimeMs,
    style: 'NORMAL',
    createdAt: '2026-08-28T08:00:00Z',
  }
}

describe('danmakuScheduler', () => {
  it('dedupes live and historical records by id', () => {
    const scheduler = createDanmakuScheduler()
    expect(scheduler.ingest([item(1, 1000), item(2, 2000)])).toHaveLength(2)
    expect(scheduler.ingest([item(1, 1000), item(3, 3000)])).toHaveLength(1)
    expect(scheduler.has(1)).toBe(true)
  })

  it('spawns only messages in the advancing playback window', () => {
    const scheduler = createDanmakuScheduler()
    scheduler.ingest([item(1, 1000), item(2, 2500), item(3, 4000)])
    expect(scheduler.due(1200, false).map((row) => row.id)).toEqual([1])
    expect(scheduler.due(2600, false).map((row) => row.id)).toEqual([2])
    expect(scheduler.due(2600, true)).toEqual([])
  })

  it('seek does not replay the skipped range', () => {
    const scheduler = createDanmakuScheduler()
    scheduler.ingest([item(1, 20000), item(2, 90000), item(3, 91000)])
    scheduler.due(1000, false)
    scheduler.seek(90000)
    expect(scheduler.due(90000, false).map((row) => row.id)).toEqual([2])
    expect(scheduler.due(92000, false).map((row) => row.id)).toEqual([3])
  })
})
