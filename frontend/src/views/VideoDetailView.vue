<template>
  <main>
    <p v-if="loading">Loading video...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <section v-else-if="video">
      <h1>{{ video.title }}</h1>
      <CreatorCard
        v-if="relationship"
        :user-id="video.owner.id"
        :username="video.owner.username"
        :display-name="video.owner.displayName"
        :relationship="relationship"
        @update:relationship="relationship = $event"
      />
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
      <InteractionBar
        v-if="interactions"
        :video-id="video.id"
        :interactions="interactions"
        @update:interactions="onInteractionsUpdate"
      />
      <CommentsSection :video-id="video.id" @created="onCommentCreated" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { isApiError } from '../api/auth'
import { getCreatorRelationship, getVideoInteractions, type CreatorRelationship, type VideoInteractions } from '../api/interactions'
import {
  getPlayback,
  getVideo,
  isProcessingStatus,
  videoContentUrl,
  type Playback,
  type Video,
} from '../api/videos'
import CreatorCard from '../components/CreatorCard.vue'
import CommentsSection from '../components/CommentsSection.vue'
import HlsPlayer from '../components/HlsPlayer.vue'
import InteractionBar from '../components/InteractionBar.vue'

const POLL_MS = 4000

const route = useRoute()
const video = ref<Video | null>(null)
const playback = ref<Playback | null>(null)
const interactions = ref<VideoInteractions | null>(null)
const relationship = ref<CreatorRelationship | null>(null)
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

function onInteractionsUpdate(value: VideoInteractions) {
  interactions.value = value
}

async function loadSocial(videoId: string, ownerId: number) {
  const [interactionResponse, relationshipResponse] = await Promise.all([
    getVideoInteractions(videoId),
    getCreatorRelationship(ownerId),
  ])
  interactions.value = interactionResponse.data
  relationship.value = relationshipResponse.data
}

async function onCommentCreated() {
  if (!video.value) {
    return
  }
  try {
    const response = await getVideoInteractions(video.value.id)
    interactions.value = response.data
  } catch {
    if (interactions.value) {
      interactions.value = {
        ...interactions.value,
        commentCount: interactions.value.commentCount + 1,
      }
    }
  }
}

async function refresh() {
  const id = String(route.params.id)
  const [videoResponse, playbackResponse] = await Promise.all([getVideo(id), getPlayback(id)])
  video.value = videoResponse.data
  playback.value = playbackResponse.data
  contentUrl.value = videoContentUrl(id)
  await loadSocial(id, videoResponse.data.owner.id)
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
