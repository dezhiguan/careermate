<template>
  <div class="opportunity-page">
    <header class="page-header">
      <div class="header-top">
        <div>
          <h1 class="page-title">今天的机会</h1>
          <p class="page-sub">
            {{ hasResume ? '基于你的简历 AI 匹配排序' : '传简历即可看到每个 JD 的匹配分' }}
          </p>
        </div>
      </div>
      <div class="filter-chip-row" aria-label="机会筛选条件">
        <span class="filter-chip">城市 · {{ activeCity }}</span>
        <span class="filter-chip">年限 · {{ activeYears }}</span>
        <span class="filter-chip">岗位 · {{ activeRole }}</span>
      </div>
    </header>

    <div v-if="!hasResume && !loading && !degraded" class="resume-banner">
      <span class="resume-banner-text">传简历获得 AI 匹配分</span>
      <button type="button" class="resume-banner-btn" @click="goUploadResume">上传简历</button>
    </div>

    <div v-if="loading || degraded" class="card-list">
      <div v-if="degraded" class="degraded-hint">
        AI 正在抓取最新机会，可以先逛其他 tab，回来就有结果
      </div>
      <div v-for="n in 3" :key="n" class="skeleton-card">
        <div class="skeleton-line wide" />
        <div class="skeleton-line" />
        <div class="skeleton-block" />
        <div class="skeleton-btn" />
      </div>
    </div>

    <div v-else-if="items.length === 0" class="empty-state">
      暂无机会 · 换个关键词试试
    </div>

    <div v-else class="card-list">
      <article
        v-for="(item, index) in items"
        :key="item.jdId"
        class="jd-card"
        :class="{ 'jd-card-high': item.matchTier === 'HIGH' }"
      >
        <div v-if="item.matchTier === 'HIGH'" class="high-badge">⭐ AI 强推{{ index === 0 ? ' TOP 1' : '' }}</div>
        <button
          v-if="item.isDemo"
          type="button"
          class="demo-badge"
          @click.stop="goUploadResume"
        >
          示例 · 上传简历看真实匹配分
        </button>

        <div class="card-head">
          <div class="company-avatar">{{ companyInitial(item.company) }}</div>
          <div class="card-head-text">
            <div class="company-name">{{ item.company || '未知公司' }}</div>
            <div class="job-meta">
              {{ item.title || '岗位' }}
              <template v-if="item.level"> · {{ item.level }}</template>
              <template v-if="item.city"> · {{ item.city }}</template>
            </div>
          </div>
          <div v-if="item.matchScore != null" class="match-badge">
            <div class="match-label">AI 匹配分</div>
            <div class="match-score">{{ item.matchScore }}</div>
          </div>
        </div>

        <div v-if="item.matchScore != null" class="tier-row">
          <span class="tier-chip" :class="tierClass(item.matchTier)">{{ tierLabel(item.matchTier) }}</span>
          <span v-for="reason in (item.matchReasons || []).slice(0, 1)" :key="reason" class="reason-text">
            {{ reason }}
          </span>
        </div>

        <div v-if="item.skills?.length" class="skills-row">
          <span v-for="skill in item.skills.slice(0, 4)" :key="skill" class="skill-chip">{{ skill }}</span>
        </div>

        <div class="meta-row">
          <span v-if="item.experienceRange">{{ item.experienceRange }}</span>
          <span v-if="item.education">{{ item.education }}</span>
          <span v-if="item.companySize">{{ item.companySize }}</span>
        </div>

        <div class="card-actions">
          <button
            type="button"
            class="btn-action"
            :disabled="!!preparingId"
            @click.stop="handleWorkspaceAction(item, 'ANALYZE_JD')"
          >
            分析 JD
          </button>
          <button
            type="button"
            class="btn-action"
            :disabled="!!preparingId || !hasResume"
            :title="!hasResume ? '定制简历需要先上传简历，去「我的简历」上传后即可使用' : ''"
            @click.stop="handleWorkspaceAction(item, 'GENERATE_RESUME')"
          >
            定制简历
          </button>
          <button
            type="button"
            class="btn-action"
            :disabled="!!preparingId"
            @click.stop="handleWorkspaceAction(item, 'PREPARE_INTERVIEW')"
          >
            准备面试
          </button>
        </div>
      </article>
      <div v-if="loadingMore" class="load-more-state">正在加载更多机会</div>
      <div v-else-if="!hasMore" class="load-more-state">已加载全部机会</div>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOpportunities, prepareWithAi } from '../api/opportunity'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'
import { homeStore } from '../stores/homeStore'

const router = useRouter()
const route = useRoute()

const items = ref([])
const hasResume = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const totalCount = ref(0)
const error = ref('')
const preparingId = ref('')
const degraded = ref(false)
const degradedRetryCount = ref(0)
let degradedRefreshTimer = null

const activeKeyword = computed(() => String(route.query.keyword || '').trim())
const activeCity = computed(() => String(route.query.city || '不限').trim())
const activeYears = computed(() => String(route.query.years || '不限').trim())
const activeRole = computed(() => activeKeyword.value || String(route.query.position || '全部').trim())

watch(
  () => [route.query.keyword, route.query.city, route.query.position, route.query.years, route.query.t],
  () => {
    if (route.path === '/opportunity') {
      fetchList()
    }
  }
)

async function fetchList({ autoRefresh = false, page = 1, append = false } = {}) {
  if (append) {
    if (loading.value || loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  } else {
    loading.value = true
    currentPage.value = 1
    totalCount.value = 0
    hasMore.value = true
  }
  if (!autoRefresh) {
    degradedRetryCount.value = 0
  }
  error.value = ''
  try {
    const hasDefaultResume = !!homeStore.state.defaultResume
    const data = await listOpportunities({
      keyword: activeKeyword.value || undefined,
      city: route.query.city || undefined,
      position: route.query.position || undefined,
      mode: hasDefaultResume ? undefined : 'demo',
      page,
      size: 10,
    })
    const meta = data?._meta || data?.meta || null
    if (meta?.state === 'LOADING') {
      if (!append) {
        items.value = []
      }
      hasResume.value = !!data?.hasResume
      degraded.value = true
      scheduleDegradedRefresh()
      return
    }
    degraded.value = false
    clearDegradedRefreshTimer()
    const nextItems = data?.items || []
    items.value = append ? appendUniqueItems(items.value, nextItems) : nextItems
    currentPage.value = page
    totalCount.value = Number(data?.total || items.value.length || 0)
    hasMore.value = items.value.length < totalCount.value && nextItems.length > 0
    hasResume.value = !!data?.hasResume
    if (!activeKeyword.value && page === 1) {
      homeStore.updateTopOpportunities(items.value)
    }
  } catch (e) {
    error.value = e.message || '加载失败'
    if (!append) {
      items.value = []
      totalCount.value = 0
      hasMore.value = false
    }
    degraded.value = false
    clearDegradedRefreshTimer()
  } finally {
    if (append) {
      loadingMore.value = false
    } else {
      loading.value = false
    }
  }
}

const DEGRADED_REFETCH_MAX = 6
const DEGRADED_REFETCH_DELAY_MS = 3000

function scheduleDegradedRefresh() {
  if (degradedRetryCount.value >= DEGRADED_REFETCH_MAX) return
  degradedRetryCount.value += 1
  clearDegradedRefreshTimer()
  degradedRefreshTimer = window.setTimeout(() => {
    fetchList({ autoRefresh: true })
  }, DEGRADED_REFETCH_DELAY_MS)
}

function clearDegradedRefreshTimer() {
  if (!degradedRefreshTimer) return
  window.clearTimeout(degradedRefreshTimer)
  degradedRefreshTimer = null
}

function hydrateFromBootstrap() {
  clearDegradedRefreshTimer()
  degraded.value = false
  items.value = Array.isArray(homeStore.state.topOpportunities)
    ? homeStore.state.topOpportunities
    : []
  currentPage.value = 1
  totalCount.value = items.value.length
  hasMore.value = items.value.length >= 10
  hasResume.value = !!homeStore.state.defaultResume
  loading.value = false
  loadingMore.value = false
  error.value = ''
}

function appendUniqueItems(currentItems, nextItems) {
  const seen = new Set(currentItems.map(item => item.jdId))
  const merged = [...currentItems]
  for (const item of nextItems) {
    if (!item?.jdId || seen.has(item.jdId)) continue
    seen.add(item.jdId)
    merged.push(item)
  }
  return merged
}

function handleScroll() {
  if (loading.value || loadingMore.value || degraded.value || !hasMore.value) return
  const scrollTop = window.scrollY || document.documentElement.scrollTop || 0
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0
  const docHeight = document.documentElement.scrollHeight || 0
  if (docHeight - (scrollTop + viewportHeight) < 360) {
    fetchList({ page: currentPage.value + 1, append: true })
  }
}

async function handleWorkspaceAction(item, entryAction) {
  if (!item?.jdId || preparingId.value) return
  preparingId.value = item.jdId
  error.value = ''
  try {
    const goalMap = {
      ANALYZE_JD: '分析 JD 匹配度与要求',
      GENERATE_RESUME: '按 JD 定制简历',
      PREPARE_INTERVIEW: '准备该岗位面试',
    }
    const resp = await createWorkspace({
      workspaceType: 'JD_PREP',
      title: buildJdTitle(item),
      goalText: goalMap[entryAction] || 'JD 准备',
      entryAction,
      contextMetadata: {
        jdId: item.jdId,
        company: item.company,
        title: item.title,
        city: item.city,
        skills: item.skills,
        matchScore: item.matchScore,
      },
    })
    await navigateToWorkspace(router, resp)
  } catch (e) {
    error.value = e?.message || '准备失败，请稍后重试'
  } finally {
    preparingId.value = ''
  }
}

/** 保留旧接口兼容路径，供回归或外部调用 */
async function handlePrepare(item) {
  if (!item?.jdId || preparingId.value) return
  preparingId.value = item.jdId
  error.value = ''
  try {
    const resp = await prepareWithAi(item.jdId)
    await navigateToWorkspace(router, resp)
  } catch (e) {
    error.value = e?.message || '准备失败，请稍后重试'
  } finally {
    preparingId.value = ''
  }
}

function buildJdTitle(item) {
  const company = item?.company?.trim()
  const title = item?.title?.trim()
  if (company && title) return `${company} ${title}`
  return title || company || 'JD 准备空间'
}

function goUploadResume() {
  router.push('/mine')
}

function companyInitial(company) {
  if (!company) return '?'
  return company.trim().charAt(0)
}

function tierLabel(tier) {
  const map = { HIGH: '高匹配', MEDIUM: '中匹配', LOW: '低匹配', UNKNOWN: '未评分' }
  return map[tier] || tier || '未评分'
}

function tierClass(tier) {
  if (tier === 'HIGH') return 'tier-high'
  if (tier === 'MEDIUM') return 'tier-medium'
  if (tier === 'LOW') return 'tier-low'
  return 'tier-unknown'
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  if (!activeKeyword.value && homeStore.state.initialized && homeStore.state.topOpportunities.length > 0) {
    hydrateFromBootstrap()
    return
  }
  fetchList()
})

onBeforeUnmount(() => {
  clearDegradedRefreshTimer()
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.opportunity-page {
  min-height: 100%;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
  background: #f8fafc;
  /* 底部留白由 AppShellMobile 统一处理 */
}

.page-header {
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 14px 16px 12px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.page-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
}

.filter-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  align-items: center;
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
}

.resume-banner {
  margin: 12px 16px 0;
  padding: 10px 14px;
  background: #eef2ff;
  border: 1px solid #e0e7ff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.resume-banner-text {
  font-size: 13px;
  color: #4338ca;
  font-weight: 500;
}

.resume-banner-btn {
  border: 0;
  background: #4f46e5;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.card-list {
  padding: 14px 16px;
  display: grid;
  gap: 12px;
}

.degraded-hint {
  grid-column: 1 / -1;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
}

.load-more-state {
  grid-column: 1 / -1;
  min-height: 36px;
  display: grid;
  place-items: center;
  color: #64748b;
  font-size: 12px;
}

@media (min-width: 768px) {
  .card-list {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    padding: 16px 20px;
  }
}

.jd-card {
  position: relative;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
}

.jd-card-high {
  border: 2px solid #4f46e5;
}

.high-badge {
  position: absolute;
  top: -9px;
  left: 14px;
  background: #4f46e5;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
}

.demo-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  max-width: calc(100% - 24px);
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999px;
  padding: 3px 9px;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.4;
  cursor: pointer;
  white-space: normal;
}

.demo-badge:hover {
  background: #dbeafe;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 0 10px;
}

.company-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #4f46e5;
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 13px;
  flex-shrink: 0;
}

.card-head-text {
  flex: 1;
  min-width: 0;
}

.company-name {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.job-meta {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.match-badge {
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border-radius: 8px;
  padding: 6px 10px;
  text-align: center;
  flex-shrink: 0;
}

.match-label {
  font-size: 10px;
  color: #4338ca;
  font-weight: 600;
}

.match-score {
  font-size: 32px;
  font-weight: 800;
  color: #4f46e5;
  line-height: 1;
}

.tier-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.tier-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.tier-high {
  background: #eef2ff;
  color: #4338ca;
}

.tier-medium {
  background: #f1f5f9;
  color: #475569;
}

.tier-low {
  background: #fef3c7;
  color: #b45309;
}

.tier-unknown {
  background: #f1f5f9;
  color: #94a3b8;
}

.reason-text {
  font-size: 11px;
  color: #64748b;
}

.skills-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.skill-chip {
  font-size: 10px;
  padding: 2px 8px;
  background: #eef2ff;
  color: #4f46e5;
  border-radius: 999px;
  font-weight: 500;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: #475569;
  margin-bottom: 10px;
}

.meta-row span::after {
  content: '·';
  margin-left: 8px;
  color: #cbd5e1;
}

.meta-row span:last-child::after {
  content: '';
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  position: relative;
  z-index: 1;
}

.btn-action {
  flex: 1 1 calc(33% - 6px);
  min-width: 72px;
  background: #4f46e5;
  color: #fff;
  border: 0;
  border-radius: 8px;
  padding: 8px 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.btn-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #64748b;
  font-size: 13px;
}

.skeleton-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
}

.skeleton-line,
.skeleton-block,
.skeleton-btn {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
  border-radius: 6px;
}

.skeleton-line {
  height: 12px;
  margin-bottom: 8px;
}

.skeleton-line.wide {
  width: 70%;
}

.skeleton-block {
  height: 48px;
  margin: 12px 0;
}

.skeleton-btn {
  height: 40px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.error-text {
  text-align: center;
  color: #ef4444;
  font-size: 12px;
  padding: 8px 16px;
}
</style>
