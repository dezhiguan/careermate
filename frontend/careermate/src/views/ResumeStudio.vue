<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">📝 简历工作室</h2>
      <p class="page-sub">文本简历：创建、编辑、设为默认（V1）</p>
    </div>

    <div v-if="pageError" class="banner error">{{ pageError }}</div>
    <div v-if="pageLoading" class="banner loading">加载中...</div>

    <div class="content-grid">
      <div class="left-panel">
        <div v-if="resumes.length === 0 && !showCreateForm" class="empty-state">
          <div class="empty-icon">📋</div>
          <div class="empty-text">还没有简历，先创建一份文本简历</div>
          <button class="btn primary" type="button" @click="openCreateForm">创建简历</button>
        </div>

        <div v-else class="list-section">
          <div class="list-toolbar">
            <div class="section-title">我的简历 ({{ resumes.length }})</div>
            <button class="btn ghost" type="button" @click="openCreateForm">+ 新建</button>
            <button class="btn ghost" type="button" :disabled="uploading" @click="fileInputRef?.click()">
              {{ uploading ? '解析中...' : '上传文件' }}
            </button>
            <input
              ref="fileInputRef"
              type="file"
              accept=".pdf,.doc,.docx,.md,.markdown"
              style="display:none"
              @change="onFileSelected"
            />
          </div>

          <div v-if="uploadError" class="upload-error">{{ uploadError }}</div>

          <div
            v-for="item in resumes"
            :key="item.id"
            class="resume-card"
            :class="{ active: selectedId === item.id }"
            @click="selectResume(item.id)"
          >
            <div class="resume-name">
              📄 {{ item.title }}
              <span v-if="item.isDefault" class="default-badge">默认</span>
            </div>
            <div class="resume-meta">{{ item.contentPreview || '（无预览）' }}</div>
            <div class="resume-date">{{ formatDate(item.updatedAt || item.createdAt) }}</div>
          </div>
        </div>

        <div v-if="showCreateForm" class="create-panel">
          <div class="section-title">{{ editingId ? '' : '新建简历' }}</div>
          <label class="field-label">标题</label>
          <input v-model="formTitle" class="field-input" maxlength="128" placeholder="例如：Java 后端简历">
          <label class="field-label">正文</label>
          <textarea
            v-model="formContent"
            class="field-textarea"
            rows="8"
            maxlength="50000"
            placeholder="在此填写简历正文..."
          />
          <div class="form-actions">
            <button class="btn primary" type="button" :disabled="saving" @click="saveCreate">
              {{ saving ? '保存中...' : '保存' }}
            </button>
            <button class="btn ghost" type="button" :disabled="saving" @click="cancelCreate">取消</button>
          </div>
        </div>
      </div>

      <div class="right-panel">
        <div v-if="!selectedId && resumes.length > 0" class="empty-detail">
          <div class="empty-text">点击左侧简历查看与编辑</div>
        </div>

        <div v-else-if="selectedId && detailLoading" class="empty-detail">
          <div class="empty-text">加载简历详情...</div>
        </div>

        <div v-else-if="selectedId" class="detail-panel">
          <div class="section-title">编辑简历</div>
          <label class="field-label">标题</label>
          <input v-model="formTitle" class="field-input" maxlength="128">
          <label class="field-label">正文</label>
          <textarea v-model="formContent" class="field-textarea detail-textarea" rows="14" maxlength="50000" />
          <div v-if="detailError" class="inline-error">{{ detailError }}</div>
          <div class="form-actions">
            <button class="btn primary" type="button" :disabled="saving" @click="saveEdit">
              {{ saving ? '保存中...' : '保存修改' }}
            </button>
            <button
              class="btn ghost"
              type="button"
              :disabled="saving || detail?.isDefault"
              @click="makeDefault"
            >
              设为默认
            </button>
            <button class="btn danger" type="button" :disabled="saving" @click="removeResume">删除</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import {
  listResumes,
  createResume,
  getResume,
  updateResume,
  deleteResume,
  setDefaultResume,
  uploadResumeFile,
} from '../api/resume'

const resumes = ref([])
const selectedId = ref(null)
const detail = ref(null)
const formTitle = ref('')
const formContent = ref('')

const pageLoading = ref(false)
const pageError = ref('')
const detailLoading = ref(false)
const detailError = ref('')
const saving = ref(false)
const showCreateForm = ref(false)
const editingId = ref(null)
const fileInputRef = ref(null)
const uploading = ref(false)
const uploadError = ref('')

function formatDate(value) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

async function loadList(selectAfterId = null) {
  pageLoading.value = true
  pageError.value = ''
  try {
    resumes.value = await listResumes()
    if (selectAfterId && resumes.value.some((r) => r.id === selectAfterId)) {
      await selectResume(selectAfterId)
    } else if (selectedId.value && !resumes.value.some((r) => r.id === selectedId.value)) {
      selectedId.value = null
      detail.value = null
      if (resumes.value.length > 0) {
        await selectResume(resumes.value[0].id)
      }
    } else if (!selectedId.value && resumes.value.length > 0) {
      await selectResume(resumes.value[0].id)
    }
  } catch (e) {
    pageError.value = e?.message || '加载简历列表失败'
  } finally {
    pageLoading.value = false
  }
}

async function selectResume(id) {
  selectedId.value = id
  showCreateForm.value = false
  editingId.value = null
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getResume(id)
    formTitle.value = detail.value.title
    formContent.value = detail.value.content
  } catch (e) {
    detailError.value = e?.message || '加载简历失败'
  } finally {
    detailLoading.value = false
  }
}

function openCreateForm() {
  showCreateForm.value = true
  editingId.value = null
  selectedId.value = null
  detail.value = null
  formTitle.value = ''
  formContent.value = ''
  detailError.value = ''
}

function cancelCreate() {
  showCreateForm.value = false
  if (resumes.value.length > 0 && !selectedId.value) {
    selectResume(resumes.value[0].id)
  }
}

function validateForm() {
  const title = formTitle.value.trim()
  const content = formContent.value.trim()
  if (!title) {
    detailError.value = '请填写标题'
    return null
  }
  if (!content) {
    detailError.value = '请填写正文'
    return null
  }
  detailError.value = ''
  return { title, content }
}

async function saveCreate() {
  const payload = validateForm()
  if (!payload) return
  saving.value = true
  try {
    const created = await createResume(payload)
    showCreateForm.value = false
    await loadList(created.id)
  } catch (e) {
    pageError.value = e?.message || '创建失败'
  } finally {
    saving.value = false
  }
}

async function saveEdit() {
  if (!selectedId.value) return
  const payload = validateForm()
  if (!payload) return
  saving.value = true
  try {
    detail.value = await updateResume(selectedId.value, payload)
    await loadList(selectedId.value)
  } catch (e) {
    detailError.value = e?.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function makeDefault() {
  if (!selectedId.value) return
  saving.value = true
  try {
    detail.value = await setDefaultResume(selectedId.value)
    await loadList(selectedId.value)
  } catch (e) {
    detailError.value = e?.message || '设置默认失败'
  } finally {
    saving.value = false
  }
}

async function removeResume() {
  if (!selectedId.value) return
  if (!window.confirm('确定删除这份简历吗？删除后不可恢复。')) return
  saving.value = true
  try {
    await deleteResume(selectedId.value)
    selectedId.value = null
    detail.value = null
    await loadList()
  } catch (e) {
    detailError.value = e?.message || '删除失败'
  } finally {
    saving.value = false
  }
}

async function onFileSelected(event) {
  const file = event.target.files?.[0]
  if (!fileInputRef.value) return
  fileInputRef.value.value = '' // 允许重复选同一文件
  if (!file) return

  uploading.value = true
  uploadError.value = ''
  pageError.value = ''
  try {
    const created = await uploadResumeFile(file, null)
    await loadList(created.id)
  } catch (e) {
    uploadError.value = e?.message || '上传失败'
  } finally {
    uploading.value = false
  }
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.page-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 20px;
  overflow-x: hidden;
}
.page-header { margin-bottom: 16px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.banner {
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 12px;
  margin-bottom: 12px;
}
.banner.loading { background: #eef2ff; color: #3730a3; }
.banner.error { background: #fef2f2; color: #991b1b; }

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 20px;
  align-items: start;
}

.left-panel,
.right-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title { font-weight: 600; font-size: 12px; margin-bottom: 8px; }
.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.empty-state,
.empty-detail {
  text-align: center;
  padding: 32px 16px;
  background: #fff;
  border: 1px dashed var(--border);
  border-radius: 12px;
  color: var(--text-muted);
}
.empty-icon { font-size: 36px; margin-bottom: 8px; }
.empty-text { font-size: 13px; margin-bottom: 12px; }

.resume-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.resume-card:hover,
.resume-card.active {
  border-color: var(--purple);
  box-shadow: 0 2px 8px rgba(139, 92, 246, 0.1);
}
.resume-name { font-weight: 600; font-size: 12px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.default-badge {
  font-size: 10px;
  color: var(--purple);
  background: #f5f3ff;
  border-radius: 999px;
  padding: 1px 6px;
}
.resume-meta {
  color: var(--text-muted);
  font-size: 11px;
  margin-top: 4px;
  line-height: 1.4;
  word-break: break-word;
}
.resume-date { color: var(--text-muted); font-size: 10px; margin-top: 4px; }

.create-panel,
.detail-panel {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px;
}

.field-label { display: block; font-size: 11px; color: var(--text-muted); margin: 8px 0 4px; }
.field-input,
.field-textarea {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 13px;
  font-family: inherit;
  box-sizing: border-box;
}
.field-textarea { resize: vertical; min-height: 120px; }
.detail-textarea { min-height: 220px; }

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.btn {
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid transparent;
}
.btn:disabled { opacity: 0.5; cursor: default; }
.btn.primary { background: var(--purple); color: #fff; }
.btn.ghost { background: #fff; border-color: var(--border); color: var(--text); }
.btn.danger { background: #fff; border-color: #fecaca; color: #E5484D; }

.inline-error {
  margin-top: 8px;
  font-size: 12px;
  color: #E5484D;
}

@media (max-width: 768px) {
  .page-container {
    padding: 16px 12px calc(16px + env(safe-area-inset-bottom));
    max-width: 100%;
  }
  .content-grid { grid-template-columns: 1fr; gap: 16px; }
  .form-actions .btn { flex: 1 1 auto; min-height: 44px; }
  .field-input,
  .field-textarea { font-size: 16px; }
}

.upload-error {
  font-size: 12px;
  color: #E5484D;
  background: #fef2f2;
  border-radius: 6px;
  padding: 6px 10px;
  margin-bottom: 8px;
}
</style>
