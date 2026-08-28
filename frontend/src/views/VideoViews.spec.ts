import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VideoDetailView from './VideoDetailView.vue'
import MyVideosView from './MyVideosView.vue'
import VideoUploadView from './VideoUploadView.vue'

const { uploadResumableMock, getVideoMock, getMyVideosMock } = vi.hoisted(() => ({
  uploadResumableMock: vi.fn(),
  getVideoMock: vi.fn(),
  getMyVideosMock: vi.fn(),
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
    videoContentUrl: actual.videoContentUrl,
  }
})

async function mountWithRouter(component: object, path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/videos/upload', name: 'video-upload', component: VideoUploadView },
      { path: '/videos/:id', name: 'video-detail', component: VideoDetailView },
      { path: '/my/videos', name: 'my-videos', component: MyVideosView },
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

  it('renders video detail metadata and a native player source', async () => {
    getVideoMock.mockResolvedValue({
      data: {
        id: 9,
        title: 'Demo video',
        description: 'First upload',
        owner: { id: 1, username: 'alice', displayName: 'alice' },
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        status: 'UPLOADED',
        createdAt: '2026-08-28T01:00:00Z',
      },
    })

    const wrapper = await mountWithRouter(VideoDetailView, '/videos/9')
    await flushPromises()

    expect(wrapper.text()).toContain('Demo video')
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('First upload')
    expect(wrapper.get('video').attributes('src')).toBe('/api/videos/9/content')
  })

  it('renders the current user video list', async () => {
    getMyVideosMock.mockResolvedValue({
      data: {
        items: [
          {
            id: 3,
            title: 'My first video',
            status: 'UPLOADED',
            fileSizeBytes: 2048,
            createdAt: '2026-08-28T01:00:00Z',
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
    expect(wrapper.text()).toContain('UPLOADED')
    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.get('a').attributes('href')).toBe('/videos/3')
  })
})
