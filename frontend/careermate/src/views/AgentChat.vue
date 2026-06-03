<template>
  <div class="chat-page">
    <div class="chat-layout">
      <div class="chat-main">
        <div class="chat-header">
          <div class="header-left">
            <div class="ai-badge">AI</div>
            <div>
              <div class="header-title">Agent 对话台</div>
              <div class="header-sub">CareerMate · 一切交互的起点和终点</div>
            </div>
          </div>
          <button class="header-action" :disabled="sessionCreating || streamState === 'streaming'" @click="resetChat">
            {{ sessionCreating ? '创建中...' : '🔄 新会话' }}
          </button>
        </div>
        <div v-if="globalError" class="global-error">{{ globalError }}</div>

        <div class="messages-area" ref="msgContainer">
          <div v-for="msg in messages" :key="msg.id" class="msg-wrapper">
            <div v-if="msg.role === 'user'" class="msg-row user-row">
              <div class="msg-bubble user-bubble">{{ msg.text }}</div>
            </div>

            <div v-else class="msg-row agent-row">
              <div class="ai-avatar">AI</div>
              <div class="msg-bubble agent-bubble">
                <div>{{ msg.text }}</div>
                <div v-if="msg.streaming" class="stream-flag">流式输出中...</div>
                <div v-if="msg.error" class="stream-error">{{ msg.error }}</div>
              </div>
            </div>
          </div>

        </div>

        <div class="input-area">
          <div class="suggestions">
            <span v-for="s in suggestions" :key="s" class="sug-chip" @click="sendSuggestion(s)">💡 {{ s }}</span>
          </div>
          <div class="input-row">
            <span class="mic-btn">🎤</span>
            <input
              v-model="inputText"
              placeholder="说说你想做什么..."
              class="chat-input"
              @keydown.enter="sendMessage"
            >
            <button class="send-btn" @click="sendMessage" :disabled="!canSend">↑</button>
          </div>
        </div>
      </div>

      <div class="session-panel">
        <div class="panel-title">📋 当前会话</div>
        <div class="panel-section">
          <div class="panel-label">当前用户：</div>
          <div class="panel-value">{{ userLabel }}</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">sessionId：</div>
          <div class="panel-value">{{ sessionId || '创建中...' }}</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">当前状态：</div>
          <div class="panel-value">{{ streamStateLabel }}</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">已接收事件数：</div>
          <div class="panel-value">{{ eventCount }}</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">最后耗时：</div>
          <div class="panel-value">{{ totalLatencyMs ? `${totalLatencyMs}ms` : '-' }}</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">工具调用：</div>
          <div class="panel-value">暂无工具调用</div>
        </div>
        <div class="panel-divider" />
        <div class="panel-title-sm trace-header">
          <span>🧠 Agent Trace / 执行轨迹</span>
          <button
            class="trace-refresh-btn"
            :disabled="!sessionId || traceLoading"
            @click="refreshTraceFromServer"
          >
            {{ traceLoading ? '刷新中...' : '刷新 Trace' }}
          </button>
        </div>
        <div v-if="traceEvents.length === 0" class="tool-log">暂无 trace 事件</div>
        <div v-for="trace in traceEvents" :key="trace.id" class="tool-log">
          [{{ trace.type }}] {{ trace.title }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { authStore } from '../stores/authStore'
import { createAgentSession, getAgentTrace, sendAgentMessageStream } from '../api/agent'

const inputText = ref('')
const msgContainer = ref(null)
const sessionId = ref('')
const streamState = ref('idle')
const sessionCreating = ref(false)
const globalError = ref('')
const eventCount = ref(0)
const totalLatencyMs = ref(0)
const traceEvents = ref([])
const traceLoading = ref(false)
const idSeed = ref(0)

const suggestions = ['帮我优化简历', '匹配后端岗位', '准备 Java 面试']

const messages = ref([{
  id: 'm_init',
  role: 'agent',
  text: '你好！我是 CareerMate 求职助手。你可以直接提问，比如“帮我分析简历”。',
  streaming: false,
  error: '',
}])

const canSend = computed(() => !!inputText.value.trim() && streamState.value !== 'streaming')
const streamStateLabel = computed(() => {
  if (streamState.value === 'session_creating') return '会话创建中'
  if (streamState.value === 'streaming') return '流式生成中'
  if (streamState.value === 'done') return '已完成'
  if (streamState.value === 'error') return '错误'
  return '空闲'
})
const userLabel = computed(() => {
  const user = authStore.state.currentUser
  if (!user) return '未登录'
  return `${user.username} / ${user.role}`
})

function scrollBottom() {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}

function sendSuggestion(text) {
  if (streamState.value === 'streaming') return
  inputText.value = text
  sendMessage()
}

function formatTraceTitle(t) {
  const type = t.toolName || t.type || 'trace'
  if (t.responseSummary) {
    try {
      const summary = JSON.parse(t.responseSummary)
      if (summary?.message) {
        return summary.message
      }
    } catch {
      // 非 JSON 时沿用默认格式
    }
  }
  const latency = t.latencyMs ? ` · ${t.latencyMs}ms` : ''
  return `${type} · ${t.status || ''}${latency}`.trim()
}

function pushTrace(type, title, payload = null) {
  traceEvents.value.push({
    id: `t_${Date.now()}_${idSeed.value++}`,
    type,
    title,
    payload,
    timestamp: Date.now(),
  })
}

async function refreshTraceFromServer() {
  if (!sessionId.value || traceLoading.value) return
  traceLoading.value = true
  try {
    const traces = await getAgentTrace(sessionId.value)
    traceEvents.value = traces.map((t) => ({
      id: `db_${t.id}`,
      type: t.toolName || t.type,
      title: formatTraceTitle(t),
      payload: t,
      timestamp: t.createdAt ? new Date(t.createdAt).getTime() : Date.now(),
    }))
    if (traces.length === 0) {
      pushTrace('refresh', '服务端暂无 trace 记录')
    }
  } catch (e) {
    pushTrace('error', e?.message || '刷新 trace 失败')
  } finally {
    traceLoading.value = false
  }
}

async function initSession() {
  sessionCreating.value = true
  globalError.value = ''
  streamState.value = 'session_creating'
  try {
    sessionId.value = await createAgentSession()
    streamState.value = 'idle'
    pushTrace('session', `会话创建成功: ${sessionId.value}`)
  } catch (e) {
    streamState.value = 'error'
    globalError.value = e?.message || '会话创建失败'
    pushTrace('error', '会话创建失败')
    messages.value.push({
      id: `m_${Date.now()}`,
      role: 'agent',
      text: '会话创建失败，请刷新后重试。',
      streaming: false,
      error: e?.message || '',
    })
  } finally {
    sessionCreating.value = false
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || streamState.value === 'streaming' || sessionCreating.value) return
  if (!sessionId.value) {
    await initSession()
    if (!sessionId.value) return
  }
  globalError.value = ''

  messages.value.push({ id: `m_${Date.now()}_u`, role: 'user', text, streaming: false, error: '' })
  inputText.value = ''
  scrollBottom()

  const agentMessage = {
    id: `m_${Date.now()}_a`,
    role: 'agent',
    text: '',
    streaming: true,
    error: '',
  }
  messages.value.push(agentMessage)
  streamState.value = 'streaming'
  eventCount.value = 0
  totalLatencyMs.value = 0
  scrollBottom()

  try {
    await sendAgentMessageStream(sessionId.value, text, {
      onRawEvent() {
        eventCount.value += 1
      },
      onPlan(data) {
        const steps = Array.isArray(data?.steps) ? data.steps.join(' -> ') : '执行计划已生成'
        pushTrace('plan', steps, data)
      },
      onToolStart(data) {
        const name = data?.toolName || 'unknown'
        const summary = data?.summary || '正在执行工具'
        pushTrace('tool_start', `正在执行 ${name}：${summary}`, data)
      },
      onToolResult(data) {
        const name = data?.toolName || 'unknown'
        const status = data?.success ? '执行成功' : '执行失败'
        const summary = data?.summary || ''
        pushTrace('tool_result', `${name} ${status}${summary ? `：${summary}` : ''}`, data)
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
        agentMessage.streaming = false
        pushTrace('message', '收到完整回复', data)
      },
      onDone(data) {
        streamState.value = 'done'
        totalLatencyMs.value = Number(data?.totalLatencyMs || 0)
        agentMessage.streaming = false
        pushTrace('done', `流式完成，耗时 ${totalLatencyMs.value}ms`, data)
        refreshTraceFromServer()
      },
      onError(error) {
        streamState.value = 'error'
        agentMessage.streaming = false
        agentMessage.error = error?.message || '流式调用失败'
        globalError.value = agentMessage.error
        pushTrace('error', agentMessage.error)
      },
    })
    if (streamState.value === 'streaming') {
      streamState.value = 'done'
      agentMessage.streaming = false
    }
  } catch (e) {
    streamState.value = 'error'
    agentMessage.streaming = false
    agentMessage.error = e?.message || '流式请求失败'
    globalError.value = agentMessage.error
    pushTrace('error', agentMessage.error)
  } finally {
    if (streamState.value === 'streaming') {
      streamState.value = 'error'
      agentMessage.streaming = false
      agentMessage.error = '流式响应未正常结束，请重试。'
      globalError.value = agentMessage.error
      pushTrace('error', agentMessage.error)
    }
    if (!agentMessage.text) {
      agentMessage.text = '暂未收到回复，请稍后重试。'
    }
    scrollBottom()
  }
}

function resetChat() {
  globalError.value = ''
  messages.value = [{
    id: `m_${Date.now()}_reset`,
    role: 'agent',
    text: '新会话已重置。你可以继续提问。',
    streaming: false,
    error: '',
  }]
  traceEvents.value = []
  streamState.value = 'idle'
  eventCount.value = 0
  totalLatencyMs.value = 0
  sessionId.value = ''
  initSession().then(() => {
    scrollBottom()
  })
}

onMounted(async () => {
  await initSession()
  scrollBottom()
})
</script>

<style scoped>
.chat-page {
  max-width: 1200px;
  margin: 0 auto;
  height: calc(100dvh - 44px - 84px - env(safe-area-inset-bottom));
  min-height: 520px;
  overflow: hidden;
}
.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  height: 100%;
  min-height: 0;
}

.chat-header {
  padding: 14px 18px; background: #fff; border-bottom: 1px solid var(--border);
  display: flex; justify-content: space-between; align-items: center;
}
.header-left { display: flex; align-items: center; gap: 10px; }
.ai-badge {
  width: 32px; height: 32px; border-radius: 50%;
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
  display: flex; align-items: center; justify-content: center; color: #fff;
  font-size: 11px; font-weight: 700;
}
.header-title { font-weight: 700; font-size: 15px; }
.header-sub { font-size: 11px; color: var(--text-muted); }
.header-action {
  background: none; border: 1px solid var(--border); padding: 5px 12px;
  border-radius: 6px; font-size: 11px; cursor: pointer; color: var(--text-muted);
}
.header-action:hover { background: var(--light); }
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
.agent-row { gap: 8px; align-items: flex-start; }
.ai-avatar {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 10px; font-weight: 700;
}
.msg-bubble {
  max-width: 75%; padding: 10px 14px; border-radius: 10px; font-size: 12px; line-height: 1.7;
}
.user-bubble { background: var(--purple); color: #fff; border-radius: 10px 10px 0 10px; }
.agent-bubble { background: #fff; border: 1px solid var(--border); border-radius: 10px 10px 10px 0; }
.stream-flag { margin-top: 4px; color: var(--purple); font-size: 11px; }
.stream-error { margin-top: 4px; color: var(--red); font-size: 11px; }

.input-area {
  flex-shrink: 0;
  border-top: 1px solid var(--border);
  padding: 12px 16px;
  background: #fff;
  z-index: 10;
}
.suggestions { display: flex; gap: 8px; margin-bottom: 8px; font-size: 10px; overflow-x: auto; }
.sug-chip {
  color: var(--purple); cursor: pointer; white-space: nowrap; padding: 2px 8px; border-radius: 10px; background: #f5f3ff;
  transition: background .2s;
}
.sug-chip:hover { background: #ede9fe; }
.input-row { display: flex; align-items: center; background: var(--light); border: 1px solid var(--border); border-radius: 10px; padding: 4px; }
.mic-btn { padding: 6px 10px; color: var(--text-muted); font-size: 14px; cursor: default; }
.chat-input { flex: 1; border: none; background: transparent; padding: 6px; outline: none; font-size: 12px; font-family: inherit; }
.send-btn {
  padding: 6px 12px; background: var(--purple); color: #fff; border: none; border-radius: 6px;
  font-size: 14px; cursor: pointer; transition: opacity .2s;
}
.send-btn:disabled { opacity: .4; cursor: default; }

.session-panel {
  background: #f8fafc;
  border-left: 1px solid var(--border);
  padding: 16px 12px;
  font-size: 10px;
  overflow-y: auto;
  min-height: 0;
}
.panel-title { font-weight: 700; font-size: 11px; margin-bottom: 12px; }
.panel-title-sm { font-weight: 600; margin-bottom: 6px; font-size: 10px; }
.trace-header { display: flex; align-items: center; justify-content: space-between; gap: 6px; }
.trace-refresh-btn {
  border: 1px solid var(--border); background: #fff; border-radius: 4px;
  font-size: 9px; padding: 2px 6px; cursor: pointer; color: var(--text-muted);
}
.trace-refresh-btn:disabled { opacity: .5; cursor: default; }
.trace-refresh-btn:not(:disabled):hover { background: var(--light); }
.panel-section { margin-bottom: 10px; }
.panel-label { font-weight: 600; margin-bottom: 2px; }
.panel-value { color: var(--slate); word-break: break-all; }
.panel-divider { border-top: 1px solid var(--border); margin: 10px 0; }
.tool-log { color: var(--text-muted); padding: 2px 0; line-height: 1.4; }

@media (max-width: 768px) {
  .chat-page {
    max-width: 100%;
    margin: 0;
    height: calc(100dvh - 40px - 84px - env(safe-area-inset-bottom));
    min-height: 420px;
    overflow: hidden;
  }

  .chat-layout {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
  }

  .chat-main {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  .session-panel {
    display: none;
  }

  .input-area {
    flex-shrink: 0;
    padding-bottom: calc(12px + env(safe-area-inset-bottom));
  }

  .msg-bubble {
    max-width: 88%;
  }

  .suggestions {
    scrollbar-width: none;
    -ms-overflow-style: none;
  }

  .suggestions::-webkit-scrollbar {
    display: none;
  }

  .chat-input {
    min-height: 44px;
    font-size: 16px;
  }

  .send-btn {
    min-height: 44px;
    min-width: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .mic-btn {
    min-height: 44px;
    min-width: 44px;
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
    max-width: 90%;
    font-size: 13px;
  }

  .panel-value {
    word-break: break-all;
    overflow-wrap: anywhere;
  }

  .trace-header {
    flex-wrap: wrap;
  }

  .trace-refresh-btn {
    min-height: 32px;
  }
}
</style>
