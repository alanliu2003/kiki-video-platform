import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VideoDetailView from './VideoDetailView.vue'
import { useAuthStore } from '../stores/auth'

const {
  getVideoMock,
  getPlaybackMock,
  getVideoInteractionsMock,
  getCreatorRelationshipMock,
  likeVideoMock,
  unlikeVideoMock,
  favoriteVideoMock,
  unfavoriteVideoMock,
  followUserMock,
  unfollowUserMock,
  listCommentsMock,
  createCommentMock,
} = vi.hoisted(() => ({
  getVideoMock: vi.fn(),
  getPlaybackMock: vi.fn(),
  getVideoInteractionsMock: vi.fn(),
  getCreatorRelationshipMock: vi.fn(),
  likeVideoMock: vi.fn(),
  unlikeVideoMock: vi.fn(),
  favoriteVideoMock: vi.fn(),
  unfavoriteVideoMock: vi.fn(),
  followUserMock: vi.fn(),
  unfollowUserMock: vi.fn(),
  listCommentsMock: vi.fn(),
  createCommentMock: vi.fn(),
}))

vi.mock('../api/videos', async () => {
  const actual = await vi.importActual<typeof import('../api/videos')>('../api/videos')
  return {
    ...actual,
    getVideo: getVideoMock,
    getPlayback: getPlaybackMock,
  }
})

vi.mock('../api/interactions', () => ({
  getVideoInteractions: getVideoInteractionsMock,
  getCreatorRelationship: getCreatorRelationshipMock,
  likeVideo: likeVideoMock,
  unlikeVideo: unlikeVideoMock,
  favoriteVideo: favoriteVideoMock,
  unfavoriteVideo: unfavoriteVideoMock,
  followUser: followUserMock,
  unfollowUser: unfollowUserMock,
}))

vi.mock('../api/users', () => ({
  followUser: followUserMock,
  unfollowUser: unfollowUserMock,
}))

vi.mock('../api/comments', () => ({
  listComments: listCommentsMock,
  createComment: createCommentMock,
}))

vi.mock('../services/hlsPlayback', () => ({
  attachHlsPlayback: vi.fn(() => ({ destroy: vi.fn() })),
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

function videoPayload() {
  return {
    id: 9,
    title: 'Demo video',
    description: 'First upload',
    owner: { id: 1, username: 'alice', displayName: 'Alice' },
    contentType: 'video/mp4',
    fileSizeBytes: 1234,
    status: 'UPLOADED',
    processingStatus: 'NOT_REQUESTED',
    createdAt: '2026-08-28T01:00:00Z',
  }
}

function interactions(overrides: Record<string, unknown> = {}) {
  return {
    likeCount: 3,
    favoriteCount: 2,
    commentCount: 1,
    likedByCurrentUser: false,
    favoritedByCurrentUser: false,
    ...overrides,
  }
}

async function mountDetail() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/videos/:id', name: 'video-detail', component: VideoDetailView },
      { path: '/login', name: 'login', component: { template: '<p>Login</p>' } },
      { path: '/users/:id', name: 'user-profile', component: { template: '<p>Profile</p>' } },
    ],
  })
  await router.push('/videos/9')
  await router.isReady()
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(VideoDetailView, {
    global: {
      plugins: [router, pinia],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

function authenticate() {
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.user = {
    id: 2,
    username: 'bob',
    email: 'bob@example.com',
    displayName: 'Bob',
    role: 'USER',
  }
}

describe('video social interactions', () => {
  beforeEach(() => {
    getVideoMock.mockReset()
    getPlaybackMock.mockReset()
    getVideoInteractionsMock.mockReset()
    getCreatorRelationshipMock.mockReset()
    likeVideoMock.mockReset()
    unlikeVideoMock.mockReset()
    favoriteVideoMock.mockReset()
    unfavoriteVideoMock.mockReset()
    followUserMock.mockReset()
    unfollowUserMock.mockReset()
    listCommentsMock.mockReset()
    createCommentMock.mockReset()
    getVideoMock.mockResolvedValue({ data: videoPayload() })
    getPlaybackMock.mockResolvedValue({
      data: { status: 'NOT_REQUESTED', type: 'ORIGINAL', manifestUrl: null, contentUrl: '/api/videos/9/content', thumbnailUrl: null },
    })
    getVideoInteractionsMock.mockResolvedValue({ data: interactions() })
    getCreatorRelationshipMock.mockResolvedValue({
      data: { followerCount: 4, followedByCurrentUser: false },
    })
    listCommentsMock.mockResolvedValue({
      data: {
        items: [
          {
            id: 11,
            videoId: 9,
            author: { id: 3, username: 'carol', displayName: 'Carol' },
            content: 'Great video',
            parentCommentId: null,
            createdAt: '2026-08-28T02:00:00Z',
            updatedAt: '2026-08-28T02:00:00Z',
            replies: [],
          },
        ],
        page: 0,
        size: 20,
        total: 1,
      },
    })
  })

  it('renders interaction counts, follow count, and comments', async () => {
    const { wrapper } = await mountDetail()
    expect(wrapper.text()).toContain('Like (3)')
    expect(wrapper.text()).toContain('Favorite (2)')
    expect(wrapper.text()).toContain('1 comments')
    expect(wrapper.text()).toContain('4 followers')
    expect(wrapper.text()).toContain('Great video')
    expect(wrapper.find('#comment-11').exists()).toBe(true)
    expect(wrapper.find('.creator-link').attributes('href')).toBe('/users/1')
    expect(wrapper.text()).not.toContain('Follow')
  })

  it('redirects unauthenticated like clicks to login', async () => {
    const { wrapper, router } = await mountDetail()
    const push = vi.spyOn(router, 'push')
    await wrapper.get('.interaction-bar button').trigger('click')
    expect(likeVideoMock).not.toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/videos/9' } })
  })

  it('toggles like for an authenticated user', async () => {
    likeVideoMock.mockResolvedValue({
      data: interactions({ likeCount: 4, likedByCurrentUser: true }),
    })
    const { wrapper } = await mountDetail()
    authenticate()
    await wrapper.get('.interaction-bar button').trigger('click')
    await flushPromises()
    expect(likeVideoMock).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('Unlike (4)')
  })

  it('rolls back like state when the API fails', async () => {
    likeVideoMock.mockRejectedValue(Object.assign(new Error('Unable to like'), { name: 'ApiError' }))
    const { wrapper } = await mountDetail()
    authenticate()
    await wrapper.get('.interaction-bar button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Like (3)')
    expect(wrapper.text()).toMatch(/Unable to (like|update like)/)
  })

  it('toggles favorite for an authenticated user', async () => {
    favoriteVideoMock.mockResolvedValue({
      data: interactions({ favoriteCount: 3, favoritedByCurrentUser: true }),
    })
    const { wrapper } = await mountDetail()
    authenticate()
    const buttons = wrapper.findAll('.interaction-bar button')
    await buttons[1].trigger('click')
    await flushPromises()
    expect(favoriteVideoMock).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('Unfavorite (3)')
  })

  it('toggles follow for an authenticated user', async () => {
    followUserMock.mockResolvedValue({
      data: { followerCount: 5, followedByCurrentUser: true },
    })
    const { wrapper } = await mountDetail()
    authenticate()
    await flushPromises()
    await wrapper.get('.creator-card button').trigger('click')
    await flushPromises()
    expect(followUserMock).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Unfollow')
    expect(wrapper.text()).toContain('5 followers')
  })

  it('submits a comment and refreshes the list', async () => {
    createCommentMock.mockResolvedValue({ data: { id: 12 } })
    listCommentsMock
      .mockResolvedValueOnce({
        data: { items: [], page: 0, size: 20, total: 0 },
      })
      .mockResolvedValueOnce({
        data: {
          items: [
            {
              id: 12,
              videoId: 9,
              author: { id: 2, username: 'bob', displayName: 'Bob' },
              content: 'Nice work',
              parentCommentId: null,
              createdAt: '2026-08-28T03:00:00Z',
              updatedAt: '2026-08-28T03:00:00Z',
              replies: [],
            },
          ],
          page: 0,
          size: 20,
          total: 1,
        },
      })
    getVideoInteractionsMock.mockResolvedValue({ data: interactions({ commentCount: 1 }) })
    const { wrapper } = await mountDetail()
    authenticate()
    await flushPromises()
    await wrapper.get('textarea').setValue('Nice work')
    await wrapper.get('.comment-form').trigger('submit')
    await flushPromises()
    expect(createCommentMock).toHaveBeenCalledWith(9, 'Nice work')
    expect(wrapper.text()).toContain('Nice work')
  })

  it('submits a reply to a top-level comment', async () => {
    createCommentMock.mockResolvedValue({ data: { id: 13 } })
    listCommentsMock.mockResolvedValue({
      data: {
        items: [
          {
            id: 11,
            videoId: 9,
            author: { id: 3, username: 'carol', displayName: 'Carol' },
            content: 'Great video',
            parentCommentId: null,
            createdAt: '2026-08-28T02:00:00Z',
            updatedAt: '2026-08-28T02:00:00Z',
            replies: [
              {
                id: 13,
                videoId: 9,
                author: { id: 2, username: 'bob', displayName: 'Bob' },
                content: 'Thanks',
                parentCommentId: 11,
                createdAt: '2026-08-28T03:00:00Z',
                updatedAt: '2026-08-28T03:00:00Z',
                replies: [],
              },
            ],
          },
        ],
        page: 0,
        size: 20,
        total: 1,
      },
    })
    const { wrapper } = await mountDetail()
    authenticate()
    await flushPromises()
    await wrapper.get('.comment-item button').trigger('click')
    await wrapper.get('.comment-item textarea').setValue('Thanks')
    const replyForm = wrapper.get('.comment-item .comment-form')
    await replyForm.trigger('submit')
    await flushPromises()
    expect(createCommentMock).toHaveBeenCalledWith(9, 'Thanks', 11)
  })
})
