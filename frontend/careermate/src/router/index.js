import { createRouter, createWebHashHistory } from 'vue-router'
import { authStore } from '../stores/authStore'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/', name: 'chat', component: () => import('../views/AgentChat.vue') },
  { path: '/resume', name: 'resume', component: () => import('../views/ResumeStudio.vue') },
  { path: '/match', name: 'match', component: () => import('../views/JobMatching.vue') },
  { path: '/interview', name: 'interview', component: () => import('../views/InterviewPrep.vue') },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/CareerDashboard.vue') },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach(async (to) => {
  if (!authStore.state.initialized) {
    await authStore.init()
  }

  const authenticated = authStore.isAuthenticated()
  if (to.path === '/login') {
    if (authenticated) {
      return '/'
    }
    return true
  }

  if (!authenticated) {
    return '/login'
  }
  return true
})

export default router
