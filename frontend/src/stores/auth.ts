import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginRequest, register as registerRequest, type User } from '../api/auth'
import { clearStoredAccessToken, getStoredAccessToken, setStoredAccessToken } from '../api/http'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const accessToken = ref<string | null>(getStoredAccessToken())
  const initialized = ref(false)
  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

  async function register(username: string, email: string, password: string) {
    const response = await registerRequest({ username, email, password })
    return response.data
  }

  async function login(identifier: string, password: string) {
    const response = await loginRequest({ identifier, password })
    accessToken.value = response.data.accessToken
    user.value = response.data.user
    setStoredAccessToken(response.data.accessToken)
    return response.data
  }

  function logout() {
    user.value = null
    accessToken.value = null
    clearStoredAccessToken()
  }

  async function fetchCurrentUser() {
    const response = await getCurrentUser()
    user.value = response.data
    return response.data
  }

  async function initializeAuth() {
    if (initialized.value) {
      return
    }

    const token = getStoredAccessToken()
    if (!token) {
      logout()
      initialized.value = true
      return
    }

    accessToken.value = token
    try {
      await fetchCurrentUser()
    } catch {
      logout()
    } finally {
      initialized.value = true
    }
  }

  return {
    user,
    accessToken,
    initialized,
    isAuthenticated,
    register,
    login,
    logout,
    fetchCurrentUser,
    initializeAuth,
  }
})
