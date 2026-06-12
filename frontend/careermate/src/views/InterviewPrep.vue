<template>
  <div class="interview-page">
    <div v-if="noDefaultResumeHint" class="banner warn">
      请先到简历页创建并设置默认简历。
      <router-link to="/mine" class="banner-link">前往我的 →</router-link>
    </div>
    <div v-if="pageError" class="banner error">{{ pageError }}</div>

    <header class="page-header">
      <h1 class="page-title">面试准备中心</h1>
      <p class="page-sub">考点速查 · 公司面经 · 模拟练习</p>
    </header>

    <div class="page-content">

      <!-- 区块一：考点速查 -->
      <section class="card kb-section">
        <div class="section-header">
          <h2 class="section-title">考点速查</h2>
          <p class="section-desc">输入技术关键词，AI 从面试知识库提炼高频考题</p>
        </div>
        <div class="search-row">
          <input
            v-model="kbQuery"
            class="search-input"
            type="search"
            placeholder="如：Spring Boot / Redis / JVM"
            @keyup.enter="searchKb"
          >
          <button type="button" class="search-btn" :disabled="kbLoading || !kbQuery.trim()" @click="searchKb">
            {{ kbLoading ? '搜索中...' : '搜索' }}
          </button>
        </div>
        <div class="quick-tags">
          <button
            v-for="tag in kbQuickTags"
            :key="tag"
            type="button"
            class="quick-tag"
            @click="kbQuery = tag; searchKb()"
          >
            {{ tag }}
          </button>
        </div>
        <div v-if="kbLoading" class="skeleton-group">
          <div v-for="i in 3" :key="i" class="skeleton" style="height:52px;margin-bottom:8px" />
        </div>
        <template v-else-if="kbData">
          <p v-if="kbData.aiSummary" class="ai-summary">{{ kbData.aiSummary }}</p>
          <div v-if="kbData.questions?.length">
            <div v-for="(item, i) in kbData.questions" :key="i" class="question-card">
              <div class="question-header" @click="toggleAnswer(i)">
                <span class="q-index">Q{{ i + 1 }}</span>
                <span class="q-category-tag" :class="categoryClass(item.category)">{{ item.category }}</span>
                <span class="q-text">{{ item.question }}</span>
                <span class="expand-arrow">{{ expandedAnswers.has(i) ? '▲' : '▼' }}</span>
              </div>
              <div v-if="expandedAnswers.has(i)" class="answer-body">{{ item.answer }}</div>
            </div>
          </div>
          <div v-else class="empty-tip">暂无题目，换个关键词试试</div>
        </template>
      </section>

      <!-- 区块二：公司面经 -->
      <section class="card company-section">
        <div class="section-header">
          <h2 class="section-title">公司面经</h2>
          <p class="section-desc">输入目标公司名，AI 整理面试风格和高频考题</p>
        </div>
        <div class="search-row">
          <input
            v-model="companyQuery"
            class="search-input"
            type="search"
            placeholder="如：字节跳动 / 阿里巴巴 / 腾讯"
            @keyup.enter="searchCompany"
          >
          <button type="button" class="search-btn" :disabled="companyLoading || !companyQuery.trim()" @click="searchCompany">
            {{ companyLoading ? '查询中...' : '查询' }}
          </button>
        </div>
        <div v-if="companyLoading" class="skeleton-group">
          <div class="skeleton" style="height:16px;width:40%;margin-bottom:8px" />
          <div class="skeleton" style="height:12px;width:90%;margin-bottom:6px" />
          <div class="skeleton" style="height:12px;width:70%" />
        </div>
        <div v-else-if="companyData" class="company-prep-card">
          <div class="card-title-row">
            <h3>{{ companyData.companyName }}</h3>
          </div>
          <p v-if="companyData.interviewStyle" class="style-text">{{ companyData.interviewStyle }}</p>
          <div v-if="companyData.techFocus?.length" class="tech-tags">
            <span v-for="t in companyData.techFocus" :key="t" class="tech-tag">{{ t }}</span>
          </div>
          <div v-if="companyData.commonQuestions?.length" class="common-questions">
            <div class="sub-title">高频面试题</div>
            <ul>
              <li v-for="(q, i) in companyData.commonQuestions" :key="i">{{ q }}</li>
            </ul>
          </div>
          <div v-if="companyData.behaviorTips" class="behavior-tips">
            <div class="sub-title">行为面 Tips</div>
            <p>{{ companyData.behaviorTips }}</p>
          </div>
          <p v-if="companyData.aiSummary" class="ai-summary">{{ companyData.aiSummary }}</p>
        </div>
      </section>

      <!-- 区块三：模拟练习 -->
      <section class="card practice-section">
        <div class="section-header">
          <h2 class="section-title">模拟练习</h2>
          <p class="section-desc">AI 根据你的简历生成专属面试题，实时评分反馈</p>
        </div>
        <div class="start-card">
          <div class="start-card-info">
            <div class="start-title">开始新的模拟训练</div>
            <div class="start-desc">约 5 题 · AI 评分 · 随时退出</div>
          </div>
          <button type="button" class="btn-start" :disabled="creating" @click="startNewSession">
            {{ creating ? '生成中...' : '开始 →' }}
          </button>
        </div>
        <div v-if="listLoading" class="list-hint">加载记录...</div>
        <div v-else-if="sessions.length" class="session-list">
          <article
            v-for="item in sessions"
            :key="item.id"
            class="session-item"
            @click="goToSession(item.id)"
          >
            <span v-if="item.averageScore != null" class="session-score">{{ Math.round(item.averageScore) }}分</span>
            <span class="session-title">{{ item.title }}</span>
            <span class="session-status">{{ item.status === 'COMPLETED' ? '已完成' : '进行中' }}</span>
            <span class="session-progress">{{ item.answeredQuestions }}/{{ item.totalQuestions }}题</span>
          </article>
        </div>
        <div v-else class="empty-hint">暂无练习记录</div>
      </section>

    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  createInterviewSession,
  getCompanyPrep,
  getKbQuestions,
  listInterviewSessions,
} from '../api/interview'

const kbQuery = ref('')
const companyQuery = ref('')
const kbLoading = ref(false)
const companyLoading = ref(false)
const kbData = ref(null)
const companyData = ref(null)
const expandedAnswers = ref(new Set())

const sessions = ref([])
const listLoading = ref(false)
const creating = ref(false)
const pageError = ref('')
const noDefaultResumeHint = ref(false)

const kbQuickTags = ['Spring Boot', 'Redis', 'MySQL', 'JVM', '系统设计', '行为面试']

function toggleAnswer(index) {
  const next = new Set(expandedAnswers.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedAnswers.value = next
}

function categoryClass(category) {
  if (category === '技术') return 'cat-tech'
  if (category === '行为') return 'cat-behavior'
  if (category === 'HR') return 'cat-hr'
  return 'cat-default'
}

async function searchKb() {
  const q = kbQuery.value.trim()
  if (!q) return
  kbLoading.value = true
  pageError.value = ''
  expandedAnswers.value = new Set()
  try {
    kbData.value = await getKbQuestions(q)
  } catch (e) {
    kbData.value = { query: q, questions: [], aiSummary: e?.message || '搜索失败，请稍后重试' }
  } finally {
    kbLoading.value = false
  }
}

async function searchCompany() {
  const q = companyQuery.value.trim()
  if (!q) return
  companyLoading.value = true
  pageError.value = ''
  try {
    companyData.value = await getCompanyPrep(q)
  } catch (e) {
    companyData.value = {
      companyName: q,
      interviewStyle: '',
      techFocus: [],
      commonQuestions: [],
      behaviorTips: '',
      aiSummary: e?.message || '查询失败，请稍后重试',
    }
  } finally {
    companyLoading.value = false
  }
}

function formatDefaultTitle() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `面试训练 ${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadSessions() {
  listLoading.value = true
  pageError.value = ''
  try {
    sessions.value = await listInterviewSessions()
  } catch (e) {
    pageError.value = e?.message || '加载训练记录失败'
  } finally {
    listLoading.value = false
  }
}

async function startNewSession() {
  creating.value = true
  pageError.value = ''
  noDefaultResumeHint.value = false
  try {
    await createInterviewSession({ title: formatDefaultTitle() })
    await loadSessions()
    window.alert('练习会话已创建，答题功能即将上线，请稍后再试。')
  } catch (e) {
    const msg = e?.message || '创建训练失败'
    if (msg.includes('默认简历')) {
      noDefaultResumeHint.value = true
    }
    pageError.value = msg
  } finally {
    creating.value = false
  }
}

function goToSession() {
  window.alert('答题功能即将上线，敬请期待。')
}

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.interview-page {
  min-height: 100%;
  background: #f8fafc;
  padding-bottom: 80px;
  color: #0f172a;
}

.banner {
  margin: 12px 16px 0;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}

.banner.error { background: #fef2f2; color: #991b1b; }
.banner.warn { background: #fffbeb; color: #92400e; }

.banner-link {
  margin-left: 8px;
  color: #4f46e5;
  font-weight: 600;
}

.page-header {
  padding: 14px 16px 10px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}

.page-title {
  margin: 0 0 2px;
  font-size: 16px;
  font-weight: 700;
}

.page-sub {
  margin: 0;
  font-size: 11px;
  color: #94a3b8;
}

.page-content {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
}

.section-header { margin-bottom: 12px; }

.section-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
}

.section-desc {
  margin: 0;
  font-size: 11px;
  color: #64748b;
}

.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.search-input {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 13px;
  outline: none;
  font-family: inherit;
}

.search-btn {
  background: #4f46e5;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 9px 16px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  font-family: inherit;
}

.search-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.quick-tag {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 11px;
  cursor: pointer;
  font-family: inherit;
}

.quick-tag:hover { border-color: #6366f1; color: #4f46e5; }

.ai-summary {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border-radius: 8px;
  padding: 10px 12px;
  margin: 0 0 12px;
}

.question-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-bottom: 8px;
  overflow: hidden;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  background: #fafafa;
}

.q-index {
  font-size: 11px;
  font-weight: 700;
  color: #4f46e5;
  flex-shrink: 0;
}

.q-category-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
  flex-shrink: 0;
}

.cat-tech { background: #dbeafe; color: #1e40af; }
.cat-behavior { background: #f1f5f9; color: #475569; }
.cat-hr { background: #fef3c7; color: #92400e; }
.cat-default { background: #f1f5f9; color: #64748b; }

.q-text {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  min-width: 0;
}

.expand-arrow {
  font-size: 10px;
  color: #94a3b8;
  flex-shrink: 0;
}

.answer-body {
  padding: 10px 12px 12px;
  font-size: 12px;
  color: #475569;
  line-height: 1.7;
  border-top: 1px solid #f1f5f9;
  background: #fff;
}

.company-prep-card { margin-top: 4px; }

.card-title-row h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
}

.style-text {
  font-size: 12px;
  color: #64748b;
  margin: 0 0 10px;
  line-height: 1.6;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 12px;
}

.tech-tag {
  background: #eef2ff;
  color: #4338ca;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 11px;
}

.sub-title {
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}

.common-questions ul {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 12px;
  color: #334155;
  line-height: 1.7;
}

.behavior-tips p {
  margin: 0 0 12px;
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
}

.start-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 12px;
  padding: 16px;
  color: #fff;
  margin-bottom: 12px;
}

.start-title { font-size: 14px; font-weight: 700; }
.start-desc { font-size: 11px; opacity: 0.85; margin-top: 4px; }

.btn-start {
  background: #fff;
  color: #4f46e5;
  border: none;
  border-radius: 8px;
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  font-family: inherit;
}

.btn-start:disabled { opacity: 0.6; cursor: not-allowed; }

.session-list { display: flex; flex-direction: column; gap: 8px; }

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  background: #fafafa;
}

.session-item:hover { border-color: #6366f1; background: #f8fafc; }

.session-score {
  background: #dcfce7;
  color: #15803d;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.session-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-status,
.session-progress {
  font-size: 11px;
  color: #64748b;
  flex-shrink: 0;
}

.skeleton-group { display: flex; flex-direction: column; }

.skeleton {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: 6px;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-tip,
.empty-hint,
.list-hint {
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  padding: 16px 0;
}
</style>
