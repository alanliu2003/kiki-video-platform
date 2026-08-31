import { http } from './http'
import type { AxiosRequestConfig } from 'axios'

export interface HighlightSpan {
  text: string
  highlighted: boolean
}

export interface SearchHighlights {
  title: HighlightSpan[]
  description: HighlightSpan[]
  ownerUsername: HighlightSpan[]
  ownerDisplayName: HighlightSpan[]
}

export interface SearchOwner {
  id: number
  username: string
  displayName: string
}

export interface VideoSearchItem {
  videoId: number
  title: string
  descriptionSnippet: string
  owner: SearchOwner
  createdAt: string
  durationSeconds: number | null
  thumbnailUrl: string | null
  processingStatus: string
  highlights: SearchHighlights
}

export interface VideoSearchResponse {
  items: VideoSearchItem[]
  page: number
  size: number
  total: number
  tookMs: number | null
}

export type VideoSearchSort = 'RELEVANCE' | 'NEWEST' | 'OLDEST'

export interface SearchVideosParams {
  q: string
  page?: number
  size?: number
  sort?: VideoSearchSort
  ownerId?: number
  processingStatus?: string
  createdAfter?: string
  createdBefore?: string
}

export function searchVideos(params: SearchVideosParams, config: AxiosRequestConfig = {}) {
  return http.get<VideoSearchResponse>('/search/videos', {
    ...config,
    params: {
      q: params.q,
      page: params.page,
      size: params.size,
      sort: params.sort,
      ownerId: params.ownerId,
      processingStatus: params.processingStatus,
      createdAfter: params.createdAfter,
      createdBefore: params.createdBefore,
    },
  })
}
