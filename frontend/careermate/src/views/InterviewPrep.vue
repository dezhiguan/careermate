<template>
  <div class="interview-page">
    <div v-if="noDefaultResumeHint" class="banner warn">
      请先到简历页创建并设置默认简历。
      <router-link to="/resume" class="banner-link">前往简历工作室 →</router-link>
    </div>
    <div v-if="pageError" class="banner error">{{ pageError }}</div>

    <!-- 视图 A：题库首页 -->
    <template v-if="!activeSession">
      <header class="page-header-bar">
        <div class="header-left">
          <h1 class="page-title">题库</h1>
          <span class="stats-chip">已练 {{ totalPracticed }} 题 · 均分 {{ averageScoreDisplay }}</span>
        </div>
      </header>

      <div class="page-body">
        <div class="ai-push-card">
          <div class="push-icon">
            <svg class="icon-svg" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
            </svg>
          </div>
          <div class="push-text">
            <div class="push-sub">开始新的训练</div>
            <div v-if="creating" class="push-title">
              正在生成题目
              <span class="thinking-dots">
                <span class="dot-anim" /><span class="dot-anim" /><span class="dot-anim" />
              </span>
            </div>
            <div v-else class="push-title">AI 根据你的简历生成专属面试题</div>
            <div class="push-desc">基于默认简历 · 约 10 题 · 随时退出</div>
          </div>
          <div class="push-actions">
            <button
              type="button"
              class="btn-start"
              :disabled="creating"
              @click="startNewSession"
            >
              {{ creating ? '创建中...' : '开始 →' }}
            </button>
            <button
              type="button"
              class="btn-swap"
              :disabled="creating"
              @click="startNewSession"
            >
              换
            </button>
          </div>
        </div>

        <div class="filter-row">
          <span class="filter-label">挑你想练的：</span>
          <button
            v-for="chip in filterOptions"
            :key="chip"
            type="button"
            class="filter-chip"
            :class="{ active: activeFilter === chip }"
            @click="activeFilter = chip"
          >
            {{ chip }}
          </button>
        </div>

        <div v-if="listLoading" class="list-hint">加载训练记录...</div>

        <div v-else-if="filteredSessions.length === 0" class="empty-state">
          暂无训练记录，点击上方「开始 →」生成第一套题
        </div>

        <div v-else class="session-grid">
          <article
            v-for="item in filteredSessions"
            :key="item.id"
            class="session-card"
          >
            <button type="button" class="card-delete" @click.stop="confirmDeleteSession(item)">
              删除
            </button>

            <div class="card-top">
              <span
                v-if="item.averageScore != null"
                class="score-badge"
                :class="scoreBadgeClass(item.averageScore)"
              >
                {{ Math.round(item.averageScore) }} 分
              </span>
              <span class="card-time">{{ formatRelativeTime(item.updatedAt) }}</span>
            </div>

            <span
              class="category-badge"
              :style="categoryBadgeStyle(inferCategoryLabel(item.title))"
            >
              {{ inferCategoryLabel(item.title) }}
            </span>

            <h3 class="card-title">{{ item.title }}</h3>

            <p class="card-status">
              {{ item.status === 'COMPLETED' ? '已完成' : '进行中' }}
              · 已答 {{ item.answeredQuestions }}/{{ item.totalQuestions }} 题
            </p>

            <button
              type="button"
              class="card-action"
              :class="item.status === 'COMPLETED' ? 'btn-ghost' : 'btn-primary'"
              @click="openSession(item.id)"
            >
              {{ item.status === 'COMPLETED' ? '查看复盘' : '继续练' }}
            </button>
          </article>
        </div>
      </div>
    </template>

    <!-- 视图 B：答题视图 -->
    <template v-else>
      <header class="practice-header">
        <button type="button" class="btn-ghost exit-btn" @click="backToList">
          <svg class="icon-svg-sm" viewBox="0 0 24 24" aria-hidden="true">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          退出（自动保存）
        </button>
        <span class="practice-title">{{ activeSession.title }}</span>
        <span
          v-if="currentQuestion"
          class="category-badge header-badge"
          :style="categoryBadgeStyle(typeLabel(currentQuestion.questionType))"
        >
          {{ typeLabel(currentQuestion.questionType) }}
        </span>
        <div class="header-spacer" />
        <span class="timer-text">已用时 {{ formatElapsed(elapsedSeconds) }}</span>
        <div class="user-avatar">{{ avatarInitial }}</div>
      </header>

      <div v-if="currentQuestion" class="practice-body">
        <!-- 答题状态 -->
        <template v-if="currentQuestion.status !== 'ANSWERED'">
          <div class="question-card">
            <div class="question-card-top">
              <div class="question-badges">
                <span
                  class="category-badge"
                  :style="categoryBadgeStyle(typeLabel(currentQuestion.questionType))"
                >
                  {{ typeLabel(currentQuestion.questionType) }}
                </span>
              </div>
              <span v-if="sessionSourceHint" class="source-hint">{{ sessionSourceHint }}</span>
            </div>
            <div class="question-text">{{ currentQuestion.questionText }}</div>
            <div
              v-if="currentQuestion.referencePoints?.length && !showReferenceHints"
              class="hint-box"
            >
              💡 提示: {{ currentQuestion.referencePoints[0] }}
            </div>
            <div v-if="showReferenceHints && currentQuestion.referencePoints?.length" class="hint-list">
              <div class="hint-list-title">参考要点</div>
              <ul>
                <li v-for="(p, i) in currentQuestion.referencePoints" :key="'hint-' + i">{{ p }}</li>
              </ul>
            </div>
          </div>

          <div class="answer-card">
            <div class="answer-card-top">
              <span class="answer-label">你的回答</span>
              <span class="word-badge">{{ answerText.length }} 字</span>
            </div>
            <textarea
              v-model="answerText"
              class="answer-textarea"
              placeholder="输入你的回答..."
              :disabled="currentQuestion.status === 'ANSWERED' && !editingAnswer"
              maxlength="10000"
            />
            <div class="answer-toolbar">
              <button
                v-if="currentQuestion.referencePoints?.length"
                type="button"
                class="btn-ghost toolbar-btn"
                @click="showReferenceHints = !showReferenceHints"
              >
                提示
              </button>
              <div class="toolbar-spacer" />
              <button type="button" class="btn-ghost toolbar-btn" @click="giveUpAndSeeAnswer">
                放弃·看答案
              </button>
              <button
                type="button"
                class="btn-primary toolbar-btn"
                :disabled="!answerText.trim() || submitting || (currentQuestion.status === 'ANSWERED' && !editingAnswer)"
                @click="submitAnswer"
              >
                {{ submitting ? '提交中...' : '提交 → 看评分' }}
              </button>
            </div>
          </div>
        </template>

        <!-- 反馈状态 -->
        <template v-else>
          <div class="feedback-layout">
            <div class="feedback-main">
              <div class="score-header">
                <div class="score-box">{{ currentQuestion.score ?? '—' }}</div>
                <div class="score-info">
                  <div class="score-title">本题得分 {{ currentQuestion.score ?? '—' }}</div>
                  <div
                    v-if="currentQuestion.referencePoints?.length"
                    class="score-sub"
                  >
                    命中关键点 {{ currentQuestion.strengths?.length || 0 }}/{{ currentQuestion.referencePoints.length }}
                  </div>
                </div>
                <span class="done-badge">已答完</span>
              </div>

              <div class="feedback-columns">
                <div v-if="currentQuestion.strengths?.length" class="fb-card fb-good">
                  <div class="fb-card-title">✓ 答得好</div>
                  <ul>
                    <li v-for="(t, i) in currentQuestion.strengths" :key="'s-' + i">{{ t }}</li>
                  </ul>
                </div>
                <div v-if="currentQuestion.improvements?.length" class="fb-card fb-warn">
                  <div class="fb-card-title">⚠ 还可以补</div>
                  <ul>
                    <li v-for="(t, i) in currentQuestion.improvements" :key="'i-' + i">{{ t }}</li>
                  </ul>
                </div>
              </div>

              <div v-if="currentQuestion.referencePoints?.length" class="reference-card">
                <div class="reference-card-title">📖 参考要点</div>
                <ul>
                  <li
                    v-for="(p, i) in visibleReferencePoints"
                    :key="'rp-' + i"
                  >
                    {{ p }}
                  </li>
                </ul>
                <button
                  v-if="currentQuestion.referencePoints.length > 3"
                  type="button"
                  class="expand-link"
                  @click="showAllReferencePoints = !showAllReferencePoints"
                >
                  {{ showAllReferencePoints ? '收起 ←' : '展开全部 →' }}
                </button>
              </div>

              <p v-if="currentQuestion.feedback" class="ai-comment">{{ currentQuestion.feedback }}</p>
            </div>

            <aside class="feedback-side">
              <button
                type="button"
                class="btn-primary side-btn"
                :disabled="!hasNextQuestion"
                @click="nextQuestion"
              >
                <span class="side-btn-main">{{ hasNextQuestion ? '再来一题' : '已是最后一题' }}</span>
              </button>
              <button type="button" class="btn-ghost side-btn" @click="backToList">
                回题库选别的
              </button>
              <button
                type="button"
                class="btn-ghost side-btn"
                :disabled="completing"
                @click="handleCompleteAndExit"
              >
                {{ completing ? '结束中...' : '结束 · 看训练记录' }}
              </button>

              <div class="week-stats-card">
                <div class="week-stats-label">本周训练</div>
                <div class="week-stats-row">
                  <span class="week-stats-num">{{ totalPracticed }}</span>
                  <span class="week-stats-meta">道 · 均 {{ averageScoreDisplay }}</span>
                </div>
                <div class="week-stats-tip">不需要凑数 · 想练就练，不想练就关</div>
              </div>
            </aside>
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  completeInterviewSession,
  createInterviewSession,
  deleteInterviewSession,
  getInterviewSession,
  listInterviewSessions,
  submitInterviewAnswer,
} from '../api/interview'
import { authStore } from '../stores/authStore'

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
const activeFilter = ref('全部')
const elapsedSeconds = ref(0)
const showReferenceHints = ref(false)
const showAllReferencePoints = ref(false)

const filterOptions = ['全部', 'Java 八股', '系统设计', '分布式', '项目追问', '算法']

const FILTER_KEYWORDS = {
  'Java 八股': ['Java', '八股', 'JVM', 'Concurrent', 'HashMap', 'GC'],
  '系统设计': ['系统设计', '架构', '弹幕', '在线', '系统'],
  '分布式': ['分布式', 'Redisson', '锁', 'Kafka', 'MQ', 'Redis'],
  '项目追问': ['项目', '简历', '缓存', '追问'],
  '算法': ['算法', 'LRU', '手写', 'LeetCode', '树', '链表'],
}

let timerInterval = null

const currentQuestion = computed(() => {
  if (!activeSession.value?.questions?.length) return null
  return (
    activeSession.value.questions.find((q) => q.id === currentQuestionId.value) ||
    activeSession.value.questions[0]
  )
})

const totalPracticed = computed(() =>
  sessions.value.reduce((sum, s) => sum + (s.answeredQuestions || 0), 0)
)

const averageScore = computed(() => {
  const scored = sessions.value.filter((s) => s.averageScore != null)
  if (!scored.length) return null
  const total = scored.reduce((sum, s) => sum + s.averageScore, 0)
  return Math.round(total / scored.length)
})

const averageScoreDisplay = computed(() => (averageScore.value != null ? averageScore.value : '—'))

const filteredSessions = computed(() => {
  if (activeFilter.value === '全部') return sessions.value
  const keywords = FILTER_KEYWORDS[activeFilter.value] || [activeFilter.value]
  return sessions.value.filter((item) => {
    const title = item.title || ''
    return keywords.some((kw) => title.includes(kw)) || inferCategoryLabel(title) === activeFilter.value
  })
})

const avatarInitial = computed(() => {
  const name = authStore.state.currentUser?.username || '用'
  return name.charAt(0).toUpperCase()
})

const sessionSourceHint = computed(() => {
  const title = activeSession.value?.title || ''
  const hints = ['WXG', '高频', '三面', '二面', '大厂']
  const matched = hints.find((h) => title.includes(h))
  return matched ? `出自 ${title.includes('WXG') ? 'WXG' : ''} · 高频`.replace('出自  ·', '出自') : ''
})

const hasNextQuestion = computed(() => {
  const questions = activeSession.value?.questions || []
  const currentIdx = questions.findIndex((q) => q.id === currentQuestionId.value)
  return currentIdx >= 0 && currentIdx < questions.length - 1
})

const visibleReferencePoints = computed(() => {
  const points = currentQuestion.value?.referencePoints || []
  if (showAllReferencePoints.value) return points
  return points.slice(0, 3)
})

watch(activeSession, (session) => {
  if (session) {
    startTimer()
    showReferenceHints.value = false
    showAllReferencePoints.value = false
  } else {
    stopTimer()
  }
})

watch(currentQuestionId, () => {
  showReferenceHints.value = false
  showAllReferencePoints.value = false
})

onMounted(() => {
  loadSessions()
})

onBeforeUnmount(() => {
  stopTimer()
})

function startTimer() {
  stopTimer()
  elapsedSeconds.value = 0
  timerInterval = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
}

function stopTimer() {
  if (timerInterval) {
    window.clearInterval(timerInterval)
    timerInterval = null
  }
}

function formatElapsed(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatRelativeTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const diffMs = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `${diffDay} 天前`
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function inferCategoryLabel(title) {
  if (!title) return '综合'
  for (const [label, keywords] of Object.entries(FILTER_KEYWORDS)) {
    if (keywords.some((kw) => title.includes(kw))) return label
  }
  return '综合'
}

function categoryBadgeStyle(label) {
  const map = {
    'Java 八股': { background: '#dbeafe', color: '#1e40af' },
    '系统设计': { background: '#e0e7ff', color: '#3730a3' },
    '项目追问': { background: '#e0e7ff', color: '#3730a3' },
    '技能差距': { background: '#fef3c7', color: '#92400e' },
    '行为面试': { background: '#f1f5f9', color: '#475569' },
    '分布式': { background: '#fef3c7', color: '#92400e' },
    '算法': { background: '#dcfce7', color: '#15803d' },
    '综合': { background: '#f1f5f9', color: '#475569' },
  }
  const style = map[label] || map['综合']
  return { background: style.background, color: style.color }
}

function scoreBadgeClass(score) {
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-mid'
  return 'score-low'
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

function nextQuestion() {
  const questions = activeSession.value?.questions || []
  const currentIdx = questions.findIndex((q) => q.id === currentQuestionId.value)
  const next = questions[currentIdx + 1]
  if (next) {
    currentQuestionId.value = next.id
    syncAnswerFromQuestion()
  }
}

async function giveUpAndSeeAnswer() {
  answerText.value = '（放弃作答，查看参考答案）'
  await submitAnswer()
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

async function handleCompleteAndExit() {
  await completeSession()
  if (activeSession.value?.status === 'COMPLETED' || !pageError.value) {
    backToList()
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
    SKILL: 'Java 八股',
    SYSTEM_DESIGN: '系统设计',
    PROJECT: '项目追问',
    GAP: '技能差距',
    BEHAVIOR: '行为面试',
  }
  return map[type] || '综合'
}
</script>

<style scoped>
.interview-page {
  min-height: 100%;
  background: #f8fafc;
  color: #0f172a;
}

.banner {
  margin: 12px 16px 0;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}

.banner.error {
  background: #fef2f2;
  color: #991b1b;
}

.banner.warn {
  background: #fffbeb;
  color: #92400e;
}

.banner-link {
  margin-left: 8px;
  color: #4f46e5;
  font-weight: 600;
}

.page-header-bar {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 0 20px;
  display: flex;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.stats-chip {
  background: #f1f5f9;
  color: #64748b;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
}

.page-body {
  padding: 20px;
}

.ai-push-card {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 14px;
  padding: 18px;
  color: #fff;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.push-icon {
  width: 54px;
  height: 54px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 14px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.icon-svg {
  width: 26px;
  height: 26px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.push-text {
  flex: 1;
  min-width: 0;
}

.push-sub {
  font-size: 13px;
  opacity: 0.85;
}

.push-title {
  font-size: 17px;
  font-weight: 700;
  margin-top: 2px;
  line-height: 1.4;
}

.push-desc {
  font-size: 11px;
  opacity: 0.85;
  margin-top: 6px;
  line-height: 1.6;
}

.push-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

.btn-start {
  background: #fff;
  color: #4f46e5;
  border: 0;
  border-radius: 8px;
  padding: 10px 22px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.btn-start:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-swap {
  background: transparent;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 11px;
  cursor: pointer;
}

.btn-swap:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.thinking-dots {
  display: inline-flex;
  gap: 3px;
  margin-left: 4px;
  vertical-align: middle;
}

.dot-anim {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #fff;
  animation: dot-bounce 1.2s ease-in-out infinite;
}

.dot-anim:nth-child(2) { animation-delay: 0.2s; }
.dot-anim:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-4px); opacity: 1; }
}

.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  align-items: center;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 11px;
  color: #64748b;
}

.filter-chip {
  border: 0;
  background: #f1f5f9;
  color: #334155;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
}

.filter-chip.active {
  background: #eef2ff;
  color: #4f46e5;
}

.list-hint,
.empty-state {
  text-align: center;
  color: #64748b;
  padding: 48px 16px;
  font-size: 13px;
}

.session-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.session-card {
  position: relative;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
}

.card-delete {
  position: absolute;
  top: 10px;
  right: 10px;
  border: 0;
  background: transparent;
  color: #b91c1c;
  font-size: 10px;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
}

.card-top {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-right: 36px;
}

.score-badge {
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
}

.score-high {
  background: #dcfce7;
  color: #15803d;
}

.score-mid {
  background: #fef3c7;
  color: #92400e;
}

.score-low {
  background: #fee2e2;
  color: #b91c1c;
}

.card-time {
  font-size: 10px;
  color: #64748b;
}

.category-badge {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 600;
  margin-bottom: 8px;
}

.card-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.5;
  padding-right: 8px;
}

.card-status {
  margin: 0 0 10px;
  font-size: 11px;
  color: #64748b;
}

.card-action {
  width: 100%;
  padding: 9px;
  font-size: 12px;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  font-weight: 600;
}

.btn-primary {
  background: #4f46e5;
  color: #fff;
  border: 0;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-ghost {
  background: #fff;
  color: #334155;
  border: 1px solid #e2e8f0;
}

.practice-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.exit-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  border-radius: 8px;
}

.icon-svg-sm {
  width: 13px;
  height: 13px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.practice-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.header-badge {
  margin-bottom: 0;
}

.header-spacer {
  flex: 1;
}

.timer-text {
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}

.user-avatar {
  width: 30px;
  height: 30px;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 11px;
  flex-shrink: 0;
}

.practice-body {
  padding: 24px 20px;
  overflow: auto;
}

.question-card,
.answer-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  max-width: 780px;
  margin: 0 auto;
}

.question-card {
  padding: 18px;
  margin-bottom: 14px;
}

.question-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}

.question-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.source-hint {
  font-size: 10px;
  color: #94a3b8;
  white-space: nowrap;
}

.question-text {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.6;
  margin-bottom: 10px;
  word-break: break-word;
}

.hint-box {
  background: #f8fafc;
  border-radius: 6px;
  padding: 10px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.6;
}

.hint-list {
  background: #f8fafc;
  border-radius: 6px;
  padding: 10px 10px 10px 24px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.6;
}

.hint-list-title {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
  margin-left: -14px;
}

.answer-card {
  border: 2px solid #4f46e5;
  padding: 16px;
}

.answer-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.answer-label {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.word-badge {
  background: #dcfce7;
  color: #15803d;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.answer-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 0;
  background: transparent;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  min-height: 130px;
  outline: none;
  resize: vertical;
  font-family: inherit;
}

.answer-textarea:disabled {
  color: #94a3b8;
}

.answer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #f1f5f9;
  padding-top: 10px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.toolbar-spacer {
  flex: 1;
}

.toolbar-btn {
  padding: 9px 14px;
  font-size: 12px;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
}

.feedback-layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 18px;
  max-width: 1100px;
  margin: 0 auto;
}

.feedback-main {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px;
}

.score-header {
  display: flex;
  align-items: center;
  gap: 14px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 14px;
  margin-bottom: 14px;
}

.score-box {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #10b981, #34d399);
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  font-size: 24px;
  flex-shrink: 0;
}

.score-info {
  flex: 1;
  min-width: 0;
}

.score-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.score-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
}

.done-badge {
  background: #dcfce7;
  color: #15803d;
  padding: 5px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.feedback-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 14px;
}

.fb-card {
  border-radius: 6px;
  padding: 12px;
  font-size: 11px;
  line-height: 1.7;
}

.fb-good {
  background: #f0fdf4;
  border-left: 3px solid #10b981;
  color: #166534;
}

.fb-warn {
  background: #fef3c7;
  border-left: 3px solid #f59e0b;
  color: #78350f;
}

.fb-card-title {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 6px;
}

.fb-card ul {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.fb-card li::before {
  content: '· ';
}

.reference-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.reference-card-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}

.reference-card ul {
  margin: 0;
  padding-left: 16px;
  font-size: 11px;
  color: #475569;
  line-height: 1.7;
}

.expand-link {
  border: 0;
  background: transparent;
  color: #4f46e5;
  font-size: 11px;
  cursor: pointer;
  padding: 6px 0 0;
  font-family: inherit;
}

.ai-comment {
  margin: 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.7;
}

.feedback-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-btn {
  width: 100%;
  padding: 13px;
  font-size: 12px;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  text-align: center;
}

.side-btn-main {
  font-weight: 700;
  font-size: 13px;
}

.week-stats-card {
  background: #eef2ff;
  border-radius: 10px;
  padding: 12px;
  margin-top: 6px;
}

.week-stats-label {
  font-size: 10px;
  color: #4338ca;
  font-weight: 700;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.week-stats-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.week-stats-num {
  font-size: 24px;
  font-weight: 800;
  color: #4f46e5;
  line-height: 1;
}

.week-stats-meta {
  font-size: 10px;
  color: #64748b;
  padding-bottom: 2px;
}

.week-stats-tip {
  font-size: 10px;
  color: #64748b;
  margin-top: 6px;
  line-height: 1.5;
}

@media (max-width: 1024px) {
  .session-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .feedback-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-body,
  .practice-body {
    padding: 14px 16px;
  }

  .ai-push-card {
    flex-wrap: wrap;
  }

  .push-actions {
    flex-direction: row;
    width: 100%;
  }

  .btn-start {
    flex: 1;
  }

  .session-grid {
    grid-template-columns: 1fr;
  }

  .filter-row {
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 4px;
  }

  .filter-chip {
    white-space: nowrap;
    flex-shrink: 0;
  }

  .practice-header {
    padding: 0 12px;
    gap: 8px;
  }

  .practice-title {
    max-width: 100px;
  }

  .header-badge {
    display: none;
  }

  .feedback-columns {
    grid-template-columns: 1fr;
  }

  .answer-textarea {
    font-size: 16px;
    min-height: 140px;
  }

  .toolbar-btn,
  .card-action,
  .exit-btn {
    min-height: 44px;
  }
}
</style>
