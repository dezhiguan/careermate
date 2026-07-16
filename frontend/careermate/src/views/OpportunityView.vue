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
      <div class="filter-bar" aria-label="机会筛选">
        <label class="filter-field">
          <span class="filter-label">城市</span>
          <select class="filter-select" :value="activeCity" @change="onCityChange($event)">
            <option v-for="c in CITY_OPTIONS" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label class="filter-field">
          <span class="filter-label">排序</span>
          <select class="filter-select" v-model="sortMode">
            <option v-for="s in SORT_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
        </label>
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
        v-for="(item, index) in displayItems"
        :key="item.jdId"
        class="jd-card"
        :class="{ 'jd-card-high': item.matchTier === 'HIGH' }"
      >
        <div v-if="item.matchTier === 'HIGH'" class="high-badge">⭐ AI 强推{{ index === 0 && sortMode === 'match' ? ' TOP 1' : '' }}</div>
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
              <template v-if="item.salaryRange"> · {{ item.salaryRange }}</template>
              <template v-else-if="item.level"> · {{ item.level }}</template>
              <template v-if="item.city"> · {{ item.city }}</template>
            </div>
          </div>
          <span v-if="item.matchScore != null" class="tier-chip tier-badge" :class="tierClass(item.matchTier)">
            {{ tierLabel(item.matchTier) }}
          </span>
          <button
            type="button"
            class="save-star"
            :class="{ saved: isSaved(item) }"
            :title="isSaved(item) ? '取消收藏' : '收藏到暂存区'"
            @click.stop="toggleSave(item)"
          >
            {{ isSaved(item) ? '★' : '☆' }}
          </button>
        </div>

        <div v-if="(item.matchReasons || []).length" class="tier-row">
          <span v-for="reason in (item.matchReasons || []).slice(0, 2)" :key="reason" class="reason-text">
            ✓ {{ reason }}
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
            class="btn-action btn-primary"
            :disabled="!!preparingId || !hasResume"
            :title="!hasResume ? '定制简历需要先上传简历，去「我的简历」上传后即可使用' : ''"
            @click.stop="handleWorkspaceAction(item, 'GENERATE_RESUME')"
          >
            ✦ 定制简历
          </button>
          <button
            type="button"
            class="btn-action btn-mini"
            :disabled="!!preparingId"
            @click.stop="handleWorkspaceAction(item, 'ANALYZE_JD')"
          >
            分析 JD
          </button>
          <button
            type="button"
            class="btn-action btn-mini"
            :disabled="!!preparingId"
            @click.stop="handleWorkspaceAction(item, 'PREPARE_INTERVIEW')"
          >
            准备面试
          </button>
        </div>
      </article>
      <!-- 移动端：触底无限滚动 -->
      <template v-if="!isDesktop">
        <div v-if="loadingMore" class="load-more-state">正在加载更多机会</div>
        <div v-else-if="!hasMore" class="load-more-state">已加载全部机会</div>
      </template>
    </div>

    <!-- 桌面：点击页码翻页 -->
    <nav v-if="isDesktop && !loading && !degraded && totalPages > 1" class="pager" aria-label="分页导航">
      <button type="button" class="pager-btn" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
        上一页
      </button>
      <button
        v-for="(p, i) in pageWindow"
        :key="`${p}-${i}`"
        type="button"
        class="pager-num"
        :class="{ active: p === currentPage, ellipsis: p === '…' }"
        :disabled="p === '…'"
        @click="goToPage(p)"
      >
        {{ p }}
      </button>
      <button type="button" class="pager-btn" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">
        下一页
      </button>
    </nav>

    <p v-if="error" class="error-text">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOpportunities } from '../api/opportunity'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'
import { createApplication } from '../api/pipeline'
import { listSavedJobs, saveJob, unsaveJob } from '../api/savedJobs'
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

// 暂存区收藏：记录已收藏的 jdDocId（数字）
const savedSet = ref(new Set())
function jdNum(item) {
  const n = item?.docId || Number(String(item?.jdId || '').replace(/\D/g, ''))
  return Number.isFinite(n) && n > 0 ? n : null
}
function isSaved(item) {
  const n = jdNum(item)
  return n != null && savedSet.value.has(n)
}
async function loadSaved() {
  try {
    const list = await listSavedJobs()
    savedSet.value = new Set(list.map((s) => Number(s.jdDocId)).filter(Boolean))
  } catch (e) {
    // 收藏态非关键，失败忽略
  }
}
async function toggleSave(item) {
  const n = jdNum(item)
  if (n == null) return
  const next = new Set(savedSet.value)
  try {
    if (next.has(n)) {
      next.delete(n)
      savedSet.value = next
      await unsaveJob(n)
    } else {
      next.add(n)
      savedSet.value = next
      await saveJob({ jdDocId: n, company: item.company, roleTitle: item.title })
    }
  } catch (e) {
    error.value = e?.message || '收藏操作失败'
    loadSaved()
  }
}

const CITY_OPTIONS = ['不限', '北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '西安']
const SORT_OPTIONS = [
  { value: 'match', label: '匹配度' },
  { value: 'salaryDesc', label: '薪资高→低' },
  { value: 'fresh', label: '最新发布' },
]
const sortMode = ref('match')

const activeKeyword = computed(() => String(route.query.keyword || '').trim())
const activeCity = computed(() => String(route.query.city || '不限').trim())
const activeYears = computed(() => String(route.query.years || '不限').trim())
const activeRole = computed(() => activeKeyword.value || String(route.query.position || '全部').trim())

function onCityChange(evt) {
  const city = String(evt?.target?.value || '不限')
  const query = { ...route.query, t: String(Date.now()) }
  if (city === '不限') {
    delete query.city
  } else {
    query.city = city
  }
  router.replace({ path: '/opportunity', query })
}

// 薪资排序键：取字符串中最大数字，「万」量级近似折算到「K」量级便于同轴比较
function parseSalaryKey(range) {
  if (!range) return -1
  const nums = String(range).match(/\d+(?:\.\d+)?/g)
  if (!nums) return -1
  let key = Math.max(...nums.map(Number))
  if (/万/.test(range)) key *= 10
  return key
}

function parseFreshKey(publishedAt) {
  if (!publishedAt) return 0
  const t = Date.parse(publishedAt)
  return Number.isNaN(t) ? 0 : t
}

// 排序仅对已加载结果重排（匹配度=服务端 AI 排序原样）；薪资/最新为客户端重排
const displayItems = computed(() => {
  const list = items.value.slice()
  if (sortMode.value === 'salaryDesc') {
    return list.sort((a, b) => parseSalaryKey(b.salaryRange) - parseSalaryKey(a.salaryRange))
  }
  if (sortMode.value === 'fresh') {
    return list.sort((a, b) => parseFreshKey(b.publishedAt) - parseFreshKey(a.publishedAt))
  }
  return list
})

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

// 分页分平台：桌面点击页码翻页、移动端触底无限滚动（设计 R54/R162）
const PAGE_SIZE = 10
const isDesktop = ref(false)
function updateIsDesktop() {
  isDesktop.value = typeof window !== 'undefined' && window.innerWidth >= 900
}
const totalPages = computed(() => Math.max(1, Math.ceil((totalCount.value || 0) / PAGE_SIZE)))
const pageWindow = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const out = [1]
  const start = Math.max(2, cur - 1)
  const end = Math.min(total - 1, cur + 1)
  if (start > 2) out.push('…')
  for (let p = start; p <= end; p++) out.push(p)
  if (end < total - 1) out.push('…')
  out.push(total)
  return out
})
async function goToPage(p) {
  if (typeof p !== 'number' || p < 1 || p > totalPages.value || p === currentPage.value) return
  await fetchList({ page: p, append: false })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleScroll() {
  if (isDesktop.value) return
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
    // 开始为该 JD 干活 → 自动进投递看板（jd 去重，失败不影响主流程）
    if (entryAction === 'GENERATE_RESUME' || entryAction === 'PREPARE_INTERVIEW') {
      const jdDocId = item.docId || Number(String(item.jdId || '').replace(/\D/g, '')) || null
      if (jdDocId) {
        createApplication({ jdDocId, company: item.company, roleTitle: item.title }).catch(() => {})
      }
    }
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
  updateIsDesktop()
  loadSaved()
  window.addEventListener('resize', updateIsDesktop, { passive: true })
  window.addEventListener('scroll', handleScroll, { passive: true })
  if (!activeKeyword.value && homeStore.state.initialized && homeStore.state.topOpportunities.length > 0) {
    hydrateFromBootstrap()
    return
  }
  fetchList()
})

onBeforeUnmount(() => {
  clearDegradedRefreshTimer()
  window.removeEventListener('resize', updateIsDesktop)
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

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  align-items: center;
}

.filter-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 3px 10px 3px 12px;
  border-radius: 999px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.filter-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.filter-select {
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  cursor: pointer;
  outline: none;
  font-family: inherit;
  padding-right: 2px;
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

.pager {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin: 20px 16px 8px;
}

.pager-btn,
.pager-num {
  min-width: 34px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.pager-btn:hover:not(:disabled),
.pager-num:hover:not(:disabled):not(.active) {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.pager-num.active {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #fff;
  cursor: default;
}

.pager-num.ellipsis {
  border: none;
  background: transparent;
  cursor: default;
  color: #94a3b8;
}

.pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.save-star {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #cbd5e1;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  padding: 0 2px;
}

.save-star.saved {
  color: #f59e0b;
}

@media (min-width: 768px) {
  .card-list {
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    padding: 20px 32px;
    max-width: 1240px;
    margin: 0 auto;
    width: 100%;
  }
  .page-header {
    padding: 20px 32px 16px;
    max-width: 1240px;
    margin: 0 auto;
    width: 100%;
    background: transparent;
    border-bottom: none;
  }
  .page-title {
    font-size: 22px;
  }
  .page-sub {
    font-size: 13px;
  }
}

@media (min-width: 1280px) {
  .card-list {
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
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

.tier-badge {
  margin-left: auto;
  align-self: flex-start;
  font-size: 12px;
  padding: 3px 11px;
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
  min-width: 72px;
  border: 0;
  border-radius: 8px;
  padding: 8px 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  flex: 1 1 100%;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  padding: 9px 6px;
  font-size: 12.5px;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.25);
}

.btn-mini {
  flex: 1 1 calc(50% - 3px);
  background: #fff;
  color: #475569;
  border: 1px solid #e2e8f0;
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
