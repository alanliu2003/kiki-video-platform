<template>
  <main>
    <div class="notifications-header">
      <PageHeader title="Notifications" description="Activity on your videos and profile." />
      <button
        v-if="notifications.items.length > 0"
        type="button"
        class="btn btn-secondary"
        :disabled="notifications.loading || notifications.unreadCount === 0"
        @click="onMarkAll"
      >
        Mark all as read
      </button>
    </div>

    <p v-if="notifications.loading && notifications.items.length === 0" class="progress">
      Loading notifications...
    </p>
    <LoadingSkeleton v-if="notifications.loading && notifications.items.length === 0" :count="3" />
    <p v-else-if="notifications.error" class="error">{{ notifications.error }}</p>
    <EmptyState
      v-else-if="notifications.items.length === 0"
      title="No notifications yet."
      description="Likes, comments, and follows will show up here."
      icon="bell"
    />

    <ul v-if="notifications.items.length > 0" class="notification-list">
      <li
        v-for="item in notifications.items"
        :key="item.id"
        class="notification-item"
        :class="{ unread: !item.read }"
      >
        <button type="button" class="notification-button" @click="onOpen(item)">
          <span class="unread-dot" :style="{ visibility: item.read ? 'hidden' : 'visible' }" aria-hidden="true"></span>
          <img
            v-if="item.video?.thumbnailUrl && !brokenThumbs[item.id]"
            class="notification-thumb"
            :src="item.video.thumbnailUrl"
            :alt="item.video.title || 'Video'"
            @error="brokenThumbs[item.id] = true"
          >
          <div v-else class="notification-thumb notification-thumb-empty thumb-placeholder" aria-hidden="true"></div>
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
      class="btn btn-secondary"
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
import EmptyState from '../components/EmptyState.vue'
import LoadingSkeleton from '../components/LoadingSkeleton.vue'
import PageHeader from '../components/PageHeader.vue'
import { useNotificationsStore } from '../stores/notifications'
import { formatRelativeTime } from '../utils/formatters'
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
  return formatRelativeTime(value)
}
</script>
