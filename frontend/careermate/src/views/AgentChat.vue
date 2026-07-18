<template>
  <div class="chat-page" :data-workspace-id="workspaceId || undefined">
    <div v-if="isMobile && drawerOpen" class="chat-drawer-overlay" @click.self="drawerOpen = false">
      <aside class="chat-drawer">
        <div class="drawer-user">
          <div class="drawer-avatar">
            {{ avatarInitial }}
            <span class="drawer-online-dot" />
          </div>
          <div>
            <div class="drawer-name">{{ userDisplayName }}</div>
            <div class="drawer-status">已登录</div>
          </div>
        </div>
        <nav class="drawer-nav">
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/opportunity')">机会</button>
          <button type="button" class="drawer-link active" @click="drawerOpen = false">小职 · 当前</button>
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/interview')">面试准备</button>
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/market')">市场</button>
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/mine')">我的</button>
        </nav>
        <div class="drawer-footer">
          <button type="button" class="drawer-footer-link" @click="authStore.logout()">退出登录</button>
        </div>
      </aside>
    </div>

    <div class="chat-layout">
      <aside v-if="!isMobile" class="chat-sessions" @scroll="onSessionsScroll">
        <button type="button" class="cs-new" @click="startNewChat">＋ 新对话</button>
        <div class="cs-section">
          <div class="cs-title">我的准备</div>
          <button
            v-for="ln in recentLines"
            :key="ln.sessionId"
            type="button"
            class="cs-item"
            :class="{ active: activeSessionId === ln.sessionId }"
            @click="openSessionLine(ln.sessionId)"
          >
            <span class="cs-avatar">{{ (ln.title || '职').charAt(0) }}</span>
            <span class="cs-name">{{ ln.title }}</span>
          </button>
          <div v-if="!recentLines.length" class="cs-empty">还没有 JD 会话线</div>
        </div>
        <div class="cs-section">
          <div class="cs-title">通用对话</div>
          <button
            v-for="s in displayChatSessions"
            :key="s.sessionId"
            type="button"
            class="cs-item"
            :class="{ active: activeSessionId === s.sessionId }"
            @click="openSessionLine(s.sessionId)"
          >
            <span class="cs-name">💬 {{ chatSessionTitle(s) }}</span>
          </button>
          <div v-if="!leftChatSessions.length" class="cs-empty">暂无</div>
          <div v-else-if="leftVisibleCount < leftChatSessions.length" class="cs-more">下滑加载更多…</div>
        </div>
      </aside>
      <div class="chat-main">
        <div class="chat-header">
          <div class="header-left">
            <button
              v-if="isMobile"
              type="button"
              class="hamburger-btn"
              aria-label="打开菜单"
              @click="drawerOpen = true"
            >
              ☰
            </button>
            <div>
              <div class="header-title">小职</div>
              <div class="header-sub" :class="workspaceSubClass">
                <template v-if="workspaceInfo">● {{ workspaceSubText }}</template>
                <template v-else>● 求职军师 · 一句话办求职事</template>
              </div>
            </div>
          </div>
          <div class="header-actions">
            <button
              v-if="isMobile && workspaceVersions.length && !resumeViewerOpen"
              type="button"
              class="canvas-chip-btn"
              @click="openCanvasChip"
            >
              📄 简历 ▸
            </button>
            <div
              v-if="currentPathMode"
              class="path-mode-chip"
              :class="currentPathMode === 'DEEP' ? 'path-mode-chip--deep' : 'path-mode-chip--fast'"
              :title="currentPathMode === 'DEEP' ? '深度模式：多轮推敲，较慢' : '极速模式：单轮直答'"
            >
              {{ currentPathMode === 'DEEP' ? '🧠 深度模式' : '⚡ 极速' }}
            </div>
            <div v-if="showTraceId && currentTraceId" class="trace-id-chip" title="SkyWalking Trace ID">
              <span class="trace-id-label">Trace ID</span>
              <code class="trace-id-value">{{ currentTraceId }}</code>
              <button type="button" class="trace-id-copy" @click="copyTraceId">复制</button>
            </div>
          </div>
        </div>

        <div v-if="workspaceInfo" class="context-chips-bar">
          <span class="ctx-anchor">🧵</span>
          <span class="ctx-avatar">{{ anchorAvatar }}</span>
          <span
            v-for="(chip, idx) in contextChipList"
            :key="`${chip}-${idx}`"
            class="ctx-chip"
            :class="{ 'ctx-chip--resume': chip.includes('简历') }"
          >
            {{ chip }}
          </span>
          <span v-if="resumeViewerScore != null" class="ctx-chip ctx-chip--score">契合 {{ resumeViewerScore }}</span>
          <span class="ctx-chip ctx-chip--mem" :class="{ 'is-new': memoryStatus === '新对话' }">{{ memoryStatus }}</span>
        </div>

        <button
          v-if="showProfileBanner"
          type="button"
          class="profile-aha-banner"
          @click="router.push('/mine')"
        >
          <span class="profile-aha-main">画像完整度 {{ profileCompleteness }}%</span>
          <span class="profile-aha-sub">补全画像 → AI 匹配更准</span>
        </button>

        <div v-if="globalError" class="global-error">
          <div>{{ globalError }}</div>
          <details v-if="errorDetail" class="error-detail">
            <summary>错误详情</summary>
            <dl>
              <div v-if="errorDetail.status">
                <dt>HTTP</dt>
                <dd>{{ errorDetail.status }}</dd>
              </div>
              <div v-if="errorDetail.code">
                <dt>Code</dt>
                <dd>{{ errorDetail.code }}</dd>
              </div>
              <div v-if="errorDetail.traceId">
                <dt>Trace ID</dt>
                <dd><code>{{ errorDetail.traceId }}</code></dd>
              </div>
              <div v-if="errorDetail.requestId">
                <dt>Request ID</dt>
                <dd><code>{{ errorDetail.requestId }}</code></dd>
              </div>
            </dl>
          </details>
        </div>

        <div class="messages-area" ref="msgContainer">
          <section v-if="recentLines.length && !workspaceId" class="recent-lines">
            <div class="recent-lines-title">继续你的会话线</div>
            <button
              v-for="ln in recentLines"
              :key="ln.sessionId"
              type="button"
              class="recent-line-item"
              @click="resumeLine(ln.sessionId)"
            >
              <span class="recent-line-name">{{ ln.title }}</span>
              <span class="recent-line-time">{{ formatVersionDate(ln.lastActiveAt) }}</span>
            </button>
          </section>

          <section v-if="showZeroStateExample" class="zero-chat-card">
            <div class="zero-chat-label">示例 · 点 chip 开始真实对话</div>
            <div class="zero-chat-bubbles">
              <div class="zero-bubble agent">
                小职：广州 Java 3 年的同学，我可以先帮你看 JD 要求，再把你的简历改成更贴近岗位的一版。
              </div>
              <div class="zero-bubble user">我想尽快知道下一步该做什么。</div>
              <div class="zero-bubble agent">
                小职：可以。我会把 JD 拆成技能、项目证据和面试风险，再给你一版可下载的定制简历。
              </div>
            </div>
            <div class="zero-chip-row">
              <button
                v-for="chip in zeroStatePrompts"
                :key="chip.label"
                type="button"
                class="zero-prompt-chip"
                :disabled="sessionCreating || streamState === 'streaming'"
                @click="sendExamplePrompt(chip.prompt)"
              >
                {{ chip.label }}
              </button>
            </div>
          </section>

          <div v-for="msg in messages" :key="msg.id" class="msg-wrapper">
            <div v-if="msg.role === 'user'" class="msg-row user-row">
              <div class="msg-bubble user-bubble">{{ msg.text }}</div>
            </div>

            <div v-else class="msg-row agent-row">
              <div
                class="msg-bubble agent-bubble"
                :class="{ 'agent-bubble--waiting': isAgentWaiting(msg) }"
              >
                <div v-if="isAgentWaiting(msg)" class="thinking-flag" aria-label="正在思考">
                  <span class="thinking-dot" /><span class="thinking-dot" /><span class="thinking-dot" />
                </div>
                <div v-if="msg.toolCalls?.length" class="tool-call-list">
                  <ToolCallCard
                    v-for="tc in msg.toolCalls"
                    :key="tc.id"
                    :tool="tc"
                  />
                </div>
                <div v-if="msg.text || msg.html" class="md-body">
                  <div v-if="msg.html" v-html="msg.html"></div>
                  <span v-else class="md-plain">{{ msg.text }}</span>
                </div>
                <ChatCard
                  v-if="msg.card"
                  :card="msg.card"
                  :disabled="resumeGenerating"
                  :pdf-downloading="pdfDownloading"
                  :word-downloading="wordDownloading"
                  @action="handleCardAction"
                />
                <div v-if="msg.streaming && msg.text" class="stream-flag">▌</div>
                <div v-if="msg.error" class="stream-error">{{ msg.error }}</div>
              </div>
            </div>
          </div>

        </div>

        <div class="input-area">
          <div class="focus-bar" role="group" aria-label="焦点">
            <span class="focus-label">焦点</span>
            <button
              v-for="f in FOCUS_OPTIONS"
              :key="f.key"
              type="button"
              class="focus-chip"
              :class="{ 'focus-chip--on': activeFocuses.includes(f.key) }"
              @click="toggleFocus(f.key)"
            >
              {{ f.label }}
            </button>
          </div>
          <div v-if="refMenuOpen" class="ref-menu">
            <div class="ref-sect">
              <div class="ref-title">简历版本</div>
              <button
                v-for="v in workspaceVersions"
                :key="v.versionId"
                type="button"
                class="ref-row"
                @click="insertResumeRef(v)"
              >
                📄 {{ v.versionName }}
              </button>
              <div v-if="!workspaceVersions.length" class="ref-empty">暂无简历版本</div>
            </div>
            <div class="ref-sect">
              <div class="ref-title">八股题库</div>
              <button
                v-for="n in refStudyNotes"
                :key="n.id"
                type="button"
                class="ref-row"
                @click="insertStudyRef(n)"
              >
                🎯 {{ n.question }}
              </button>
              <div v-if="!refStudyNotes.length" class="ref-empty">暂无（去资产库收录）</div>
            </div>
          </div>
          <div class="input-row">
            <button
              type="button"
              class="ref-btn"
              :class="{ on: refMenuOpen }"
              title="引用我的简历 / 八股"
              @click="toggleRefMenu"
            >＠</button>
            <input
              v-model="inputText"
              :placeholder="resumeGenerating ? '小职正在为你重写简历...' : '说说你想做什么...'"
              class="chat-input"
              :disabled="resumeGenerating"
              @keydown.enter="sendMessage"
            >
            <button
              type="button"
              class="deep-toggle"
              :class="{ 'deep-toggle--on': deepModeOn }"
              :title="deepModeOn ? '深度模式已开：多轮推敲，较慢' : '开启深度模式：深度定制/模拟面试'"
              @click="deepModeOn = !deepModeOn"
            >🧠 深度</button>
            <button type="button" class="send-btn" :disabled="!canSend" @click="sendMessage">发送</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="jdViewerOpen" class="modal-overlay" @click.self="jdViewerOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <span>JD 详情</span>
          <button type="button" class="modal-close" @click="jdViewerOpen = false">×</button>
        </div>
        <div class="modal-body markdown-preview" v-html="renderMd(jdViewerContent)"></div>
      </div>
    </div>

    <div v-if="resumeViewerOpen" class="canvas-dock" data-testid="resume-canvas" @click.self="resumeViewerOpen = false">
      <div class="canvas-panel">
        <div class="canvas-head">
          <span class="canvas-title">📄 简历</span>
          <select
            v-if="workspaceVersions.length > 1"
            class="canvas-ver"
            :value="activeVersionId"
            @change="switchCanvasVersion($event)"
          >
            <option v-for="v in workspaceVersions" :key="v.versionId" :value="v.versionId">
              {{ v.versionName }}
            </option>
          </select>
          <span v-else class="canvas-vername">{{ resumeViewerTitle }}</span>
          <span class="canvas-spacer" />
          <button
            v-if="prevVersion"
            type="button"
            class="canvas-act"
            :class="{ 'canvas-act-on': diffMode }"
            :disabled="diffLoading"
            @click="toggleDiff"
          >
            {{ diffMode ? '退出对比' : '对比上一版' }}
          </button>
          <button type="button" class="canvas-act" :disabled="pdfDownloading" @click="canvasExportPdf">PDF</button>
          <button type="button" class="canvas-act" :disabled="wordDownloading" @click="canvasExportWord">Word</button>
          <button type="button" class="canvas-act" @click="copyResumeMarkdown(activeVersionId)">复制</button>
          <button type="button" class="canvas-close" @click="resumeViewerOpen = false">×</button>
        </div>
        <div v-if="resumeViewerSummary || resumeViewerScore != null" class="canvas-changes">
          <span class="canvas-changes-kicker">✎ 小职改动</span>
          <span v-if="resumeViewerScore != null" class="canvas-gap">契合 {{ resumeViewerScore }} 分</span>
          <span v-if="resumeViewerNotes.length" class="canvas-gap">改了 {{ resumeViewerNotes.length }} 处</span>
          <span v-if="resumeViewerSummary">{{ resumeViewerSummary }}</span>
        </div>
        <div v-if="hasGap && !diffMode" class="canvas-gap-line">
          <span v-if="resumeGap.hit.length" class="gapl hit" :title="resumeGap.hit.join(' · ')">✓ 命中 {{ resumeGap.hit.length }}</span>
          <span v-if="resumeGap.understated.length" class="gapl mid" :title="resumeGap.understated.join(' · ')">◐ 未突出 {{ resumeGap.understated.length }}：{{ resumeGap.understated.slice(0, 3).join(' · ') }}</span>
          <span v-if="resumeGap.missing.length" class="gapl miss" :title="resumeGap.missing.join(' · ')">✗ 缺 {{ resumeGap.missing.length }}：{{ resumeGap.missing.slice(0, 3).join(' · ') }}</span>
        </div>
        <div v-if="canvasAnnotated.unmatched.length && !diffMode" class="canvas-notes">
          <div
            v-for="(note, idx) in canvasAnnotated.unmatched"
            :key="idx"
            class="canvas-note"
            :class="{ kept: note.state === 'kept', reverting: note.state === 'reverting' }"
          >
            <span class="canvas-note-mark">✎ 小职</span>
            <span class="canvas-note-text">{{ note.text }}</span>
            <span v-if="note.state === 'kept'" class="canvas-note-done">已保留</span>
            <span v-else-if="note.state === 'reverting'" class="canvas-note-done">已请求撤销</span>
            <template v-else>
              <button type="button" class="canvas-note-btn keep" @click="keepNote(note)">保留</button>
              <button type="button" class="canvas-note-btn revert" @click="revertNote(note)">撤销这处</button>
            </template>
          </div>
        </div>
        <div class="canvas-body">
          <div v-if="diffMode" class="canvas-paper canvas-diff">
            <div class="diff-legend">
              <span class="diff-legend-add">＋ 新增</span>
              <span class="diff-legend-del">－ 删除</span>
              <span class="diff-legend-hint">对比「{{ prevVersion?.versionName || '上一版' }}」</span>
            </div>
            <div
              v-for="(ln, idx) in diffLines"
              :key="idx"
              class="diff-line"
              :class="`diff-${ln.type}`"
            >
              <span class="diff-gutter">{{ ln.type === 'add' ? '＋' : ln.type === 'del' ? '－' : '' }}</span>
              <span class="diff-text">{{ ln.text || ' ' }}</span>
            </div>
          </div>
          <div v-else class="canvas-paper markdown-preview resume-annotated">
            <div
              v-for="blk in canvasAnnotated.blocks"
              :key="blk.key"
              class="rline"
              :class="{ chg: blk.changes.length }"
            >
              <div class="rline-body" v-html="blk.html"></div>
              <div v-for="(c, ci) in blk.changes" :key="'c' + ci" class="rline-note">
                <span class="rline-note-mark">✎ 小职</span>{{ c.reason || c.text }}
              </div>
              <div v-for="(s, si) in blk.suggestions" :key="'s' + si" class="rline-sug">
                <span class="rline-sug-icon">💡</span>
                <span class="rline-sug-text">{{ s.text }}</span>
                <button type="button" class="rline-sug-btn accept" @click="acceptSuggestion(s)">采纳</button>
                <button type="button" class="rline-sug-btn ignore" @click="ignoreSuggestion(s)">忽略</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="pendingExportFormat" class="modal-overlay" @click.self="pendingExportFormat = ''">
      <div class="modal-panel fact-confirm">
        <div class="modal-header">
          <span>⚠ 导出前终检</span>
          <button type="button" class="modal-close" @click="pendingExportFormat = ''">×</button>
        </div>
        <div class="modal-body">
          <p class="fact-confirm-lead">
            以下内容在你的原始简历 / 画像里没找到出处，导出前请再确认属实——小职不替你担保这些是真的：
          </p>
          <ul class="fact-list">
            <li v-for="(f, i) in factSuspects" :key="i">{{ f }}</li>
          </ul>
          <div class="fact-confirm-actions">
            <button type="button" class="fact-btn-ghost" @click="pendingExportFormat = ''">再改改</button>
            <button type="button" class="fact-btn-primary" @click="confirmExport">确认属实，继续导出</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="versionsDrawerOpen" class="modal-overlay" @click.self="versionsDrawerOpen = false">
      <div class="modal-panel drawer-panel">
        <div class="modal-header">
          <span>本空间简历版本</span>
          <button type="button" class="modal-close" @click="versionsDrawerOpen = false">×</button>
        </div>
        <div class="modal-body versions-list">
          <div v-if="workspaceVersions.length === 0" class="empty-hint">暂无生成版本</div>
          <button
            v-for="v in workspaceVersions"
            :key="v.versionId"
            type="button"
            class="version-item"
            @click="openResumeVersion(v.versionId)"
          >
            <span>{{ v.versionName }}</span>
            <span class="version-date">{{ formatVersionDate(v.createdAt) }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'
import { homeStore } from '../stores/homeStore'
import {
  createAgentSession,
  getAgentSession,
  getAgentStreamStatus,
  getAgentTrace,
  listAgentSessions,
  sendAgentMessageStream,
} from '../api/agent'
import { getWorkspace, getMessages, postAction, openResumeGenerateStreamByEndpoint, listRecentLines, LAST_WORKSPACE_CREATE_KEY } from '../api/workspace'
import { confirmStage } from '../api/pipeline'
import { listStudyNotes } from '../api/study'
import { getOpportunityDetail } from '../api/opportunity'
import { downloadVersionDocx, downloadVersionPdf, getVersion, listVersions } from '../api/resumeVersion'
import { getCareerProfile } from '../api/profile'
import { isCareerTaskToolName, notifyCareerTasksUpdated } from '../utils/agentToolDisplay'
import ToolCallCard from '../components/agent/ToolCallCard.vue'
import ChatCard from '../components/ChatCard.vue'
import { getToolLabel, isBusinessToolName, sanitizeToolSummary } from '../utils/agentToolDisplay'
import { renderMarkdown } from '../utils/markdown'
import { computeProfileCompleteness } from '../utils/profileCompleteness'

const route = useRoute()
const router = useRouter()

function renderMd(text) {
  return renderMarkdown(text)
}

function withMarkdown(msg) {
  const text = msg?.text || ''
  return {
    ...msg,
    text,
    html: text ? renderMd(text) : '',
  }
}

function scheduleMarkdownForMessages(msgs) {
  if (!Array.isArray(msgs) || msgs.length === 0) return
  const pending = msgs.filter((msg) => msg?.role === 'agent' && msg.text && !msg.html)
  if (pending.length === 0) return
  if (pending.length <= 24) {
    for (const msg of pending) {
      msg.html = renderMd(msg.text)
    }
    return
  }
  let index = 0
  const step = () => {
    const batchSize = 6
    for (let n = 0; n < batchSize && index < pending.length; n += 1, index += 1) {
      const msg = pending[index]
      msg.html = renderMd(msg.text)
    }
    if (index < pending.length) {
      requestAnimationFrame(step)
    }
  }
  requestAnimationFrame(step)
}

function normalizeWsId(raw) {
  if (Array.isArray(raw)) return raw[0] || null
  if (raw == null || raw === '') return null
  const text = String(raw).trim()
  return text || null
}

/** Phase 2: workspaceId 将用于 API；Phase 1 仅接收路由参数 */
const workspaceId = computed(() => normalizeWsId(route.params.wsId))

let workspaceLoadSeq = 0

const MOBILE_MAX = 767
const isMobile = ref(false)
const drawerOpen = ref(false)

const inputText = ref('')
const msgContainer = ref(null)
const sessionId = ref('')
const streamState = ref('idle')
const currentPathMode = ref('')
const deepModeOn = ref(false)
const sessionCreating = ref(false)
const globalError = ref('')
// 焦点条：会话内检索信号（可多选），切焦点不新建会话、不中断对话
const FOCUS_OPTIONS = [
  { key: 'resume', label: '改简历' },
  { key: 'jd', label: 'JD分析' },
  { key: 'interview', label: '面试' },
  { key: 'salary', label: '薪资' },
  { key: 'company', label: '公司氛围' },
]
const activeFocuses = ref([])
// @引用资产：简历版本(已有 workspaceVersions) + 八股题
const refMenuOpen = ref(false)
const refStudyNotes = ref([])
async function toggleRefMenu() {
  refMenuOpen.value = !refMenuOpen.value
  if (refMenuOpen.value && !refStudyNotes.value.length) {
    try {
      const data = await listStudyNotes({ page: 1, size: 8 })
      refStudyNotes.value = Array.isArray(data?.items) ? data.items : []
    } catch (e) {
      refStudyNotes.value = []
    }
  }
}
function insertRef(text) {
  inputText.value = (inputText.value ? inputText.value + ' ' : '') + text
  refMenuOpen.value = false
}
function insertResumeRef(v) {
  insertRef(`（参考我的「${v.versionName || '简历'}」）`)
}
function insertStudyRef(n) {
  insertRef(`（参考八股「${n.question}」）`)
}
function toggleFocus(key) {
  const i = activeFocuses.value.indexOf(key)
  if (i >= 0) activeFocuses.value.splice(i, 1)
  else activeFocuses.value.push(key)
}
const errorDetail = ref(null)
const idSeed = ref(0)
const activeStreamController = ref(null)
const activeStreamTimer = ref(null)
const activeAgentMessage = ref(null)
const workspaceInfo = ref(null)
const resumeGenerating = ref(false)
const activeResumeStream = ref(null)
const jdViewerOpen = ref(false)
const jdViewerContent = ref('')
const resumeViewerOpen = ref(false)
const resumeViewerTitle = ref('')
const resumeViewerContent = ref('')
const resumeViewerSummary = ref('')
const activeVersionId = ref('')
const versionsDrawerOpen = ref(false)
const workspaceVersions = ref([])
// Canvas 逐行对比：与"上一版"做行级 diff，高亮小职到底改了哪几行
const diffMode = ref(false)
const diffLoading = ref(false)
const diffLines = ref([])
// 导出前 Critic 终检：当前版本落库的事实核对结果（疑似无出处的强事实）
const resumeViewerFactCheck = ref(null)
const pendingExportFormat = ref('')
// Canvas 逐行改动：小职这次的改动项(✎小职·[说明])，可保留/撤销这处
const resumeViewerNotes = ref([])
const resumeViewerScore = ref(null)
// Canvas gap 行：JD 技能按在简历里出现次数分桶（命中≥2 / 未突出=1 / 缺=0）
const jdSkills = ref([])
const jdSkillsLoadedFor = ref('')
const resumeGap = computed(() => {
  const skills = jdSkills.value || []
  const content = String(resumeViewerContent.value || '').toLowerCase()
  const hit = []
  const understated = []
  const missing = []
  for (const raw of skills) {
    const k = String(raw || '').trim()
    if (!k) continue
    const kl = k.toLowerCase()
    let count = 0
    let idx = content.indexOf(kl)
    while (idx !== -1) {
      count++
      idx = content.indexOf(kl, idx + kl.length)
    }
    if (count === 0) missing.push(k)
    else if (count === 1) understated.push(k)
    else hit.push(k)
  }
  return { hit, understated, missing }
})
const hasGap = computed(() => {
  const g = resumeGap.value
  return g.hit.length || g.understated.length || g.missing.length
})
async function loadJdSkills() {
  const jd = workspaceInfo.value?.jdId
  if (!jd || jdSkillsLoadedFor.value === jd) return
  jdSkillsLoadedFor.value = jd
  try {
    const detail = await getOpportunityDetail(jd)
    jdSkills.value = Array.isArray(detail?.skills) ? detail.skills : []
  } catch (e) {
    jdSkills.value = []
  }
}
// 最近会话线：落地页（无 wsId）时列出用户在推进的 JD 对话线，一键返回续聊
const recentLines = ref([])
// 小职左栏常驻会话列表（桌面）：我的准备(JD线) + 通用对话(CHAT)
const leftChatSessions = ref([])
const activeSessionId = computed(() => workspaceId.value || sessionId.value || '')
const pdfDownloading = ref(false)
const wordDownloading = ref(false)
const currentTraceId = ref('')
const careerProfile = ref({
  targetRole: '',
  targetCity: '',
  seniority: '',
  workMode: '',
  skillKeywords: [],
})

const zeroStatePrompts = [
  { label: '帮我看 JD', prompt: '帮我看 JD，告诉我该怎么判断是否值得投递。' },
  { label: '改我的简历', prompt: '改我的简历，帮我把项目经历写得更贴近目标岗位。' },
  { label: '练一道面试题', prompt: '练一道 Java 后端面试题，并根据我的回答追问。' },
]

const STREAM_UI_IDLE_NOTICE_MS = Number(import.meta.env.VITE_AGENT_STREAM_UI_IDLE_NOTICE_MS || 90000)
const showTraceId = import.meta.env.VITE_SHOW_TRACE_ID === 'true'

function resolveErrorDetail(error) {
  if (!error) return null
  const detail = {
    status: error.status || error.payload?.status || null,
    code: error.code || error.payload?.code || null,
    traceId: error.traceId || error.payload?.traceId || null,
    requestId: error.requestId || null,
  }
  return Object.values(detail).some(Boolean) ? detail : null
}

function setGlobalError(message, error = null) {
  globalError.value = message || error?.message || '系统异常'
  errorDetail.value = resolveErrorDetail(error)
}

function clearGlobalError() {
  globalError.value = ''
  errorDetail.value = null
}

const hasResumeVersion = computed(() => workspaceVersions.value.length > 0)

const profileCompleteness = computed(() => computeProfileCompleteness(careerProfile.value))

const showProfileBanner = computed(() => profileCompleteness.value < 80)

const showZeroStateExample = computed(() => (
  !workspaceInfo.value
  && !sessionId.value
  && messages.value.length === 0
  && !sessionCreating.value
))

const workspaceSubText = computed(() => {
  if (!workspaceInfo.value) return ''
  const type = workspaceInfo.value.workspaceType
  const typeLabel = {
    JD_PREP: 'JD 准备空间',
    INTERVIEW: '面试训练空间',
    MARKET: '市场策略空间',
    RESUME: '简历优化空间',
    GENERAL: '通用对话',
  }[type] || '工作空间'
  if (type === 'JD_PREP') {
    return hasResumeVersion.value ? `${typeLabel} · JD + 简历已加载` : `${typeLabel} · JD 已加载`
  }
  const summary = workspaceInfo.value.contextSummary
  return summary ? `${typeLabel} · ${summary}` : typeLabel
})

const workspaceSubClass = computed(() => {
  if (!workspaceInfo.value) return ''
  if (workspaceInfo.value.workspaceType === 'JD_PREP') {
    return hasResumeVersion.value ? 'header-sub--ready' : 'header-sub--pending'
  }
  return 'header-sub--ready'
})

const contextChipList = computed(() => {
  const chips = workspaceInfo.value?.contextChips
  if (Array.isArray(chips) && chips.length > 0) {
    return chips
  }
  const fallback = []
  if (jdChipLabel.value) fallback.push(jdChipLabel.value)
  if (resumeChipLabel.value) fallback.push(resumeChipLabel.value)
  return fallback
})

// 锚条：公司头像 + 记忆态
const anchorAvatar = computed(() => {
  const src = workspaceInfo.value?.snapshot?.company || workspaceInfo.value?.title || '职'
  return String(src).trim().charAt(0).toUpperCase() || '职'
})
const memoryStatus = computed(() => {
  const real = messages.value.filter((m) => m.role === 'user' || (m.role === 'agent' && (m.text || m.card))).length
  return real > 1 ? '连续' : '新对话'
})

const jdChipLabel = computed(() => {
  const snap = workspaceInfo.value?.jdSnapshot
  if (!snap) return ''
  const parts = [snap.company, snap.title].filter((v) => v && String(v).trim())
  return parts.length ? `📋 ${parts.join(' ')} JD` : ''
})

const resumeChipLabel = computed(() => {
  const latest = workspaceVersions.value?.[0]
  if (!latest?.versionName) return ''
  return `📄 ${latest.versionName}`
})

const messages = ref([defaultWelcomeMessage()])

const canSend = computed(() => (
  !!inputText.value.trim()
  && streamState.value !== 'streaming'
  && !sessionCreating.value
  && !resumeGenerating.value
))

const userDisplayName = computed(() => authStore.state.currentUser?.username || '用户')

const avatarInitial = computed(() => {
  const name = authStore.state.currentUser?.username || '用'
  return name.charAt(0).toUpperCase()
})

function updateViewport() {
  isMobile.value = window.innerWidth <= MOBILE_MAX
  if (!isMobile.value) {
    drawerOpen.value = false
  }
}

function navigateFromDrawer(path) {
  drawerOpen.value = false
  if (route.path !== path) {
    router.push(path)
  }
}

function appendReactiveMessage(message) {
  const reactiveMsg = reactive(message)
  messages.value.push(reactiveMsg)
  return reactiveMsg
}

function isAgentWaiting(msg) {
  return !!msg?.streaming && !msg.text && !msg.html
}

function ensureAgentMessageShape(msg) {
  if (!msg.toolCalls) {
    msg.toolCalls = []
  }
}

function summaryFromTraceRow(row) {
  if (!row?.responseSummary) return ''
  try {
    const parsed = JSON.parse(row.responseSummary)
    return sanitizeToolSummary(parsed?.summary || parsed?.message || '')
  } catch {
    return ''
  }
}

/** 用 splice 替换条目，确保 Vue 能检测到 toolCalls 变更 */
function upsertToolCall(agentMessage, toolName, patch) {
  ensureAgentMessageShape(agentMessage)
  const idx = agentMessage.toolCalls.findIndex((t) => t.toolName === toolName)
  const base =
    idx >= 0
      ? { ...agentMessage.toolCalls[idx] }
      : {
          id: `tool_${toolName}_${idSeed.value++}`,
          toolName,
          status: 'running',
          summary: '',
          success: null,
          errorHint: '',
        }
  const next = { ...base, ...patch }
  if (idx >= 0) {
    agentMessage.toolCalls.splice(idx, 1, next)
  } else {
    agentMessage.toolCalls.push(next)
  }
  return next
}

function syncToolCallsFromServerTraces(agentMessage, traces) {
  if (!agentMessage?.toolCalls?.length || !Array.isArray(traces)) return
  for (const tc of agentMessage.toolCalls) {
    if (tc.status !== 'running') continue
    const row = traces.find((t) => (t.toolName || t.type) === tc.toolName)
    if (!row || !isBusinessToolName(tc.toolName)) continue
    const success = row.status === 'SUCCESS'
    const traceSummary = summaryFromTraceRow(row)
    upsertToolCall(agentMessage, tc.toolName, {
      status: success ? 'success' : 'failed',
      success,
      summary: sanitizeToolSummary(
        traceSummary || tc.summary || (success ? '工具执行完成' : '工具执行失败')
      ),
      errorHint: success
        ? ''
        : sanitizeToolSummary(traceSummary || '工具执行失败，请稍后重试或前往对应页面手动操作。'),
    })
  }
}

function handleToolStart(agentMessage, data) {
  const toolName = data?.toolName || 'unknown'
  upsertToolCall(agentMessage, toolName, {
    status: 'running',
    summary: sanitizeToolSummary(data?.summary || '正在调用工具…'),
    success: null,
    errorHint: '',
  })
  scrollBottom()
}

function handleToolResult(agentMessage, data) {
  const toolName = data?.toolName || 'unknown'
  const success = !!data?.success
  upsertToolCall(agentMessage, toolName, {
    status: success ? 'success' : 'failed',
    success,
    summary: sanitizeToolSummary(data?.summary || (success ? '执行成功' : '执行失败')),
    errorHint: success
      ? ''
      : sanitizeToolSummary(data?.summary || '工具执行失败，请稍后重试或前往对应页面手动操作。'),
  })
  scrollBottom()
}

function finishStreaming(agentMessage) {
  agentMessage.streaming = false
  if (agentMessage.text) {
    agentMessage.html = renderMd(agentMessage.text)
  }
}

function clearStreamWatchdog() {
  if (activeStreamTimer.value) {
    window.clearTimeout(activeStreamTimer.value)
    activeStreamTimer.value = null
  }
}

function markStreamInterrupted(agentMessage, message) {
  if (!agentMessage) return
  streamState.value = 'error'
  finishStreaming(agentMessage)
  finalizeRunningToolCalls(agentMessage, false)
  agentMessage.error = message
  setGlobalError(message)
}

function abortActiveStream(reason = '当前流式请求已取消') {
  clearStreamWatchdog()
  const controller = activeStreamController.value
  if (controller && !controller.signal.aborted) {
    controller.abort(new Error(reason))
  }
  activeStreamController.value = null
}

function startStreamWatchdog(agentMessage) {
  clearStreamWatchdog()
  if (!Number.isFinite(STREAM_UI_IDLE_NOTICE_MS) || STREAM_UI_IDLE_NOTICE_MS <= 0) return
  activeStreamTimer.value = window.setTimeout(() => {
    if (streamState.value !== 'streaming' || !agentMessage?.streaming) return
    const message = `Agent 已超过 ${Math.round(STREAM_UI_IDLE_NOTICE_MS / 1000)} 秒未返回结束事件，仍在等待后端完成。`
    agentMessage.error = message
    setGlobalError(message)
  }, STREAM_UI_IDLE_NOTICE_MS)
}

/** tool_result 偶发丢失时，避免卡片一直停在「执行中」 */
function finalizeRunningToolCalls(agentMessage, success = true) {
  if (!agentMessage?.toolCalls?.length) return
  for (const tc of [...agentMessage.toolCalls]) {
    if (tc.status !== 'running') continue
    upsertToolCall(agentMessage, tc.toolName, {
      status: success ? 'success' : 'failed',
      success,
      summary: sanitizeToolSummary(
        !tc.summary || /^正在/.test(tc.summary)
          ? success
            ? '工具执行完成'
            : '未收到工具结果'
          : tc.summary
      ),
      errorHint: success ? tc.errorHint || '' : tc.errorHint || '工具结果未返回，请重试。',
    })
  }
}

function scrollBottom() {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}

async function copyTraceId() {
  if (!currentTraceId.value) return
  try {
    await navigator.clipboard.writeText(currentTraceId.value)
  } catch {
    // clipboard unavailable
  }
}

function defaultWelcomeMessage() {
  return withMarkdown({
    id: `m_welcome_${idSeed.value++}`,
    role: 'agent',
    text: '你好！你可以直接提问，比如「帮我分析简历」。',
    streaming: false,
    error: '',
    toolCalls: [],
  })
}

function mapServerMessages(serverMessages) {
  if (!Array.isArray(serverMessages) || serverMessages.length === 0) {
    return []
  }
  return serverMessages.map((m) => mapWorkspaceMessage(m))
}

function mapWorkspaceMessage(m) {
  const messageType = (m.messageType || '').toUpperCase()
  const card = m.metadata?.card || null
  const isCard = messageType === 'CARD' && card
  const role = m.role === 'user' ? 'user' : 'agent'
  const text = m.content || ''
  const base = {
    id: `m_${m.id ?? idSeed.value++}`,
    role,
    text,
    card: isCard ? card : null,
    messageType,
    streaming: false,
    error: '',
    toolCalls: [],
  }
  if (role === 'agent') {
    return withMarkdown(base)
  }
  return base
}

function formatVersionDate(value) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadWorkspaceContext(wsId) {
  const normalizedWsId = normalizeWsId(wsId)
  if (!normalizedWsId) return
  const seq = ++workspaceLoadSeq
  sessionCreating.value = true
  clearGlobalError()
  try {
    const [ws, msgs, versions] = await Promise.all([
      getWorkspace(normalizedWsId),
      getMessages(normalizedWsId, { limit: 100 }),
      listVersions(normalizedWsId).catch(() => []),
    ])
    if (seq !== workspaceLoadSeq) return
    workspaceInfo.value = ws
    workspaceVersions.value = versions || ws?.resumeVersions || []
    sessionId.value = normalizedWsId
    const restored = mapServerMessages(msgs)
    messages.value = restored.length > 0 ? restored : [defaultWelcomeMessage()]
    scheduleMarkdownForMessages(messages.value)
    streamState.value = 'idle'
    scrollBottom()
  } catch (e) {
    if (seq !== workspaceLoadSeq) return
    const msg = e?.message || '加载工作空间失败'
    if (msg.includes('工作空间不存在')) {
      let lastCreateWorkspaceId = ''
      try {
        lastCreateWorkspaceId = sessionStorage.getItem(LAST_WORKSPACE_CREATE_KEY) || ''
      } catch {
        // ignore
      }
      console.warn('[workspace] GET 404', {
        routeWsId: route.params.wsId,
        requestWsId: normalizedWsId,
        lastCreateWorkspaceId,
      })
    }
    setGlobalError(msg, e)
    messages.value = [withMarkdown({
      id: `m_err_${Date.now()}`,
      role: 'agent',
      text: globalError.value,
      streaming: false,
      error: globalError.value,
      toolCalls: [],
    })]
  } finally {
    if (seq === workspaceLoadSeq) {
      sessionCreating.value = false
    }
  }
}

async function handleCardAction(actionItem) {
  const action = actionItem?.action
  const payload = actionItem?.payload
  if (!action || !sessionId.value) return

  if (action === 'GENERATE_RESUME' || action === 'RETRY') {
    await requestGenerateResumeConfirmation(payload)
    return
  }
  if (action === 'CONFIRM_PENDING_ACTION') {
    await confirmPendingResumeAction(payload)
    return
  }
  if (action === 'CANCEL_PENDING_ACTION') {
    await cancelPendingResumeAction(payload)
    return
  }
  if (action === 'CONFIRM_STAGE') {
    // Layer-2 一键确认：卡片已乐观置为已推进，这里落库；失败仅提示
    try {
      await confirmStage({
        jdDocId: actionItem?.jdDocId,
        stage: actionItem?.stage,
        company: actionItem?.company,
      })
    } catch (e) {
      globalError.value = e?.message || '流转阶段失败'
    }
    return
  }
  if (action === 'DISMISS_STAGE') {
    return
  }
  if (action === 'VIEW_JD') {
    await openJdViewer(payload || workspaceInfo.value?.jdId)
    return
  }
  if (action === 'VIEW_RESUME') {
    await openResumeVersion(payload)
    return
  }
  if (action === 'DOWNLOAD_PDF') {
    const pl = typeof payload === 'object' && payload !== null
      ? payload
      : { versionId: payload, versionName: '' }
    pdfDownloading.value = true
    try {
      await downloadVersionPdf(pl.versionId, pl.versionName)
    } catch (e) {
      globalError.value = e?.message || 'PDF 下载失败'
    } finally {
      pdfDownloading.value = false
    }
    return
  }
  if (action === 'DOWNLOAD_WORD') {
    const pl = typeof payload === 'object' && payload !== null
      ? payload
      : { versionId: payload, versionName: '' }
    wordDownloading.value = true
    try {
      await downloadVersionDocx(pl.versionId, pl.versionName)
    } catch (e) {
      globalError.value = e?.message || 'Word 下载失败'
    } finally {
      wordDownloading.value = false
    }
    return
  }
  if (action === 'COPY_MARKDOWN') {
    await copyResumeMarkdown(payload)
    return
  }
  if (action === 'NAVIGATE') {
    handleNavigateCardAction(actionItem)
  }
}

const ENTRY_ACTION_PROMPTS = {
  EXPLAIN_MARKET: '请帮我解读当前市场行情',
  NEGOTIATION_SCRIPT: '请帮我生成谈薪脚本',
  EXPLAIN_QUESTION: '请讲解这道面试题',
  FOLLOW_UP: '请为这道面试题生成追问',
  CREATE_STRENGTHEN_TASK: '请针对这道题生成补强任务',
  CONTINUE_WITH_ASSET: '请基于当前资产继续优化',
}

function handleNavigateCardAction(actionItem) {
  const payload = actionItem?.payload
  const target = payload == null || payload === '' ? '/mine' : String(payload)
  if (target.startsWith('/') || target.startsWith('#/')) {
    router.push(target.startsWith('#/') ? target.slice(1) : target)
    return
  }
  const prompt = ENTRY_ACTION_PROMPTS[target] || actionItem?.label || ''
  if (prompt) {
    inputText.value = prompt.startsWith('请') ? prompt : `请帮我${prompt}`
  }
}

async function openJdViewer(jdId) {
  if (!jdId) return
  try {
    const detail = await getOpportunityDetail(jdId)
    jdViewerContent.value = detail?.jdContent || '暂无 JD 内容'
    jdViewerOpen.value = true
  } catch (e) {
    globalError.value = e?.message || '加载 JD 失败'
  }
}

async function openResumeVersion(versionId) {
  if (!versionId) return
  try {
    const detail = await getVersion(versionId)
    activeVersionId.value = versionId
    resumeViewerTitle.value = detail?.versionName || '简历预览'
    resumeViewerContent.value = detail?.contentMarkdown || ''
    resumeViewerSummary.value = detail?.changeSummary || ''
    resumeViewerFactCheck.value = parseFactCheck(detail?.factCheck)
    resumeViewerNotes.value = normalizeNotes(detail?.optimizationNotes)
    resumeViewerScore.value = detail?.aiScore != null ? Math.round(Number(detail.aiScore)) : null
    loadJdSkills()
    resumeViewerOpen.value = true
    versionsDrawerOpen.value = false
    // 切版本时退出对比态，避免旧 diff 残留误导
    diffMode.value = false
  } catch (e) {
    globalError.value = e?.message || '加载简历版本失败'
  }
}

function switchCanvasVersion(evt) {
  const id = evt?.target?.value
  if (id && id !== activeVersionId.value) {
    openResumeVersion(id)
  }
}

// 移动端「📄简历 ▸」抽出 Canvas：开当前版本，否则最新版本
function latestVersionId() {
  const list = workspaceVersions.value || []
  if (!list.length) return ''
  let best = list[0]
  for (const v of list) {
    if (new Date(v.createdAt || 0) > new Date(best.createdAt || 0)) best = v
  }
  return best.versionId
}
function openCanvasChip() {
  const target = activeVersionId.value || latestVersionId()
  if (target) openResumeVersion(target)
}

// 归一化改动项：后端 optimizationNotes 可能是字符串或 {text/note/change/detail}
function normalizeNotes(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .map((n) => {
      if (typeof n === 'string') {
        return { text: n.trim(), reason: n.trim(), anchor: '', kind: 'change', state: '' }
      }
      const reason = String(n?.reason || n?.text || n?.note || n?.content || n?.change || n?.detail || '').trim()
      return {
        text: reason,
        reason,
        anchor: String(n?.anchor || '').trim(),
        kind: n?.kind === 'suggestion' ? 'suggestion' : 'change',
        state: '',
      }
    })
    .filter((x) => x.text)
}

// 保留这处改动（确认接受）
function keepNote(note) {
  note.state = 'kept'
}

// 撤销这处改动：预填对话让小职改回（走正常对话流，不新增后端）
function revertNote(note) {
  note.state = 'reverting'
  inputText.value = `请撤销这处改动：${note.text}`
}

// #3 Canvas 逐行归因 + 内联建议：按 anchor 把改动/建议锚到正文行，内联渲染。
const ignoredSuggestions = ref(new Set())
function sugKey(s) {
  return `${s.anchor}|${s.text}`
}
const canvasAnnotated = computed(() => {
  const content = String(resumeViewerContent.value || '')
  const notes = resumeViewerNotes.value || []
  const changes = notes.filter((n) => n.kind !== 'suggestion')
  const sugs = notes.filter((n) => n.kind === 'suggestion' && !ignoredSuggestions.value.has(sugKey(n)))
  const usedC = new Set()
  const usedS = new Set()
  const blocks = content.split('\n').map((line, i) => {
    const lc = []
    changes.forEach((c, ci) => {
      if (c.anchor && !usedC.has(ci) && line.includes(c.anchor)) { usedC.add(ci); lc.push(c) }
    })
    const ls = []
    sugs.forEach((s, si) => {
      if (s.anchor && !usedS.has(si) && line.includes(s.anchor)) { usedS.add(si); ls.push(s) }
    })
    return { key: i, html: renderMd(line), changes: lc, suggestions: ls }
  })
  // 未锚定到行的改动 → 底部清单兜底
  const unmatched = changes.filter((c, ci) => !usedC.has(ci))
  return { blocks, unmatched }
})

function acceptSuggestion(s) {
  inputText.value = `请采纳这条建议：${s.text}`
  ignoredSuggestions.value = new Set(ignoredSuggestions.value).add(sugKey(s))
}
function ignoreSuggestion(s) {
  ignoredSuggestions.value = new Set(ignoredSuggestions.value).add(sugKey(s))
}

// 当前版本的"上一版"：按 createdAt 找严格更早、且时间最近的那一版（不依赖数组顺序）
const prevVersion = computed(() => {
  const list = workspaceVersions.value || []
  const active = list.find((v) => v.versionId === activeVersionId.value)
  if (!active) return null
  const activeTime = new Date(active.createdAt || 0).getTime()
  let best = null
  let bestTime = -Infinity
  for (const v of list) {
    if (v.versionId === active.versionId) continue
    const t = new Date(v.createdAt || 0).getTime()
    if (t <= activeTime && t > bestTime) {
      best = v
      bestTime = t
    }
  }
  return best
})

// 行级 diff（LCS）：返回 [{type:'same'|'add'|'del', text}]
function computeLineDiff(oldText, newText) {
  const a = String(oldText || '').split('\n')
  const b = String(newText || '').split('\n')
  const n = a.length
  const m = b.length
  const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }
  const out = []
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ type: 'same', text: b[j] })
      i++
      j++
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      out.push({ type: 'del', text: a[i] })
      i++
    } else {
      out.push({ type: 'add', text: b[j] })
      j++
    }
  }
  while (i < n) {
    out.push({ type: 'del', text: a[i] })
    i++
  }
  while (j < m) {
    out.push({ type: 'add', text: b[j] })
    j++
  }
  return out
}

async function toggleDiff() {
  if (diffMode.value) {
    diffMode.value = false
    return
  }
  const prev = prevVersion.value
  if (!prev) return
  diffLoading.value = true
  try {
    const detail = await getVersion(prev.versionId)
    diffLines.value = computeLineDiff(detail?.contentMarkdown || '', resumeViewerContent.value)
    diffMode.value = true
  } catch (e) {
    globalError.value = e?.message || '加载对比版本失败'
  } finally {
    diffLoading.value = false
  }
}

// 事实核对：解析落库的 factCheck，取出"疑似无出处"的强事实
function parseFactCheck(raw) {
  if (!raw) return null
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (e) {
    return null
  }
}
const factSuspects = computed(() => {
  const fc = resumeViewerFactCheck.value
  const list = fc && Array.isArray(fc.unsourcedFacts) ? fc.unsourcedFacts : []
  return list.filter((f) => f != null && String(f).trim())
})

async function canvasExportPdf() {
  if (!activeVersionId.value) return
  // 导出前终检：有疑似无出处的强事实 → 先弹确认，属实才导出
  if (factSuspects.value.length) {
    pendingExportFormat.value = 'pdf'
    return
  }
  await runExport('pdf')
}

async function canvasExportWord() {
  if (!activeVersionId.value) return
  if (factSuspects.value.length) {
    pendingExportFormat.value = 'word'
    return
  }
  await runExport('word')
}

// 用户在终检弹窗点"确认属实，继续导出"
async function confirmExport() {
  const fmt = pendingExportFormat.value
  pendingExportFormat.value = ''
  if (fmt) await runExport(fmt)
}

async function runExport(format) {
  if (!activeVersionId.value) return
  if (format === 'pdf') {
    pdfDownloading.value = true
    try {
      await downloadVersionPdf(activeVersionId.value, resumeViewerTitle.value)
    } catch (e) {
      globalError.value = e?.message || 'PDF 下载失败'
    } finally {
      pdfDownloading.value = false
    }
  } else {
    wordDownloading.value = true
    try {
      await downloadVersionDocx(activeVersionId.value, resumeViewerTitle.value)
    } catch (e) {
      globalError.value = e?.message || 'Word 下载失败'
    } finally {
      wordDownloading.value = false
    }
  }
}

async function copyResumeMarkdown(versionId) {
  if (!versionId) return
  try {
    const detail = await getVersion(versionId)
    const text = detail?.contentMarkdown || ''
    await navigator.clipboard.writeText(text)
  } catch (e) {
    globalError.value = e?.message || '复制失败'
  }
}

function appendCardMessage(card) {
  if (!card) return
  messages.value.push({
    id: `m_card_${Date.now()}`,
    role: 'agent',
    text: '',
    card,
    messageType: 'CARD',
    streaming: false,
    error: '',
    toolCalls: [],
  })
  scrollBottom()
  // 简历生成后自动打开右侧简历 Canvas
  if (card.type === 'RESUME_GENERATED' && card.versionId) {
    openResumeVersion(card.versionId)
  }
}

function resolveActionPayload(payload) {
  if (payload == null) return {}
  if (typeof payload === 'object') return payload
  if (typeof payload === 'string' && payload.trim().startsWith('{')) {
    try {
      return JSON.parse(payload)
    } catch {
      return { actionId: payload }
    }
  }
  return { actionId: payload }
}

async function requestGenerateResumeConfirmation(jdId) {
  if (!sessionId.value || resumeGenerating.value) return
  try {
    const ack = await postAction(
      sessionId.value,
      'GENERATE_RESUME',
      jdId || workspaceInfo.value?.jdId
    )
    if (ack?.card) {
      appendCardMessage(ack.card)
      return
    }
    throw new Error('无法发起简历生成确认')
  } catch (e) {
    globalError.value = e?.message || '发起确认失败'
  }
}

async function confirmPendingResumeAction(payload) {
  if (!sessionId.value || resumeGenerating.value) return
  const pl = resolveActionPayload(payload)
  try {
    const ack = await postAction(sessionId.value, 'CONFIRM_PENDING_ACTION', pl)
    if (!ack?.sseEndpoint) {
      throw new Error('确认失败，未获得生成流地址')
    }
    await startResumeGenerationWithEndpoint(ack.sseEndpoint)
  } catch (e) {
    globalError.value = e?.message || '确认生成失败'
  }
}

async function cancelPendingResumeAction(payload) {
  if (!sessionId.value) return
  const pl = resolveActionPayload(payload)
  try {
    const ack = await postAction(sessionId.value, 'CANCEL_PENDING_ACTION', pl)
    if (ack?.card) {
      appendCardMessage(ack.card)
    }
  } catch (e) {
    globalError.value = e?.message || '取消失败'
  }
}

async function startResumeGenerationWithEndpoint(sseEndpoint) {
  if (!sessionId.value || resumeGenerating.value) return
  resumeGenerating.value = true
  streamState.value = 'streaming'

  const streamMsg = appendReactiveMessage({
    id: `m_resume_stream_${Date.now()}`,
    role: 'agent',
    text: '',
    html: '',
    streaming: true,
    error: '',
    toolCalls: [],
  })
  scrollBottom()

  try {
    if (activeResumeStream.value?.close) {
      activeResumeStream.value.close()
    }

    activeResumeStream.value = openResumeGenerateStreamByEndpoint(sseEndpoint, {
      onResumeDelta(delta) {
        streamMsg.text += delta || ''
        streamMsg.html = renderMd(streamMsg.text)
        scrollBottom()
      },
      onCard(card) {
        streamMsg.text = ''
        finishStreaming(streamMsg)
        messages.value.push({
          id: `m_card_${Date.now()}`,
          role: 'agent',
          text: '',
          card,
          messageType: 'CARD',
          streaming: false,
          error: '',
          toolCalls: [],
        })
        scrollBottom()
      },
      onError(message) {
        streamMsg.error = message
        finishStreaming(streamMsg)
        globalError.value = message
        messages.value.push(withMarkdown({
          id: `m_fail_${Date.now()}`,
          role: 'agent',
          text: message,
          card: {
            type: 'GENERATE_FAILED',
            message,
            actions: [{ label: '重试', action: 'RETRY', payload: workspaceInfo.value?.jdId }],
          },
          messageType: 'CARD',
          streaming: false,
          error: message,
          toolCalls: [],
        }))
      },
      onDone() {
        finishStreaming(streamMsg)
        listVersions(sessionId.value).then((v) => {
          workspaceVersions.value = v || []
        }).catch(() => {})
      },
    })
  } catch (e) {
    streamMsg.error = e?.message || '启动生成失败'
    finishStreaming(streamMsg)
    globalError.value = streamMsg.error
  } finally {
    resumeGenerating.value = false
    if (streamState.value === 'streaming') {
      streamState.value = 'idle'
    }
  }
}

async function refreshTraceFromServer(agentMessage = null) {
  if (!sessionId.value) return
  try {
    const traces = await getAgentTrace(sessionId.value)
    if (agentMessage) {
      syncToolCallsFromServerTraces(agentMessage, traces)
    }
  } catch {
    // trace 同步失败不影响主流程
  }
}

async function createNewSession({ withWelcome = true } = {}) {
  sessionCreating.value = true
  clearGlobalError()
  streamState.value = 'session_creating'
  try {
    sessionId.value = await createAgentSession()
    streamState.value = 'idle'
    if (withWelcome) {
      messages.value = [defaultWelcomeMessage()]
    }
  } catch (e) {
    streamState.value = 'error'
    setGlobalError(e?.message || '会话创建失败', e)
    messages.value.push(withMarkdown({
      id: `m_${Date.now()}`,
      role: 'agent',
      text: '会话创建失败，请刷新后重试。',
      streaming: false,
      error: e?.message || '',
      toolCalls: [],
    }))
  } finally {
    sessionCreating.value = false
  }
}

async function restoreLatestChatSession() {
  sessionCreating.value = true
  clearGlobalError()
  streamState.value = 'session_creating'
  try {
    const sessions = await listAgentSessions({ taskType: 'CHAT', limit: 1 })
    const latest = Array.isArray(sessions) ? sessions[0] : null
    if (!latest?.sessionId) {
      return false
    }
    const restoredSession = await getAgentSession(latest.sessionId)
    if (!restoredSession?.sessionId) {
      return false
    }
    sessionId.value = restoredSession.sessionId
    const restoredMessages = mapServerMessages(restoredSession.messages)
    messages.value = restoredMessages.length > 0 ? restoredMessages : [defaultWelcomeMessage()]
    scheduleMarkdownForMessages(messages.value)
    streamState.value = 'idle'
    scrollBottom()
    // U3 断点续传：如果最后一条是 user 消息（没等到 agent 回复），server 可能还在写
    void resumePendingAgentReplyIfAny(restoredSession.sessionId)
    return true
  } catch (e) {
    console.warn('[agent] restore latest chat session failed', e)
    return false
  } finally {
    sessionCreating.value = false
    if (streamState.value === 'session_creating') {
      streamState.value = 'idle'
    }
  }
}

const RESUME_POLL_INTERVAL_MS = 2000
const RESUME_POLL_MAX_ATTEMPTS = 20 // 最多等 40s
const RESUME_PLACEHOLDER_MIN_VISIBLE_MS = 1500 // "恢复中"占位最短可见时长，避免恢复过快时一闪即逝

async function resumePendingAgentReplyIfAny(targetSessionId) {
  const last = messages.value[messages.value.length - 1]
  if (!last || last.role !== 'user') return
  let status = null
  try {
    status = await getAgentStreamStatus(targetSessionId)
  } catch (e) {
    return
  }
  if (!status?.running) return

  const placeholderId = `m_resume_${Date.now()}`
  const placeholderShownAt = Date.now()
  messages.value.push(withMarkdown({
    id: placeholderId,
    role: 'agent',
    text: '上次的回复还在 server 上继续生成，稍候为你恢复…',
    streaming: true,
    error: '',
    toolCalls: [],
  }))
  scrollBottom()

  const userMsgCountBefore = messages.value.filter((m) => m.role === 'user').length
  for (let i = 0; i < RESUME_POLL_MAX_ATTEMPTS; i++) {
    await new Promise((resolve) => setTimeout(resolve, RESUME_POLL_INTERVAL_MS))
    if (sessionId.value !== targetSessionId) return // 会话已切换
    try {
      const refreshed = await getAgentSession(targetSessionId)
      const refreshedMessages = mapServerMessages(refreshed?.messages)
      const refreshedAgentAfterUser = refreshedMessages
        .slice(refreshedMessages.findIndex((m) => m.role === 'user' && userMsgCountBefore > 0) + 1)
        .find((m) => m.role === 'agent')
      if (refreshedAgentAfterUser) {
        // 保证"恢复中"占位稳定可见，避免恢复过快时一闪即逝
        const elapsed = Date.now() - placeholderShownAt
        if (elapsed < RESUME_PLACEHOLDER_MIN_VISIBLE_MS) {
          await new Promise((resolve) => setTimeout(resolve, RESUME_PLACEHOLDER_MIN_VISIBLE_MS - elapsed))
          if (sessionId.value !== targetSessionId) return // 期间会话已切换
        }
        messages.value = refreshedMessages
        scheduleMarkdownForMessages(messages.value)
        scrollBottom()
        return
      }
      const stillRunning = (await getAgentStreamStatus(targetSessionId).catch(() => null))?.running
      if (!stillRunning) {
        // server 已结束但未产出 agent 消息，移除 placeholder
        messages.value = messages.value.filter((m) => m.id !== placeholderId)
        return
      }
    } catch (e) {
      // 忽略单次失败，继续轮询
    }
  }
  // 超时
  const placeholder = messages.value.find((m) => m.id === placeholderId)
  if (placeholder) {
    placeholder.streaming = false
    placeholder.text = '恢复超时，可重新提问继续对话。'
    placeholder.error = '恢复超时'
  }
}

async function bootstrapChat() {
  if (workspaceId.value) {
    await loadWorkspaceContext(workspaceId.value)
    return
  }
  // 根聊天页始终拉「最近会话线」：即便自动恢复了上一段 CHAT 会话，也能一键切回某条 JD 线
  loadRecentLines()
  const restored = await restoreLatestChatSession()
  if (restored) {
    return
  }
  sessionId.value = ''
  messages.value = []
  streamState.value = 'idle'
}

async function loadRecentLines() {
  try {
    recentLines.value = await listRecentLines(8)
  } catch (e) {
    recentLines.value = []
  }
}

function resumeLine(sid) {
  if (sid) {
    router.push({ name: 'chat-workspace', params: { wsId: sid } })
  }
}

// 左栏：加载 JD 会话线 + 通用对话（桌面常驻）
async function loadSessionPanes() {
  loadRecentLines()
  try {
    const sessions = await listAgentSessions({ taskType: 'CHAT', limit: 100 })
    leftChatSessions.value = Array.isArray(sessions) ? sessions : (sessions?.items || [])
  } catch (e) {
    leftChatSessions.value = []
  }
}

// 通用对话滚动分页：先渲染一批，左栏触底再加载更多（web/移动统一滚动分页）
const leftVisibleCount = ref(15)
const displayChatSessions = computed(() => leftChatSessions.value.slice(0, leftVisibleCount.value))
function onSessionsScroll(evt) {
  const el = evt.target
  if (el.scrollHeight - (el.scrollTop + el.clientHeight) < 120
    && leftVisibleCount.value < leftChatSessions.value.length) {
    leftVisibleCount.value += 15
  }
}
// 通用对话标题兜底：默认「新会话」/空 → 用相对时间，避免整列同名
function chatSessionTitle(s) {
  const t = String(s?.title || '').trim()
  if (t && t !== '新会话') return t
  return s?.updatedAt ? `对话 · ${formatVersionDate(s.updatedAt)}` : '新对话'
}

// 点左栏某条会话线 → 切换到该 JD/对话（复用 /chat/:wsId 路由）
function openSessionLine(sid) {
  if (!sid || sid === activeSessionId.value) return
  router.push({ name: 'chat-workspace', params: { wsId: sid } })
}

// ＋新对话：回到空白根聊天
function startNewChat() {
  if (workspaceId.value) {
    router.push('/chat')
  } else {
    sessionId.value = ''
    messages.value = []
    streamState.value = 'idle'
  }
}

async function sendExamplePrompt(prompt) {
  inputText.value = prompt
  await sendMessage()
}

async function loadCareerProfileBanner() {
  const cachedProfile = homeStore.state.careerProfile
  if (cachedProfile) {
    careerProfile.value = {
      ...careerProfile.value,
      ...cachedProfile,
      skillKeywords: Array.isArray(cachedProfile.skillKeywords) ? cachedProfile.skillKeywords : [],
    }
    return
  }
  try {
    const profile = await getCareerProfile()
    if (profile) {
      careerProfile.value = {
        ...careerProfile.value,
        ...profile,
        skillKeywords: Array.isArray(profile.skillKeywords) ? profile.skillKeywords : [],
      }
      homeStore.updateCareerProfile(profile)
    }
  } catch {
    // profile banner is non-blocking
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streamState.value === 'streaming' || sessionCreating.value) return
  if (!sessionId.value) {
    await createNewSession({ withWelcome: false })
    if (!sessionId.value) return
  }
  // 焦点作为会话内检索信号注入给小职（不污染可见气泡，仅提示路由）
  const focusLabels = FOCUS_OPTIONS.filter((f) => activeFocuses.value.includes(f.key)).map((f) => f.label)
  const outgoing = focusLabels.length ? `（当前关注：${focusLabels.join('、')}）\n${text}` : text
  clearGlobalError()
  currentTraceId.value = ''

  messages.value.push({
    id: `m_${Date.now()}_u`,
    role: 'user',
    text,
    streaming: false,
    error: '',
    toolCalls: [],
  })
  inputText.value = ''
  scrollBottom()

  const agentMessage = appendReactiveMessage({
    id: `m_${Date.now()}_a`,
    role: 'agent',
    text: '',
    html: '',
    streaming: true,
    error: '',
    toolCalls: [],
  })
  streamState.value = 'streaming'
  activeAgentMessage.value = agentMessage
  scrollBottom()

  const streamController = new AbortController()
  activeStreamController.value = streamController
  startStreamWatchdog(agentMessage)

  try {
    await sendAgentMessageStream(sessionId.value, outgoing, {
      onTraceHeader({ traceId }) {
        if (traceId) {
          currentTraceId.value = traceId
        }
      },
      onPathMode(data) {
        currentPathMode.value = data?.mode || ''
      },
      onPlan() {},
      onToolStart(data) {
        handleToolStart(agentMessage, data)
      },
      onToolResult(data) {
        handleToolResult(agentMessage, data)
        const name = data?.toolName || 'unknown'
        if (isCareerTaskToolName(name) && data?.success) {
          notifyCareerTasksUpdated()
        }
      },
      onTrace() {},
      onCard(card) {
        messages.value.push({
          id: `m_card_${Date.now()}`,
          role: 'agent',
          text: '',
          card,
          messageType: 'CARD',
          streaming: false,
          error: '',
          toolCalls: [],
        })
        scrollBottom()
      },
      onToken(data) {
        const token = data?.content || ''
        agentMessage.text += token
        agentMessage.html = renderMd(agentMessage.text)
        scrollBottom()
      },
      onMessage(data) {
        if (data?.content) {
          agentMessage.text = data.content
          agentMessage.html = renderMd(agentMessage.text)
        }
        finalizeRunningToolCalls(agentMessage, true)
      },
      onDone(data) {
        clearStreamWatchdog()
        streamState.value = 'done'
        finalizeRunningToolCalls(agentMessage, true)
        finishStreaming(agentMessage)
        refreshTraceFromServer(agentMessage)
      },
      onError(error) {
        clearStreamWatchdog()
        streamState.value = 'error'
        finalizeRunningToolCalls(agentMessage, false)
        finishStreaming(agentMessage)
        agentMessage.error = error?.message || '流式调用失败'
        setGlobalError(agentMessage.error, error)
      },
    }, {
      signal: streamController.signal,
      deepMode: deepModeOn.value,
    })
    if (streamState.value === 'streaming') {
      streamState.value = 'done'
      finishStreaming(agentMessage)
    }
  } catch (e) {
    streamState.value = 'error'
    finishStreaming(agentMessage)
    agentMessage.error = e?.message || '流式请求失败'
    setGlobalError(agentMessage.error, e)
  } finally {
    clearStreamWatchdog()
    if (activeStreamController.value === streamController) {
      activeStreamController.value = null
    }
    if (activeAgentMessage.value === agentMessage) {
      activeAgentMessage.value = null
    }
    if (streamState.value === 'done') {
      finalizeRunningToolCalls(agentMessage, true)
    } else if (streamState.value === 'error') {
      finalizeRunningToolCalls(agentMessage, false)
    }
    if (streamState.value === 'streaming') {
      streamState.value = 'error'
      finalizeRunningToolCalls(agentMessage, false)
      finishStreaming(agentMessage)
      agentMessage.error = '流式响应未正常结束，请重试。'
      setGlobalError(agentMessage.error)
    }
    if (!agentMessage.text) {
      agentMessage.text = '暂未收到回复，请稍后重试。'
    }
    finishStreaming(agentMessage)
    scrollBottom()
  }
}


watch(() => route.params.wsId, async (rawWsId) => {
  const wsId = normalizeWsId(rawWsId)
  if (wsId) {
    await loadWorkspaceContext(wsId)
  }
})

onMounted(async () => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  if (!isMobile.value) loadSessionPanes()
  await Promise.allSettled([bootstrapChat(), loadCareerProfileBanner()])
  // 资产库「复用」某简历版本：预填对话意图，让小职以该版本为基础改
  if (route.query.reuse) {
    const name = String(route.query.reuseName || '这份简历')
    inputText.value = `请以「${name}」为基础，帮我针对当前 JD 调整这份简历`
  }
  // 资产库薪资面板「带入小职薪资焦点」：激活薪资焦点 + 预填谈薪问句
  if (route.query.focus === 'salary') {
    if (!activeFocuses.value.includes('salary')) activeFocuses.value.push('salary')
    const role = String(route.query.role || '这个岗位')
    const city = String(route.query.city || '')
    inputText.value = `${role}${city ? '（' + city + '）' : ''}给到多少合理？结合我的画像帮我看看谈薪`
  }
  scrollBottom()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
  abortActiveStream('页面已离开，流式请求已取消')
  if (activeResumeStream.value?.close) {
    activeResumeStream.value.close()
  }
})
</script>

<style scoped>
.header-action.secondary {
  margin-right: 8px;
  background: #f1f5f9;
  color: #334155;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 400;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

/* 简历 Canvas：右侧停靠的纸感画布 */
.canvas-dock {
  position: fixed;
  inset: 0;
  z-index: 420;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  justify-content: flex-end;
}
.canvas-panel {
  width: min(560px, 92vw);
  height: 100%;
  background: #eef0f5;
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 30px rgba(15, 23, 42, 0.2);
  animation: canvasIn 0.18s ease;
}
@keyframes canvasIn {
  from { transform: translateX(24px); opacity: 0.6; }
  to { transform: translateX(0); opacity: 1; }
}
.canvas-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}
.canvas-title {
  font-weight: 700;
  font-size: 14px;
  color: #1A1D26;
}
.canvas-ver,
.canvas-vername {
  font-size: 12px;
  color: #475569;
  max-width: 180px;
}
.canvas-ver {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 4px 8px;
  background: #fff;
}
.canvas-spacer {
  flex: 1;
}
.canvas-act {
  font-size: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 5px 11px;
  background: #fff;
  color: #334155;
  cursor: pointer;
}
.canvas-act:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.canvas-act-on {
  background: #4E5BEF;
  border-color: #4E5BEF;
  color: #fff;
}
.fact-confirm {
  max-width: 440px;
}
.fact-confirm-lead {
  font-size: 13px;
  line-height: 1.6;
  color: #DB9A2D;
  margin: 0 0 12px;
}
.fact-list {
  margin: 0 0 16px;
  padding: 10px 12px 10px 28px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  max-height: 220px;
  overflow: auto;
}
.fact-list li {
  font-size: 13px;
  color: #92400e;
  line-height: 1.8;
}
.fact-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.fact-btn-ghost {
  font-size: 13px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 16px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}
.fact-btn-primary {
  font-size: 13px;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  color: #fff;
  cursor: pointer;
  font-weight: 600;
}
.canvas-diff {
  font-family: 'SFMono-Regular', ui-monospace, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.7;
  color: #1A1D26;
}
.diff-legend {
  display: flex;
  gap: 14px;
  align-items: center;
  padding-bottom: 10px;
  margin-bottom: 8px;
  border-bottom: 1px dashed #e2e8f0;
  font-size: 12px;
}
.diff-legend-add {
  color: #0DA76A;
  font-weight: 600;
}
.diff-legend-del {
  color: #E5484D;
  font-weight: 600;
}
.diff-legend-hint {
  color: #94a3b8;
  margin-left: auto;
}
.diff-line {
  display: flex;
  gap: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 0 6px;
  border-radius: 4px;
}
.diff-gutter {
  flex-shrink: 0;
  width: 14px;
  text-align: center;
  color: #94a3b8;
  user-select: none;
}
.diff-text {
  flex: 1;
  min-width: 0;
}
.diff-add {
  background: #dcfce7;
}
.diff-add .diff-gutter {
  color: #0DA76A;
}
.diff-del {
  background: #fee2e2;
  color: #7f1d1d;
  text-decoration: line-through;
  text-decoration-color: rgba(185, 28, 28, 0.4);
}
.diff-del .diff-gutter {
  color: #E5484D;
}
.canvas-close {
  font-size: 20px;
  line-height: 1;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 0 4px;
}
.canvas-changes {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 8px 16px;
  background: #e6f6ef;
  border-bottom: 1px solid #cdeadd;
  font-size: 12px;
  color: #0f5132;
}
.canvas-changes-kicker {
  font-weight: 700;
  color: #0da76a;
  flex: 0 0 auto;
}
.canvas-gap {
  flex: 0 0 auto;
  color: #0f5132;
  font-weight: 600;
}
.canvas-gap-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 16px;
  background: #F5F6F8;
  border-bottom: 1px solid #e2e8f0;
  font-size: 12px;
}
.gapl {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-weight: 600;
}
.gapl.hit {
  color: #0DA76A;
  background: #dcfce7;
}
.gapl.mid {
  color: #DB9A2D;
  background: #fffbeb;
}
.gapl.miss {
  color: #E5484D;
  background: #fee2e2;
}
.canvas-notes {
  padding: 8px 16px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.canvas-note {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px dashed #c7d2fe;
  border-radius: 8px;
  background: #f5f7ff;
  font-size: 12px;
}
.canvas-note.kept {
  border-style: solid;
  border-color: #bbf7d0;
  background: #f0fdf4;
}
.canvas-note.reverting {
  border-color: #fde68a;
  background: #fffbeb;
}
.canvas-note-mark {
  flex: 0 0 auto;
  font-weight: 700;
  color: #4E5BEF;
}
.canvas-note-text {
  flex: 1;
  min-width: 0;
  color: #334155;
}
.canvas-note-done {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 11px;
}
.canvas-note-btn {
  flex: 0 0 auto;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 3px 10px;
  font-size: 11px;
  background: #fff;
  cursor: pointer;
  font-family: inherit;
}
.canvas-note-btn.keep {
  color: #0DA76A;
  border-color: #bbf7d0;
}
.canvas-note-btn.revert {
  color: #DB9A2D;
  border-color: #fde68a;
}
/* #3 逐行归因 + 内联建议 */
.resume-annotated .rline {
  position: relative;
}
.resume-annotated .rline.chg {
  border-left: 2px solid #D8DCFB;
  padding-left: 8px;
  margin-left: -10px;
}
.rline-note {
  font-size: 11.5px;
  color: #5C6472;
  margin: 2px 0 6px;
  padding-left: 2px;
}
.rline-note-mark {
  font-weight: 700;
  color: #4E5BEF;
  margin-right: 6px;
}
.rline-sug {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 8px;
  padding: 6px 10px;
  border: 1px dashed #D8DCFB;
  border-radius: 8px;
  background: #F5F7FF;
  font-size: 12px;
}
.rline-sug-icon { flex-shrink: 0; }
.rline-sug-text { flex: 1; color: #334155; }
.rline-sug-btn {
  flex-shrink: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 2px 10px;
  font-size: 11.5px;
  cursor: pointer;
  background: #fff;
}
.rline-sug-btn.accept { color: #4E5BEF; border-color: #D8DCFB; font-weight: 600; }
.rline-sug-btn.ignore { color: #9AA2AF; border-color: #E8EAF0; }
.canvas-body {
  flex: 1;
  overflow: auto;
  padding: 20px;
}
.canvas-paper {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(20, 24, 40, 0.05), 0 8px 28px rgba(20, 24, 40, 0.09);
  padding: 28px 32px;
  font-size: 13px;
  line-height: 1.7;
  color: #1a1d26;
  min-height: 100%;
}
@media (max-width: 640px) {
  .canvas-panel {
    width: 100vw;
  }
}

.modal-panel {
  width: min(640px, 100%);
  max-height: 80vh;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
}

.drawer-panel {
  align-self: flex-end;
  margin-left: auto;
  height: 80vh;
  width: min(360px, 100%);
  border-radius: 12px 0 0 12px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
}

.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
  color: #64748b;
}

.viewer-summary-banner {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 12px 16px 0;
  border: 1px solid #c7d2fe;
  background: #eef2ff;
  color: #334155;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.55;
}

.viewer-summary-kicker {
  color: #4338ca;
  font-size: 11px;
  font-weight: 700;
}

.modal-body {
  padding: 16px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
}

.markdown-preview {
  white-space: normal;
}

.markdown-preview :deep(p) {
  margin: 0 0 10px;
}

.markdown-preview :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: 6px 0 10px 18px;
  padding: 0;
}

.markdown-preview :deep(li) {
  margin-bottom: 5px;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin: 12px 0 6px;
  color: #1A1D26;
}

.versions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #F5F6F8;
  cursor: pointer;
  text-align: left;
}

.version-date {
  font-size: 12px;
  color: #64748b;
}

.empty-hint {
  color: #94a3b8;
  font-size: 14px;
}

.chat-page {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.chat-layout {
  display: flex;
  flex-direction: row;
  height: 100%;
  min-height: 0;
}

/* 桌面常驻左栏：会话线列表 */
.chat-sessions {
  flex: 0 0 208px;
  min-width: 0;
  height: 100%;
  overflow-y: auto;
  border-right: 1px solid #e2e8f0;
  background: #fafbfc;
  padding: 12px 10px;
}
.cs-new {
  width: 100%;
  border: 1px dashed #c7d2fe;
  border-radius: 8px;
  background: #fff;
  color: #4338ca;
  font-size: 13px;
  font-weight: 600;
  padding: 8px;
  cursor: pointer;
  font-family: inherit;
  margin-bottom: 12px;
}
.cs-section {
  margin-bottom: 14px;
}
.cs-title {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  padding: 0 4px 6px;
}
.cs-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 7px 8px;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  margin-bottom: 2px;
}
.cs-item:hover {
  background: #eef2ff;
}
.cs-item.active {
  background: #e0e7ff;
}
.cs-avatar {
  flex: 0 0 auto;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.cs-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cs-item.active .cs-name {
  color: #3730a3;
  font-weight: 600;
}
.cs-empty {
  font-size: 12px;
  color: #cbd5e1;
  padding: 4px 8px;
}
.cs-more {
  text-align: center;
  font-size: 11px;
  color: #9AA2AF;
  padding: 8px 0;
}

.context-chips-bar {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 6px 16px;
  background: #eef2ff;
  border-bottom: 1px solid #c7d2fe;
  flex-shrink: 0;
  overflow-x: auto;
}

.profile-aha-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 8px 16px 0;
  padding: 9px 12px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-family: inherit;
  cursor: pointer;
  text-align: left;
}

.profile-aha-main {
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.profile-aha-sub {
  min-width: 0;
  font-size: 12px;
  color: #c2410c;
  overflow-wrap: anywhere;
}

.ctx-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  background: #fff;
  color: #4338ca;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.ctx-chip--resume {
  background: #f0fdf4;
  color: #0DA76A;
}
.ctx-anchor {
  flex-shrink: 0;
  font-size: 12px;
}
.ctx-avatar {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
}
.ctx-chip--score {
  background: #fffbeb;
  color: #DB9A2D;
}
.ctx-chip--mem {
  background: #ecfdf5;
  color: #0f766e;
}
.ctx-chip--mem.is-new {
  background: #eef2ff;
  color: #4338ca;
}

.chat-drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(15, 23, 42, 0.45);
}

.chat-drawer {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 80%;
  max-width: 300px;
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: 8px 0 24px rgba(0, 0, 0, 0.15);
}

.drawer-user {
  padding: 18px 16px;
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  color: #fff;
  display: flex;
  gap: 10px;
  align-items: center;
}

.drawer-avatar {
  position: relative;
  width: 42px;
  height: 42px;
  background: rgba(255, 255, 255, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 800;
}

.drawer-online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  background: #10b981;
  border: 2px solid #fff;
  border-radius: 50%;
}

.drawer-name {
  font-size: 14px;
  font-weight: 700;
}

.drawer-status {
  font-size: 10px;
  opacity: 0.85;
}

.drawer-nav {
  padding: 14px 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-link {
  border: none;
  background: transparent;
  text-align: left;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 13px;
  color: #1A1D26;
  cursor: pointer;
  font-family: inherit;
}

.drawer-link.active {
  background: #eef2ff;
  color: #4338ca;
  font-weight: 700;
}

.drawer-footer {
  margin-top: auto;
  padding: 14px 20px;
  border-top: 1px solid #f1f5f9;
}

.drawer-footer-link {
  border: none;
  background: transparent;
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  padding: 0;
}

.chat-header {
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.header-left { display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1; }

.hamburger-btn {
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  padding: 6px;
  cursor: pointer;
  color: #1A1D26;
  flex-shrink: 0;
}

.header-title { font-weight: 700; font-size: 13px; color: #1A1D26; }
.header-sub { font-size: 10px; color: #10b981; }
.header-sub--ready { color: #10b981; }
.header-sub--pending { color: #f59e0b; }

.header-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-shrink: 0;
}

.canvas-chip-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 11px;
  border-radius: 999px;
  border: 1px solid #c7d2fe;
  background: #eef2ff;
  color: #4338ca;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  font-family: inherit;
}

.path-mode-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.path-mode-chip--fast {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}
.path-mode-chip--deep {
  background: #eef2ff;
  color: #4338ca;
  border: 1px solid #c7d2fe;
}

.trace-id-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: min(280px, 42vw);
  padding: 4px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #F5F6F8;
  font-size: 10px;
  color: #475569;
}

.trace-id-label {
  flex-shrink: 0;
  font-weight: 600;
  color: #64748b;
}

.trace-id-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 10px;
  color: #334155;
}

.trace-id-copy {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #4E5BEF;
  font-size: 10px;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
}

.header-action {
  background: none; border: 1px solid #e2e8f0; padding: 5px 12px;
  border-radius: 6px; font-size: 11px; cursor: pointer; color: #64748b;
}

.header-action:hover { background: #F5F6F8; }
.header-action:disabled { opacity: .5; cursor: default; }

.global-error {
  margin: 10px 16px 0;
  padding: 8px 10px;
  border: 1px solid #fecaca;
  background: #fff1f2;
  color: #E5484D;
  font-size: 12px;
  border-radius: 8px;
}

.error-detail {
  margin-top: 6px;
  color: #7f1d1d;
}

.error-detail summary {
  cursor: pointer;
  font-weight: 600;
}

.error-detail dl {
  margin: 6px 0 0;
  display: grid;
  gap: 4px;
}

.error-detail dl > div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.error-detail dt {
  font-weight: 600;
  color: #991b1b;
}

.error-detail dd {
  margin: 0;
  min-width: 0;
  overflow-wrap: anywhere;
}

.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  min-width: 0;
}

.messages-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.recent-lines {
  width: min(680px, 100%);
  margin: 4px auto 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
}

.recent-lines-title {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}

.recent-line-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #F5F6F8;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  margin-bottom: 6px;
  transition: background 0.15s, border-color 0.15s;
}

.recent-line-item:hover {
  background: #eef2ff;
  border-color: #c7d2fe;
}

.recent-line-name {
  font-size: 13px;
  font-weight: 600;
  color: #1A1D26;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-line-time {
  flex-shrink: 0;
  font-size: 11px;
  color: #94a3b8;
}

.zero-chat-card {
  width: min(680px, 100%);
  margin: 4px auto 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #F5F6F8;
  padding: 12px;
}

.zero-chat-label {
  display: inline-flex;
  align-items: center;
  margin-bottom: 10px;
  padding: 3px 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.zero-chat-bubbles {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.zero-bubble {
  max-width: 84%;
  padding: 9px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.zero-bubble.agent {
  align-self: flex-start;
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #334155;
}

.zero-bubble.user {
  align-self: flex-end;
  background: #4E5BEF;
  color: #fff;
}

.zero-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.zero-prompt-chip {
  border: 1px solid #c7d2fe;
  background: #fff;
  color: #4338ca;
  border-radius: 999px;
  padding: 7px 11px;
  font-size: 12px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
}

.zero-prompt-chip:hover {
  background: #eef2ff;
}

.zero-prompt-chip:disabled {
  opacity: 0.55;
  cursor: default;
}

.msg-wrapper { margin-bottom: 6px; }
.msg-row { display: flex; }
.user-row { justify-content: flex-end; }
.agent-row { align-items: flex-start; }

.msg-bubble {
  max-width: 75%;
  font-size: 13px;
  line-height: 1.6;
}

.user-bubble {
  background: #4E5BEF;
  color: #fff;
  border-radius: 14px 14px 4px 14px;
  padding: 9px 13px;
}

.agent-bubble {
  width: fit-content;
  max-width: 85%;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 12px 14px;
  color: #334155;
}

.agent-bubble--waiting {
  width: fit-content;
  min-width: 64px;
  padding: 14px 16px;
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.md-body {
  font-size: 13px;
  line-height: 1.75;
  color: #334155;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.md-plain {
  white-space: pre-wrap;
  word-break: break-word;
}

.md-body :deep(p) {
  margin: 0 0 8px;
}

.md-body :deep(p:last-child) {
  margin-bottom: 0;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  font-weight: 700;
  color: #1A1D26;
  margin: 12px 0 6px;
  line-height: 1.4;
}

.md-body :deep(h1) { font-size: 15px; }
.md-body :deep(h2) { font-size: 14px; }
.md-body :deep(h3) { font-size: 13px; }

.md-body :deep(ul),
.md-body :deep(ol) {
  padding-left: 18px;
  margin: 6px 0 10px;
}

.md-body :deep(li) {
  margin-bottom: 5px;
  line-height: 1.65;
}

.md-body :deep(strong) {
  font-weight: 700;
  color: #111827;
}

.md-body :deep(em) {
  font-style: italic;
  color: #475569;
}

.md-body :deep(code) {
  background: #f1f5f9;
  color: #4E5BEF;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

.md-body :deep(pre) {
  background: #1e293b;
  border-radius: 8px;
  padding: 12px 14px;
  margin: 8px 0;
  overflow-x: auto;
  max-width: 100%;
}

.md-body :deep(pre code) {
  background: transparent;
  color: #e2e8f0;
  padding: 0;
  font-size: 12px;
}

.md-body :deep(blockquote) {
  border-left: 3px solid #c7d2fe;
  background: #eef2ff;
  margin: 8px 0;
  padding: 8px 12px;
  border-radius: 0 6px 6px 0;
  color: #4338ca;
  font-size: 12px;
}

.md-body :deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 10px 0;
}

.md-body :deep(a) {
  color: #4E5BEF;
  text-decoration: underline;
}

.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  margin: 8px 0;
  display: block;
  overflow-x: auto;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  text-align: left;
}

.md-body :deep(th) {
  background: #F5F6F8;
  font-weight: 700;
  color: #1A1D26;
}

.stream-flag { display: inline-block; color: #4E5BEF; font-size: 13px; line-height: 1; animation: blink 1s step-start infinite; margin-left: 1px; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

.thinking-flag { display: flex; gap: 4px; align-items: center; padding: 4px 0; }

.thinking-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #6366f1;
  animation: thinking-bounce 1.2s ease-in-out infinite;
}

.thinking-dot:nth-child(1) { animation-delay: 0s; }
.thinking-dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes thinking-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.4; }
  40% { transform: translateY(-6px); opacity: 1; }
}

.stream-error { margin-top: 4px; color: #E5484D; font-size: 11px; }

.tool-call-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 4px;
}

.input-area {
  flex-shrink: 0;
  position: relative;
  border-top: 1px solid #e2e8f0;
  padding: 12px 16px;
  background: #fff;
  z-index: 10;
}

.focus-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.focus-label {
  font-size: 11px;
  color: #94a3b8;
  margin-right: 2px;
}

.focus-chip {
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  color: #64748b;
  font-size: 11px;
  padding: 3px 10px;
  cursor: pointer;
  font-family: inherit;
}

.focus-chip--on {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: #4338ca;
  font-weight: 600;
}

.input-row {
  display: flex;
  align-items: center;
  background: #F5F6F8;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 6px 6px 6px 14px;
}

.chat-input { flex: 1; border: none; background: transparent; padding: 6px; outline: none; font-size: 12px; font-family: inherit; }

.ref-btn {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  padding: 0 6px;
  font-family: inherit;
}
.ref-btn.on { color: #4E5BEF; }
.ref-menu {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 100%;
  margin-bottom: 8px;
  max-height: 300px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  padding: 8px;
  z-index: 20;
}
.ref-sect { padding: 4px; }
.ref-title { font-size: 11px; font-weight: 700; color: #94a3b8; padding: 4px 6px; }
.ref-row {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 8px 8px;
  font-size: 12.5px;
  color: #334155;
  cursor: pointer;
  font-family: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref-row:hover { background: #eef2ff; }
.ref-empty { font-size: 12px; color: #cbd5e1; padding: 6px 8px; }

.deep-toggle {
  flex-shrink: 0;
  padding: 6px 10px;
  background: #fff;
  color: #64748b;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all .2s;
}
.deep-toggle--on {
  background: #eef2ff;
  color: #4338ca;
  border-color: #c7d2fe;
}

.send-btn {
  flex-shrink: 0;
  padding: 6px 14px;
  /* 定稿：发送键用 AI 渐变 */
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity .2s;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(78, 91, 239, .28);
}

.send-btn:disabled { opacity: .4; cursor: default; }

@media (max-width: 768px) {
  .chat-page {
    max-width: 100%;
    width: 100%;
    margin: 0;
    height: 100dvh;
    min-height: 0;
    overflow: hidden;
  }

  .chat-layout {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
    width: 100%;
    max-width: 100%;
  }

  .chat-main {
    flex: 1;
    min-height: 0;
    overflow: hidden;
    width: 100%;
    max-width: 100%;
  }

  .messages-area {
    padding: 12px 6px 12px 4px;
  }

  .profile-aha-banner {
    margin: 8px 8px 0;
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;
  }

  .zero-chat-card {
    margin-top: 0;
    padding: 10px;
  }

  .zero-bubble {
    max-width: 92%;
  }

  .agent-row {
    width: 100%;
    padding-right: 0;
  }

  .input-area {
    flex-shrink: 0;
    padding-bottom: calc(12px + env(safe-area-inset-bottom));
  }

  .msg-bubble {
    max-width: 88%;
  }

  .user-bubble {
    max-width: 80%;
  }

  .agent-bubble {
    max-width: 88%;
    width: fit-content;
  }

  .chat-input {
    min-height: 44px;
    font-size: 16px;
  }

  .send-btn {
    min-height: 44px;
    min-width: 56px;
    padding: 8px 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .input-row {
    min-height: 44px;
  }

  .header-action {
    min-height: 44px;
    flex-shrink: 0;
  }

  .hamburger-btn {
    min-height: 44px;
    min-width: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .header-left {
    min-width: 0;
    flex: 1;
  }

  .header-title {
    word-break: break-word;
  }
}

@media (max-width: 480px) {
  .chat-header {
    padding: 10px 12px;
    gap: 8px;
    align-items: flex-start;
  }

  .header-sub {
    display: none;
  }

  .header-title {
    font-size: 14px;
  }

  .header-action {
    font-size: 10px;
    padding: 8px 10px;
    white-space: nowrap;
  }

  .msg-bubble {
    max-width: 92%;
    font-size: 13px;
  }

  .agent-bubble {
    max-width: 88%;
    width: fit-content;
  }
}
</style>
