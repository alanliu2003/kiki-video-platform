<template>
  <main>
    <h1>Account</h1>
    <section v-if="auth.user">
      <p>Username: {{ auth.user.username }}</p>
      <p>Email: {{ auth.user.email }}</p>
      <p>Display name: {{ auth.user.displayName }}</p>
      <p>Role: {{ auth.user.role }}</p>
      <button type="button" @click="onLogout">Log out</button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  auth.logout()
  await router.push({ name: 'home' })
}
</script>
