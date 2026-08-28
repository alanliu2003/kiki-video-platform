<template>
  <div class="interaction-bar">
    <LikeButton :liked="liked" :count="likeCount" :busy="busy" @toggle="onLike" />
    <FavoriteButton :favorited="favorited" :count="favoriteCount" :busy="busy" @toggle="onFavorite" />
    <span class="hint">{{ commentCount }} comments</span>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import {
  favoriteVideo,
  likeVideo,
  unfavoriteVideo,
  unlikeVideo,
  type VideoInteractions,
} from '../api/interactions'
import { useAuthStore } from '../stores/auth'
import FavoriteButton from './FavoriteButton.vue'
import LikeButton from './LikeButton.vue'

const props = defineProps<{
  videoId: number
  interactions: VideoInteractions
}>()

const emit = defineEmits<{
  'update:interactions': [value: VideoInteractions]
}>()

const auth = useAuthStore()
const router = useRouter()
const busy = ref(false)
const error = ref('')

const liked = computed(() => props.interactions.likedByCurrentUser)
const favorited = computed(() => props.interactions.favoritedByCurrentUser)
const likeCount = computed(() => props.interactions.likeCount)
const favoriteCount = computed(() => props.interactions.favoriteCount)
const commentCount = computed(() => props.interactions.commentCount)

async function requireAuth(): Promise<boolean> {
  if (auth.isAuthenticated) {
    return true
  }
  await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  return false
}

function apply(next: VideoInteractions) {
  emit('update:interactions', next)
}

async function onLike() {
  if (!(await requireAuth()) || busy.value) {
    return
  }
  const previous = { ...props.interactions }
  const nextLiked = !previous.likedByCurrentUser
  apply({
    ...previous,
    likedByCurrentUser: nextLiked,
    likeCount: previous.likeCount + (nextLiked ? 1 : -1),
  })
  busy.value = true
  error.value = ''
  try {
    const response = nextLiked ? await likeVideo(props.videoId) : await unlikeVideo(props.videoId)
    apply(response.data)
  } catch (err) {
    apply(previous)
    error.value = isApiError(err) ? err.message : 'Unable to update like.'
  } finally {
    busy.value = false
  }
}

async function onFavorite() {
  if (!(await requireAuth()) || busy.value) {
    return
  }
  const previous = { ...props.interactions }
  const nextFavorited = !previous.favoritedByCurrentUser
  apply({
    ...previous,
    favoritedByCurrentUser: nextFavorited,
    favoriteCount: previous.favoriteCount + (nextFavorited ? 1 : -1),
  })
  busy.value = true
  error.value = ''
  try {
    const response = nextFavorited
      ? await favoriteVideo(props.videoId)
      : await unfavoriteVideo(props.videoId)
    apply(response.data)
  } catch (err) {
    apply(previous)
    error.value = isApiError(err) ? err.message : 'Unable to update favorite.'
  } finally {
    busy.value = false
  }
}
</script>
