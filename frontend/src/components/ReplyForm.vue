<template>
  <CommentForm label="Reply" submit-label="Post reply" :submit="onSubmit" />
</template>

<script setup lang="ts">
import CommentForm from './CommentForm.vue'

const props = defineProps<{
  submit: (content: string) => Promise<void>
}>()

function onSubmit(content: string) {
  return props.submit(content)
}
</script>
