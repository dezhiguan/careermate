<template>
  <div class="asset-page">
    <header class="asset-head">
      <h1 class="asset-title">资产</h1>
      <span class="asset-badge">全部归你 · 与会话生死无关</span>
    </header>

    <div class="asset-tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        type="button"
        class="asset-tab"
        :class="{ on: activeTab === t.key }"
        @click="switchTab(t.key)"
      >
        {{ t.label }}<span v-if="t.count != null" class="tab-count">{{ t.count }}</span>
      </button>
    </div>

    <p v-if="error" class="asset-error">{{ error }}</p>

    <!-- 简历版本 -->
    <section v-if="activeTab === 'resume'" class="asset-body">
      <p v-if="loadingResume" class="asset-loading">加载中…</p>
      <p v-else-if="resumeVersions.length === 0" class="asset-empty">
        还没有定制简历。去「机会」选个岗位点「定制简历」，产出会归档到这里。
      </p>
      <div v-else class="asset-list">
        <article v-for="v in pagedResume" :key="v.versionId" class="asset-row">
          <div class="row-main">
            <div class="row-name">📄 {{ v.versionName || '简历版本' }}</div>
            <div class="row-meta">
              <span v-if="v.targetJdLabel" class="row-tag">→ {{ shortJd(v.targetJdLabel) }}</span>
              <span v-if="v.origin === 'MANUAL_EDIT'" class="row-tag gray">手改</span>
              <span class="row-time">{{ formatDate(v.createdAt) }}</span>
            </div>
          </div>
          <div class="row-actions">
            <button class="row-btn" @click="preview(v.versionId)">查看</button>
            <button class="row-btn" :disabled="busy" @click="exportPdf(v)">PDF</button>
            <button class="row-btn" :disabled="busy" @click="exportWord(v)">Word</button>
            <button class="row-btn danger" :disabled="busy" @click="removeVersion(v.versionId)">删除</button>
          </div>
        </article>
      </div>
      <div v-if="resumePages > 1" class="pager">
        <button class="pg" :disabled="resumePage === 1" @click="resumePage--">‹</button>
        <span class="pg on">{{ resumePage }}</span>
        <span class="pg-total">/ {{ resumePages }}</span>
        <button class="pg" :disabled="resumePage === resumePages" @click="resumePage++">›</button>
      </div>
    </section>

    <!-- 面试记录 -->
    <section v-else-if="activeTab === 'interview'" class="asset-body">
      <p v-if="loadingInterview" class="asset-loading">加载中…</p>
      <p v-else-if="interviewSessions.length === 0" class="asset-empty">还没有面试记录。让小职来一轮模拟面试，记录会存到这里。</p>
      <div v-else class="asset-list">
        <article v-for="s in interviewSessions" :key="s.id" class="asset-row">
          <div class="row-main">
            <div class="row-name">🎤 {{ s.title || s.company || '模拟面试' }}</div>
            <div class="row-meta">
              <span v-if="s.status" class="row-tag gray">{{ statusLabel(s.status) }}</span>
              <span v-if="s.score != null" class="row-tag">得分 {{ s.score }}</span>
              <span class="row-time">{{ formatDate(s.createdAt || s.updatedAt) }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- 八股题库（个人：收录平台题 + 手写答案，跨机会复用） -->
    <section v-else-if="activeTab === 'eightlegged'" class="asset-body">
      <div class="qbank-toolbar">
        <div class="qbank-skills">
          <button
            v-for="s in SKILL_TAGS"
            :key="s"
            type="button"
            class="qbank-chip"
            :class="{ on: studySkill === (s === '全部' ? '' : s) }"
            @click="selectSkill(s)"
          >
            {{ s }}
          </button>
        </div>
        <div class="qbank-actions">
          <input
            v-model="studyKeyword"
            class="qbank-search"
            type="search"
            placeholder="搜题目 / 答案"
            @keydown.enter="reloadStudy"
          >
          <button class="qbank-add" @click="openAddNote">＋ 收录题目</button>
        </div>
      </div>

      <p v-if="loadingStudy" class="asset-empty">加载中…</p>
      <p v-else-if="studyNotes.length === 0" class="asset-empty">
        还没有收录的题。点「＋ 收录题目」把高频题和你的手写答案存进来，所有岗位都能复用。
      </p>
      <ul v-else class="qbank-list">
        <li v-for="n in studyNotes" :key="n.id" class="qbank-item">
          <div class="qbank-q-row">
            <span class="qbank-q">{{ n.question }}</span>
            <span v-if="n.skillTag" class="qbank-tag">{{ n.skillTag }}</span>
          </div>
          <p v-if="n.answer" class="qbank-a">{{ n.answer }}</p>
          <p v-else class="qbank-a qbank-a-empty">（还没写答案）</p>
          <div class="qbank-item-actions">
            <button class="qbank-mini" @click="openEditNote(n)">编辑</button>
            <button class="qbank-mini danger" @click="removeNote(n)">删除</button>
          </div>
        </li>
      </ul>

      <div v-if="studyPages > 1" class="pager">
        <button class="pg" :disabled="studyPage === 1" @click="goStudyPage(studyPage - 1)">‹</button>
        <span class="pg on">{{ studyPage }}</span>
        <span class="pg-total">/ {{ studyPages }}</span>
        <button class="pg" :disabled="studyPage === studyPages" @click="goStudyPage(studyPage + 1)">›</button>
      </div>
    </section>

    <!-- 薪资行情（检索面板，跳市场） -->
    <section v-else class="asset-body">
      <div class="asset-placeholder">
        <div class="ph-title">薪资 / 行情参考</div>
        <p class="ph-text">薪资行情是<b>检索面板</b>，不是个人列表——按岗位+城市实时查询即可。</p>
        <button class="ph-btn" @click="goMarket">去市场查行情 →</button>
      </div>
    </section>

    <!-- 收录/编辑题目 -->
    <div v-if="noteEditorOpen" class="prev-overlay" @click.self="closeNoteEditor">
      <div class="prev-panel note-editor">
        <div class="prev-head">
          <span>{{ noteForm.id ? '编辑题目' : '收录题目' }}</span>
          <button class="prev-close" @click="closeNoteEditor">×</button>
        </div>
        <div class="note-body">
          <label class="note-label">题目</label>
          <textarea v-model="noteForm.question" class="note-input" rows="2" placeholder="如：Redis 持久化 RDB 和 AOF 的区别"></textarea>

          <div class="note-browse">
            <input
              v-model="browseKeyword"
              class="note-input note-browse-input"
              placeholder="从平台题库找题（输关键词，如 缓存一致性）"
              @keydown.enter="browsePlatform"
            >
            <button class="qbank-mini" :disabled="browsing" @click="browsePlatform">搜题</button>
          </div>
          <ul v-if="browseResults.length" class="browse-list">
            <li v-for="(q, i) in browseResults" :key="i" class="browse-item" @click="pickBrowse(q)">
              {{ q.question }}
            </li>
          </ul>

          <label class="note-label">技能标签</label>
          <input v-model="noteForm.skillTag" class="note-input" placeholder="如 Redis / Java并发 / 系统设计">

          <label class="note-label">手写答案</label>
          <textarea v-model="noteForm.answer" class="note-input" rows="6" placeholder="用自己的话写答案，跨岗位复用"></textarea>

          <div class="note-actions">
            <button class="qbank-mini" @click="closeNoteEditor">取消</button>
            <button class="qbank-add" :disabled="savingNote || !noteForm.question.trim()" @click="saveNote">保存</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 简历预览 -->
    <div v-if="previewOpen" class="prev-overlay" @click.self="previewOpen = false">
      <div class="prev-panel">
        <div class="prev-head">
          <span>{{ previewTitle }}</span>
          <button class="prev-close" @click="previewOpen = false">×</button>
        </div>
        <div class="prev-body markdown-preview" v-html="previewHtml"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listVersions, getVersion, downloadVersionPdf, downloadVersionDocx, deleteVersion } from '../api/resumeVersion'
import { listInterviewSessions, getKbQuestions } from '../api/interview'
import { listStudyNotes, saveStudyNote, deleteStudyNote } from '../api/study'
import { renderMarkdown } from '../utils/markdown'

const router = useRouter()
const activeTab = ref('resume')
const error = ref('')
const busy = ref(false)

const resumeVersions = ref([])
const loadingResume = ref(false)
const resumePage = ref(1)
const PAGE_SIZE = 6

const interviewSessions = ref([])
const loadingInterview = ref(false)
const interviewLoaded = ref(false)

const previewOpen = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')

// 八股题库
const SKILL_TAGS = ['全部', 'Java并发', 'JVM', 'MySQL', 'Redis', '算法', '系统设计', '行为面']
const STUDY_SIZE = 8
const studyNotes = ref([])
const studyTotal = ref(0)
const studyPage = ref(1)
const studySkill = ref('')
const studyKeyword = ref('')
const loadingStudy = ref(false)
const studyLoaded = ref(false)
const studyPages = computed(() => Math.max(1, Math.ceil(studyTotal.value / STUDY_SIZE)))

const noteEditorOpen = ref(false)
const savingNote = ref(false)
const noteForm = ref({ id: null, question: '', skillTag: '', answer: '' })
const browseKeyword = ref('')
const browseResults = ref([])
const browsing = ref(false)

const tabs = computed(() => [
  { key: 'resume', label: '简历版本', count: resumeVersions.value.length || null },
  { key: 'interview', label: '面试记录', count: interviewLoaded.value ? interviewSessions.value.length : null },
  { key: 'eightlegged', label: '八股题库', count: studyLoaded.value ? studyTotal.value : null },
  { key: 'salary', label: '薪资行情', count: null },
])

const resumePages = computed(() => Math.max(1, Math.ceil(resumeVersions.value.length / PAGE_SIZE)))
const pagedResume = computed(() => {
  const start = (resumePage.value - 1) * PAGE_SIZE
  return resumeVersions.value.slice(start, start + PAGE_SIZE)
})

async function loadResume() {
  loadingResume.value = true
  error.value = ''
  try {
    const data = await listVersions()
    resumeVersions.value = Array.isArray(data) ? data : (data?.items || [])
  } catch (e) {
    error.value = e?.message || '加载简历版本失败'
  } finally {
    loadingResume.value = false
  }
}

async function loadInterview() {
  if (interviewLoaded.value) return
  loadingInterview.value = true
  try {
    const data = await listInterviewSessions()
    interviewSessions.value = Array.isArray(data) ? data : (data?.items || data?.sessions || [])
    interviewLoaded.value = true
  } catch (e) {
    error.value = e?.message || '加载面试记录失败'
  } finally {
    loadingInterview.value = false
  }
}

function switchTab(key) {
  activeTab.value = key
  if (key === 'interview') loadInterview()
  if (key === 'eightlegged' && !studyLoaded.value) loadStudy()
}

async function loadStudy() {
  loadingStudy.value = true
  error.value = ''
  try {
    const data = await listStudyNotes({
      skill: studySkill.value || undefined,
      keyword: studyKeyword.value.trim() || undefined,
      page: studyPage.value,
      size: STUDY_SIZE,
    })
    studyNotes.value = Array.isArray(data?.items) ? data.items : []
    studyTotal.value = Number(data?.total || 0)
    studyLoaded.value = true
  } catch (e) {
    error.value = e?.message || '加载题库失败'
  } finally {
    loadingStudy.value = false
  }
}

function reloadStudy() {
  studyPage.value = 1
  loadStudy()
}

function selectSkill(s) {
  studySkill.value = s === '全部' ? '' : s
  reloadStudy()
}

function goStudyPage(p) {
  if (p < 1 || p > studyPages.value || p === studyPage.value) return
  studyPage.value = p
  loadStudy()
}

function openAddNote() {
  noteForm.value = { id: null, question: '', skillTag: studySkill.value || '', answer: '' }
  browseKeyword.value = ''
  browseResults.value = []
  noteEditorOpen.value = true
}

function openEditNote(n) {
  noteForm.value = { id: n.id, question: n.question || '', skillTag: n.skillTag || '', answer: n.answer || '' }
  browseKeyword.value = ''
  browseResults.value = []
  noteEditorOpen.value = true
}

function closeNoteEditor() {
  noteEditorOpen.value = false
}

async function browsePlatform() {
  const kw = browseKeyword.value.trim()
  if (!kw || browsing.value) return
  browsing.value = true
  try {
    const data = await getKbQuestions(kw)
    browseResults.value = Array.isArray(data?.questions) ? data.questions.slice(0, 8) : []
  } catch (e) {
    error.value = e?.message || '题库搜索失败'
  } finally {
    browsing.value = false
  }
}

function pickBrowse(q) {
  noteForm.value.question = q?.question || ''
  if (q?.category && !noteForm.value.skillTag) noteForm.value.skillTag = q.category
  if (q?.answer && !noteForm.value.answer) noteForm.value.answer = q.answer
  browseResults.value = []
}

async function saveNote() {
  if (!noteForm.value.question.trim() || savingNote.value) return
  savingNote.value = true
  try {
    await saveStudyNote({
      question: noteForm.value.question.trim(),
      skillTag: noteForm.value.skillTag.trim() || undefined,
      answer: noteForm.value.answer.trim() || undefined,
    })
    noteEditorOpen.value = false
    await loadStudy()
  } catch (e) {
    error.value = e?.message || '保存失败'
  } finally {
    savingNote.value = false
  }
}

async function removeNote(n) {
  if (!n?.id) return
  try {
    await deleteStudyNote(n.id)
    // 删末页最后一条时回退一页
    if (studyNotes.value.length === 1 && studyPage.value > 1) studyPage.value -= 1
    await loadStudy()
  } catch (e) {
    error.value = e?.message || '删除失败'
  }
}

async function preview(versionId) {
  try {
    const detail = await getVersion(versionId)
    previewTitle.value = detail?.versionName || '简历'
    previewHtml.value = renderMarkdown(detail?.contentMarkdown || '')
    previewOpen.value = true
  } catch (e) {
    error.value = e?.message || '加载失败'
  }
}

async function exportPdf(v) {
  busy.value = true
  try { await downloadVersionPdf(v.versionId, v.versionName) } catch (e) { error.value = e?.message || 'PDF 下载失败' } finally { busy.value = false }
}
async function exportWord(v) {
  busy.value = true
  try { await downloadVersionDocx(v.versionId, v.versionName) } catch (e) { error.value = e?.message || 'Word 下载失败' } finally { busy.value = false }
}
async function removeVersion(versionId) {
  busy.value = true
  try {
    await deleteVersion(versionId)
    await loadResume()
    if (resumePage.value > resumePages.value) resumePage.value = resumePages.value
  } catch (e) {
    error.value = e?.message || '删除失败'
  } finally { busy.value = false }
}

function goMarket() {
  router.push('/market')
}

function shortJd(label) {
  const s = String(label || '')
  return s.length > 28 ? s.slice(0, 28) + '…' : s
}
function statusLabel(s) {
  return { COMPLETED: '已完成', IN_PROGRESS: '进行中', CREATED: '待开始' }[s] || s
}
function formatDate(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

onMounted(loadResume)
</script>

<style scoped>
.asset-page { padding: 20px 24px; max-width: 900px; margin: 0 auto; }
.asset-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.asset-title { margin: 0; font-size: 20px; font-weight: 700; color: #0f172a; }
.asset-badge { font-size: 11px; font-weight: 600; color: #0da76a; background: #e6f6ef; border-radius: 12px; padding: 3px 10px; }
.asset-tabs { display: flex; gap: 2px; border-bottom: 1px solid #e2e8f0; margin-bottom: 16px; }
.asset-tab { font-size: 13.5px; padding: 9px 15px; color: #64748b; border: 0; background: transparent; border-bottom: 2px solid transparent; margin-bottom: -1px; cursor: pointer; }
.asset-tab.on { color: #4f46e5; border-bottom-color: #4f46e5; font-weight: 700; }
.tab-count { font-size: 11px; color: #94a3b8; margin-left: 4px; }
.asset-error { color: #dc2626; }
.asset-loading, .asset-empty { color: #64748b; font-size: 14px; padding: 20px 0; }
.asset-list { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 2px rgba(20,24,40,.05); }
.asset-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #f1f5f9; gap: 12px; }
.asset-row:last-child { border-bottom: 0; }
.row-name { font-size: 13.5px; font-weight: 600; color: #0f172a; }
.row-meta { display: flex; gap: 8px; align-items: center; margin-top: 4px; }
.row-tag { font-size: 10.5px; color: #4f46e5; background: #eef0fe; border-radius: 7px; padding: 1px 8px; }
.row-tag.gray { color: #64748b; background: #f1f5f9; }
.row-time { font-size: 11px; color: #94a3b8; }
.row-actions { display: flex; gap: 6px; flex: 0 0 auto; }
.row-btn { font-size: 12px; border: 1px solid #e2e8f0; border-radius: 8px; padding: 5px 11px; background: #fff; color: #334155; cursor: pointer; }
.row-btn.danger { color: #dc2626; }
.row-btn:disabled { opacity: .5; cursor: not-allowed; }
.pager { display: flex; gap: 8px; align-items: center; justify-content: center; margin-top: 16px; }
.pg { min-width: 30px; height: 30px; border: 1px solid #e2e8f0; border-radius: 9px; background: #fff; color: #334155; cursor: pointer; }
.pg.on { background: #4f46e5; color: #fff; border-color: #4f46e5; font-weight: 700; }
.pg:disabled { opacity: .4; cursor: not-allowed; }
.pg-total { font-size: 12px; color: #94a3b8; }
.asset-placeholder { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 28px 24px; text-align: center; box-shadow: 0 1px 2px rgba(20,24,40,.05); }
.ph-title { font-weight: 700; font-size: 15px; color: #0f172a; }
.ph-text { color: #64748b; font-size: 13px; line-height: 1.7; margin: 10px 0 14px; }
.ph-btn { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border: 0; border-radius: 10px; padding: 8px 18px; font-size: 13px; font-weight: 600; cursor: pointer; }
.prev-overlay { position: fixed; inset: 0; z-index: 420; background: rgba(15,23,42,.4); display: flex; justify-content: flex-end; }
.prev-panel { width: min(560px, 92vw); height: 100%; background: #eef0f5; display: flex; flex-direction: column; }
.prev-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: #fff; border-bottom: 1px solid #e2e8f0; font-weight: 700; font-size: 14px; }
.prev-close { font-size: 20px; border: 0; background: transparent; color: #94a3b8; cursor: pointer; }
.prev-body { flex: 1; overflow: auto; padding: 24px; }
.prev-body :deep(.markdown-preview), .prev-body { background: #fff; border-radius: 12px; }
.prev-body { font-size: 13px; line-height: 1.7; color: #1a1d26; }

/* 八股题库 */
.qbank-toolbar { display: flex; flex-wrap: wrap; gap: 10px; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.qbank-skills { display: flex; flex-wrap: wrap; gap: 6px; }
.qbank-chip { border: 1px solid #e2e8f0; background: #fff; color: #64748b; border-radius: 999px; padding: 4px 12px; font-size: 12px; cursor: pointer; font-family: inherit; }
.qbank-chip.on { background: #eef2ff; border-color: #c7d2fe; color: #4338ca; font-weight: 600; }
.qbank-actions { display: flex; gap: 8px; align-items: center; }
.qbank-search { border: 1px solid #e2e8f0; border-radius: 8px; padding: 6px 10px; font-size: 12px; outline: none; font-family: inherit; }
.qbank-add { background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; border: 0; border-radius: 8px; padding: 7px 14px; font-size: 12px; font-weight: 600; cursor: pointer; }
.qbank-add:disabled { opacity: .5; cursor: not-allowed; }
.qbank-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.qbank-item { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px 14px; }
.qbank-q-row { display: flex; justify-content: space-between; gap: 10px; align-items: flex-start; }
.qbank-q { font-size: 14px; font-weight: 600; color: #1a1d26; }
.qbank-tag { flex-shrink: 0; font-size: 11px; color: #4338ca; background: #eef2ff; border-radius: 6px; padding: 2px 8px; }
.qbank-a { margin: 8px 0 0; font-size: 13px; line-height: 1.6; color: #475569; white-space: pre-wrap; }
.qbank-a-empty { color: #94a3b8; }
.qbank-item-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
.qbank-mini { border: 1px solid #e2e8f0; background: #fff; color: #475569; border-radius: 8px; padding: 5px 12px; font-size: 12px; cursor: pointer; font-family: inherit; }
.qbank-mini.danger { color: #b91c1c; border-color: #fecaca; }
.qbank-mini:disabled { opacity: .5; cursor: not-allowed; }
.note-editor { width: min(560px, 96vw); }
.note-body { flex: 1; overflow: auto; padding: 18px; display: flex; flex-direction: column; gap: 6px; background: #eef0f5; }
.note-label { font-size: 12px; font-weight: 600; color: #64748b; margin-top: 8px; }
.note-input { border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px 10px; font-size: 13px; font-family: inherit; outline: none; background: #fff; resize: vertical; }
.note-browse { display: flex; gap: 8px; align-items: center; }
.note-browse-input { flex: 1; }
.browse-list { list-style: none; margin: 4px 0 0; padding: 0; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; max-height: 160px; overflow: auto; }
.browse-item { padding: 8px 10px; font-size: 12px; color: #334155; cursor: pointer; border-bottom: 1px solid #f1f5f9; }
.browse-item:hover { background: #eef2ff; }
.note-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; }
</style>
