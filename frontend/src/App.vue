<template>
  <div class="app">
    <header>
      <nav>
        <RouterLink to="/">Home</RouterLink>
        <SearchBar />
        <template v-if="auth.isAuthenticated">
          <NotificationBell />
          <RouterLink to="/videos/upload">Upload</RouterLink>
          <RouterLink to="/my/videos">My videos</RouterLink>
          <RouterLink to="/profile">Profile</RouterLink>
          <button type="button" @click="onLogout">Logout</button>
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
