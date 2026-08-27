<template>
  <p>Backend: {{ health.message }}</p>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useHealthStore } from '../stores/health'

const health = useHealthStore()

onMounted(() => {
  void health.checkHealth()
})
</script>
