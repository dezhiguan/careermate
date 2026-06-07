<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">📊 求职看板</h2>
      <p class="page-sub">你的求职全景 · 基于简历、岗位匹配与面试训练数据</p>
    </div>

    <div v-if="loading" class="state-hint">加载看板数据...</div>
    <div v-else-if="error" class="banner error">{{ error }}</div>

    <template v-else-if="overview">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-value">{{ overview.resumeStats.totalResumes }}</div>
          <div class="stat-label">简历数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ overview.jobMatchStats.totalMatches }}</div>
          <div class="stat-label">岗位匹配数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ overview.jobMatchStats.highMatches }}</div>
          <div class="stat-label">高匹配岗位</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ overview.interviewStats.totalSessions }}</div>
          <div class="stat-label">面试训练数</div>
        </div>
        <div v-if="overview.interviewStats.averageScore != null" class="stat-card stat-optional">
          <div class="stat-value">{{ overview.interviewStats.averageScore }}</div>
          <div class="stat-label">平均面试分</div>
        </div>
      </div>

      <section class="summary-panel">
        <div class="section-title">📌 当前状态</div>
        <div class="summary-row">
          <span class="summary-label">默认简历</span>
          <span v-if="overview.resumeStats.hasDefaultResume" class="summary-value ok">
            {{ overview.resumeStats.defaultResumeTitle }}
          </span>
          <span v-else class="summary-value warn">未设置</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">最近岗位</span>
          <span v-if="overview.jobMatchStats.latestJobTitle" class="summary-value">
            {{ overview.jobMatchStats.latestJobTitle }}
            <template v-if="overview.jobMatchStats.latestMatchScore != null">
              · 匹配 {{ overview.jobMatchStats.latestMatchScore }}%
            </template>
          </span>
          <span v-else class="summary-value muted">暂无</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">最近面试训练</span>
          <span v-if="overview.interviewStats.latestSessionTitle" class="summary-value">
            {{ overview.interviewStats.latestSessionTitle }}
            · {{ interviewStatusLabel }}
          </span>
          <span v-else class="summary-value muted">暂无</span>
        </div>
      </section>

      <div class="section-title">✅ 下一步任务</div>
      <div v-if="!overview.tasks?.length" class="empty-state tasks-empty">
        <div>暂无任务</div>
      </div>
      <div v-else class="task-list">
        <div v-for="task in overview.tasks" :key="task.id" class="task-card">
          <div class="task-main">
            <div class="task-title">{{ task.title }}</div>
            <div class="task-meta">
              <span class="task-tag">{{ categoryLabel(task.category) }}</span>
              <span class="task-tag" :class="priorityClass(task.priority)">
                {{ priorityLabel(task.priority) }}
              </span>
              <span v-if="task.dueDate" class="task-due">截止 {{ task.dueDate }}</span>
            </div>
          </div>
          <button
            type="button"
            class="task-done-btn"
            :disabled="completingTaskId === task.id"
            @click="completeTask(task.id)"
          >
            {{ completingTaskId === task.id ? '处理中...' : '完成' }}
          </button>
        </div>
      </div>

      <div class="section-title">📋 下一步建议</div>
      <div class="suggestions-grid">
        <div
          v-for="(card, i) in overview.suggestions"
          :key="i"
          class="suggestion-card"
          :class="priorityClass(card.priority)"
        >
          <div class="card-priority-badge">{{ priorityBadge(card.priority) }}</div>
          <div class="card-title">{{ card.title }}</div>
          <div class="card-text">{{ card.description }}</div>
          <router-link :to="card.route" class="card-link">{{ card.actionText }} →</router-link>
        </div>
      </div>

      <div class="section-title">🧩 跨岗位技能缺口</div>
      <div v-if="skillGapLoading" class="state-hint">分析中...</div>
      <template v-else-if="skillGap">
        <div v-if="!skillGap.items?.length" class="empty-state">
          <div>暂无数据，录入多个岗位匹配后即可查看高频缺失技能</div>
        </div>
        <div v-else class="skill-gap-list">
          <div v-for="item in skillGap.items" :key="item.skill" class="skill-gap-row">
            <div class="gap-label">{{ item.skill }}</div>
            <div class="gap-bar-wrap">
              <div class="gap-bar" :style="{ width: gapBarWidth(item.count) }"></div>
            </div>
            <div class="gap-count">{{ item.count }}</div>
          </div>
          <div class="gap-note">基于 {{ skillGap.totalJobMatches }} 条岗位匹配</div>
        </div>
      </template>

      <div class="section-title">📅 最近动态</div>
      <div v-if="!overview.recentActivities?.length" class="empty-state">
        <div class="empty-icon">📭</div>
        <div>暂无动态，完成简历、岗位匹配或面试训练后会出现在这里</div>
      </div>
      <div v-else class="timeline">
        <div v-for="(item, i) in overview.recentActivities" :key="i" class="timeline-item">
          <div class="timeline-dot" :class="activityDotClass(item.type)"></div>
          <div class="timeline-content">
            <router-link :to="item.route" class="timeline-title">{{ item.title }}</router-link>
            <div class="timeline-text">{{ item.description }}</div>
            <div class="timeline-time">{{ formatTime(item.occurredAt) }}</div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getDashboardOverview, getSkillGap } from '../api/dashboard'
import { markTaskDone } from '../api/tasks'
import { CAREER_TASKS_UPDATED_EVENT } from '../utils/agentToolDisplay'

const overview = ref(null)
const loading = ref(false)
const error = ref('')
const completingTaskId = ref(null)
const skillGap = ref(null)
const skillGapLoading = ref(false)

const skillGapMaxCount = computed(() => skillGap.value?.items?.[0]?.count || 1)

function gapBarWidth(count) {
  return Math.round(count * 100 / skillGapMaxCount.value) + '%'
}

const interviewStatusLabel = computed(() => {
  const stats = overview.value?.interviewStats
  if (!stats?.latestSessionTitle) return ''
  if (stats.activeSessions > 0 && stats.completedSessions === 0) return '进行中'
  if (stats.completedSessions > 0) return '含已完成'
  return '进行中'
})

function onTasksUpdated() {
  loadOverview()
}

onMounted(() => {
  loadOverview()
  skillGapLoading.value = true
  getSkillGap()
    .then((data) => { skillGap.value = data })
    .catch(() => {})
    .finally(() => { skillGapLoading.value = false })
  window.addEventListener(CAREER_TASKS_UPDATED_EVENT, onTasksUpdated)
})

onBeforeUnmount(() => {
  window.removeEventListener(CAREER_TASKS_UPDATED_EVENT, onTasksUpdated)
})

async function loadOverview() {
  loading.value = true
  error.value = ''
  try {
    overview.value = await getDashboardOverview()
  } catch (e) {
    error.value = e?.message || '加载看板失败'
  } finally {
    loading.value = false
  }
}

async function completeTask(taskId) {
  if (!taskId || completingTaskId.value) return
  completingTaskId.value = taskId
  try {
    await markTaskDone(taskId)
    await loadOverview()
  } catch (e) {
    error.value = e?.message || '标记完成失败'
  } finally {
    completingTaskId.value = null
  }
}

function categoryLabel(category) {
  const map = {
    RESUME: '简历',
    JOB_MATCH: '岗位匹配',
    INTERVIEW: '面试',
    PROFILE: '画像',
    GENERAL: '通用',
  }
  return map[category] || category || '任务'
}

function priorityLabel(priority) {
  if (priority === 'HIGH') return '高优先级'
  if (priority === 'MEDIUM') return '中优先级'
  return '低优先级'
}

function priorityClass(priority) {
  if (priority === 'HIGH') return 'high'
  if (priority === 'MEDIUM') return 'mid'
  return 'low'
}

function priorityBadge(priority) {
  if (priority === 'HIGH') return '⚠️ 高优先级'
  if (priority === 'MEDIUM') return '📝 建议完成'
  return '💡 可选'
}

function activityDotClass(type) {
  if (!type) return ''
  if (type.startsWith('RESUME')) return 'resume'
  if (type.startsWith('JOB_MATCH')) return 'match'
  if (type.startsWith('INTERVIEW')) return 'interview'
  return ''
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped>
.page-container { max-width: 960px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 18px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.state-hint {
  text-align: center; color: var(--text-muted); padding: 32px 16px; font-size: 14px;
}
.banner.error {
  padding: 10px 12px; border-radius: 8px; font-size: 13px;
  background: #fef2f2; color: #991b1b; margin-bottom: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--navy); border-radius: 10px; padding: 16px 12px;
  color: #fff; text-align: center; transition: transform .2s;
}
.stat-card:hover { transform: translateY(-2px); }
.stat-optional { background: #1e3a5f; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 10px; opacity: .55; margin-top: 2px; }

.summary-panel {
  background: #f8fafc; border: 1px solid var(--border); border-radius: 12px;
  padding: 14px 16px; margin-bottom: 22px;
}
.summary-row {
  display: flex; flex-wrap: wrap; gap: 8px 12px; padding: 6px 0;
  font-size: 12px; border-bottom: 1px solid var(--border);
}
.summary-row:last-child { border-bottom: none; }
.summary-label { color: var(--text-muted); min-width: 72px; }
.summary-value { color: var(--slate); flex: 1; word-break: break-word; }
.summary-value.ok { color: var(--green); font-weight: 600; }
.summary-value.warn { color: #b45309; }
.summary-value.muted { color: var(--text-muted); }

.section-title { font-weight: 600; font-size: 12px; margin-bottom: 10px; color: var(--navy); }
.suggestions-grid {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 22px;
}

.suggestion-card {
  border-radius: 10px; padding: 14px; font-size: 11px;
  display: flex; flex-direction: column; gap: 6px;
  transition: transform .2s; background: #fff; min-width: 0;
}
.suggestion-card:hover { transform: translateY(-2px); }
.suggestion-card.high { border: 2px solid var(--red); }
.suggestion-card.mid { border: 2px solid var(--amber); }
.suggestion-card.low { border: 1px solid var(--border); }

.card-priority-badge { font-weight: 600; font-size: 10px; }
.card-title { font-weight: 600; font-size: 12px; color: var(--navy); }
.card-text { color: var(--gray); line-height: 1.5; word-break: break-word; }
.card-link {
  color: var(--purple); font-size: 11px; text-decoration: none; font-weight: 500;
  margin-top: 4px;
}
.card-link:hover { color: #7c3aed; }

.empty-state {
  text-align: center; color: var(--text-muted); padding: 24px 16px; font-size: 13px;
  background: #f8fafc; border-radius: 10px; margin-bottom: 22px;
}
.tasks-empty { padding: 16px; margin-bottom: 22px; }

.task-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 22px; }
.task-card {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 12px 14px; background: #fff; border: 1px solid var(--border); border-radius: 10px;
}
.task-title { font-size: 13px; font-weight: 600; color: var(--navy); margin-bottom: 6px; }
.task-meta { display: flex; flex-wrap: wrap; gap: 6px; font-size: 10px; }
.task-tag {
  padding: 2px 8px; border-radius: 999px; background: #f1f5f9; color: var(--slate);
}
.task-tag.high { background: #fef2f2; color: #991b1b; }
.task-tag.mid { background: #fffbeb; color: #92400e; }
.task-tag.low { background: #f0fdf4; color: #166534; }
.task-due { color: var(--text-muted); }
.task-done-btn {
  flex-shrink: 0; border: 1px solid var(--green); background: #fff; color: var(--green);
  border-radius: 6px; padding: 6px 12px; font-size: 11px; font-weight: 600; cursor: pointer;
}
.task-done-btn:hover:not(:disabled) { background: #f0fdf4; }
.task-done-btn:disabled { opacity: .6; cursor: default; }
.empty-icon { font-size: 28px; margin-bottom: 8px; }

.skill-gap-list { margin-bottom: 16px; }
.skill-gap-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 12px; }
.gap-label { width: 90px; flex-shrink: 0; color: var(--slate); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gap-bar-wrap { flex: 1; background: #f1f5f9; border-radius: 4px; height: 8px; overflow: hidden; }
.gap-bar { height: 100%; background: linear-gradient(90deg, #8b5cf6, #6366f1); border-radius: 4px; transition: width .3s; }
.gap-count { width: 24px; text-align: right; color: var(--text-muted); font-size: 11px; flex-shrink: 0; }
.gap-note { font-size: 11px; color: var(--text-muted); margin-top: 4px; }

.timeline { margin-bottom: 22px; }
.timeline-item {
  display: flex; gap: 12px; padding: 10px 0;
  border-bottom: 1px solid var(--border); align-items: flex-start;
}
.timeline-dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; margin-top: 5px;
}
.timeline-dot.match { background: var(--purple); }
.timeline-dot.resume { background: var(--pink); }
.timeline-dot.interview { background: var(--green); }
.timeline-title {
  font-size: 12px; font-weight: 600; color: var(--navy); text-decoration: none;
}
.timeline-title:hover { color: var(--purple); }
.timeline-text { font-size: 12px; color: var(--slate); word-break: break-word; overflow-wrap: anywhere; }
.timeline-time { font-size: 10px; color: var(--text-muted); margin-top: 2px; }

@media (max-width: 768px) {
  .page-container {
    padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
    max-width: 100%; overflow-x: hidden;
  }
  .stats-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .suggestions-grid { grid-template-columns: 1fr; }
  .stat-card, .suggestion-card, .summary-panel { width: 100%; min-width: 0; }
  .card-text, .timeline-text, .summary-value { word-break: break-word; overflow-wrap: anywhere; }
}

@media (max-width: 480px) {
  .page-title { font-size: 18px; }
  .stats-grid { grid-template-columns: 1fr; }
  .stat-value { font-size: 24px; }
}
</style>
