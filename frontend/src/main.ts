import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './api/http'
import { useAuthStore } from './stores/auth'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const auth = useAuthStore()
setUnauthorizedHandler(() => {
  auth.logout()
  if (router.currentRoute.value.meta.requiresAuth) {
    void router.push({ name: 'login' })
  }
})

app.use(router)
app.mount('#app')
