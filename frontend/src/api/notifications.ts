import { http } from './http'

export type NotificationType =
  | 'VIDEO_LIKED'
  | 'VIDEO_FAVORITED'
  | 'VIDEO_COMMENTED'
  | 'COMMENT_REPLIED'
  | 'USER_FOLLOWED'

export interface NotificationActor {
  id: number
  username: string
  displayName: string
}

export interface NotificationVideo {
  id: number
  title: string | null
  thumbnailUrl: string | null
}

export interface NotificationComment {
  id: number
  contentSnippet: string | null
}

export interface NotificationItem {
  id: number
  type: NotificationType
  read: boolean
  createdAt: string
  actor: NotificationActor | null
  video: NotificationVideo | null
  comment: NotificationComment | null
}

export interface NotificationListResponse {
  items: NotificationItem[]
  page: number
  size: number
  total: number
}

export interface NotificationUnreadCountResponse {
  unreadCount: number
}

export function listNotifications(page = 0, size = 20) {
  return http.get<NotificationListResponse>('/notifications', {
    params: { page, size },
  })
}

export function getNotificationUnreadCount() {
  return http.get<NotificationUnreadCountResponse>('/notifications/unread-count')
}

export function markNotificationRead(id: number) {
  return http.post<NotificationUnreadCountResponse>(`/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return http.post<NotificationUnreadCountResponse>('/notifications/read-all')
}
