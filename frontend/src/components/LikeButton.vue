<template>
  <button
    type="button"
    class="interaction-btn"
    :class="{ 'is-on': liked }"
    :disabled="busy"
    :aria-pressed="liked"
    :aria-label="liked ? `Unlike, ${count}` : `Like, ${count}`"
    @click="onClick"
  >
    <AppIcon name="heart" />
    {{ liked ? 'Unlike' : 'Like' }} ({{ count }})
  </button>
</template>

<script setup lang="ts">
import AppIcon from './AppIcon.vue'

defineProps<{
  liked: boolean
  count: number
  busy: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

function onClick() {
  emit('toggle')
}
</script>
