<template>
  <article class="video-card">
    <RouterLink :to="{ name: 'video-detail', params: { id: item.id } }" class="video-card-media">
      <img
        v-if="thumbnailSrc && !thumbBroken"
        :src="thumbnailSrc"
        :alt="item.title"
        loading="lazy"
        @error="thumbBroken = true"
      >
      <div v-else class="thumb-placeholder" aria-hidden="true">
        <AppIcon name="play" :size="22" />
      </div>
      <span v-if="durationLabel" class="video-card-duration">{{ durationLabel }}</span>
    </RouterLink>
    <div class="video-card-body">
      <h2 class="video-card-title">
        <RouterLink :to="{ name: 'video-detail', params: { id: item.id } }">{{ item.title }}</RouterLink>
      </h2>
      <p v-if="item.recommendationReason" class="recommendation-reason">{{ item.recommendationReason }}</p>
      <p v-else-if="processingHint" class="recommendation-reason">{{ processingHint }}</p>
      <p class="video-card-meta">
        <RouterLink
          v-if="item.owner"
          class="creator-link"
          :to="userProfileLocation(item.owner.id)"
        >
          {{ item.owner.displayName }}
        </RouterLink>
        <span>{{ formatViewCount(item.viewCount) }}</span>
        <span v-if="item.fileSizeLabel">• {{ item.fileSizeLabel }}</span>
        <span v-if="createdLabel">• {{ createdLabel }}</span>
        <StatusPill v-if="showStatus && item.processingStatus" :status="item.processingStatus" />
        <span v-else-if="item.processingStatus && item.processingStatus !== 'READY'">
          · {{ item.processingStatus }}
        </span>
      </p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { userProfileLocation } from '../router/userProfile'
import { formatDuration, formatRelativeTime, formatViewCount } from '../utils/formatters'
import AppIcon from './AppIcon.vue'
import StatusPill from './StatusPill.vue'

export interface VideoCardItem {
  id: number
  title: string
  owner?: { id: number; username: string; displayName: string }
  createdAt: string
  durationSeconds?: number | null
  thumbnailUrl?: string | null
  processingStatus?: string
  viewCount: number
  likeCount?: number
  recommendationReason?: string | null
  fileSizeLabel?: string
}

const props = withDefaults(defineProps<{
  item: VideoCardItem
  showStatus?: boolean
}>(), {
  showStatus: false,
})

const thumbBroken = ref(false)
const thumbnailSrc = computed(() => props.item.thumbnailUrl || '')
const durationLabel = computed(() => formatDuration(props.item.durationSeconds))
const createdLabel = computed(() => formatRelativeTime(props.item.createdAt))
const processingHint = computed(() => {
  if (!props.showStatus) {
    return ''
  }
  const status = (props.item.processingStatus || '').toUpperCase()
  if (status === 'FAILED') {
    return 'Processing failed.'
  }
  if (status && status !== 'READY') {
    return 'Processing video. Preparing playback and thumbnail…'
  }
  return ''
})
</script>
