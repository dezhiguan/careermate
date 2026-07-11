<template>
  <div class="market-page">

    <!-- 顶部筛选 -->
    <header class="filter-bar">
      <h1 class="filter-title">{{ filterTitle }}</h1>
      <div class="filter-controls">
        <label class="filter-item">
          <span class="filter-label">城市</span>
          <select v-model="city" class="filter-select" @change="applyFilters">
            <option v-for="c in cityOptions" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label class="filter-item">
          <span class="filter-label">岗位</span>
          <select v-model="role" class="filter-select" @change="applyFilters">
            <option v-for="r in roleOptions" :key="r" :value="r">{{ r }}</option>
          </select>
        </label>
        <label class="filter-item">
          <span class="filter-label">年限</span>
          <select v-model="years" class="filter-select" @change="applyFilters">
            <option v-for="y in MARKET_YEARS" :key="y" :value="y">{{ y }}</option>
          </select>
        </label>
      </div>
      <p class="filter-sub">数据来源：知识库 AI 分析 · 基于你的职业画像，可在下方切换筛选</p>
    </header>

    <div class="market-content">
      <section class="card snapshot-card">
        <div class="snapshot-head">
          <div>
            <div class="card-title">市场快照</div>
            <p class="snapshot-sub">P50 / P75 薪资 + 技能 TOP5</p>
          </div>
          <button type="button" class="more-btn" @click="moreOpen = true">更多</button>
        </div>
        <div v-if="snapshotLoading" class="skeleton-group">
          <div class="loading-hint">AI 正在抓取最新行情，可以先逛其他 tab，回来就有结果</div>
          <div class="skeleton" style="height:36px;width:50%;margin-bottom:8px" />
          <div class="skeleton" style="height:14px;width:80%" />
          <div class="skeleton" style="height:34px;margin-top:12px" />
          <div class="skeleton" style="height:34px;margin-top:8px" />
        </div>
        <div v-else-if="snapshotDegraded" class="state-panel degraded-panel">
          <div>AI 暂时不可用，请稍后重试</div>
          <button type="button" class="retry-btn" @click="retryMarketData">重试</button>
        </div>
        <template v-else-if="snapshotHasData">
          <div v-if="salaryData" class="salary-summary-grid">
            <div class="p-item">
              <div class="p-label">P50</div>
              <div class="p-val highlight">{{ salaryData.p50 }}</div>
            </div>
            <div class="p-item">
              <div class="p-label">P75</div>
              <div class="p-val">{{ salaryData.p75 }}</div>
            </div>
            <div class="trend-tag" :class="salaryData.trend === '上涨' ? 'tag-up' : 'tag-flat'">
              {{ salaryData.trend || '稳定' }}
            </div>
          </div>
          <div v-if="skillsLoading" class="skeleton-group skill-skeleton">
            <div v-for="i in 3" :key="i" class="skeleton" style="height:34px;margin-bottom:8px" />
          </div>
          <div v-else-if="topSkills.length" class="skill-list compact">
            <div v-for="s in topSkills" :key="s.rank" class="skill-item">
              <div class="skill-row">
                <div class="skill-left">
                  <span class="rank-badge" :class="s.rank <= 2 ? 'rank-hot' : 'rank-normal'">
                    {{ s.rank }}
                  </span>
                  <span class="skill-name">{{ s.name }}</span>
                  <span class="own-chip" :class="isOwned(s.name) ? 'chip-has' : 'chip-miss'">
                    {{ isOwned(s.name) ? '✓ 你有' : '你没' }}
                  </span>
                </div>
                <span class="growth-tag" :class="growthClass(s.growth)">{{ s.growth }}</span>
              </div>
              <div class="skill-bar">
                <div
                  class="skill-bar-fill"
                  :class="isOwned(s.name) ? 'bar-purple' : 'bar-green'"
                  :style="{ width: barWidth(s.rank) + '%' }"
                />
              </div>
            </div>
          </div>
        </template>
        <div v-else class="state-panel empty-panel">该筛选条件无数据，建议切换城市/岗位</div>
        <div class="market-cta-row">
          <button
            type="button"
            class="cta-btn secondary"
            :disabled="!!workspaceLoading"
            @click="enterMarketWorkspace('EXPLAIN_MARKET')"
          >
            解读行情
          </button>
          <button
            type="button"
            class="cta-btn"
            :disabled="!!workspaceLoading"
            @click="enterMarketWorkspace('NEGOTIATION_SCRIPT')"
          >
            生成谈薪脚本 →
          </button>
        </div>
      </section>
    </div>

    <div v-if="moreOpen" class="drawer-overlay" @click.self="moreOpen = false">
      <aside class="more-drawer" aria-label="市场更多分析">
        <div class="drawer-header">
          <div>
            <div class="drawer-title">更多市场分析</div>
            <div class="drawer-sub">{{ filterTitle }}</div>
          </div>
          <button type="button" class="drawer-close" @click="moreOpen = false">×</button>
        </div>

        <section v-if="salaryData" class="drawer-section">
          <div class="card-title">薪资分位分布</div>
          <div class="salary-main">{{ salaryData.p50 }}</div>
          <div class="salary-sub">市场中位薪资（P50）</div>
          <div class="percentile-row">
            <div class="p-item"><div class="p-label">P25</div><div class="p-val">{{ salaryData.p25 }}</div></div>
            <div class="p-item"><div class="p-label">P50</div><div class="p-val highlight">{{ salaryData.p50 }}</div></div>
            <div class="p-item"><div class="p-label">P75</div><div class="p-val">{{ salaryData.p75 }}</div></div>
            <div class="p-item"><div class="p-label">P90</div><div class="p-val">{{ salaryData.p90 }}</div></div>
          </div>
          <div v-if="salaryData.aiSummary" class="ai-box">
            <span class="ai-label">AI 解读</span>
            <p class="ai-text">{{ salaryData.aiSummary }}</p>
          </div>
          <div v-if="marketSources(salaryData).length" class="source-strip">
            <div class="source-title">来源摘要</div>
            <div v-for="source in marketSources(salaryData)" :key="source.citation || source.contentPreview" class="source-row">
              <span class="source-chip">{{ sourceLabel(source) }}</span>
              <span class="source-preview">{{ source.contentPreview }}</span>
            </div>
          </div>
          <p class="disclaimer">基于 AI 分析，仅供参考</p>
        </section>

        <section v-if="skillsData?.skills?.length" class="drawer-section">
          <div class="card-title">完整技能热榜<span v-if="role" class="card-context"> · {{ role }}</span></div>
          <div class="skill-list">
            <div v-for="s in skillsData.skills" :key="s.rank" class="skill-item">
              <div class="skill-row">
                <div class="skill-left">
                  <span class="rank-badge" :class="s.rank <= 2 ? 'rank-hot' : 'rank-normal'">{{ s.rank }}</span>
                  <span class="skill-name">{{ s.name }}</span>
                  <span class="own-chip" :class="isOwned(s.name) ? 'chip-has' : 'chip-miss'">{{ isOwned(s.name) ? '你有' : '你没' }}</span>
                </div>
                <span class="growth-tag" :class="growthClass(s.growth)">{{ s.growth }}</span>
              </div>
              <div class="skill-bar">
                <div class="skill-bar-fill" :class="isOwned(s.name) ? 'bar-purple' : 'bar-green'" :style="{ width: barWidth(s.rank) + '%' }" />
              </div>
            </div>
          </div>
          <div v-if="skillsData.aiSummary" class="ai-box drawer-ai">
            <span class="ai-label">AI 解读</span>
            <p class="ai-text">{{ skillsData.aiSummary }}</p>
          </div>
        </section>

        <section class="drawer-section">
          <div class="card-title">简历 Gap 分析<span v-if="gapContext" class="card-context"> · {{ gapContext }}</span></div>
          <div v-if="gapLoading" class="skeleton-group">
            <div class="skeleton" style="height:14px;width:70%;margin-bottom:8px" />
            <div class="skeleton" style="height:14px;width:90%;margin-bottom:8px" />
          </div>
          <div v-else-if="isDegradedState(gapState)" class="state-panel degraded-panel">
            <div>AI 暂时不可用，请稍后重试</div>
            <button type="button" class="retry-btn" @click="retryMarketData">重试</button>
          </div>
          <div v-else-if="isEmptyState(gapState)" class="state-panel empty-panel">上传简历后可查看 Gap 分析。</div>
          <template v-else-if="gapData">
            <div class="score-row">
              <div class="score-circle">
                <span class="score-num">{{ gapData.matchScore }}</span>
                <span class="score-unit">分</span>
              </div>
              <div class="score-desc">市场匹配度</div>
            </div>
            <div v-if="gapData.hasSkills?.length" class="gap-group">
              <div class="gap-label has">你已具备</div>
              <div class="chip-row">
                <span v-for="s in gapData.hasSkills" :key="s" class="chip chip-has">{{ s }}</span>
              </div>
            </div>
            <div v-if="gapData.missingSkills?.length" class="gap-group">
              <div class="gap-label miss">建议补齐</div>
              <div class="chip-row">
                <span v-for="s in gapData.missingSkills" :key="s" class="chip chip-miss">{{ s }}</span>
              </div>
            </div>
            <div v-if="gapData.topSuggestion" class="suggest-box">{{ gapData.topSuggestion }}</div>
          </template>
          <div v-else class="compact-empty">上传简历后可查看 Gap 分析。</div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSalaryInsight, getSkillTrends, getResumeGap } from '../api/market'
import { getCareerProfile } from '../api/profile'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'
import { stripMarkdown } from '../utils/markdown'

const router = useRouter()
const route = useRoute()

const MARKET_CITIES = ['广州', '深圳', '北京', '上海', '杭州', '成都', '南京', '武汉', '西安', '苏州']
const MARKET_ROLES = [
  'Java后端',
  'Java 后端开发',
  '前端开发',
  '算法工程师',
  '测试工程师',
  'Go后端',
  'Python后端',
]
const MARKET_YEARS = ['1-3年', '3-5年', '5-10年', '10年以上']
const META_STATE = {
  FRESH: 'FRESH',
  LOADING: 'LOADING',
  DEGRADED: 'DEGRADED',
  EMPTY: 'EMPTY',
}
const LOADING_REFETCH_DELAY_MS = 3000
const LOADING_REFETCH_MAX = 6

const city = ref('')
const role = ref('')
const years = ref('')

const salaryLoading = ref(true)
const skillsLoading = ref(true)
const gapLoading = ref(true)

const salaryData = ref(null)
const skillsData = ref(null)
const gapData = ref(null)
const salaryState = ref(META_STATE.LOADING)
const skillsState = ref(META_STATE.LOADING)
const gapState = ref(META_STATE.LOADING)
const workspaceLoading = ref('')
const moreOpen = ref(false)
const loadingRefetchCount = ref(0)
let loadingRefetchTimer = null

const filterTitle = computed(() => {
  const parts = [city.value, role.value, years.value].filter(Boolean)
  return parts.length ? parts.join(' · ') : '请完善职业画像'
})

const cityOptions = computed(() => {
  if (city.value && !MARKET_CITIES.includes(city.value)) {
    return [city.value, ...MARKET_CITIES]
  }
  return MARKET_CITIES
})

const roleOptions = computed(() => {
  if (role.value && !MARKET_ROLES.includes(role.value)) {
    return [role.value, ...MARKET_ROLES]
  }
  return MARKET_ROLES
})

const gapContext = computed(() => [city.value, role.value].filter(Boolean).join(' · '))
const topSkills = computed(() => (skillsData.value?.skills || []).slice(0, 5))
const snapshotLoading = computed(() =>
  salaryLoading.value
  || skillsLoading.value
  || isLoadingState(salaryState.value)
  || isLoadingState(skillsState.value)
)
const snapshotDegraded = computed(() =>
  isDegradedState(salaryState.value) || isDegradedState(skillsState.value)
)
const snapshotHasData = computed(() => {
  const hasSalary = salaryData.value && !isEmptyState(salaryState.value) && !isDegradedState(salaryState.value)
  const hasSkills = topSkills.value.length > 0 && !isEmptyState(skillsState.value) && !isDegradedState(skillsState.value)
  return hasSalary || hasSkills
})

function applyFilters() {
  router.replace({
    query: {
      city: city.value,
      role: role.value,
      years: years.value,
    },
  })
  loadMarketData()
}

function syncFiltersFromRoute() {
  const qCity = route.query.city
  const qRole = route.query.role
  const qYears = route.query.years
  if (qCity && MARKET_CITIES.includes(String(qCity))) city.value = String(qCity)
  if (qRole) role.value = String(qRole)
  if (qYears && MARKET_YEARS.includes(String(qYears))) years.value = String(qYears)
}

async function loadMarketData({ autoRefresh = false } = {}) {
  if (!autoRefresh) {
    loadingRefetchCount.value = 0
  }
  clearLoadingRefetchTimer()
  salaryLoading.value = true
  skillsLoading.value = true
  gapLoading.value = true
  salaryData.value = null
  skillsData.value = null
  gapData.value = null
  salaryState.value = META_STATE.LOADING
  skillsState.value = META_STATE.LOADING
  gapState.value = META_STATE.LOADING

  const tasks = []

  if (city.value && role.value && years.value) {
    tasks.push(
      getSalaryInsight({ role: role.value, city: city.value, years: years.value })
        .then((r) => {
          salaryData.value = r
          salaryState.value = responseState(r)
        })
        .catch(() => { salaryState.value = META_STATE.DEGRADED })
        .finally(() => { salaryLoading.value = false })
    )
  } else {
    salaryLoading.value = false
    salaryState.value = META_STATE.EMPTY
  }

  if (role.value) {
    tasks.push(
      getSkillTrends({ city: city.value || '广州', role: role.value })
        .then((r) => {
          skillsData.value = r
          skillsState.value = responseState(r)
        })
        .catch(() => { skillsState.value = META_STATE.DEGRADED })
        .finally(() => { skillsLoading.value = false })
    )
  } else {
    skillsLoading.value = false
    skillsState.value = META_STATE.EMPTY
  }

  tasks.push(
    getResumeGap()
      .then((r) => {
        gapData.value = r
        gapState.value = responseState(r)
      })
      .catch(() => { gapState.value = META_STATE.DEGRADED })
      .finally(() => { gapLoading.value = false })
  )

  await Promise.allSettled(tasks)
  scheduleLoadingRefetch()
}

function responseState(data) {
  return data?._meta?.state || data?.meta?.state || META_STATE.FRESH
}

function isLoadingState(state) {
  return state === META_STATE.LOADING
}

function isDegradedState(state) {
  return state === META_STATE.DEGRADED
}

function isEmptyState(state) {
  return state === META_STATE.EMPTY
}

function scheduleLoadingRefetch() {
  if (!hasLoadingState()) return
  if (loadingRefetchCount.value >= LOADING_REFETCH_MAX) {
    // 评审 P1-1：轮询到上限仍未出数据，判定为超时，把仍处 LOADING 的模块降级，
    // 触发「AI 暂时不可用，请稍后重试」面板，避免骨架屏永远转圈。
    markLoadingStatesTimedOut()
    return
  }
  loadingRefetchCount.value += 1
  loadingRefetchTimer = window.setTimeout(() => {
    loadMarketData({ autoRefresh: true })
  }, LOADING_REFETCH_DELAY_MS)
}

function markLoadingStatesTimedOut() {
  if (isLoadingState(salaryState.value)) salaryState.value = META_STATE.DEGRADED
  if (isLoadingState(skillsState.value)) skillsState.value = META_STATE.DEGRADED
  if (isLoadingState(gapState.value)) gapState.value = META_STATE.DEGRADED
}

function hasLoadingState() {
  return isLoadingState(salaryState.value)
    || isLoadingState(skillsState.value)
    || isLoadingState(gapState.value)
}

function clearLoadingRefetchTimer() {
  if (!loadingRefetchTimer) return
  window.clearTimeout(loadingRefetchTimer)
  loadingRefetchTimer = null
}

function retryMarketData() {
  loadMarketData()
}

onMounted(async () => {
  try {
    const profile = await getCareerProfile()
    if (profile?.targetCity?.trim()) city.value = profile.targetCity.trim()
    if (profile?.targetRole?.trim()) role.value = profile.targetRole.trim()
    if (profile?.seniority?.trim()) years.value = profile.seniority.trim()
  } catch {
    // 画像加载失败时使用默认值
  }
  if (!city.value) city.value = '广州'
  if (!role.value) role.value = 'Java后端'
  if (!years.value) years.value = '3-5年'
  syncFiltersFromRoute()
  await loadMarketData()
})

onBeforeUnmount(() => {
  clearLoadingRefetchTimer()
})

const hasSkillSet = computed(() =>
  new Set((gapData.value?.hasSkills || []).map((s) => s.toLowerCase()))
)

function isOwned(skillName) {
  return hasSkillSet.value.has(skillName.toLowerCase())
}

function barWidth(rank) {
  return Math.max(20, 100 - (rank - 1) * 14)
}

function growthClass(growth) {
  return { 快涨: 'tag-fast', 上涨: 'tag-up', 稳定: 'tag-flat', 下降: 'tag-down' }[growth] || 'tag-flat'
}

function marketSources(data) {
  const citations = Array.isArray(data?.citations) ? data.citations : []
  return citations
    .filter((source) => source?.contentPreview || source?.citation)
    .map((source) => ({
      ...source,
      contentPreview: stripMarkdown(source.contentPreview || source.citation || ''),
    }))
    .slice(0, 2)
}

function sourceLabel(source) {
  const type = source?.chunkType || 'RAG'
  const score = typeof source?.score === 'number' ? ` · ${(source.score * 100).toFixed(0)}%` : ''
  return `${type}${score}`
}

function buildMarketContextMetadata() {
  return {
    city: city.value,
    role: role.value,
    years: years.value,
    salaryData: salaryData.value,
    skillsData: skillsData.value,
    gapData: gapData.value,
  }
}

async function enterMarketWorkspace(entryAction) {
  if (workspaceLoading.value) return
  workspaceLoading.value = entryAction
  try {
    const goalMap = {
      EXPLAIN_MARKET: '解读当前市场行情',
      NEGOTIATION_SCRIPT: '生成谈薪脚本',
    }
    const resp = await createWorkspace({
      workspaceType: 'MARKET',
      title: filterTitle.value || '市场策略空间',
      goalText: goalMap[entryAction] || '市场分析',
      entryAction,
      contextMetadata: buildMarketContextMetadata(),
    })
    await navigateToWorkspace(router, resp)
  } catch (e) {
    console.error('进入小职失败', e)
  } finally {
    workspaceLoading.value = ''
  }
}
</script>

<style scoped>
.market-page { min-height: 100%; width: 100%; max-width: 100%; overflow-x: hidden; background: #f8fafc; }
.filter-bar { padding: 14px 16px 10px; background: #fff; border-bottom: 1px solid #e2e8f0; }
.filter-title { font-size: 16px; font-weight: 700; color: #0f172a; margin: 0 0 10px; }
.filter-controls { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.filter-item { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 11px; color: #64748b; font-weight: 600; white-space: nowrap; }
.filter-select {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 999px;
  padding: 5px 28px 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
  outline: none;
  cursor: pointer;
  font-family: inherit;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
}
.filter-select:focus { border-color: #6366f1; box-shadow: 0 0 0 2px rgba(99,102,241,.15); }
.filter-sub { font-size: 11px; color: #94a3b8; margin: 0; }
.market-content { padding: 14px 14px 0; display: flex; flex-direction: column; gap: 12px; }
.card { background: #fff; border-radius: 12px; padding: 16px; border: 1px solid #e2e8f0; }
.card-title { font-size: 13px; font-weight: 700; color: #0f172a; margin-bottom: 12px; }
.card-context { font-weight: 500; color: #64748b; font-size: 12px; }
.snapshot-card { min-height: 260px; }
.snapshot-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.snapshot-sub { margin: -8px 0 0; color: #64748b; font-size: 11px; }
.more-btn {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
}
.more-btn:hover { border-color: #cbd5e1; background: #f1f5f9; }

/* 薪资 */
.salary-main { font-size: 36px; font-weight: 800; color: #4f46e5; line-height: 1; }
.salary-sub { font-size: 11px; color: #64748b; margin: 4px 0 14px; }
.salary-summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: stretch;
  margin-bottom: 14px;
}
.percentile-row { display: flex; justify-content: space-between; margin-bottom: 12px; }
.p-item { text-align: center; background: #f8fafc; border-radius: 10px; padding: 10px 8px; min-width: 0; }
.p-label { font-size: 10px; color: #64748b; font-weight: 600; }
.p-val { font-size: 13px; font-weight: 700; color: #334155; margin-top: 2px; }
.p-val.highlight { color: #4f46e5; font-size: 15px; }
.trend-tag { display: inline-flex; align-items: center; justify-content: center; padding: 2px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.tag-up { background: #dcfce7; color: #15803d; }
.tag-flat { background: #f1f5f9; color: #475569; }

/* AI box */
.ai-box { background: linear-gradient(135deg,#eef2ff,#ede9fe); border-radius: 8px; padding: 10px 12px; }
.ai-label { font-size: 10px; font-weight: 700; color: #4338ca; display: block; margin-bottom: 4px; }
.ai-text { font-size: 12px; color: #1e1b4b; line-height: 1.6; margin: 0; }
.disclaimer { font-size: 10px; color: #94a3b8; margin: 8px 0 0; }
.source-strip {
  margin-top: 10px;
  border-top: 1px solid #e2e8f0;
  padding-top: 9px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.source-title {
  font-size: 10px;
  color: #64748b;
  font-weight: 700;
}
.source-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: start;
}
.source-chip {
  max-width: 96px;
  border: 1px solid #cbd5e1;
  background: #f8fafc;
  color: #475569;
  border-radius: 6px;
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.source-preview {
  min-width: 0;
  color: #475569;
  font-size: 11px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* CTA */
.market-cta-row {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.cta-btn {
  flex: 1;
  padding: 14px;
  background: #b45309;
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.cta-btn.secondary {
  background: #fff;
  color: #b45309;
  border: 1px solid #fcd34d;
}

.cta-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 技能 */
.skill-list { display: flex; flex-direction: column; gap: 10px; }
.skill-list.compact { gap: 8px; }
.skill-skeleton { margin-top: 10px; }
.skill-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.skill-left { display: flex; align-items: center; gap: 6px; }
.rank-badge { width: 18px; height: 18px; border-radius: 4px; display: grid; place-items: center; font-size: 10px; font-weight: 700; }
.rank-hot { background: #fef3c7; color: #92400e; }
.rank-normal { background: #f1f5f9; color: #475569; }
.skill-name { font-size: 13px; font-weight: 600; color: #0f172a; }
.own-chip { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 600; }
.chip-has { background: #d1fae5; color: #047857; }
.chip-miss { background: #fef3c7; color: #92400e; }
.growth-tag { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; }
.tag-fast { background: #dcfce7; color: #15803d; }
.tag-down { background: #fee2e2; color: #b91c1c; }
.skill-bar { height: 6px; background: #f1f5f9; border-radius: 3px; overflow: hidden; }
.skill-bar-fill { height: 100%; border-radius: 3px; }
.bar-purple { background: linear-gradient(90deg,#4f46e5,#818cf8); }
.bar-green { background: linear-gradient(90deg,#10b981,#34d399); }

/* Gap */
.score-row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.score-circle { width: 60px; height: 60px; border-radius: 50%; background: linear-gradient(135deg,#4f46e5,#7c3aed); display: flex; align-items: baseline; justify-content: center; gap: 2px; }
.score-num { color: #fff; font-size: 22px; font-weight: 800; }
.score-unit { color: rgba(255,255,255,.8); font-size: 11px; }
.score-desc { font-size: 13px; color: #334155; font-weight: 600; }
.gap-group { margin-bottom: 10px; }
.gap-label { font-size: 11px; font-weight: 700; margin-bottom: 6px; }
.gap-label.has { color: #047857; }
.gap-label.miss { color: #b45309; }
.chip-row { display: flex; flex-wrap: wrap; gap: 5px; }
.chip { padding: 3px 10px; border-radius: 999px; font-size: 11px; font-weight: 500; }
.chip-tech { background: #eef2ff; color: #4338ca; }
.suggest-box { background: linear-gradient(135deg,#fef3c7,#fde68a); border-radius: 8px; padding: 10px 12px; font-size: 12px; color: #78350f; line-height: 1.6; margin-top: 4px; }
.drawer-ai { margin-top: 12px; }

.drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 420;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  justify-content: flex-end;
}

.more-drawer {
  width: min(420px, 100%);
  height: 100%;
  background: #fff;
  box-shadow: -18px 0 36px rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.drawer-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  padding: 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}

.drawer-title { color: #0f172a; font-size: 15px; font-weight: 800; }
.drawer-sub { color: #64748b; font-size: 11px; margin-top: 3px; }
.drawer-close {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.drawer-section {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
}

/* 骨架屏 */
.skeleton-group { display: flex; flex-direction: column; }

.loading-hint {
  margin-bottom: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
}
.skeleton { background: linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 50%,#f1f5f9 75%); background-size: 200% 100%; animation: shimmer 1.4s infinite; border-radius: 6px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.empty-tip { text-align: center; color: #94a3b8; font-size: 13px; padding: 20px 0; }
.state-panel {
  border-radius: 10px;
  padding: 14px;
  font-size: 12px;
  line-height: 1.6;
}
.degraded-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}
.empty-panel {
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.retry-btn {
  flex-shrink: 0;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
}
.retry-btn:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}
.compact-empty {
  color: #64748b;
  background: #f8fafc;
  border-radius: 10px;
  padding: 14px;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 520px) {
  .salary-summary-grid {
    grid-template-columns: 1fr 1fr;
  }

  .salary-summary-grid .trend-tag {
    grid-column: 1 / -1;
    min-height: 30px;
  }
}
</style>
