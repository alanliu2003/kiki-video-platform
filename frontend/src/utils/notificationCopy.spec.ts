import { describe, expect, it } from 'vitest'
import type { NotificationItem } from '../api/notifications'
import { notificationActionText, notificationActorName, notificationTarget } from './notificationCopy'

function item(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id: 1,
    type: 'VIDEO_LIKED',
    read: false,
    createdAt: '2026-08-31T01:00:00Z',
    actor: { id: 5, username: 'alice', displayName: 'Alice' },
    video: { id: 10, title: 'Clip', thumbnailUrl: '/api/videos/10/thumbnail' },
    comment: null,
    ...overrides,
  }
}

describe('notification copy and targets', () => {
  it('derives action text from type', () => {
    expect(notificationActionText('VIDEO_LIKED')).toBe('liked your video')
    expect(notificationActionText('VIDEO_FAVORITED')).toBe('favorited your video')
    expect(notificationActionText('VIDEO_COMMENTED')).toBe('commented on your video')
    expect(notificationActionText('COMMENT_REPLIED')).toBe('replied to your comment')
    expect(notificationActionText('USER_FOLLOWED')).toBe('followed you')
  })

  it('falls back to someone when actor is missing', () => {
    expect(notificationActorName(item({ actor: null }))).toBe('Someone')
  })

  it('routes video and comment notifications and leaves follows unlinked', () => {
    expect(notificationTarget(item())).toEqual({ name: 'video-detail', params: { id: '10' } })
    expect(
      notificationTarget(
        item({
          type: 'COMMENT_REPLIED',
          comment: { id: 20, contentSnippet: 'Hi' },
        }),
      ),
    ).toEqual({
      name: 'video-detail',
      params: { id: '10' },
      hash: '#comment-20',
    })
    expect(notificationTarget(item({ type: 'USER_FOLLOWED', video: null }))).toBeNull()
  })
})
