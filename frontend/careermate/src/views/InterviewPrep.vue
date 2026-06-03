<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">🎤 面试特训</h2>
      <p class="page-sub">基于默认简历与最近岗位匹配生成面试题（V1）</p>
    </div>

    <div v-if="noDefaultResumeHint" class="banner warn">
      请先到简历页创建并设置默认简历。
      <router-link to="/resume" class="banner-link">前往简历工作室 →</router-link>
    </div>
    <div v-if="pageError" class="banner error">{{ pageError }}</div>

    <div class="toolbar">
      <button class="btn primary" type="button" :disabled="creating" @click="startNewSession">
        {{ creating ? '创建中...' : '开始新的面试训练' }}
      </button>
    </div>

    <div v-if="listLoading" class="list-hint">加载训练记录...</div>

    <div v-else-if="!activeSession" class="interview-layout list-only">
      <div v-if="sessions.length === 0" class="empty-state">
        <div class="empty-icon">🎯</div>
        <div>暂无训练记录，点击「开始新的面试训练」</div>
      </div>
      <div v-else class="session-list">
        <div
          v-for="item in sessions"
          :key="item.id"
          class="session-card"
          @click="openSession(item.id)"
        >
          <div class="session-title">{{ item.title }}</div>
          <div class="session-meta">
            {{ statusLabel(item.status) }} · 已答 {{ item.answeredQuestions }}/{{ item.totalQuestions }}
            <span v-if="item.averageScore != null"> · 均分 {{ item.averageScore }}</span>
          </div>
          <p v-if="item.summary" class="session-summary">{{ item.summary }}</p>
          <button class="card-delete" type="button" @click.stop="confirmDeleteSession(item)">
            删除
          </button>
        </div>
      </div>
    </div>

    <div v-else class="interview-layout">
      <aside class="question-nav">
        <div class="nav-title">题目列表</div>
        <button
          v-for="q in activeSession.questions"
          :key="q.id"
          type="button"
          class="nav-item"
          :class="{ active: q.id === currentQuestion?.id, answered: q.status === 'ANSWERED' }"
          @click="selectQuestion(q)"
        >
          Q{{ q.questionNo }} · {{ typeLabel(q.questionType) }}
        </button>
        <button
          class="btn outline complete-btn"
          type="button"
          :disabled="completing || activeSession.status === 'COMPLETED'"
          @click="completeSession"
        >
          {{ activeSession.status === 'COMPLETED' ? '已完成' : '完成训练' }}
        </button>
        <button class="btn outline back-btn" type="button" @click="backToList">← 返回列表</button>
      </aside>

      <div class="main-area" v-if="currentQuestion">
        <div class="session-bar">
          <span class="session-bar-title">{{ activeSession.title }}</span>
          <span class="session-bar-meta">
            {{ statusLabel(activeSession.status) }} · {{ activeSession.answeredQuestions }}/{{ activeSession.totalQuestions }}
          </span>
        </div>

        <div class="question-card">
          <div class="question-meta">
            第 {{ currentQuestion.questionNo }} 题 / 共 {{ activeSession.questions.length }} 题 ·
            {{ typeLabel(currentQuestion.questionType) }}
          </div>
          <div class="question-text">{{ currentQuestion.questionText }}</div>
          <div v-if="currentQuestion.referencePoints?.length" class="reference-box">
            <div class="reference-label">参考要点</div>
            <div class="reference-tags">
              <span
                v-for="(p, i) in currentQuestion.referencePoints"
                :key="'rp-' + i"
                class="reference-tag"
              >{{ p }}</span>
            </div>
          </div>
        </div>

        <div class="answer-area">
          <textarea
            v-model="answerText"
            class="answer-input"
            placeholder="输入你的回答..."
            :disabled="currentQuestion.status === 'ANSWERED' && !editingAnswer"
            maxlength="10000"
          />
        </div>

        <div class="action-row">
          <button
            class="btn primary"
            type="button"
            :disabled="!answerText.trim() || submitting || (currentQuestion.status === 'ANSWERED' && !editingAnswer)"
            @click="submitAnswer"
          >
            {{ submitting ? '提交中...' : currentQuestion.status === 'ANSWERED' ? '已提交' : '提交回答' }}
          </button>
        </div>

        <div v-if="currentQuestion.status === 'ANSWERED'" class="feedback-card">
          <div class="feedback-header">📊 评分反馈 · {{ currentQuestion.score }} 分</div>
          <p class="feedback-content">{{ currentQuestion.feedback }}</p>
          <div v-if="currentQuestion.strengths?.length" class="fb-section">
            <div class="fb-label">优势</div>
            <ul class="bullet-list">
              <li v-for="(t, i) in currentQuestion.strengths" :key="'s-' + i">{{ t }}</li>
            </ul>
          </div>
          <div v-if="currentQuestion.improvements?.length" class="fb-section">
            <div class="fb-label">改进建议</div>
            <ul class="bullet-list">
              <li v-for="(t, i) in currentQuestion.improvements" :key="'i-' + i">{{ t }}</li>
            </ul>
          </div>
        </div>

        <div class="progress-bar">
          <div
            v-for="q in activeSession.questions"
            :key="'p-' + q.id"
            class="progress-seg"
            :class="{ done: q.status === 'ANSWERED', current: q.id === currentQuestion.id }"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  completeInterviewSession,
  createInterviewSession,
  deleteInterviewSession,
  getInterviewSession,
  listInterviewSessions,
  submitInterviewAnswer,
} from '../api/interview'

const sessions = ref([])
const activeSession = ref(null)
const currentQuestionId = ref(null)
const answerText = ref('')
const editingAnswer = ref(false)
const listLoading = ref(false)
const creating = ref(false)
const submitting = ref(false)
const completing = ref(false)
const pageError = ref('')
const noDefaultResumeHint = ref(false)

const currentQuestion = computed(() => {
  if (!activeSession.value?.questions?.length) return null
  return (
    activeSession.value.questions.find((q) => q.id === currentQuestionId.value) ||
    activeSession.value.questions[0]
  )
})

onMounted(() => {
  loadSessions()
})

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
    const detail = await createInterviewSession({ title: formatDefaultTitle() })
    await loadSessions()
    activeSession.value = detail
    currentQuestionId.value = detail.questions?.[0]?.id ?? null
    answerText.value = ''
    editingAnswer.value = false
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

async function openSession(id) {
  pageError.value = ''
  try {
    const detail = await getInterviewSession(id)
    activeSession.value = detail
    const firstPending = detail.questions?.find((q) => q.status === 'PENDING')
    currentQuestionId.value = (firstPending || detail.questions?.[0])?.id ?? null
    syncAnswerFromQuestion()
  } catch (e) {
    pageError.value = e?.message || '加载训练详情失败'
  }
}

function backToList() {
  activeSession.value = null
  currentQuestionId.value = null
  answerText.value = ''
  loadSessions()
}

function selectQuestion(q) {
  currentQuestionId.value = q.id
  syncAnswerFromQuestion()
}

function syncAnswerFromQuestion() {
  const q = currentQuestion.value
  if (!q) {
    answerText.value = ''
    return
  }
  answerText.value = q.answerText || ''
  editingAnswer.value = false
}

async function submitAnswer() {
  if (!activeSession.value || !currentQuestion.value || !answerText.value.trim()) return
  submitting.value = true
  pageError.value = ''
  try {
    const updated = await submitInterviewAnswer(
      activeSession.value.id,
      currentQuestion.value.id,
      { answerText: answerText.value.trim() }
    )
    const idx = activeSession.value.questions.findIndex((q) => q.id === updated.id)
    if (idx >= 0) {
      activeSession.value.questions[idx] = updated
    }
    const refreshed = await getInterviewSession(activeSession.value.id)
    activeSession.value = refreshed
    currentQuestionId.value = updated.id
    syncAnswerFromQuestion()
    await loadSessions()
  } catch (e) {
    pageError.value = e?.message || '提交回答失败'
  } finally {
    submitting.value = false
  }
}

async function completeSession() {
  if (!activeSession.value) return
  completing.value = true
  pageError.value = ''
  try {
    activeSession.value = await completeInterviewSession(activeSession.value.id)
    await loadSessions()
  } catch (e) {
    pageError.value = e?.message || '完成训练失败'
  } finally {
    completing.value = false
  }
}

async function confirmDeleteSession(item) {
  if (!window.confirm(`确定删除训练「${item.title}」吗？`)) return
  try {
    await deleteInterviewSession(item.id)
    if (activeSession.value?.id === item.id) {
      activeSession.value = null
    }
    await loadSessions()
  } catch (e) {
    pageError.value = e?.message || '删除失败'
  }
}

function statusLabel(status) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'ACTIVE') return '进行中'
  return status || '未知'
}

function typeLabel(type) {
  const map = {
    PROJECT: '项目经历',
    SKILL: '技能落地',
    GAP: '技能差距',
    SYSTEM_DESIGN: '系统设计',
    BEHAVIOR: '行为面试',
  }
  return map[type] || type
}
</script>

<style scoped>
.page-container { max-width: 1100px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.banner {
  padding: 10px 12px; border-radius: 8px; font-size: 13px; margin-bottom: 12px;
}
.banner.error { background: #fef2f2; color: #991b1b; }
.banner.warn { background: #fffbeb; color: #92400e; }
.banner-link { margin-left: 8px; color: var(--purple); font-weight: 600; }

.toolbar { margin-bottom: 16px; }
.btn {
  padding: 8px 18px; border-radius: 8px; font-size: 12px; cursor: pointer;
  border: none; font-family: inherit; transition: all .2s;
}
.btn.primary { background: var(--purple); color: #fff; font-weight: 600; }
.btn.primary:disabled { opacity: .5; cursor: default; }
.btn.outline {
  background: #fff; border: 1px solid var(--border); color: var(--slate);
  width: 100%; margin-top: 8px;
}

.list-hint, .empty-state {
  text-align: center; color: var(--text-muted); padding: 32px 16px; font-size: 14px;
}
.empty-icon { font-size: 32px; margin-bottom: 8px; }

.session-list { display: flex; flex-direction: column; gap: 10px; }
.session-card {
  background: #fff; border: 1px solid var(--border); border-radius: 12px;
  padding: 14px; cursor: pointer; position: relative;
}
.session-card:hover { border-color: var(--purple); }
.session-title { font-weight: 600; font-size: 14px; color: var(--navy); }
.session-meta { font-size: 11px; color: var(--text-muted); margin-top: 4px; }
.session-summary {
  font-size: 11px; color: var(--text-muted); margin-top: 8px;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.card-delete {
  margin-top: 8px; font-size: 11px; color: #991b1b; background: transparent;
  border: 1px solid #fecaca; border-radius: 6px; padding: 4px 8px; cursor: pointer;
}

.interview-layout {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 20px;
  min-width: 0;
}
.interview-layout.list-only { grid-template-columns: 1fr; }

.question-nav {
  background: #f8fafc; border: 1px solid var(--border); border-radius: 12px;
  padding: 12px; min-width: 0;
}
.nav-title { font-weight: 600; font-size: 12px; margin-bottom: 8px; color: var(--navy); }
.nav-item {
  display: block; width: 100%; text-align: left; padding: 8px 10px; margin-bottom: 4px;
  border: 1px solid transparent; border-radius: 8px; background: #fff; font-size: 11px;
  cursor: pointer; color: var(--slate); word-break: break-word;
}
.nav-item.active { border-color: var(--purple); color: var(--purple); font-weight: 600; }
.nav-item.answered { border-left: 3px solid var(--green); }

.main-area { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.session-bar {
  display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px;
  font-size: 12px; color: var(--text-muted);
}
.session-bar-title { font-weight: 600; color: var(--navy); }

.question-card {
  background: var(--navy); border-radius: 12px; padding: 18px 20px; color: #fff;
}
.question-meta { font-size: 10px; opacity: .55; margin-bottom: 8px; }
.question-text { font-size: 14px; line-height: 1.7; word-break: break-word; overflow-wrap: anywhere; }
.reference-box { margin-top: 12px; opacity: .9; }
.reference-label { font-size: 10px; margin-bottom: 6px; }
.reference-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.reference-tag {
  font-size: 10px; background: rgba(255,255,255,.15); padding: 3px 8px; border-radius: 6px;
}

.answer-input {
  width: 100%; box-sizing: border-box; min-height: 120px; border: 1px solid var(--border);
  border-radius: 10px; padding: 12px; font-size: 14px; resize: vertical; font-family: inherit;
}
.answer-input:focus { border-color: var(--purple); outline: none; }
.answer-input:disabled { background: #f8f8f8; color: var(--text-muted); }

.action-row { display: flex; gap: 10px; flex-wrap: wrap; }

.feedback-card {
  background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 10px; padding: 14px;
}
.feedback-header { font-weight: 600; font-size: 13px; color: var(--green); margin-bottom: 6px; }
.feedback-content { font-size: 12px; color: var(--slate); line-height: 1.6; margin: 0 0 10px; }
.fb-section { margin-top: 8px; }
.fb-label { font-size: 11px; font-weight: 600; color: var(--navy); margin-bottom: 4px; }
.bullet-list { margin: 0; padding-left: 18px; font-size: 12px; color: var(--text-muted); }

.progress-bar { display: flex; gap: 4px; }
.progress-seg {
  flex: 1; height: 4px; border-radius: 2px; background: var(--light); transition: background .3s;
}
.progress-seg.done { background: var(--green); }
.progress-seg.current { background: var(--purple); }

@media (max-width: 768px) {
  .page-container {
    padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
    max-width: 100%; overflow-x: hidden;
  }
  .interview-layout { grid-template-columns: 1fr; }
  .question-nav {
    display: flex; flex-wrap: nowrap; overflow-x: auto; gap: 8px; padding-bottom: 8px;
    -webkit-overflow-scrolling: touch;
  }
  .nav-title { width: 100%; flex: 0 0 100%; }
  .nav-item { flex: 0 0 auto; min-width: 120px; margin-bottom: 0; }
  .complete-btn, .back-btn { flex: 0 0 100%; }
  .answer-input { font-size: 16px; min-height: 140px; }
  .btn { min-height: 44px; }
}
</style>
