<template>
  <main>
    <PageHeader title="Upload" description="Resumable uploads continue from the missing chunks if the same file is selected again." />

    <p v-if="resumeHint" class="hint">
      Previous upload found for {{ resumeHint.fileName }}. Re-select the same file to resume missing chunks.
    </p>

    <form @submit.prevent="onSubmit">
      <label>
        Title
        <input v-model.trim="title" type="text" maxlength="120" required />
      </label>
      <label>
        Description
        <textarea v-model="description" maxlength="2000" rows="4"></textarea>
      </label>

      <div
        class="drop-zone"
        :class="{ 'is-active': dragActive }"
        @dragenter.prevent="dragActive = true"
        @dragover.prevent="dragActive = true"
        @dragleave.prevent="dragActive = false"
        @drop.prevent="onDrop"
      >
        <AppIcon name="upload" :size="28" />
        <p>Drop videos here</p>
        <p class="hint">or choose a file from your computer</p>
        <p class="hint">MP4 or WebM. Upload progress is resumable.</p>
        <label class="btn btn-secondary">
          Choose file
          <input class="visually-hidden" type="file" accept="video/mp4,video/webm" @change="onFileChange" />
        </label>
        <p v-if="file">Selected: {{ file.name }} ({{ formatFileSize(file.size) }})</p>
      </div>

      <div v-if="busy || session.progress" class="upload-card">
        <p>
          <strong>{{ session.fileName || file?.name }}</strong>
          <span class="hint"> {{ session.progress?.percent ?? 0 }}%</span>
        </p>
        <div class="progress-track" role="progressbar" :aria-valuenow="session.progress?.percent ?? 0" aria-valuemin="0" aria-valuemax="100">
          <div class="progress-fill" :style="{ width: (session.progress?.percent ?? 0) + '%' }"></div>
        </div>
        <p v-if="byteLabel" class="hint">{{ byteLabel }}</p>
        <p class="progress">{{ progressMessage || session.progress?.message }}</p>
        <p class="hint">Upload progress is resumable.</p>
      </div>

      <p v-if="dedupeNotice" class="success">{{ dedupeNotice }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>
      <button type="submit" class="btn btn-primary" :disabled="busy">
        {{ busy ? 'Working...' : 'Upload' }}
      </button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import { formatFileSize } from '../api/videos'
import AppIcon from '../components/AppIcon.vue'
import PageHeader from '../components/PageHeader.vue'
import { latestResumeHint, type UploadResumeRecord } from '../services/uploadResumeStore'
import { useUploadSessionStore } from '../stores/uploadSession'

const router = useRouter()
const session = useUploadSessionStore()
const title = ref('')
const description = ref('')
const file = ref<File | null>(null)
const busy = ref(false)
const progressMessage = ref('')
const error = ref('')
const success = ref('')
const dedupeNotice = ref('')
const resumeHint = ref<UploadResumeRecord | null>(null)
const dragActive = ref(false)

const byteLabel = computed(() => {
  const progress = session.progress
  if (!progress || progress.totalBytes <= 0 || progress.phase === 'hashing' || progress.phase === 'checking') {
    return ''
  }
  return `${formatFileSize(progress.uploadedBytes)} / ${formatFileSize(progress.totalBytes)}`
})

onMounted(() => {
  resumeHint.value = latestResumeHint()
})

function isSupportedVideo(selected: File): boolean {
  if (selected.type === 'video/mp4' || selected.type === 'video/webm') {
    return true
  }
  return /\.(mp4|webm)$/i.test(selected.name)
}

function assignFile(selected: File | null) {
  file.value = selected
  error.value = ''
  success.value = ''
  dedupeNotice.value = ''
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  assignFile(input.files?.[0] ?? null)
}

function onDrop(event: DragEvent) {
  dragActive.value = false
  assignFile(event.dataTransfer?.files?.[0] ?? null)
}

async function onSubmit() {
  error.value = ''
  success.value = ''
  dedupeNotice.value = ''
  if (!title.value || title.value.length > 120) {
    error.value = 'Title must be between 1 and 120 characters.'
    return
  }
  if (!file.value) {
    error.value = 'Choose a video file to upload.'
    return
  }
  if (!isSupportedVideo(file.value)) {
    error.value = 'Use an MP4 or WebM video file.'
    return
  }

  busy.value = true
  progressMessage.value = 'Hashing...'
  try {
    const response = await session.start({
      file: file.value,
      title: title.value,
      description: description.value,
    })
    progressMessage.value = session.progress?.message || 'Upload complete.'
    if (session.progress?.phase === 'deduplicated') {
      dedupeNotice.value = session.progress.message
    }
    success.value = 'Upload complete.'
    await router.push({ name: 'video-detail', params: { id: String(response.video.id) } })
  } catch (err) {
    error.value = isApiError(err) ? err.message : session.error || 'Unable to upload the video.'
  } finally {
    busy.value = false
  }
}
</script>
