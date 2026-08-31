export const QUALIFY_SECONDS = 10
export const QUALIFY_PERCENT = 0.25

export function qualifyThresholdMs(
  durationMs: number | null | undefined,
  qualifySeconds = QUALIFY_SECONDS,
  qualifyPercent = QUALIFY_PERCENT,
): number {
  const qualifyMs = Math.max(1, Math.round(qualifySeconds * 1000))
  if (durationMs == null || !Number.isFinite(durationMs) || durationMs <= 0) {
    return qualifyMs
  }
  return Math.min(qualifyMs, Math.max(1, Math.round(durationMs * qualifyPercent)))
}
