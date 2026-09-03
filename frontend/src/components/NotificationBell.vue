<template>
  <RouterLink
    to="/notifications"
    class="notification-link btn btn-ghost btn-icon"
    :aria-label="bellLabel"
  >
    <AppIcon name="bell" />
    <span class="visually-hidden">Notifications</span>
    <span
      v-if="unreadLabel"
      class="notification-badge"
      aria-hidden="true"
    >
      {{ unreadLabel }}
    </span>
    <span v-if="unreadLabel" class="visually-hidden">{{ unreadLabel }} unread</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useNotificationsStore } from '../stores/notifications'
import AppIcon from './AppIcon.vue'

const notifications = useNotificationsStore()
const unreadLabel = computed(() => {
  const count = notifications.unreadCount
  if (count < 1) {
    return ''
  }
  return count > 99 ? '99+' : String(count)
})
const bellLabel = computed(() => {
  if (!unreadLabel.value) {
    return 'Notifications'
  }
  return `Notifications, ${unreadLabel.value} unread`
})
</script>
