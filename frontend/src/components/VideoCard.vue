<template>
  <article class="search-result-card video-card">
    <RouterLink :to="{ name: 'video-detail', params: { id: item.id } }">
      <img
        v-if="item.thumbnailUrl && !thumbBroken"
        class="search-thumb"
        :src="item.thumbnailUrl"
        :alt="item.title"
        @error="thumbBroken = true"
      >
      <div v-else class="search-thumb search-thumb-empty" aria-hidden="true"></div>
      <div>
        <h2>{{ item.title }}</h2>
        <p class="hint">
          {{ item.owner.displayName }} · {{ item.owner.username }}
          <span v-if="durationLabel"> · {{ durationLabel }}</span>
          · {{ formatViewCount(item.viewCount) }}
          · {{ createdLabel }}
          <span v-if="item.likeCount > 0"> · {{ item.likeCount }} likes</span>
          <span v-if="item.processingStatus !== 'READY'"> · {{ item.processingStatus }}</span>
        </p>
      </div>
    </RouterLink>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { VideoCard } from '../api/discovery'
import { formatDuration, formatRelativeTime, formatViewCount } from '../utils/formatters'

const props = defineProps<{
  item: VideoCard
}>()

const thumbBroken = ref(false)
const durationLabel = computed(() => formatDuration(props.item.durationSeconds))
const createdLabel = computed(() => formatRelativeTime(props.item.createdAt))
</script>
