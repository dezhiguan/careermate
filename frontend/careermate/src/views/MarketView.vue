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
      <p class="filter-sub">数据来源：知识库 AI 分析 · 切换筛选自动刷新 · 顶部搜索查公司</p>
    </header>

    <div class="market-content">

      <!-- 薪资分位 -->
      <section class="card">
        <div class="card-title">薪资分位分布</div>
        <div v-if="salaryLoading" class="skeleton-group">
          <div class="skeleton" style="height:36px;width:50%;margin-bottom:8px" />
          <div class="skeleton" style="height:14px;width:80%" />
        </div>
        <template v-else-if="salaryData">
          <div class="salary-main">{{ salaryData.p50 }}</div>
          <div class="salary-sub">市场中位薪资（P50）</div>
          <div class="percentile-row">
            <div class="p-item">
              <div class="p-label">P25</div>
              <div class="p-val">{{ salaryData.p25 }}</div>
            </div>
            <div class="p-item">
              <div class="p-label">P50</div>
              <div class="p-val highlight">{{ salaryData.p50 }}</div>
            </div>
            <div class="p-item">
              <div class="p-label">P75</div>
              <div class="p-val">{{ salaryData.p75 }}</div>
            </div>
            <div class="p-item">
              <div class="p-label">P90</div>
              <div class="p-val">{{ salaryData.p90 }}</div>
            </div>
          </div>
          <div class="trend-tag" :class="salaryData.trend === '上涨' ? 'tag-up' : 'tag-flat'">
            {{ salaryData.trend }}
          </div>
          <div class="ai-box">
            <span class="ai-label">⚡ AI 解读</span>
            <p class="ai-text">{{ salaryData.aiSummary }}</p>
          </div>
          <p class="disclaimer">基于 AI 分析，仅供参考</p>
        </template>
        <div v-else class="empty-tip">暂无薪资数据</div>
      </section>

      <!-- 跳转谈薪 -->
      <button type="button" class="cta-btn" @click="router.push('/')">
        去小职练谈薪 →
      </button>

      <!-- 技能热榜 -->
      <section class="card">
        <div class="card-title">
          技能需求热榜
          <span v-if="role" class="card-context">· {{ role }}</span>
        </div>
        <div v-if="skillsLoading" class="skeleton-group">
          <div v-for="i in 4" :key="i" class="skeleton" style="height:44px;margin-bottom:8px" />
        </div>
        <template v-else-if="skillsData?.skills?.length">
          <div class="skill-list">
            <div v-for="s in skillsData.skills" :key="s.rank" class="skill-item">
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
          <div v-if="skillsData.aiSummary" class="ai-box" style="margin-top:12px">
            <span class="ai-label">⚡ AI 解读</span>
            <p class="ai-text">{{ skillsData.aiSummary }}</p>
          </div>
        </template>
        <div v-else class="empty-tip">暂无技能数据</div>
      </section>

      <!-- 简历 Gap 分析 -->
      <section class="card">
        <div class="card-title">
          简历 Gap 分析
          <span v-if="gapContext" class="card-context">· {{ gapContext }}</span>
        </div>
        <div v-if="gapLoading" class="skeleton-group">
          <div class="skeleton" style="height:14px;width:70%;margin-bottom:8px" />
          <div class="skeleton" style="height:14px;width:90%;margin-bottom:8px" />
          <div class="skeleton" style="height:14px;width:60%" />
        </div>
        <template v-else-if="gapData">
          <div class="score-row">
            <div class="score-circle">
              <span class="score-num">{{ gapData.matchScore }}</span>
              <span class="score-unit">分</span>
            </div>
            <div class="score-desc">市场匹配度</div>
          </div>
          <div v-if="gapData.hasSkills?.length" class="gap-group">
            <div class="gap-label has">✓ 你已具备</div>
            <div class="chip-row">
              <span v-for="s in gapData.hasSkills" :key="s" class="chip chip-has">{{ s }}</span>
            </div>
          </div>
          <div v-if="gapData.missingSkills?.length" class="gap-group">
            <div class="gap-label miss">⚠ 建议补齐</div>
            <div class="chip-row">
              <span v-for="s in gapData.missingSkills" :key="s" class="chip chip-miss">{{ s }}</span>
            </div>
          </div>
          <div v-if="gapData.topSuggestion" class="suggest-box">
            🎯 {{ gapData.topSuggestion }}
          </div>
          <div v-if="gapData.aiSummary" class="ai-box" style="margin-top:10px">
            <span class="ai-label">⚡ AI 解读</span>
            <p class="ai-text">{{ gapData.aiSummary }}</p>
          </div>
        </template>
        <div v-else class="empty-tip">请先上传简历以获取 Gap 分析</div>
      </section>

      <!-- 公司情报（联动顶部搜索） -->
      <section class="card">
        <div class="card-title">
          公司情报
          <span v-if="companyKeyword" class="card-context">· {{ companyKeyword }}</span>
        </div>
        <div v-if="companyLoading" class="skeleton-group">
          <div class="skeleton" style="height:16px;width:40%;margin-bottom:8px" />
          <div class="skeleton" style="height:12px;width:90%;margin-bottom:6px" />
          <div class="skeleton" style="height:12px;width:70%" />
        </div>
        <div v-else-if="companyData" class="company-result">
          <div class="company-name">{{ companyData.companyName }}</div>
          <div class="company-meta">{{ companyData.scale }} · {{ companyData.stage }}</div>
          <div v-if="companyData.techStack?.length" class="chip-row" style="margin:8px 0">
            <span v-for="t in companyData.techStack" :key="t" class="chip chip-tech">{{ t }}</span>
          </div>
          <div v-if="companyData.currentJds?.length" class="company-jds">
            在招：{{ companyData.currentJds.join(' · ') }}
          </div>
          <p v-if="companyData.aiSummary" class="company-summary">{{ companyData.aiSummary }}</p>
        </div>
        <div v-else-if="companySearched" class="empty-tip">未找到「{{ companyKeyword }}」相关数据</div>
        <div v-else class="empty-tip">在顶部搜索框输入公司名查询</div>
      </section>

    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSalaryInsight, getSkillTrends, getResumeGap, getCompanyInsight } from '../api/market'
import { getCareerProfile } from '../api/profile'

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

const city = ref('')
const role = ref('')
const years = ref('')

const salaryLoading = ref(true)
const skillsLoading = ref(true)
const gapLoading = ref(true)
const companyLoading = ref(false)
const companySearched = ref(false)

const salaryData = ref(null)
const skillsData = ref(null)
const gapData = ref(null)
const companyData = ref(null)
const companyKeyword = ref('')

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

function isRoleKeyword(q) {
  return /(后端|前端|算法|测试|开发|工程师|架构|产品|运营|Java|Python|Go|C\+\+)/i.test(q)
}

function applyFilters() {
  companyKeyword.value = ''
  companyData.value = null
  companySearched.value = false
  router.replace({
    query: {
      city: city.value,
      role: role.value,
      years: years.value,
    },
  })
  runMarketSearch('')
}

function syncFiltersFromRoute() {
  const qCity = route.query.city
  const qRole = route.query.role
  const qYears = route.query.years
  if (qCity && MARKET_CITIES.includes(String(qCity))) city.value = String(qCity)
  if (qRole) role.value = String(qRole)
  if (qYears && MARKET_YEARS.includes(String(qYears))) years.value = String(qYears)
}

async function runMarketSearch(keyword) {
  const q = typeof keyword === 'string' ? keyword.trim() : ''
  let searchType = null

  if (q) {
    if (MARKET_CITIES.includes(q)) {
      searchType = 'city'
      city.value = q
    } else if (isRoleKeyword(q)) {
      searchType = 'role'
      role.value = q
    } else {
      searchType = 'company'
      companyKeyword.value = q
    }
  }

  salaryLoading.value = true
  skillsLoading.value = true
  gapLoading.value = true
  salaryData.value = null
  skillsData.value = null
  gapData.value = null

  if (searchType === 'company') {
    companyLoading.value = true
    companyData.value = null
    companySearched.value = false
  } else if (searchType === 'city' || searchType === 'role') {
    companyLoading.value = false
    companyData.value = null
    companyKeyword.value = ''
    companySearched.value = false
  }

  const tasks = []

  if (city.value && role.value && years.value) {
    tasks.push(
      getSalaryInsight({ role: role.value, city: city.value, years: years.value })
        .then((r) => { salaryData.value = r })
        .catch(() => {})
        .finally(() => { salaryLoading.value = false })
    )
  } else {
    salaryLoading.value = false
  }

  if (role.value) {
    tasks.push(
      getSkillTrends({ role: role.value })
        .then((r) => { skillsData.value = r })
        .catch(() => {})
        .finally(() => { skillsLoading.value = false })
    )
  } else {
    skillsLoading.value = false
  }

  tasks.push(
    getResumeGap()
      .then((r) => { gapData.value = r })
      .catch(() => {})
      .finally(() => { gapLoading.value = false })
  )

  if (searchType === 'company' && q) {
    tasks.push(
      getCompanyInsight(q)
        .then((r) => { companyData.value = r })
        .catch(() => { companyData.value = null })
        .finally(() => {
          companyLoading.value = false
          companySearched.value = true
        })
    )
  }

  await Promise.allSettled(tasks)
}

watch(
  () => [route.query.q, route.query.t],
  ([q]) => {
    if (q) runMarketSearch(String(q))
  }
)

onMounted(async () => {
  try {
    const profile = await getCareerProfile()
    if (profile?.targetCity?.trim()) city.value = profile.targetCity.trim()
    if (profile?.targetRole?.trim()) role.value = profile.targetRole.trim()
    if (profile?.seniority?.trim()) years.value = profile.seniority.trim()
  } catch {
    // 画像加载失败时保持空，由空态提示
  }
  if (!city.value) city.value = '广州'
  if (!role.value) role.value = 'Java后端'
  if (!years.value) years.value = '3-5年'
  syncFiltersFromRoute()
  if (route.query.q) {
    await runMarketSearch(String(route.query.q))
  } else {
    await runMarketSearch('')
  }
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
</script>

<style scoped>
.market-page { min-height: 100%; background: #f8fafc; padding-bottom: 80px; }
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

/* 薪资 */
.salary-main { font-size: 36px; font-weight: 800; color: #4f46e5; line-height: 1; }
.salary-sub { font-size: 11px; color: #64748b; margin: 4px 0 14px; }
.percentile-row { display: flex; justify-content: space-between; margin-bottom: 12px; }
.p-item { text-align: center; }
.p-label { font-size: 10px; color: #64748b; font-weight: 600; }
.p-val { font-size: 13px; font-weight: 700; color: #334155; margin-top: 2px; }
.p-val.highlight { color: #4f46e5; font-size: 15px; }
.trend-tag { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; margin-bottom: 12px; }
.tag-up { background: #dcfce7; color: #15803d; }
.tag-flat { background: #f1f5f9; color: #475569; }

/* AI box */
.ai-box { background: linear-gradient(135deg,#eef2ff,#ede9fe); border-radius: 8px; padding: 10px 12px; }
.ai-label { font-size: 10px; font-weight: 700; color: #4338ca; display: block; margin-bottom: 4px; }
.ai-text { font-size: 12px; color: #1e1b4b; line-height: 1.6; margin: 0; }
.disclaimer { font-size: 10px; color: #94a3b8; margin: 8px 0 0; }

/* CTA */
.cta-btn { width: 100%; padding: 14px; background: #b45309; color: #fff; border: none; border-radius: 12px; font-size: 14px; font-weight: 700; cursor: pointer; }

/* 技能 */
.skill-list { display: flex; flex-direction: column; gap: 10px; }
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

/* 公司情报 */
.company-result { margin-top: 0; }
.company-name { font-size: 15px; font-weight: 700; color: #0f172a; margin-bottom: 3px; }
.company-meta { font-size: 11px; color: #64748b; margin-bottom: 6px; }
.company-jds { font-size: 11px; color: #334155; margin-bottom: 6px; }
.company-summary { font-size: 12px; color: #475569; line-height: 1.6; margin: 0; }

/* 骨架屏 */
.skeleton-group { display: flex; flex-direction: column; }
.skeleton { background: linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 50%,#f1f5f9 75%); background-size: 200% 100%; animation: shimmer 1.4s infinite; border-radius: 6px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

.empty-tip { text-align: center; color: #94a3b8; font-size: 13px; padding: 20px 0; }
</style>
