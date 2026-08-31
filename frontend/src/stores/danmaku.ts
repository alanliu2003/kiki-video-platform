import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getVideoDanmaku, historyWindow, type DanmakuItem } from '../api/danmaku'
import { createDanmakuScheduler } from '../services/danmakuScheduler'
import { createDanmakuSocket, type DanmakuSocket } from '../services/danmakuSocket'

const ENABLED_KEY = 'kiki.danmaku.enabled'

export interface ActiveDanmaku extends DanmakuItem {
  key: string
}

function newClientMessageId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `c-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export const useDanmakuStore = defineStore('danmaku', () => {
  const enabled = ref(localStorage.getItem(ENABLED_KEY) !== '0')
  const visible = ref<ActiveDanmaku[]>([])
  const paused = ref(false)
  const error = ref('')
  const connectedVideoId = ref<number | null>(null)

  let socket: DanmakuSocket | null = null
  let scheduler = createDanmakuScheduler()
  const loadedWindows = new Set<string>()
  let fetchGeneration = 0

  function setEnabled(value: boolean) {
    enabled.value = value
    localStorage.setItem(ENABLED_KEY, value ? '1' : '0')
    if (!value) {
      visible.value = []
    }
  }

  function stop() {
    fetchGeneration += 1
    socket?.close()
    socket = null
    scheduler.reset()
    loadedWindows.clear()
    visible.value = []
    error.value = ''
    connectedVideoId.value = null
    paused.value = false
  }

  function start(videoId: number, token: string | null) {
    if (connectedVideoId.value === videoId && socket) {
      return
    }
    stop()
    connectedVideoId.value = videoId
    scheduler = createDanmakuScheduler()
    socket = createDanmakuSocket({
      videoId,
      token,
      onMessage(message) {
        if (message.type === 'DANMAKU' && message.danmaku) {
          scheduler.ingest([message.danmaku])
          return
        }
        if (message.type === 'ERROR') {
          error.value = message.message ?? message.code ?? 'Danmaku error'
        }
      },
    })
  }

  async function ensureWindow(currentMs: number) {
    const videoId = connectedVideoId.value
    if (videoId == null) {
      return
    }
    const { fromMs, toMs } = historyWindow(currentMs)
    const key = `${fromMs}-${toMs}`
    if (loadedWindows.has(key)) {
      return
    }
    loadedWindows.add(key)
    const generation = fetchGeneration
    try {
      const response = await getVideoDanmaku(videoId, fromMs, toMs)
      if (generation !== fetchGeneration) {
        return
      }
      scheduler.ingest(response.data)
    } catch {
      loadedWindows.delete(key)
    }
  }

  function onTime(currentMs: number, isPaused: boolean) {
    paused.value = isPaused
    void ensureWindow(currentMs)
    if (!enabled.value) {
      return
    }
    const due = scheduler.due(currentMs, isPaused)
    if (due.length === 0) {
      return
    }
    visible.value = [
      ...visible.value,
      ...due.map((item) => ({ ...item, key: `${item.id}` })),
    ]
  }

  function onSeek(currentMs: number) {
    visible.value = []
    scheduler.seek(currentMs)
    void ensureWindow(currentMs)
  }

  function send(content: string, videoTimeMs: number) {
    error.value = ''
    socket?.send(content.trim(), videoTimeMs, newClientMessageId())
  }

  function remove(key: string) {
    visible.value = visible.value.filter((item) => item.key !== key)
  }

  return {
    enabled,
    visible,
    paused,
    error,
    connectedVideoId,
    setEnabled,
    start,
    stop,
    ensureWindow,
    onTime,
    onSeek,
    send,
    remove,
  }
})
