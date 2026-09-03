<template>
  <main>
    <PageHeader
      :title="query ? `Search results for “${query}”` : 'Search'"
      :description="result ? `${result.total} result(s)` : undefined"
    />
    <form class="search-filters" @submit.prevent="applyFilters">
      <label>
        Sort
        <select v-model="sort">
          <option value="RELEVANCE">Relevance</option>
          <option value="NEWEST">Newest</option>
          <option value="OLDEST">Oldest</option>
        </select>
      </label>
      <label>
        Processing
        <select v-model="processingStatus">
          <option value="">Any</option>
          <option value="READY">READY</option>
          <option value="PENDING">PENDING</option>
          <option value="PROCESSING">PROCESSING</option>
          <option value="FAILED">FAILED</option>
          <option value="NOT_REQUESTED">NOT_REQUESTED</option>
        </select>
      </label>
      <button type="submit" class="btn btn-secondary">Apply</button>
    </form>

    <p v-if="status === 'LOADING'" class="progress">Searching…</p>
    <LoadingSkeleton v-if="status === 'LOADING'" :count="3" />
    <EmptyState
      v-else-if="status === 'EMPTY'"
      :title="query ? 'No search results' : 'Search videos'"
      :description="query ? 'No videos matched that search.' : 'Type a search to find videos.'"
      icon="search"
    />
    <p v-else-if="status === 'ERROR'" class="error">{{ errorMessage }}</p>
    <template v-else>
      <p class="hint">{{ result?.total ?? 0 }} result(s)</p>
      <SearchResultCard v-for="item in result?.items ?? []" :key="item.videoId" :item="item" />
      <nav v-if="(result?.total ?? 0) > (result?.size ?? 20)" class="search-pagination">
        <button type="button" class="btn btn-secondary" :disabled="page <= 0" @click="goToPage(page - 1)">Previous</button>
        <span>Page {{ page + 1 }}</span>
        <button type="button" class="btn btn-secondary" :disabled="!hasNext" @click="goToPage(page + 1)">Next</button>
      </nav>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/http'
import { searchVideos, type VideoSearchResponse, type VideoSearchSort } from '../api/search'
import EmptyState from '../components/EmptyState.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import PageHeader from '../components/PageHeader.vue'
import SearchResultCard from '../components/SearchResultCard.vue'

type SearchStatus = 'LOADING' | 'RESULTS' | 'EMPTY' | 'ERROR'

const route = useRoute()
const router = useRouter()
const status = ref<SearchStatus>('LOADING')
const result = ref<VideoSearchResponse | null>(null)
const errorMessage = ref('Search is temporarily unavailable.')
const sort = ref<VideoSearchSort>('RELEVANCE')
const processingStatus = ref('')
let loadId = 0
let controller: AbortController | null = null

const query = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const page = computed(() => {
  const raw = Number(route.query.page)
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 0
})
const hasNext = computed(() => {
  if (!result.value) {
    return false
  }
  return (page.value + 1) * result.value.size < result.value.total
})

watch(
  () => [route.query.q, route.query.page, route.query.sort, route.query.processingStatus],
  () => {
    sort.value = parseSort(route.query.sort)
    processingStatus.value = typeof route.query.processingStatus === 'string'
      ? route.query.processingStatus
      : ''
    void load()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  controller?.abort()
})

async function load() {
  const q = query.value
  if (!q) {
    status.value = 'EMPTY'
    result.value = null
    return
  }
  const requestId = ++loadId
  controller?.abort()
  controller = new AbortController()
  status.value = 'LOADING'
  try {
    const response = await searchVideos(
      {
        q,
        page: page.value,
        sort: parseSort(route.query.sort),
        processingStatus: typeof route.query.processingStatus === 'string' && route.query.processingStatus
          ? route.query.processingStatus
          : undefined,
      },
      { signal: controller.signal },
    )
    if (requestId !== loadId) {
      return
    }
    result.value = response.data
    status.value = response.data.items.length === 0 ? 'EMPTY' : 'RESULTS'
  } catch (error) {
    if (requestId !== loadId || isCanceled(error)) {
      return
    }
    result.value = null
    status.value = 'ERROR'
    errorMessage.value = error instanceof ApiError && error.code === 'SEARCH_UNAVAILABLE'
      ? 'Search is temporarily unavailable.'
      : error instanceof Error
        ? error.message
        : 'Search is temporarily unavailable.'
  }
}

async function applyFilters() {
  await router.push({
    name: 'search',
    query: {
      q: query.value,
      page: '0',
      sort: sort.value,
      processingStatus: processingStatus.value || undefined,
    },
  })
}

async function goToPage(nextPage: number) {
  await router.push({
    name: 'search',
    query: {
      ...route.query,
      q: query.value,
      page: String(Math.max(0, nextPage)),
    },
  })
}

function parseSort(value: unknown): VideoSearchSort {
  if (value === 'NEWEST' || value === 'OLDEST' || value === 'RELEVANCE') {
    return value
  }
  return 'RELEVANCE'
}

function isCanceled(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && 'code' in error && error.code === 'ERR_CANCELED')
}
</script>
