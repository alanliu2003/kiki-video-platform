<template>
  <button type="button" :disabled="busy" @click="onClick">
    {{ liked ? 'Unlike' : 'Like' }} ({{ count }})
  </button>
</template>

<script setup lang="ts">
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
