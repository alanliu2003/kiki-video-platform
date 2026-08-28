<template>
  <main>
    <h1>Upload a video</h1>
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
      <label>
        Video file
        <input type="file" accept="video/mp4,video/webm" @change="onFileChange" />
      </label>
      <p v-if="file">Selected: {{ file.name }} ({{ formatFileSize(file.size) }})</p>
      <p v-if="busy" class="progress">{{ progressMessage }}</p>
      <p v-if="dedupeNotice" class="success">{{ dedupeNotice }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>
      <button type="submit" :disabled="busy">
        {{ busy ? 'Working...' : 'Upload' }}
      </button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import { formatFileSize } from '../api/videos'
import { latestResumeHint, type UploadResumeRecord } from '../services/uploadResumeStore'
import { uploadResumable } from '../services/uploadManager'

const router = useRouter()
const title = ref('')
const description = ref('')
const file = ref<File | null>(null)
const busy = ref(false)
const progressMessage = ref('')
const error = ref('')
const success = ref('')
const dedupeNotice = ref('')
const resumeHint = ref<UploadResumeRecord | null>(null)

onMounted(() => {
  resumeHint.value = latestResumeHint()
})

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
  error.value = ''
  success.value = ''
  dedupeNotice.value = ''
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

  busy.value = true
  progressMessage.value = 'Hashing...'
  try {
    const response = await uploadResumable({
      file: file.value,
      title: title.value,
      description: description.value,
      onProgress(progress) {
        progressMessage.value = progress.message
        if (progress.phase === 'deduplicated') {
          dedupeNotice.value = progress.message
        }
      },
    })
    success.value = 'Upload complete.'
    await router.push({ name: 'video-detail', params: { id: String(response.video.id) } })
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to upload the video.'
  } finally {
    busy.value = false
  }
}
</script>
