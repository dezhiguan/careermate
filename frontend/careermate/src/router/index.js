import { ref } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import { authStore } from '../stores/authStore'
import { homeStore } from '../stores/homeStore'

// 路由/懒加载 chunk 加载期间为真：供 App 显示"加载中"，避免深链/刷新时主区白屏
export const routeLoading = ref(false)

// 安全兜底：无论 afterEach 是否触发，加载态最多保留 8s，绝不会卡死在转圈
let loadingTimer = null
function setRouteLoading(v) {
  routeLoading.value = v
  if (loadingTimer) {
    clearTimeout(loadingTimer)
    loadingTimer = null
  }
  if (v) {
    loadingTimer = setTimeout(() => {
      routeLoading.value = false
    }, 8000)
  }
}

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  { path: '/', redirect: '/chat' },
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
    meta: { title: '面试准备' },
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
  {
    path: '/mine/runs',
    name: 'mine-runs',
    component: () => import('../views/AgentRuns.vue'),
    meta: { title: '深度任务' },
  },
  {
    path: '/mine/account-settings',
    name: 'account-settings',
    component: () => import('../views/AccountSettings.vue'),
    meta: { title: '账号与安全' },
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
  setRouteLoading(true)
  if (!authStore.state.initialized) {
    await authStore.init()
  }

  const authenticated = authStore.isAuthenticated()
  if (to.path === '/login') {
    if (authenticated) {
      return '/chat'
    }
    return true
  }

  if (!authenticated) {
    // 兜底：未认证却要进受保护页时，清掉本地可能残留的失效/注入 token，避免登出不彻底（TC-CM-04）
    authStore.clearAuth()
    return '/login'
  }
  // 非阻塞后台加载首页数据：避免深链/刷新时首页接口慢或失败导致 router-view 空白（主区白屏）。
  // 各页面在 onMounted 自行拉数据；鉴权失效由 http 层全局 handleUnauthorized 统一兜底，
  // 不因一次 bootstrap 失败就阻塞导航或把用户登出。
  homeStore.fetchBootstrap().catch(() => {})
  return true
})

router.afterEach(() => {
  setRouteLoading(false)
})
router.onError(() => {
  setRouteLoading(false)
})

export default router
