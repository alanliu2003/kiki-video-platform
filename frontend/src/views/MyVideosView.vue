<template>
  <main>
    <h1>My videos</h1>
    <p v-if="loading">Loading your videos...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="videos.length === 0">You have not uploaded any videos yet.</p>
    <table v-else>
      <thead>
        <tr>
          <th>Title</th>
          <th>Status</th>
          <th>Size</th>
          <th>Views</th>
          <th>Created</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in videos" :key="item.id">
          <td>
            <RouterLink :to="{ name: 'video-detail', params: { id: String(item.id) } }">
              {{ item.title }}
            </RouterLink>
          </td>
          <td>{{ item.processingStatus || item.status }}</td>
          <td>{{ formatFileSize(item.fileSizeBytes) }}</td>
          <td>{{ formatViewCount(item.viewCount) }}</td>
          <td>{{ formatDate(item.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { isApiError } from '../api/auth'
import { formatFileSize, getMyVideos, type VideoSummary } from '../api/videos'
import { formatViewCount } from '../utils/formatters'

const videos = ref<VideoSummary[]>([])
const loading = ref(true)
const error = ref('')

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}

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
