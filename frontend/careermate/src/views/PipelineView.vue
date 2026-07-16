<template>
  <div class="pipeline-view">
    <header class="pipeline-head">
      <div>
        <h1 class="pipeline-title">准备 · 投递看板</h1>
        <p class="pipeline-sub">
          共 {{ board?.total || 0 }} 个在办 ·
          <span class="hint">问小职「我最近投得怎么样」也能看</span>
        </p>
      </div>
      <button class="refresh-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="pipeline-error">{{ error }}</p>
    <p v-else-if="loading && !board" class="pipeline-loading">加载中…</p>
    <p v-else-if="board && board.total === 0" class="pipeline-empty">
      还没有在办的投递机会。去「机会」选一个岗位点「定制简历」，就会出现在这里。
    </p>

    <div v-else-if="board" class="board" data-testid="pipeline-board">
      <section
        v-for="col in board.columns"
        :key="col.stage"
        class="board-col"
        :class="colClass(col.stage)"
      >
        <div class="col-head">
          <span class="col-label">{{ col.label }}</span>
          <span class="col-count">{{ col.count }}</span>
        </div>
        <div class="col-body">
          <article v-for="app in col.applications" :key="app.id" class="app-card">
            <div class="app-co">{{ app.company || '未知公司' }}</div>
            <div class="app-role">{{ app.roleTitle || '—' }}</div>
            <div class="app-meta">
              <span v-if="app.resumeVersionId" class="app-tag">简历已挂</span>
              <span class="app-time">{{ formatTime(app.lastActiveAt) }}</span>
            </div>
            <div class="app-actions">
              <select
                class="stage-select"
                :value="app.stage"
                :disabled="busyId === app.id"
                @change="onStageChange(app, $event)"
              >
                <option v-for="s in board.columns" :key="s.stage" :value="s.stage">{{ s.label }}</option>
              </select>
              <button class="archive-btn" type="button" :disabled="busyId === app.id" @click="onArchive(app)">
                归档
              </button>
            </div>
          </article>
          <p v-if="col.count === 0" class="col-empty">—</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getPipelineBoard, updateApplicationStage, archiveApplication } from '../api/pipeline'

const board = ref(null)
const loading = ref(false)
const error = ref('')
const busyId = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    board.value = await getPipelineBoard()
  } catch (e) {
    error.value = e?.message || '加载看板失败'
  } finally {
    loading.value = false
  }
}

async function onStageChange(app, evt) {
  const stage = evt?.target?.value
  if (!stage || stage === app.stage) return
  busyId.value = app.id
  try {
    await updateApplicationStage(app.id, stage)
    await load()
  } catch (e) {
    error.value = e?.message || '移动阶段失败'
  } finally {
    busyId.value = null
  }
}

async function onArchive(app) {
  busyId.value = app.id
  try {
    await archiveApplication(app.id)
    await load()
  } catch (e) {
    error.value = e?.message || '归档失败'
  } finally {
    busyId.value = null
  }
}

function colClass(stage) {
  if (stage === 'INTERVIEWING') return 'col-hot'
  if (stage === 'OFFER') return 'col-gold'
  return ''
}

function formatTime(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

onMounted(load)
</script>

<style scoped>
.pipeline-view {
  padding: 20px 24px;
  max-width: 1200px;
  margin: 0 auto;
}
.pipeline-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}
.pipeline-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.pipeline-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}
.pipeline-sub .hint {
  color: #4f46e5;
}
.refresh-btn {
  padding: 7px 16px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 13px;
  cursor: pointer;
}
.pipeline-error {
  color: #dc2626;
}
.pipeline-loading,
.pipeline-empty {
  color: #64748b;
  font-size: 14px;
  padding: 20px 0;
}
.board {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.board-col {
  background: #f5f6f8;
  border-radius: 12px;
  padding: 10px 8px;
  min-height: 220px;
}
.col-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 6px 10px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}
.col-count {
  font-weight: 600;
  color: #94a3b8;
  background: #e8eaf0;
  border-radius: 10px;
  padding: 0 8px;
  font-size: 12px;
}
.app-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 9px;
  box-shadow: 0 1px 2px rgba(20, 24, 40, 0.05);
}
.col-hot .app-card {
  border-color: #f5c6c8;
}
.col-gold .app-card {
  border-color: #f0dcb2;
}
.app-co {
  font-weight: 700;
  font-size: 13px;
  color: #0f172a;
}
.app-role {
  font-size: 12px;
  color: #64748b;
  margin-top: 1px;
}
.app-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 7px;
}
.app-tag {
  font-size: 10px;
  color: #4f46e5;
  background: #eef0fe;
  border-radius: 6px;
  padding: 1px 7px;
}
.app-time {
  font-size: 10px;
  color: #94a3b8;
  margin-left: auto;
}
.app-actions {
  display: flex;
  gap: 6px;
  margin-top: 9px;
}
.stage-select {
  flex: 1;
  font-size: 11px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  padding: 4px 6px;
  color: #334155;
  background: #fff;
}
.archive-btn {
  font-size: 11px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  padding: 4px 10px;
  background: #fff;
  color: #94a3b8;
  cursor: pointer;
}
.col-empty {
  text-align: center;
  color: #cbd5e1;
  font-size: 12px;
  margin: 6px 0;
}
@media (max-width: 900px) {
  .board {
    grid-template-columns: 1fr 1fr;
  }
}

/* 手机：5 列 kanban 改为横向滑动，每列固定可读宽度，避免挤成两列错乱 */
@media (max-width: 640px) {
  .board {
    display: flex;
    grid-template-columns: none;
    overflow-x: auto;
    scroll-snap-type: x mandatory;
    -webkit-overflow-scrolling: touch;
    padding-bottom: 6px;
  }
  .board-col {
    flex: 0 0 78%;
    max-width: 78%;
    scroll-snap-align: start;
  }
}
</style>
