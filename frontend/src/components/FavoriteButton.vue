<template>
  <button type="button" :disabled="busy" @click="onClick">
    {{ favorited ? 'Unfavorite' : 'Favorite' }} ({{ count }})
  </button>
</template>

<script setup lang="ts">
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
