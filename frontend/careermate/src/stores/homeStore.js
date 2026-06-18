import { reactive } from 'vue'
import { getHomeBootstrap } from '../api/home'
import { authStore } from './authStore'

const STALE_AFTER_MS = 30 * 1000
let inflight = null
let lastFetchedAt = 0

const state = reactive({
  initialized: false,
  loading: false,
  loadedUserId: null,
  user: null,
  careerProfile: null,
  defaultResume: null,
  topOpportunities: [],
  error: '',
})

function applyBootstrap(data) {
  const user = data?.user || null
  state.user = user
  state.careerProfile = data?.careerProfile || null
  state.defaultResume = data?.defaultResume || null
  state.topOpportunities = Array.isArray(data?.topOpportunities) ? data.topOpportunities : []
  state.loadedUserId = user?.userId || null
  state.initialized = true
  state.error = ''
  if (user) {
    authStore.applyUserProfile(user)
  }
}

async function fetchBootstrap({ force = false } = {}) {
  const currentUserId = authStore.state.currentUser?.userId || null
  const fresh = Date.now() - lastFetchedAt < STALE_AFTER_MS
  if (!force && fresh && state.initialized && state.loadedUserId === currentUserId) {
    return snapshot()
  }
  if (inflight) return inflight
  state.loading = true
  inflight = (async () => {
    try {
      const data = await getHomeBootstrap()
      applyBootstrap(data)
      lastFetchedAt = Date.now()
      return snapshot()
    } catch (e) {
      state.error = e?.message || '首屏数据加载失败'
      throw e
    } finally {
      state.loading = false
      inflight = null
    }
  })()
  return inflight
}

function snapshot() {
  return {
    user: state.user,
    careerProfile: state.careerProfile,
    defaultResume: state.defaultResume,
    topOpportunities: state.topOpportunities,
  }
}

function updateCareerProfile(profile) {
  state.careerProfile = profile || null
}

function updateDefaultResume(resume) {
  state.defaultResume = resume || null
}

function updateTopOpportunities(items) {
  state.topOpportunities = Array.isArray(items) ? items : []
}

function reset() {
  inflight = null
  lastFetchedAt = 0
  state.initialized = false
  state.loading = false
  state.loadedUserId = null
  state.user = null
  state.careerProfile = null
  state.defaultResume = null
  state.topOpportunities = []
  state.error = ''
}

export const homeStore = {
  state,
  fetchBootstrap,
  applyBootstrap,
  updateCareerProfile,
  updateDefaultResume,
  updateTopOpportunities,
  reset,
}
