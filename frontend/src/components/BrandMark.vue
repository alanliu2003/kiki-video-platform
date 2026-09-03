<template>
  <RouterLink to="/" class="app-brand" :aria-label="ariaLabel">
    <span aria-hidden="true">ki</span><span class="app-brand-accent" aria-hidden="true">ki</span>
  </RouterLink>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

const ariaLabel = 'Kiki home'
</script>
