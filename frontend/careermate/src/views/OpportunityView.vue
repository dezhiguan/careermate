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
      <div class="filter-row">
        <input
          v-model="searchInput"
          class="search-input"
          type="search"
          placeholder="搜索：Redis / Java / 算法..."
          @input="onSearchInput"
        />
        <select v-model="filterCity" class="filter-select">
          <option value="">全部城市</option>
          <option v-for="city in cityOptions" :key="city" :value="city">{{ city }}</option>
        </select>
        <select v-model="filterPosition" class="filter-select">
          <option value="">全部岗位</option>
          <option v-for="pos in positionOptions" :key="pos" :value="pos">{{ pos }}</option>
        </select>
      </div>
    </header>

    <div v-if="!hasResume && !loading" class="resume-banner">
      <span class="resume-banner-text">传简历获得 AI 匹配分</span>
      <button type="button" class="resume-banner-btn" @click="goUploadResume">上传简历</button>
    </div>

    <div v-if="loading" class="card-list">
      <div v-for="n in 3" :key="n" class="skeleton-card">
        <div class="skeleton-line wide" />
        <div class="skeleton-line" />
        <div class="skeleton-block" />
        <div class="skeleton-btn" />
      </div>
    </div>

    <div v-else-if="filteredItems.length === 0" class="empty-state">
      暂无机会 · 换个关键词试试
    </div>

    <div v-else class="card-list">
      <article
        v-for="(item, index) in filteredItems"
        :key="item.jdId"
        class="jd-card"
        :class="{ 'jd-card-high': item.matchTier === 'HIGH' }"
      >
        <div v-if="item.matchTier === 'HIGH'" class="high-badge">⭐ AI 强推{{ index === 0 ? ' TOP 1' : '' }}</div>

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
          <div v-if="hasResume" class="match-badge">
            <div class="match-label">AI 匹配分</div>
            <div class="match-score">{{ item.matchScore ?? '—' }}</div>
          </div>
        </div>

        <div v-if="hasResume" class="tier-row">
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
            class="btn-primary"
            :disabled="preparingId === item.jdId"
            @click="handlePrepare(item)"
          >
            {{ preparingId === item.jdId ? '准备中...' : '用 AI 准备 →' }}
          </button>
          <button
            type="button"
            class="btn-ghost"
            :disabled="!item.externalUrl"
            @click="openExternal(item.externalUrl)"
          >
            外链投递
          </button>
        </div>
      </article>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOpportunities, prepareWithAi } from '../api/opportunity'

const router = useRouter()

const searchInput = ref('')
const keyword = ref('')
const filterCity = ref('')
const filterPosition = ref('')
const items = ref([])
const hasResume = ref(false)
const loading = ref(true)
const error = ref('')
const preparingId = ref('')

let debounceTimer = null

const cityOptions = computed(() => {
  const set = new Set()
  items.value.forEach((item) => {
    if (item.city) set.add(item.city)
  })
  return [...set]
})

const positionOptions = computed(() => {
  const set = new Set()
  items.value.forEach((item) => {
    if (item.title) set.add(item.title)
  })
  return [...set]
})

const filteredItems = computed(() => {
  let list = items.value.slice(0, 10)
  if (filterCity.value) {
    list = list.filter((item) => item.city === filterCity.value)
  }
  if (filterPosition.value) {
    list = list.filter((item) => item.title === filterPosition.value)
  }
  return list
})

function onSearchInput() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    keyword.value = searchInput.value.trim()
    fetchList()
  }, 300)
}

async function fetchList() {
  loading.value = true
  error.value = ''
  try {
    const data = await listOpportunities({
      keyword: keyword.value || undefined,
      page: 1,
      size: 10,
    })
    items.value = data?.items || []
    hasResume.value = !!data?.hasResume
  } catch (e) {
    error.value = e.message || '加载失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

async function handlePrepare(item) {
  preparingId.value = item.jdId
  try {
    const resp = await prepareWithAi(item.jdId)
    const path = resp?.redirectPath || `/chat/${resp?.workspaceId}`
    router.push(path)
  } catch (e) {
    error.value = e.message || '准备失败'
  } finally {
    preparingId.value = ''
  }
}

function openExternal(url) {
  if (url) window.open(url, '_blank', 'noopener')
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

onMounted(fetchList)
</script>

<style scoped>
.opportunity-page {
  min-height: 100%;
  background: #f8fafc;
  padding-bottom: 16px;
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

.filter-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.search-input {
  flex: 1;
  min-width: 160px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #0f172a;
  background: #fff;
}

.filter-select {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
  color: #334155;
  background: #fff;
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

.card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0 10px;
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
  gap: 8px;
}

.btn-primary {
  flex: 1;
  background: #4f46e5;
  color: #fff;
  border: 0;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-ghost {
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  cursor: pointer;
}

.btn-ghost:disabled {
  opacity: 0.45;
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
