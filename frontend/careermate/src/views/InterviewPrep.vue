<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">🎤 面试特训</h2>
      <p class="page-sub">字节跳动 后端开发 · 个性化出题</p>
    </div>

    <div class="interview-layout">
      <!-- Main Interview Area -->
      <div class="main-area">
        <!-- Question Card -->
        <div class="question-card">
          <div class="question-meta">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题 · 技术面试</div>
          <div class="question-text">"{{ currentQuestion.text }}"</div>
          <div class="question-source">Agent 出题依据: {{ currentQuestion.source }}</div>
        </div>

        <!-- Answer Area -->
        <div class="answer-area">
          <textarea
            v-model="answer"
            placeholder="输入你的回答..."
            class="answer-input"
            :disabled="answered"
          ></textarea>
        </div>

        <!-- Action Buttons -->
        <div class="action-row">
          <button class="btn primary" @click="submitAnswer" :disabled="!answer.trim() || answered">
            {{ answered ? '已提交' : '提交回答' }}
          </button>
          <button class="btn outline" @click="voiceAnswer">
            🎤 语音回答
          </button>
          <button class="btn outline" @click="skipQuestion">
            ⏭ 跳过
          </button>
        </div>

        <!-- Feedback (after submit) -->
        <div v-if="feedback" class="feedback-card">
          <div class="feedback-header">🤖 Agent 即时反馈</div>
          <div class="feedback-content">{{ feedback }}</div>
          <button class="btn primary" @click="nextQuestion" style="margin-top:10px;">
            {{ currentIndex + 1 < questions.length ? '下一题 →' : '完成特训' }}
          </button>
        </div>

        <!-- Progress Bar -->
        <div class="progress-bar">
          <div
            v-for="(q, i) in questions"
            :key="i"
            class="progress-seg"
            :class="{ done: q.done, current: i === currentIndex && !q.done }"
            :style="{ flex: 1 }"
          ></div>
        </div>
      </div>

      <!-- Right: Evaluation Panel -->
      <div class="eval-panel">
        <div class="panel-title">📊 已完成的评估</div>

        <div v-for="(ev, i) in evaluations" :key="i" class="eval-item">
          <div class="eval-question" :class="ev.qualityClass">Q{{ ev.num }}: {{ ev.topic }}</div>
          <div class="eval-detail">回答质量: {{ ev.quality }} · {{ ev.tip }}</div>
        </div>

        <div v-if="evaluations.length === 0" class="eval-empty">
          <div class="empty-icon">🎯</div>
          <div class="empty-text">开始答题后，Agent 会实时评估你的回答质量</div>
        </div>

        <div class="panel-divider"></div>

        <div class="panel-title-sm">🤖 Agent 出题策略</div>
        <div class="strategy-item">· 基于你的简历弱点出题</div>
        <div class="strategy-item">· 参考字节面试风格</div>
        <div class="strategy-item">· 覆盖技术+行为+系统设计</div>
        <div class="tool-log">
          🔧 RAGForge.search("字节 后端 面试题")<br>
          🔧 RAGForge.search("系统设计 微服务拆分")
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const answer = ref('')
const answered = ref(false)
const feedback = ref('')
const currentIndex = ref(0)

const questions = ref([
  { text: '请简述 Spring Boot 自动配置的核心原理，以及如何自定义一个 Starter？', source: '你的简历「Spring Boot」+ 字节JD「框架深度」', done: false },
  { text: '你在简历中提到使用 Redis 做缓存，请谈谈缓存穿透、缓存击穿、缓存雪崩的区别和解决方案？', source: '你的简历「Redis」+ 字节JD「高并发处理」', done: false },
  { text: '你在简历中提到微服务项目，请问你们的服务拆分粒度是怎么确定的？如果让你重新设计，会怎么改进？', source: '你的简历「微服务项目」+ 字节JD「系统设计能力」', done: false },
  { text: '谈谈你对 CAP 理论的理解，以及在分布式系统中如何权衡？', source: '字节JD「分布式系统」- 你的技能差距', done: false },
  { text: '你做过的最有挑战性的技术项目是什么？请画一下架构图并说明关键决策。', source: '字节面试风格「项目深挖」', done: false },
  { text: '如果线上服务突然出现大量超时，你会怎么排查和解决？', source: '字节JD「问题排查能力」', done: false },
  { text: '谈谈你对消息队列的理解，Kafka 和 RocketMQ 各有什么适用场景？', source: '你的技能差距「消息队列」', done: false },
  { text: '请设计一个支持百万并发的短链接系统。', source: '字节JD「系统设计」+ 面试高频题', done: false },
])

const evaluations = ref([])

const currentQuestion = computed(() => questions.value[currentIndex.value])

function submitAnswer() {
  if (!answer.value.trim() || answered.value) return
  answered.value = true
  const quality = Math.random() > 0.3 ? '良好' : '优秀'
  const tips = [
    'Agent 提示: 可以补充实际案例来增强说服力',
    'Agent 提示: 结构清晰，如果加上性能数据会更好',
    'Agent 提示: 回答全面，建议强调一下你自己的思考',
  ]
  feedback.value = `回答评估: ${quality}。${tips[Math.floor(Math.random() * tips.length)]}`

  questions.value[currentIndex.value].done = true
  evaluations.value.push({
    num: currentIndex.value + 1,
    topic: currentQuestion.value.text.slice(0, 18) + '...',
    quality,
    qualityClass: quality === '优秀' ? 'excellent' : 'good',
    tip: tips[Math.floor(Math.random() * tips.length)],
  })
}

function nextQuestion() {
  if (currentIndex.value + 1 < questions.value.length) {
    currentIndex.value++
    answer.value = ''
    answered.value = false
    feedback.value = ''
  } else {
    router.push('/')
  }
}

function skipQuestion() {
  questions.value[currentIndex.value].done = true
  evaluations.value.push({
    num: currentIndex.value + 1,
    topic: currentQuestion.value.text.slice(0, 18) + '...',
    quality: '跳过',
    qualityClass: 'skip',
    tip: '建议之后回来完成',
  })
  nextQuestion()
}

function voiceAnswer() {
  answer.value = '（模拟语音输入）Spring Boot 的自动配置原理基于 @EnableAutoConfiguration 注解...'
}
</script>

<style scoped>
.page-container { max-width: 1100px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 18px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.interview-layout { display: grid; grid-template-columns: 1fr 280px; gap: 20px; }

/* Main */
.main-area { display: flex; flex-direction: column; gap: 16px; }

.question-card {
  background: var(--navy); border-radius: 12px; padding: 18px 20px; color: #fff;
}
.question-meta { font-size: 10px; opacity: .5; margin-bottom: 8px; }
.question-text { font-size: 14px; line-height: 1.7; }
.question-source { font-size: 10px; opacity: .4; margin-top: 10px; }

.answer-input {
  width: 100%; height: 110px; border: 1px solid var(--border); border-radius: 10px;
  padding: 12px; font-size: 12px; resize: none; font-family: inherit; outline: none;
  transition: border-color .2s;
}
.answer-input:focus { border-color: var(--purple); }
.answer-input:disabled { background: #f8f8f8; color: var(--text-muted); }

.action-row { display: flex; gap: 10px; }
.btn {
  padding: 8px 18px; border-radius: 8px; font-size: 12px; cursor: pointer; border: none; transition: all .2s; font-family: inherit;
}
.btn.primary { background: var(--purple); color: #fff; }
.btn.primary:hover { background: #7c3aed; }
.btn.primary:disabled { opacity: .4; cursor: default; }
.btn.outline { background: #fff; border: 1px solid var(--border); color: var(--slate); }
.btn.outline:hover { border-color: var(--purple); color: var(--purple); }

.feedback-card {
  background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 10px; padding: 14px;
}
.feedback-header { font-weight: 600; font-size: 12px; color: var(--green); margin-bottom: 4px; }
.feedback-content { font-size: 12px; color: var(--slate); }

/* Progress */
.progress-bar { display: flex; gap: 4px; margin-top: 4px; }
.progress-seg { height: 4px; border-radius: 2px; background: var(--light); transition: background .3s; }
.progress-seg.done { background: var(--green); }
.progress-seg.current { background: var(--purple); }

/* Eval Panel */
.eval-panel {
  background: #f8fafc; border: 1px solid var(--border); border-radius: 12px; padding: 14px; font-size: 11px;
}
.panel-title { font-weight: 600; font-size: 12px; margin-bottom: 10px; }
.panel-title-sm { font-weight: 600; margin-bottom: 4px; font-size: 11px; }
.panel-divider { border-top: 1px solid var(--border); margin: 10px 0; }

.eval-item { margin-bottom: 10px; }
.eval-question { font-weight: 600; margin-bottom: 2px; }
.eval-question.excellent { color: var(--green); }
.eval-question.good { color: var(--green); }
.eval-question.skip { color: var(--text-muted); }
.eval-detail { color: var(--text-muted); font-size: 10px; }

.eval-empty { text-align: center; padding: 20px 0; color: var(--text-muted); }
.empty-icon { font-size: 28px; margin-bottom: 6px; }
.empty-text { font-size: 11px; }

.strategy-item { color: var(--text-muted); font-size: 10px; padding: 1px 0; }
.tool-log {
  background: #fffbeb; border: 1px solid #fde68a; border-radius: 6px;
  padding: 8px 10px; font-size: 10px; margin-top: 8px; color: var(--amber); line-height: 1.6;
}
</style>
