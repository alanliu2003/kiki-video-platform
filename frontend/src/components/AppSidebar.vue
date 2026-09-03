<template>
  <aside class="app-sidebar">
    <BrandMark />

    <nav aria-label="Main" class="app-nav">
      <RouterLink class="app-nav-link" :class="{ 'is-active': route.name === 'home' }" to="/" @click="ui.closeNav()">
        <AppIcon name="home" />
        Home
      </RouterLink>
      <RouterLink class="app-nav-link" :class="{ 'is-active': route.name === 'search' }" to="/search" @click="ui.closeNav()">
        <AppIcon name="search" />
        Search
      </RouterLink>
      <template v-if="auth.isAuthenticated">
        <RouterLink
          class="app-nav-link"
          :class="{ 'is-active': route.name === 'video-upload' }"
          to="/videos/upload"
          @click="ui.closeNav()"
        >
          <AppIcon name="upload" />
          Upload
        </RouterLink>
        <RouterLink
          class="app-nav-link"
          :class="{ 'is-active': route.name === 'my-videos' }"
          to="/my/videos"
          @click="ui.closeNav()"
        >
          <AppIcon name="library" />
          My Videos
        </RouterLink>
        <hr class="app-nav-sep">
        <RouterLink
          class="app-nav-link"
          :class="{ 'is-active': route.name === 'notifications' }"
          to="/notifications"
          @click="ui.closeNav()"
        >
          <AppIcon name="bell" />
          Notifications
        </RouterLink>
        <RouterLink
          class="app-nav-link"
          :class="{ 'is-active': route.name === 'profile' }"
          to="/profile"
          @click="ui.closeNav()"
        >
          <AppIcon name="user" />
          Profile
        </RouterLink>
      </template>
      <template v-else>
        <hr class="app-nav-sep">
        <RouterLink class="app-nav-link" :class="{ 'is-active': route.name === 'login' }" to="/login" @click="ui.closeNav()">
          <AppIcon name="user" />
          Login
        </RouterLink>
        <RouterLink class="app-nav-link" :class="{ 'is-active': route.name === 'register' }" to="/register" @click="ui.closeNav()">
          Register
        </RouterLink>
      </template>
    </nav>

    <div class="app-sidebar-footer">
      <HealthStatus />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import AppIcon from './AppIcon.vue'
import BrandMark from './BrandMark.vue'
import HealthStatus from './HealthStatus.vue'

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
</script>
