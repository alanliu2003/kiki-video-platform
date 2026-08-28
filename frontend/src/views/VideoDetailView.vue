<template>
  <main>
    <p v-if="loading">Loading video...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <section v-else-if="video">
      <h1>{{ video.title }}</h1>
      <p>Creator: {{ video.owner.displayName }} (@{{ video.owner.username }})</p>
      <p>Uploaded: {{ formatDate(video.createdAt) }}</p>
      <p v-if="video.description">{{ video.description }}</p>
      <video controls :src="contentUrl" preload="metadata">
        Your browser does not support HTML video playback.
      </video>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { isApiError } from '../api/auth'
import { getVideo, videoContentUrl, type Video } from '../api/videos'

const route = useRoute()
const video = ref<Video | null>(null)
const contentUrl = ref('')
const loading = ref(true)
const error = ref('')

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}

onMounted(async () => {
  const id = String(route.params.id)
  try {
    const response = await getVideo(id)
    video.value = response.data
    contentUrl.value = videoContentUrl(id)
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to load this video.'
  } finally {
    loading.value = false
  }
})
</script>
