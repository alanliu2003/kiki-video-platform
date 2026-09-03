<template>
  <main>
    <PageHeader title="My Videos" description="Manage your uploaded videos and processing status.">
      <template #actions>
        <RouterLink to="/videos/upload" class="btn btn-primary">Upload</RouterLink>
      </template>
    </PageHeader>

    <p v-if="loading" class="progress">Loading your videos...</p>
    <LoadingSkeleton v-if="loading" :count="4" />
    <p v-else-if="error" class="error">{{ error }}</p>
    <EmptyState
      v-else-if="videos.length === 0"
      title="No videos yet"
      description="Upload your first video."
      icon="upload"
    >
      <RouterLink to="/videos/upload" class="btn btn-primary">Upload</RouterLink>
    </EmptyState>
    <VideoGrid v-else>
      <VideoCard
        v-for="item in cards"
        :key="item.id"
        :item="item"
        show-status
      />
    </VideoGrid>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { isApiError } from '../api/auth'
import { formatFileSize, getMyVideos, videoThumbnailUrl, type VideoSummary } from '../api/videos'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import VideoCard, { type VideoCardItem } from '../components/VideoCard.vue'
import VideoGrid from '../components/VideoGrid.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const videos = ref<VideoSummary[]>([])
const loading = ref(true)
const error = ref('')

const cards = computed<VideoCardItem[]>(() =>
  videos.value.map((item) => ({
    id: item.id,
    title: item.title,
    owner: auth.user
      ? { id: auth.user.id, username: auth.user.username, displayName: auth.user.displayName }
      : undefined,
    createdAt: item.createdAt,
    thumbnailUrl: videoThumbnailUrl(item.id),
    processingStatus: item.processingStatus || item.status,
    viewCount: item.viewCount,
    fileSizeLabel: formatFileSize(item.fileSizeBytes),
  })),
)

onMounted(async () => {
  try {
    const response = await getMyVideos()
    videos.value = response.data.items
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to load your videos.'
  } finally {
    loading.value = false
  }
})

</script>
