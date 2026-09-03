<template>
  <header class="app-topbar">
    <button
      type="button"
      class="app-menu-toggle"
      :aria-label="ui.navOpen ? 'Close navigation' : 'Open navigation'"
      :aria-expanded="ui.navOpen"
      @click="ui.toggleNav()"
    >
      <AppIcon :name="ui.navOpen ? 'close' : 'menu'" />
    </button>
    <RouterLink to="/" class="app-brand-compact" aria-label="Kiki home">K</RouterLink>

    <SearchBar />

    <div class="topbar-actions">
      <RouterLink
        v-if="auth.isAuthenticated"
        to="/videos/upload"
        class="btn btn-primary topbar-upload"
      >
        Upload
      </RouterLink>
      <NotificationBell v-if="auth.isAuthenticated" />
      <details v-if="auth.isAuthenticated" class="account-menu">
        <summary>
          <Avatar :name="auth.user?.displayName || 'Account'" :seed="auth.user?.id" :size="32" />
          <span>{{ auth.user?.displayName || 'Account' }}</span>
        </summary>
        <div class="account-menu-panel">
          <RouterLink v-if="auth.user" :to="userProfileLocation(auth.user.id)" @click="closeMenu">
            Public profile
          </RouterLink>
          <RouterLink to="/profile" @click="closeMenu">Account</RouterLink>
          <button type="button" @click="onLogout">Log out</button>
        </div>
      </details>
      <template v-else>
        <RouterLink to="/login" class="btn btn-ghost topbar-auth">Login</RouterLink>
        <RouterLink to="/register" class="btn btn-secondary topbar-auth">Register</RouterLink>
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { RouterLink, useRouter } from 'vue-router'
import { userProfileLocation } from '../router/userProfile'
import { useAuthStore } from '../stores/auth'
import { useNotificationsStore } from '../stores/notifications'
import { useUiStore } from '../stores/ui'
import AppIcon from './AppIcon.vue'
import Avatar from './Avatar.vue'
import NotificationBell from './NotificationBell.vue'
import SearchBar from './SearchBar.vue'

const auth = useAuthStore()
const notifications = useNotificationsStore()
const ui = useUiStore()
const router = useRouter()

function closeMenu() {
  const open = document.querySelector('.account-menu[open]')
  if (open instanceof HTMLDetailsElement) {
    open.removeAttribute('open')
  }
}

async function onLogout() {
  notifications.stopPolling()
  auth.logout()
  closeMenu()
  ui.closeNav()
  await router.push({ name: 'home' })
}
</script>
