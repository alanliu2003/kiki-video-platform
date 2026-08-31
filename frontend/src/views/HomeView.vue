<template>
  <main>
    <h1>Discover</h1>
    <p class="hint">Trending is a deterministic ranking of public videos, not a personalized recommendation.</p>

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
import { onMounted, reactive } from 'vue'
import { isApiError } from '../api/auth'
import { getRecentVideos, getTrendingVideos, type VideoCard as DiscoveryVideo } from '../api/discovery'
import HealthStatus from '../components/HealthStatus.vue'
import VideoCard from '../components/VideoCard.vue'

interface FeedState {
  status: 'LOADING' | 'READY' | 'ERROR'
  items: DiscoveryVideo[]
  error: string
}

const trending = reactive<FeedState>({ status: 'LOADING', items: [], error: '' })
const recent = reactive<FeedState>({ status: 'LOADING', items: [], error: '' })

async function loadFeed(
  state: FeedState,
  loader: () => Promise<{ data: { items: DiscoveryVideo[] } }>,
  fallback: string,
) {
  try {
    const response = await loader()
    state.items = response.data.items
    state.status = 'READY'
  } catch (err) {
    state.error = isApiError(err) ? err.message : fallback
    state.status = 'ERROR'
  }
}

onMounted(() => {
  void loadFeed(trending, () => getTrendingVideos(), 'Unable to load trending videos.')
  void loadFeed(recent, () => getRecentVideos(), 'Unable to load new uploads.')
})
</script>
