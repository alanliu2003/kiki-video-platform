import Hls from 'hls.js'

export interface HlsPlaybackHandle {
  destroy(): void
}

export interface HlsInstance {
  loadSource(src: string): void
  attachMedia(video: HTMLVideoElement): void
  on(event: string, handler: (event: string, data: { fatal?: boolean; type?: string }) => void): void
  destroy(): void
}

export interface HlsAdapter {
  isSupported(): boolean
  Events: { ERROR: string }
  create(config?: { enableWorker?: boolean }): HlsInstance
}

const defaultAdapter: HlsAdapter = {
  isSupported: () => Hls.isSupported(),
  Events: Hls.Events,
  create: (config) => new Hls(config),
}

export function attachHlsPlayback(
  video: HTMLVideoElement,
  src: string,
  hlsImpl: HlsAdapter = defaultAdapter,
): HlsPlaybackHandle {
  if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = src
    return {
      destroy() {
        video.removeAttribute('src')
        video.load()
      },
    }
  }
  if (hlsImpl.isSupported()) {
    const hls = hlsImpl.create({ enableWorker: false })
    hls.loadSource(src)
    hls.attachMedia(video)
    hls.on(hlsImpl.Events.ERROR, (_event, data) => {
      if (data.fatal) {
        video.dispatchEvent(new CustomEvent('hlserror', { detail: data }))
      }
    })
    return {
      destroy() {
        hls.destroy()
        video.removeAttribute('src')
        video.load()
      },
    }
  }
  video.dispatchEvent(new CustomEvent('hlsunsupported'))
  return {
    destroy() {
      video.removeAttribute('src')
    },
  }
}
