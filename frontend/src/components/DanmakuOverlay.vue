<template>
  <div ref="rootEl" class="danmaku-overlay" :class="{ 'is-paused': paused }" aria-hidden="true">
    <span
      v-for="item in rendered"
      :key="item.key"
      class="danmaku-item"
      :style="{ top: `${item.lane * laneHeight}px`, animationDuration: `${durationSec}s` }"
      @animationend="onFinished(item.key)"
    >{{ item.content }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ActiveDanmaku } from '../stores/danmaku'

const props = defineProps<{
  items: ActiveDanmaku[]
  paused: boolean
}>()

const emit = defineEmits<{
  finished: [key: string]
}>()

const rootEl = ref<HTMLElement | null>(null)
const laneHeight = 28
const durationSec = 8
const busyUntil = ref<number[]>([])
const lanesByKey = ref<Record<string, number>>({})

const laneCount = computed(() => {
  const height = rootEl.value?.clientHeight ?? 180
  return Math.max(3, Math.floor(height / laneHeight))
})

const rendered = computed(() =>
  props.items.map((item) => ({
    ...item,
    lane: lanesByKey.value[item.key] ?? 0,
  })),
)

function allocateLane(): number {
  const now = Date.now()
  const count = laneCount.value
  const next = [...busyUntil.value]
  for (let i = 0; i < count; i++) {
    if ((next[i] ?? 0) <= now) {
      next[i] = now + 4000
      busyUntil.value = next
      return i
    }
  }
  let best = 0
  for (let i = 1; i < count; i++) {
    if ((next[i] ?? 0) < (next[best] ?? 0)) {
      best = i
    }
  }
  next[best] = now + 4000
  busyUntil.value = next
  return best
}

watch(
  () => props.items.map((item) => item.key),
  (keys) => {
    const current = { ...lanesByKey.value }
    for (const key of keys) {
      if (current[key] === undefined) {
        current[key] = allocateLane()
      }
    }
    for (const key of Object.keys(current)) {
      if (!keys.includes(key)) {
        delete current[key]
      }
    }
    lanesByKey.value = current
  },
)

function onFinished(key: string) {
  emit('finished', key)
}
</script>
