<template>
  <ul class="comment-list">
    <li v-for="comment in items" :key="comment.id" class="comment-item">
      <p>
        <strong>{{ comment.author.displayName }}</strong>
        <span class="hint"> @{{ comment.author.username }} · {{ formatDate(comment.createdAt) }}</span>
      </p>
      <p>{{ comment.content }}</p>
      <button v-if="canReply" type="button" @click="toggleReply(comment.id)">
        {{ replyTo === comment.id ? 'Cancel reply' : 'Reply' }}
      </button>
      <ReplyForm
        v-if="replyTo === comment.id"
        :submit="(content) => reply(comment.id, content)"
      />
      <ul v-if="comment.replies?.length" class="comment-replies">
        <li v-for="replyItem in comment.replies" :key="replyItem.id">
          <p>
            <strong>{{ replyItem.author.displayName }}</strong>
            <span class="hint"> @{{ replyItem.author.username }} · {{ formatDate(replyItem.createdAt) }}</span>
          </p>
          <p>{{ replyItem.content }}</p>
        </li>
      </ul>
    </li>
  </ul>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { VideoComment } from '../api/comments'
import ReplyForm from './ReplyForm.vue'

defineProps<{
  items: VideoComment[]
  canReply: boolean
  reply: (parentCommentId: number, content: string) => Promise<void>
}>()

const replyTo = ref<number | null>(null)

function toggleReply(commentId: number) {
  replyTo.value = replyTo.value === commentId ? null : commentId
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}
</script>
