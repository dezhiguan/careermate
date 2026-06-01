<template>
  <div class="chat-page">
    <div class="chat-layout">
      <!-- Main Chat Area -->
      <div class="chat-main">
        <div class="chat-header">
          <div class="header-left">
            <div class="ai-badge">AI</div>
            <div>
              <div class="header-title">Agent 对话台</div>
              <div class="header-sub">CareerMate · 一切交互的起点和终点</div>
            </div>
          </div>
          <button class="header-action" @click="resetChat">🔄 新会话</button>
        </div>

        <!-- Messages -->
        <div class="messages-area" ref="msgContainer">
          <div v-for="(msg, i) in messages" :key="i" class="msg-wrapper">
            <!-- User Message -->
            <div v-if="msg.role === 'user'" class="msg-row user-row">
              <div class="msg-bubble user-bubble">{{ msg.text }}</div>
            </div>

            <!-- Agent Thinking (collapsible) -->
            <div v-else-if="msg.role === 'thinking'" class="thinking-block">
              <div class="thinking-header" @click="msg.expanded = !msg.expanded">
                <span>🧠 Agent 思考中...</span>
                <span class="expand-icon">{{ msg.expanded ? '▼' : '▶' }}</span>
              </div>
              <div v-if="msg.expanded" class="thinking-body">
                <div v-for="(step, si) in msg.steps" :key="si" class="thinking-step">{{ step }}</div>
              </div>
            </div>

            <!-- Tool Call -->
            <div v-else-if="msg.role === 'tool'" class="tool-block">
              <div class="tool-name">🔧 调用工具: {{ msg.toolName }}</div>
              <div class="tool-detail">{{ msg.detail }}</div>
            </div>

            <!-- Agent Reply -->
            <div v-else-if="msg.role === 'agent'" class="msg-row agent-row">
              <div class="ai-avatar">AI</div>
              <div class="msg-bubble agent-bubble">
                <div v-html="msg.html || msg.text"></div>
                <!-- Navigation card embedded in message -->
                <div v-if="msg.navCard" class="nav-card" @click="$router.push(msg.navCard.route)">
                  {{ msg.navCard.label }}
                </div>
              </div>
            </div>
          </div>

          <!-- Typing indicator -->
          <div v-if="typing" class="msg-row agent-row">
            <div class="ai-avatar">AI</div>
            <div class="typing-dots"><span></span><span></span><span></span></div>
          </div>
        </div>

        <!-- Input Area -->
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
            <button class="send-btn" @click="sendMessage" :disabled="!inputText.trim()">↑</button>
          </div>
        </div>
      </div>

      <!-- Right Session Panel -->
      <div class="session-panel">
        <div class="panel-title">📋 当前会话</div>
        <div class="panel-section">
          <div class="panel-label">已加载简历：</div>
          <div class="panel-value">后端开发_3年.pdf</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">关注方向：</div>
          <div class="panel-value">后端开发 · 大厂</div>
        </div>
        <div class="panel-section">
          <div class="panel-label">会话步骤：</div>
          <div v-for="s in sessionSteps" :key="s.label" class="step-item" :class="s.status">
            {{ s.icon }} {{ s.label }}
          </div>
        </div>
        <div class="panel-divider"></div>
        <div class="panel-title-sm">🔧 已调用的工具</div>
        <div v-for="t in calledTools" :key="t" class="tool-log">{{ t }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const inputText = ref('')
const typing = ref(false)
const msgContainer = ref(null)

const suggestions = ['帮我优化简历', '匹配后端岗位', '准备字节面试']

const messages = ref([
  {
    role: 'user',
    text: '帮我看看我的简历适合投哪些大厂的后端岗位？',
  },
  {
    role: 'thinking',
    expanded: false,
    steps: [
      '观察: 用户想匹配后端岗位，需要先有简历数据',
      '规划: ① 检查简历库是否有简历 ② 搜索JD库中的后端岗位 ③ 逐一匹配分析 ④ 生成推荐列表',
      '决策: 简历库已有1份简历，直接进入匹配流程',
    ],
  },
  {
    role: 'tool',
    toolName: 'RAGForge.search()',
    detail: 'query: "后端开发工程师 大厂 2026" · kb: jd_library · strategy: hybrid_rerank · 返回 8 条结果 · 耗时 452ms',
  },
  {
    role: 'agent',
    html: `<p>找到了 <strong>6 个大厂后端岗位</strong>和你的简历匹配。按匹配度排序：</p>
<p>🥇 字节跳动 · 后端开发 — <strong style="color:#10b981;">匹配度 78%</strong></p>
<p>🥈 美团 · 后端工程师 — <strong style="color:#10b981;">匹配度 74%</strong></p>
<p>🥉 腾讯 · 后台开发 — <strong style="color:#f59e0b;">匹配度 68%</strong></p>`,
    navCard: { label: '🎯 查看全部岗位匹配详情 →', route: '/match' },
  },
])

const sessionSteps = ref([
  { label: '简历已解析', status: 'done', icon: '✓' },
  { label: '岗位匹配中', status: 'active', icon: '●' },
  { label: '简历优化', status: 'pending', icon: '○' },
  { label: '面试准备', status: 'pending', icon: '○' },
])

const calledTools = ref([
  'RAGForge.search × 2',
  '简历解析 × 1',
  '匹配度计算 × 6',
])

function scrollBottom() {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}

function sendSuggestion(text) {
  inputText.value = text
  sendMessage()
}

function sendMessage() {
  const text = inputText.value.trim()
  if (!text) return
  messages.value.push({ role: 'user', text })
  inputText.value = ''
  scrollBottom()

  // Simulate agent processing
  typing.value = true
  scrollBottom()

  setTimeout(() => {
    typing.value = false
    // Push thinking
    const thinkingMsg = {
      role: 'thinking',
      expanded: true,
      steps: getThinkingSteps(text),
    }
    messages.value.push(thinkingMsg)
    scrollBottom()

    setTimeout(() => {
      // Push tool call
      messages.value.push({
        role: 'tool',
        toolName: 'RAGForge.search()',
        detail: `query: "${text}" · kb: jd_library · hybrid_rerank · 返回 6 条结果 · 耗时 380ms`,
      })
      scrollBottom()

      setTimeout(() => {
        // Push agent reply
        messages.value.push({
          role: 'agent',
          html: getAgentReply(text),
          navCard: getNavCard(text),
        })
        scrollBottom()
      }, 600)
    }, 400)
  }, 1000)
}

function getThinkingSteps(text) {
  if (text.includes('优化') || text.includes('简历')) {
    return [
      '观察: 用户想优化简历，需要先分析现有简历内容',
      '规划: ① 读取用户简历 ② 分析技能结构 ③ 对比行业标准 ④ 生成优化建议',
      '决策: 简历已存在，直接进入分析流程',
    ]
  }
  if (text.includes('面试') || text.includes('准备')) {
    return [
      '观察: 用户想准备面试，需要目标岗位信息',
      '规划: ① 确认目标公司/岗位 ② 检索面试题库 ③ 基于简历+JD生成个性化题目',
      '决策: 已有匹配岗位数据，可直接生成面试题',
    ]
  }
  if (text.includes('匹配') || text.includes('岗位')) {
    return [
      '观察: 用户想匹配岗位，需要简历+JD库',
      '规划: ① 读取简历 ② 搜索JD库 ③ 逐一匹配计算 ④ 排序推荐',
      '决策: 简历已就绪，启动匹配流程',
    ]
  }
  return [
    '观察: 分析用户意图',
    '规划: 检索相关知识库',
    '决策: 直接回复用户',
  ]
}

function getAgentReply(text) {
  if (text.includes('优化') || text.includes('简历')) {
    return `<p>你的简历已解析完成。发现了 <strong>3 个优化点</strong>：</p>
<p>⚠️ 缺少分布式系统经验描述</p>
<p>⚠️ 性能优化成果未量化</p>
<p>💡 建议增加大模型相关项目</p>`
  }
  if (text.includes('面试') || text.includes('准备')) {
    return `<p>已为你生成了 <strong>8 道个性化面试题</strong>，覆盖：</p>
<p>✅ 技术基础 (Spring Boot / Redis)</p>
<p>✅ 系统设计 (微服务拆分 / 分布式)</p>
<p>✅ 项目经验深挖</p>`
  }
  if (text.includes('匹配') || text.includes('岗位')) {
    return `<p>找到了 <strong>6 个大厂后端岗位</strong>和你的简历匹配。按匹配度排序：</p>
<p>🥇 字节跳动 · 后端开发 — <strong style="color:#10b981;">匹配度 78%</strong></p>
<p>🥈 美团 · 后端工程师 — <strong style="color:#10b981;">匹配度 74%</strong></p>
<p>🥉 腾讯 · 后台开发 — <strong style="color:#f59e0b;">匹配度 68%</strong></p>`
  }
  return `<p>好的，我来帮你分析。你可以试试：</p>
<p>📝 上传并优化简历</p>
<p>🎯 匹配适合的岗位</p>
<p>🎤 准备面试特训</p>`
}

function getNavCard(text) {
  if (text.includes('优化') || text.includes('简历')) {
    return { label: '📝 打开简历工作室查看详情 →', route: '/resume' }
  }
  if (text.includes('面试') || text.includes('准备')) {
    return { label: '🎤 开始面试特训 →', route: '/interview' }
  }
  if (text.includes('匹配') || text.includes('岗位')) {
    return { label: '🎯 查看全部岗位匹配详情 →', route: '/match' }
  }
  return null
}

function resetChat() {
  messages.value = [
    {
      role: 'agent',
      text: '你好！我是 CareerMate 求职助手。你可以直接告诉我想做什么，比如"帮我优化简历"、"匹配后端岗位"或"准备面试"。',
    },
  ]
  sessionSteps.value[1].status = 'pending'
  sessionSteps.value[1].icon = '○'
}
</script>

<style scoped>
.chat-page { max-width: 1200px; margin: 0 auto; height: calc(100vh - 72px); }
.chat-layout { display: grid; grid-template-columns: 1fr 260px; height: 100%; }

/* Header */
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

/* Messages */
.chat-main { display: flex; flex-direction: column; height: 100%; }
.messages-area {
  flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 4px;
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
.msg-bubble p { margin: 0 0 4px; }
.msg-bubble p:last-child { margin: 0; }
.user-bubble { background: var(--purple); color: #fff; border-radius: 10px 10px 0 10px; }
.agent-bubble { background: #fff; border: 1px solid var(--border); border-radius: 10px 10px 10px 0; }

/* Thinking */
.thinking-block {
  background: #f5f3ff; border: 1px solid #ddd6fe; border-radius: 8px;
  padding: 10px 14px; margin: 4px 0; font-size: 11px; color: var(--purple); max-width: 85%;
}
.thinking-header {
  font-weight: 600; display: flex; justify-content: space-between; cursor: pointer; user-select: none;
}
.expand-icon { font-size: 9px; }
.thinking-body { margin-top: 6px; }
.thinking-step { padding: 3px 0 3px 14px; position: relative; }
.thinking-step::before { content: "▸"; position: absolute; left: 0; }

/* Tool */
.tool-block {
  background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px;
  padding: 10px 14px; margin: 4px 0; font-size: 11px; max-width: 85%;
}
.tool-name { font-weight: 600; color: var(--amber); }
.tool-detail { font-size: 10px; color: var(--text-muted); margin-top: 2px; }

/* Nav card in message */
.nav-card {
  margin-top: 8px; padding: 8px; background: var(--light);
  border-radius: 6px; border: 1px solid var(--purple); text-align: center;
  color: var(--purple); font-size: 11px; cursor: pointer; transition: all .2s;
}
.nav-card:hover { background: #f5f3ff; transform: translateY(-1px); }

/* Typing dots */
.typing-dots { display: flex; gap: 4px; align-items: center; padding: 10px 14px; background: #fff; border: 1px solid var(--border); border-radius: 10px; }
.typing-dots span { width: 6px; height: 6px; border-radius: 50%; background: var(--purple); animation: bounce 1.4s infinite ease-in-out both; }
.typing-dots span:nth-child(1) { animation-delay: -.32s; }
.typing-dots span:nth-child(2) { animation-delay: -.16s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* Input */
.input-area { border-top: 1px solid var(--border); padding: 12px 16px; background: #fff; }
.suggestions { display: flex; gap: 8px; margin-bottom: 8px; font-size: 10px; overflow-x: auto; }
.sug-chip {
  color: var(--purple); cursor: pointer; white-space: nowrap; padding: 2px 8px; border-radius: 10px; background: #f5f3ff;
  transition: background .2s;
}
.sug-chip:hover { background: #ede9fe; }
.input-row { display: flex; align-items: center; background: var(--light); border: 1px solid var(--border); border-radius: 10px; padding: 4px; }
.mic-btn { padding: 6px 10px; color: var(--text-muted); font-size: 14px; cursor: pointer; }
.chat-input { flex: 1; border: none; background: transparent; padding: 6px; outline: none; font-size: 12px; font-family: inherit; }
.send-btn {
  padding: 6px 12px; background: var(--purple); color: #fff; border: none; border-radius: 6px;
  font-size: 14px; cursor: pointer; transition: opacity .2s;
}
.send-btn:disabled { opacity: .4; cursor: default; }

/* Session panel */
.session-panel {
  background: #f8fafc; border-left: 1px solid var(--border); padding: 16px 12px; font-size: 10px; overflow-y: auto;
}
.panel-title { font-weight: 700; font-size: 11px; margin-bottom: 12px; }
.panel-title-sm { font-weight: 600; margin-bottom: 6px; font-size: 10px; }
.panel-section { margin-bottom: 10px; }
.panel-label { font-weight: 600; margin-bottom: 2px; }
.panel-value { color: var(--slate); }
.step-item { padding: 2px 0; }
.step-item.done { color: var(--green); }
.step-item.active { color: var(--purple); }
.step-item.pending { color: var(--text-muted); }
.panel-divider { border-top: 1px solid var(--border); margin: 10px 0; }
.tool-log { color: var(--text-muted); padding: 2px 0; }
</style>
