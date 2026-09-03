import { http } from './http'

export interface VideoOwner {
  id: number
  username: string
  displayName: string
}

export interface Video {
  id: number
  title: string
  description: string | null
  owner: VideoOwner
  contentType: string
  fileSizeBytes: number
  status: string
  processingStatus: string
  createdAt: string
  viewCount: number
  durationSeconds?: number | null
}

export interface VideoSummary {
  id: number
  title: string
  status: string
  processingStatus: string
  fileSizeBytes: number
  createdAt: string
  viewCount: number
}

export interface VideoListResponse {
  items: VideoSummary[]
  page: number
  size: number
  total: number
}

export interface Playback {
  status: string
  type: 'HLS' | 'ORIGINAL' | 'NONE' | string
  mode?: 'HLS' | 'LEGACY' | 'NONE' | string
  url?: string | null
  expiresAt?: string | null
  fallbackUrl?: string | null
  processingStatus?: string
  deliveryMode?: 'presigned' | 'proxy' | string
  manifestUrl: string | null
  contentUrl: string | null
  thumbnailUrl: string | null
}

export function isHlsPlayback(playback: Playback | null | undefined): boolean {
  if (!playback) {
    return false
  }
  return playback.mode === 'HLS' || playback.type === 'HLS'
}

export function isLegacyPlayback(playback: Playback | null | undefined): boolean {
  if (!playback) {
    return false
  }
  return playback.mode === 'LEGACY' || playback.type === 'ORIGINAL'
}

export function playbackSourceUrl(playback: Playback | null | undefined): string | null {
  if (!playback) {
    return null
  }
  if (isHlsPlayback(playback)) {
    return playback.url || playback.manifestUrl
  }
  if (isLegacyPlayback(playback)) {
    return playback.url || playback.contentUrl || playback.fallbackUrl || null
  }
  return playback.fallbackUrl || playback.contentUrl || null
}

export interface UploadVideoPayload {
  title: string
  description: string
  file: File
  onProgress?: (percent: number) => void
}

const UPLOAD_TIMEOUT_MS = 15 * 60 * 1000

/** Legacy single-request multipart upload. The UI uses chunked uploads instead. */
export function uploadVideo(payload: UploadVideoPayload) {
  const form = new FormData()
  form.append('title', payload.title)
  if (payload.description.trim()) {
    form.append('description', payload.description.trim())
  }
  form.append('file', payload.file)

  return http.post<Video>('/videos', form, {
    timeout: UPLOAD_TIMEOUT_MS,
    onUploadProgress(event) {
      if (!payload.onProgress || !event.total) {
        return
      }
      payload.onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}

export function getVideo(videoId: number | string) {
  return http.get<Video>(`/videos/${videoId}`)
}

export function getPlayback(videoId: number | string) {
  return http.get<Playback>(`/videos/${videoId}/playback`)
}

export interface QualifyViewRequest {
  watchedMs: number
  durationMs: number | null
  clientViewId: string
}

export interface QualifyViewResponse {
  counted: boolean
  alreadyCounted: boolean
  viewCount: number
}

export function qualifyView(videoId: number | string, payload: QualifyViewRequest) {
  return http.post<QualifyViewResponse>(`/videos/${videoId}/views/qualify`, payload, {
    withCredentials: true,
  })
}

export function getMyVideos(page = 0, size = 20) {
  return http.get<VideoListResponse>('/users/me/videos', {
    params: { page, size },
  })
}

function apiBase(): string {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return base.replace(/\/$/, '')
}

export function videoContentUrl(videoId: number | string): string {
  return `${apiBase()}/videos/${videoId}/content`
}

export function videoManifestUrl(videoId: number | string): string {
  return `${apiBase()}/videos/${videoId}/hls/master.m3u8`
}

export function videoThumbnailUrl(videoId: number | string): string {
  return `${apiBase()}/videos/${videoId}/thumbnail`
}

export function isProcessingStatus(status: string | undefined): boolean {
  return status === 'PENDING' || status === 'PROCESSING'
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}
