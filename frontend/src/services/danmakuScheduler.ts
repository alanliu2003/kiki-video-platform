import type { DanmakuItem } from '../api/danmaku'

export function createDanmakuScheduler() {
  const knownIds = new Set<number>()
  const pending: DanmakuItem[] = []
  let lastMs = -1

  function ingest(items: DanmakuItem[]): DanmakuItem[] {
    const added: DanmakuItem[] = []
    for (const item of items) {
      if (knownIds.has(item.id)) {
        continue
      }
      knownIds.add(item.id)
      pending.push(item)
      added.push(item)
    }
    pending.sort((a, b) => a.videoTimeMs - b.videoTimeMs || a.id - b.id)
    if (knownIds.size > 4000) {
      const oldest = [...knownIds].slice(0, knownIds.size - 3000)
      for (const id of oldest) {
        knownIds.delete(id)
      }
    }
    return added
  }

  function due(currentMs: number, paused: boolean): DanmakuItem[] {
    if (paused) {
      return []
    }
    const spawned: DanmakuItem[] = []
    const remaining: DanmakuItem[] = []
    for (const item of pending) {
      if (item.videoTimeMs > lastMs && item.videoTimeMs <= currentMs) {
        spawned.push(item)
      } else if (item.videoTimeMs > currentMs) {
        remaining.push(item)
      }
    }
    pending.length = 0
    pending.push(...remaining)
    lastMs = currentMs
    return spawned
  }

  function seek(currentMs: number) {
    lastMs = Math.max(-1, currentMs - 1)
    const remaining = pending.filter((item) => item.videoTimeMs > lastMs)
    pending.length = 0
    pending.push(...remaining)
  }

  function reset() {
    knownIds.clear()
    pending.length = 0
    lastMs = -1
  }

  function has(id: number): boolean {
    return knownIds.has(id)
  }

  return { ingest, due, seek, reset, has }
}

export type DanmakuScheduler = ReturnType<typeof createDanmakuScheduler>
