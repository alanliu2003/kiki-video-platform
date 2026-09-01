<template>
  <article class="search-result-card">
    <div class="video-card-layout">
      <RouterLink :to="{ name: 'video-detail', params: { id: item.videoId } }">
        <img
          v-if="item.thumbnailUrl"
          class="search-thumb"
          :src="item.thumbnailUrl"
          :alt="item.title"
        >
        <div v-else class="search-thumb search-thumb-empty" aria-hidden="true"></div>
      </RouterLink>
      <div>
        <h2>
          <RouterLink :to="{ name: 'video-detail', params: { id: item.videoId } }">
            <HighlightedText v-if="item.highlights.title.length" :parts="item.highlights.title" />
            <template v-else>{{ item.title }}</template>
          </RouterLink>
        </h2>
        <p class="hint">
          <RouterLink class="creator-link" :to="userProfileLocation(item.owner.id)">
            {{ item.owner.displayName }}
          </RouterLink>
          · {{ item.owner.username }}
          <span v-if="durationLabel"> · {{ durationLabel }}</span>
          · {{ formatViewCount(item.viewCount ?? 0) }}
          · {{ createdLabel }}
          <span v-if="item.processingStatus !== 'READY'"> · {{ item.processingStatus }}</span>
        </p>
        <p v-if="item.descriptionSnippet" class="search-snippet">
          <HighlightedText
            v-if="item.highlights.description.length"
            :parts="item.highlights.description"
          />
          <template v-else>{{ item.descriptionSnippet }}</template>
        </p>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { VideoSearchItem } from '../api/search'
import { userProfileLocation } from '../router/userProfile'
import { formatDuration, formatViewCount } from '../utils/formatters'
import HighlightedText from './HighlightedText.vue'

const props = defineProps<{
  item: VideoSearchItem
}>()

const durationLabel = computed(() => formatDuration(props.item.durationSeconds))
const createdLabel = computed(() => {
  const date = new Date(props.item.createdAt)
  if (Number.isNaN(date.getTime())) {
    return props.item.createdAt
  }
  return date.toLocaleDateString()
})
</script>
