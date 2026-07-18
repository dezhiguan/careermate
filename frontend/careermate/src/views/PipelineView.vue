<template>
  <div class="pipeline-view">
    <header class="mhead">
      <span class="mhead-t">准备</span>
      <span class="mhead-sub">{{ board?.total || 0 }} 个在办 · 阶段由对话自动推断</span>
      <button class="ask-chip" type="button" @click="router.push('/chat')">问小职：我最近投得怎么样</button>
      <button class="refresh-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </header>

    <p v-if="error" class="pipeline-error">{{ error }}</p>
    <p v-else-if="loading && !board" class="pipeline-loading">加载中…</p>

    <!-- 暂存区：收藏但还没动的 JD（不占看板，一键转为机会） -->
    <section v-if="board && savedJobs.length" class="holding">
      <div class="holding-head">📌 暂存区 · {{ savedJobs.length }}<span class="holding-hint">收藏但还没动，一键转为机会</span></div>
      <div class="holding-list">
        <div v-for="job in savedJobs" :key="job.id" class="holding-card">
          <div class="holding-main">
            <div class="holding-co">{{ job.company || '未知公司' }}</div>
            <div class="holding-role">{{ job.roleTitle || '岗位' }}</div>
          </div>
          <button class="holding-btn promote" :disabled="savedBusyId === job.jdDocId" @click="promoteSaved(job)">转为机会</button>
          <button class="holding-btn" :disabled="savedBusyId === job.jdDocId" @click="removeSaved(job)">移除</button>
        </div>
      </div>
    </section>

    <p v-if="board && board.total === 0 && !savedJobs.length" class="pipeline-empty">
      还没有在办的投递机会。去「机会」选一个岗位点「定制简历」，或收藏几个 JD 进暂存区。
    </p>

    <!-- 桌面：五列看板 -->
    <div v-else-if="board && isDesktop" class="board" data-testid="pipeline-board">
      <section
        v-for="col in board.columns"
        :key="col.stage"
        class="board-col"
        :class="[colClass(col.stage), { 'drag-over': dragOverStage === col.stage }]"
        @dragover.prevent="dragOverStage = col.stage"
        @dragleave="dragOverStage === col.stage && (dragOverStage = '')"
        @drop.prevent="onDrop(col.stage)"
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
            :class="{ opening: openingId === app.id, dragging: draggingId === app.id }"
            draggable="true"
            @dragstart="onDragStart(app)"
            @dragend="onDragEnd"
            @click="openLine(app)"
          >
            <div class="app-co">{{ cardName(app) }}</div>
            <div class="app-activity">{{ activityText(app) }}</div>
            <div v-if="app.resumeVersionCount > 0 || app.needsSalaryNegotiation || app.resumeVersionId || app.interviewCount > 0" class="app-mt">
              <span v-if="app.resumeVersionCount > 0" class="tagx gray">简历v{{ app.resumeVersionCount }}</span>
              <span v-else-if="app.resumeVersionId" class="tagx gray">简历已挂</span>
              <span v-if="app.interviewCount > 0" class="tagx gray">面经×{{ app.interviewCount }}</span>
              <span v-if="app.needsSalaryNegotiation" class="tagx warn">待谈薪</span>
            </div>
            <div class="app-ctl" @click.stop>
              <select
                class="stage-select"
                :value="app.stage"
                :disabled="busyId === app.id"
                @change="onStageChange(app, $event)"
              >
                <option v-for="s in board.columns" :key="s.stage" :value="s.stage">{{ s.label }}</option>
              </select>
              <button class="archive-btn" type="button" @click="onRename(app)">
                改名
              </button>
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
            <div class="app-co">{{ cardName(app) }}</div>
            <div class="app-activity">{{ activityText(app) }}</div>
            <div v-if="app.resumeVersionCount > 0 || app.needsSalaryNegotiation || app.resumeVersionId || app.interviewCount > 0" class="app-mt">
              <span v-if="app.resumeVersionCount > 0" class="tagx gray">简历v{{ app.resumeVersionCount }}</span>
              <span v-else-if="app.resumeVersionId" class="tagx gray">简历已挂</span>
              <span v-if="app.interviewCount > 0" class="tagx gray">面经×{{ app.interviewCount }}</span>
              <span v-if="app.needsSalaryNegotiation" class="tagx warn">待谈薪</span>
            </div>
            <div class="app-ctl" @click.stop>
              <select
                class="stage-select"
                :value="app.stage"
                :disabled="busyId === app.id"
                @change="onStageChange(app, $event)"
              >
                <option v-for="s in board.columns" :key="s.stage" :value="s.stage">{{ s.label }}</option>
              </select>
              <button class="archive-btn" type="button" @click="onRename(app)">
                改名
              </button>
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
import { getPipelineBoard, updateApplicationStage, archiveApplication, renameApplication } from '../api/pipeline'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'
import { listSavedJobs, promoteJob, unsaveJob } from '../api/savedJobs'
import { cardDistinguishers } from '../utils/cardDistinguisher'

const router = useRouter()
const board = ref(null)
const savedJobs = ref([])
const loading = ref(false)
const error = ref('')
const busyId = ref(null)
const openingId = ref(null)
const savedBusyId = ref(null)

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

// 同「公司·职位」多次出现时补区分项：职级 > 产品线 > 城市 > 序号（设计 v2.3）
const dedupSuffix = computed(() => {
  const all = []
  for (const col of board.value?.columns || []) {
    for (const app of col.applications || []) all.push(app)
  }
  return cardDistinguishers(all, {
    getId: (a) => a.id,
    getCompany: (a) => a.company,
    getRole: (a) => a.roleTitle,
    getCity: (a) => a.city,
  })
})
function cardName(app) {
  if (app.displayName && app.displayName.trim()) return app.displayName.trim()
  const base = `${app.company || '未知公司'} · ${app.roleTitle || '岗位'}`
  const suffix = dedupSuffix.value[app.id]
  return suffix ? `${base} · ${suffix}` : base
}
async function onRename(app) {
  const current = app.displayName || cardName(app)
  const next = window.prompt('卡片改名（留空恢复默认名）', current)
  if (next === null) return
  try {
    await renameApplication(app.id, next.trim())
    app.displayName = next.trim() || null
  } catch (e) {
    error.value = e?.message || '改名失败'
  }
}
function avatarChar(app) {
  const c = (app.company || '公').trim()
  return c.charAt(0).toUpperCase()
}
function activityText(app) {
  const label = app.activity || app.stageLabel || ''
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
    const [boardData, saved] = await Promise.all([getPipelineBoard(), listSavedJobs()])
    board.value = boardData
    savedJobs.value = saved
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

async function promoteSaved(job) {
  if (!job?.jdDocId || savedBusyId.value) return
  savedBusyId.value = job.jdDocId
  try {
    await promoteJob(job.jdDocId)
    await load()
  } catch (e) {
    error.value = e?.message || '转为机会失败'
  } finally {
    savedBusyId.value = null
  }
}

async function removeSaved(job) {
  if (!job?.jdDocId || savedBusyId.value) return
  savedBusyId.value = job.jdDocId
  try {
    await unsaveJob(job.jdDocId)
    savedJobs.value = savedJobs.value.filter((j) => j.jdDocId !== job.jdDocId)
  } catch (e) {
    error.value = e?.message || '移除失败'
  } finally {
    savedBusyId.value = null
  }
}

async function onStageChange(app, evt) {
  const stage = evt?.target?.value
  if (!stage || stage === app.stage) return
  await moveStage(app, stage)
}

// #4 Web 看板拖拽改阶段
const draggingId = ref(null)
const draggingApp = ref(null)
const dragOverStage = ref('')
function onDragStart(app) {
  draggingId.value = app.id
  draggingApp.value = app
}
function onDragEnd() {
  draggingId.value = null
  draggingApp.value = null
  dragOverStage.value = ''
}
async function onDrop(stage) {
  const app = draggingApp.value
  dragOverStage.value = ''
  draggingId.value = null
  draggingApp.value = null
  if (app && stage && stage !== app.stage) {
    await moveStage(app, stage)
  }
}

async function moveStage(app, stage) {
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
  min-height: 100%;
  background: #F5F6F8;
  padding: 0 0 20px;
  --line: #E8EAF0; --line2: #F1F3F7; --ink: #1A1D26; --ink2: #5C6472; --ink3: #9AA2AF;
  --brand: #4E5BEF; --brand-soft: #EEF0FE; --brand-line: #D8DCFB;
  --good: #0DA76A; --good-soft: #E6F6EF; --warn: #DB9A2D; --warn-soft: #FBF3E2; --bad: #E5484D;
}

/* 头部 */
.mhead { display:flex; align-items:center; gap:12px; padding:14px 20px; background:#fff; border-bottom:1px solid var(--line); flex-wrap:wrap; }
.mhead-t { font-size:16px; font-weight:700; color:var(--ink); }
.mhead-sub { font-size:13px; color:var(--ink2); }
.ask-chip { margin-left:auto; display:inline-flex; align-items:center; font-size:12px; background:#fff; border:1px solid var(--line); border-radius:16px; padding:5px 12px; color:var(--ink2); cursor:pointer; font-family:inherit; }
.refresh-btn { border:1px solid var(--line); background:#fff; color:var(--ink2); border-radius:9px; padding:6px 12px; font-size:12px; cursor:pointer; font-family:inherit; }
.refresh-btn:disabled { opacity:.5; cursor:not-allowed; }

.pipeline-error { color:var(--bad); padding:14px 20px; font-size:13px; }
.pipeline-loading, .pipeline-empty { padding:24px 20px; color:var(--ink3); font-size:13px; }

/* 暂存区 (stash) */
.holding { margin:16px 20px 0; border:1.5px dashed var(--line); border-radius:12px; padding:12px 14px; background:#FCFCFE; }
.holding-head { font-size:12.5px; font-weight:700; color:var(--ink2); margin-bottom:10px; }
.holding-hint { font-weight:400; color:var(--ink3); margin-left:8px; font-size:11.5px; }
.holding-list { display:flex; flex-wrap:wrap; gap:8px; }
.holding-card { display:flex; align-items:center; gap:8px; background:#fff; border:1px solid var(--line); border-radius:10px; padding:8px 10px; }
.holding-main { min-width:0; }
.holding-co { font-size:13px; font-weight:600; color:var(--ink); }
.holding-role { font-size:11px; color:var(--ink3); }
.holding-btn { flex-shrink:0; border:1px solid var(--line); border-radius:8px; padding:4px 10px; font-size:12px; background:#fff; color:var(--ink2); cursor:pointer; font-family:inherit; }
.holding-btn.promote { color:var(--brand); border-color:var(--brand-line); background:var(--brand-soft); font-weight:600; }
.holding-btn:disabled { opacity:.5; cursor:not-allowed; }

/* 桌面看板 */
.board { display:grid; grid-template-columns:repeat(5,1fr); gap:12px; padding:18px 20px; }
.board-col { background:transparent; padding:0; }
.col-head { display:flex; justify-content:space-between; align-items:center; padding:2px 6px 10px; font-size:12.5px; font-weight:700; color:var(--ink2); }
.col-count { color:var(--ink3); font-weight:600; background:var(--line2); border-radius:10px; padding:0 8px; font-size:11px; }
.col-body { min-height:60px; }

.app-card { position:relative; background:#fff; border:1px solid var(--line); border-radius:12px; padding:11px 13px; margin-bottom:10px; box-shadow:0 1px 2px rgba(20,24,40,.05),0 3px 10px rgba(20,24,40,.05); cursor:pointer; transition:border-color .15s, box-shadow .15s; }
.app-card[draggable="true"] { cursor: grab; }
.app-card.dragging { opacity: .5; }
.board-col.drag-over { background: #EEF0FE; outline: 2px dashed #D8DCFB; outline-offset: -2px; border-radius: 12px; }
.app-card:hover { box-shadow:0 2px 8px rgba(78,91,239,.12); }
.app-card.opening { opacity:.6; }
.col-hot .app-card { border-color:#F5C6C8; }
.col-gold .app-card { border-color:#F0DCB2; }
.app-co { font-weight:700; font-size:12.5px; color:var(--ink); }
.app-activity { font-size:11px; color:var(--ink2); margin-top:1px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.app-mt { display:flex; gap:5px; margin-top:8px; flex-wrap:wrap; }
.tagx { font-size:10.5px; border-radius:8px; padding:1px 8px; font-weight:600; }
.tagx.gray { background:var(--line2); color:var(--ink3); }
.tagx.brand { background:var(--brand-soft); color:var(--brand); }
.tagx.warn { background:var(--warn-soft); color:var(--warn); }

/* 卡片控件：桌面 hover 揭示，保持卡面干净 */
.app-ctl { display:flex; gap:6px; margin-top:9px; opacity:0; max-height:0; overflow:hidden; transition:opacity .15s; }
.app-card:hover .app-ctl { opacity:1; max-height:60px; }
.stage-select { flex:1; font-size:11px; border:1px solid var(--line); border-radius:7px; padding:4px 6px; color:var(--ink2); background:#fff; }
.archive-btn { font-size:11px; border:1px solid var(--line); border-radius:7px; padding:4px 10px; background:#fff; color:var(--ink3); cursor:pointer; font-family:inherit; }
.col-empty { text-align:center; color:#CBD3DE; font-size:12px; margin:6px 0; }

/* 移动端阶段分段 + 列表 */
.stage-seg { display:flex; gap:6px; overflow-x:auto; -webkit-overflow-scrolling:touch; padding:14px 16px 6px; }
.seg-chip { flex:0 0 auto; display:inline-flex; align-items:center; gap:5px; border:1px solid var(--line); border-radius:999px; background:#fff; color:var(--ink2); font-size:12.5px; padding:6px 12px; cursor:pointer; font-family:inherit; }
.seg-chip.on { background:var(--brand-soft); border-color:var(--brand-line); color:var(--brand); font-weight:600; }
.seg-count { font-size:11px; background:var(--line2); color:var(--ink3); border-radius:8px; padding:0 6px; }
.seg-chip.on .seg-count { background:var(--brand-line); color:var(--brand); }
.mobile-list { display:flex; flex-direction:column; padding:0 16px; }
.mobile-list .app-ctl { opacity:1; max-height:none; }

@media (min-width: 900px) {
  .board, .mhead, .holding { max-width:1180px; margin-left:auto; margin-right:auto; }
}
</style>
