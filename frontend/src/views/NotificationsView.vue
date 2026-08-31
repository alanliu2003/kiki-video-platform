<template>
  <main>
    <div class="notifications-header">
      <h1>Notifications</h1>
      <button
        v-if="notifications.items.length > 0"
        type="button"
        :disabled="notifications.loading || notifications.unreadCount === 0"
        @click="onMarkAll"
      >
        Mark all as read
      </button>
    </div>

    <p v-if="notifications.loading && notifications.items.length === 0" class="progress">
      Loading notifications...
    </p>
    <p v-else-if="notifications.error" class="error">{{ notifications.error }}</p>
    <p v-else-if="notifications.items.length === 0" class="hint">No notifications yet.</p>

    <ul v-if="notifications.items.length > 0" class="notification-list">
      <li
        v-for="item in notifications.items"
        :key="item.id"
        class="notification-item"
        :class="{ unread: !item.read }"
      >
        <button type="button" class="notification-button" @click="onOpen(item)">
          <img
            v-if="item.video?.thumbnailUrl && !brokenThumbs[item.id]"
            class="notification-thumb"
            :src="item.video.thumbnailUrl"
            :alt="item.video.title || 'Video'"
            @error="brokenThumbs[item.id] = true"
          >
          <div v-else class="notification-thumb notification-thumb-empty" aria-hidden="true"></div>
          <div>
            <p>
              <strong>{{ notificationActorName(item) }}</strong>
              {{ notificationLine(item) }}
            </p>
            <p v-if="item.comment?.contentSnippet" class="hint">{{ item.comment.contentSnippet }}</p>
            <p class="hint">{{ formatDate(item.createdAt) }}</p>
          </div>
        </button>
      </li>
    </ul>

    <button
      v-if="notifications.hasMore"
      type="button"
      :disabled="notifications.loading"
      @click="onLoadMore"
    >
      Load more
    </button>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import type { NotificationItem } from '../api/notifications'
import { useNotificationsStore } from '../stores/notifications'
import {
  notificationActionText,
  notificationActorName,
  notificationTarget,
} from '../utils/notificationCopy'

const notifications = useNotificationsStore()
const router = useRouter()
const brokenThumbs = reactive<Record<number, boolean>>({})

onMounted(() => {
  void notifications.loadInbox(true)
})

async function onOpen(item: NotificationItem) {
  if (!item.read) {
    try {
      await notifications.markRead(item.id)
    } catch (err) {
      notifications.error = isApiError(err) ? err.message : 'Unable to mark notification as read.'
      return
    }
  }
  const target = notificationTarget(item)
  if (target) {
    await router.push(target)
  }
}

async function onMarkAll() {
  try {
    await notifications.markAllRead()
  } catch (err) {
    notifications.error = isApiError(err) ? err.message : 'Unable to mark notifications as read.'
  }
}

async function onLoadMore() {
  await notifications.loadInbox(false)
}

function notificationLine(item: NotificationItem): string {
  const action = notificationActionText(item.type)
  return item.video?.title ? `${action} "${item.video.title}"` : action
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}
</script>
