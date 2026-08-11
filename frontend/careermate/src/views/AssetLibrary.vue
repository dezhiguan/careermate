<template>
  <div class="asset-page">
    <header class="asset-head">
      <h1 class="asset-title">资产</h1>
      <span class="asset-badge">这里的内容长期保留，换对话也不会丢</span>
      <button type="button" class="ask-chip" @click="router.push('/chat')">问小职：我给字节那份简历</button>
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
      <div v-if="resumeVersions.length > 0" class="asset-subbar">
        <input v-model="resumeSearch" class="asset-search" type="search" placeholder="🔍 搜简历版本（名称 / 岗位）">
        <label class="asset-sort">排序
          <select v-model="resumeSort" class="asset-sort-sel">
            <option value="recent">最近更新</option>
            <option value="name">名称</option>
          </select>
        </label>
      </div>
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
              <span v-else class="row-tag base">base</span>
              <span v-if="v.origin === 'MANUAL_EDIT'" class="row-tag gray">手改</span>
              <span v-if="v.exported" class="row-tag exported">已导出 ✓</span>
              <span v-if="v.reusedCount > 0" class="row-tag reused">用于 {{ v.reusedCount }} 机会</span>
              <span class="row-time">{{ formatDate(v.createdAt) }}</span>
            </div>
          </div>
          <div class="row-actions">
            <button class="row-btn reuse" @click="reuseVersion(v)">复用</button>
            <button class="row-btn" @click="preview(v.versionId)">查看</button>
            <button class="row-btn" :disabled="busy" @click="exportPdf(v)">PDF</button>
            <button class="row-btn" :disabled="busy" @click="exportWord(v)">Word</button>
            <button class="row-btn danger" :disabled="busy" @click="removeVersion(v.versionId)">删除</button>
          </div>
        </article>
      </div>
      <div v-if="isDesktop && resumePages > 1" class="pager">
        <button class="pg" :disabled="resumePage === 1" @click="resumePage--">‹</button>
        <span class="pg on">{{ resumePage }}</span>
        <span class="pg-total">/ {{ resumePages }}</span>
        <button class="pg" :disabled="resumePage === resumePages" @click="resumePage++">›</button>
      </div>
    </section>

    <!-- 面试记录 -->
    <section v-else-if="activeTab === 'interview'" class="asset-body">
      <div class="itype-bar">
        <button
          v-for="opt in visibleInterviewTypes"
          :key="opt.value"
          type="button"
          class="itype-chip"
          :class="{ on: interviewType === opt.value }"
          @click="setInterviewType(opt.value)"
        >{{ opt.label }}</button>
      </div>
      <p v-if="loadingInterview" class="asset-loading">加载中…</p>
      <p v-else-if="filteredInterview.length === 0" class="asset-empty">{{ interviewEmptyText }}</p>
      <div v-else class="asset-list">
        <article v-for="s in pagedInterview" :key="s.id" class="asset-row">
          <div class="row-main">
            <div class="row-name">🎤 {{ s.title || '面试训练' }}</div>
            <div class="row-meta">
              <span v-if="s.sessionType === 'REAL'" class="row-tag real">真实复盘</span>
              <span class="row-tag" :class="interviewDone(s) ? 'exported' : 'gray'">{{ interviewProgressLabel(s) }}</span>
              <span v-if="interviewScore(s) != null" class="row-tag score">得分 {{ interviewScore(s) }}</span>
              <span v-if="s.weakness" class="row-tag weak">弱项·{{ s.weakness }}</span>
              <span class="row-time">{{ formatDate(s.createdAt || s.updatedAt) }}</span>
            </div>
          </div>
          <div class="row-actions">
            <button type="button" class="row-act primary" @click="continueInterview(s)">
              {{ interviewDone(s) ? '查看复盘' : (interviewAnswered(s) > 0 ? '继续训练' : '开始训练') }}
            </button>
            <button type="button" class="row-act" @click="renameInterview(s)">重命名</button>
            <button type="button" class="row-act danger" @click="removeInterview(s)">删除</button>
          </div>
        </article>
      </div>
      <div v-if="isDesktop && interviewPages > 1" class="pager">
        <button class="pg" :disabled="interviewPage === 1" @click="interviewPage--">‹</button>
        <span class="pg on">{{ interviewPage }}</span>
        <span class="pg-total">/ {{ interviewPages }}</span>
        <button class="pg" :disabled="interviewPage === interviewPages" @click="interviewPage++">›</button>
      </div>
    </section>

    <!-- 八股题库（个人：收录平台题 + 手写答案，跨机会复用） -->
    <section v-else-if="activeTab === 'eightlegged'" class="asset-body">
      <div class="qbank-toolbar">
        <div class="qbank-skills">
          <button
            type="button"
            class="qbank-chip"
            :class="{ on: studySkill === '' }"
            @click="selectSkill('')"
          >
            全部
          </button>
          <button
            v-for="s in skillTags"
            :key="s.tag"
            type="button"
            class="qbank-chip"
            :class="{ on: studySkill === s.tag, custom: !s.preset }"
            @click="selectSkill(s.tag)"
          >
            {{ s.tag }}
            <span v-if="s.count" class="qbank-chip-n">{{ s.count }}</span>
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

      <p v-if="!loadingStudy && !studySkill && untaggedCount" class="qbank-hint">
        还有 {{ untaggedCount }} 道题没打标签，只能在「全部」里看到；编辑时补个标签就能按方向筛。
      </p>

      <p v-if="loadingStudy" class="asset-empty">加载中…</p>
      <p v-else-if="studyNotes.length === 0" class="asset-empty">
        {{ studySkill || studyKeyword.trim()
          ? '当前筛选条件下没有题。换个标签或清掉关键词试试。'
          : '还没有收录的题。点「＋ 收录题目」把高频题和你的手写答案存进来，所有岗位都能复用。' }}
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

      <div v-if="isDesktop && studyPages > 1" class="pager">
        <button class="pg" :disabled="studyPage === 1" @click="goStudyPage(studyPage - 1)">‹</button>
        <span class="pg on">{{ studyPage }}</span>
        <span class="pg-total">/ {{ studyPages }}</span>
        <button class="pg" :disabled="studyPage === studyPages" @click="goStudyPage(studyPage + 1)">›</button>
      </div>
    </section>

    <!-- 薪资行情（内联检索面板） -->
    <section v-else class="asset-body">
      <div class="sal-query">
        <label class="sal-field">岗位
          <select v-model="salRole" class="sal-sel">
            <option v-for="r in SAL_ROLES" :key="r" :value="r">{{ r }}</option>
          </select>
        </label>
        <label class="sal-field">城市
          <select v-model="salCity" class="sal-sel">
            <option v-for="c in SAL_CITIES" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label class="sal-field">经验
          <select v-model="salYears" class="sal-sel">
            <option v-for="y in SAL_YEARS" :key="y" :value="y">{{ y }}</option>
          </select>
        </label>
        <button class="qbank-add" :disabled="salLoading" @click="querySalary">查询</button>
      </div>

      <p v-if="salLoading" class="asset-loading">查询中…</p>
      <div v-else-if="salData" class="sal-result">
        <div class="sal-dist">
          <div class="sal-cell"><span class="sal-k">P25</span><span class="sal-v">{{ salData.p25 || '—' }}</span></div>
          <div class="sal-cell hl"><span class="sal-k">P50</span><span class="sal-v">{{ salData.p50 || '—' }}</span></div>
          <div class="sal-cell"><span class="sal-k">P75</span><span class="sal-v">{{ salData.p75 || '—' }}</span></div>
          <span v-if="salData.trend" class="sal-trend">{{ salData.trend }}</span>
        </div>
        <div v-if="salSkills.length" class="sal-heat">
          <div class="sal-heat-title">岗位技能热度 Top{{ salSkills.length }}</div>
          <div v-for="s in salSkills" :key="s.rank" class="heat-row">
            <span class="heat-name">{{ s.name }}</span>
            <span class="heat-bar-wrap"><span class="heat-bar" :style="{ width: skillHeatWidth(s.rank) }" /></span>
            <span v-if="s.growth" class="heat-growth">{{ s.growth }}</span>
          </div>
        </div>
        <p v-if="salData.aiSummary" class="sal-tip">💡 {{ salData.aiSummary }}</p>
        <button class="ph-btn" @click="bringSalaryToChat">带入小职薪资焦点 →</button>
      </div>
      <p v-else class="asset-empty">选岗位 + 城市，点「查询」看 P25/P50/P75 与谈薪参考。</p>
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
          <input
            v-model="noteForm.skillTag"
            class="note-input"
            list="qbank-skill-options"
            placeholder="选一个已有标签，或直接敲新的（如 Kafka）"
          >
          <datalist id="qbank-skill-options">
            <option v-for="opt in skillOptions" :key="opt" :value="opt"></option>
          </datalist>

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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listVersions, getVersion, downloadVersionPdf, downloadVersionDocx, deleteVersion } from '../api/resumeVersion'
import { listInterviewSessions, getKbQuestions, renameInterviewSession, deleteInterviewSession } from '../api/interview'
import { listStudyNotes, listStudySkills, saveStudyNote, deleteStudyNote } from '../api/study'
import { getSalaryInsight, getSkillTrends } from '../api/market'
import { renderMarkdown } from '../utils/markdown'

const router = useRouter()
const activeTab = ref('resume')
const error = ref('')
const busy = ref(false)
// 分页分平台：桌面点击页码 / 移动端整列滚动看全部
const isDesktop = ref(false)
function updateIsDesktop() {
  isDesktop.value = typeof window !== 'undefined' && window.innerWidth >= 900
}

const resumeVersions = ref([])
const loadingResume = ref(false)
const resumePage = ref(1)
const resumeSort = ref('recent')
const resumeSearch = ref('')
const PAGE_SIZE = 6

const interviewSessions = ref([])
const loadingInterview = ref(false)
const interviewLoaded = ref(false)
const interviewPage = ref(1)

const previewOpen = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')

// 八股题库
// 预置标签只作为接口不可用时的兜底；正常走 /study/notes/skills，
// 返回「预置 ∪ 用户自建」，保证任何已存在的标签都有筛选入口（含平台题带过来的分类）。
const FALLBACK_SKILL_TAGS = [
  'AI大模型', 'RAG', 'Agent',
  'Java并发', 'JVM', 'MySQL', 'Redis',
  '算法', '系统设计', '行为面',
]
const STUDY_SIZE = 8
const studyNotes = ref([])
const studyTotal = ref(0)
const studyPage = ref(1)
const studySkill = ref('')
const studyKeyword = ref('')
const loadingStudy = ref(false)
const studyLoaded = ref(false)
const studyPages = computed(() => Math.max(1, Math.ceil(studyTotal.value / STUDY_SIZE)))

// [{ tag, count, preset }]，服务端已按展示顺序排好（预置在前，自建按题数倒序在后）
const skillTags = ref(FALLBACK_SKILL_TAGS.map((tag) => ({ tag, count: 0, preset: true })))
const untaggedCount = ref(0)
// 收录弹窗的标签候选：筛选面上的标签全都可选，也允许自己敲新的
const skillOptions = computed(() => skillTags.value.map((s) => s.tag))

// 薪资行情内联面板
const SAL_ROLES = ['Java后端', '前端', 'Python', 'Go', '算法', '测试', '产品经理']
const SAL_CITIES = ['广州', '北京', '上海', '深圳', '杭州', '成都']
const SAL_YEARS = ['不限', '1-3年', '3-5年', '5-10年', '10年以上']
const salRole = ref('Java后端')
const salCity = ref('广州')
const salYears = ref('不限')
const salData = ref(null)
const salSkills = ref([])
const salLoading = ref(false)

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

const sortedResume = computed(() => {
  const kw = resumeSearch.value.trim().toLowerCase()
  let list = resumeVersions.value.slice()
  if (kw) {
    list = list.filter((v) =>
      String(v.versionName || '').toLowerCase().includes(kw)
      || String(v.targetJdLabel || '').toLowerCase().includes(kw))
  }
  if (resumeSort.value === 'name') {
    return list.sort((a, b) => String(a.versionName || '').localeCompare(String(b.versionName || ''), 'zh'))
  }
  return list.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
})
const resumePages = computed(() => Math.max(1, Math.ceil(sortedResume.value.length / PAGE_SIZE)))
const pagedResume = computed(() => {
  if (!isDesktop.value) return sortedResume.value
  const start = (resumePage.value - 1) * PAGE_SIZE
  return sortedResume.value.slice(start, start + PAGE_SIZE)
})

const INTERVIEW_TYPES = [
  { value: 'ALL', label: '全部' },
  { value: 'REAL', label: '真实复盘' },
  { value: 'MOCK', label: '模拟面试' },
]
const interviewType = ref('ALL')
function setInterviewType(v) {
  interviewType.value = v
  interviewPage.value = 1
}
const filteredInterview = computed(() => {
  if (interviewType.value === 'ALL') return interviewSessions.value
  return interviewSessions.value.filter((s) => (s.sessionType || 'MOCK') === interviewType.value)
})
const interviewPages = computed(() => Math.max(1, Math.ceil(filteredInterview.value.length / PAGE_SIZE)))
const pagedInterview = computed(() => {
  if (!isDesktop.value) return filteredInterview.value
  const start = (interviewPage.value - 1) * PAGE_SIZE
  return filteredInterview.value.slice(start, start + PAGE_SIZE)
})
function interviewScore(s) {
  const v = s?.score ?? s?.averageScore
  return v == null ? null : v
}

function interviewAnswered(s) {
  return Number(s?.answeredQuestions ?? 0) || 0
}
function interviewTotal(s) {
  return Number(s?.totalQuestions ?? 0) || 0
}
function interviewDone(s) {
  return s?.status === 'COMPLETED'
}
/** 把状态说成「下一步该干什么」，而不是抛一个 ACTIVE 给用户 */
function interviewProgressLabel(s) {
  if (interviewDone(s)) return '已完成'
  const answered = interviewAnswered(s)
  const total = interviewTotal(s)
  if (answered === 0) return total > 0 ? `未开始 · ${total} 题` : '未开始'
  return total > 0 ? `已答 ${answered}/${total}` : `已答 ${answered}`
}

// 真实复盘目前没有任何创建入口，一条记录都没有时不展示这个死筛选项
const hasRealInterview = computed(() =>
  interviewSessions.value.some((s) => (s.sessionType || 'MOCK') === 'REAL'))
const visibleInterviewTypes = computed(() =>
  hasRealInterview.value ? INTERVIEW_TYPES : INTERVIEW_TYPES.filter((o) => o.value !== 'REAL'))
const interviewEmptyText = computed(() => {
  if (interviewType.value === 'REAL') return '还没有真实面试复盘记录。'
  return '还没有面试记录。让小职来一轮模拟面试，记录会存到这里。'
})

function continueInterview(s) {
  if (!s?.id) return
  router.push({ path: '/interview', query: { session: s.id } })
}

async function renameInterview(s) {
  if (!s?.id) return
  const next = window.prompt('重命名这次训练', s.title || '')
  if (next == null) return
  const title = next.trim()
  if (!title || title === s.title) return
  try {
    await renameInterviewSession(s.id, title)
    s.title = title
  } catch (e) {
    error.value = e?.message || '重命名失败'
  }
}

async function removeInterview(s) {
  if (!s?.id) return
  if (!window.confirm(`删除「${s.title || '面试训练'}」？删除后无法恢复。`)) return
  try {
    await deleteInterviewSession(s.id)
    interviewSessions.value = interviewSessions.value.filter((x) => x.id !== s.id)
    if (pagedInterview.value.length === 0 && interviewPage.value > 1) interviewPage.value--
  } catch (e) {
    error.value = e?.message || '删除失败'
  }
}

function reuseVersion(v) {
  if (!v?.versionId) return
  router.push({ path: '/chat', query: { reuse: v.versionId, reuseName: v.versionName || '这份简历' } })
}

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
  if (key === 'eightlegged' && !studyLoaded.value) {
    loadStudy()
    loadSkills()
  }
}

async function loadStudy() {
  loadingStudy.value = true
  error.value = ''
  try {
    const data = await listStudyNotes({
      skill: studySkill.value || undefined,
      keyword: studyKeyword.value.trim() || undefined,
      page: isDesktop.value ? studyPage.value : 1,
      size: isDesktop.value ? STUDY_SIZE : 50,
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

// 标签面与题目列表分开取：切标签/翻页只重拉列表，标签面只在题库内容变化后刷新
async function loadSkills() {
  try {
    const data = await listStudySkills()
    const tags = Array.isArray(data?.tags) ? data.tags : []
    if (tags.length) {
      skillTags.value = tags.map((t) => ({
        tag: String(t?.tag || ''),
        count: Number(t?.count || 0),
        preset: t?.preset !== false,
      })).filter((t) => t.tag)
    }
    untaggedCount.value = Number(data?.untagged || 0)
    // 当前选中的自建标签被删空后其 chip 会消失，此时退回「全部」，避免停在一个点不到的筛选态
    if (studySkill.value && !skillOptions.value.includes(studySkill.value)) {
      studySkill.value = ''
      await reloadStudy()
    }
  } catch {
    // 标签面拿不到不该挡住题库本身，保留兜底预置项
  }
}

function reloadStudy() {
  studyPage.value = 1
  loadStudy()
}

function selectSkill(tag) {
  studySkill.value = tag || ''
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

// 平台题库的 category 只有 技术/行为/HR 三档：行为/HR 就是「行为面」这一格，
// 「技术」太粗（几乎覆盖所有技术题），当不了筛选维度，留空让用户自己选具体方向。
const COARSE_CATEGORY = '技术'

function pickBrowse(q) {
  noteForm.value.question = q?.question || ''
  const category = (q?.category || '').trim()
  if (category && category !== COARSE_CATEGORY && !noteForm.value.skillTag) {
    noteForm.value.skillTag = category
  }
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
    await loadSkills()
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
    await loadSkills()
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

async function querySalary() {
  salLoading.value = true
  error.value = ''
  try {
    const [salary, trends] = await Promise.allSettled([
      getSalaryInsight({ role: salRole.value, city: salCity.value, years: salYears.value === '不限' ? undefined : salYears.value }),
      getSkillTrends({ role: salRole.value, city: salCity.value }),
    ])
    salData.value = salary.status === 'fulfilled' ? salary.value : null
    const skills = trends.status === 'fulfilled' ? (trends.value?.skills || []) : []
    salSkills.value = Array.isArray(skills) ? skills.slice(0, 6) : []
    if (salary.status === 'rejected') error.value = salary.reason?.message || '薪资查询失败'
  } finally {
    salLoading.value = false
  }
}

// 技能热度条宽度：按排名，Top1 最宽
function skillHeatWidth(rank) {
  const total = salSkills.value.length || 1
  const r = Number(rank) || total
  return `${Math.round(((total - r + 1) / total) * 100)}%`
}

function bringSalaryToChat() {
  router.push({ path: '/chat', query: { focus: 'salary', role: salRole.value, city: salCity.value } })
}

function goMarket() {
  router.push('/market')
}

function shortJd(label) {
  const s = String(label || '')
  return s.length > 28 ? s.slice(0, 28) + '…' : s
}
function statusLabel(s) {
  // ACTIVE 此前没有映射，会把后端枚举原样透给用户
  return { ACTIVE: '未完成', COMPLETED: '已完成', IN_PROGRESS: '进行中', CREATED: '待开始' }[s] || s
}
function formatDate(raw) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const days = Math.floor((now - d) / 86400000)
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days} 天前`
  // 跨年必须带年份，否则 8/11 有歧义
  if (d.getFullYear() !== now.getFullYear()) {
    return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' })
  }
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

onMounted(() => {
  updateIsDesktop()
  window.addEventListener('resize', updateIsDesktop, { passive: true })
  loadResume()
})
onBeforeUnmount(() => window.removeEventListener('resize', updateIsDesktop))
</script>

<style scoped>
.asset-page { padding: 20px 24px; max-width: 900px; margin: 0 auto; background: #F5F6F8; min-height: 100%; }
.asset-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.ask-chip { margin-left: auto; display: inline-flex; align-items: center; font-size: 12px; background: #fff; border: 1px solid #E8EAF0; border-radius: 16px; padding: 5px 12px; color: #5C6472; cursor: pointer; font-family: inherit; }
.asset-title { margin: 0; font-size: 20px; font-weight: 700; color: #1A1D26; }
.asset-badge { font-size: 11px; font-weight: 600; color: #0da76a; background: #e6f6ef; border-radius: 12px; padding: 3px 10px; }
.asset-tabs { display: flex; gap: 2px; border-bottom: 1px solid #e2e8f0; margin-bottom: 16px; }
.asset-tab { font-size: 13.5px; padding: 9px 15px; color: #64748b; border: 0; background: transparent; border-bottom: 2px solid transparent; margin-bottom: -1px; cursor: pointer; }
.asset-tab.on { color: #4E5BEF; border-bottom-color: #4E5BEF; font-weight: 700; }
.tab-count { font-size: 11px; color: #94a3b8; margin-left: 4px; }
.asset-error { color: #E5484D; }
.asset-loading, .asset-empty { color: #64748b; font-size: 14px; padding: 20px 0; }
.asset-list { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 2px rgba(20,24,40,.05); }
.asset-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #f1f5f9; gap: 12px; }
.asset-row:last-child { border-bottom: 0; }
.row-name { font-size: 13.5px; font-weight: 600; color: #1A1D26; }
.row-meta { display: flex; gap: 8px; align-items: center; margin-top: 4px; }
.row-tag { font-size: 10.5px; color: #4E5BEF; background: #eef0fe; border-radius: 7px; padding: 1px 8px; }
.row-tag.gray { color: #64748b; background: #f1f5f9; }
.row-tag.base { color: #475569; background: #f1f5f9; }
.row-tag.mock { color: #64748b; background: #f1f5f9; }
.row-tag.real { color: #0DA76A; background: #E6F6EF; }
.row-tag.weak { color: #DB9A2D; background: #FBF3E2; }
.row-tag.exported { color: #0DA76A; background: #E6F6EF; }
.row-tag.reused { color: #4E5BEF; background: #EEF0FE; }
.row-tag.score { color: #0DA76A; background: #dcfce7; }
.itype-bar { display: flex; gap: 8px; margin-bottom: 12px; }
.itype-chip { font-size: 12px; color: #5C6472; background: #F1F3F7; border: 1px solid transparent; border-radius: 999px; padding: 4px 12px; cursor: pointer; }
.itype-chip.on { color: #4E5BEF; background: #EEF0FE; border-color: #D8DCFB; font-weight: 600; }
.asset-subbar { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
.asset-search { flex: 1; min-width: 0; max-width: 320px; font-size: 12.5px; padding: 6px 12px; border: 1px solid #E8EAF0; border-radius: 8px; background: #F5F6F8; outline: none; }
.asset-search:focus { border-color: #D8DCFB; background: #fff; }
.asset-sort { font-size: 12px; color: #64748b; display: inline-flex; align-items: center; gap: 6px; }
.asset-sort-sel { border: 1px solid #e2e8f0; border-radius: 8px; padding: 4px 8px; font-size: 12px; color: #334155; background: #fff; font-family: inherit; cursor: pointer; }
.row-btn.reuse { color: #4338ca; border-color: #c7d2fe; background: #eef2ff; }
.row-time { font-size: 11px; color: #94a3b8; }
.row-actions { display: flex; gap: 6px; flex-shrink: 0; }
.row-act { font-size: 11.5px; color: #5C6472; background: #F1F3F7; border: 1px solid transparent; border-radius: 7px; padding: 4px 10px; cursor: pointer; white-space: nowrap; }
.row-act:hover { background: #E7EAF1; }
.row-act.primary { color: #4E5BEF; background: #EEF0FE; border-color: #D8DCFB; font-weight: 600; }
.row-act.primary:hover { background: #E2E6FD; }
.row-act.danger:hover { color: #DC2626; background: #FEE2E2; }
.row-actions { display: flex; gap: 6px; flex: 0 0 auto; }
.row-btn { font-size: 12px; border: 1px solid #e2e8f0; border-radius: 8px; padding: 5px 11px; background: #fff; color: #334155; cursor: pointer; }
.row-btn.danger { color: #E5484D; }
.row-btn:disabled { opacity: .5; cursor: not-allowed; }
.pager { display: flex; gap: 8px; align-items: center; justify-content: center; margin-top: 16px; }
.pg { min-width: 30px; height: 30px; border: 1px solid #e2e8f0; border-radius: 9px; background: #fff; color: #334155; cursor: pointer; }
.pg.on { background: #4E5BEF; color: #fff; border-color: #4E5BEF; font-weight: 700; }
.pg:disabled { opacity: .4; cursor: not-allowed; }
.pg-total { font-size: 12px; color: #94a3b8; }
.asset-placeholder { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 28px 24px; text-align: center; box-shadow: 0 1px 2px rgba(20,24,40,.05); }
.sal-query { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 14px; }
.sal-field { font-size: 12px; color: #64748b; display: inline-flex; align-items: center; gap: 6px; }
.sal-sel { border: 1px solid #e2e8f0; border-radius: 8px; padding: 6px 10px; font-size: 13px; color: #334155; background: #fff; font-family: inherit; cursor: pointer; }
.sal-result { background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 16px; box-shadow: 0 1px 2px rgba(20,24,40,.05); }
.sal-dist { display: flex; align-items: center; gap: 10px; }
.sal-cell { flex: 1; text-align: center; background: #F5F6F8; border: 1px solid #eef2f7; border-radius: 10px; padding: 10px 6px; }
.sal-cell.hl { background: #eef2ff; border-color: #c7d2fe; }
.sal-k { display: block; font-size: 11px; color: #94a3b8; }
.sal-v { display: block; font-size: 16px; font-weight: 700; color: #1A1D26; margin-top: 2px; }
.sal-cell.hl .sal-v { color: #4338ca; }
.sal-trend { flex: 0 0 auto; font-size: 12px; color: #0DA76A; background: #dcfce7; border-radius: 8px; padding: 4px 10px; }
.sal-tip { margin: 12px 0; font-size: 13px; line-height: 1.6; color: #475569; }
.sal-heat { margin-top: 14px; }
.sal-heat-title { font-size: 12px; font-weight: 700; color: #64748b; margin-bottom: 8px; }
.heat-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.heat-name { flex: 0 0 84px; font-size: 12px; color: #334155; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.heat-bar-wrap { flex: 1; height: 8px; background: #f1f5f9; border-radius: 999px; overflow: hidden; }
.heat-bar { display: block; height: 100%; background: linear-gradient(90deg, #8B5CF6, #4E5BEF); border-radius: 999px; }
.heat-growth { flex: 0 0 auto; font-size: 11px; color: #0DA76A; }
.ph-title { font-weight: 700; font-size: 15px; color: #1A1D26; }
.ph-text { color: #64748b; font-size: 13px; line-height: 1.7; margin: 10px 0 14px; }
.ph-btn { background: linear-gradient(135deg, #4E5BEF, #8B5CF6); color: #fff; border: 0; border-radius: 10px; padding: 8px 18px; font-size: 13px; font-weight: 600; cursor: pointer; }
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
/* 用户自建标签（非预置）用虚线边框区分，避免和平台给的默认方向混为一谈 */
.qbank-chip.custom { border-style: dashed; }
.qbank-chip-n { margin-left: 4px; font-size: 11px; color: #94a3b8; }
.qbank-hint { margin: 0 0 10px; font-size: 12px; color: #94a3b8; }
.qbank-chip.on .qbank-chip-n { color: #6366f1; }
.qbank-actions { display: flex; gap: 8px; align-items: center; }
.qbank-search { border: 1px solid #e2e8f0; border-radius: 8px; padding: 6px 10px; font-size: 12px; outline: none; font-family: inherit; }
.qbank-add { background: linear-gradient(135deg, #4E5BEF, #8B5CF6); color: #fff; border: 0; border-radius: 8px; padding: 7px 14px; font-size: 12px; font-weight: 600; cursor: pointer; }
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
.qbank-mini.danger { color: #E5484D; border-color: #fecaca; }
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
