<template>
  <form class="comment-form" @submit.prevent="onSubmit">
    <label>
      {{ label }}
      <textarea v-model="content" maxlength="2000" :disabled="submitting" required />
    </label>
    <p v-if="error" class="error">{{ error }}</p>
    <button type="submit" class="btn btn-primary" :disabled="submitting">
      {{ submitting ? 'Posting...' : submitLabel }}
    </button>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { isApiError } from '../api/auth'

const props = withDefaults(defineProps<{
  label?: string
  submitLabel?: string
  submit: (content: string) => Promise<void>
}>(), {
  label: 'Add a comment',
  submitLabel: 'Post comment',
})

const content = ref('')
const error = ref('')
const submitting = ref(false)

async function onSubmit() {
  error.value = ''
  const trimmed = content.value.trim()
  if (!trimmed) {
    error.value = 'Comment cannot be empty.'
    return
  }
  submitting.value = true
  try {
    await props.submit(trimmed)
    content.value = ''
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to post comment.'
  } finally {
    submitting.value = false
  }
}
</script>
