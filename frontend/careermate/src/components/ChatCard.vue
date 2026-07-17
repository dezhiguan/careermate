<template>
  <div
    class="chat-card"
    :class="`chat-card--${cardType}`"
    :data-testid="card.type === 'RESUME_GENERATED' ? 'resume-generated-card' : undefined"
  >
    <div v-if="card.type === 'OFFER_GENERATE_RESUME'" class="chat-card-body">
      <p class="chat-card-text">小职可以按这份 JD 帮你重写简历，选个操作吧：</p>
    </div>
    <div v-else-if="card.type === 'RESUME_GENERATED'" class="chat-card-body">
      <p class="chat-card-title">✅ {{ card.title || '简历已生成' }}</p>
      <p v-if="card.versionName" class="chat-card-subtitle">{{ card.versionName }}</p>
      <div v-if="card.previewMarkdown" class="chat-card-preview" v-html="previewHtml"></div>
    </div>
    <div v-else-if="card.type === 'GENERATE_FAILED'" class="chat-card-body">
      <p class="chat-card-error">{{ card.message || '生成失败，请重试' }}</p>
    </div>
    <div v-else-if="card.type === 'CONFIRM_ACTION'" class="chat-card-body">
      <p class="chat-card-title">{{ card.title || '请确认操作' }}</p>
      <p v-if="card.summary" class="chat-card-text">{{ card.summary }}</p>
      <p v-if="card.riskLabel" class="chat-card-risk">{{ card.riskLabel }}</p>
      <p v-if="expiresHint" class="chat-card-expiry">{{ expiresHint }}</p>
    </div>
    <div v-else-if="card.type === 'ACTION_CANCELLED'" class="chat-card-body">
      <p class="chat-card-title">{{ card.title || '操作已取消' }}</p>
      <p v-if="card.summary" class="chat-card-text">{{ card.summary }}</p>
    </div>
    <div v-else-if="card.type === 'COMPANY_ATMOSPHERE'" class="chat-card-body" data-testid="company-atmosphere-card">
      <p class="chat-card-title">🏢 {{ card.companyName || '公司' }} · 氛围</p>
      <template v-if="card.dataAvailable">
        <div v-if="cultureTags.length" class="cc-tags">
          <span
            v-for="(t, i) in cultureTags"
            :key="i"
            class="cc-tag"
            :class="tagSentimentClass(t.sentiment)"
          >{{ t.label }}</span>
        </div>
        <div v-if="card.overtimeSignal" class="cc-kv"><span class="cc-k">加班</span><span class="cc-v">{{ card.overtimeSignal }}</span></div>
        <div v-if="card.workIntensity" class="cc-kv"><span class="cc-k">工作强度</span><span class="cc-v">{{ card.workIntensity }}</span></div>
        <div v-if="card.teamReputation" class="cc-kv"><span class="cc-k">团队口碑</span><span class="cc-v">{{ card.teamReputation }}</span></div>
        <div v-if="card.interviewStyle" class="cc-kv"><span class="cc-k">面试风格</span><span class="cc-v">{{ card.interviewStyle }}</span></div>
        <p v-if="card.aiSummary" class="chat-card-text">{{ card.aiSummary }}</p>
        <p v-if="citationCount" class="cc-src">依据 {{ citationCount }} 条来源</p>
      </template>
      <p v-else class="cc-empty">暂无该公司的氛围情报，建议结合面试沟通进一步了解。</p>
    </div>
    <div v-else-if="card.type === 'INTERVIEW_QUESTIONS'" class="chat-card-body" data-testid="interview-questions-card">
      <p class="chat-card-title">🎤 {{ card.jdTitle || '本岗' }} · 面试题（{{ card.questionCount || questions.length }}）</p>
      <template v-if="card.dataAvailable && questions.length">
        <div v-for="(q, i) in questions" :key="i" class="cc-q">
          <span class="cc-q-no">{{ q.questionNo || i + 1 }}.</span>
          <span class="cc-q-text">{{ q.questionText }}</span>
          <span v-if="q.tag" class="cc-q-tag" :class="qTagClass(q.tag)">{{ qTagLabel(q.tag) }}</span>
        </div>
        <p v-if="card.aiSummary" class="chat-card-text">{{ card.aiSummary }}</p>
      </template>
      <p v-else class="cc-empty">未找到该岗位 JD，暂无法生成面试题。</p>
    </div>
    <div v-else-if="card.type === 'SALARY_GUIDANCE'" class="chat-card-body" data-testid="salary-guidance-card">
      <p class="chat-card-title">💰 薪酬谈判建议</p>
      <template v-if="card.dataAvailable">
        <div class="cc-sal">
          <div class="cc-sal-cell"><span class="cc-k">P25</span><span class="cc-sal-v">{{ card.p25 }}</span></div>
          <div class="cc-sal-cell hl"><span class="cc-k">P50</span><span class="cc-sal-v">{{ card.p50 }}</span></div>
          <div class="cc-sal-cell"><span class="cc-k">P75</span><span class="cc-sal-v">{{ card.p75 }}</span></div>
        </div>
        <div v-if="card.userExpectation" class="cc-kv"><span class="cc-k">你的期望</span><span class="cc-v">{{ card.userExpectation }}<template v-if="card.quartile"> · {{ card.quartile }}</template></span></div>
        <div v-if="card.anchorPoint" class="cc-kv"><span class="cc-k">建议锚定</span><span class="cc-v cc-anchor">{{ card.anchorPoint }}</span></div>
        <p v-if="card.negotiationAdvice" class="chat-card-text">{{ card.negotiationAdvice }}</p>
      </template>
      <p v-else class="cc-empty">暂无市场薪资数据，无法给出谈判建议。</p>
    </div>
    <div v-else-if="card.type === 'STAGE_CONFIRM'" class="chat-card-body" data-testid="stage-confirm-card">
      <p class="chat-card-title">📌 投递进度确认</p>
      <p class="chat-card-text">{{ card.question }}</p>
      <div v-if="stageState === ''" class="chat-card-actions">
        <button type="button" class="chat-card-btn primary" :disabled="disabled" @click="confirmStage">
          确认，已推进
        </button>
        <button type="button" class="chat-card-btn" :disabled="disabled" @click="dismissStage">暂不</button>
      </div>
      <p v-else-if="stageState === 'confirmed'" class="cc-confirmed">✓ 已推进到「{{ card.stageLabel }}」</p>
      <p v-else class="cc-dismissed">已忽略</p>
    </div>
    <div v-if="actions.length" class="chat-card-actions">
      <button
        v-for="(act, idx) in actions"
        :key="`${act.action}-${idx}`"
        type="button"
        class="chat-card-btn"
        :class="{ primary: idx === 0 }"
        :disabled="disabled || isActionLoading(act)"
        @click="$emit('action', act)"
      >
        {{ actionLabel(act) }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps({
  card: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
  pdfDownloading: { type: Boolean, default: false },
  wordDownloading: { type: Boolean, default: false },
})

const emit = defineEmits(['action'])

// STAGE_CONFIRM 卡本地态：'' | 'confirmed' | 'dismissed'（乐观更新，父组件走 API）
const stageState = ref('')
function confirmStage() {
  stageState.value = 'confirmed'
  emit('action', {
    action: 'CONFIRM_STAGE',
    jdDocId: props.card?.jdDocId,
    stage: props.card?.targetStage,
    stageLabel: props.card?.stageLabel,
    company: props.card?.company,
  })
}
function dismissStage() {
  stageState.value = 'dismissed'
  emit('action', { action: 'DISMISS_STAGE' })
}

const cardType = computed(() => (props.card?.type || 'unknown').toLowerCase())
const actions = computed(() => (Array.isArray(props.card?.actions) ? props.card.actions : []))
const previewHtml = computed(() => renderMarkdown(props.card?.previewMarkdown || ''))

// 围绕 JD 的能力卡片
const cultureTags = computed(() => (Array.isArray(props.card?.cultureTags) ? props.card.cultureTags : []))
const questions = computed(() => (Array.isArray(props.card?.questions) ? props.card.questions : []))
const citationCount = computed(() => (Array.isArray(props.card?.citations) ? props.card.citations.length : 0))

const Q_TAGS = {
  JD_FOCUSED: { label: 'JD重点', cls: 'jd' },
  HIT_RESUME: { label: '命中简历', cls: 'hit' },
  WEAK_POINT: { label: '弱项', cls: 'weak' },
}

function tagSentimentClass(sentiment) {
  const v = String(sentiment || '').toUpperCase()
  if (v === 'POSITIVE') return 'pos'
  if (v === 'NEGATIVE') return 'neg'
  return 'neu'
}
function qTagClass(tag) {
  return Q_TAGS[tag]?.cls || 'jd'
}
function qTagLabel(tag) {
  return Q_TAGS[tag]?.label || tag
}

const expiresHint = computed(() => {
  const raw = props.card?.expiresAt
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  return `请在 ${date.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })} 前确认`
})

function isActionLoading(act) {
  return (act?.action === 'DOWNLOAD_PDF' && props.pdfDownloading)
    || (act?.action === 'DOWNLOAD_WORD' && props.wordDownloading)
}

function actionLabel(act) {
  if (isActionLoading(act)) {
    return '下载中...'
  }
  return act?.label || ''
}
</script>

<style scoped>
.chat-card {
  margin-top: 8px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #F5F6F8;
  border: 1px solid #e2e8f0;
}
.chat-card-title {
  margin: 0 0 8px;
  font-weight: 600;
  color: #1A1D26;
}
.chat-card-subtitle {
  margin: 0 0 8px;
  font-size: 13px;
  color: #64748b;
}
.chat-card-text {
  margin: 0 0 10px;
  color: #334155;
  font-size: 14px;
}
.chat-card-preview {
  margin: 0 0 10px;
  padding: 8px 10px;
  background: #fff;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
  max-height: 120px;
  overflow: auto;
  color: #475569;
}
.chat-card-preview :deep(p) {
  margin: 0 0 6px;
}
.chat-card-preview :deep(p:last-child) {
  margin-bottom: 0;
}
.chat-card-preview :deep(ul),
.chat-card-preview :deep(ol) {
  margin: 4px 0 6px 18px;
  padding: 0;
}
.chat-card-error {
  margin: 0 0 10px;
  color: #E5484D;
  font-size: 14px;
}
.chat-card-risk {
  margin: 0 0 8px;
  color: #DB9A2D;
  font-size: 12px;
  font-weight: 600;
}
.chat-card-expiry {
  margin: 0 0 10px;
  color: #64748b;
  font-size: 12px;
}
.chat-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chat-card-btn {
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  font-size: 13px;
  cursor: pointer;
}
.chat-card-btn.primary {
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  border-color: transparent;
  color: #fff;
}
.chat-card-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 围绕 JD 的能力卡片 */
.cc-empty {
  margin: 4px 0 0;
  color: #94a3b8;
  font-size: 13px;
}
.cc-confirmed {
  margin: 8px 0 0;
  color: #0da76a;
  font-size: 13px;
  font-weight: 600;
}
.cc-dismissed {
  margin: 8px 0 0;
  color: #94a3b8;
  font-size: 13px;
}
.cc-src {
  margin: 8px 0 0;
  color: #94a3b8;
  font-size: 11px;
}
.cc-kv {
  display: flex;
  gap: 8px;
  margin: 5px 0;
  font-size: 13px;
}
.cc-k {
  color: #64748b;
  min-width: 56px;
  flex: 0 0 auto;
}
.cc-v {
  color: #1A1D26;
}
.cc-anchor {
  color: #db9a2d;
  font-weight: 700;
}
.cc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 2px 0 8px;
}
.cc-tag {
  font-size: 11px;
  border-radius: 12px;
  padding: 2px 9px;
  border: 1px solid #e2e8f0;
  color: #64748b;
}
.cc-tag.pos {
  color: #0da76a;
  border-color: rgba(13, 167, 106, 0.35);
  background: #e6f6ef;
}
.cc-tag.neg {
  color: #db9a2d;
  border-color: rgba(219, 154, 45, 0.35);
  background: #fbf3e2;
}
.cc-tag.neu {
  color: #64748b;
  background: #f1f5f9;
}
.cc-q {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.5;
}
.cc-q-no {
  color: #94a3b8;
  flex: 0 0 auto;
}
.cc-q-text {
  color: #1A1D26;
  flex: 1;
}
.cc-q-tag {
  flex: 0 0 auto;
  font-size: 10px;
  border-radius: 6px;
  padding: 1px 7px;
}
.cc-q-tag.jd {
  color: #4E5BEF;
  background: #eef0fe;
}
.cc-q-tag.hit {
  color: #0da76a;
  background: #e6f6ef;
}
.cc-q-tag.weak {
  color: #db9a2d;
  background: #fbf3e2;
}
.cc-sal {
  display: flex;
  gap: 10px;
  margin: 4px 0 10px;
}
.cc-sal-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  padding: 6px 4px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
}
.cc-sal-cell.hl {
  border-color: rgba(79, 70, 229, 0.4);
  background: #eef0fe;
}
.cc-sal-v {
  font-weight: 700;
  color: #1A1D26;
  font-size: 15px;
}
</style>
