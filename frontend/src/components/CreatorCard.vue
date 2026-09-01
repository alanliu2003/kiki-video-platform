<template>
  <section class="creator-card">
    <p>
      Creator:
      <RouterLink class="creator-link" :to="userProfileLocation(userId)">{{ displayName }}</RouterLink>
      (@{{ username }})
      <span class="hint">{{ followerCount }} followers</span>
    </p>
    <FollowButton v-if="auth.isAuthenticated" :followed="followed" :busy="busy" :self="self" @toggle="onFollow" />
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { userProfileLocation } from '../router/userProfile'
import { isApiError } from '../api/auth'
import { followUser, unfollowUser, type CreatorRelationship } from '../api/users'
import { useAuthStore } from '../stores/auth'
import FollowButton from './FollowButton.vue'

const props = defineProps<{
  userId: number
  username: string
  displayName: string
  relationship: CreatorRelationship
}>()

const emit = defineEmits<{
  'update:relationship': [value: CreatorRelationship]
}>()

const auth = useAuthStore()
const router = useRouter()
const busy = ref(false)
const error = ref('')

const followerCount = computed(() => props.relationship.followerCount)
const followed = computed(() => props.relationship.followedByCurrentUser)
const self = computed(() => auth.user?.id === props.userId)

async function onFollow() {
  if (self.value || busy.value) {
    return
  }
  if (!auth.isAuthenticated) {
    await router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  const previous = { ...props.relationship }
  const nextFollowed = !previous.followedByCurrentUser
  emit('update:relationship', {
    followedByCurrentUser: nextFollowed,
    followerCount: previous.followerCount + (nextFollowed ? 1 : -1),
  })
  busy.value = true
  error.value = ''
  try {
    const response = nextFollowed ? await followUser(props.userId) : await unfollowUser(props.userId)
    emit('update:relationship', response.data)
  } catch (err) {
    emit('update:relationship', previous)
    error.value = isApiError(err) ? err.message : 'Unable to update follow.'
  } finally {
    busy.value = false
  }
}
</script>
