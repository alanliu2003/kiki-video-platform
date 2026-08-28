<template>
  <section class="comments-section">
    <h2>Comments</h2>
    <p v-if="!auth.isAuthenticated" class="hint">
      <RouterLink :to="{ name: 'login', query: { redirect: currentPath } }">Log in</RouterLink>
      to post a comment.
    </p>
    <CommentForm v-else :submit="onCreate" />
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">Loading comments...</p>
    <CommentList
      v-else
      :items="items"
      :can-reply="auth.isAuthenticated"
      :reply="onReply"
    />
    <p v-if="!loading && items.length === 0" class="hint">No comments yet.</p>
    <button
      v-if="hasMore"
      type="button"
      :disabled="loading"
      @click="loadMore"
    >
      Load more comments
    </button>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { createComment, listComments, type VideoComment } from '../api/comments'
import { isApiError } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import CommentForm from './CommentForm.vue'
import CommentList from './CommentList.vue'

const props = defineProps<{
  videoId: number
}>()

const emit = defineEmits<{
  created: []
}>()

const auth = useAuthStore()
const route = useRoute()
const items = ref<VideoComment[]>([])
const page = ref(0)
const total = ref(0)
const loading = ref(true)
const error = ref('')
const pageSize = 20
const currentPath = computed(() => route.fullPath)
const hasMore = computed(() => items.value.length < total.value)

async function refresh(reset = false) {
  loading.value = true
  error.value = ''
  try {
    const nextPage = reset ? 0 : page.value
    const response = await listComments(props.videoId, nextPage, pageSize)
    items.value = reset ? response.data.items : [...items.value, ...response.data.items]
    page.value = response.data.page
    total.value = response.data.total
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to load comments.'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  page.value += 1
  await refresh(false)
}

async function onCreate(content: string) {
  error.value = ''
  try {
    await createComment(props.videoId, content)
    await refresh(true)
    emit('created')
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to post comment.'
    throw err
  }
}

async function onReply(parentCommentId: number, content: string) {
  error.value = ''
  try {
    await createComment(props.videoId, content, parentCommentId)
    await refresh(true)
    emit('created')
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to post reply.'
    throw err
  }
}

onMounted(() => {
  void refresh(true)
})
</script>
