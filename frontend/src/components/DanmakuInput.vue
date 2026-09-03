<template>
  <form class="danmaku-input" @submit.prevent="onSubmit">
    <label>
      Danmaku
      <input
        v-model="draft"
        type="text"
        maxlength="200"
        :disabled="disabled"
        :placeholder="disabled ? 'Log in to send danmaku' : 'Send a comment on the video'"
      >
    </label>
    <span class="hint">{{ draft.length }}/200</span>
    <button type="submit" class="btn btn-secondary" :disabled="disabled || !draft.trim()">Send</button>
    <p v-if="error" class="error">{{ error }}</p>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  disabled: boolean
  error: string
}>()

const emit = defineEmits<{
  send: [content: string]
}>()

const draft = ref('')

function onSubmit() {
  const content = draft.value.trim()
  if (!content) {
    return
  }
  emit('send', content)
  draft.value = ''
}
</script>
