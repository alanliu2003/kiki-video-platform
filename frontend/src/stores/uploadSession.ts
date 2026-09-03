import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { isApiError } from '../api/auth'
import { uploadResumable, type UploadProgress } from '../services/uploadManager'

export const useUploadSessionStore = defineStore('uploadSession', () => {
  const fileName = ref('')
  const progress = ref<UploadProgress | null>(null)
  const error = ref('')
  const videoId = ref<number | null>(null)
  const minimized = ref(false)
  const dismissed = ref(true)
  let generation = 0

  const active = computed(() => {
    const phase = progress.value?.phase
    return Boolean(phase && phase !== 'complete' && phase !== 'deduplicated' && !error.value)
  })

  const visible = computed(() => !dismissed.value && Boolean(fileName.value) && (active.value || Boolean(error.value) || Boolean(progress.value)))

  async function start(options: { file: File; title: string; description: string }) {
    const token = ++generation
    fileName.value = options.file.name
    progress.value = null
    error.value = ''
    videoId.value = null
    minimized.value = false
    dismissed.value = false

    try {
      const response = await uploadResumable({
        file: options.file,
        title: options.title,
        description: options.description,
        onProgress(next) {
          if (token === generation) {
            progress.value = next
          }
        },
      })
      if (token === generation) {
        videoId.value = response.video.id
        window.setTimeout(() => {
          if (token === generation && !active.value && !error.value) {
            dismissed.value = true
          }
        }, 6000)
      }
      return response
    } catch (err) {
      if (token === generation) {
        error.value = isApiError(err) ? err.message : 'Unable to upload the video.'
      }
      throw err
    }
  }

  function toggleMinimized() {
    minimized.value = !minimized.value
  }

  function dismiss() {
    if (active.value) {
      minimized.value = true
      return
    }
    dismissed.value = true
  }

  return {
    fileName,
    progress,
    error,
    videoId,
    minimized,
    dismissed,
    active,
    visible,
    start,
    toggleMinimized,
    dismiss,
  }
})
