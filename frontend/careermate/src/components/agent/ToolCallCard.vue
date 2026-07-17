<template>
  <div
    class="tool-call-card"
    :class="statusClass"
    data-testid="tool-call-card"
  >
    <div class="tool-call-head">
      <span class="tool-call-icon">{{ statusIcon }}</span>
      <span class="tool-call-title" data-testid="tool-call-label">{{ label }}</span>
      <span class="tool-call-badge">{{ statusText }}</span>
    </div>
    <p v-if="displaySummary" class="tool-call-summary" data-testid="tool-call-summary">
      {{ displaySummary }}
    </p>
    <p v-if="tool.status === 'failed' && tool.errorHint" class="tool-call-error">
      {{ tool.errorHint }}
    </p>
    <router-link
      v-if="showAction && route"
      :to="route"
      class="tool-call-action"
      data-testid="tool-call-action"
    >
      {{ actionLabel }}
    </router-link>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  getToolActionLabel,
  getToolLabel,
  getToolRoute,
  sanitizeToolSummary,
} from '../../utils/agentToolDisplay'

const props = defineProps({
  tool: {
    type: Object,
    required: true,
  },
})

const label = computed(() => getToolLabel(props.tool.toolName))
const route = computed(() => getToolRoute(props.tool.toolName))
const actionLabel = computed(() => getToolActionLabel(props.tool.toolName))

const displaySummary = computed(() => sanitizeToolSummary(props.tool.summary))

const statusClass = computed(() => {
  if (props.tool.status === 'running') return 'is-running'
  if (props.tool.status === 'failed') return 'is-failed'
  return 'is-success'
})

const statusText = computed(() => {
  if (props.tool.status === 'running') return '执行中'
  if (props.tool.status === 'failed') return '失败'
  return '完成'
})

const statusIcon = computed(() => {
  if (props.tool.status === 'running') return '⚙️'
  if (props.tool.status === 'failed') return '⚠️'
  return '✓'
})

const showAction = computed(() => props.tool.status === 'success' && !!route.value)
</script>

<style scoped>
.tool-call-card {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: #F5F6F8;
  font-size: 11px;
  line-height: 1.5;
}
.tool-call-card.is-running {
  border-color: #c4b5fd;
  background: #f5f3ff;
}
.tool-call-card.is-success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}
.tool-call-card.is-failed {
  border-color: #fecaca;
  background: #fff1f2;
}
.tool-call-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tool-call-icon {
  font-size: 12px;
  line-height: 1;
}
.tool-call-title {
  font-weight: 600;
  color: var(--slate);
}
.tool-call-badge {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-muted);
}
.tool-call-summary {
  margin: 6px 0 0;
  color: var(--text-muted);
  word-break: break-word;
}
.tool-call-error {
  margin: 4px 0 0;
  color: var(--red);
  font-size: 10px;
}
.tool-call-action {
  display: inline-block;
  margin-top: 8px;
  padding: 4px 10px;
  border-radius: 6px;
  background: var(--purple);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  text-decoration: none;
}
.tool-call-action:hover {
  opacity: 0.9;
}
</style>
