import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', () => {
  const navOpen = ref(false)

  function openNav() {
    navOpen.value = true
  }

  function closeNav() {
    navOpen.value = false
  }

  function toggleNav() {
    navOpen.value = !navOpen.value
  }

  return { navOpen, openNav, closeNav, toggleNav }
})
