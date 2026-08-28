import { sha256 } from '@noble/hashes/sha2.js'
import { bytesToHex } from '@noble/hashes/utils.js'

export const HASH_PIECE_SIZE = 8 * 1024 * 1024

export async function sha256File(
  file: File,
  onProgress?: (ratio: number) => void,
  pieceSize = HASH_PIECE_SIZE,
): Promise<string> {
  const hasher = sha256.create()
  let offset = 0
  while (offset < file.size) {
    const end = Math.min(offset + pieceSize, file.size)
    const piece = new Uint8Array(await file.slice(offset, end).arrayBuffer())
    hasher.update(piece)
    offset = end
    onProgress?.(file.size === 0 ? 1 : offset / file.size)
  }
  return bytesToHex(hasher.digest())
}
