import { http } from './http'

export interface VideoInteractions {
  likeCount: number
  favoriteCount: number
  commentCount: number
  likedByCurrentUser: boolean
  favoritedByCurrentUser: boolean
}

export interface CreatorRelationship {
  followerCount: number
  followedByCurrentUser: boolean
}

export function getVideoInteractions(videoId: number | string) {
  return http.get<VideoInteractions>(`/videos/${videoId}/interactions`)
}

export function likeVideo(videoId: number | string) {
  return http.put<VideoInteractions>(`/videos/${videoId}/like`)
}

export function unlikeVideo(videoId: number | string) {
  return http.delete<VideoInteractions>(`/videos/${videoId}/like`)
}

export function favoriteVideo(videoId: number | string) {
  return http.put<VideoInteractions>(`/videos/${videoId}/favorite`)
}

export function unfavoriteVideo(videoId: number | string) {
  return http.delete<VideoInteractions>(`/videos/${videoId}/favorite`)
}

export function getCreatorRelationship(userId: number | string) {
  return http.get<CreatorRelationship>(`/users/${userId}/relationship`)
}

export function followUser(userId: number | string) {
  return http.put<CreatorRelationship>(`/users/${userId}/follow`)
}

export function unfollowUser(userId: number | string) {
  return http.delete<CreatorRelationship>(`/users/${userId}/follow`)
}
