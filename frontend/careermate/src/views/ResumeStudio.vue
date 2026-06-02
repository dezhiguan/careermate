<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">📝 简历工作室</h2>
      <p class="page-sub">Agent 触发：用户说"帮我看看/优化简历"</p>
    </div>

    <div class="content-grid">
      <!-- Left: Upload & List -->
      <div class="left-panel">
        <!-- Upload -->
        <div
          class="upload-zone"
          :class="{ dragging: isDragging }"
          @dragover.prevent="isDragging = true"
          @dragleave="isDragging = false"
          @drop.prevent="handleDrop"
          @click="triggerUpload"
        >
          <div class="upload-icon">{{ uploading ? '⏳' : '📤' }}</div>
          <div class="upload-title">{{ uploading ? '解析中...' : '上传简历' }}</div>
          <div class="upload-hint">PDF / Word / Markdown</div>
          <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.md" style="display:none" @change="handleFileSelect">
        </div>

        <div v-if="uploadStatus" class="upload-status" :class="uploadStatus">
          {{ uploadStatusText }}
        </div>

        <!-- Resume List -->
        <div class="resume-list">
          <div class="section-title">已有简历 ({{ resumes.length }})</div>
          <div
            v-for="(r, i) in resumes"
            :key="i"
            class="resume-card"
            :class="{ active: r.active }"
            @click="selectResume(i)"
          >
            <div class="resume-name">📄 {{ r.name }}</div>
            <div class="resume-meta">{{ r.status }} · {{ r.chunks }} Chunk · {{ r.date }}</div>
            <div v-if="r.analyzed" class="resume-tag">🔍 Agent 已分析 →</div>
          </div>
        </div>
      </div>

      <!-- Right: Analysis -->
      <div class="right-panel">
        <div class="section-title">🤖 Agent 简历分析摘要</div>

        <div v-if="selectedResume && selectedResume.analyzed" class="analysis-content">
          <!-- Thinking -->
          <div class="thinking-box">
            <div class="thinking-box-title">🧠 Agent 思考</div>
            <div v-for="(step, i) in analysisSteps" :key="i" class="thinking-step">{{ step }}</div>
          </div>

          <div class="suggestions-box">
            <div class="suggestions-title">优化建议 ({{ suggestions.length }}条)</div>
            <div v-for="(s, i) in suggestions" :key="i" class="suggestion-item" :class="s.type">
              <span class="sug-icon">{{ s.type === 'warn' ? '⚠️' : '💡' }}</span>
              {{ s.text }}
            </div>
          </div>

          <router-link to="/" class="nav-link-btn">💬 回对话台讨论优化方案 →</router-link>
        </div>

        <div v-else class="empty-analysis">
          <div class="empty-icon">📋</div>
          <div class="empty-text">选择一个简历查看 Agent 分析结果</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const fileInput = ref(null)
const isDragging = ref(false)
const uploading = ref(false)
const uploadStatus = ref('')
const selectedIndex = ref(0)

const resumes = ref([
  { name: '后端开发_3年经验.pdf', status: '解析完成', chunks: 420, date: '5月20日上传', analyzed: true, active: true },
])

const analysisSteps = [
  '提取技能: Java, Spring Boot, MySQL, Redis',
  '识别经验: 3年后端开发, 1个微服务项目',
  '检测缺失: 分布式系统、消息队列、大模型应用',
  '建议: 补充量化成果、增加项目技术细节',
]

const suggestions = ref([
  { text: '缺少分布式系统经验描述', type: 'warn' },
  { text: '性能优化成果未量化', type: 'warn' },
  { text: '建议增加大模型相关项目', type: 'tip' },
])

const selectedResume = computed(() => resumes.value[selectedIndex.value] || null)

const uploadStatusText = computed(() => {
  if (uploadStatus.value === 'success') return '✅ 上传成功，Agent 正在解析...'
  if (uploadStatus.value === 'error') return '❌ 上传失败，请重试'
  return ''
})

function triggerUpload() {
  fileInput.value?.click()
}

function handleFileSelect(e) {
  const file = e.target.files?.[0]
  if (file) simulateUpload(file.name)
}

function handleDrop(e) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) simulateUpload(file.name)
}

function simulateUpload(name) {
  uploading.value = true
  uploadStatus.value = ''
  setTimeout(() => {
    uploading.value = false
    uploadStatus.value = 'success'
    resumes.value.push({
      name,
      status: '解析中...',
      chunks: '--',
      date: new Date().toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) + '日上传',
      analyzed: false,
      active: false,
    })
    setTimeout(() => {
      const r = resumes.value[resumes.value.length - 1]
      r.status = '解析完成'
      r.chunks = Math.floor(Math.random() * 300 + 200)
      r.analyzed = true
      uploadStatus.value = ''
      selectedIndex.value = resumes.value.length - 1
    }, 2000)
  }, 1500)
}

function selectResume(i) {
  selectedIndex.value = i
  resumes.value.forEach((r, idx) => r.active = idx === i)
}
</script>

<style scoped>
.page-container { max-width: 960px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.content-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }

/* Left */
.left-panel { display: flex; flex-direction: column; gap: 16px; }

.upload-zone {
  border: 2px dashed var(--border); border-radius: 12px; padding: 28px 20px;
  text-align: center; cursor: pointer; transition: all .2s;
}
.upload-zone:hover, .upload-zone.dragging { border-color: var(--purple); background: #faf5ff; }
.upload-icon { font-size: 32px; margin-bottom: 6px; }
.upload-title { font-weight: 600; font-size: 13px; }
.upload-hint { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.upload-status { padding: 8px 12px; border-radius: 6px; font-size: 11px; text-align: center; }
.upload-status.success { background: #d1fae5; color: #065f46; }
.upload-status.error { background: #fef2f2; color: #991b1b; }

.section-title { font-weight: 600; font-size: 12px; margin-bottom: 8px; }

.resume-list { }
.resume-card {
  background: #fff; border: 1px solid var(--border); border-radius: 8px;
  padding: 10px 12px; margin-bottom: 8px; cursor: pointer; transition: all .2s;
}
.resume-card:hover, .resume-card.active { border-color: var(--purple); box-shadow: 0 2px 8px rgba(139,92,246,.1); }
.resume-name { font-weight: 600; font-size: 12px; }
.resume-meta { color: var(--text-muted); font-size: 10px; margin-top: 2px; }
.resume-tag { color: var(--purple); font-size: 10px; margin-top: 4px; }

/* Right */
.right-panel { }
.thinking-box {
  background: #f5f3ff; border: 1px solid #ddd6fe; border-radius: 8px;
  padding: 12px 14px; margin-bottom: 12px; font-size: 11px;
}
.thinking-box-title { font-weight: 600; margin-bottom: 6px; color: var(--purple); }
.thinking-step { padding: 2px 0 2px 14px; position: relative; color: var(--gray); }
.thinking-step::before { content: "▸"; position: absolute; left: 0; color: var(--purple); }

.suggestions-box { margin-bottom: 14px; }
.suggestions-title { font-weight: 600; font-size: 12px; margin-bottom: 6px; }
.suggestion-item { padding: 6px 10px; border-radius: 6px; font-size: 11px; margin-bottom: 4px; }
.suggestion-item.warn { background: #fef2f2; color: #991b1b; }
.suggestion-item.tip { background: #f0fdf4; color: #065f46; }
.sug-icon { margin-right: 4px; }

.nav-link-btn {
  display: block; text-align: center; padding: 8px;
  border: 1px solid var(--purple); border-radius: 8px; color: var(--purple);
  font-size: 11px; text-decoration: none; transition: all .2s;
}
.nav-link-btn:hover { background: #f5f3ff; }

.empty-analysis {
  text-align: center; padding: 40px 20px; color: var(--text-muted);
}
.empty-icon { font-size: 36px; margin-bottom: 8px; }
.empty-text { font-size: 12px; }

@media (max-width: 768px) {
  .page-container {
    padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
    max-width: 100%;
    overflow-x: hidden;
  }

  .page-title {
    font-size: 20px;
    word-break: break-word;
  }

  .content-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .left-panel,
  .right-panel {
    width: 100%;
    min-width: 0;
  }

  .upload-zone {
    width: 100%;
    padding: 24px 16px;
  }

  .resume-card,
  .thinking-box,
  .suggestions-box {
    width: 100%;
    max-width: 100%;
  }

  .resume-name,
  .suggestion-item,
  .thinking-step {
    word-break: break-word;
    overflow-wrap: anywhere;
  }

  .nav-link-btn {
    width: 100%;
    min-height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    white-space: normal;
    text-align: center;
    line-height: 1.4;
  }
}

@media (max-width: 480px) {
  .page-container {
    padding: 12px 10px calc(12px + env(safe-area-inset-bottom));
  }

  .page-title {
    font-size: 18px;
  }
}
</style>
