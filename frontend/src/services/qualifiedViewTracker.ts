import { qualifyThresholdMs } from './viewQualification'

const SEEK_JUMP_SECONDS = 1.5

export interface QualifyViewPayload {
  watchedMs: number
  durationMs: number | null
  clientViewId: string
}

export interface QualifiedViewTrackerOptions {
  videoId: number
  durationMs?: number | null
  report: (payload: QualifyViewPayload) => Promise<void>
  now?: () => number
}

export class QualifiedViewTracker {
  readonly videoId: number
  readonly clientViewId: string
  watchedMs = 0

  private durationMs: number | null
  private lastCurrentTime: number | null = null
  private seeking = false
  private reported = false
  private reporting = false
  private readonly report: (payload: QualifyViewPayload) => Promise<void>

  constructor(options: QualifiedViewTrackerOptions) {
    this.videoId = options.videoId
    this.durationMs = options.durationMs ?? null
    this.report = options.report
    this.clientViewId = createClientViewId()
  }

  updateDuration(durationMs: number | null | undefined) {
    if (durationMs != null && Number.isFinite(durationMs) && durationMs > 0) {
      this.durationMs = durationMs
    }
  }

  onPlay(currentTimeSec: number) {
    this.lastCurrentTime = currentTimeSec
  }

  onPause(currentTimeSec: number) {
    this.lastCurrentTime = currentTimeSec
  }

  onSeeking() {
    this.seeking = true
  }

  onSeeked(currentTimeSec: number) {
    this.seeking = false
    this.lastCurrentTime = currentTimeSec
  }

  onTimeUpdate(currentTimeSec: number, paused: boolean, hidden = false) {
    if (paused || hidden || this.seeking) {
      this.lastCurrentTime = currentTimeSec
      return
    }
    if (this.lastCurrentTime != null) {
      const delta = currentTimeSec - this.lastCurrentTime
      if (delta > 0 && delta <= SEEK_JUMP_SECONDS) {
        this.watchedMs += Math.round(delta * 1000)
      }
    }
    this.lastCurrentTime = currentTimeSec
    void this.maybeReport()
  }

  private async maybeReport() {
    if (this.reported || this.reporting) {
      return
    }
    if (this.watchedMs < qualifyThresholdMs(this.durationMs)) {
      return
    }
    this.reporting = true
    try {
      await this.report({
        watchedMs: this.watchedMs,
        durationMs: this.durationMs,
        clientViewId: this.clientViewId,
      })
      this.reported = true
    } catch {
      // Retry on the next qualifying timeupdate with the same clientViewId.
    } finally {
      this.reporting = false
    }
  }
}

function createClientViewId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return '00000000-0000-4000-8000-000000000000'
}
