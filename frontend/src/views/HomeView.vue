<template>
  <main>
    <section
      v-if="auth.isAuthenticated"
      class="feed-section"
      aria-labelledby="recommended-heading"
    >
      <h2 id="recommended-heading" class="section-title">Recommended for you</h2>
      <p v-if="recommended.status === 'LOADING'" class="progress">Loading recommendations…</p>
      <LoadingSkeleton v-if="recommended.status === 'LOADING'" :count="4" />
      <p v-else-if="recommended.status === 'ERROR'" class="error">{{ recommended.error }}</p>
      <EmptyState
        v-else-if="recommended.items.length === 0"
        title="No recommendations yet"
        description="Watch and follow creators to build this feed."
      />
      <template v-else>
        <p v-if="recommended.coldStart" class="hint">
          Not enough activity yet — showing popular and recent videos.
        </p>
        <VideoGrid>
          <VideoCard v-for="item in recommended.items" :key="'rec-' + item.id" :item="item" />
        </VideoGrid>
      </template>
    </section>

    <section class="feed-section" aria-labelledby="trending-heading">
      <h2 id="trending-heading" class="section-title">Trending</h2>
      <p v-if="trending.status === 'LOADING'" class="progress">Loading trending videos…</p>
      <LoadingSkeleton v-if="trending.status === 'LOADING'" :count="4" />
      <p v-else-if="trending.status === 'ERROR'" class="error">{{ trending.error }}</p>
      <p v-else-if="visibleTrending.length === 0" class="hint">No trending videos yet.</p>
      <VideoGrid v-else>
        <VideoCard v-for="item in visibleTrending" :key="'t-' + item.id" :item="item" />
      </VideoGrid>
    </section>

    <section class="feed-section" aria-labelledby="recent-heading">
      <h2 id="recent-heading" class="section-title">New uploads</h2>
      <p v-if="recent.status === 'LOADING'" class="progress">Loading new uploads…</p>
      <LoadingSkeleton v-if="recent.status === 'LOADING'" :count="4" />
      <p v-else-if="recent.status === 'ERROR'" class="error">{{ recent.error }}</p>
      <p v-else-if="visibleRecent.length === 0" class="hint">No uploads yet.</p>
      <VideoGrid v-else>
        <VideoCard v-for="item in visibleRecent" :key="'r-' + item.id" :item="item" />
      </VideoGrid>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue'
import { isApiError } from '../api/auth'
import {
  getRecentVideos,
  getRecommendedVideos,
  getTrendingVideos,
  type VideoCard as DiscoveryVideo,
} from '../api/discovery'
import EmptyState from '../components/EmptyState.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import VideoCard from '../components/VideoCard.vue'
import VideoGrid from '../components/VideoGrid.vue'
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

const recommendedIds = computed(() => {
  if (recommended.status !== 'READY') {
    return new Set<number>()
  }
  return new Set(recommended.items.map((item) => item.id))
})
const visibleTrending = computed(() => trending.items.filter((item) => !recommendedIds.value.has(item.id)))
const visibleRecent = computed(() => {
  const seen = new Set(recommendedIds.value)
  for (const item of visibleTrending.value) {
    seen.add(item.id)
  }
  return recent.items.filter((item) => !seen.has(item.id))
})

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
