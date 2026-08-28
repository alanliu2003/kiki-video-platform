import { http } from './http'

export interface CommentAuthor {
  id: number
  username: string
  displayName: string
}

export interface VideoComment {
  id: number
  videoId: number
  author: CommentAuthor
  content: string
  parentCommentId: number | null
  createdAt: string
  updatedAt: string
  replies?: VideoComment[]
}

export interface CommentListResponse {
  items: VideoComment[]
  page: number
  size: number
  total: number
}

export function listComments(videoId: number | string, page = 0, size = 20) {
  return http.get<CommentListResponse>(`/videos/${videoId}/comments`, {
    params: { page, size },
  })
}

export function createComment(
  videoId: number | string,
  content: string,
  parentCommentId?: number | null,
) {
  return http.post<VideoComment>(`/videos/${videoId}/comments`, {
    content,
    parentCommentId: parentCommentId ?? undefined,
  })
}
