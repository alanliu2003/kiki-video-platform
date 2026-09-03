<template>
  <main>
    <p v-if="loading" class="progress">Loading video...</p>
    <div v-if="loading" class="player-shell" aria-hidden="true">
      <div class="skeleton" style="height: 100%"></div>
    </div>
    <p v-else-if="error" class="error">{{ error }}</p>
    <section v-else-if="video">
      <div class="player-shell">
        <HlsPlayer
          v-if="showHls"
          :key="playerGeneration"
          ref="hlsPlayer"
          :src="hlsSrc"
          :poster="playback?.thumbnailUrl"
          @fatal="onPlaybackFatal"
        />
        <video
          v-else-if="showOriginal"
          ref="rawPlayer"
          controls
          :src="contentUrl"
          preload="metadata"
          @error="onPlaybackFatal"
        >
          Your browser does not support HTML video playback.
        </video>
        <div v-else-if="failed" class="player-placeholder">
          <h2>Processing failed</h2>
          <p>Video processing failed.</p>
        </div>
        <div v-else-if="processingMessage" class="player-placeholder">
          <div class="spinner" aria-hidden="true"></div>
          <h2>Processing video</h2>
          <p>{{ processingMessage }}</p>
          <p>Upload complete. Preparing optimized playback and thumbnail…</p>
        </div>
        <DanmakuOverlay
          :items="danmaku.visible"
          :paused="danmaku.paused"
          @finished="danmaku.remove"
        />
      </div>

      <div v-if="showHls || showOriginal" class="player-controls">
        <div class="danmaku-toolbar">
          <button
            type="button"
            class="btn btn-secondary"
            :aria-label="danmaku.enabled ? 'Turn danmaku off' : 'Turn danmaku on'"
            @click="danmaku.setEnabled(!danmaku.enabled)"
          >
            Danmaku: {{ danmaku.enabled ? 'ON' : 'OFF' }}
          </button>
        </div>
        <DanmakuInput
          :disabled="!auth.isAuthenticated"
          :error="danmaku.error"
          @send="onSendDanmaku"
        />
      </div>

      <p v-if="failed" class="error">Video processing failed.</p>
      <h1 class="video-title">{{ video.title }}</h1>
      <div class="video-meta-row">
        <CreatorCard
          v-if="relationship"
          :user-id="video.owner.id"
          :username="video.owner.username"
          :display-name="video.owner.displayName"
          :relationship="relationship"
          @update:relationship="relationship = $event"
        />
        <InteractionBar
          v-if="interactions"
          :video-id="video.id"
          :interactions="interactions"
          @update:interactions="onInteractionsUpdate"
        />
      </div>
      <p class="video-stats">{{ formatViewCount(video.viewCount) }} • {{ formatDate(video.createdAt) }}</p>
      <p v-if="video.description" class="video-description">{{ video.description }}</p>
      <CommentsSection :video-id="video.id" @created="onCommentCreated" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { isApiError } from '../api/auth'
import { getCreatorRelationship, getVideoInteractions, type CreatorRelationship, type VideoInteractions } from '../api/interactions'
import {
  getPlayback,
  getVideo,
  isHlsPlayback,
  isLegacyPlayback,
  isProcessingStatus,
  playbackSourceUrl,
  qualifyView,
  videoContentUrl,
  type Playback,
  type Video,
} from '../api/videos'
import { isDeliveryRefreshError, loadWithSingleRetry } from '../services/playbackRefresh'
import { QualifiedViewTracker } from '../services/qualifiedViewTracker'
import { formatRelativeTime, formatViewCount } from '../utils/formatters'
import CreatorCard from '../components/CreatorCard.vue'
import CommentsSection from '../components/CommentsSection.vue'
import DanmakuInput from '../components/DanmakuInput.vue'
import DanmakuOverlay from '../components/DanmakuOverlay.vue'
import HlsPlayer from '../components/HlsPlayer.vue'
import InteractionBar from '../components/InteractionBar.vue'
import { useAuthStore } from '../stores/auth'
import { useDanmakuStore } from '../stores/danmaku'

const POLL_MS = 4000

const route = useRoute()
const auth = useAuthStore()
const danmaku = useDanmakuStore()
const video = ref<Video | null>(null)
const playback = ref<Playback | null>(null)
const interactions = ref<VideoInteractions | null>(null)
const relationship = ref<CreatorRelationship | null>(null)
const contentUrl = ref('')
const playerGeneration = ref(0)
const loading = ref(true)
const error = ref('')
const hlsPlayer = ref<{ videoElement?: HTMLVideoElement | null } | null>(null)
const rawPlayer = ref<HTMLVideoElement | null>(null)
let pollTimer: ReturnType<typeof setTimeout> | null = null
let active = true
let boundVideo: HTMLVideoElement | null = null
let viewTracker: QualifiedViewTracker | null = null

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
const showHls = computed(() => isHlsPlayback(playback.value) && Boolean(hlsSrc.value))
const showOriginal = computed(() => {
  return isLegacyPlayback(playback.value) || (failed.value && Boolean(contentUrl.value))
})
const hlsSrc = computed(() => playbackSourceUrl(playback.value) || '')
let refreshedPlayback = false

function formatDate(value: string): string {
  return formatRelativeTime(value)
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

function currentVideoElement(): HTMLVideoElement | null {
  return hlsPlayer.value?.videoElement ?? rawPlayer.value
}

function currentTimeMs(el: HTMLVideoElement): number {
  return Math.max(0, Math.round(el.currentTime * 1000))
}

function onTimeUpdate() {
  if (!boundVideo) {
    return
  }
  danmaku.onTime(currentTimeMs(boundVideo), boundVideo.paused)
  viewTracker?.onTimeUpdate(boundVideo.currentTime, boundVideo.paused, document.hidden)
}

function onSeeking() {
  viewTracker?.onSeeking()
}

function onSeeked() {
  if (!boundVideo) {
    return
  }
  danmaku.onSeek(currentTimeMs(boundVideo))
  viewTracker?.onSeeked(boundVideo.currentTime)
}

function onPlayPause() {
  if (!boundVideo) {
    return
  }
  danmaku.paused = boundVideo.paused
  if (boundVideo.paused) {
    viewTracker?.onPause(boundVideo.currentTime)
  } else {
    viewTracker?.onPlay(boundVideo.currentTime)
  }
}

function onLoadedMetadata() {
  if (!boundVideo || !Number.isFinite(boundVideo.duration) || boundVideo.duration <= 0) {
    return
  }
  viewTracker?.updateDuration(Math.round(boundVideo.duration * 1000))
}

function onVisibilityChange() {
  if (!boundVideo) {
    return
  }
  viewTracker?.onTimeUpdate(boundVideo.currentTime, boundVideo.paused, document.hidden)
}

function unbindPlayer() {
  if (!boundVideo) {
    return
  }
  boundVideo.removeEventListener('timeupdate', onTimeUpdate)
  boundVideo.removeEventListener('seeking', onSeeking)
  boundVideo.removeEventListener('seeked', onSeeked)
  boundVideo.removeEventListener('play', onPlayPause)
  boundVideo.removeEventListener('pause', onPlayPause)
  boundVideo.removeEventListener('loadedmetadata', onLoadedMetadata)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  boundVideo = null
}

function bindPlayer() {
  const el = currentVideoElement()
  if (el === boundVideo) {
    return
  }
  unbindPlayer()
  if (!el || !video.value) {
    return
  }
  boundVideo = el
  el.addEventListener('timeupdate', onTimeUpdate)
  el.addEventListener('seeking', onSeeking)
  el.addEventListener('seeked', onSeeked)
  el.addEventListener('play', onPlayPause)
  el.addEventListener('pause', onPlayPause)
  el.addEventListener('loadedmetadata', onLoadedMetadata)
  document.addEventListener('visibilitychange', onVisibilityChange)
  ensureViewTracker(video.value)
  onLoadedMetadata()
  danmaku.start(video.value.id, auth.accessToken)
  danmaku.paused = el.paused
  void danmaku.ensureWindow(currentTimeMs(el))
}

function ensureViewTracker(current: Video) {
  if (viewTracker && viewTracker.videoId === current.id) {
    if (current.durationSeconds) {
      viewTracker.updateDuration(Math.round(current.durationSeconds * 1000))
    }
    return
  }
  viewTracker = new QualifiedViewTracker({
    videoId: current.id,
    durationMs: current.durationSeconds ? Math.round(current.durationSeconds * 1000) : null,
    report: async (payload) => {
      const response = await qualifyView(current.id, payload)
      if (video.value && video.value.id === current.id) {
        video.value = { ...video.value, viewCount: response.data.viewCount }
      }
    },
  })
}

function onSendDanmaku(content: string) {
  const el = currentVideoElement()
  danmaku.send(content, el ? currentTimeMs(el) : 0)
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

async function onPlaybackFatal() {
  if (refreshedPlayback || !video.value) {
    return
  }
  refreshedPlayback = true
  try {
    const playbackResponse = await loadWithSingleRetry({
      load: () => getPlayback(video.value!.id),
      isRetryable: isDeliveryRefreshError,
    })
    playback.value = playbackResponse.data
    contentUrl.value = playbackSourceUrl(playbackResponse.data) || videoContentUrl(video.value.id)
    playerGeneration.value += 1
  } catch {
    // Keep the current player; one refresh attempt is enough.
  }
}

async function refresh() {
  const id = String(route.params.id)
  if (video.value && String(video.value.id) !== id) {
    refreshedPlayback = false
  }
  const [videoResponse, playbackResponse] = await Promise.all([
    getVideo(id),
    loadWithSingleRetry({
      load: () => getPlayback(id),
      isRetryable: isDeliveryRefreshError,
    }),
  ])
  video.value = videoResponse.data
  playback.value = playbackResponse.data
  contentUrl.value = playbackSourceUrl(playbackResponse.data) || videoContentUrl(id)
  await loadSocial(id, videoResponse.data.owner.id)
  await nextTick()
  bindPlayer()
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

watch([hlsPlayer, rawPlayer, playback], () => {
  void nextTick().then(bindPlayer)
})

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
  unbindPlayer()
  danmaku.stop()
})
</script>
