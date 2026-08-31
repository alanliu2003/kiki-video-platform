import { createRouter, createWebHistory, type NavigationGuard } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import MyVideosView from '../views/MyVideosView.vue'
import ProfileView from '../views/ProfileView.vue'
import RegisterView from '../views/RegisterView.vue'
import VideoDetailView from '../views/VideoDetailView.vue'
import SearchView from '../views/SearchView.vue'
import VideoUploadView from '../views/VideoUploadView.vue'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { requiresAuth: true },
    },
    {
      path: '/videos/upload',
      name: 'video-upload',
      component: VideoUploadView,
      meta: { requiresAuth: true },
    },
    {
      path: '/search',
      name: 'search',
      component: SearchView,
    },
    {
      path: '/videos/:id',
      name: 'video-detail',
      component: VideoDetailView,
    },
    {
      path: '/my/videos',
      name: 'my-videos',
      component: MyVideosView,
      meta: { requiresAuth: true },
    },
  ],
})

export const authGuard: NavigationGuard = async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized) {
    await auth.initializeAuth()
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if ((to.name === 'login' || to.name === 'register') && auth.isAuthenticated) {
    return { name: 'profile' }
  }

  return true
}

router.beforeEach(authGuard)
