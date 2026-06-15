import { createRouter, createWebHashHistory } from 'vue-router'
import { authStore } from '../stores/authStore'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/', redirect: '/opportunity' },
  {
    path: '/opportunity',
    name: 'opportunity',
    component: () => import('../views/OpportunityView.vue'),
    meta: { title: '机会' },
  },
  {
    path: '/interview',
    name: 'interview',
    component: () => import('../views/InterviewPrep.vue'),
    meta: { title: '面试题' },
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('../views/AgentChat.vue'),
    meta: { title: '小职', immersive: true },
  },
  {
    path: '/chat/:wsId',
    name: 'chat-workspace',
    component: () => import('../views/AgentChat.vue'),
    meta: { title: '小职', immersive: true },
  },
  {
    path: '/market',
    name: 'market',
    component: () => import('../views/MarketView.vue'),
    meta: { title: '市场' },
  },
  {
    path: '/mine',
    name: 'mine',
    component: () => import('../views/MineView.vue'),
    meta: { title: '我的' },
  },
  {
    path: '/mine/resume',
    name: 'mine-resume',
    component: () => import('../views/ResumeManage.vue'),
    meta: { title: '简历管理' },
  },
  { path: '/match', redirect: '/opportunity' },
  { path: '/dashboard', redirect: '/opportunity' },
  { path: '/resume', redirect: '/mine/resume' },
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
      return '/opportunity'
    }
    return true
  }

  if (!authenticated) {
    return '/login'
  }
  return true
})

export default router
