<template>
  <div class="skeleton-grid" :aria-hidden="true">
    <div v-for="index in count" :key="index" class="skeleton-card">
      <div class="skeleton skeleton-thumb"></div>
      <div class="skeleton skeleton-line" style="width: 80%"></div>
      <div class="skeleton skeleton-line" style="width: 50%"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  count?: number
}>(), {
  count: 4,
})
</script>
