import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import VideoDetailView from './VideoDetailView.vue'
import MyVideosView from './MyVideosView.vue'
import VideoUploadView from './VideoUploadView.vue'

const {
  uploadResumableMock,
  getVideoMock,
  getMyVideosMock,
  getPlaybackMock,
  attachMock,
  getVideoInteractionsMock,
  getCreatorRelationshipMock,
  listCommentsMock,
} = vi.hoisted(() => ({
  uploadResumableMock: vi.fn(),
  getVideoMock: vi.fn(),
  getMyVideosMock: vi.fn(),
  getPlaybackMock: vi.fn(),
  attachMock: vi.fn(),
  getVideoInteractionsMock: vi.fn(),
  getCreatorRelationshipMock: vi.fn(),
  listCommentsMock: vi.fn(),
}))

vi.mock('../services/uploadManager', () => ({
  uploadResumable: uploadResumableMock,
}))

vi.mock('../api/videos', async () => {
  const actual = await vi.importActual<typeof import('../api/videos')>('../api/videos')
  return {
    ...actual,
    getVideo: getVideoMock,
    getMyVideos: getMyVideosMock,
    getPlayback: getPlaybackMock,
    videoContentUrl: actual.videoContentUrl,
  }
})

vi.mock('../services/hlsPlayback', () => ({
  attachHlsPlayback: attachMock,
}))

vi.mock('../api/interactions', () => ({
  getVideoInteractions: getVideoInteractionsMock,
  getCreatorRelationship: getCreatorRelationshipMock,
}))

vi.mock('../api/comments', () => ({
  listComments: listCommentsMock,
  createComment: vi.fn(),
}))

vi.mock('../api/danmaku', async () => {
  const actual = await vi.importActual<typeof import('../api/danmaku')>('../api/danmaku')
  return {
    ...actual,
    getVideoDanmaku: vi.fn().mockResolvedValue({ data: [] }),
  }
})

vi.mock('../services/danmakuSocket', () => ({
  createDanmakuSocket: vi.fn(() => ({ send: vi.fn(), close: vi.fn() })),
}))

function videoPayload(overrides: Record<string, unknown> = {}) {
  return {
    id: 9,
    title: 'Demo video',
    description: 'First upload',
    owner: { id: 1, username: 'alice', displayName: 'alice' },
    contentType: 'video/mp4',
    fileSizeBytes: 1234,
    status: 'UPLOADED',
    processingStatus: 'NOT_REQUESTED',
    createdAt: '2026-08-28T01:00:00Z',
    viewCount: 0,
    ...overrides,
  }
}

async function mountWithRouter(component: object, path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/videos/upload', name: 'video-upload', component: VideoUploadView },
      { path: '/videos/:id', name: 'video-detail', component: VideoDetailView },
      { path: '/my/videos', name: 'my-videos', component: MyVideosView },
      { path: '/login', name: 'login', component: { template: '<p>Login</p>' } },
      { path: '/users/:id', name: 'user-profile', component: { template: '<p>Profile</p>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return mount(component, {
    global: {
      plugins: [router, createPinia()],
    },
  })
}

describe('video views', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    uploadResumableMock.mockReset()
    getVideoMock.mockReset()
    getMyVideosMock.mockReset()
    getPlaybackMock.mockReset()
    attachMock.mockReset()
    attachMock.mockReturnValue({ destroy: vi.fn() })
    getVideoInteractionsMock.mockReset()
    getCreatorRelationshipMock.mockReset()
    listCommentsMock.mockReset()
    getVideoInteractionsMock.mockResolvedValue({
      data: {
        likeCount: 0,
        favoriteCount: 0,
        commentCount: 0,
        likedByCurrentUser: false,
        favoritedByCurrentUser: false,
      },
    })
    getCreatorRelationshipMock.mockResolvedValue({
      data: { followerCount: 0, followedByCurrentUser: false },
    })
    listCommentsMock.mockResolvedValue({
      data: { items: [], page: 0, size: 20, total: 0 },
    })
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('navigates to the video detail page after a successful upload', async () => {
    uploadResumableMock.mockResolvedValue({ video: { id: 42 }, deduplicated: false })
    const wrapper = await mountWithRouter(VideoUploadView, '/videos/upload')
    const router = wrapper.vm.$router
    const push = vi.spyOn(router, 'push')

    await wrapper.get('input[type="text"]').setValue('Demo video')
    const file = new File([new Uint8Array([1, 2, 3])], 'demo.mp4', { type: 'video/mp4' })
    const input = wrapper.get('input[type="file"]').element as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [file] })
    await wrapper.get('input[type="file"]').trigger('change')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(uploadResumableMock).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith({ name: 'video-detail', params: { id: '42' } })
  })

  it('shows a pending processing message and no player', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'PENDING' }) })
    getPlaybackMock.mockResolvedValue({ data: { status: 'PENDING', type: 'NONE', manifestUrl: null, contentUrl: null, thumbnailUrl: null } })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(wrapper.text()).toContain('Video is waiting to be processed.')
    expect(wrapper.find('video').exists()).toBe(false)
  })

  it('shows a processing message', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'PROCESSING' }) })
    getPlaybackMock.mockResolvedValue({ data: { status: 'PROCESSING', type: 'NONE', manifestUrl: null, contentUrl: null, thumbnailUrl: null } })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(wrapper.text()).toContain('Video is processing...')
    expect(wrapper.find('video').exists()).toBe(false)
  })

  it('initializes HLS when playback is ready', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'READY' }) })
    getPlaybackMock.mockResolvedValue({
      data: {
        status: 'READY',
        type: 'HLS',
        manifestUrl: '/api/videos/9/hls/master.m3u8',
        contentUrl: '/api/videos/9/content',
        thumbnailUrl: '/api/videos/9/thumbnail',
      },
    })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(attachMock).toHaveBeenCalled()
    expect(attachMock.mock.calls[0][1]).toBe('/api/videos/9/hls/master.m3u8')
    expect(wrapper.text()).not.toContain('Video is processing...')
  })

  it('shows failed state', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'FAILED' }) })
    getPlaybackMock.mockResolvedValue({
      data: { status: 'FAILED', type: 'NONE', manifestUrl: null, contentUrl: null, thumbnailUrl: null },
    })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(wrapper.text()).toContain('Video processing failed.')
  })

  it('stops polling when processing becomes READY', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'PENDING' }) })
    getPlaybackMock
      .mockResolvedValueOnce({ data: { status: 'PENDING', type: 'NONE', manifestUrl: null, contentUrl: null, thumbnailUrl: null } })
      .mockResolvedValueOnce({
        data: {
          status: 'READY',
          type: 'HLS',
          manifestUrl: '/api/videos/9/hls/master.m3u8',
          contentUrl: '/api/videos/9/content',
          thumbnailUrl: null,
        },
      })

    await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()
    expect(getPlaybackMock).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(4000)
    await flushPromises()
    expect(getPlaybackMock).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(4000)
    await flushPromises()
    expect(getPlaybackMock).toHaveBeenCalledTimes(2)
  })

  it('destroys the HLS instance on unmount', async () => {
    const destroy = vi.fn()
    attachMock.mockReturnValue({ destroy })
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'READY' }) })
    getPlaybackMock.mockResolvedValue({
      data: {
        status: 'READY',
        type: 'HLS',
        manifestUrl: '/api/videos/9/hls/master.m3u8',
        contentUrl: '/api/videos/9/content',
        thumbnailUrl: null,
      },
    })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()
    wrapper.unmount()
    expect(destroy).toHaveBeenCalledTimes(1)
  })

  it('plays HLS from a playback descriptor url', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'READY' }) })
    getPlaybackMock.mockResolvedValue({
      data: {
        status: 'READY',
        type: 'HLS',
        mode: 'HLS',
        url: '/api/videos/9/hls/master.m3u8',
        expiresAt: '2026-09-01T06:00:00Z',
        fallbackUrl: 'https://minio.example/raw?X-Amz-Signature=1',
        processingStatus: 'READY',
        deliveryMode: 'presigned',
        manifestUrl: '/api/videos/9/hls/master.m3u8',
        contentUrl: 'https://minio.example/raw?X-Amz-Signature=1',
        thumbnailUrl: 'https://minio.example/thumb?X-Amz-Signature=1',
      },
    })

    await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(attachMock).toHaveBeenCalled()
    expect(attachMock.mock.calls[0][1]).toBe('/api/videos/9/hls/master.m3u8')
  })

  it('uses a legacy descriptor URL for raw playback', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload() })
    getPlaybackMock.mockResolvedValue({
      data: {
        status: 'NOT_REQUESTED',
        type: 'ORIGINAL',
        mode: 'LEGACY',
        url: 'https://minio.example/raw?X-Amz-Signature=1',
        fallbackUrl: 'https://minio.example/raw?X-Amz-Signature=1',
        deliveryMode: 'presigned',
        manifestUrl: null,
        contentUrl: 'https://minio.example/raw?X-Amz-Signature=1',
        thumbnailUrl: null,
      },
    })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(wrapper.get('video').attributes('src')).toBe('https://minio.example/raw?X-Amz-Signature=1')
    expect(attachMock).not.toHaveBeenCalled()
  })

  it('refetches playback once after a fatal HLS error', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload({ processingStatus: 'READY' }) })
    getPlaybackMock
      .mockResolvedValueOnce({
        data: {
          status: 'READY',
          type: 'HLS',
          mode: 'HLS',
          url: '/api/videos/9/hls/master.m3u8',
          manifestUrl: '/api/videos/9/hls/master.m3u8',
          contentUrl: '/api/videos/9/content',
          thumbnailUrl: null,
        },
      })
      .mockResolvedValueOnce({
        data: {
          status: 'READY',
          type: 'HLS',
          mode: 'HLS',
          url: '/api/videos/9/hls/master.m3u8?refresh=1',
          manifestUrl: '/api/videos/9/hls/master.m3u8?refresh=1',
          contentUrl: '/api/videos/9/content',
          thumbnailUrl: null,
        },
      })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()
    expect(getPlaybackMock).toHaveBeenCalledTimes(1)

    await wrapper.getComponent({ name: 'HlsPlayer' }).vm.$emit('fatal')
    await flushPromises()
    expect(getPlaybackMock).toHaveBeenCalledTimes(2)

    await wrapper.getComponent({ name: 'HlsPlayer' }).vm.$emit('fatal')
    await flushPromises()
    expect(getPlaybackMock).toHaveBeenCalledTimes(2)
  })

  it('uses original raw playback for legacy videos', async () => {
    getVideoMock.mockResolvedValue({ data: videoPayload() })
    getPlaybackMock.mockResolvedValue({
      data: {
        status: 'NOT_REQUESTED',
        type: 'ORIGINAL',
        manifestUrl: null,
        contentUrl: '/api/videos/9/content',
        thumbnailUrl: null,
      },
    })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(wrapper.get('video').attributes('src')).toBe('/api/videos/9/content')
    expect(attachMock).not.toHaveBeenCalled()
  })

  it('renders the current user video list', async () => {
    getMyVideosMock.mockResolvedValue({
      data: {
        items: [
          {
            id: 3,
            title: 'My first video',
            status: 'UPLOADED',
            processingStatus: 'PENDING',
            fileSizeBytes: 2048,
            createdAt: '2026-08-28T01:00:00Z',
            viewCount: 0,
          },
        ],
        page: 0,
        size: 20,
        total: 1,
      },
    })

    const wrapper = await mountWithRouter(MyVideosView, '/my/videos')
    await flushPromises()

    expect(wrapper.text()).toContain('My first video')
    expect(wrapper.text()).toContain('PENDING')
    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.text()).toContain('0 views')
    expect(wrapper.findAll('a').some((link) => link.attributes('href') === '/videos/3')).toBe(true)
  })
})
