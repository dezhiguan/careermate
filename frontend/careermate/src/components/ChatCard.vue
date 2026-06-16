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
      <pre v-if="card.previewMarkdown" class="chat-card-preview">{{ card.previewMarkdown }}</pre>
    </div>
    <div v-else-if="card.type === 'GENERATE_FAILED'" class="chat-card-body">
      <p class="chat-card-error">{{ card.message || '生成失败，请重试' }}</p>
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
import { computed } from 'vue'

const props = defineProps({
  card: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
  pdfDownloading: { type: Boolean, default: false },
  wordDownloading: { type: Boolean, default: false },
})

defineEmits(['action'])

const cardType = computed(() => (props.card?.type || 'unknown').toLowerCase())
const actions = computed(() => (Array.isArray(props.card?.actions) ? props.card.actions : []))

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
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.chat-card-title {
  margin: 0 0 8px;
  font-weight: 600;
  color: #0f172a;
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
  white-space: pre-wrap;
  max-height: 120px;
  overflow: auto;
  color: #475569;
}
.chat-card-error {
  margin: 0 0 10px;
  color: #dc2626;
  font-size: 14px;
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
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-color: transparent;
  color: #fff;
}
.chat-card-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
