<template>
  <main>
    <h1>Discover</h1>
    <p class="hint">
      {{
        auth.isAuthenticated
          ? 'Recommended for you is a deterministic ranking from your follows, likes, favorites, comments, and qualified views. Trending is a global ranking. Neither is machine learning.'
          : 'Trending is a deterministic ranking of public videos. Sign in for personalized recommendations.'
      }}
    </p>

    <section
      v-if="auth.isAuthenticated"
      class="feed-section"
      aria-labelledby="recommended-heading"
    >
      <h2 id="recommended-heading">Recommended for you</h2>
      <p v-if="recommended.status === 'LOADING'" class="progress">Loading recommendations…</p>
      <p v-else-if="recommended.status === 'ERROR'" class="error">{{ recommended.error }}</p>
      <p v-else-if="recommended.items.length === 0" class="hint">No recommendations yet.</p>
      <template v-else>
        <p v-if="recommended.coldStart" class="hint">
          Not enough activity yet — showing popular and recent videos.
        </p>
        <VideoCard v-for="item in recommended.items" :key="'rec-' + item.id" :item="item" />
      </template>
    </section>

    <section class="feed-section" aria-labelledby="trending-heading">
      <h2 id="trending-heading">Trending</h2>
      <p v-if="trending.status === 'LOADING'" class="progress">Loading trending videos…</p>
      <p v-else-if="trending.status === 'ERROR'" class="error">{{ trending.error }}</p>
      <p v-else-if="trending.items.length === 0" class="hint">No trending videos yet.</p>
      <VideoCard v-for="item in trending.items" :key="'t-' + item.id" :item="item" />
    </section>

    <section class="feed-section" aria-labelledby="recent-heading">
      <h2 id="recent-heading">New uploads</h2>
      <p v-if="recent.status === 'LOADING'" class="progress">Loading new uploads…</p>
      <p v-else-if="recent.status === 'ERROR'" class="error">{{ recent.error }}</p>
      <p v-else-if="recent.items.length === 0" class="hint">No uploads yet.</p>
      <VideoCard v-for="item in recent.items" :key="'r-' + item.id" :item="item" />
    </section>

    <HealthStatus />
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, watch } from 'vue'
import { isApiError } from '../api/auth'
import {
  getRecentVideos,
  getRecommendedVideos,
  getTrendingVideos,
  type VideoCard as DiscoveryVideo,
} from '../api/discovery'
import HealthStatus from '../components/HealthStatus.vue'
import VideoCard from '../components/VideoCard.vue'
import { useAuthStore } from '../stores/auth'

interface FeedState {
  status: 'LOADING' | 'READY' | 'ERROR'
  items: DiscoveryVideo[]
  error: string
  coldStart: boolean
}

const auth = useAuthStore()
const recommended = reactive<FeedState>({ status: 'LOADING', items: [], error: '', coldStart: false })
const trending = reactive<FeedState>({ status: 'LOADING', items: [], error: '', coldStart: false })
const recent = reactive<FeedState>({ status: 'LOADING', items: [], error: '', coldStart: false })

async function loadFeed(
  state: FeedState,
  loader: () => Promise<{ data: { items: DiscoveryVideo[]; coldStart?: boolean } }>,
  fallback: string,
) {
  try {
    const response = await loader()
    state.items = response.data.items
    state.coldStart = Boolean(response.data.coldStart)
    state.status = 'READY'
  } catch (err) {
    state.error = isApiError(err) ? err.message : fallback
    state.status = 'ERROR'
  }
}

watch(
  () => auth.isAuthenticated,
  (signedIn) => {
    if (!signedIn) {
      recommended.status = 'READY'
      recommended.items = []
      recommended.error = ''
      recommended.coldStart = false
      return
    }
    recommended.status = 'LOADING'
    void loadFeed(recommended, () => getRecommendedVideos(), 'Unable to load recommendations.')
  },
  { immediate: true },
)

onMounted(() => {
  void loadFeed(trending, () => getTrendingVideos(), 'Unable to load trending videos.')
  void loadFeed(recent, () => getRecentVideos(), 'Unable to load new uploads.')
})
</script>
