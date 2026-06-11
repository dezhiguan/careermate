<template>
  <div class="mine-page">
    <!-- 区块 1：用户身份卡 -->
    <section class="identity-card">
      <label class="identity-avatar-wrap" title="点击上传头像">
        <input
          ref="avatarInputRef"
          type="file"
          accept="image/png,image/jpeg,image/webp,image/gif"
          class="avatar-input"
          @change="onAvatarSelected"
        >
        <div class="identity-avatar">
          <img v-if="avatarUrl" :src="avatarUrl" alt="" class="avatar-image">
          <span v-else class="avatar-initial">{{ avatarInitial }}</span>
          <span class="online-dot" />
          <span class="avatar-upload-hint">换头像</span>
        </div>
      </label>
      <div class="identity-info">
        <div v-if="!editingName" class="identity-name-row">
          <div class="identity-name">{{ displayName }}</div>
          <button type="button" class="name-edit-btn" @click="startEditName">编辑</button>
        </div>
        <div v-else class="identity-name-edit">
          <input
            v-model="editDisplayName"
            class="name-input"
            type="text"
            maxlength="64"
            placeholder="输入昵称"
            @keydown.enter="saveDisplayName"
          >
          <button type="button" class="name-save-btn" :disabled="profileSaving" @click="saveDisplayName">
            {{ profileSaving ? '保存中' : '保存' }}
          </button>
          <button type="button" class="name-cancel-btn" @click="cancelEditName">取消</button>
        </div>
        <div class="identity-sub">{{ loginHint }}</div>
        <div v-if="profileMessage" class="profile-message" :class="{ 'profile-message--error': profileError }">
          {{ profileMessage }}
        </div>
      </div>
      <div class="identity-stats">
        <div class="stat-item">
          <div class="stat-num">{{ resumeCount }}</div>
          <div class="stat-label">简历</div>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <div class="stat-num">{{ totalAnswered }}</div>
          <div class="stat-label">已做题</div>
        </div>
      </div>
    </section>

    <!-- 区块 2：简历库 -->
    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">📄 我的简历（{{ resumeCount }}）</div>
          <div class="panel-sub">
            默认 {{ defaultResume ? 1 : 0 }} 份 + 在 小职 里针对 JD 定制 {{ versions.length }} 份
          </div>
        </div>
      </div>

      <div v-if="resumesLoadFailed && versionsLoadFailed" class="empty-hint">暂无数据</div>

      <!-- 桌面端：4 列 grid -->
      <div v-else class="resume-desktop">
        <div
          v-if="defaultResume"
          class="resume-card resume-card--default"
        >
          <div class="resume-card-top">
            <span class="badge badge-default">默认</span>
            <span class="resume-date">{{ formatRelativeTime(defaultResume.updatedAt || defaultResume.createdAt) }}</span>
          </div>
          <div class="resume-title">{{ defaultResume.title || '默认简历' }}</div>
          <div class="resume-preview">{{ defaultResume.contentPreview || '暂无预览' }}</div>
          <div class="resume-actions">
            <button type="button" class="btn-ghost" @click="openResumeModal('default', defaultResume, 'preview')">预览</button>
            <button type="button" class="btn-ghost" @click="openResumeModal('default', defaultResume, 'edit')">修改</button>
          </div>
        </div>

        <div
          v-for="version in versions"
          :key="version.versionId"
          class="resume-card"
        >
          <div class="resume-card-top">
            <span class="badge badge-custom">定制</span>
            <span class="resume-date">{{ formatRelativeTime(version.createdAt) }}</span>
          </div>
          <div class="resume-title">{{ version.versionName || '定制简历' }}</div>
          <div class="resume-preview">{{ version.targetJdLabel || '针对 JD 定制' }}</div>
          <div v-if="version.aiScore != null" class="resume-ats">
            <span class="chip chip-success">ATS {{ Math.round(version.aiScore) }}</span>
          </div>
          <div class="resume-actions resume-actions--triple">
            <button type="button" class="btn-ghost" @click="openResumeModal('custom', version, 'preview')">预览</button>
            <button type="button" class="btn-ghost" @click="openResumeModal('custom', version, 'edit')">修改</button>
            <button
              type="button"
              class="btn-ghost"
              :disabled="pdfDownloadingId === version.versionId"
              @click="handleDownloadPdf(version)"
            >
              {{ pdfDownloadingId === version.versionId ? '下载中...' : 'PDF' }}
            </button>
          </div>
        </div>

        <button type="button" class="resume-card resume-card--placeholder" @click="goChat">
          <span class="placeholder-icon">+</span>
          <span class="placeholder-text">去 小职<br>再定制一份</span>
        </button>
      </div>

      <!-- 移动端：行列表 -->
      <div v-if="!(resumesLoadFailed && versionsLoadFailed)" class="resume-mobile">
        <div v-if="!defaultResume && versions.length === 0" class="empty-hint">暂无数据</div>
        <div
          v-if="defaultResume"
          class="resume-row"
        >
          <span class="badge badge-default badge-sm">默认</span>
          <div class="resume-row-title">{{ defaultResume.title || '默认简历' }}</div>
          <button type="button" class="btn-ghost btn-sm" @click="openResumeModal('default', defaultResume, 'preview')">预览</button>
          <button type="button" class="btn-ghost btn-sm" @click="openResumeModal('default', defaultResume, 'edit')">修改</button>
        </div>
        <div
          v-for="version in versions"
          :key="`m-${version.versionId}`"
          class="resume-row"
        >
          <span class="badge badge-custom badge-sm">定制</span>
          <div class="resume-row-title">{{ version.versionName || '定制简历' }}</div>
          <span v-if="version.aiScore != null" class="resume-row-score">ATS {{ Math.round(version.aiScore) }}</span>
          <button type="button" class="btn-ghost btn-sm" @click="openResumeModal('custom', version, 'preview')">预览</button>
          <button type="button" class="btn-ghost btn-sm" @click="openResumeModal('custom', version, 'edit')">修改</button>
          <button
            type="button"
            class="btn-ghost btn-sm"
            :disabled="pdfDownloadingId === version.versionId"
            @click="handleDownloadPdf(version)"
          >
            PDF
          </button>
        </div>
        <button type="button" class="resume-row resume-row--link" @click="goChat">
          <span>去 小职 再定制一份 →</span>
        </button>
      </div>
    </section>

    <!-- 区块 3：训练记录 -->
    <section class="panel">
      <div class="panel-head">
        <div>
          <div class="panel-title">📝 训练记录</div>
          <div class="panel-sub">已做 {{ totalAnswered }} 题 · 均 {{ avgScore }} 分</div>
        </div>
      </div>

      <div v-if="sessionsLoadFailed" class="empty-hint">暂无数据</div>
      <div v-else-if="sessions.length === 0" class="empty-hint">暂无数据</div>
      <div v-else class="training-layout">
        <div class="training-col">
          <div class="col-label col-label-desktop">按 JD 分组</div>
          <div class="training-scroll training-scroll--jd" tabindex="0">
            <div class="training-scroll-list">
              <div
                v-for="session in sessions"
                :key="session.id"
                class="jd-group-card"
              >
                <div class="jd-group-head">
                  <div class="jd-group-title">🏢 {{ session.title || '训练会话' }} · {{ session.totalQuestions || 0 }} 题</div>
                  <span
                    class="jd-group-score"
                    :style="{ color: sessionScoreColor(session.averageScore) }"
                  >
                    均 {{ Math.round(session.averageScore || 0) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
          <div v-if="sessions.length > 3" class="scroll-hint">滑动查看更多</div>
        </div>

        <div class="training-col">
          <div class="col-label">最近做过</div>
          <div class="training-scroll training-scroll--recent" tabindex="0">
            <div class="training-scroll-list">
              <div
                v-for="session in sortedSessionsByTime"
                :key="`recent-${session.id}`"
                class="recent-row"
              >
                <span class="recent-badge" :class="recentBadgeClass(session.averageScore)">
                  {{ Math.round(session.averageScore || 0) }}
                </span>
                <div class="recent-title">{{ session.title || '训练会话' }}</div>
                <span class="recent-time">{{ formatRelativeTime(session.createdAt) }}</span>
              </div>
            </div>
          </div>
          <div v-if="sortedSessionsByTime.length > 3" class="scroll-hint">滑动查看更多</div>
        </div>
      </div>
    </section>

    <button type="button" class="logout-btn" @click="handleLogout">退出登录</button>

    <!-- 简历预览 / 编辑弹层 -->
    <div v-if="resumeModalOpen" class="modal-overlay" @click.self="closeResumeModal">
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
            <button type="button" class="modal-close" @click="closeResumeModal">×</button>
          </div>
        </div>

        <div v-if="resumeModalLoading" class="modal-status">加载中...</div>
        <div v-else-if="resumeModalError && resumeModalMode === 'preview' && !editContent" class="modal-status modal-status--error">
          {{ resumeModalError }}
        </div>
        <template v-else>
          <div v-if="resumeModalMode === 'preview'" class="modal-body modal-preview">{{ resumeModalContent }}</div>
          <div v-else class="modal-edit">
            <label class="edit-label">标题</label>
            <input v-model="editTitle" class="edit-title" type="text" maxlength="128">
            <label class="edit-label">内容</label>
            <textarea v-model="editContent" class="edit-content" maxlength="50000" />
            <div v-if="resumeModalError" class="modal-edit-error">{{ resumeModalError }}</div>
            <div class="modal-footer">
              <button type="button" class="btn-ghost" @click="closeResumeModal">取消</button>
              <button type="button" class="btn-primary" :disabled="resumeSaving" @click="saveResume">
                {{ resumeSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getResume, listResumes, updateResume } from '../api/resume'
import { downloadVersionPdf, getVersion, listVersions, updateVersion } from '../api/resumeVersion'
import { listInterviewSessions } from '../api/interview'
import { authStore } from '../stores/authStore'

const router = useRouter()

const resumes = ref([])
const versions = ref([])
const sessions = ref([])
const resumesLoadFailed = ref(false)
const versionsLoadFailed = ref(false)
const sessionsLoadFailed = ref(false)
const pdfDownloadingId = ref(null)

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
const resumeModalMeta = ref({ targetJdLabel: '', aiScore: null })

const avatarInputRef = ref(null)
const editingName = ref(false)
const editDisplayName = ref('')
const profileSaving = ref(false)
const profileMessage = ref('')
const profileError = ref(false)

const MAX_AVATAR_BYTES = 512 * 1024

const displayName = computed(() => (
  authStore.state.currentUser?.displayName
  || authStore.state.currentUser?.username
  || '未登录'
))

const avatarUrl = computed(() => authStore.state.currentUser?.avatarUrl || '')

const avatarInitial = computed(() => {
  const name = displayName.value || '用'
  return name.charAt(0).toUpperCase()
})

const loginHint = computed(() => (
  authStore.state.currentUser?.username ? '已登录' : '未登录'
))

watch(displayName, (value) => {
  if (!editingName.value) {
    editDisplayName.value = value === '未登录' ? '' : value
  }
})

const defaultResume = computed(() => resumes.value.find((r) => r.isDefault) || null)

const resumeCount = computed(() => resumes.value.length + versions.value.length)

const totalAnswered = computed(() => (
  sessions.value.reduce((sum, s) => sum + (s.answeredQuestions || 0), 0)
))

const avgScore = computed(() => {
  const scored = sessions.value.filter((s) => s.averageScore && s.averageScore !== 0)
  if (scored.length === 0) return 0
  const total = scored.reduce((sum, s) => sum + s.averageScore, 0)
  return Math.round(total / scored.length)
})

const sortedSessionsByTime = computed(() => (
  [...sessions.value].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
))

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

function sessionScoreColor(score) {
  const value = Number(score) || 0
  if (value >= 85) return '#10b981'
  if (value >= 70) return '#4f46e5'
  return '#64748b'
}

function recentBadgeClass(score) {
  const value = Number(score) || 0
  if (value >= 80) return 'badge-green'
  if (value >= 60) return 'badge-yellow'
  return 'badge-red'
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

async function openResumeModal(type, item, mode = 'preview') {
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
      : (detail.versionName || '定制简历')
    editTitle.value = resumeModalTitle.value
    editContent.value = type === 'default'
      ? (detail.content || '')
      : (detail.contentMarkdown || '')
    if (type === 'custom') {
      resumeModalMeta.value = {
        targetJdLabel: detail.targetJdLabel || '',
        aiScore: detail.aiScore ?? null,
      }
    }
    resumeModalContent.value = buildPreviewContent(type, detail)
  } catch (e) {
    resumeModalError.value = e?.message || '加载失败'
  } finally {
    resumeModalLoading.value = false
  }
}

function closeResumeModal() {
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
      const idx = versions.value.findIndex((v) => v.versionId === resumeModalItem.value.versionId)
      if (idx >= 0) {
        versions.value[idx] = {
          ...versions.value[idx],
          versionName: updated.versionName,
        }
      }
      resumeModalItem.value = { ...resumeModalItem.value, versionName: updated.versionName }
    }

    resumeModalTitle.value = title
    resumeModalContent.value = resumeModalType.value === 'default'
      ? content
      : buildPreviewContent('custom', {
          contentMarkdown: content,
          targetJdLabel: resumeModalMeta.value.targetJdLabel,
          aiScore: resumeModalMeta.value.aiScore,
        })
    resumeModalMode.value = 'preview'
  } catch (e) {
    resumeModalError.value = e?.message || '保存失败'
  } finally {
    resumeSaving.value = false
  }
}

async function handleDownloadPdf(version) {
  if (!version?.versionId || pdfDownloadingId.value) return
  pdfDownloadingId.value = version.versionId
  try {
    await downloadVersionPdf(version.versionId, version.versionName)
  } catch {
    // 下载失败时静默处理，按钮恢复可点
  } finally {
    pdfDownloadingId.value = null
  }
}

function goChat() {
  router.push('/chat')
}

function clearProfileMessage() {
  profileMessage.value = ''
  profileError.value = false
}

function showProfileMessage(message, isError = false) {
  profileMessage.value = message
  profileError.value = isError
}

function startEditName() {
  editingName.value = true
  editDisplayName.value = displayName.value === '未登录' ? '' : displayName.value
  clearProfileMessage()
}

function cancelEditName() {
  editingName.value = false
  editDisplayName.value = displayName.value === '未登录' ? '' : displayName.value
}

async function saveDisplayName() {
  const name = editDisplayName.value.trim()
  if (!name) {
    showProfileMessage('昵称不能为空', true)
    return
  }
  profileSaving.value = true
  clearProfileMessage()
  try {
    await authStore.updateProfile({
      displayName: name,
      avatarUrl: avatarUrl.value || undefined,
    })
    editingName.value = false
    showProfileMessage('昵称已更新')
  } catch (e) {
    showProfileMessage(e?.message || '保存失败', true)
  } finally {
    profileSaving.value = false
  }
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('读取头像失败'))
    reader.readAsDataURL(file)
  })
}

async function onAvatarSelected(event) {
  const file = event.target?.files?.[0]
  if (avatarInputRef.value) {
    avatarInputRef.value.value = ''
  }
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showProfileMessage('请选择图片文件', true)
    return
  }
  if (file.size > MAX_AVATAR_BYTES) {
    showProfileMessage('头像不能超过 512KB', true)
    return
  }

  profileSaving.value = true
  clearProfileMessage()
  try {
    const dataUrl = await readFileAsDataUrl(file)
    await authStore.updateProfile({
      displayName: displayName.value === '未登录' ? '用户' : displayName.value,
      avatarUrl: dataUrl,
    })
    showProfileMessage('头像已更新')
  } catch (e) {
    showProfileMessage(e?.message || '头像上传失败', true)
  } finally {
    profileSaving.value = false
  }
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

onMounted(async () => {
  try {
    await authStore.fetchCurrentUser()
  } catch {
    // 未登录时保持现状
  }
  const [resumesResult, versionsResult, sessionsResult] = await Promise.allSettled([
    listResumes(),
    listVersions(),
    listInterviewSessions(),
  ])

  if (resumesResult.status === 'fulfilled') {
    resumes.value = Array.isArray(resumesResult.value) ? resumesResult.value : []
    resumesLoadFailed.value = false
  } else {
    resumes.value = []
    resumesLoadFailed.value = true
  }

  if (versionsResult.status === 'fulfilled') {
    versions.value = Array.isArray(versionsResult.value) ? versionsResult.value : []
    versionsLoadFailed.value = false
  } else {
    versions.value = []
    versionsLoadFailed.value = true
  }

  if (sessionsResult.status === 'fulfilled') {
    sessions.value = Array.isArray(sessionsResult.value) ? sessionsResult.value : []
    sessionsLoadFailed.value = false
  } else {
    sessions.value = []
    sessionsLoadFailed.value = true
  }
})
</script>

<style scoped>
.mine-page {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background: #f8fafc;
  min-height: 100%;
}

/* 区块 1：身份卡 */
.identity-card {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 16px;
  padding: 22px;
  color: #fff;
  display: flex;
  gap: 18px;
  align-items: center;
}

.identity-avatar-wrap {
  position: relative;
  flex-shrink: 0;
  cursor: pointer;
}

.avatar-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  z-index: 2;
}

.identity-avatar {
  position: relative;
  width: 64px;
  height: 64px;
  background: rgba(255, 255, 255, 0.2);
  border: 3px solid rgba(255, 255, 255, 0.4);
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 24px;
  overflow: hidden;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-initial {
  font-weight: 800;
  font-size: 24px;
}

.avatar-upload-hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.45);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.2s;
}

.identity-avatar-wrap:hover .avatar-upload-hint,
.identity-avatar-wrap:focus-within .avatar-upload-hint {
  opacity: 1;
}

.online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 14px;
  height: 14px;
  background: #10b981;
  border: 3px solid #fff;
  border-radius: 50%;
}

.identity-info {
  flex: 1;
  min-width: 0;
}

.identity-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.identity-name {
  font-size: 20px;
  font-weight: 800;
}

.name-edit-btn,
.name-save-btn,
.name-cancel-btn {
  border: 1px solid rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 11px;
  cursor: pointer;
  font-family: inherit;
}

.name-edit-btn:disabled,
.name-save-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.identity-name-edit {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.name-input {
  flex: 1;
  min-width: 120px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  background: rgba(255, 255, 255, 0.95);
  color: #0f172a;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 13px;
  font-family: inherit;
}

.identity-sub {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
}

.profile-message {
  margin-top: 6px;
  font-size: 11px;
  color: #e0e7ff;
}

.profile-message--error {
  color: #fecaca;
}

.identity-stats {
  display: flex;
  gap: 24px;
  align-items: center;
  text-align: center;
  flex-shrink: 0;
}

.stat-num {
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
}

.stat-label {
  font-size: 10px;
  opacity: 0.85;
  margin-top: 4px;
}

.stat-divider {
  width: 1px;
  height: 36px;
  background: rgba(255, 255, 255, 0.25);
}

/* 通用面板 */
.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.panel-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.empty-hint {
  text-align: center;
  padding: 24px 12px;
  color: #94a3b8;
  font-size: 13px;
}

/* 简历库 - 桌面 grid */
.resume-desktop {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.resume-mobile {
  display: none;
}

.resume-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  text-align: left;
}

.resume-card--default {
  background: linear-gradient(135deg, #eef2ff, #ede9fe);
  border-color: #c7d2fe;
}

.resume-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.badge {
  padding: 1px 8px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 700;
}

.badge-default {
  background: #4f46e5;
  color: #fff;
}

.badge-custom {
  background: #fef3c7;
  color: #92400e;
}

.badge-sm {
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 9px;
  flex-shrink: 0;
}

.resume-date {
  font-size: 10px;
  color: #64748b;
}

.resume-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.resume-preview {
  font-size: 10px;
  color: #64748b;
  margin-top: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resume-ats {
  margin-top: 6px;
}

.chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 600;
}

.chip-success {
  background: #d1fae5;
  color: #047857;
}

.resume-actions {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}

.resume-actions--triple .btn-ghost {
  font-size: 9px;
  padding: 6px 4px;
}

.btn-ghost {
  flex: 1;
  background: #fff;
  color: #334155;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px;
  font-size: 10px;
  cursor: pointer;
  font-family: inherit;
}

.btn-ghost:disabled {
  opacity: 0.55;
  cursor: default;
}

.btn-sm {
  flex: 0 0 auto;
  padding: 4px 8px;
}

.resume-card--placeholder {
  border: 1px dashed #cbd5e1;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  cursor: pointer;
  min-height: 140px;
  font-family: inherit;
}

.placeholder-icon {
  font-size: 24px;
  line-height: 1;
  margin-bottom: 6px;
}

.placeholder-text {
  font-size: 11px;
  text-align: center;
  line-height: 1.4;
}

/* 训练记录 */
.training-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.col-label {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.col-label-desktop {
  display: block;
}

.training-scroll {
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
  border-radius: 8px;
  position: relative;
}

.training-scroll--jd {
  max-height: 155px;
}

.training-scroll--recent {
  max-height: 143px;
}

.training-scroll::-webkit-scrollbar {
  width: 4px;
}

.training-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.training-scroll-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.training-scroll--recent .training-scroll-list {
  gap: 6px;
}

.scroll-hint {
  margin-top: 6px;
  font-size: 10px;
  color: #94a3b8;
  text-align: center;
}

.jd-group-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  flex-shrink: 0;
}

.jd-group-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}

.jd-group-title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
  flex: 1;
}

.jd-group-score {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.jd-group-meta {
  font-size: 10px;
  color: #64748b;
  line-height: 1.7;
}

.recent-row {
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 8px;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.recent-badge {
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.badge-green {
  background: #dcfce7;
  color: #15803d;
}

.badge-yellow {
  background: #fef3c7;
  color: #92400e;
}

.badge-red {
  background: #fee2e2;
  color: #b91c1c;
}

.recent-title {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  font-size: 10px;
  color: #64748b;
  flex-shrink: 0;
}

.logout-btn {
  width: 100%;
  border: 1px solid #fecaca;
  background: #fff;
  color: #ef4444;
  border-radius: 12px;
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.logout-btn:hover {
  background: #fff1f2;
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

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  color: #0f172a;
}

.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
  color: #64748b;
  line-height: 1;
}

.modal-panel--large {
  width: min(720px, 100%);
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
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
  flex: 1;
  min-height: 0;
}

.modal-preview {
  max-height: 60vh;
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

/* 移动端 */
@media (max-width: 768px) {
  .mine-page {
    padding: 14px;
    gap: 10px;
  }

  .identity-card {
    padding: 18px 16px;
    gap: 12px;
  }

  .identity-avatar {
    width: 54px;
    height: 54px;
    font-size: 20px;
  }

  .online-dot {
    width: 13px;
    height: 13px;
  }

  .identity-name {
    font-size: 17px;
  }

  .identity-sub {
    font-size: 10px;
  }

  .identity-stats {
    gap: 14px;
  }

  .stat-num {
    font-size: 18px;
  }

  .stat-label {
    font-size: 9px;
  }

  .panel {
    border-radius: 12px;
    padding: 12px;
  }

  .panel-title {
    font-size: 12px;
  }

  .panel-sub {
    display: none;
  }

  .resume-desktop {
    display: none;
  }

  .resume-mobile {
    display: block;
  }

  .resume-row {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
    border-bottom: 1px solid #f1f5f9;
  }

  .resume-row:last-child {
    border-bottom: none;
  }

  .resume-row-title {
    flex: 1;
    font-size: 11px;
    font-weight: 600;
    color: #0f172a;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .resume-row-score {
    font-size: 10px;
    color: #10b981;
    flex-shrink: 0;
  }

  .resume-row--link {
    border: none;
    background: transparent;
    justify-content: flex-start;
    color: #4f46e5;
    font-size: 11px;
    cursor: pointer;
    font-family: inherit;
    padding-top: 10px;
  }

  .training-layout {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .col-label-desktop {
    display: none;
  }

  .training-scroll--jd {
    max-height: 119px;
  }

  .training-scroll--recent {
    max-height: 113px;
  }

  .jd-group-card {
    padding: 8px 0;
    background: transparent;
    border-bottom: 1px solid #f1f5f9;
    border-radius: 0;
  }

  .jd-group-head {
    margin-bottom: 0;
  }

  .jd-group-meta {
    display: none;
  }

}

@media (max-width: 1100px) {
  .resume-desktop {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
