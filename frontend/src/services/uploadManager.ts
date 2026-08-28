import {
  completeUpload,
  inferVideoContentType,
  initUpload,
  uploadChunk,
  type CompleteUploadResponse,
  type InitUploadResponse,
} from '../api/uploads'
import { missingChunks, sliceChunk, uploadedBytes } from './chunks'
import { withRetries } from './retry'
import { sha256File } from './sha256'
import { clearResume, saveResume } from './uploadResumeStore'

export const DEFAULT_UPLOAD_CONCURRENCY = 4

function configuredConcurrency(): number {
  const configured = Number(import.meta.env.VITE_UPLOAD_MAX_CONCURRENCY)
  return Number.isFinite(configured) && configured > 0 ? configured : DEFAULT_UPLOAD_CONCURRENCY
}

export type UploadPhase =
  | 'hashing'
  | 'checking'
  | 'uploading'
  | 'resuming'
  | 'finalizing'
  | 'complete'
  | 'deduplicated'

export interface UploadProgress {
  phase: UploadPhase
  uploadedBytes: number
  totalBytes: number
  percent: number
  message: string
}

export interface UploadManagerOptions {
  file: File
  title: string
  description: string
  concurrency?: number
  maxRetries?: number
  onProgress?: (progress: UploadProgress) => void
}

export async function uploadResumable(options: UploadManagerOptions): Promise<CompleteUploadResponse> {
  const { file, title, description, onProgress } = options
  const concurrency = options.concurrency ?? configuredConcurrency()
  const maxRetries = options.maxRetries ?? 3

  onProgress?.(progress('hashing', 0, file.size, 'Hashing...'))
  const fileSha256 = await sha256File(file, (ratio) => {
    onProgress?.(progress('hashing', Math.round(ratio * file.size), file.size, 'Hashing...'))
  })

  onProgress?.(progress('checking', 0, file.size, 'Checking existing upload...'))
  const init = (
    await initUpload({
      fileName: file.name,
      fileSizeBytes: file.size,
      contentType: inferVideoContentType(file),
      fileSha256,
    })
  ).data
  saveResume({
    uploadId: init.uploadId,
    fileSha256,
    fileName: file.name,
    fileSize: file.size,
  })

  if (!init.uploadRequired || init.deduplicated) {
    onProgress?.(
      progress('deduplicated', file.size, file.size, 'File already exists on the server. Upload skipped.'),
    )
    const done = await finalize(init.uploadId, title, description, fileSha256, onProgress, true)
    return done
  }

  const uploaded = new Set(init.uploadedChunks)
  const missing = missingChunks(init.totalChunks, [...uploaded])
  const phase: UploadPhase = uploaded.size > 0 ? 'resuming' : 'uploading'
  reportChunkProgress(file.size, init, uploaded, phase, onProgress)

  await mapPool(missing, concurrency, async (chunkIndex) => {
    const blob = sliceChunk(file, chunkIndex, init.chunkSizeBytes)
    await withRetries(() => uploadChunk(init.uploadId, chunkIndex, blob), maxRetries)
    uploaded.add(chunkIndex)
    reportChunkProgress(file.size, init, uploaded, phase, onProgress)
  })

  return finalize(init.uploadId, title, description, fileSha256, onProgress, false)
}

async function finalize(
  uploadId: string,
  title: string,
  description: string,
  fileSha256: string,
  onProgress: UploadManagerOptions['onProgress'],
  deduplicated: boolean,
): Promise<CompleteUploadResponse> {
  onProgress?.(progress('finalizing', 1, 1, 'Finalizing...'))
  const done = (await completeUpload(uploadId, title, description)).data
  clearResume(fileSha256)
  onProgress?.(
    progress(
      'complete',
      1,
      1,
      deduplicated ? 'File already exists on the server. Upload skipped.' : 'Complete',
    ),
  )
  return done
}

function reportChunkProgress(
  fileSize: number,
  init: InitUploadResponse,
  uploaded: Set<number>,
  phase: UploadPhase,
  onProgress: UploadManagerOptions['onProgress'],
) {
  const bytes = uploadedBytes(fileSize, init.chunkSizeBytes, [...uploaded])
  const percent = fileSize === 0 ? 100 : Math.round((bytes / fileSize) * 100)
  onProgress?.(
    progress(
      phase,
      bytes,
      fileSize,
      `${phase === 'resuming' ? 'Resuming' : 'Uploading'} ${percent}%`,
    ),
  )
}

function progress(phase: UploadPhase, uploadedBytes: number, totalBytes: number, message: string): UploadProgress {
  return {
    phase,
    uploadedBytes,
    totalBytes,
    percent: totalBytes === 0 ? 100 : Math.round((uploadedBytes / totalBytes) * 100),
    message,
  }
}

async function mapPool<T>(items: T[], concurrency: number, worker: (item: T) => Promise<void>): Promise<void> {
  const pending = [...items]
  const workers = Array.from({ length: Math.max(1, concurrency) }, async () => {
    while (pending.length > 0) {
      const item = pending.shift()
      if (item === undefined) {
        return
      }
      await worker(item)
    }
  })
  await Promise.all(workers)
}
