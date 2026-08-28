import { describe, expect, it, vi } from 'vitest'
import { attachHlsPlayback, type HlsAdapter } from './hlsPlayback'

function fakeVideo(canPlay = ''): HTMLVideoElement {
  const listeners: Record<string, EventListener[]> = {}
  return {
    src: '',
    canPlayType: () => canPlay,
    load: vi.fn(),
    removeAttribute: vi.fn(),
    addEventListener(type: string, handler: EventListener) {
      listeners[type] = listeners[type] || []
      listeners[type].push(handler)
    },
    dispatchEvent(event: Event) {
      for (const handler of listeners[event.type] || []) {
        handler(event)
      }
      return true
    },
  } as unknown as HTMLVideoElement
}

describe('attachHlsPlayback', () => {
  it('uses native HLS when the video element can play MPEG-URL', () => {
    const video = fakeVideo('maybe')
    const destroy = attachHlsPlayback(video, '/api/videos/1/hls/master.m3u8').destroy
    expect(video.src).toBe('/api/videos/1/hls/master.m3u8')
    destroy()
    expect(video.removeAttribute).toHaveBeenCalledWith('src')
  })

  it('creates and destroys an hls.js instance when native HLS is missing', () => {
    const destroyMock = vi.fn()
    const hls = {
      loadSource: vi.fn(),
      attachMedia: vi.fn(),
      on: vi.fn(),
      destroy: destroyMock,
    }
    const adapter: HlsAdapter = {
      isSupported: () => true,
      Events: { ERROR: 'hlsError' },
      create: () => hls,
    }
    const video = fakeVideo('')
    const handle = attachHlsPlayback(video, '/manifest.m3u8', adapter)
    expect(hls.loadSource).toHaveBeenCalledWith('/manifest.m3u8')
    expect(hls.attachMedia).toHaveBeenCalledWith(video)
    handle.destroy()
    expect(destroyMock).toHaveBeenCalledTimes(1)
  })

  it('signals unsupported browsers', () => {
    const video = fakeVideo('')
    const unsupported = vi.fn()
    video.addEventListener('hlsunsupported', unsupported)
    attachHlsPlayback(video, '/manifest.m3u8', {
      isSupported: () => false,
      Events: { ERROR: 'hlsError' },
      create: () => {
        throw new Error('unused')
      },
    })
    expect(unsupported).toHaveBeenCalled()
  })
})
