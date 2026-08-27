import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getHealth } from '../api/health'

export const useHealthStore = defineStore('health', () => {
  const status = ref<'unknown' | 'ok' | 'error'>('unknown')
  const message = ref('Checking backend connectivity...')

  async function checkHealth() {
    try {
      const response = await getHealth()
      if (response.data.status === 'ok') {
        status.value = 'ok'
        message.value = 'Backend is reachable.'
        return
      }

      status.value = 'error'
      message.value = `Unexpected health status: ${response.data.status}`
    } catch {
      status.value = 'error'
      message.value = 'Backend is not reachable.'
    }
  }

  return { status, message, checkHealth }
})
