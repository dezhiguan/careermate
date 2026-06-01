<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">🎯 岗位匹配</h2>
      <p class="page-sub">Agent 已为你匹配 {{ filteredJobs.length }} 个岗位</p>
    </div>

    <!-- Filter Chips -->
    <div class="filter-row">
      <span
        v-for="f in filters"
        :key="f.key"
        class="filter-chip"
        :class="{ active: activeFilter === f.key }"
        @click="activeFilter = f.key"
      >{{ f.label }}</span>
    </div>

    <!-- Match Cards -->
    <div class="job-grid">
      <div
        v-for="job in filteredJobs"
        :key="job.id"
        class="job-card"
        :class="{ featured: job.match >= 75 }"
        @click="selectJob(job)"
      >
        <div class="job-header">
          <span class="job-company">{{ job.company }}</span>
          <span class="job-badge" :class="matchClass(job.match)">匹配 {{ job.match }}%</span>
        </div>
        <div class="job-info">{{ job.title }} · {{ job.city }} · {{ job.salary }}</div>
        <div v-for="(pt, idx) in job.points" :key="idx" class="job-point" :class="pt.type">
          <span class="pt-icon">{{ pt.type === 'match' ? '✅' : pt.type === 'gap' ? '⚠️' : '💡' }}</span>
          {{ pt.text }}
        </div>
      </div>
    </div>

    <!-- Job Detail Modal -->
    <div v-if="selectedJob" class="modal-overlay" @click.self="selectedJob = null">
      <div class="modal-card">
        <div class="modal-header">
          <div>
            <div class="modal-company">{{ selectedJob.company }}</div>
            <div class="modal-title">{{ selectedJob.title }}</div>
          </div>
          <span class="job-badge large" :class="matchClass(selectedJob.match)">匹配 {{ selectedJob.match }}%</span>
        </div>
        <div class="modal-body">
          <div class="modal-section">
            <div class="modal-label">📍 地点 & 薪资</div>
            <div>{{ selectedJob.city }} · {{ selectedJob.salary }}</div>
          </div>
          <div class="modal-section">
            <div class="modal-label">✅ 匹配技能</div>
            <div class="skill-tags">
              <span v-for="s in selectedJob.matchedSkills" :key="s" class="skill-tag match">{{ s }}</span>
            </div>
          </div>
          <div class="modal-section">
            <div class="modal-label">⚠️ 技能差距</div>
            <div class="skill-tags">
              <span v-for="s in selectedJob.gapSkills" :key="s" class="skill-tag gap">{{ s }}</span>
            </div>
          </div>
          <router-link to="/" class="modal-action">💬 回对话台查看深度分析 →</router-link>
        </div>
        <button class="modal-close" @click="selectedJob = null">✕</button>
      </div>
    </div>

    <!-- Agent Stats -->
    <div class="agent-stats">
      🤖 Agent 已调用 RAGForge.search × 1 · 匹配计算 × {{ jobs.length }} · 耗时 {{ (jobs.length * 0.38).toFixed(1) }}s
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const activeFilter = ref('all')
const selectedJob = ref(null)

const filters = [
  { key: 'all', label: '全部(6)' },
  { key: 'high', label: '匹配度 80%+' },
  { key: 'backend', label: '后端开发' },
  { key: 'big', label: '大厂' },
  { key: 'ai', label: 'AI 相关' },
]

const jobs = ref([
  {
    id: 1, company: '字节跳动', title: '后端开发工程师', city: '北京', salary: '25-50K',
    match: 78,
    matchedSkills: ['Java', 'Spring Boot', 'MySQL', 'Redis', '微服务'],
    gapSkills: ['分布式系统', '消息队列'],
    points: [
      { text: 'Java/Spring Boot 完全匹配', type: 'match' },
      { text: '缺分布式系统经验', type: 'gap' },
      { text: '大模型开发经验（你有基础）', type: 'tip' },
    ],
  },
  {
    id: 2, company: '美团', title: '后端工程师', city: '上海', salary: '25-40K',
    match: 74,
    matchedSkills: ['Java', 'MySQL', 'Spring'],
    gapSkills: ['消息队列', '高并发'],
    points: [
      { text: 'Java/MySQL 匹配', type: 'match' },
      { text: '缺消息队列经验', type: 'gap' },
    ],
  },
  {
    id: 3, company: '腾讯', title: '后台开发', city: '深圳', salary: '25-45K',
    match: 68,
    matchedSkills: ['微服务', 'Java'],
    gapSkills: ['C++', 'Go', '分布式'],
    points: [
      { text: '微服务经验匹配', type: 'match' },
      { text: '缺C++/Go经验', type: 'gap' },
    ],
  },
  {
    id: 4, company: '小红书', title: '基础架构开发', city: '上海', salary: '30-50K',
    match: 65,
    matchedSkills: ['Java', '基础组件'],
    gapSkills: ['中间件开发', '容器化'],
    points: [
      { text: '基础组件经验匹配', type: 'match' },
      { text: '缺中间件开发经验', type: 'gap' },
    ],
  },
  {
    id: 5, company: '百度', title: '后端研发工程师', city: '北京', salary: '25-45K',
    match: 82,
    matchedSkills: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
    gapSkills: ['AI平台经验'],
    points: [
      { text: '技术栈高度匹配', type: 'match' },
      { text: 'AI平台经验可快速补齐', type: 'tip' },
    ],
  },
  {
    id: 6, company: '阿里巴巴', title: 'Java开发工程师', city: '杭州', salary: '28-50K',
    match: 71,
    matchedSkills: ['Java', 'Spring', '微服务'],
    gapSkills: ['中间件', '高可用架构'],
    points: [
      { text: 'Java生态完全匹配', type: 'match' },
      { text: '缺高可用架构经验', type: 'gap' },
    ],
  },
])

const filteredJobs = computed(() => {
  if (activeFilter.value === 'all') return jobs.value
  if (activeFilter.value === 'high') return jobs.value.filter(j => j.match >= 80)
  if (activeFilter.value === 'backend') return jobs.value.filter(j => j.title.includes('后端') || j.title.includes('后台') || j.title.includes('Java'))
  if (activeFilter.value === 'big') return jobs.value
  if (activeFilter.value === 'ai') return jobs.value.filter(j => j.gapSkills.some(s => s.includes('AI')))
  return jobs.value
})

function matchClass(match) {
  if (match >= 75) return 'high'
  if (match >= 65) return 'mid'
  return 'low'
}

function selectJob(job) {
  selectedJob.value = job
}
</script>

<style scoped>
.page-container { max-width: 960px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

/* Filters */
.filter-row { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.filter-chip {
  font-size: 11px; padding: 5px 12px; border-radius: 14px; cursor: pointer;
  background: var(--light); border: 1px solid var(--border); transition: all .2s; user-select: none;
}
.filter-chip.active { background: var(--purple); color: #fff; border-color: var(--purple); }
.filter-chip:hover:not(.active) { border-color: var(--purple); color: var(--purple); }

/* Job Grid */
.job-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 16px; }
.job-card {
  background: #fff; border: 2px solid var(--border); border-radius: 12px;
  padding: 14px; cursor: pointer; transition: all .2s;
}
.job-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,.06); }
.job-card.featured { border-color: var(--green); }

.job-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.job-company { font-weight: 600; font-size: 13px; }
.job-badge {
  display: inline-block; padding: 2px 8px; border-radius: 8px; font-size: 10px; font-weight: 600;
}
.job-badge.high { background: #d1fae5; color: #065f46; }
.job-badge.mid { background: #fef3c7; color: #92400e; }
.job-badge.low { background: #fef2f2; color: #991b1b; }
.job-badge.large { font-size: 12px; padding: 4px 12px; }

.job-info { font-size: 11px; color: var(--text-muted); margin-bottom: 6px; }

.job-point { font-size: 10px; padding: 2px 0; }
.job-point.match { color: var(--green); }
.job-point.gap { color: var(--amber); }
.job-point.tip { color: var(--green); }
.pt-icon { margin-right: 2px; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(15,23,42,.6); z-index: 200;
  display: flex; align-items: center; justify-content: center; padding: 20px;
}
.modal-card {
  background: #fff; border-radius: 14px; max-width: 480px; width: 100%; position: relative; overflow: hidden;
}
.modal-header { padding: 18px; background: var(--navy); color: #fff; display: flex; justify-content: space-between; align-items: flex-start; }
.modal-company { font-weight: 700; font-size: 16px; }
.modal-title { font-size: 12px; opacity: .7; }
.modal-body { padding: 18px; }
.modal-section { margin-bottom: 14px; }
.modal-label { font-weight: 600; font-size: 11px; margin-bottom: 4px; }
.skill-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.skill-tag { padding: 3px 8px; border-radius: 6px; font-size: 10px; }
.skill-tag.match { background: #d1fae5; color: #065f46; }
.skill-tag.gap { background: #fef3c7; color: #92400e; }
.modal-action {
  display: block; text-align: center; padding: 8px; margin-top: 8px;
  border: 1px solid var(--purple); border-radius: 8px; color: var(--purple);
  font-size: 11px; text-decoration: none;
}
.modal-action:hover { background: #f5f3ff; }
.modal-close {
  position: absolute; top: 12px; right: 12px; background: rgba(255,255,255,.2);
  border: none; color: #fff; width: 24px; height: 24px; border-radius: 50%;
  font-size: 12px; cursor: pointer; display: flex; align-items: center; justify-content: center;
}

/* Agent Stats */
.agent-stats { text-align: center; font-size: 11px; color: var(--text-muted); margin-top: 8px; }
</style>
