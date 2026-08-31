<template>
  <article class="search-result-card">
    <RouterLink :to="{ name: 'video-detail', params: { id: item.videoId } }">
      <img
        v-if="item.thumbnailUrl"
        class="search-thumb"
        :src="item.thumbnailUrl"
        :alt="item.title"
      >
      <div v-else class="search-thumb search-thumb-empty" aria-hidden="true"></div>
      <div>
        <h2>
          <HighlightedText v-if="item.highlights.title.length" :parts="item.highlights.title" />
          <template v-else>{{ item.title }}</template>
        </h2>
        <p class="hint">
          {{ item.owner.displayName }} · {{ item.owner.username }} · {{ createdLabel }}
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
    </RouterLink>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { VideoSearchItem } from '../api/search'
import HighlightedText from './HighlightedText.vue'

const props = defineProps<{
  item: VideoSearchItem
}>()

const createdLabel = computed(() => {
  const date = new Date(props.item.createdAt)
  if (Number.isNaN(date.getTime())) {
    return props.item.createdAt
  }
  return date.toLocaleDateString()
})
</script>
