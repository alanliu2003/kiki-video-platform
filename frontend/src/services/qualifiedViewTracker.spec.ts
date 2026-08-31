import { describe, expect, it, vi } from 'vitest'
import { QualifiedViewTracker } from './qualifiedViewTracker'
import { qualifyThresholdMs } from './viewQualification'

function playThrough(tracker: QualifiedViewTracker, fromSec: number, toSec: number, step = 0.25) {
  tracker.onPlay(fromSec)
  for (let t = fromSec + step; t <= toSec + 1e-9; t = Math.round((t + step) * 1000) / 1000) {
    tracker.onTimeUpdate(t, false)
  }
}

describe('qualifyThresholdMs', () => {
  it('uses min(10s, 25% duration)', () => {
    expect(qualifyThresholdMs(null)).toBe(10_000)
    expect(qualifyThresholdMs(60_000)).toBe(10_000)
    expect(qualifyThresholdMs(20_000)).toBe(5_000)
    expect(qualifyThresholdMs(4_000)).toBe(1_000)
  })
})

describe('QualifiedViewTracker', () => {
  it('qualifies only after accumulated playback reaches the threshold', async () => {
    const report = vi.fn().mockResolvedValue(undefined)
    const tracker = new QualifiedViewTracker({
      videoId: 9,
      durationMs: 60_000,
      report,
    })

    playThrough(tracker, 0, 5)
    expect(report).not.toHaveBeenCalled()
    playThrough(tracker, 5, 10)
    await Promise.resolve()
    expect(report).toHaveBeenCalledTimes(1)
    expect(report.mock.calls[0][0].watchedMs).toBeGreaterThanOrEqual(10_000)
    expect(report.mock.calls[0][0].clientViewId).toBe(tracker.clientViewId)
  })

  it('does not accumulate while paused', async () => {
    const report = vi.fn().mockResolvedValue(undefined)
    const tracker = new QualifiedViewTracker({
      videoId: 9,
      durationMs: 8_000,
      report,
    })

    playThrough(tracker, 0, 1)
    tracker.onPause(1)
    tracker.onTimeUpdate(8, true)
    await Promise.resolve()
    expect(tracker.watchedMs).toBe(1000)
    expect(report).not.toHaveBeenCalled()
  })

  it('does not qualify from seeking alone', async () => {
    const report = vi.fn().mockResolvedValue(undefined)
    const tracker = new QualifiedViewTracker({
      videoId: 9,
      durationMs: 60_000,
      report,
    })

    tracker.onPlay(0)
    tracker.onSeeking()
    tracker.onTimeUpdate(30, false)
    tracker.onSeeked(30)
    tracker.onTimeUpdate(30.2, false)
    await Promise.resolve()
    expect(tracker.watchedMs).toBeLessThan(10_000)
    expect(report).not.toHaveBeenCalled()
  })

  it('sends only one qualification POST per viewing session', async () => {
    const report = vi.fn().mockResolvedValue(undefined)
    const tracker = new QualifiedViewTracker({
      videoId: 9,
      durationMs: 20_000,
      report,
    })

    playThrough(tracker, 0, 5)
    playThrough(tracker, 5, 10)
    playThrough(tracker, 10, 15)
    await Promise.resolve()
    await Promise.resolve()
    expect(report).toHaveBeenCalledTimes(1)
  })

  it('retries the same clientViewId after a failed report', async () => {
    const report = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(undefined)
    const tracker = new QualifiedViewTracker({
      videoId: 9,
      durationMs: 20_000,
      report,
    })

    playThrough(tracker, 0, 5)
    await Promise.resolve()
    expect(report).toHaveBeenCalledTimes(1)
    playThrough(tracker, 5, 6)
    await Promise.resolve()
    expect(report).toHaveBeenCalledTimes(2)
    expect(report.mock.calls[0][0].clientViewId).toBe(report.mock.calls[1][0].clientViewId)
  })
})
