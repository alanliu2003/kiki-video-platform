export function formatViewCount(count: number | null | undefined): string {
  if (count == null || !Number.isFinite(count) || count < 0) {
    return '0 views'
  }
  const whole = Math.floor(count)
  if (whole === 1) {
    return '1 view'
  }
  return `${formatCompactCount(whole)} views`
}

export function formatCompactCount(count: number): string {
  if (!Number.isFinite(count) || count < 0) {
    return '0'
  }
  const whole = Math.floor(count)
  if (whole < 1000) {
    return String(whole)
  }
  if (whole < 1_000_000) {
    return formatScaled(whole, 1000, 'K')
  }
  return formatScaled(whole, 1_000_000, 'M')
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) {
    return ''
  }
  const total = Math.round(seconds)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const remainder = total % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`
  }
  return `${minutes}:${String(remainder).padStart(2, '0')}`
}

export function formatRelativeTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const deltaMs = Date.now() - date.getTime()
  if (deltaMs < 0) {
    return date.toLocaleDateString()
  }
  const minutes = Math.floor(deltaMs / 60_000)
  if (minutes < 1) {
    return 'just now'
  }
  if (minutes < 60) {
    return `${minutes}m ago`
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return `${hours}h ago`
  }
  const days = Math.floor(hours / 24)
  if (days < 7) {
    return `${days}d ago`
  }
  return date.toLocaleDateString()
}

function formatScaled(count: number, divisor: number, suffix: string): string {
  const scaled = count / divisor
  const rounded = scaled >= 10 ? Math.round(scaled) : Math.round(scaled * 10) / 10
  const label = Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
  return `${label}${suffix}`
}
