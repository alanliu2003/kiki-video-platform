import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getNotificationUnreadCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationItem,
} from '../api/notifications'

const PAGE_SIZE = 20
const POLL_MS = 30_000

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref<NotificationItem[]>([])
  const page = ref(0)
  const total = ref(0)
  const unreadCount = ref(0)
  const loading = ref(false)
  const error = ref('')
  const polling = ref(false)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  const hasMore = computed(() => items.value.length < total.value)

  async function refreshUnread() {
    const response = await getNotificationUnreadCount()
    unreadCount.value = response.data.unreadCount
    return unreadCount.value
  }

  async function loadInbox(reset = true) {
    loading.value = true
    error.value = ''
    try {
      const nextPage = reset ? 0 : page.value + 1
      const response = await listNotifications(nextPage, PAGE_SIZE)
      items.value = reset ? response.data.items : [...items.value, ...response.data.items]
      page.value = response.data.page
      total.value = response.data.total
      await refreshUnread()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Unable to load notifications.'
      if (reset) {
        items.value = []
        total.value = 0
      }
    } finally {
      loading.value = false
    }
  }

  async function markRead(id: number) {
    const response = await markNotificationRead(id)
    unreadCount.value = response.data.unreadCount
    items.value = items.value.map((item) => (item.id === id ? { ...item, read: true } : item))
  }

  async function markAllRead() {
    const response = await markAllNotificationsRead()
    unreadCount.value = response.data.unreadCount
    items.value = items.value.map((item) => ({ ...item, read: true }))
  }

  function startPolling() {
    if (polling.value) {
      return
    }
    polling.value = true
    void refreshUnread().catch(() => {
      unreadCount.value = 0
    })
    pollTimer = setInterval(() => {
      void refreshUnread().catch(() => undefined)
    }, POLL_MS)
  }

  function stopPolling() {
    polling.value = false
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    items.value = []
    page.value = 0
    total.value = 0
    unreadCount.value = 0
    error.value = ''
  }

  return {
    items,
    page,
    total,
    unreadCount,
    loading,
    error,
    hasMore,
    polling,
    refreshUnread,
    loadInbox,
    markRead,
    markAllRead,
    startPolling,
    stopPolling,
  }
})
