import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getHealth } from '../api/health'

export const useHealthStore = defineStore('health', () => {
  const status = ref<'unknown' | 'ok' | 'error'>('unknown')
  const message = ref('Checking connection…')

  async function checkHealth() {
    try {
      const response = await getHealth()
      if (response.data.status === 'ok') {
        status.value = 'ok'
        message.value = 'Online'
        return
      }

      status.value = 'error'
      message.value = 'Service unavailable'
    } catch {
      status.value = 'error'
      message.value = 'Offline'
    }
  }

  return { status, message, checkHealth }
})
