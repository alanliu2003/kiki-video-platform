<template>
  <button type="button" :disabled="busy || self" @click="onClick">
    {{ self ? 'You' : followed ? 'Unfollow' : 'Follow' }}
  </button>
</template>

<script setup lang="ts">
defineProps<{
  followed: boolean
  busy: boolean
  self: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

function onClick() {
  emit('toggle')
}
</script>
