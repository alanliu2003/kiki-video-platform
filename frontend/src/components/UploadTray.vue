<template>
  <aside v-if="session.visible && route.name !== 'video-upload'" class="upload-tray" aria-label="Uploads">
    <div class="upload-tray-head">
      <strong>Uploads</strong>
      <div>
        <button type="button" class="btn btn-ghost" @click="session.toggleMinimized()">
          {{ session.minimized ? 'Show' : 'Hide' }}
        </button>
        <button type="button" class="btn btn-ghost" :aria-label="session.active ? 'Minimize uploads' : 'Dismiss uploads'" @click="session.dismiss()">
          <AppIcon name="close" :size="16" />
        </button>
      </div>
    </div>
    <p>{{ session.fileName }} <span class="hint">{{ percentLabel }}</span></p>
    <div v-if="!session.minimized">
      <div class="progress-track" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
        <div class="progress-fill" :style="{ width: percent + '%' }"></div>
      </div>
      <p class="hint">{{ statusLabel }}</p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUploadSessionStore } from '../stores/uploadSession'
import AppIcon from './AppIcon.vue'

const session = useUploadSessionStore()
const route = useRoute()

const percent = computed(() => session.progress?.percent ?? 0)
const percentLabel = computed(() => `${percent.value}%`)
const statusLabel = computed(() => {
  if (session.error) {
    return session.error
  }
  return session.progress?.message || 'Uploading…'
})
</script>
