<template>
  <div class="market-page">
    <header class="filter-bar">
      <h1 class="filter-title">广州 · Java 后端 · 3-5 年</h1>
      <p class="filter-sub">2,847 份样本 · 数据周期：近 90 天 · 含股权折算</p>
      <div class="filter-chips">
        <button
          v-for="chip in headerFilterOptions"
          :key="chip"
          type="button"
          class="filter-chip"
          :class="{ active: activeHeaderFilters[chip] }"
          @click="toggleHeaderFilter(chip)"
        >
          {{ chip }}
        </button>
      </div>
    </header>

    <div class="market-content">
      <section class="card positioning-card">
        <template v-if="salaryLoading">
          <div class="skeleton-line" style="width:60%;height:11px" />
          <div class="skeleton-line" style="width:40%;height:30px;margin:6px 0" />
          <div class="skeleton-line" style="width:50%;height:11px" />
        </template>
        <template v-else>
          <div class="pos-label">市场薪资中位 · P50</div>
          <div class="pos-value">{{ salaryData?.p50 || '28K' }}</div>
          <div class="pos-sub">P25: {{ salaryData?.p25 || '20K' }} · P75: {{ salaryData?.p75 || '38K' }}</div>
        </template>
      </section>

      <section class="card offer-card">
        <div class="offer-row">
          <div>
            <div class="offer-label">WXG Offer · P82</div>
            <div class="offer-value">42K</div>
          </div>
          <div class="offer-badge">
            <div class="offer-badge-top">超定位</div>
            <div class="offer-badge-val">+31%</div>
          </div>
        </div>
      </section>

      <section class="card percentile-card">
        <div class="section-title">分位分布</div>
        <div class="percentile-bar">
          <div class="p50-line" />
          <div class="marker you-marker">
            <span class="marker-tip top-tip">你 32K</span>
            <span class="marker-dot you-dot" />
          </div>
          <div class="marker offer-marker">
            <span class="marker-dot offer-dot" />
            <span class="marker-tip bottom-tip">Offer 42K</span>
          </div>
        </div>
        <div class="percentile-labels">
          <span><b>P25</b><br>{{ salaryData?.p25 || '22K' }}</span>
          <span class="p50-text"><b>P50</b><br>{{ salaryData?.p50 || '28K' }}</span>
          <span><b>P75</b><br>{{ salaryData?.p75 || '38K' }}</span>
          <span><b>P90</b><br>{{ salaryData?.p90 || '50K' }}</span>
        </div>
      </section>

      <section class="card trend-card">
        <div class="trend-head">
          <span class="section-title">近 6 月 P50 走势</span>
          <span class="trend-up">↑ +12%</span>
        </div>
        <div class="bar-chart">
          <div
            v-for="(bar, i) in barChartData"
            :key="bar.month"
            class="bar-col"
          >
            <div
              class="bar"
              :class="bar.tone"
              :style="{ height: bar.height + '%' }"
            />
          </div>
        </div>
        <div class="bar-months">
          <span
            v-for="(bar, i) in barChartData"
            :key="'m-' + bar.month"
            :class="{ 'month-active': i === barChartData.length - 1 }"
          >
            {{ bar.month }}
          </span>
        </div>
      </section>

      <section class="card advice-card">
        <div class="advice-title">🎯 AI 薪资解读</div>
        <template v-if="salaryLoading">
          <div class="skeleton-line" style="width:90%;height:12px;margin-bottom:6px" />
          <div class="skeleton-line" style="width:75%;height:12px" />
        </template>
        <p v-else class="advice-text">{{ salaryData?.aiSummary || '暂无数据' }}</p>
        <p class="advice-disclaimer">基于 AI 分析，仅供参考</p>
      </section>

      <button type="button" class="salary-cta" @click="goToChat">
        带回小职 · 练谈薪 →
      </button>

      <section class="card heat-card">
        <div class="heat-head">
          <div>
            <div class="heat-title">广州 · Java 后端 招聘热度</div>
            <div class="heat-sub">活跃 JD 数 / 近 6 个月</div>
          </div>
          <span class="yoy-badge">↑ +24% YoY</span>
        </div>
        <div class="chart-wrap">
          <svg viewBox="0 0 300 120" preserveAspectRatio="none" class="line-chart">
            <defs>
              <linearGradient id="marketHeatGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#6366f1" stop-opacity="0.4" />
                <stop offset="100%" stop-color="#6366f1" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path
              d="M0,80 L60,72 L120,65 L180,50 L240,30 L300,18 L300,120 L0,120 Z"
              fill="url(#marketHeatGrad)"
            />
            <path
              d="M0,80 L60,72 L120,65 L180,50 L240,30 L300,18"
              stroke="#6366f1"
              stroke-width="2.5"
              fill="none"
            />
            <circle cx="0" cy="80" r="3" fill="#6366f1" />
            <circle cx="60" cy="72" r="3" fill="#6366f1" />
            <circle cx="120" cy="65" r="3" fill="#6366f1" />
            <circle cx="180" cy="50" r="3" fill="#6366f1" />
            <circle cx="240" cy="30" r="3" fill="#6366f1" />
            <circle cx="300" cy="18" r="4" fill="#4f46e5" />
          </svg>
        </div>
        <div class="heat-months">
          <span v-for="(item, i) in heatMonths" :key="item.month" :class="{ 'month-active': i === heatMonths.length - 1 }">
            {{ item.month }}<br><b>{{ item.value }}</b>
          </span>
        </div>
        <div class="heat-ai">
          ⚡ AI 解读：广州 Java 后端 6 月活跃 JD 同比上涨 24%，<b class="highlight-green">行业回暖</b>。其中电商/直播领域涨 38%，金融科技涨 18%。<b>是投递的好时机</b>。
        </div>
      </section>

      <section class="card skills-card">
        <div class="skills-head">
          <div>
            <div class="skills-title">技能薪资涨幅榜 TOP 6</div>
            <div class="skills-sub">含该技能 vs 不含的薪资差</div>
          </div>
          <select class="skills-select" aria-label="时间范围">
            <option>近 90 天</option>
          </select>
        </div>
        <div class="skill-list">
          <div v-for="skill in skillRankings" :key="skill.rank" class="skill-item">
            <div class="skill-row1">
              <div class="skill-left">
                <span class="rank-badge" :class="skill.rank <= 2 ? 'rank-warn' : 'rank-gray'">{{ skill.rank }}</span>
                <span class="skill-name">{{ skill.name }}</span>
                <span class="own-chip" :class="skill.owned ? 'chip-success' : 'chip-warning'">
                  {{ skill.owned ? '✓你有' : '你没' }}
                </span>
              </div>
              <span class="skill-pct">+{{ skill.pct }}%</span>
            </div>
            <div class="skill-bar-track">
              <div
                class="skill-bar-fill"
                :class="skill.owned ? 'bar-purple' : 'bar-green'"
                :style="{ width: skill.width + '%' }"
              />
            </div>
          </div>
        </div>
        <div class="invest-tip">
          <template v-if="gapLoading">
            <div class="skeleton-line" style="width:85%;height:12px" />
          </template>
          <template v-else-if="gapData?.topSuggestion">
            🎯 {{ gapData.topSuggestion }}
          </template>
          <template v-else>
            🎯 投资建议：你已命中 {{ gapData?.hasSkills?.length || 3 }} 个高频技能，继续完善技术栈。
          </template>
        </div>
      </section>

      <section class="card company-search-card">
        <div class="section-title">目标公司情报</div>
        <div class="company-search-row">
          <input
            v-model="companyInput"
            class="company-search-input"
            placeholder="输入公司名，如：腾讯、字节跳动"
            @keydown.enter="searchCompany"
          >
          <button
            type="button"
            class="company-search-btn"
            :disabled="companyLoading || !companyInput.trim()"
            @click="searchCompany"
          >
            {{ companyLoading ? '查询中...' : '查询' }}
          </button>
        </div>

        <div v-if="companyLoading" class="company-result">
          <div class="skeleton-line" style="width:60%;height:13px;margin-bottom:8px" />
          <div class="skeleton-line" style="width:90%;height:11px;margin-bottom:6px" />
          <div class="skeleton-line" style="width:75%;height:11px" />
        </div>

        <div v-else-if="companyData" class="company-result">
          <div class="company-result-name">{{ companyData.companyName }}</div>
          <div class="company-result-meta">{{ companyData.scale }} · {{ companyData.stage }}</div>
          <div v-if="companyData.techStack?.length" class="company-tech-chips">
            <span v-for="t in companyData.techStack" :key="t" class="tech-chip">{{ t }}</span>
          </div>
          <div v-if="companyData.currentJds?.length" class="company-jds">
            在招：{{ companyData.currentJds.join(' · ') }}
          </div>
          <p class="company-summary">{{ companyData.aiSummary }}</p>
        </div>
      </section>

      <section class="card hc-card">
        <div class="hc-head">
          <div>
            <div class="hc-title">大厂 HC 动态 · 你的目标公司</div>
            <div class="hc-sub">基于招聘网站 HC 变化 · 数据滞后 1-3 天</div>
          </div>
        </div>
        <div class="hc-filters">
          <button
            type="button"
            class="hc-chip"
            :class="{ active: hcFilter === 'all' }"
            @click="hcFilter = 'all'"
          >
            全部
          </button>
          <button
            type="button"
            class="hc-chip chip-success"
            :class="{ active: hcFilter === 'expand' }"
            @click="hcFilter = 'expand'"
          >
            ✓ 扩招（12）
          </button>
          <button
            type="button"
            class="hc-chip chip-danger"
            :class="{ active: hcFilter === 'shrink' }"
            @click="hcFilter = 'shrink'"
          >
            ⚠ 缩招（3）
          </button>
        </div>
        <div class="company-grid">
          <article
            v-for="co in visibleCompanies"
            :key="co.id"
            class="company-card"
            :style="{ borderColor: co.borderColor }"
          >
            <div class="company-top">
              <div class="company-info">
                <div class="company-logo" :style="{ background: co.logoBg }">{{ co.logo }}</div>
                <div>
                  <div class="company-name">{{ co.name }}</div>
                  <div class="company-dept">{{ co.dept }}</div>
                </div>
              </div>
              <span class="status-badge" :class="co.statusClass">{{ co.status }}</span>
            </div>
            <div class="company-body" v-html="co.bodyHtml" />
            <div class="company-foot" :class="co.footClass">{{ co.foot }}</div>
          </article>

          <div class="company-card add-card">
            <svg class="add-icon" viewBox="0 0 24 24" aria-hidden="true">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            <div class="add-title">添加关注公司</div>
            <div class="add-sub">追踪 HC + 内推消息</div>
          </div>

          <div class="company-card suggest-card">
            <div class="suggest-title">🎯 本周投递建议</div>
            <div class="suggest-list">
              1. 网易游戏（91分 · 扩招 +138%）<br>
              2. 腾讯 WXG（87分 · 扩招 +68%）
            </div>
            <button type="button" class="suggest-btn" @click="goToOpportunity">
              回机会页投递 →
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSalaryInsight, getSkillTrends, getResumeGap, getCompanyInsight } from '../api/market'

const router = useRouter()

const salaryLoading = ref(true)
const skillsLoading = ref(true)
const gapLoading = ref(true)
const companyLoading = ref(false)

const salaryData = ref(null)
const skillsData = ref(null)
const gapData = ref(null)
const companyData = ref(null)

const companyInput = ref('')

onMounted(async () => {
  await Promise.allSettled([
    getSalaryInsight()
      .then((r) => { salaryData.value = r })
      .finally(() => { salaryLoading.value = false }),
    getSkillTrends()
      .then((r) => { skillsData.value = r })
      .finally(() => { skillsLoading.value = false }),
    getResumeGap()
      .then((r) => { gapData.value = r })
      .finally(() => { gapLoading.value = false }),
  ])
})

async function searchCompany() {
  const q = companyInput.value.trim()
  if (!q) return
  companyLoading.value = true
  companyData.value = null
  try {
    companyData.value = await getCompanyInsight(q)
  } catch (e) {
    companyData.value = null
  } finally {
    companyLoading.value = false
  }
}

const headerFilterOptions = ['广州', '3-5y', '大厂', '含股']
const activeHeaderFilters = reactive({
  广州: true,
  '3-5y': false,
  大厂: false,
  含股: false,
})

const hcFilter = ref('all')

const barChartData = [
  { month: '1月', height: 62, tone: 'light' },
  { month: '2月', height: 58, tone: 'light' },
  { month: '3月', height: 64, tone: 'light' },
  { month: '4月', height: 72, tone: 'mid' },
  { month: '5月', height: 80, tone: 'deep' },
  { month: '6月', height: 88, tone: 'primary' },
]

const heatMonths = [
  { month: '1月', value: '820' },
  { month: '2月', value: '880' },
  { month: '3月', value: '930' },
  { month: '4月', value: '1,050' },
  { month: '5月', value: '1,180' },
  { month: '6月', value: '1,247' },
]

const skillRankings = computed(() => {
  if (!skillsData.value?.skills?.length) {
    return [
      { rank: 1, name: '大模型/RAG', owned: false, pct: 28, width: 92 },
      { rank: 2, name: 'Flink 流计算', owned: false, pct: 22, width: 78 },
      { rank: 3, name: 'Redis 集群', owned: true, pct: 18, width: 64 },
      { rank: 4, name: 'K8s / 云原生', owned: false, pct: 15, width: 54 },
      { rank: 5, name: 'RocketMQ', owned: true, pct: 12, width: 42 },
      { rank: 6, name: 'MySQL 优化', owned: true, pct: 8, width: 28 },
    ]
  }
  const hasSet = new Set((gapData.value?.hasSkills || []).map((s) => s.toLowerCase()))
  const growthToPct = { 快涨: 28, 上涨: 15, 稳定: 8, 下降: 3 }
  return skillsData.value.skills.map((s, i) => ({
    rank: s.rank ?? i + 1,
    name: s.name,
    owned: hasSet.size > 0
      ? hasSet.has(s.name.toLowerCase())
      : i % 2 === 0,
    pct: growthToPct[s.growth] ?? 10,
    width: Math.max(20, 100 - (s.rank - 1) * 13),
  }))
})

const companies = [
  {
    id: 'tencent',
    filter: 'expand',
    borderColor: '#10b981',
    logo: '腾',
    logoBg: '#4f46e5',
    name: '腾讯 WXG',
    dept: '微信事业群',
    status: '扩招',
    statusClass: 'badge-expand',
    bodyHtml: '后端 HC：<b style="color:#10b981">28 → 47（+68%）</b><br>重点缺：Java 高级 / 系统架构<br>6 月新增 IM 中台 12 HC',
    foot: '💡 你简历贴合度 87，立刻投',
    footClass: 'foot-green',
  },
  {
    id: 'bytedance',
    filter: 'all',
    borderColor: '#f59e0b',
    logo: '字',
    logoBg: '#0ea5e9',
    name: '字节·飞书',
    dept: '协作平台',
    status: '观望',
    statusClass: 'badge-watch',
    bodyHtml: '后端 HC：<b style="color:#f59e0b">15 → 14（持平）</b><br>主推 Go，Java HC 收紧<br>流程慢 · 平均 6 周',
    foot: '⚠️ 优先级降，先投腾讯/网易',
    footClass: 'foot-yellow',
  },
  {
    id: 'netease',
    filter: 'expand',
    borderColor: '#10b981',
    logo: '网',
    logoBg: '#10b981',
    name: '网易游戏',
    dept: '广州工作室',
    status: '扩招',
    statusClass: 'badge-expand',
    bodyHtml: '后端 HC：<b style="color:#10b981">8 → 19（+138%）</b><br>缺：Java 服务端 / 弹幕/IM<br>内推可加面，5 天出结果',
    foot: '💡 91 分匹配 · 最优先',
    footClass: 'foot-green',
  },
  {
    id: 'ecommerce',
    filter: 'shrink',
    borderColor: '#ef4444',
    logo: 'A',
    logoBg: '#94a3b8',
    name: '某电商 A',
    dept: '华南事业部',
    status: '缩招',
    statusClass: 'badge-shrink',
    bodyHtml: '后端 HC：<b style="color:#ef4444">22 → 9（-59%）</b><br>5 月有 OD 转正暂停<br>暂时回避',
    foot: '⚠️ 不建议投，浪费弹药',
    footClass: 'foot-red',
  },
]

const visibleCompanies = computed(() => {
  if (hcFilter.value === 'all') return companies
  if (hcFilter.value === 'expand') {
    return companies.filter((c) => c.filter === 'expand')
  }
  return companies.filter((c) => c.filter === 'shrink')
})

function toggleHeaderFilter(chip) {
  activeHeaderFilters[chip] = !activeHeaderFilters[chip]
}

function goToChat() {
  router.push('/chat')
}

function goToOpportunity() {
  router.push('/opportunity')
}
</script>

<style scoped>
.market-page {
  min-height: 100%;
  background: #f8fafc;
}

.filter-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 14px 10px;
}

.filter-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.filter-sub {
  margin: 4px 0 0;
  font-size: 11px;
  color: #64748b;
}

.filter-chips {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  -webkit-overflow-scrolling: touch;
}

.filter-chip {
  flex-shrink: 0;
  border: 0;
  background: #f1f5f9;
  color: #475569;
  padding: 5px 12px;
  border-radius: 14px;
  font-size: 11px;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
}

.filter-chip.active {
  background: #4f46e5;
  color: #fff;
}

.market-content {
  padding: 14px;
  padding-bottom: 80px;
}

.card {
  margin-bottom: 12px;
}

.positioning-card {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
  border-radius: 12px;
  padding: 14px;
}

.pos-label {
  font-size: 11px;
  opacity: 0.85;
}

.pos-value {
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
  margin: 4px 0;
}

.pos-sub {
  font-size: 11px;
  opacity: 0.85;
}

.offer-card {
  background: #fff;
  border: 2px solid #10b981;
  border-radius: 12px;
  padding: 14px;
}

.offer-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.offer-label {
  font-size: 11px;
  color: #15803d;
  font-weight: 600;
}

.offer-value {
  font-size: 24px;
  font-weight: 800;
  color: #10b981;
  line-height: 1;
  margin-top: 2px;
}

.offer-badge {
  background: #dcfce7;
  color: #15803d;
  border-radius: 8px;
  padding: 5px 10px;
  text-align: center;
}

.offer-badge-top {
  font-size: 9px;
  font-weight: 600;
}

.offer-badge-val {
  font-size: 16px;
  font-weight: 800;
  line-height: 1;
  margin-top: 2px;
}

.percentile-card,
.trend-card,
.heat-card,
.skills-card,
.hc-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.percentile-bar {
  position: relative;
  height: 50px;
  background: linear-gradient(90deg, #dbeafe, #a5b4fc, #6366f1);
  border-radius: 6px;
  margin-top: 18px;
  margin-bottom: 24px;
}

.p50-line {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #4f46e5;
}

.marker {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
}

.you-marker {
  left: 60%;
}

.offer-marker {
  left: 78%;
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translate(-50%, -50%);
}

.marker-dot {
  display: block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #fff;
}

.you-dot {
  background: #4f46e5;
}

.offer-dot {
  background: #10b981;
}

.marker-tip {
  position: absolute;
  color: #fff;
  font-size: 9px;
  padding: 2px 5px;
  border-radius: 4px;
  white-space: nowrap;
  left: 50%;
  transform: translateX(-50%);
}

.top-tip {
  top: -18px;
  background: #4f46e5;
}

.bottom-tip {
  bottom: -18px;
  background: #10b981;
}

.percentile-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #64748b;
  text-align: center;
}

.p50-text {
  color: #4338ca;
}

.trend-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.trend-up {
  color: #10b981;
  font-weight: 700;
  font-size: 10px;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 60px;
}

.bar-col {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: flex-end;
}

.bar {
  width: 100%;
  border-radius: 3px 3px 0 0;
}

.bar.light {
  background: #a5b4fc;
}

.bar.mid {
  background: #818cf8;
}

.bar.deep {
  background: #6366f1;
}

.bar.primary {
  background: #4f46e5;
}

.bar-months {
  display: flex;
  justify-content: space-between;
  font-size: 9px;
  color: #64748b;
  margin-top: 4px;
}

.month-active {
  color: #4f46e5;
  font-weight: 700;
}

.advice-card {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border: 1px solid #fbbf24;
  border-radius: 12px;
  padding: 14px;
}

.advice-title {
  font-size: 12px;
  font-weight: 700;
  color: #78350f;
  margin-bottom: 6px;
}

.advice-text {
  margin: 0;
  font-size: 11px;
  color: #78350f;
  line-height: 1.7;
}

.salary-cta {
  width: 100%;
  background: #b45309;
  color: #fff;
  border: 0;
  border-radius: 12px;
  padding: 14px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  font-family: inherit;
  margin-bottom: 12px;
}

.heat-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.heat-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.heat-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.yoy-badge {
  background: #dcfce7;
  color: #15803d;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 8px;
  flex-shrink: 0;
}

.chart-wrap {
  width: 100%;
  height: 100px;
}

.line-chart {
  width: 100%;
  height: 100%;
  display: block;
}

.heat-months {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #64748b;
  margin-top: 4px;
  text-align: center;
}

.heat-months b {
  color: #334155;
}

.heat-months .month-active {
  color: #4f46e5;
}

.heat-months .month-active b {
  color: #4f46e5;
}

.heat-ai {
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border-radius: 8px;
  padding: 12px;
  margin-top: 12px;
  font-size: 11px;
  color: #3730a3;
  line-height: 1.6;
}

.highlight-green {
  color: #10b981;
}

.skills-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}

.skills-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.skills-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.skills-select {
  font-size: 11px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 4px 8px;
  background: #fff;
  font-family: inherit;
  flex-shrink: 0;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skill-row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 3px;
}

.skill-left {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}

.rank-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
}

.rank-warn {
  background: #fef3c7;
  color: #92400e;
}

.rank-gray {
  background: #f1f5f9;
  color: #475569;
}

.skill-name {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.own-chip {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 500;
}

.chip-warning {
  background: #fef3c7;
  color: #92400e;
}

.chip-success {
  background: #d1fae5;
  color: #047857;
}

.skill-pct {
  color: #10b981;
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
}

.skill-bar-track {
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
}

.skill-bar-fill {
  height: 100%;
  border-radius: 4px;
}

.bar-green {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.bar-purple {
  background: linear-gradient(90deg, #4f46e5, #6366f1);
}

.invest-tip {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border-radius: 8px;
  padding: 12px;
  margin-top: 14px;
  font-size: 11px;
  color: #78350f;
  line-height: 1.6;
}

.flink-link {
  color: #4f46e5;
}

.hc-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.hc-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.hc-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.hc-chip {
  border: 0;
  background: #f1f5f9;
  color: #334155;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-family: inherit;
  cursor: pointer;
}

.hc-chip.active {
  background: #eef2ff;
  color: #4f46e5;
}

.hc-chip.chip-success {
  background: #d1fae5;
  color: #047857;
}

.hc-chip.chip-success.active {
  background: #d1fae5;
  color: #047857;
  box-shadow: inset 0 0 0 1px #10b981;
}

.hc-chip.chip-danger {
  background: #fee2e2;
  color: #b91c1c;
}

.hc-chip.chip-danger.active {
  background: #fee2e2;
  color: #b91c1c;
  box-shadow: inset 0 0 0 1px #ef4444;
}

.company-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 12px;
}

.company-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
}

.company-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 8px;
}

.company-info {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.company-logo {
  width: 32px;
  height: 32px;
  color: #fff;
  border-radius: 7px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 12px;
  flex-shrink: 0;
}

.company-name {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.company-dept {
  font-size: 10px;
  color: #64748b;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.badge-expand {
  background: #dcfce7;
  color: #15803d;
}

.badge-watch {
  background: #fef3c7;
  color: #92400e;
}

.badge-shrink {
  background: #fee2e2;
  color: #b91c1c;
}

.company-body {
  font-size: 11px;
  color: #334155;
  line-height: 1.7;
}

.company-foot {
  border-radius: 6px;
  padding: 8px;
  margin-top: 8px;
  font-size: 10px;
}

.foot-green {
  background: #f0fdf4;
  color: #15803d;
}

.foot-yellow {
  background: #fef3c7;
  color: #92400e;
}

.foot-red {
  background: #fee2e2;
  color: #b91c1c;
}

.add-card {
  border: 1px dashed #e2e8f0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  min-height: 110px;
  text-align: center;
}

.add-icon {
  width: 24px;
  height: 24px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  margin-bottom: 6px;
}

.add-title {
  font-size: 11px;
}

.add-sub {
  font-size: 10px;
  color: #cbd5e1;
  margin-top: 2px;
}

.suggest-card {
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border: 1px solid #c7d2fe;
  grid-column: 1 / -1;
}

.suggest-title {
  font-size: 12px;
  font-weight: 700;
  color: #4338ca;
  margin-bottom: 6px;
}

.suggest-list {
  font-size: 11px;
  color: #3730a3;
  line-height: 1.7;
}

.suggest-btn {
  width: 100%;
  background: #4f46e5;
  color: #fff;
  border: 0;
  border-radius: 8px;
  padding: 8px;
  font-size: 11px;
  font-weight: 600;
  margin-top: 8px;
  cursor: pointer;
  font-family: inherit;
}

@media (max-width: 360px) {
  .company-grid {
    grid-template-columns: 1fr;
  }
}

.skeleton-line {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 4px;
  display: block;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.advice-disclaimer {
  font-size: 10px;
  color: #94a3b8;
  margin: 6px 0 0;
}
.company-search-card {
  margin-bottom: 12px;
}
.company-search-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.company-search-input {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
}
.company-search-btn {
  background: #4f46e5;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.company-search-btn:disabled { opacity: .5; cursor: not-allowed; }
.company-result { margin-top: 12px; }
.company-result-name { font-size: 14px; font-weight: 700; color: #0f172a; margin-bottom: 4px; }
.company-result-meta { font-size: 11px; color: #64748b; margin-bottom: 8px; }
.company-tech-chips { display: flex; flex-wrap: wrap; gap: 5px; margin-bottom: 8px; }
.tech-chip {
  background: #eef2ff;
  color: #4338ca;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
}
.company-jds { font-size: 11px; color: #334155; margin-bottom: 6px; }
.company-summary { font-size: 12px; color: #475569; line-height: 1.6; margin: 0; }
</style>
