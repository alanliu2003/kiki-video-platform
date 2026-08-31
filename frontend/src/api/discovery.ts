import { http } from './http'
import type { VideoOwner } from './videos'

export interface VideoCard {
  id: number
  title: string
  owner: VideoOwner
  createdAt: string
  durationSeconds: number | null
  thumbnailUrl: string | null
  processingStatus: string
  viewCount: number
  likeCount: number
  recommendationReason?: string | null
}

export interface VideoFeedResponse {
  items: VideoCard[]
  page: number
  size: number
  total: number
}

export interface RecommendationFeedResponse extends VideoFeedResponse {
  coldStart: boolean
}

export function getTrendingVideos(page = 0, size = 20) {
  return http.get<VideoFeedResponse>('/videos/trending', {
    params: { page, size },
  })
}

export function getRecentVideos(page = 0, size = 20) {
  return http.get<VideoFeedResponse>('/videos/recent', {
    params: { page, size },
  })
}

export function getRecommendedVideos(page = 0, size = 20) {
  return http.get<RecommendationFeedResponse>('/recommendations/videos', {
    params: { page, size },
  })
}
