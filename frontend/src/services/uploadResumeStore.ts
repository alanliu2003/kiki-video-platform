const STORAGE_PREFIX = 'kiki.uploadResume.'

export interface UploadResumeRecord {
  uploadId: string
  fileSha256: string
  fileName: string
  fileSize: number
}

export function resumeStorageKey(fileSha256: string): string {
  return `${STORAGE_PREFIX}${fileSha256}`
}

export function saveResume(record: UploadResumeRecord): void {
  localStorage.setItem(resumeStorageKey(record.fileSha256), JSON.stringify(record))
}

export function loadResume(fileSha256: string): UploadResumeRecord | null {
  const raw = localStorage.getItem(resumeStorageKey(fileSha256))
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UploadResumeRecord
  } catch {
    return null
  }
}

export function clearResume(fileSha256: string): void {
  localStorage.removeItem(resumeStorageKey(fileSha256))
}

export function latestResumeHint(): UploadResumeRecord | null {
  let found: UploadResumeRecord | null = null
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (!key?.startsWith(STORAGE_PREFIX)) {
      continue
    }
    const raw = localStorage.getItem(key)
    if (!raw) {
      continue
    }
    try {
      found = JSON.parse(raw) as UploadResumeRecord
    } catch {
      // Ignore malformed records.
    }
  }
  return found
}
