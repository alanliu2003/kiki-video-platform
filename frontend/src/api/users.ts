import { http } from './http'
import type { VideoFeedResponse } from './discovery'

export {
  getCreatorRelationship,
  followUser,
  unfollowUser,
  type CreatorRelationship,
} from './interactions'

export interface PublicProfile {
  id: number
  username: string
  displayName: string
  createdAt: string
  followerCount: number
  followingCount: number
  publicVideoCount: number
  totalViews: number
  followedByCurrentUser?: boolean
}

export function getPublicProfile(userId: number | string) {
  return http.get<PublicProfile>(`/users/${userId}`)
}

export function getUserVideos(userId: number | string, page = 0, size = 20) {
  return http.get<VideoFeedResponse>(`/users/${userId}/videos`, {
    params: { page, size },
  })
}
