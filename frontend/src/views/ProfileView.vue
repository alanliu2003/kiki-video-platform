<template>
  <main>
    <PageHeader title="Account" description="Your Kiki profile and library." />
    <section v-if="auth.user" class="profile-header">
      <Avatar :name="auth.user.displayName" :seed="auth.user.id" :size="72" :alt="auth.user.displayName" />
      <div>
        <h2 class="section-title">{{ auth.user.displayName }}</h2>
        <p class="hint">@{{ auth.user.username }}</p>
        <p class="hint">{{ auth.user.email }}</p>
        <div class="library-actions" style="margin-top: 16px">
          <RouterLink :to="userProfileLocation(auth.user.id)" class="btn btn-secondary">Public profile</RouterLink>
          <RouterLink to="/my/videos" class="btn btn-secondary">My Videos</RouterLink>
          <button type="button" class="btn btn-ghost" @click="onLogout">Log out</button>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import Avatar from '../components/Avatar.vue'
import PageHeader from '../components/PageHeader.vue'
import { userProfileLocation } from '../router/userProfile'
import { useAuthStore } from '../stores/auth'
import { useNotificationsStore } from '../stores/notifications'

const auth = useAuthStore()
const notifications = useNotificationsStore()
const router = useRouter()

async function onLogout() {
  notifications.stopPolling()
  auth.logout()
  await router.push({ name: 'home' })
}
</script>
