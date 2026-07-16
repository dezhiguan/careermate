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

    <!-- 桌面：五列看板 -->
    <div v-else-if="board && isDesktop" class="board" data-testid="pipeline-board">
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
          <article
            v-for="app in col.applications"
            :key="app.id"
            class="app-card"
            :class="{ opening: openingId === app.id }"
            @click="openLine(app)"
          >
            <div class="app-top">
              <span class="app-avatar">{{ avatarChar(app) }}</span>
              <div class="app-headtext">
                <div class="app-co">{{ cardName(app) }}</div>
                <div class="app-activity">{{ activityText(app) }}</div>
              </div>
            </div>
            <div v-if="app.resumeVersionId" class="app-meta">
              <span class="app-tag">简历已挂</span>
            </div>
            <div class="app-actions" @click.stop>
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

    <!-- 移动端：阶段分段器 + 单列卡片列表 -->
    <div v-else-if="board" class="mobile-pipe" data-testid="pipeline-board">
      <div class="stage-seg">
        <button
          v-for="col in board.columns"
          :key="col.stage"
          type="button"
          class="seg-chip"
          :class="{ on: activeStage === col.stage }"
          @click="activeStage = col.stage"
        >
          {{ col.label }}<span class="seg-count">{{ col.count }}</span>
        </button>
      </div>
      <div class="mobile-list">
        <template v-if="mobileColumn && mobileColumn.applications.length">
          <article
            v-for="app in mobileColumn.applications"
            :key="app.id"
            class="app-card"
            :class="{ opening: openingId === app.id }"
            @click="openLine(app)"
          >
            <div class="app-top">
              <span class="app-avatar">{{ avatarChar(app) }}</span>
              <div class="app-headtext">
                <div class="app-co">{{ cardName(app) }}</div>
                <div class="app-activity">{{ activityText(app) }}</div>
              </div>
            </div>
            <div v-if="app.resumeVersionId" class="app-meta">
              <span class="app-tag">简历已挂</span>
            </div>
            <div class="app-actions" @click.stop>
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
        </template>
        <p v-else class="col-empty">该阶段暂无卡片</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getPipelineBoard, updateApplicationStage, archiveApplication } from '../api/pipeline'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'

const router = useRouter()
const board = ref(null)
const loading = ref(false)
const error = ref('')
const busyId = ref(null)
const openingId = ref(null)

// 分平台：桌面五列看板 / 移动阶段分段器 + 单列列表
const isDesktop = ref(false)
function updateIsDesktop() {
  isDesktop.value = typeof window !== 'undefined' && window.innerWidth >= 640
}
const activeStage = ref('')
const mobileColumn = computed(() => {
  const cols = board.value?.columns || []
  return cols.find((c) => c.stage === activeStage.value) || cols[0] || null
})

// 同「公司·职位」在看板上出现多次时补序号区分项（②③…）
const dedupSuffix = computed(() => {
  const map = {}
  const groups = {}
  for (const col of board.value?.columns || []) {
    for (const app of col.applications || []) {
      const key = `${app.company || ''}|${app.roleTitle || ''}`
      ;(groups[key] = groups[key] || []).push(app.id)
    }
  }
  for (const ids of Object.values(groups)) {
    if (ids.length > 1) {
      ids.forEach((id, i) => { map[id] = `②③④⑤⑥⑦⑧⑨`[i - 1] || (i > 0 ? `·${i + 1}` : '') })
    }
  }
  return map
})
function cardName(app) {
  const base = `${app.company || '未知公司'} · ${app.roleTitle || '岗位'}`
  const suffix = dedupSuffix.value[app.id]
  return suffix ? `${base} · ${suffix}` : base
}
function avatarChar(app) {
  const c = (app.company || '公').trim()
  return c.charAt(0).toUpperCase()
}
function activityText(app) {
  const label = app.stageLabel || ''
  const t = formatTime(app.lastActiveAt)
  return t ? `${label} · 最近 ${t}` : label
}

async function openLine(app) {
  if (!app?.jdDocId || openingId.value) return
  openingId.value = app.id
  try {
    const resp = await createWorkspace({
      workspaceType: 'JD_PREP',
      title: `${app.company || ''} ${app.roleTitle || ''}`.trim() || 'JD 准备空间',
      contextMetadata: {
        jdId: `doc-${app.jdDocId}`,
        company: app.company,
        title: app.roleTitle,
      },
    })
    await navigateToWorkspace(router, resp)
  } catch (e) {
    error.value = e?.message || '进入会话失败'
  } finally {
    openingId.value = null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    board.value = await getPipelineBoard()
    // 移动分段器默认选第一个有卡的阶段，否则第一列
    const cols = board.value?.columns || []
    if (!activeStage.value || !cols.some((c) => c.stage === activeStage.value)) {
      activeStage.value = (cols.find((c) => c.count > 0) || cols[0])?.stage || ''
    }
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

onMounted(() => {
  updateIsDesktop()
  window.addEventListener('resize', updateIsDesktop, { passive: true })
  load()
})
onBeforeUnmount(() => window.removeEventListener('resize', updateIsDesktop))
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
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.app-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.1);
}
.app-card.opening {
  opacity: 0.6;
}
.app-top {
  display: flex;
  gap: 8px;
  align-items: center;
}
.app-avatar {
  flex: 0 0 auto;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.app-headtext {
  min-width: 0;
}
.app-activity {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

/* 手机：阶段分段器 + 单列卡片列表（<640px 用 mobile-pipe，不再渲染 board） */
.stage-seg {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 8px;
  margin-bottom: 6px;
}
.seg-chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  padding: 6px 12px;
  cursor: pointer;
  font-family: inherit;
}
.seg-chip.on {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: #4338ca;
  font-weight: 600;
}
.seg-count {
  font-size: 11px;
  background: #e8eaf0;
  color: #64748b;
  border-radius: 8px;
  padding: 0 6px;
}
.seg-chip.on .seg-count {
  background: #c7d2fe;
  color: #3730a3;
}
.mobile-list {
  display: flex;
  flex-direction: column;
}
</style>
