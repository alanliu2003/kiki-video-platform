<template>
  <main>
    <h1>Upload a video</h1>
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
      <p v-if="uploading" class="progress">
        {{ progress === null ? 'Uploading...' : `Uploading ${progress}%` }}
      </p>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>
      <button type="submit" :disabled="uploading">
        {{ uploading ? 'Uploading...' : 'Upload' }}
      </button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import { formatFileSize, uploadVideo } from '../api/videos'

const router = useRouter()
const title = ref('')
const description = ref('')
const file = ref<File | null>(null)
const uploading = ref(false)
const progress = ref<number | null>(null)
const error = ref('')
const success = ref('')

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

async function onSubmit() {
  error.value = ''
  success.value = ''
  if (!title.value || title.value.length > 120) {
    error.value = 'Title must be between 1 and 120 characters.'
    return
  }
  if (!file.value) {
    error.value = 'Choose a video file to upload.'
    return
  }

  uploading.value = true
  progress.value = 0
  try {
    const response = await uploadVideo({
      title: title.value,
      description: description.value,
      file: file.value,
      onProgress(percent) {
        progress.value = percent
      },
    })
    success.value = 'Upload complete.'
    await router.push({ name: 'video-detail', params: { id: String(response.data.id) } })
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to upload the video.'
  } finally {
    uploading.value = false
  }
}
</script>
