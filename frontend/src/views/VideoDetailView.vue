<template>
  <main>
    <p v-if="loading">Loading video...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <section v-else-if="video">
      <h1>{{ video.title }}</h1>
      <p>Creator: {{ video.owner.displayName }} (@{{ video.owner.username }})</p>
      <p>Uploaded: {{ formatDate(video.createdAt) }}</p>
      <p v-if="video.description">{{ video.description }}</p>
      <p v-if="processingMessage" class="processing">{{ processingMessage }}</p>
      <p v-if="failed" class="error">Video processing failed.</p>
      <HlsPlayer
        v-if="playback?.type === 'HLS' && playback.manifestUrl"
        :src="playback.manifestUrl"
        :poster="playback.thumbnailUrl"
      />
      <video
        v-else-if="showOriginal"
        controls
        :src="contentUrl"
        preload="metadata"
      >
        Your browser does not support HTML video playback.
      </video>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { isApiError } from '../api/auth'
import {
  getPlayback,
  getVideo,
  isProcessingStatus,
  videoContentUrl,
  type Playback,
  type Video,
} from '../api/videos'
import HlsPlayer from '../components/HlsPlayer.vue'

const POLL_MS = 4000

const route = useRoute()
const video = ref<Video | null>(null)
const playback = ref<Playback | null>(null)
const contentUrl = ref('')
const loading = ref(true)
const error = ref('')
let pollTimer: ReturnType<typeof setTimeout> | null = null
let active = true

const processingMessage = computed(() => {
  if (playback.value?.status === 'PENDING') {
    return 'Video is waiting to be processed.'
  }
  if (playback.value?.status === 'PROCESSING') {
    return 'Video is processing...'
  }
  return ''
})

const failed = computed(() => playback.value?.status === 'FAILED')
const showOriginal = computed(() => {
  return playback.value?.type === 'ORIGINAL' || (failed.value && Boolean(contentUrl.value))
})

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}

function stopPolling() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

async function refresh() {
  const id = String(route.params.id)
  const [videoResponse, playbackResponse] = await Promise.all([getVideo(id), getPlayback(id)])
  video.value = videoResponse.data
  playback.value = playbackResponse.data
  contentUrl.value = videoContentUrl(id)
  if (active && isProcessingStatus(playbackResponse.data.status)) {
    pollTimer = setTimeout(() => {
      void refresh().catch((err) => {
        error.value = isApiError(err) ? err.message : 'Unable to load this video.'
      })
    }, POLL_MS)
  } else {
    stopPolling()
  }
}

onMounted(async () => {
  try {
    await refresh()
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to load this video.'
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  active = false
  stopPolling()
})
</script>
