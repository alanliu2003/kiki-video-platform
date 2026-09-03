<template>
  <div class="hls-player">
    <video
      ref="videoEl"
      controls
      playsinline
      preload="metadata"
      :poster="poster || undefined"
    >
      Your browser does not support HTML video playback.
    </video>
    <p v-if="unsupported" class="error">This browser cannot play HLS video.</p>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'HlsPlayer' })
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { attachHlsPlayback, type HlsPlaybackHandle } from '../services/hlsPlayback'

const props = defineProps<{
  src: string
  poster?: string | null
}>()

const emit = defineEmits<{
  fatal: []
}>()

const videoEl = ref<HTMLVideoElement | null>(null)
const unsupported = ref(false)
let handle: HlsPlaybackHandle | null = null

function onUnsupported() {
  unsupported.value = true
}

function onFatal() {
  emit('fatal')
}

function attach(src: string) {
  if (!videoEl.value) {
    return
  }
  handle?.destroy()
  handle = attachHlsPlayback(videoEl.value, src)
}

onMounted(() => {
  if (!videoEl.value) {
    return
  }
  videoEl.value.addEventListener('hlsunsupported', onUnsupported)
  videoEl.value.addEventListener('hlserror', onFatal)
  attach(props.src)
})

watch(
  () => props.src,
  (src) => {
    if (!src) {
      return
    }
    attach(src)
  },
)

onUnmounted(() => {
  videoEl.value?.removeEventListener('hlsunsupported', onUnsupported)
  videoEl.value?.removeEventListener('hlserror', onFatal)
  handle?.destroy()
  handle = null
})

defineExpose({
  get videoElement() {
    return videoEl.value
  },
})
</script>
