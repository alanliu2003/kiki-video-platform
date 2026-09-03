<template>
  <main class="auth-card">
    <BrandMark />
    <h1>Sign in</h1>
    <form @submit.prevent="onSubmit">
      <label>
        Username or email
        <input v-model.trim="identifier" type="text" autocomplete="username" required />
      </label>
      <label>
        Password
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" class="btn btn-primary" :disabled="submitting">
        {{ submitting ? 'Signing in...' : 'Log in' }}
      </button>
    </form>
    <p class="auth-switch">
      New to Kiki?
      <RouterLink to="/register">Create an account</RouterLink>
    </p>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import BrandMark from '../components/BrandMark.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const identifier = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function onSubmit() {
  error.value = ''
  if (!identifier.value || !password.value) {
    error.value = 'Username/email and password are required.'
    return
  }

  submitting.value = true
  try {
    await auth.login(identifier.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/profile'
    await router.push(redirect)
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to log in.'
  } finally {
    submitting.value = false
  }
}
</script>
