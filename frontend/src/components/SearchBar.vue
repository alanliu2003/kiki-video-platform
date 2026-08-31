<template>
  <form class="search-bar" @submit.prevent="onSubmit">
    <input
      v-model="draft"
      type="search"
      name="q"
      aria-label="Search videos"
      placeholder="Search videos"
    >
    <button type="submit">Search</button>
  </form>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const draft = ref(typeof route.query.q === 'string' ? route.query.q : '')

watch(
  () => route.query.q,
  (value) => {
    draft.value = typeof value === 'string' ? value : ''
  },
)

async function onSubmit() {
  const q = draft.value.trim()
  if (!q) {
    return
  }
  await router.push({ name: 'search', query: { q, page: '0' } })
}
</script>
