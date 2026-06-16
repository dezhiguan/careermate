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
          <button type="button" class="drawer-link active" @click="drawerOpen = false">AI 小职 · 当前</button>
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/interview')">面试题</button>
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/market')">市场</button>
          <button type="button" class="drawer-link" @click="navigateFromDrawer('/mine')">我的</button>
        </nav>
        <div class="drawer-footer">
          <button type="button" class="drawer-footer-link" @click="authStore.logout()">退出登录</button>
        </div>
      </aside>
    </div>

    <div class="chat-layout">
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
            <div v-if="currentTraceId" class="trace-id-chip" title="SkyWalking Trace ID">
              <span class="trace-id-label">Trace ID</span>
              <code class="trace-id-value">{{ currentTraceId }}</code>
              <button type="button" class="trace-id-copy" @click="copyTraceId">复制</button>
            </div>
            <button
              type="button"
              class="header-action secondary"
              :disabled="!workspaceInfo"
              @click="openContext"
            >
              查看上下文
            </button>
            <button class="header-action" :disabled="sessionCreating" @click="resetChat">
              {{ sessionCreating ? '创建中...' : '重置会话' }}
            </button>
          </div>
        </div>

        <div v-if="workspaceInfo" class="context-chips-bar">
          <span
            v-for="(chip, idx) in contextChipList"
            :key="`${chip}-${idx}`"
            class="ctx-chip"
            :class="{ 'ctx-chip--resume': chip.includes('简历') }"
          >
            {{ chip }}
          </span>
        </div>

        <div v-if="globalError" class="global-error">{{ globalError }}</div>

        <div class="messages-area" ref="msgContainer">
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
                  <span v-if="msg.streaming || !msg.html" class="md-plain">{{ msg.text }}</span>
                  <div v-else v-html="msg.html"></div>
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
          <div class="input-row">
            <input
              v-model="inputText"
              :placeholder="resumeGenerating ? '小职正在为你重写简历...' : '说说你想做什么...'"
              class="chat-input"
              :disabled="resumeGenerating"
              @keydown.enter="sendMessage"
            >
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
        <pre class="modal-body">{{ jdViewerContent }}</pre>
      </div>
    </div>

    <div v-if="resumeViewerOpen" class="modal-overlay" @click.self="resumeViewerOpen = false">
      <div class="modal-panel">
        <div class="modal-header">
          <span>{{ resumeViewerTitle }}</span>
          <button type="button" class="modal-close" @click="resumeViewerOpen = false">×</button>
        </div>
        <pre class="modal-body">{{ resumeViewerContent }}</pre>
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
import { marked } from 'marked'
import { authStore } from '../stores/authStore'
import {
  createAgentSession,
  getAgentTrace,
  sendAgentMessageStream,
} from '../api/agent'
import { getWorkspace, getMessages, postAction, openResumeGenerateStream, LAST_WORKSPACE_CREATE_KEY } from '../api/workspace'
import { getOpportunityDetail } from '../api/opportunity'
import { downloadVersionDocx, downloadVersionPdf, getVersion, listVersions } from '../api/resumeVersion'
import { isCareerTaskToolName, notifyCareerTasksUpdated } from '../utils/agentToolDisplay'
import ToolCallCard from '../components/agent/ToolCallCard.vue'
import ChatCard from '../components/ChatCard.vue'
import { getToolLabel, isBusinessToolName, sanitizeToolSummary } from '../utils/agentToolDisplay'

const route = useRoute()
const router = useRouter()

marked.setOptions({
  breaks: true,
  gfm: true,
  async: false,
})

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function renderMd(text) {
  if (text == null || text === '') return ''
  const raw = typeof text === 'string' ? text : String(text)
  try {
    const html = marked.parse(raw, { async: false })
    if (html instanceof Promise) return `<p>${escapeHtml(raw)}</p>`
    return String(html)
  } catch {
    return `<p>${escapeHtml(raw).replace(/\n/g, '<br>')}</p>`
  }
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
const sessionCreating = ref(false)
const globalError = ref('')
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
const versionsDrawerOpen = ref(false)
const workspaceVersions = ref([])
const pdfDownloading = ref(false)
const wordDownloading = ref(false)
const currentTraceId = ref('')

const STREAM_UI_IDLE_NOTICE_MS = Number(import.meta.env.VITE_AGENT_STREAM_UI_IDLE_NOTICE_MS || 90000)

const hasResumeVersion = computed(() => workspaceVersions.value.length > 0)

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
  globalError.value = message
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
    globalError.value = message
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

function openContext() {
  if (!workspaceInfo.value) return
  versionsDrawerOpen.value = true
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
  globalError.value = ''
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
    globalError.value = msg
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
    await startResumeGeneration(payload)
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
    resumeViewerTitle.value = detail?.versionName || '简历预览'
    resumeViewerContent.value = detail?.contentMarkdown || ''
    resumeViewerOpen.value = true
    versionsDrawerOpen.value = false
  } catch (e) {
    globalError.value = e?.message || '加载简历版本失败'
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

async function startResumeGeneration(jdId) {
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
    const ack = await postAction(sessionId.value, 'GENERATE_RESUME', jdId || workspaceInfo.value?.jdId)
    if (ack?.noop) {
      throw new Error('无法启动简历生成')
    }

    if (activeResumeStream.value?.close) {
      activeResumeStream.value.close()
    }

    activeResumeStream.value = openResumeGenerateStream(sessionId.value, {
      onResumeDelta(delta) {
        streamMsg.text += delta || ''
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
  globalError.value = ''
  streamState.value = 'session_creating'
  try {
    sessionId.value = await createAgentSession()
    streamState.value = 'idle'
    if (withWelcome) {
      messages.value = [defaultWelcomeMessage()]
    }
  } catch (e) {
    streamState.value = 'error'
    globalError.value = e?.message || '会话创建失败'
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

async function bootstrapChat() {
  if (workspaceId.value) {
    await loadWorkspaceContext(workspaceId.value)
    return
  }
  await createNewSession({ withWelcome: true })
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streamState.value === 'streaming' || sessionCreating.value) return
  if (!sessionId.value) {
    await createNewSession({ withWelcome: false })
    if (!sessionId.value) return
  }
  globalError.value = ''
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
    await sendAgentMessageStream(sessionId.value, text, {
      onTraceHeader({ traceId }) {
        if (traceId) {
          currentTraceId.value = traceId
        }
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
        scrollBottom()
      },
      onMessage(data) {
        if (data?.content) {
          agentMessage.text = data.content
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
        globalError.value = agentMessage.error
      },
    }, {
      signal: streamController.signal,
    })
    if (streamState.value === 'streaming') {
      streamState.value = 'done'
      finishStreaming(agentMessage)
    }
  } catch (e) {
    streamState.value = 'error'
    finishStreaming(agentMessage)
    agentMessage.error = e?.message || '流式请求失败'
    globalError.value = agentMessage.error
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
      globalError.value = agentMessage.error
    }
    if (!agentMessage.text) {
      agentMessage.text = '暂未收到回复，请稍后重试。'
    }
    finishStreaming(agentMessage)
    scrollBottom()
  }
}

async function resetChat() {
  abortActiveStream('用户已停止当前 Agent 流式请求')
  if (activeAgentMessage.value?.streaming) {
    markStreamInterrupted(activeAgentMessage.value, '当前流式请求已停止，已切换到新会话。')
  }
  activeAgentMessage.value = null
  globalError.value = ''
  streamState.value = 'idle'
  sessionId.value = ''
  messages.value = [withMarkdown({
    id: `m_${Date.now()}_reset`,
    role: 'agent',
    text: '新会话已重置。你可以继续提问。',
    streaming: false,
    error: '',
    toolCalls: [],
  })]
  await createNewSession({ withWelcome: false })
  scrollBottom()
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
  await bootstrapChat()
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

.modal-body {
  padding: 16px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
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
  background: #f8fafc;
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
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.chat-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
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
  color: #15803d;
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
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
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
  color: #0f172a;
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
  color: #0f172a;
  flex-shrink: 0;
}

.header-title { font-weight: 700; font-size: 13px; color: #0f172a; }
.header-sub { font-size: 10px; color: #10b981; }
.header-sub--ready { color: #10b981; }
.header-sub--pending { color: #f59e0b; }

.header-actions {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-shrink: 0;
}

.trace-id-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: min(280px, 42vw);
  padding: 4px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
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
  color: #4f46e5;
  font-size: 10px;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
}

.header-action {
  background: none; border: 1px solid #e2e8f0; padding: 5px 12px;
  border-radius: 6px; font-size: 11px; cursor: pointer; color: #64748b;
}

.header-action:hover { background: #f8fafc; }
.header-action:disabled { opacity: .5; cursor: default; }

.global-error {
  margin: 10px 16px 0;
  padding: 8px 10px;
  border: 1px solid #fecaca;
  background: #fff1f2;
  color: #b91c1c;
  font-size: 12px;
  border-radius: 8px;
}

.chat-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
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
  background: #4f46e5;
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
  color: #0f172a;
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
  color: #4f46e5;
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
  color: #4f46e5;
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
  background: #f8fafc;
  font-weight: 700;
  color: #0f172a;
}

.stream-flag { display: inline-block; color: #4f46e5; font-size: 13px; line-height: 1; animation: blink 1s step-start infinite; margin-left: 1px; }
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

.stream-error { margin-top: 4px; color: #dc2626; font-size: 11px; }

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

.input-row {
  display: flex;
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 6px 6px 6px 14px;
}

.chat-input { flex: 1; border: none; background: transparent; padding: 6px; outline: none; font-size: 12px; font-family: inherit; }

.send-btn {
  flex-shrink: 0;
  padding: 6px 14px;
  background: #4f46e5;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity .2s;
  white-space: nowrap;
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
