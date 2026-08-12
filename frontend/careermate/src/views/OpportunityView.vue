<template>
  <div class="opportunity-page">
    <header class="mhead">
      <h1 class="mhead-t">机会</h1>
      <div class="search-wrap">
        <button v-if="!(isDesktop && searchInlineOpen)" type="button" class="search-chip" @click="openSearch">
          🔍 <span class="sc-ph">{{ activeKeyword || '搜公司 / 岗位' }}</span>
        </button>
        <div v-else class="search-inline">
          <span class="si-icon">🔍</span>
          <input
            ref="searchInlineEl"
            v-model="searchInput"
            class="si-input"
            type="search"
            placeholder="搜公司 / 岗位"
            @keydown.enter="doSearch(searchInput)"
            @keydown.esc="closeInlineSearch"
            @blur="onInlineBlur"
          >
          <div v-if="inlineSuggests.length" class="search-drop">
            <div v-for="g in inlineSuggests" :key="g.title" class="sd-sect">
              <div class="sd-title">
                {{ g.title }}
                <button v-if="g.title === '搜索历史'" type="button" class="sd-clear" @mousedown.prevent="clearHistory">清空</button>
              </div>
              <button
                v-for="it in g.items"
                :key="it"
                type="button"
                class="sd-row"
                @mousedown.prevent="pickSuggest(it)"
              >{{ g.icon }} {{ it }}</button>
            </div>
          </div>
        </div>
      </div>
      <button type="button" class="fchip" :class="{ on: sortMode === 'match' }" @click="setSort('match')">匹配度</button>
      <button type="button" class="fchip" :class="{ on: sortMode === 'fresh' }" @click="setSort('fresh')">最新</button>
      <CityPicker :model-value="activeCity" :options="cityOptions" variant="chip" @update:model-value="onCityChange" />
    </header>

    <!-- 冷启动轻问答：纯新用户（无简历 + 无意向）先问一句 -->
    <section v-if="showColdStart" class="coldstart">
      <div class="cs-card">
        <div class="cs-q">先告诉我们，你想找什么？</div>
        <div class="cs-sub">用于给你第一批机会，稍后上传简历即可精准匹配。</div>
        <div class="cs-field">
          <span class="cs-lbl">岗位</span>
          <input
            v-model="csRole"
            class="cs-input"
            type="text"
            placeholder="如 Java 后端开发（可自由输入任意岗位）"
            @keydown.enter="submitColdStart"
          >
        </div>
        <div class="cs-chips">
          <button v-for="r in COMMON_ROLES" :key="r" type="button" class="cs-chip" :class="{ on: csRole === r }" @click="csRole = r">{{ r }}</button>
        </div>
        <div class="cs-field">
          <span class="cs-lbl">城市</span>
          <CityPicker :model-value="csCity" :options="cityOptions" variant="select" @update:model-value="(v) => (csCity = v)" />
        </div>
        <button type="button" class="cs-go" :disabled="csSubmitting" @click="submitColdStart">看看机会 →</button>
        <button type="button" class="cs-skip" @click="dismissColdStart">跳过，先看最新机会</button>
      </div>
    </section>

    <div v-if="!hasResume && !loading && !degraded && !showColdStart" class="resume-banner">
      <span class="resume-banner-text">传简历获得 AI 匹配分</span>
      <button type="button" class="resume-banner-btn" @click="goUploadResume">上传简历</button>
    </div>

    <p v-if="!loading && !degraded && items.length" class="opp-count">
      共 <b>{{ totalCount || items.length }}</b> 个机会 ·
      {{ hasResume ? '已按你的画像匹配排序' : '通用推荐 · 上传简历后精准匹配' }}
    </p>

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
      <article v-for="item in displayItems" :key="item.jdId" class="opp">
        <button v-if="item.unmatched" type="button" class="unmatched-badge" @click.stop="goUploadResume">
          上传简历，解锁精准匹配分
        </button>
        <div class="opp-top">
          <span class="coav">{{ companyInitial(item.company) }}</span>
          <div class="opp-i">
            <div class="co">{{ item.company || '未知公司' }}</div>
            <div class="ro">{{ item.title || '岗位' }}<template v-if="item.city"> · {{ item.city }}</template></div>
            <div v-if="item.salaryRange || item.level" class="salr">{{ item.salaryRange || item.level }}</div>
          </div>
          <div v-if="item.matchScore != null" class="score">
            <span class="ring" :class="ringClass(item.matchTier)">{{ item.matchScore }}</span>
            <div class="lbl">{{ tierLabel(item.matchTier) }}</div>
          </div>
        </div>
        <div v-if="hitCount(item) || (item.missingSkills || []).length" class="hit">
          <template v-if="hitCount(item)"><span class="g">✓ 命中 {{ hitCount(item) }}</span> {{ hitNames(item) }}</template>
          <template v-if="(item.missingSkills || []).length">　<span class="r">✗ 缺 {{ item.missingSkills.length }}</span> {{ item.missingSkills.slice(0, 3).join(' · ') }}</template>
        </div>
        <div class="opp-act">
          <button
            type="button"
            class="btn ai"
            :disabled="!!preparingId || !hasResume"
            :title="!hasResume ? '定制简历需先上传简历' : ''"
            @click.stop="handleWorkspaceAction(item, 'GENERATE_RESUME')"
          >
            ✦ 定制简历
          </button>
          <button type="button" class="btn g" @click.stop="toggleSave(item)">
            {{ isSaved(item) ? '★ 已收藏' : '☆ 收藏' }}
          </button>
        </div>
      </article>
      <!-- 移动端：触底无限滚动 -->
      <template v-if="!isDesktop">
        <div v-if="loadingMore" class="load-more-state">正在加载更多机会</div>
        <div v-else-if="!hasMore" class="load-more-state">已加载全部机会</div>
      </template>
    </div>

    <!-- 桌面：统一分页组件 -->
    <PaginationBar
      v-if="isDesktop && !loading && !degraded && totalCount > 0"
      :total="totalCount"
      :page="currentPage"
      :size="pageSize"
      @update:page="goToPage"
      @update:size="onPageSizeChange"
    />

    <p v-if="error" class="error-text">{{ error }}</p>

    <!-- 移动端全屏搜索 -->
    <div v-if="searchOverlayOpen" class="search-overlay">
      <div class="search-bar">
        <span class="sb-icon">🔍</span>
        <input
          ref="searchInputEl"
          v-model="searchInput"
          class="search-ov-input"
          type="search"
          placeholder="搜 JD / 公司 / 技能"
          @keydown.enter="doSearch(searchInput)"
        >
        <button type="button" class="search-ov-cancel" @click="closeSearch">取消</button>
      </div>
      <div class="search-body">
        <div v-if="companySuggest.length" class="search-sect">
          <div class="search-sect-title">公司</div>
          <button v-for="c in companySuggest" :key="c" type="button" class="search-row" @click="doSearch(c)">
            🏢 只看「{{ c }}」
          </button>
        </div>
        <div v-if="titleSuggest.length" class="search-sect">
          <div class="search-sect-title">岗位</div>
          <button v-for="t in titleSuggest" :key="t" type="button" class="search-row" @click="doSearch(t)">
            💼 {{ t }}
          </button>
        </div>
        <div v-if="searchHistory.length" class="search-sect">
          <div class="search-sect-title">
            搜索历史
            <button type="button" class="search-clear" @click="clearHistory">清空</button>
          </div>
          <button v-for="h in searchHistory" :key="h" type="button" class="search-row" @click="doSearch(h)">
            🕘 {{ h }}
          </button>
        </div>
        <p v-if="!companySuggest.length && !titleSuggest.length && !searchHistory.length" class="search-empty">
          输入关键词搜索岗位或公司
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listOpportunities, getOpportunityCities } from '../api/opportunity'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'
import { createApplication } from '../api/pipeline'
import { listSavedJobs, saveJob, unsaveJob } from '../api/savedJobs'
import { getCareerProfile, updateCareerProfile } from '../api/profile'
import { homeStore } from '../stores/homeStore'
import PaginationBar from '../components/PaginationBar.vue'
import CityPicker from '../components/CityPicker.vue'

const router = useRouter()
const route = useRoute()

const items = ref([])
const hasResume = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)
const error = ref('')
const preparingId = ref('')
const degraded = ref(false)
const degradedRetryCount = ref(0)
let degradedRefreshTimer = null

// 移动端全屏搜索
const SEARCH_HISTORY_KEY = 'cm_search_history'
const searchOverlayOpen = ref(false)
const searchInput = ref('')
const searchInputEl = ref(null)
const searchHistory = ref([])
const companySuggest = computed(() => {
  const kw = searchInput.value.trim().toLowerCase()
  if (!kw) return []
  const seen = new Set()
  const out = []
  for (const it of items.value) {
    const co = it.company
    if (co && co.toLowerCase().includes(kw) && !seen.has(co)) {
      seen.add(co)
      out.push(co)
      if (out.length >= 6) break
    }
  }
  return out
})
const titleSuggest = computed(() => {
  const kw = searchInput.value.trim().toLowerCase()
  if (!kw) return []
  const seen = new Set()
  const out = []
  for (const it of items.value) {
    const t = it.title || it.roleTitle
    if (t && t.toLowerCase().includes(kw) && !seen.has(t)) {
      seen.add(t)
      out.push(t)
      if (out.length >= 6) break
    }
  }
  return out
})
function loadSearchHistory() {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY)
    searchHistory.value = raw ? JSON.parse(raw) : []
  } catch (e) {
    searchHistory.value = []
  }
}
// 桌面：工具条内内联搜索 + 联想；移动端：全屏搜索遮罩
const searchInlineOpen = ref(false)
const searchInlineEl = ref(null)
let inlineBlurTimer = null
function openSearch() {
  searchInput.value = activeKeyword.value
  if (isDesktop.value) {
    searchInlineOpen.value = true
    nextTick(() => searchInlineEl.value?.focus())
  } else {
    searchOverlayOpen.value = true
    nextTick(() => searchInputEl.value?.focus())
  }
}
function closeSearch() {
  searchOverlayOpen.value = false
}
function closeInlineSearch() {
  searchInlineOpen.value = false
}
function onInlineBlur() {
  // 延迟收起，留出点选联想项的时间
  inlineBlurTimer = setTimeout(() => {
    searchInlineOpen.value = false
  }, 160)
}
function pickSuggest(kw) {
  if (inlineBlurTimer) clearTimeout(inlineBlurTimer)
  doSearch(kw)
}
// 内联联想：公司 + 岗位 + 历史（复用既有联想数据）
const inlineSuggests = computed(() => {
  const groups = []
  if (companySuggest.value.length) groups.push({ title: '公司', icon: '🏢', items: companySuggest.value })
  if (titleSuggest.value.length) groups.push({ title: '岗位', icon: '💼', items: titleSuggest.value })
  if (!searchInput.value.trim() && searchHistory.value.length) {
    groups.push({ title: '搜索历史', icon: '🕘', items: searchHistory.value })
  }
  return groups
})
function doSearch(kw) {
  const q = String(kw || '').trim()
  if (!q) return
  const next = [q, ...searchHistory.value.filter((h) => h !== q)].slice(0, 8)
  searchHistory.value = next
  try {
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(next))
  } catch (e) {
    // localStorage 不可用忽略
  }
  searchOverlayOpen.value = false
  searchInlineOpen.value = false
  router.replace({ path: '/opportunity', query: { keyword: q, t: String(Date.now()) } })
}
function clearHistory() {
  searchHistory.value = []
  try {
    localStorage.removeItem(SEARCH_HISTORY_KEY)
  } catch (e) {
    // ignore
  }
}

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

// 城市下拉：从后端动态获取（可用城市集 + 默认城市），失败保底一份基础列表
const cityOptions = ref(['不限', '北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '西安'])
const defaultCity = ref('不限')
async function loadCities() {
  try {
    const data = await getOpportunityCities()
    const cities = data?.cities || []
    if (cities.length) cityOptions.value = cities
    if (data?.defaultCity) {
      defaultCity.value = data.defaultCity
      if (csCity.value === '不限') csCity.value = data.defaultCity
    }
  } catch (e) {
    // 保底沿用默认城市列表
  }
}

// 冷启动轻问答：纯新用户（无简历 + 无画像意向）首次进页引导一句
const COMMON_ROLES = ['Java 后端', '前端开发', '算法工程师', '测试开发', 'Golang 后端', '数据开发']
const profileHasIntent = ref(false)
const coldStartDismissed = ref(false)
const csRole = ref('')
const csCity = ref('不限')
const csSubmitting = ref(false)
async function loadProfileIntent() {
  try {
    const p = await getCareerProfile()
    profileHasIntent.value = !!(p?.targetRole || p?.targetCity)
  } catch (e) {
    profileHasIntent.value = false
  }
}
const showColdStart = computed(() =>
  !loading.value && !degraded.value && !hasResume.value &&
  !profileHasIntent.value && !coldStartDismissed.value
)
function dismissColdStart() {
  coldStartDismissed.value = true
}
async function submitColdStart() {
  const role = csRole.value.trim()
  const city = csCity.value
  if (!role && (!city || city === '不限')) {
    dismissColdStart()
    return
  }
  csSubmitting.value = true
  try {
    await updateCareerProfile({
      targetRole: role || undefined,
      targetCity: city && city !== '不限' ? city : undefined,
    })
    profileHasIntent.value = true
  } catch (e) {
    // 保存失败不阻断浏览
  }
  csSubmitting.value = false
  coldStartDismissed.value = true
  const query = { ...route.query, t: String(Date.now()) }
  if (role) query.position = role
  if (city && city !== '不限') query.city = city
  else delete query.city
  router.replace({ path: '/opportunity', query })
}
// v2.9 定稿：排序只留「匹配度·最新」两项（六维筛选/薪资排序已删）
const SORT_OPTIONS = [
  { value: 'match', label: '匹配度' },
  { value: 'fresh', label: '最新发布' },
]
const sortMode = ref('match')

const activeKeyword = computed(() => String(route.query.keyword || '').trim())
const activeCity = computed(() => String(route.query.city || defaultCity.value || '不限').trim())
const activeYears = computed(() => String(route.query.years || '不限').trim())
const activeRole = computed(() => activeKeyword.value || String(route.query.position || '全部').trim())

function onCityChange(city) {
  const next = String(city || '不限')
  // 显式写入城市（含「不限」），避免选「不限」后被 defaultCity 兜底顶回、无法清除筛选。
  const query = { ...route.query, city: next, t: String(Date.now()) }
  router.replace({ path: '/opportunity', query })
}

function parseFreshKey(publishedAt) {
  if (!publishedAt) return 0
  const t = Date.parse(publishedAt)
  return Number.isNaN(t) ? 0 : t
}

// 排序仅对已加载结果重排（匹配度=服务端 AI 排序原样）；最新为客户端重排
const displayItems = computed(() => {
  const list = items.value.slice()
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

async function fetchList({ autoRefresh = false, page = 1, append = false, keepVisible = false } = {}) {
  if (append) {
    if (loading.value || loadingMore.value || !hasMore.value) return
    loadingMore.value = true
  } else if (keepVisible) {
    // 首屏已用 bootstrap 切片铺好卡片，这次只是后台把真实分页数据补齐：
    // 不切骨架屏、不清空列表，避免用户眼前的卡片闪一下再回来。
    currentPage.value = 1
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
    const data = await listOpportunities({
      keyword: activeKeyword.value || undefined,
      city: activeCity.value !== '不限' ? activeCity.value : undefined,
      position: route.query.position || undefined,
      page,
      size: pageSize.value,
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
    // keepVisible 时列表上已有 bootstrap 切片，后台补拉失败就保持现状，不要把已见的卡片清空
    if (!keepVisible) {
      error.value = e.message || '加载失败'
    }
    if (!append && !keepVisible) {
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
const isDesktop = ref(false)
function updateIsDesktop() {
  isDesktop.value = typeof window !== 'undefined' && window.innerWidth >= 900
}
const totalPages = computed(() => Math.max(1, Math.ceil((totalCount.value || 0) / pageSize.value)))
async function goToPage(p) {
  if (typeof p !== 'number' || p < 1 || p > totalPages.value || p === currentPage.value) return
  await fetchList({ page: p, append: false })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function onPageSizeChange(s) {
  const next = Number(s) || 10
  if (next === pageSize.value) return
  pageSize.value = next
  await fetchList({ page: 1, append: false })
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

// 定稿：匹配环配色 hi/mid/low
function ringClass(tier) {
  if (tier === 'HIGH') return 'hi'
  if (tier === 'MEDIUM') return 'mid'
  return 'low'
}
// 命中技能 = JD 技能里用户已会的（skills − missingSkills）
function hitSkillsOf(item) {
  const miss = new Set((item.missingSkills || []).map((s) => String(s).toLowerCase()))
  return (item.skills || []).filter((s) => !miss.has(String(s).toLowerCase()))
}
function hitCount(item) {
  return hitSkillsOf(item).length
}
function hitNames(item) {
  return hitSkillsOf(item).slice(0, 3).join(' · ')
}
function setSort(v) {
  sortMode.value = v
}

onMounted(async () => {
  updateIsDesktop()
  loadSaved()
  loadSearchHistory()
  loadProfileIntent()
  window.addEventListener('resize', updateIsDesktop, { passive: true })
  window.addEventListener('scroll', handleScroll, { passive: true })

  // bootstrap 里的 topOpportunities 只是首屏用的前 10 条切片，拿来先把卡片铺出来避免白屏；
  // 但它不带真实总数，之前在这里 return 掉，分页器就把 10 当成了全部——机会池里其余的
  // 岗位（线上 100+ 条）用户永远翻不到，城市/关键词筛选和匹配排序也全都无从触发。
  // 正确做法是：先用切片抢首屏，再拉真实分页数据把总数和列表补齐。
  const hydrated = !activeKeyword.value
    && homeStore.state.initialized
    && homeStore.state.topOpportunities.length > 0
  if (hydrated) {
    hydrateFromBootstrap()
  }

  // 必须等 loadCities 回来再发首次查询：activeCity 由它填的 defaultCity 推导，
  // 而 defaultCity 初值是「不限」。不等的话首屏查的是全国，筛选器上却显示着默认城市，
  // 两者对不上；等用户一翻页，activeCity 已就位，结果集和总数会当场突变。
  await loadCities()
  fetchList(hydrated ? { keepVisible: true } : {})
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
  background: #F5F6F8;
  /* v3.4 视觉定稿 token */
  --line: #E8EAF0; --line2: #F1F3F7; --ink: #1A1D26; --ink2: #5C6472; --ink3: #9AA2AF;
  --brand: #4E5BEF; --brand-soft: #EEF0FE; --brand-line: #D8DCFB;
  --ai-grad: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  --good: #0DA76A; --warn: #DB9A2D; --bad: #E5484D;
}

/* ===== 机会页 · 照 09 定稿 ===== */
.mhead { display:flex; align-items:center; gap:10px; padding:14px 20px; background:#fff; border-bottom:1px solid var(--line); flex-wrap:wrap; }
.mhead-t { margin:0; font-size:16px; font-weight:700; color:var(--ink); }
/* 冷启动轻问答卡 */
.coldstart { max-width:1100px; margin:16px auto 0; padding:0 16px; }
.cs-card { max-width:420px; margin:0 auto; background:#fff; border:1px solid var(--line); border-radius:16px; box-shadow:0 8px 24px rgba(15,23,42,.06); padding:20px; display:flex; flex-direction:column; gap:10px; }
.cs-q { font-size:17px; font-weight:760; color:var(--ink); }
.cs-sub { font-size:12px; color:var(--ink3); margin-top:-4px; }
.cs-field { display:flex; align-items:center; gap:10px; }
.cs-lbl { font-size:12px; color:var(--ink2); width:34px; flex:none; }
.cs-input { flex:1; min-width:0; border:1px solid var(--line); border-radius:10px; padding:9px 12px; font:inherit; font-size:13px; color:var(--ink); background:var(--bg); }
.cs-input:focus { outline:none; border-color:#4E5BEF; box-shadow:0 0 0 3px rgba(78,91,239,.12); }
.cs-chips { display:flex; flex-wrap:wrap; gap:6px; padding-left:44px; }
.cs-chip { border:1px solid var(--line); background:#fff; border-radius:999px; padding:4px 10px; font:inherit; font-size:11px; color:var(--ink2); cursor:pointer; }
.cs-chip.on, .cs-chip:hover { border-color:#4E5BEF; color:#4E5BEF; background:#eef0fe; }
.cs-select { flex:1; border:1px solid var(--line); border-radius:10px; padding:9px 12px; font:inherit; font-size:13px; color:var(--ink); background:var(--bg); cursor:pointer; }
.cs-go { margin-top:4px; background:#4E5BEF; color:#fff; border:none; border-radius:10px; padding:11px; font:inherit; font-size:14px; font-weight:700; cursor:pointer; }
.cs-go:disabled { opacity:.6; cursor:default; }
.cs-skip { background:transparent; border:none; color:var(--ink3); font:inherit; font-size:12px; cursor:pointer; }
.cs-skip:hover { color:#4E5BEF; }

.search-wrap { position:relative; flex:0 1 320px; min-width:130px; }
.search-chip { width:100%; display:inline-flex; align-items:center; gap:6px; justify-content:flex-start; background:#fff; border:1px solid var(--line); border-radius:12px; padding:7px 14px; color:var(--ink3); font-size:12px; cursor:pointer; font-family:inherit; }
.sc-ph { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
/* 桌面内联搜索 */
.search-inline { display:flex; align-items:center; gap:6px; width:100%; background:#fff; border:1px solid #4E5BEF; box-shadow:0 0 0 3px rgba(78,91,239,.12); border-radius:12px; padding:6px 12px; }
.si-icon { font-size:13px; }
.si-input { flex:1; min-width:0; border:none; outline:none; background:transparent; font:inherit; font-size:12px; color:var(--ink); }
.search-drop { position:absolute; top:calc(100% + 6px); left:0; right:0; z-index:60; background:#fff; border:1px solid var(--line); border-radius:12px; box-shadow:0 8px 24px rgba(15,23,42,.12); padding:6px; max-height:340px; overflow-y:auto; }
.sd-sect + .sd-sect { border-top:1px solid var(--line); margin-top:4px; padding-top:4px; }
.sd-title { display:flex; align-items:center; justify-content:space-between; font-size:10px; letter-spacing:.06em; text-transform:uppercase; color:var(--ink3); padding:4px 8px 2px; }
.sd-clear { border:none; background:transparent; color:#4E5BEF; font-size:11px; cursor:pointer; font-family:inherit; }
.sd-row { display:block; width:100%; text-align:left; border:none; background:transparent; border-radius:8px; padding:7px 8px; font:inherit; font-size:12px; color:var(--ink2); cursor:pointer; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.sd-row:hover { background:var(--bg); color:#4E5BEF; }
.fchip { display:inline-flex; align-items:center; gap:5px; font-size:12px; background:#fff; border:1px solid var(--line); border-radius:16px; padding:4px 12px; color:var(--ink2); white-space:nowrap; cursor:pointer; font-family:inherit; position:relative; }
.fchip.on { background:var(--brand-soft); border-color:var(--brand-line); color:var(--brand); font-weight:600; }
.city-native { position:absolute; inset:0; width:100%; opacity:0; cursor:pointer; font-family:inherit; }
.opp-count { margin:14px 20px 0; font-size:13px; color:var(--ink2); }
.opp-count b { color:var(--brand); }

.opp { position:relative; background:#fff; border:1px solid var(--line); border-radius:16px; padding:16px 18px; box-shadow:0 1px 2px rgba(20,24,40,.05),0 3px 10px rgba(20,24,40,.05); }
.opp-top { display:flex; gap:12px; align-items:flex-start; }
.coav { width:34px; height:34px; border-radius:10px; background:#EEF1F6; color:#3E4654; display:inline-flex; align-items:center; justify-content:center; font-size:14px; font-weight:700; flex:0 0 auto; border:1px solid var(--line); }
.opp-i { min-width:0; flex:1; }
.opp .co { font-weight:700; font-size:15px; color:var(--ink); }
.opp .ro { font-size:12.5px; color:var(--ink2); margin-top:1px; }
.opp .salr { font-size:13px; color:var(--warn); font-weight:700; margin-top:4px; }
.score { text-align:center; flex:0 0 auto; }
.ring { width:48px; height:48px; border-radius:50%; display:inline-flex; align-items:center; justify-content:center; font-weight:800; font-size:17px; border:3px solid; }
.ring.hi { border-color:var(--good); color:var(--good); }
.ring.mid { border-color:var(--warn); color:var(--warn); }
.ring.low { border-color:var(--bad); color:var(--bad); }
.lbl { font-size:10px; color:var(--ink3); margin-top:3px; }
.opp .hit { font-size:11.5px; margin-top:12px; color:var(--ink2); background:var(--line2); border-radius:9px; padding:7px 11px; line-height:1.6; }
.opp .hit .g { color:var(--good); font-weight:600; }
.opp .hit .r { color:var(--bad); font-weight:600; }
.opp-act { display:flex; gap:9px; margin-top:13px; align-items:center; }
.opp .btn { display:inline-flex; align-items:center; gap:6px; justify-content:center; border:none; border-radius:10px; padding:8px 16px; font-size:13px; font-weight:600; white-space:nowrap; cursor:pointer; font-family:inherit; }
.opp .btn.ai { flex:1; background:var(--ai-grad); color:#fff; box-shadow:0 2px 10px rgba(110,80,240,.3); }
.opp .btn.ai:disabled { opacity:.55; cursor:not-allowed; }
.opp .btn.g { background:#fff; border:1px solid var(--line); color:var(--ink2); }
.opp .unmatched-badge { margin-bottom:8px; }

.page-header {
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 14px 16px 12px;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1A1D26;
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
  background: #4E5BEF;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.card-list {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
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
  background: #4E5BEF;
  border-color: #4E5BEF;
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

.header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.mobile-search-trigger {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 52%;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #F5F6F8;
  padding: 7px 12px;
  cursor: pointer;
  font-family: inherit;
}
.mst-icon { font-size: 13px; }
.mst-text {
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-overlay {
  position: fixed;
  inset: 0;
  z-index: 500;
  background: #fff;
  display: flex;
  flex-direction: column;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
}
.sb-icon { font-size: 15px; }
.search-ov-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 15px;
  font-family: inherit;
  background: transparent;
}
.search-ov-cancel {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #4E5BEF;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}
.search-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.search-sect { padding: 8px 14px; }
.search-sect-title {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
  margin-bottom: 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-clear {
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}
.search-row {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 11px 4px;
  font-size: 14px;
  color: #334155;
  cursor: pointer;
  font-family: inherit;
  border-bottom: 1px solid #f1f5f9;
}
.search-empty {
  text-align: center;
  color: #cbd5e1;
  font-size: 13px;
  margin-top: 40px;
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
    grid-template-columns: 1fr 1fr;
    padding: 20px 32px;
    max-width: 1100px;
    margin: 0 auto;
    width: 100%;
  }
  .mhead, .opp-count {
    max-width: 1100px;
    margin-left: auto;
    margin-right: auto;
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
  border: 2px solid #4E5BEF;
}

.high-badge {
  position: absolute;
  top: -9px;
  left: 14px;
  background: #4E5BEF;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 700;
}

.unmatched-badge {
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

.unmatched-badge:hover {
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
  background: #4E5BEF;
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
  color: #1A1D26;
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
  color: #4E5BEF;
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
  color: #DB9A2D;
}

.tier-unknown {
  background: #f1f5f9;
  color: #94a3b8;
}

.reason-text {
  font-size: 11px;
  color: #64748b;
}

.gap-row {
  margin-bottom: 6px;
}
.gap-text {
  font-size: 11px;
  color: #DB9A2D;
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
  color: #4E5BEF;
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
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
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
