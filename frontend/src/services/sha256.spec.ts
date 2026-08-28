import { describe, expect, it } from 'vitest'
import { sha256File } from './sha256'

describe('sha256File', () => {
  it('hashes incrementally and matches the known digest', async () => {
    const file = new File([new Uint8Array([1, 2, 3, 4])], 'demo.bin')
    const digest = await sha256File(file, undefined, 2)
    expect(digest).toBe('9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a')
  })
})
