<template>
  <button
    type="button"
    class="interaction-btn"
    :class="{ 'is-on': favorited }"
    :disabled="busy"
    :aria-pressed="favorited"
    :aria-label="favorited ? `Unfavorite, ${count}` : `Favorite, ${count}`"
    @click="onClick"
  >
    <AppIcon name="bookmark" />
    {{ favorited ? 'Unfavorite' : 'Favorite' }} ({{ count }})
  </button>
</template>

<script setup lang="ts">
import AppIcon from './AppIcon.vue'

defineProps<{
  favorited: boolean
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
