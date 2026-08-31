import { http } from './http'

export interface DanmakuUser {
  id: number
  username: string
  displayName: string
}

export interface DanmakuItem {
  id: number
  videoId: number
  user: DanmakuUser
  content: string
  videoTimeMs: number
  style: string
  createdAt: string
}

export const DANMAKU_HISTORY_WINDOW_MS = 60_000
export const DANMAKU_MAX_LENGTH = 200

export function getVideoDanmaku(videoId: number | string, fromMs: number, toMs: number) {
  return http.get<DanmakuItem[]>(`/videos/${videoId}/danmaku`, {
    params: { fromMs, toMs },
  })
}

export function historyWindow(currentMs: number): { fromMs: number; toMs: number } {
  const bucket = Math.floor(Math.max(0, currentMs) / 30_000) * 30_000
  return { fromMs: bucket, toMs: bucket + DANMAKU_HISTORY_WINDOW_MS }
}
