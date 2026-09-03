<template>
  <span v-if="label" class="status-pill" :class="toneClass">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string | null
}>()

const normalized = computed(() => (props.status || '').toUpperCase())

const label = computed(() => {
  if (!normalized.value || normalized.value === 'READY') {
    return normalized.value === 'READY' ? 'READY' : ''
  }
  if (normalized.value === 'NOT_REQUESTED') {
    return 'QUEUED'
  }
  return normalized.value
})

const toneClass = computed(() => {
  if (normalized.value === 'READY') {
    return 'status-pill--ready'
  }
  if (normalized.value === 'FAILED') {
    return 'status-pill--failed'
  }
  return 'status-pill--processing'
})
</script>
