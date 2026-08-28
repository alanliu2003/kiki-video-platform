<template>
  <div>
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
import { onMounted, onUnmounted, ref } from 'vue'
import { attachHlsPlayback, type HlsPlaybackHandle } from '../services/hlsPlayback'

const props = defineProps<{
  src: string
  poster?: string | null
}>()

const videoEl = ref<HTMLVideoElement | null>(null)
const unsupported = ref(false)
let handle: HlsPlaybackHandle | null = null

function onUnsupported() {
  unsupported.value = true
}

onMounted(() => {
  if (!videoEl.value) {
    return
  }
  videoEl.value.addEventListener('hlsunsupported', onUnsupported)
  handle = attachHlsPlayback(videoEl.value, props.src)
})

onUnmounted(() => {
  videoEl.value?.removeEventListener('hlsunsupported', onUnsupported)
  handle?.destroy()
  handle = null
})
</script>
