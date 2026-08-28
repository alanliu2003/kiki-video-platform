export function totalChunks(fileSize: number, chunkSize: number): number {
  if (fileSize <= 0 || chunkSize <= 0) {
    throw new Error('File size and chunk size must be positive')
  }
  return Math.ceil(fileSize / chunkSize)
}

export function sliceChunk(file: File, chunkIndex: number, chunkSize: number): Blob {
  const start = chunkIndex * chunkSize
  const end = Math.min(start + chunkSize, file.size)
  return file.slice(start, end)
}

export function missingChunks(total: number, uploaded: number[]): number[] {
  const present = new Set(uploaded)
  const missing: number[] = []
  for (let i = 0; i < total; i++) {
    if (!present.has(i)) {
      missing.push(i)
    }
  }
  return missing
}

export function uploadedBytes(fileSize: number, chunkSize: number, uploaded: number[]): number {
  let bytes = 0
  for (const index of uploaded) {
    const start = index * chunkSize
    bytes += Math.max(0, Math.min(chunkSize, fileSize - start))
  }
  return bytes
}
