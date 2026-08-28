import { http } from './http'
import type { Video } from './videos'

export interface InitUploadRequest {
  fileName: string
  fileSizeBytes: number
  contentType: string
  fileSha256: string
}

export interface InitUploadResponse {
  uploadId: string
  chunkSizeBytes: number
  totalChunks: number
  uploadedChunks: number[]
  deduplicated: boolean
  uploadRequired: boolean
  mediaObjectId: number | null
  status: string
  expiresAt: string
}

export interface UploadStatusResponse {
  uploadId: string
  status: string
  totalChunks: number
  uploadedChunks: number[]
  missingChunks: number[]
  expiresAt: string
  deduplicated: boolean
  uploadRequired: boolean
}

export interface CompleteUploadResponse {
  video: Video
  deduplicated: boolean
}

const CHUNK_TIMEOUT_MS = 5 * 60 * 1000
const COMPLETE_TIMEOUT_MS = 15 * 60 * 1000

export function initUpload(payload: InitUploadRequest) {
  return http.post<InitUploadResponse>('/uploads/init', payload)
}

export function getUpload(uploadId: string) {
  return http.get<UploadStatusResponse>(`/uploads/${uploadId}`)
}

export function uploadChunk(uploadId: string, chunkIndex: number, blob: Blob) {
  return http.put<void>(`/uploads/${uploadId}/chunks/${chunkIndex}`, blob, {
    headers: { 'Content-Type': 'application/octet-stream' },
    timeout: CHUNK_TIMEOUT_MS,
    maxBodyLength: Infinity,
    maxContentLength: Infinity,
  })
}

export function completeUpload(uploadId: string, title: string, description: string) {
  return http.post<CompleteUploadResponse>(
    `/uploads/${uploadId}/complete`,
    {
      title,
      description: description.trim() || undefined,
    },
    { timeout: COMPLETE_TIMEOUT_MS },
  )
}

export function inferVideoContentType(file: File): string {
  if (file.type === 'video/mp4' || file.type === 'video/webm') {
    return file.type
  }
  if (file.name.toLowerCase().endsWith('.webm')) {
    return 'video/webm'
  }
  return 'video/mp4'
}
