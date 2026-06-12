<template>
  <!-- 三区块首页 -->
  <div v-if="!activeSession" class="interview-page">
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
            <div v-for="(item, i) in kbData.questions" :key="i" class="kb-question-card">
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
          <p class="section-desc">AI 根据你的简历 + 目标 JD 出 5 道专属题，答完即评分</p>
        </div>
        <div class="start-card">
          <div class="start-info">
            <div class="start-title">开始新的模拟面试</div>
            <div class="start-desc">项目经历 · 技术深度 · 技能缺口 · 系统设计 · 行为面试</div>
          </div>
          <button type="button" class="btn-start" :disabled="creating" @click="startNewSession">
            {{ creating ? '生成题目中...' : '开始 →' }}
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
            <span v-if="item.averageScore != null" class="session-score">
              {{ Math.round(item.averageScore) }}分
            </span>
            <div class="session-main">
              <span class="session-title">{{ item.title }}</span>
              <span class="session-progress">{{ item.answeredQuestions }}/{{ item.totalQuestions }} 题</span>
            </div>
            <span class="session-status" :class="item.status === 'COMPLETED' ? 'status-done' : 'status-active'">
              {{ item.status === 'COMPLETED' ? '已完成' : '继续 →' }}
            </span>
          </article>
        </div>
        <div v-else class="empty-hint">暂无练习记录，点击「开始 →」生成第一套题</div>
      </section>

    </div>
  </div>

  <!-- 答题视图 -->
  <div v-else class="practice-view">
    <header class="practice-header">
      <button type="button" class="btn-exit" @click="exitPractice">← 退出</button>
      <div class="progress-bar-wrap">
        <div class="progress-bar-fill" :style="{ width: (answeredCount / 5 * 100) + '%' }" />
      </div>
      <span class="progress-text">{{ answeredCount }}/5</span>
    </header>

    <div v-if="currentQuestion" class="question-body">
      <div class="question-card">
        <div class="question-meta">
          <span class="type-badge">{{ typeLabel(currentQuestion.questionType) }}</span>
          <span class="question-no">第 {{ currentQuestion.questionNo }} 题 / 共 5 题</span>
        </div>
        <p class="question-text">{{ currentQuestion.questionText }}</p>

        <div v-if="currentQuestion.status !== 'ANSWERED' && currentQuestion.referencePoints?.length">
          <button type="button" class="hint-toggle" @click="showHints = !showHints">
            {{ showHints ? '收起提示 ▲' : '查看考点提示 ▼' }}
          </button>
          <ul v-if="showHints" class="hint-list">
            <li v-for="(p, i) in currentQuestion.referencePoints" :key="i">{{ p }}</li>
          </ul>
        </div>
      </div>

      <template v-if="currentQuestion.status !== 'ANSWERED'">
        <div class="answer-card">
          <div class="answer-header">
            <span class="answer-label">你的回答</span>
            <span class="word-count">{{ answerText.length }} 字</span>
          </div>
          <textarea
            v-model="answerText"
            class="answer-textarea"
            placeholder="输入你的回答，尽量展开说..."
            maxlength="2000"
          />
          <div class="answer-actions">
            <button
              type="button"
              class="btn-primary"
              :disabled="!answerText.trim() || submitting"
              @click="submitAnswer"
            >
              {{ submitting ? '评分中...' : '提交 · 看评分 →' }}
            </button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="feedback-card">
          <div class="score-row">
            <div class="score-circle">{{ currentQuestion.score ?? '—' }}</div>
            <div class="score-info">
              <div class="score-title">本题得分</div>
              <p v-if="currentQuestion.feedback" class="score-feedback">{{ currentQuestion.feedback }}</p>
            </div>
          </div>
          <div class="feedback-cols">
            <div v-if="currentQuestion.strengths?.length" class="fb-good">
              <div class="fb-title">✓ 答得好</div>
              <ul><li v-for="(s, i) in currentQuestion.strengths" :key="i">{{ s }}</li></ul>
            </div>
            <div v-if="currentQuestion.improvements?.length" class="fb-improve">
              <div class="fb-title">⚠ 可补充</div>
              <ul><li v-for="(s, i) in currentQuestion.improvements" :key="i">{{ s }}</li></ul>
            </div>
          </div>
          <div v-if="currentQuestion.referencePoints?.length" class="reference-block">
            <div class="ref-title">📖 参考要点</div>
            <ul><li v-for="(p, i) in currentQuestion.referencePoints" :key="i">{{ p }}</li></ul>
          </div>
          <div class="next-actions">
            <button
              v-if="!allAnswered"
              type="button"
              class="btn-primary"
              @click="nextQuestion"
            >
              下一题 →
            </button>
            <button
              v-else
              type="button"
              class="btn-primary"
              @click="completeSession"
            >
              完成 · 查看总结 ✓
            </button>
          </div>
        </div>
      </template>

      <div class="question-nav">
        <button
          v-for="(q, i) in activeSession.questions"
          :key="q.id"
          type="button"
          class="nav-dot"
          :class="{
            'dot-current': i === currentQuestionIndex,
            'dot-done': q.status === 'ANSWERED',
            'dot-pending': q.status !== 'ANSWERED' && i !== currentQuestionIndex
          }"
          @click="goToQuestion(i)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  completeInterviewSession,
  createInterviewSession,
  getCompanyPrep,
  getInterviewSession,
  getKbQuestions,
  listInterviewSessions,
  submitInterviewAnswer,
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

const activeSession = ref(null)
const currentQuestionIndex = ref(0)
const answerText = ref('')
const submitting = ref(false)
const showHints = ref(false)

const kbQuickTags = ['Spring Boot', 'Redis', 'MySQL', 'JVM', '系统设计', '行为面试']

const TYPE_LABEL = {
  PROJECT: '项目经历',
  SKILL: '技术深度',
  GAP: '技能缺口',
  SYSTEM_DESIGN: '系统设计',
  BEHAVIOR: '行为面试',
}

function typeLabel(type) {
  return TYPE_LABEL[type] ?? type
}

const currentQuestion = computed(() => {
  if (!activeSession.value) return null
  return activeSession.value.questions?.[currentQuestionIndex.value] ?? null
})

const allAnswered = computed(() => {
  if (!activeSession.value?.questions) return false
  return activeSession.value.questions.every((q) => q.status === 'ANSWERED')
})

const answeredCount = computed(() => {
  return activeSession.value?.questions?.filter((q) => q.status === 'ANSWERED').length ?? 0
})

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
    const session = await createInterviewSession({})
    activeSession.value = session
    currentQuestionIndex.value = 0
    answerText.value = ''
    showHints.value = false
  } catch (e) {
    console.error('创建 session 失败', e)
    const msg = e?.message || '创建训练失败'
    if (msg.includes('默认简历')) {
      noDefaultResumeHint.value = true
    }
    pageError.value = msg
  } finally {
    creating.value = false
  }
}

async function goToSession(sessionId) {
  try {
    const session = await getInterviewSession(sessionId)
    activeSession.value = session
    const firstUnanswered = session.questions?.findIndex((q) => q.status !== 'ANSWERED') ?? 0
    currentQuestionIndex.value = firstUnanswered >= 0 ? firstUnanswered : 0
    answerText.value = ''
    showHints.value = false
  } catch (e) {
    console.error('加载 session 失败', e)
    pageError.value = e?.message || '加载练习失败'
  }
}

function exitPractice() {
  activeSession.value = null
  currentQuestionIndex.value = 0
  answerText.value = ''
  showHints.value = false
  loadSessions()
}

async function submitAnswer() {
  if (!answerText.value.trim() || submitting.value) return
  const session = activeSession.value
  const question = currentQuestion.value
  if (!session || !question) return
  submitting.value = true
  try {
    const updated = await submitInterviewAnswer(session.id, question.id, {
      answerText: answerText.value.trim(),
    })
    const idx = activeSession.value.questions.findIndex((q) => q.id === updated.id)
    if (idx >= 0) {
      activeSession.value.questions[idx] = updated
    }
    showHints.value = false
  } catch (e) {
    console.error('提交答案失败', e)
    pageError.value = e?.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function nextQuestion() {
  if (currentQuestionIndex.value < (activeSession.value?.questions?.length ?? 0) - 1) {
    currentQuestionIndex.value++
    answerText.value = ''
    showHints.value = false
  }
}

function goToQuestion(i) {
  currentQuestionIndex.value = i
  const q = activeSession.value?.questions?.[i]
  answerText.value = q?.status === 'ANSWERED' ? '' : (q?.answerText || '')
  showHints.value = false
}

async function completeSession() {
  if (!activeSession.value) return
  try {
    await completeInterviewSession(activeSession.value.id)
    exitPractice()
  } catch (e) {
    console.error('complete 失败', e)
    pageError.value = e?.message || '结束练习失败'
  }
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

.kb-question-card {
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

.session-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-progress {
  font-size: 11px;
  color: #64748b;
}

.session-status {
  font-size: 11px;
  flex-shrink: 0;
}

.status-done { color: #64748b; }
.status-active { color: #7c3aed; font-weight: 600; }

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

/* ── 答题视图 ── */
.practice-view {
  min-height: 100%;
  background: #f8fafc;
  padding-bottom: 80px;
  color: #1a1a2e;
}

.practice-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}

.btn-exit {
  background: none;
  border: none;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  padding: 4px 0;
  font-family: inherit;
  flex-shrink: 0;
}

.progress-bar-wrap {
  flex: 1;
  height: 4px;
  background: #eee;
  border-radius: 2px;
  margin: 0 12px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: #7c3aed;
  border-radius: 2px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 12px;
  color: #64748b;
  flex-shrink: 0;
}

.question-body {
  padding-bottom: 16px;
}

.question-card {
  background: #fff;
  padding: 20px;
  margin: 16px;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-badge {
  border-radius: 999px;
  background: #f3e8ff;
  color: #7c3aed;
  font-size: 12px;
  padding: 2px 8px;
  font-weight: 600;
}

.question-no {
  font-size: 12px;
  color: #94a3b8;
}

.question-text {
  font-size: 16px;
  line-height: 1.6;
  margin: 12px 0 0;
  color: #1a1a2e;
}

.hint-toggle {
  background: none;
  border: none;
  color: #7c3aed;
  font-size: 13px;
  cursor: pointer;
  padding: 8px 0 4px;
  font-family: inherit;
}

.hint-list {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.7;
}

.answer-card,
.feedback-card {
  background: #fff;
  margin: 0 16px 16px;
  padding: 16px;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.answer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.answer-label {
  font-size: 13px;
  font-weight: 600;
}

.word-count {
  font-size: 12px;
  color: #94a3b8;
}

.answer-textarea {
  width: 100%;
  min-height: 120px;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 12px;
  font-size: 15px;
  resize: vertical;
  font-family: inherit;
  box-sizing: border-box;
  outline: none;
}

.answer-textarea:focus {
  border-color: #7c3aed;
}

.answer-actions,
.next-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.btn-primary {
  background: #7c3aed;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.score-circle {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #7c3aed;
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.score-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
}

.score-feedback {
  margin: 0;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
}

.feedback-cols {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.fb-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.fb-good ul,
.fb-improve ul,
.reference-block ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #475569;
  line-height: 1.7;
}

.fb-good .fb-title { color: #15803d; }
.fb-improve .fb-title { color: #b45309; }

.reference-block {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}

.ref-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 6px;
}

.question-nav {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 16px;
}

.nav-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: none;
  padding: 0;
  cursor: pointer;
  transition: transform 0.2s, background 0.2s;
}

.nav-dot.dot-done { background: #7c3aed; }
.nav-dot.dot-current { background: #a855f7; transform: scale(1.3); }
.nav-dot.dot-pending { background: #ddd; }
</style>
