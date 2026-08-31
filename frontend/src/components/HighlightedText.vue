<template>
  <span>
    <span
      v-for="(part, index) in parts"
      :key="index"
      :class="{ 'search-highlight': part.highlighted }"
    >{{ part.text }}</span>
  </span>
</template>

<script setup lang="ts">
import type { HighlightSpan } from '../api/search'

defineProps<{
  parts: HighlightSpan[]
}>()
</script>
