<template>
  <div class="empty-state">
    <AppIcon :name="icon" :size="28" />
    <p>{{ title }}</p>
    <p v-if="description" class="hint">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import AppIcon, { type IconName } from './AppIcon.vue'

withDefaults(defineProps<{
  title: string
  description?: string
  icon?: IconName
}>(), {
  icon: 'empty',
})
</script>
