<template>
  <RouterLink
    to="/notifications"
    class="notification-link"
    :aria-label="bellLabel"
  >
    Notifications
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
