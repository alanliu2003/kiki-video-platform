<template>
  <div v-if="isAuthPage" class="app app--auth">
    <RouterView />
  </div>
  <div v-else class="app app-shell" :class="{ 'is-nav-open': ui.navOpen }">
    <div v-if="ui.navOpen" class="app-backdrop" @click="ui.closeNav()"></div>
    <AppSidebar />
    <div class="app-main">
      <AppTopbar />
      <div class="app-content" :class="{ 'app-content--detail': route.name === 'video-detail' }">
        <RouterView />
      </div>
    </div>
    <UploadTray />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import AppTopbar from './components/AppTopbar.vue'
import UploadTray from './components/UploadTray.vue'
import { useAuthStore } from './stores/auth'
import { useNotificationsStore } from './stores/notifications'
import { useUiStore } from './stores/ui'

const auth = useAuthStore()
const notifications = useNotificationsStore()
const ui = useUiStore()
const route = useRoute()

const isAuthPage = computed(() => route.name === 'login' || route.name === 'register')

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

watch(
  () => route.fullPath,
  () => {
    ui.closeNav()
  },
)

</script>
