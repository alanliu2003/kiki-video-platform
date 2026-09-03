<template>
  <article class="search-result-card">
    <RouterLink :to="{ name: 'video-detail', params: { id: item.videoId } }">
      <img
        v-if="item.thumbnailUrl && !thumbBroken"
        class="search-thumb"
        :src="item.thumbnailUrl"
        :alt="item.title"
        loading="lazy"
        @error="thumbBroken = true"
      >
      <div v-else class="search-thumb search-thumb-empty thumb-placeholder" aria-hidden="true">
        <AppIcon name="play" :size="20" />
      </div>
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
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { VideoSearchItem } from '../api/search'
import { userProfileLocation } from '../router/userProfile'
import { formatDuration, formatRelativeTime, formatViewCount } from '../utils/formatters'
import AppIcon from './AppIcon.vue'
import HighlightedText from './HighlightedText.vue'

const props = defineProps<{
  item: VideoSearchItem
}>()

const thumbBroken = ref(false)
const durationLabel = computed(() => formatDuration(props.item.durationSeconds))
const createdLabel = computed(() => formatRelativeTime(props.item.createdAt))
</script>
