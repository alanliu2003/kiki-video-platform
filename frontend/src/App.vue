<template>
  <div class="app">
    <header>
      <nav>
        <RouterLink to="/">Home</RouterLink>
        <SearchBar />
        <template v-if="auth.isAuthenticated">
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
import { RouterLink, RouterView, useRouter } from 'vue-router'
import SearchBar from './components/SearchBar.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  auth.logout()
  await router.push({ name: 'home' })
}
</script>
