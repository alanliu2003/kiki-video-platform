<template>
  <ul class="comment-list">
    <li
        v-for="comment in items"
        :id="'comment-' + comment.id"
        :key="comment.id"
        class="comment-item"
      >
      <div class="comment-head">
        <Avatar :name="comment.author.displayName" :seed="comment.author.id" :size="36" />
        <div>
          <RouterLink class="creator-link" :to="userProfileLocation(comment.author.id)">
            <strong>{{ comment.author.displayName }}</strong>
          </RouterLink>
          <p class="hint">{{ formatDate(comment.createdAt) }}</p>
        </div>
      </div>
      <p class="comment-body">{{ comment.content }}</p>
      <div class="comment-actions">
        <button v-if="canReply" type="button" class="btn btn-ghost" @click="toggleReply(comment.id)">
          {{ replyTo === comment.id ? 'Cancel reply' : 'Reply' }}
        </button>
      </div>
      <ReplyForm
        v-if="replyTo === comment.id"
        :submit="(content) => reply(comment.id, content)"
      />
      <ul v-if="comment.replies?.length" class="comment-replies">
        <li
            v-for="replyItem in comment.replies"
            :id="'comment-' + replyItem.id"
            :key="replyItem.id"
          >
          <div class="comment-head">
            <Avatar :name="replyItem.author.displayName" :seed="replyItem.author.id" :size="32" />
            <div>
              <RouterLink class="creator-link" :to="userProfileLocation(replyItem.author.id)">
                <strong>{{ replyItem.author.displayName }}</strong>
              </RouterLink>
              <p class="hint">{{ formatDate(replyItem.createdAt) }}</p>
            </div>
          </div>
          <p class="comment-body">{{ replyItem.content }}</p>
        </li>
      </ul>
    </li>
  </ul>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { VideoComment } from '../api/comments'
import { userProfileLocation } from '../router/userProfile'
import { formatRelativeTime } from '../utils/formatters'
import Avatar from './Avatar.vue'
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
  return formatRelativeTime(value)
}
</script>
