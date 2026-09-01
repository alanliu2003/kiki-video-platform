import type { RouteLocationRaw } from 'vue-router'
import type { NotificationItem, NotificationType } from '../api/notifications'

export function notificationActionText(type: NotificationType): string {
  switch (type) {
    case 'VIDEO_LIKED':
      return 'liked your video'
    case 'VIDEO_FAVORITED':
      return 'favorited your video'
    case 'VIDEO_COMMENTED':
      return 'commented on your video'
    case 'COMMENT_REPLIED':
      return 'replied to your comment'
    case 'USER_FOLLOWED':
      return 'followed you'
    default:
      return 'interacted with you'
  }
}

export function notificationActorName(item: NotificationItem): string {
  return item.actor?.displayName || item.actor?.username || 'Someone'
}

export function notificationTarget(item: NotificationItem): RouteLocationRaw | null {
  switch (item.type) {
    case 'VIDEO_LIKED':
    case 'VIDEO_FAVORITED':
      return item.video ? { name: 'video-detail', params: { id: String(item.video.id) } } : null
    case 'VIDEO_COMMENTED':
    case 'COMMENT_REPLIED':
      if (!item.video) {
        return null
      }
      return {
        name: 'video-detail',
        params: { id: String(item.video.id) },
        hash: item.comment ? `#comment-${item.comment.id}` : undefined,
      }
    case 'USER_FOLLOWED':
      return item.actor ? { name: 'user-profile', params: { id: String(item.actor.id) } } : null
    default:
      return null
  }
}
