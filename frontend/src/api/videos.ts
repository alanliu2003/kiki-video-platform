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
  createdAt: string
}

export interface VideoSummary {
  id: number
  title: string
  status: string
  fileSizeBytes: number
  createdAt: string
}

export interface VideoListResponse {
  items: VideoSummary[]
  page: number
  size: number
  total: number
}

export interface UploadVideoPayload {
  title: string
  description: string
  file: File
  onProgress?: (percent: number) => void
}

const UPLOAD_TIMEOUT_MS = 15 * 60 * 1000

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

export function getMyVideos(page = 0, size = 20) {
  return http.get<VideoListResponse>('/users/me/videos', {
    params: { page, size },
  })
}

export function videoContentUrl(videoId: number | string): string {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${base.replace(/\/$/, '')}/videos/${videoId}/content`
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
