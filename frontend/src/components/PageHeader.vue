<template>
  <header class="page-header">
    <div>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-kicker">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="library-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>
