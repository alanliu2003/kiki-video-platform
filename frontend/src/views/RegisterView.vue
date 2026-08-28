<template>
  <main>
    <h1>Create an account</h1>
    <form @submit.prevent="onSubmit">
      <label>
        Username
        <input v-model.trim="username" type="text" autocomplete="username" required />
      </label>
      <label>
        Email
        <input v-model.trim="email" type="email" autocomplete="email" required />
      </label>
      <label>
        Password
        <input v-model="password" type="password" autocomplete="new-password" required />
      </label>
      <label>
        Confirm password
        <input v-model="confirmPassword" type="password" autocomplete="new-password" required />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Creating account...' : 'Register' }}
      </button>
    </form>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const submitting = ref(false)

function validate(): string | null {
  if (username.value.length < 3 || username.value.length > 30) {
    return 'Username must be 3–30 characters.'
  }
  if (!/^[A-Za-z0-9_]+$/.test(username.value)) {
    return 'Username may only contain letters, numbers, and underscores.'
  }
  if (!email.value.includes('@')) {
    return 'Enter a valid email address.'
  }
  if (password.value.length < 8) {
    return 'Password must be at least 8 characters.'
  }
  if (password.value !== confirmPassword.value) {
    return 'Passwords do not match.'
  }
  return null
}

async function onSubmit() {
  error.value = validate() ?? ''
  if (error.value) {
    return
  }

  submitting.value = true
  try {
    await auth.register(username.value, email.value, password.value)
    await router.push({ name: 'login' })
  } catch (err) {
    error.value = isApiError(err) ? err.message : 'Unable to register.'
  } finally {
    submitting.value = false
  }
}
</script>
