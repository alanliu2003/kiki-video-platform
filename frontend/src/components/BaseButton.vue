<template>
  <button :type="type" class="btn" :class="`btn-${variant}`" :disabled="disabled">
    <slot />
  </button>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  type?: 'button' | 'submit'
  disabled?: boolean
}>(), {
  variant: 'primary',
  type: 'button',
  disabled: false,
})
</script>
