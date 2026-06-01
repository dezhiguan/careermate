import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
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

export default router
