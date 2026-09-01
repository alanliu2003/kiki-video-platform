<template>
  <div class="app">
    <header>
      <nav aria-label="Main">
        <RouterLink to="/">Home</RouterLink>
        <SearchBar />
        <template v-if="auth.isAuthenticated">
          <RouterLink to="/videos/upload">Upload</RouterLink>
          <RouterLink to="/my/videos">My videos</RouterLink>
          <NotificationBell />
          <details class="account-menu">
            <summary>{{ auth.user?.displayName || 'Account' }}</summary>
            <RouterLink v-if="auth.user" :to="userProfileLocation(auth.user.id)">Public profile</RouterLink>
            <RouterLink to="/profile">Account</RouterLink>
            <button type="button" @click="onLogout">Log out</button>
          </details>
        </template>
        <template v-else>
          <RouterLink to="/login">Login</RouterLink>
          <RouterLink to="/register">Register</RouterLink>
        </template>
      </nav>
    </header>
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import NotificationBell from './components/NotificationBell.vue'
import SearchBar from './components/SearchBar.vue'
import { userProfileLocation } from './router/userProfile'
import { useAuthStore } from './stores/auth'
import { useNotificationsStore } from './stores/notifications'

const auth = useAuthStore()
const notifications = useNotificationsStore()
const router = useRouter()

watch(
  () => auth.isAuthenticated,
  (signedIn) => {
    if (signedIn) {
      notifications.startPolling()
    } else {
      notifications.stopPolling()
    }
  },
  { immediate: true },
)

async function onLogout() {
  notifications.stopPolling()
  auth.logout()
  await router.push({ name: 'home' })
}
</script>
