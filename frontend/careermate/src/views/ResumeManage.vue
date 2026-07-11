<template>
  <div class="resume-page">
    <header class="page-header">
      <button type="button" class="btn-back" @click="router.push('/mine')">← 我的</button>
      <h1 class="page-title">简历管理</h1>
    </header>

    <!-- 区块一：默认简历 -->
    <section class="resume-section">
      <div class="section-title">默认简历</div>

      <div v-if="defaultResume" class="default-resume-card">
        <div class="resume-card-meta">
          <span class="badge-default">默认</span>
          <span class="resume-name">{{ defaultResume.title || '默认简历' }}</span>
          <span class="resume-date">{{ formatRelativeTime(defaultResume.updatedAt || defaultResume.createdAt) }}</span>
        </div>
        <p class="resume-preview-text">{{ defaultResume.contentPreview || '暂无预览' }}</p>
        <div class="resume-actions">
          <button type="button" class="action-btn" @click="openModal('default', defaultResume, 'preview')">预览</button>
          <button type="button" class="action-btn" @click="openModal('default', defaultResume, 'edit')">修改</button>
          <button
            type="button"
            class="action-btn"
            :disabled="downloadingKey === `resume-pdf-${defaultResume.id}`"
            @click="handleDownloadDefault(defaultResume, 'pdf')"
          >
            {{ downloadingKey === `resume-pdf-${defaultResume.id}` ? '...' : 'PDF' }}
          </button>
          <button
            type="button"
            class="action-btn"
            :disabled="downloadingKey === `resume-docx-${defaultResume.id}`"
            @click="handleDownloadDefault(defaultResume, 'docx')"
          >
            {{ downloadingKey === `resume-docx-${defaultResume.id}` ? '...' : 'Word' }}
          </button>
          <button type="button" class="action-btn" @click="triggerUpload">重新上传</button>
          <button type="button" class="action-btn action-btn--danger" @click="handleDeleteDefault">删除</button>
        </div>
      </div>

      <div
        v-else
        class="upload-area"
        @click="triggerUpload"
        @dragover.prevent
        @drop.prevent="onFileDrop"
      >
        <div class="upload-icon">📄</div>
        <div class="upload-title">上传简历文件</div>
        <div class="upload-desc">支持 PDF、Word、Markdown · 点击或拖拽</div>
        <div v-if="uploading" class="upload-progress">上传中...</div>
      </div>

      <input
        ref="fileInputRef"
        type="file"
        accept=".pdf,.doc,.docx,.md,.markdown"
        class="hidden-input"
        @change="onFileSelected"
      >
      <p v-if="uploadError" class="error-text">{{ uploadError }}</p>

      <button
        v-if="!defaultResume && !uploading"
        type="button"
        class="btn-text-link"
        @click="openCreateModal"
      >
        或手动输入简历内容 →
      </button>
    </section>

    <!-- 区块二：AI 定制版本 -->
    <section class="resume-section">
      <div class="section-title">AI 定制版本（{{ versions.length }}）</div>
      <p class="section-desc">由小职针对 JD 生成，可预览和下载 PDF / Word</p>

      <div v-if="versions.length === 0" class="empty-tip">
        暂无定制版本，去小职对话生成 →
        <button type="button" class="btn-text-link" @click="router.push('/chat')">前往小职</button>
      </div>

      <div v-else class="version-list">
        <div v-for="v in versions" :key="v.versionId" class="version-row">
          <div class="version-row-main">
            <div class="version-row-left">
              <span class="badge-custom">定制</span>
              <div class="version-info">
                <div class="version-name">{{ v.versionName || '历史定制简历' }}</div>
                <div class="version-meta">{{ formatGeneratedAt(v.createdAt) }}</div>
              </div>
            </div>
            <div class="version-row-right">
              <span v-if="v.aiScore != null" class="ats-chip">ATS {{ Math.round(v.aiScore) }}</span>
              <button type="button" class="action-btn" @click="openModal('custom', v, 'preview')">预览</button>
              <button
                type="button"
                class="action-btn"
                :disabled="downloadingKey === `version-pdf-${v.versionId}`"
                @click="handleDownloadVersion(v, 'pdf')"
              >
                {{ downloadingKey === `version-pdf-${v.versionId}` ? '...' : 'PDF' }}
              </button>
              <button
                type="button"
                class="action-btn"
                :disabled="downloadingKey === `version-docx-${v.versionId}`"
                @click="handleDownloadVersion(v, 'docx')"
              >
                {{ downloadingKey === `version-docx-${v.versionId}` ? '...' : 'Word' }}
              </button>
              <button type="button" class="action-btn action-btn--danger" @click="handleDeleteVersion(v)">删除</button>
            </div>
          </div>
          <button
            type="button"
            class="summary-toggle"
            :aria-expanded="expandedSummaryId === v.versionId"
            @click="toggleSummary(v.versionId)"
          >
            查看改写说明
          </button>
          <div v-if="expandedSummaryId === v.versionId" class="change-summary">
            {{ v.changeSummary || '暂无改写说明' }}
          </div>
        </div>
      </div>
    </section>

    <!-- 预览 / 编辑弹层 -->
    <div v-if="resumeModalOpen" class="modal-overlay" @click.self="closeModal">
      <div class="modal-panel modal-panel--large">
        <div class="modal-header">
          <span>{{ editTitle || resumeModalTitle }}</span>
          <div class="modal-header-actions">
            <button
              v-if="!resumeModalLoading && !resumeModalError && resumeModalMode === 'preview'"
              type="button"
              class="modal-tab-btn"
              @click="resumeModalMode = 'edit'"
            >
              修改
            </button>
            <button
              v-if="!resumeModalLoading && !resumeModalError && resumeModalMode === 'edit'"
              type="button"
              class="modal-tab-btn"
              @click="resumeModalMode = 'preview'"
            >
              预览
            </button>
            <button type="button" class="modal-close" @click="closeModal">×</button>
          </div>
        </div>

        <div v-if="resumeModalLoading" class="modal-status">加载中...</div>
        <div
          v-else-if="resumeModalError && resumeModalMode === 'preview' && !editContent"
          class="modal-status modal-status--error"
        >
          {{ resumeModalError }}
        </div>
        <template v-else>
          <div
            v-if="resumeModalMode === 'preview' && resumeModalType === 'custom' && resumeModalMeta.changeSummary"
            class="change-summary-banner"
          >
            <span class="change-summary-kicker">改写说明</span>
            <span>{{ resumeModalMeta.changeSummary }}</span>
          </div>
          <div
            v-if="resumeModalMode === 'preview'"
            class="modal-body modal-preview markdown-preview"
            v-html="renderMarkdown(resumeModalContent)"
          ></div>
          <div v-else class="modal-edit">
            <label class="edit-label">标题</label>
            <input v-model="editTitle" class="edit-title" type="text" maxlength="128">
            <label class="edit-label">内容</label>
            <textarea v-model="editContent" class="edit-content" maxlength="50000" />
            <div v-if="resumeModalError" class="modal-edit-error">{{ resumeModalError }}</div>
            <div class="modal-footer">
              <button type="button" class="action-btn" @click="closeModal">取消</button>
              <button type="button" class="btn-primary" :disabled="resumeSaving" @click="saveResume">
                {{ resumeSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 手动创建弹层 -->
    <div v-if="createModalOpen" class="modal-overlay" @click.self="createModalOpen = false">
      <div class="modal-panel modal-panel--large">
        <div class="modal-header">
          <span>手动创建默认简历</span>
          <button type="button" class="modal-close" @click="createModalOpen = false">×</button>
        </div>
        <div class="modal-edit">
          <label class="edit-label">标题</label>
          <input v-model="createTitle" class="edit-title" type="text" maxlength="128" placeholder="如：我的简历">
          <label class="edit-label">内容</label>
          <textarea v-model="createContent" class="edit-content" maxlength="50000" placeholder="粘贴或输入简历正文..." />
          <div class="modal-footer">
            <button type="button" class="action-btn" @click="createModalOpen = false">取消</button>
            <button type="button" class="btn-primary" :disabled="creating" @click="confirmCreate">
              {{ creating ? '创建中...' : '创建' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createResume,
  deleteResume,
  downloadResumeDocx,
  downloadResumePdf,
  getResume,
  listResumes,
  updateResume,
  uploadResumeFile,
} from '../api/resume'
import {
  deleteVersion,
  downloadVersionDocx,
  downloadVersionPdf,
  getVersion,
  listVersions,
  updateVersion,
} from '../api/resumeVersion'
import { renderMarkdown } from '../utils/markdown'

const router = useRouter()

const resumes = ref([])
const versions = ref([])
const uploading = ref(false)
const uploadError = ref('')
const fileInputRef = ref(null)
const downloadingKey = ref('')
const deleting = ref(false)

const resumeModalOpen = ref(false)
const resumeModalMode = ref('preview')
const resumeModalType = ref('default')
const resumeModalItem = ref(null)
const resumeModalTitle = ref('')
const resumeModalContent = ref('')
const editTitle = ref('')
const editContent = ref('')
const resumeModalLoading = ref(false)
const resumeModalError = ref('')
const resumeSaving = ref(false)
const resumeModalMeta = ref({ targetJdLabel: '', aiScore: null, changeSummary: '' })
const expandedSummaryId = ref('')

const createModalOpen = ref(false)
const createTitle = ref('')
const createContent = ref('')
const creating = ref(false)

const defaultResume = computed(() => resumes.value.find((r) => r.isDefault) || null)

function formatRelativeTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const diffMs = Date.now() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 60) return `${Math.max(diffMin, 1)} 分钟前`
  const diffHour = Math.floor(diffMs / 3600000)
  if (diffHour < 24) return `${diffHour} 小时前`
  const diffDay = Math.floor(diffMs / 86400000)
  if (diffDay < 7) return `${diffDay} 天前`
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}/${day}`
}

function formatGeneratedAt(value) {
  if (!value) return '生成时间未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '生成时间未知'
  return `生成于 ${date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })}`
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function onFileSelected(e) {
  const file = e.target.files?.[0]
  if (fileInputRef.value) fileInputRef.value.value = ''
  if (!file) return
  await doUpload(file)
}

function onFileDrop(e) {
  const file = e.dataTransfer.files?.[0]
  if (file) doUpload(file)
}

async function doUpload(file) {
  uploadError.value = ''
  uploading.value = true
  try {
    await uploadResumeFile(file, file.name.replace(/\.[^.]+$/, ''))
    await loadResumes()
  } catch (e) {
    uploadError.value = e?.message || '上传失败，请重试'
  } finally {
    uploading.value = false
  }
}

function openCreateModal() {
  createTitle.value = ''
  createContent.value = ''
  createModalOpen.value = true
}

async function confirmCreate() {
  const title = createTitle.value.trim()
  const content = createContent.value.trim()
  if (!title || !content) return
  creating.value = true
  try {
    await createResume({ title, content, isDefault: true })
    createModalOpen.value = false
    await loadResumes()
  } catch (e) {
    uploadError.value = e?.message || '创建失败'
  } finally {
    creating.value = false
  }
}

function buildPreviewContent(type, detail) {
  if (type === 'default') {
    return detail.content || detail.contentPreview || '暂无内容'
  }
  const meta = []
  if (detail.targetJdLabel) meta.push(`目标 JD：${detail.targetJdLabel}`)
  if (detail.aiScore != null) meta.push(`ATS 分：${Math.round(detail.aiScore)}`)
  const body = detail.contentMarkdown || '暂无内容'
  return meta.length ? `${meta.join('\n')}\n\n${body}` : body
}

function toggleSummary(versionId) {
  expandedSummaryId.value = expandedSummaryId.value === versionId ? '' : versionId
}

async function openModal(type, item, mode = 'preview') {
  if (!item) return
  resumeModalType.value = type
  resumeModalItem.value = item
  resumeModalMode.value = mode
  resumeModalOpen.value = true
  resumeModalLoading.value = true
  resumeModalError.value = ''
  resumeModalContent.value = ''
  editTitle.value = ''
  editContent.value = ''

  try {
    const detail = type === 'default'
      ? await getResume(item.id)
      : await getVersion(item.versionId)

    resumeModalTitle.value = type === 'default'
      ? (detail.title || '默认简历')
      : (detail.versionName || '历史定制简历')
    editTitle.value = resumeModalTitle.value
    editContent.value = type === 'default'
      ? (detail.content || '')
      : (detail.contentMarkdown || '')
    if (type === 'custom') {
      resumeModalMeta.value = {
        targetJdLabel: detail.targetJdLabel || '',
        aiScore: detail.aiScore ?? null,
        changeSummary: detail.changeSummary || item.changeSummary || '',
      }
    }
    resumeModalContent.value = buildPreviewContent(type, detail)
  } catch (e) {
    resumeModalError.value = e?.message || '加载失败'
  } finally {
    resumeModalLoading.value = false
  }
}

function closeModal() {
  resumeModalOpen.value = false
  resumeModalError.value = ''
}

async function saveResume() {
  const title = editTitle.value.trim()
  const content = editContent.value.trim()
  if (!title || !content) {
    resumeModalError.value = '标题和内容不能为空'
    return
  }

  resumeSaving.value = true
  resumeModalError.value = ''
  try {
    let savedTitle = title
    if (resumeModalType.value === 'default') {
      const updated = await updateResume(resumeModalItem.value.id, { title, content })
      const idx = resumes.value.findIndex((r) => r.id === resumeModalItem.value.id)
      if (idx >= 0) {
        resumes.value[idx] = {
          ...resumes.value[idx],
          title: updated.title,
          contentPreview: (updated.content || '').slice(0, 200),
          updatedAt: updated.updatedAt,
        }
      }
    } else {
      const updated = await updateVersion(resumeModalItem.value.versionId, {
        versionName: title,
        contentMarkdown: content,
      })
      savedTitle = updated.versionName || resumeModalTitle.value || '历史定制简历'
      const idx = versions.value.findIndex((v) => v.versionId === resumeModalItem.value.versionId)
      if (idx >= 0) {
        versions.value[idx] = {
          ...versions.value[idx],
          versionName: updated.versionName,
          targetCompany: updated.targetCompany ?? versions.value[idx].targetCompany,
          targetJdTitle: updated.targetJdTitle ?? versions.value[idx].targetJdTitle,
          versionSeq: updated.versionSeq ?? versions.value[idx].versionSeq,
          changeSummary: updated.changeSummary ?? versions.value[idx].changeSummary,
        }
      }
      resumeModalItem.value = { ...resumeModalItem.value, versionName: updated.versionName }
    }

    resumeModalTitle.value = savedTitle
    resumeModalContent.value = resumeModalType.value === 'default'
      ? content
      : buildPreviewContent('custom', {
          contentMarkdown: content,
          targetJdLabel: resumeModalMeta.value.targetJdLabel,
          aiScore: resumeModalMeta.value.aiScore,
          changeSummary: resumeModalMeta.value.changeSummary,
        })
    resumeModalMode.value = 'preview'
  } catch (e) {
    resumeModalError.value = e?.message || '保存失败'
  } finally {
    resumeSaving.value = false
  }
}

async function handleDownloadDefault(resume, format) {
  if (!resume?.id || downloadingKey.value) return
  downloadingKey.value = `resume-${format}-${resume.id}`
  uploadError.value = ''
  try {
    if (format === 'docx') {
      await downloadResumeDocx(resume.id, resume.title)
    } else {
      await downloadResumePdf(resume.id, resume.title)
    }
  } catch (e) {
    uploadError.value = e?.message || '下载失败'
  } finally {
    downloadingKey.value = ''
  }
}

async function handleDownloadVersion(version, format) {
  if (!version?.versionId || downloadingKey.value) return
  downloadingKey.value = `version-${format}-${version.versionId}`
  uploadError.value = ''
  try {
    if (format === 'docx') {
      await downloadVersionDocx(version.versionId, version.versionName || '历史定制简历')
    } else {
      await downloadVersionPdf(version.versionId, version.versionName || '历史定制简历')
    }
  } catch (e) {
    uploadError.value = e?.message || '下载失败'
  } finally {
    downloadingKey.value = ''
  }
}

async function handleDeleteDefault() {
  if (!defaultResume.value?.id || deleting.value) return
  if (!window.confirm('确定删除默认简历吗？删除后不可恢复。')) return
  deleting.value = true
  uploadError.value = ''
  try {
    await deleteResume(defaultResume.value.id)
    await loadResumes()
  } catch (e) {
    uploadError.value = e?.message || '删除失败'
  } finally {
    deleting.value = false
  }
}

async function handleDeleteVersion(v) {
  if (!v?.versionId || deleting.value) return
  if (!window.confirm(`确定删除「${v.versionName || '历史定制简历'}」吗？`)) return
  deleting.value = true
  uploadError.value = ''
  try {
    await deleteVersion(v.versionId)
    await loadVersions()
  } catch (e) {
    uploadError.value = e?.message || '删除失败'
  } finally {
    deleting.value = false
  }
}

async function loadResumes() {
  try {
    resumes.value = await listResumes() || []
  } catch {
    resumes.value = []
  }
}

async function loadVersions() {
  try {
    versions.value = await listVersions() || []
  } catch {
    versions.value = []
  }
}

onMounted(async () => {
  await Promise.allSettled([loadResumes(), loadVersions()])
})
</script>

<style scoped>
.resume-page {
  min-height: 100%;
  background: #f8fafc;
  padding: 16px;
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
  overflow-x: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.btn-back {
  background: none;
  border: none;
  color: #4f46e5;
  font-size: 14px;
  cursor: pointer;
  font-family: inherit;
  padding: 0;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.resume-section {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  border: 1px solid #e2e8f0;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 12px;
}

.section-desc {
  margin: -8px 0 12px;
  font-size: 12px;
  color: #64748b;
}

.upload-area {
  border: 2px dashed #c7d2fe;
  border-radius: 10px;
  padding: 32px;
  text-align: center;
  cursor: pointer;
  background: #f5f3ff;
  transition: background 0.2s;
}

.upload-area:hover {
  background: #ede9fe;
}

.upload-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.upload-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.upload-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.upload-progress {
  margin-top: 10px;
  font-size: 13px;
  color: #4f46e5;
}

.hidden-input {
  display: none;
}

.error-text {
  margin: 8px 0 0;
  font-size: 12px;
  color: #ef4444;
}

.btn-text-link {
  background: none;
  border: none;
  color: #7c3aed;
  font-size: 13px;
  cursor: pointer;
  padding: 8px 0 0;
  font-family: inherit;
}

.default-resume-card {
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border-radius: 10px;
  padding: 14px;
}

.resume-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.badge-default {
  background: #4f46e5;
  color: #fff;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 700;
}

.badge-custom {
  background: #fef3c7;
  color: #92400e;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.resume-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.resume-date {
  font-size: 11px;
  color: #64748b;
}

.resume-preview-text {
  margin: 0 0 12px;
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resume-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-btn {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 6px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  color: #334155;
}

.action-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.action-btn--danger {
  color: #dc2626;
  border-color: #fecaca;
  background: #fef2f2;
}

.empty-tip {
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.6;
}

.version-list {
  display: flex;
  flex-direction: column;
}

.version-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.version-row:last-child {
  border-bottom: none;
}

.version-row-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.version-row-left {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.version-info {
  min-width: 0;
}

.version-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.version-meta {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.version-row-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.ats-chip {
  background: #d1fae5;
  color: #047857;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.summary-toggle {
  align-self: flex-start;
  border: none;
  background: transparent;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 600;
  padding: 0;
  cursor: pointer;
  font-family: inherit;
}

.change-summary {
  border-left: 3px solid #a5b4fc;
  background: #f8fafc;
  color: #334155;
  border-radius: 8px;
  padding: 9px 10px;
  font-size: 12px;
  line-height: 1.6;
}

.change-summary-banner {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 12px 16px 0;
  border: 1px solid #c7d2fe;
  background: #eef2ff;
  color: #334155;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.55;
}

.change-summary-kicker {
  color: #4338ca;
  font-size: 11px;
  font-weight: 700;
}

/* 弹层 */
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

.modal-panel--large {
  width: min(720px, 100%);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  color: #0f172a;
}

.modal-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-tab-btn {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
  color: #64748b;
  line-height: 1;
}

.modal-status {
  padding: 32px 16px;
  text-align: center;
  color: #64748b;
  font-size: 13px;
}

.modal-status--error {
  color: #b91c1c;
}

.modal-body {
  padding: 16px;
  overflow: auto;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
  flex: 1;
  min-height: 0;
}

.modal-preview {
  max-height: 60vh;
}

.change-summary-banner + .modal-preview {
  max-height: calc(60vh - 80px);
}

.markdown-preview {
  white-space: normal;
}

.markdown-preview :deep(p) {
  margin: 0 0 10px;
}

.markdown-preview :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
  margin: 6px 0 10px 18px;
  padding: 0;
}

.markdown-preview :deep(li) {
  margin-bottom: 5px;
}

.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin: 12px 0 6px;
  color: #0f172a;
}

.modal-edit {
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 8px;
  flex: 1;
  min-height: 0;
}

.edit-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
}

.edit-title {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  color: #0f172a;
}

.edit-content {
  flex: 1;
  min-height: 280px;
  max-height: 50vh;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: #334155;
  resize: vertical;
}

.modal-edit-error {
  color: #b91c1c;
  font-size: 12px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.btn-primary {
  background: #4f46e5;
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.btn-primary:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
