<template>
  <span class="avatar" :style="style" :aria-hidden="alt ? undefined : true" :aria-label="alt || undefined">
    {{ initials }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  seed?: number | string
  size?: number
  alt?: string
}>(), {
  size: 36,
})

const palettes = ['#3b4660', '#3d3558', '#2f4a4a', '#4a3a3a', '#33445a']

const initials = computed(() => {
  const parts = props.name.trim().split(/\s+/).filter(Boolean)
  if (parts.length >= 2) {
    return `${parts[0][0] ?? ''}${parts[1][0] ?? ''}`.toUpperCase()
  }
  return props.name.trim().slice(0, 2).toUpperCase() || '?'
})

const style = computed(() => {
  const key = String(props.seed ?? props.name)
  let hash = 0
  for (let i = 0; i < key.length; i += 1) {
    hash = (hash + key.charCodeAt(i)) % palettes.length
  }
  return {
    width: `${props.size}px`,
    height: `${props.size}px`,
    fontSize: `${Math.max(11, Math.round(props.size * 0.36))}px`,
    background: palettes[hash],
  }
})
</script>
