<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-if="name === 'home'" d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z" />
    <path v-else-if="name === 'search'" d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16m10 2-4.35-4.35" />
    <path v-else-if="name === 'upload'" d="M12 16V5m0 0-4 4m4-4 4 4M5 19h14" />
    <path v-else-if="name === 'library'" d="M4 6h10v14H4zM14 10h6v10h-6zM7 10h4M7 14h4" />
    <path v-else-if="name === 'bell'" d="M6 9a6 6 0 1 1 12 0c0 7 3 7 3 9H3c0-2 3-2 3-9m4 12h4" />
    <path v-else-if="name === 'user'" d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4m7 8a7 7 0 0 0-14 0" />
    <path v-else-if="name === 'menu'" d="M4 7h16M4 12h16M4 17h16" />
    <path v-else-if="name === 'close'" d="M6 6l12 12M18 6 6 18" />
    <path v-else-if="name === 'heart'" d="M12 20s-7-4.4-7-9a4 4 0 0 1 7-2 4 4 0 0 1 7 2c0 4.6-7 9-7 9z" />
    <path v-else-if="name === 'bookmark'" d="M7 4h10a1 1 0 0 1 1 1v16l-6-3.5L6 21V5a1 1 0 0 1 1-1z" />
    <path v-else-if="name === 'play'" d="M8 6v12l10-6z" />
    <path v-else-if="name === 'logout'" d="M10 7V5a1 1 0 0 1 1-1h8v16h-8a1 1 0 0 1-1-1v-2m-6-6h11m-4-4 4 4-4 4" />
    <circle v-else-if="name === 'empty'" cx="12" cy="12" r="8" />
  </svg>
</template>

<script setup lang="ts">
export type IconName =
  | 'home'
  | 'search'
  | 'upload'
  | 'library'
  | 'bell'
  | 'user'
  | 'menu'
  | 'close'
  | 'heart'
  | 'bookmark'
  | 'play'
  | 'logout'
  | 'empty'

withDefaults(defineProps<{
  name: IconName
  size?: number
}>(), {
  size: 18,
})
</script>
