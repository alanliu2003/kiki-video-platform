<template>
  <main>
    <p v-if="status === 'LOADING'" class="progress">Loading profile…</p>
    <div v-if="status === 'LOADING'" class="profile-header">
      <div class="skeleton avatar" style="width: 72px; height: 72px; border-radius: 50%"></div>
      <div style="flex: 1">
        <div class="skeleton skeleton-line" style="width: 180px; height: 22px"></div>
        <div class="skeleton skeleton-line" style="width: 120px"></div>
      </div>
    </div>
    <p v-else-if="status === 'ERROR'" class="error">{{ error }}</p>
    <section v-else-if="profile">
      <header class="profile-header">
        <Avatar :name="profile.displayName" :seed="profile.id" :size="72" :alt="profile.displayName" />
        <div>
          <h1 class="page-title">{{ profile.displayName }}</h1>
          <p class="hint">@{{ profile.username }}</p>
          <div class="profile-stats">
            <p><strong>{{ profile.followerCount }}</strong> followers</p>
            <p><strong>{{ profile.followingCount }}</strong> following</p>
            <p><strong>{{ profile.publicVideoCount }}</strong> videos</p>
            <p><strong>{{ formatCompactCount(profile.totalViews) }}</strong> views</p>
          </div>
        </div>
        <FollowButton
          v-if="showFollow"
          :followed="Boolean(profile.followedByCurrentUser)"
          :busy="busy"
          :self="false"
          @toggle="onFollow"
        />
      </header>
      <p v-if="followError" class="error">{{ followError }}</p>
      <p class="visually-hidden">
        {{ profile.followerCount }} followers · {{ profile.followingCount }} following
        · {{ profile.publicVideoCount }} videos · {{ formatViewCount(profile.totalViews) }}
      </p>

      <section class="feed-section" aria-labelledby="profile-videos-heading">
        <h2 id="profile-videos-heading" class="section-title">Videos</h2>
        <p v-if="videosStatus === 'LOADING'" class="progress">Loading videos…</p>
        <LoadingSkeleton v-if="videosStatus === 'LOADING'" :count="4" />
        <p v-else-if="videosStatus === 'ERROR'" class="error">{{ videosError }}</p>
        <p v-else-if="videos.length === 0" class="hint">This creator has not uploaded any videos yet.</p>
        <VideoGrid v-else>
          <VideoCard v-for="item in videos" :key="item.id" :item="item" />
        </VideoGrid>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { isApiError } from '../api/auth'
import { followUser, getPublicProfile, getUserVideos, unfollowUser, type PublicProfile } from '../api/users'
import type { VideoCard as DiscoveryVideo } from '../api/discovery'
import Avatar from '../components/Avatar.vue'
import FollowButton from '../components/FollowButton.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import VideoCard from '../components/VideoCard.vue'
import VideoGrid from '../components/VideoGrid.vue'
import { useAuthStore } from '../stores/auth'
import { formatCompactCount, formatViewCount } from '../utils/formatters'

const route = useRoute()
const auth = useAuthStore()
const status = ref<'LOADING' | 'READY' | 'ERROR'>('LOADING')
const videosStatus = ref<'LOADING' | 'READY' | 'ERROR'>('LOADING')
const profile = ref<PublicProfile | null>(null)
const videos = ref<DiscoveryVideo[]>([])
const error = ref('')
const videosError = ref('')
const followError = ref('')
const busy = ref(false)

const userId = computed(() => String(route.params.id ?? ''))
const isSelf = computed(() => Boolean(auth.user && profile.value && auth.user.id === profile.value.id))
const showFollow = computed(() => auth.isAuthenticated && Boolean(profile.value) && !isSelf.value)

watch(
  userId,
  () => {
    void load()
  },
  { immediate: true },
)

async function load() {
  const id = userId.value
  if (!id || !/^\d+$/.test(id)) {
    status.value = 'ERROR'
    error.value = 'This profile was not found.'
    profile.value = null
    videos.value = []
    return
  }
  status.value = 'LOADING'
  videosStatus.value = 'LOADING'
  error.value = ''
  videosError.value = ''
  followError.value = ''
  try {
    const [profileResponse, videosResponse] = await Promise.all([
      getPublicProfile(id),
      getUserVideos(id),
    ])
    profile.value = profileResponse.data
    videos.value = videosResponse.data.items
    status.value = 'READY'
    videosStatus.value = 'READY'
  } catch (err) {
    profile.value = null
    videos.value = []
    status.value = 'ERROR'
    videosStatus.value = 'ERROR'
    error.value = isApiError(err) ? err.message : 'Unable to load this profile.'
  }
}

async function onFollow() {
  if (!profile.value || busy.value || isSelf.value) {
    return
  }
  const previous = { ...profile.value }
  const nextFollowed = !previous.followedByCurrentUser
  profile.value = {
    ...previous,
    followedByCurrentUser: nextFollowed,
    followerCount: previous.followerCount + (nextFollowed ? 1 : -1),
  }
  busy.value = true
  followError.value = ''
  try {
    const response = nextFollowed ? await followUser(previous.id) : await unfollowUser(previous.id)
    profile.value = {
      ...profile.value,
      followedByCurrentUser: response.data.followedByCurrentUser,
      followerCount: response.data.followerCount,
    }
  } catch (err) {
    profile.value = previous
    followError.value = isApiError(err) ? err.message : 'Unable to update follow.'
  } finally {
    busy.value = false
  }
}
</script>
