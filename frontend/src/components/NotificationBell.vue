<template>
  <RouterLink to="/notifications" class="notification-link">
    Notifications
    <span v-if="unreadLabel" class="notification-badge" aria-label="Unread notifications">
      {{ unreadLabel }}
    </span>
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
</script>
