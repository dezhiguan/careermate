<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">🎯 岗位匹配</h2>
      <p class="page-sub">录入岗位 JD，基于默认简历生成匹配分析（V1）</p>
    </div>

    <div v-if="noDefaultResumeHint" class="banner warn">
      请先到简历页创建并设置默认简历。
      <router-link to="/resume" class="banner-link">前往简历工作室 →</router-link>
    </div>
    <div v-if="pageError" class="banner error">{{ pageError }}</div>

    <section class="analyze-panel">
      <div class="section-title">JD 分析</div>
      <label class="field-label">岗位名称</label>
      <input v-model="form.jobTitle" class="field-input" maxlength="128" placeholder="例如：Java 后端工程师">
      <label class="field-label">公司名称（可选）</label>
      <input v-model="form.companyName" class="field-input" maxlength="128" placeholder="例如：某互联网公司">
      <label class="field-label">岗位 JD 正文</label>
      <textarea
        v-model="form.jdContent"
        class="field-textarea"
        rows="6"
        maxlength="50000"
        placeholder="粘贴岗位描述、技能要求等..."
      />
      <button class="btn primary" type="button" :disabled="analyzing" @click="submitAnalyze">
        {{ analyzing ? '分析中...' : '开始匹配' }}
      </button>
    </section>

    <div v-if="listLoading" class="list-hint">加载匹配记录...</div>
    <div v-else-if="matches.length === 0" class="empty-state">
      <div class="empty-icon">📭</div>
      <div>暂无匹配记录，填写 JD 后点击「开始匹配」</div>
    </div>

    <div v-else class="job-grid">
      <div
        v-for="item in matches"
        :key="item.id"
        class="job-card"
        :class="{ featured: item.matchScore >= 75 }"
        @click="openDetail(item.id)"
      >
        <div class="job-header">
          <span class="job-company">{{ item.companyName || '未填写公司' }}</span>
          <span class="job-badge" :class="matchClass(item.matchScore)">匹配 {{ item.matchScore }}%</span>
        </div>
        <div class="job-info">{{ item.jobTitle }} · {{ levelLabel(item.matchLevel) }}</div>
        <div v-if="item.matchedSkills?.length" class="job-point match">
          <span class="pt-icon">✅</span> 命中：{{ item.matchedSkills.slice(0, 4).join('、') }}
        </div>
        <div v-if="item.missingSkills?.length" class="job-point gap">
          <span class="pt-icon">⚠️</span> 缺失：{{ item.missingSkills.slice(0, 4).join('、') }}
        </div>
        <p v-if="item.analysisSummary" class="job-summary">{{ item.analysisSummary }}</p>
        <button
          class="card-delete"
          type="button"
          @click.stop="confirmDelete(item)"
        >
          删除
        </button>
      </div>
    </div>

    <div v-if="selectedDetail" class="modal-overlay" @click.self="closeDetail">
      <div class="modal-card">
        <div class="modal-header">
          <div>
            <div class="modal-company">{{ selectedDetail.companyName || '未填写公司' }}</div>
            <div class="modal-title">{{ selectedDetail.jobTitle }}</div>
          </div>
          <span class="job-badge large" :class="matchClass(selectedDetail.matchScore)">
            匹配 {{ selectedDetail.matchScore }}%
          </span>
        </div>
        <div class="modal-body">
          <div class="modal-section">
            <div class="modal-label">匹配等级</div>
            <div>{{ levelLabel(selectedDetail.matchLevel) }}</div>
          </div>
          <div class="modal-section">
            <div class="modal-label">✅ 命中技能</div>
            <div class="skill-tags">
              <span v-for="s in selectedDetail.matchedSkills" :key="'m-' + s" class="skill-tag match">{{ s }}</span>
              <span v-if="!selectedDetail.matchedSkills?.length" class="muted">无</span>
            </div>
          </div>
          <div class="modal-section">
            <div class="modal-label">⚠️ 缺失技能</div>
            <div class="skill-tags">
              <span v-for="s in selectedDetail.missingSkills" :key="'g-' + s" class="skill-tag gap">{{ s }}</span>
              <span v-if="!selectedDetail.missingSkills?.length" class="muted">无</span>
            </div>
          </div>
          <div v-if="selectedDetail.strengths?.length" class="modal-section">
            <div class="modal-label">优势</div>
            <ul class="bullet-list">
              <li v-for="(t, i) in selectedDetail.strengths" :key="'s-' + i">{{ t }}</li>
            </ul>
          </div>
          <div v-if="selectedDetail.risks?.length" class="modal-section">
            <div class="modal-label">风险</div>
            <ul class="bullet-list">
              <li v-for="(t, i) in selectedDetail.risks" :key="'r-' + i">{{ t }}</li>
            </ul>
          </div>
          <div v-if="selectedDetail.suggestions?.length" class="modal-section">
            <div class="modal-label">建议</div>
            <ul class="bullet-list">
              <li v-for="(t, i) in selectedDetail.suggestions" :key="'u-' + i">{{ t }}</li>
            </ul>
          </div>
          <div v-if="selectedDetail.analysisSummary" class="modal-section">
            <div class="modal-label">分析总结</div>
            <p class="summary-text">{{ selectedDetail.analysisSummary }}</p>
          </div>
        </div>
        <button class="modal-close" type="button" @click="closeDetail">✕</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { analyzeJobMatch, deleteJobMatch, getJobMatch, listJobMatches } from '../api/jobMatch'

const matches = ref([])
const listLoading = ref(false)
const analyzing = ref(false)
const pageError = ref('')
const noDefaultResumeHint = ref(false)
const selectedDetail = ref(null)

const form = reactive({
  jobTitle: '',
  companyName: '',
  jdContent: '',
})

onMounted(() => {
  loadMatches()
})

async function loadMatches() {
  listLoading.value = true
  pageError.value = ''
  try {
    matches.value = await listJobMatches()
  } catch (e) {
    pageError.value = e?.message || '加载匹配记录失败'
  } finally {
    listLoading.value = false
  }
}

async function submitAnalyze() {
  if (!form.jobTitle.trim() || !form.jdContent.trim()) {
    pageError.value = '请填写岗位名称和 JD 正文'
    return
  }
  analyzing.value = true
  pageError.value = ''
  noDefaultResumeHint.value = false
  try {
    const detail = await analyzeJobMatch({
      jobTitle: form.jobTitle.trim(),
      companyName: form.companyName.trim() || undefined,
      jdContent: form.jdContent.trim(),
    })
    await loadMatches()
    selectedDetail.value = detail
  } catch (e) {
    const msg = e?.message || '匹配分析失败'
    if (msg.includes('默认简历')) {
      noDefaultResumeHint.value = true
    }
    pageError.value = msg
  } finally {
    analyzing.value = false
  }
}

async function openDetail(id) {
  try {
    selectedDetail.value = await getJobMatch(id)
  } catch (e) {
    pageError.value = e?.message || '加载详情失败'
  }
}

function closeDetail() {
  selectedDetail.value = null
}

async function confirmDelete(item) {
  if (!window.confirm(`确定删除「${item.jobTitle}」的匹配记录吗？`)) return
  try {
    await deleteJobMatch(item.id)
    if (selectedDetail.value?.id === item.id) {
      selectedDetail.value = null
    }
    await loadMatches()
  } catch (e) {
    pageError.value = e?.message || '删除失败'
  }
}

function matchClass(score) {
  if (score >= 75) return 'high'
  if (score >= 60) return 'mid'
  return 'low'
}

function levelLabel(level) {
  if (level === 'HIGH') return '高度匹配'
  if (level === 'MEDIUM') return '中等匹配'
  if (level === 'LOW') return '偏低'
  return '未知'
}
</script>

<style scoped>
.page-container { max-width: 960px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.banner {
  padding: 10px 12px; border-radius: 8px; font-size: 13px; margin-bottom: 12px;
}
.banner.error { background: #fef2f2; color: #991b1b; }
.banner.warn { background: #fffbeb; color: #92400e; }
.banner-link { margin-left: 8px; color: var(--purple); font-weight: 600; }

.analyze-panel {
  background: #fff; border: 1px solid var(--border); border-radius: 12px;
  padding: 16px; margin-bottom: 20px;
}
.section-title { font-weight: 600; font-size: 14px; margin-bottom: 12px; color: var(--navy); }
.field-label { display: block; font-size: 12px; color: var(--text-muted); margin: 8px 0 4px; }
.field-input, .field-textarea {
  width: 100%; box-sizing: border-box; border: 1px solid var(--border);
  border-radius: 8px; padding: 8px 10px; font-size: 14px;
}
.field-textarea { resize: vertical; min-height: 120px; }
.btn.primary {
  margin-top: 12px; padding: 10px 18px; border: none; border-radius: 8px;
  background: var(--purple); color: #fff; font-weight: 600; cursor: pointer;
}
.btn.primary:disabled { opacity: .6; cursor: default; }

.empty-state, .list-hint {
  text-align: center; color: var(--text-muted); padding: 32px 16px; font-size: 14px;
}
.empty-icon { font-size: 32px; margin-bottom: 8px; }

.job-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.job-card {
  background: #fff; border: 2px solid var(--border); border-radius: 12px;
  padding: 14px; cursor: pointer; transition: all .2s; position: relative;
}
.job-card:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,.06); }
.job-card.featured { border-color: var(--green); }

.job-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.job-company { font-weight: 600; font-size: 13px; }
.job-badge {
  display: inline-block; padding: 2px 8px; border-radius: 8px; font-size: 10px; font-weight: 600;
}
.job-badge.high { background: #d1fae5; color: #065f46; }
.job-badge.mid { background: #fef3c7; color: #92400e; }
.job-badge.low { background: #fef2f2; color: #991b1b; }
.job-badge.large { font-size: 12px; padding: 4px 12px; }

.job-info { font-size: 11px; color: var(--text-muted); margin-bottom: 6px; }
.job-point { font-size: 11px; padding: 2px 0; }
.job-point.match { color: var(--green); }
.job-point.gap { color: var(--amber); }
.pt-icon { margin-right: 2px; }
.job-summary {
  font-size: 11px; color: var(--text-muted); margin: 8px 0 0;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.card-delete {
  margin-top: 8px; font-size: 11px; color: #991b1b; background: transparent;
  border: 1px solid #fecaca; border-radius: 6px; padding: 4px 8px; cursor: pointer;
}

.modal-overlay {
  position: fixed; inset: 0; background: rgba(15,23,42,.6); z-index: 200;
  display: flex; align-items: center; justify-content: center; padding: 20px;
}
.modal-card {
  background: #fff; border-radius: 14px; max-width: 520px; width: 100%;
  position: relative; overflow: hidden; max-height: calc(100dvh - 40px);
  display: flex; flex-direction: column;
}
.modal-header {
  padding: 18px; background: var(--navy); color: #fff;
  display: flex; justify-content: space-between; align-items: flex-start; flex-shrink: 0;
}
.modal-company { font-weight: 700; font-size: 16px; }
.modal-title { font-size: 12px; opacity: .7; }
.modal-body { padding: 18px; overflow-y: auto; -webkit-overflow-scrolling: touch; }
.modal-section { margin-bottom: 14px; }
.modal-label { font-weight: 600; font-size: 11px; margin-bottom: 4px; }
.skill-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.skill-tag { padding: 3px 8px; border-radius: 6px; font-size: 10px; }
.skill-tag.match { background: #d1fae5; color: #065f46; }
.skill-tag.gap { background: #fef3c7; color: #92400e; }
.bullet-list { margin: 0; padding-left: 18px; font-size: 12px; color: var(--text-muted); }
.summary-text { font-size: 12px; line-height: 1.5; color: var(--text-muted); margin: 0; }
.muted { font-size: 11px; color: var(--text-muted); }
.modal-close {
  position: absolute; top: 12px; right: 12px; background: rgba(255,255,255,.2);
  border: none; color: #fff; width: 28px; height: 28px; border-radius: 50%;
  font-size: 12px; cursor: pointer;
}

@media (max-width: 768px) {
  .page-container { padding: 16px 12px; max-width: 100%; }
  .job-grid { grid-template-columns: 1fr; }
  .modal-overlay { align-items: flex-end; padding: 12px; }
  .modal-card { width: calc(100vw - 24px); max-width: calc(100vw - 24px); }
}
</style>
