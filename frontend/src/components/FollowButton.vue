<template>
  <button
    type="button"
    class="btn"
    :class="self ? 'btn-ghost' : followed ? 'btn-secondary' : 'btn-primary'"
    :disabled="busy || self"
    :aria-label="self ? 'This is you' : followed ? 'Unfollow this creator' : 'Follow this creator'"
    @click="onClick"
  >
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
