<template>
  <div class="runs-view">
    <header class="runs-header">
      <h2>深度任务 · 断点续跑</h2>
      <p class="desc">发起可续跑的深度任务；在关键决策点暂停，由你选择方向后继续，也可从某次运行分叉重试。</p>
    </header>

    <section class="start-box">
      <input
        v-model="topic"
        class="topic-input"
        data-testid="run-topic"
        placeholder="任务主题，如：字节跳动 Java 后端岗位深度准备"
        @keyup.enter="onStart"
      />
      <button class="btn primary" data-testid="run-start" :disabled="loading" @click="onStart">
        {{ loading ? '处理中…' : '发起任务' }}
      </button>
    </section>

    <p v-if="errorMsg" class="err" data-testid="run-error">{{ errorMsg }}</p>

    <section v-if="current" class="current-box" data-testid="run-current">
      <div class="row"><span class="k">运行</span><span class="v">{{ current.runId }}</span></div>
      <div class="row"><span class="k">状态</span><span class="v">{{ stepLabel(current.currentStep) }}</span></div>
      <div v-if="current.paused" class="pause" data-testid="run-paused">
        <p class="hint">{{ current.pendingAction }}</p>
        <div class="resume-row">
          <input v-model="decision" class="topic-input" data-testid="run-decision" placeholder="输入方向：技能补齐 / 项目包装 / 面试准备" />
          <button class="btn primary" data-testid="run-resume" :disabled="loading" @click="onResume">续跑</button>
        </div>
      </div>
      <div v-if="current.terminal" class="done" data-testid="run-done">
        <p class="result">{{ current.data && current.data.result }}</p>
        <button class="btn" data-testid="run-fork" :disabled="loading" @click="onFork">分叉重试</button>
      </div>
    </section>

    <section class="history">
      <h3>运行历史</h3>
      <ul v-if="runs.length" class="run-list" data-testid="run-list">
        <li v-for="r in runs" :key="r.runId" class="run-item" @click="openRun(r.runId)">
          <span class="rid">{{ r.runId }}</span>
          <span class="rstatus" :class="r.status">{{ r.status }}</span>
          <span v-if="r.parentRunId" class="fork-tag">分叉</span>
        </li>
      </ul>
      <p v-else class="empty">暂无运行记录</p>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { startRun, listRuns, getRun, resumeRun, forkRun } from '../api/agentCheckpoint'

const topic = ref('')
const decision = ref('')
const loading = ref(false)
const errorMsg = ref('')
const current = ref(null)
const runs = ref([])

const STEP_LABELS = {
  created: '已创建',
  analyzing: '分析中',
  awaiting_direction: '等待你选择方向',
  resumed: '已续跑',
  finalizing: '收尾中',
  done: '已完成',
  failed: '失败',
}
function stepLabel(s) {
  return STEP_LABELS[s] || s
}

async function refreshRuns() {
  try {
    runs.value = (await listRuns()) || []
  } catch (e) {
    // 能力未开启等：列表留空，错误在操作时提示
    runs.value = []
  }
}

async function onStart() {
  errorMsg.value = ''
  loading.value = true
  try {
    current.value = await startRun(topic.value)
    await refreshRuns()
  } catch (e) {
    errorMsg.value = e.message || '发起失败'
  } finally {
    loading.value = false
  }
}

async function onResume() {
  errorMsg.value = ''
  loading.value = true
  try {
    current.value = await resumeRun(current.value.runId, decision.value)
    decision.value = ''
    await refreshRuns()
  } catch (e) {
    errorMsg.value = e.message || '续跑失败'
  } finally {
    loading.value = false
  }
}

async function onFork() {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await forkRun(current.value.runId)
    if (res && res.runId) {
      current.value = await getRun(res.runId)
    }
    await refreshRuns()
  } catch (e) {
    errorMsg.value = e.message || '分叉失败'
  } finally {
    loading.value = false
  }
}

async function openRun(runId) {
  errorMsg.value = ''
  try {
    current.value = await getRun(runId)
  } catch (e) {
    errorMsg.value = e.message || '加载失败'
  }
}

onMounted(refreshRuns)
</script>

<style scoped>
.runs-view { max-width: 760px; margin: 0 auto; padding: 24px; }
.runs-header h2 { margin: 0 0 6px; }
.desc { color: #666; font-size: 13px; margin: 0 0 18px; }
.start-box { display: flex; gap: 10px; margin-bottom: 12px; }
.topic-input { flex: 1; padding: 9px 12px; border: 1px solid #d9d9e3; border-radius: 8px; font-size: 14px; }
.btn { padding: 9px 16px; border: 1px solid #d9d9e3; border-radius: 8px; background: #fff; cursor: pointer; font-size: 14px; }
.btn.primary { background: #635bff; color: #fff; border-color: #635bff; }
.btn:disabled { opacity: .55; cursor: not-allowed; }
.err { color: #d4380d; font-size: 13px; }
.current-box { border: 1px solid #ececf3; border-radius: 12px; padding: 16px; margin: 12px 0; }
.row { display: flex; gap: 10px; font-size: 13px; margin-bottom: 6px; }
.row .k { color: #888; width: 48px; }
.pause .hint { color: #614700; background: #fff7e6; padding: 8px 10px; border-radius: 8px; font-size: 13px; }
.resume-row { display: flex; gap: 10px; margin-top: 8px; }
.done .result { background: #f6ffed; padding: 10px; border-radius: 8px; font-size: 13px; }
.history h3 { font-size: 15px; margin: 18px 0 8px; }
.run-list { list-style: none; padding: 0; margin: 0; }
.run-item { display: flex; gap: 10px; align-items: center; padding: 8px 10px; border: 1px solid #f0f0f5; border-radius: 8px; margin-bottom: 6px; cursor: pointer; font-size: 13px; }
.run-item:hover { background: #fafaff; }
.rid { flex: 1; font-family: monospace; color: #555; }
.rstatus { font-size: 12px; padding: 2px 8px; border-radius: 10px; background: #f0f0f5; }
.rstatus.DONE { background: #f6ffed; color: #389e0d; }
.rstatus.PAUSED { background: #fff7e6; color: #d48806; }
.fork-tag { font-size: 11px; color: #635bff; }
.empty { color: #999; font-size: 13px; }
</style>
