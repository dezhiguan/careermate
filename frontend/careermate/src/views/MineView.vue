<template>
  <div class="mine-page">
    <!-- 区块一：用户身份卡 -->
    <section class="identity-card">
      <div class="identity-top">
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
          <div class="identity-career">
            {{ profile.targetRole || '未设置岗位' }} · {{ profile.targetCity || '未设置城市' }}
          </div>
          <div v-if="profileMessage" class="profile-message" :class="{ 'profile-message--error': profileError }">
            {{ profileMessage }}
          </div>
        </div>
      </div>
      <div class="completeness-block">
        <div class="completeness-head">
          <span class="completeness-label">画像完整度</span>
          <span class="completeness-pct">{{ profileCompleteness }}%</span>
        </div>
        <div class="completeness-track">
          <div class="completeness-fill" :style="{ width: profileCompleteness + '%' }" />
        </div>
        <p v-if="profileCompleteness < 80" class="completeness-hint">补充完整后 AI 匹配更精准</p>
      </div>
    </section>

    <!-- 区块二：职业定位 -->
    <section class="profile-section">
      <div class="section-head">
        <span class="section-title">职业定位</span>
        <button v-if="!editingCareer" type="button" class="btn-text" @click="startEditCareer">编辑</button>
        <div v-else class="edit-actions">
          <button type="button" class="btn-text" :disabled="careerSaving" @click="saveCareer">
            {{ careerSaving ? '保存中...' : '保存' }}
          </button>
          <button type="button" class="btn-text-muted" @click="cancelEditCareer">取消</button>
        </div>
      </div>

      <template v-if="!editingCareer">
        <div class="profile-row">
          <span class="profile-label">目标岗位</span>
          <span class="profile-value" :class="{ empty: !profile.targetRole }">
            {{ profile.targetRole || '未填写' }}
          </span>
        </div>
        <div class="profile-row">
          <span class="profile-label">意向城市</span>
          <span class="profile-value" :class="{ empty: !profile.targetCity }">
            {{ profile.targetCity || '未填写' }}
          </span>
        </div>
        <div class="profile-row">
          <span class="profile-label">经验年限</span>
          <span class="profile-value" :class="{ empty: !profile.seniority }">
            {{ profile.seniority || '未填写' }}
          </span>
        </div>
        <div class="profile-row">
          <span class="profile-label">工作方式</span>
          <span class="profile-value" :class="{ empty: !profile.workMode }">
            {{ profile.workMode || '未填写' }}
          </span>
        </div>
      </template>

      <template v-else>
        <div class="edit-row">
          <label>目标岗位</label>
          <input v-model="editCareer.targetRole" list="role-list" placeholder="如：Java后端">
          <datalist id="role-list">
            <option v-for="r in ROLE_SUGGESTIONS" :key="r" :value="r" />
          </datalist>
        </div>
        <div class="edit-row">
          <label>意向城市</label>
          <input v-model="editCareer.targetCity" list="city-list" placeholder="如：广州">
          <datalist id="city-list">
            <option v-for="c in CITY_SUGGESTIONS" :key="c" :value="c" />
          </datalist>
        </div>
        <div class="edit-row">
          <label>经验年限</label>
          <select v-model="editCareer.seniority">
            <option value="">请选择</option>
            <option v-for="y in YEARS_OPTIONS" :key="y" :value="y">{{ y }}</option>
          </select>
        </div>
        <div class="edit-row">
          <label>工作方式</label>
          <select v-model="editCareer.workMode">
            <option value="">请选择</option>
            <option value="全职到岗">全职到岗</option>
            <option value="全职远程">全职远程</option>
            <option value="兼职">兼职</option>
          </select>
        </div>
      </template>
      <p v-if="careerSaveMsg" class="save-msg">{{ careerSaveMsg }}</p>
    </section>

    <!-- 区块三：技能标签 -->
    <section class="profile-section">
      <div class="section-head">
        <span class="section-title">我的技能</span>
        <button v-if="!editingSkills" type="button" class="btn-text" @click="startEditSkills">编辑</button>
        <div v-else class="edit-actions">
          <button type="button" class="btn-text" :disabled="skillsSaving" @click="saveSkills">
            {{ skillsSaving ? '保存中...' : '完成' }}
          </button>
        </div>
      </div>

      <template v-if="!editingSkills">
        <div v-if="profile.skillKeywords?.length" class="skill-tags">
          <span v-for="s in profile.skillKeywords" :key="s" class="skill-tag">{{ s }}</span>
        </div>
        <p v-else class="empty-tip">暂无技能标签，点击编辑添加</p>
      </template>

      <template v-else>
        <div class="skill-tags edit-mode">
          <span v-for="(s, i) in editSkills" :key="s" class="skill-tag removable">
            {{ s }}
            <button type="button" class="skill-remove" @click="removeSkill(i)">×</button>
          </span>
        </div>
        <div class="skill-add-row">
          <input
            v-model="newSkillInput"
            placeholder="输入技能回车添加"
            maxlength="30"
            @keydown.enter.prevent="addSkill"
          >
          <button type="button" class="btn-add-skill" @click="addSkill">添加</button>
        </div>
      </template>
    </section>

    <!-- 区块四：AI 对我的理解 -->
    <section class="profile-section ai-section">
      <div class="section-head">
        <span class="section-title">AI 对我的理解</span>
      </div>
      <p v-if="profile.preferenceSummary" class="ai-summary-text">
        {{ profile.preferenceSummary }}
      </p>
      <p v-else class="empty-tip">
        AI 还不了解你，去和小职聊聊，它会自动更新对你的理解
      </p>
      <button type="button" class="btn-link" @click="router.push('/chat')">去小职完善画像 →</button>
    </section>

    <!-- A4：小职认识你这些（长期记忆） -->
    <section v-if="ltmFacts.length" class="profile-section ltm-section">
      <div class="section-head">
        <span class="section-title">小职认识你这些</span>
      </div>
      <div class="ltm-groups">
        <div v-for="group in ltmGrouped" :key="group.type" class="ltm-group">
          <span class="ltm-type-chip">{{ ltmTypeLabel(group.type) }}</span>
          <ul class="ltm-fact-list">
            <li v-for="fact in group.facts" :key="fact.id" class="ltm-fact">
              <span class="ltm-fact-text">{{ fact.factText }}</span>
              <button type="button" class="ltm-forget" title="忘掉这条" @click="forgetFact(fact.id)">忘掉</button>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- 区块五：最近产物 -->
    <section v-if="recentArtifacts.length" class="profile-section artifacts-section">
      <div class="section-head">
        <span class="section-title">最近产物</span>
      </div>
      <ul class="artifact-list">
        <li
          v-for="item in recentArtifacts"
          :key="item.artifactId"
          class="artifact-item"
          @click="openArtifact(item)"
        >
          <div class="artifact-head">
            <span class="artifact-type">{{ artifactTypeLabel(item.artifactType) }}</span>
            <span class="artifact-time">{{ formatArtifactTime(item.createdAt) }}</span>
          </div>
          <div class="artifact-title">{{ item.title }}</div>
          <p v-if="item.summary" class="artifact-summary">{{ item.summary }}</p>
          <button
            v-if="item.actionLabel"
            type="button"
            class="artifact-action"
            @click.stop="openArtifact(item)"
          >
            {{ item.actionLabel }} →
          </button>
        </li>
      </ul>
    </section>

    <!-- 区块六：快捷入口 + 活动摘要 -->
    <button type="button" class="entry-btn" @click="router.push('/mine/resume')">
      <span class="entry-icon">📄</span>
      <div class="entry-info">
        <div class="entry-title">我的简历</div>
        <div class="entry-sub">{{ resumeCount }} 份 · 上传、修改、下载 PDF</div>
      </div>
      <span class="entry-arrow">→</span>
    </button>

    <button type="button" class="entry-btn" data-testid="entry-runs" @click="router.push('/mine/runs')">
      <span class="entry-icon">🔁</span>
      <div class="entry-info">
        <div class="entry-title">深度任务</div>
        <div class="entry-sub">可暂停 / 续跑 / 分叉的深度任务运行</div>
      </div>
      <span class="entry-arrow">→</span>
    </button>

    <section class="activity-row">
      <div class="activity-item">
        <div class="activity-num">{{ resumeCount }}</div>
        <div class="activity-label">份简历</div>
      </div>
      <div class="activity-divider" />
      <div class="activity-item">
        <div class="activity-num">{{ totalAnswered }}</div>
        <div class="activity-label">道练习题</div>
      </div>
      <div class="activity-divider" />
      <div class="activity-item">
        <div class="activity-num">{{ avgScore || '—' }}</div>
        <div class="activity-label">面试均分</div>
      </div>
    </section>

    <div style="display:flex;gap:10px;padding:0 16px 24px;">
      <button type="button" class="logout-btn" style="flex:1" @click="handleLogout">退出登录</button>
      <button type="button" class="logout-btn" style="flex:1;background:#f8fafc;color:#4f46e5;border:1px solid #4f46e5;" @click="$router.push('/mine/account-settings')">账号与安全</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listRecentArtifacts } from '../api/artifact'
import { createWorkspace, navigateToWorkspace } from '../api/workspace'
import { getCareerProfile, updateCareerProfile } from '../api/profile'
import { listResumes } from '../api/resume'
import { listVersions } from '../api/resumeVersion'
import { listInterviewSessions } from '../api/interview'
import { listLongTermMemory, forgetLongTermMemory } from '../api/ltm'
import { authStore } from '../stores/authStore'
import { homeStore } from '../stores/homeStore'
import { computeProfileCompleteness } from '../utils/profileCompleteness'

const router = useRouter()

const ROLE_SUGGESTIONS = [
  'Java后端', 'Go后端', 'Python后端', '前端开发', '全栈工程师',
  '算法工程师', '大数据工程师', '测试工程师', '运维工程师',
]
const CITY_SUGGESTIONS = [
  '广州', '深圳', '北京', '上海', '杭州', '成都', '南京', '武汉', '西安', '苏州',
]
const YEARS_OPTIONS = ['应届', '1-3年', '3-5年', '5-10年', '10年以上']

const resumes = ref([])
const versions = ref([])
const sessions = ref([])
const recentArtifacts = ref([])
const artifactLoading = ref('')

// A4：长期记忆
const ltmFacts = ref([])
const LTM_TYPE_LABELS = {
  PREFERENCE: '偏好',
  SKILL: '技能',
  EXPERIENCE: '经历',
  GOAL: '目标',
  CONSTRAINT: '约束',
}
const LTM_TYPE_ORDER = ['PREFERENCE', 'SKILL', 'EXPERIENCE', 'GOAL', 'CONSTRAINT']
const ltmTypeLabel = (t) => LTM_TYPE_LABELS[t] || t
const ltmGrouped = computed(() => LTM_TYPE_ORDER
  .map((type) => ({ type, facts: ltmFacts.value.filter((f) => f.factType === type) }))
  .filter((g) => g.facts.length))

const ARTIFACT_TYPE_LABELS = {
  RESUME_VERSION: '简历版本',
  JOB_MATCH: '匹配报告',
  INTERVIEW_SESSION: '面试训练',
  MARKET_REPORT: '市场报告',
  TASK_PLAN: '任务计划',
}

const profile = ref({
  targetRole: '',
  targetCity: '',
  seniority: '',
  workMode: '',
  skillKeywords: [],
  preferenceSummary: '',
})

const editingCareer = ref(false)
const editCareer = ref({})
const careerSaving = ref(false)
const careerSaveMsg = ref('')

const editingSkills = ref(false)
const editSkills = ref([])
const newSkillInput = ref('')
const skillsSaving = ref(false)

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

watch(displayName, (value) => {
  if (!editingName.value) {
    editDisplayName.value = value === '未登录' ? '' : value
  }
})

const profileCompleteness = computed(() => computeProfileCompleteness(profile.value))

const resumeCount = computed(() => resumes.value.length + versions.value.length)

const totalAnswered = computed(() => (
  sessions.value.reduce((sum, s) => sum + (s.answeredQuestions || 0), 0)
))

const avgScore = computed(() => {
  const scored = sessions.value.filter((s) => s.averageScore)
  if (!scored.length) return 0
  return Math.round(scored.reduce((sum, s) => sum + s.averageScore, 0) / scored.length)
})

function startEditCareer() {
  editCareer.value = {
    targetRole: profile.value.targetRole || '',
    targetCity: profile.value.targetCity || '',
    seniority: profile.value.seniority || '',
    workMode: profile.value.workMode || '',
  }
  editingCareer.value = true
  careerSaveMsg.value = ''
}

function cancelEditCareer() {
  editingCareer.value = false
}

async function saveCareer() {
  careerSaving.value = true
  try {
    const updated = await updateCareerProfile(editCareer.value)
    profile.value = { ...profile.value, ...updated }
    homeStore.updateCareerProfile(profile.value)
    editingCareer.value = false
    careerSaveMsg.value = '已更新，市场行情与面试题将使用新设置'
    setTimeout(() => { careerSaveMsg.value = '' }, 3000)
  } catch (e) {
    careerSaveMsg.value = e?.message || '保存失败'
  } finally {
    careerSaving.value = false
  }
}

function startEditSkills() {
  editSkills.value = [...(profile.value.skillKeywords || [])]
  newSkillInput.value = ''
  editingSkills.value = true
}

function removeSkill(index) {
  editSkills.value.splice(index, 1)
}

function addSkill() {
  const val = newSkillInput.value.trim()
  if (!val || editSkills.value.includes(val)) return
  editSkills.value.push(val)
  newSkillInput.value = ''
}

async function saveSkills() {
  skillsSaving.value = true
  try {
    const updated = await updateCareerProfile({ skillKeywords: editSkills.value })
    profile.value.skillKeywords = updated.skillKeywords || editSkills.value
    homeStore.updateCareerProfile(profile.value)
    editingSkills.value = false
  } catch (e) {
    profileMessage.value = e?.message || '技能保存失败'
    profileError.value = true
  } finally {
    skillsSaving.value = false
  }
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

function artifactTypeLabel(type) {
  return ARTIFACT_TYPE_LABELS[type] || '产物'
}

function formatArtifactTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 60_000) return '刚刚'
  if (diffMs < 3_600_000) return `${Math.floor(diffMs / 60_000)} 分钟前`
  if (diffMs < 86_400_000) return `${Math.floor(diffMs / 3_600_000)} 小时前`
  const m = d.getMonth() + 1
  const day = d.getDate()
  return `${m}月${day}日`
}

async function openArtifact(item) {
  if (item.actionRoute) {
    router.push(item.actionRoute)
    return
  }
  if (artifactLoading.value) return
  artifactLoading.value = item.artifactId
  try {
    const resp = await createWorkspace({
      workspaceType: 'RESUME',
      title: item.title || '继续优化',
      goalText: '基于已有产物继续优化',
      entryAction: 'CONTINUE_WITH_ASSET',
      contextMetadata: {
        artifactId: item.artifactId,
        artifactType: item.artifactType,
        refType: item.refType,
        refId: item.refId,
        title: item.title,
        summary: item.summary,
        actionRoute: item.actionRoute,
        sessionId: item.sessionId,
      },
    })
    await navigateToWorkspace(router, resp)
    return
  } catch (e) {
    console.error('创建 workspace 失败', e)
  } finally {
    artifactLoading.value = ''
  }
  if (item.artifactType === 'RESUME_VERSION' && item.sessionId) {
    router.push(`/chat/${item.sessionId}`)
    return
  }
  router.push('/chat')
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

async function loadLtmFacts() {
  try {
    const data = await listLongTermMemory()
    ltmFacts.value = Array.isArray(data) ? data : (data?.list || [])
  } catch (e) {
    ltmFacts.value = []
  }
}

async function forgetFact(id) {
  try {
    await forgetLongTermMemory(id)
    ltmFacts.value = ltmFacts.value.filter((f) => f.id !== id)
  } catch (e) {
    // 忽略：忘掉失败不阻塞页面
  }
}

onMounted(async () => {
  if (homeStore.state.user) {
    authStore.applyUserProfile(homeStore.state.user)
  }
  loadLtmFacts()
  if (homeStore.state.careerProfile) {
    profile.value = { ...profile.value, ...homeStore.state.careerProfile }
  }
  if (homeStore.state.defaultResume) {
    resumes.value = [homeStore.state.defaultResume]
  }

  const tasks = [
    listVersions(),
    listInterviewSessions(),
    listRecentArtifacts(5),
  ]
  if (!homeStore.state.careerProfile) {
    tasks.unshift(getCareerProfile())
  }
  if (!homeStore.state.defaultResume) {
    tasks.unshift(listResumes())
  }
  const results = await Promise.allSettled(tasks)
  let cursor = 0

  if (!homeStore.state.defaultResume) {
    const resumesResult = results[cursor++]
    if (resumesResult.status === 'fulfilled') {
      resumes.value = Array.isArray(resumesResult.value) ? resumesResult.value : []
      homeStore.updateDefaultResume(resumes.value.find((r) => r.isDefault) || null)
    }
  }
  if (!homeStore.state.careerProfile) {
    const profileResult = results[cursor++]
    if (profileResult.status === 'fulfilled' && profileResult.value) {
      profile.value = { ...profile.value, ...profileResult.value }
      homeStore.updateCareerProfile(profileResult.value)
    }
  }
  const versionsResult = results[cursor++]
  const sessionsResult = results[cursor++]
  const artifactsResult = results[cursor++]

  if (versionsResult.status === 'fulfilled') {
    versions.value = Array.isArray(versionsResult.value) ? versionsResult.value : []
  }
  if (sessionsResult.status === 'fulfilled') {
    sessions.value = Array.isArray(sessionsResult.value) ? sessionsResult.value : []
  }
  if (artifactsResult.status === 'fulfilled') {
    recentArtifacts.value = Array.isArray(artifactsResult.value) ? artifactsResult.value : []
  }
})
</script>

<style scoped>
.mine-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #f8fafc;
  min-height: 100%;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

/* 区块一：身份卡 */
.identity-card {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  border-radius: 16px;
  padding: 18px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.identity-top {
  display: flex;
  gap: 14px;
  align-items: flex-start;
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
  width: 56px;
  height: 56px;
  background: rgba(255, 255, 255, 0.2);
  border: 3px solid rgba(255, 255, 255, 0.4);
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-initial {
  font-weight: 800;
  font-size: 22px;
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
  bottom: 2px;
  right: 2px;
  width: 10px;
  height: 10px;
  background: #10b981;
  border: 2px solid #fff;
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
  font-size: 18px;
  font-weight: 700;
  line-height: 1.3;
}

.name-edit-btn,
.name-save-btn,
.name-cancel-btn {
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 11px;
  cursor: pointer;
  font-family: inherit;
}

.identity-name-edit {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.name-input {
  flex: 1;
  min-width: 120px;
  border: none;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 14px;
  font-family: inherit;
}

.identity-career {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.9;
  line-height: 1.4;
}

.profile-message {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.95;
}

.profile-message--error {
  color: #fecaca;
}

.completeness-block {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  padding: 10px 12px;
}

.completeness-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
}

.completeness-label {
  opacity: 0.9;
}

.completeness-pct {
  font-weight: 700;
}

.completeness-track {
  height: 6px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 3px;
  overflow: hidden;
}

.completeness-fill {
  height: 100%;
  background: #fff;
  border-radius: 3px;
  transition: width 0.3s;
}

.completeness-hint {
  margin: 6px 0 0;
  font-size: 11px;
  opacity: 0.85;
}

/* 通用区块 */
.profile-section {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.btn-text {
  background: none;
  border: none;
  color: #7c3aed;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  padding: 0;
}

.btn-text:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-text-muted {
  background: none;
  border: none;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  padding: 0;
}

.edit-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.profile-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.profile-row:last-of-type {
  border-bottom: none;
}

.profile-label {
  flex-shrink: 0;
  width: 60px;
  font-size: 12px;
  color: #64748b;
}

.profile-value {
  flex: 1;
  font-size: 14px;
  color: #0f172a;
  font-weight: 500;
}

.profile-value.empty {
  color: #94a3b8;
  font-weight: 400;
}

.edit-row {
  margin-bottom: 12px;
}

.edit-row label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.edit-row input,
.edit-row select {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 14px;
  font-family: inherit;
  box-sizing: border-box;
  background: #fff;
}

.save-msg {
  margin: 10px 0 0;
  font-size: 12px;
  color: #059669;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #f3e8ff;
  color: #7c3aed;
  border-radius: 20px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
}

.skill-tag.removable {
  padding-right: 6px;
}

.skill-remove {
  background: transparent;
  border: none;
  color: #a855f7;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  padding: 0 2px;
  font-family: inherit;
}

.skill-add-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.skill-add-row input {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  font-family: inherit;
}

.btn-add-skill {
  border: none;
  background: #7c3aed;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
}

.empty-tip {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.5;
}

.ai-section {
  background: linear-gradient(135deg, #fdf4ff, #ede9fe);
  border-color: #e9d5ff;
}

.ltm-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ltm-group {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.ltm-type-chip {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  border: 1px solid #c7d2fe;
  font-size: 12px;
  font-weight: 600;
  margin-top: 3px;
}
.ltm-fact-list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
}
.ltm-fact {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
}
.ltm-fact-text {
  font-size: 13px;
  color: #374151;
}
.ltm-forget {
  flex-shrink: 0;
  background: none;
  border: none;
  color: #9ca3af;
  font-size: 12px;
  cursor: pointer;
}
.ltm-forget:hover {
  color: #ef4444;
}

.ai-summary-text {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
  font-style: italic;
}

.btn-link {
  background: none;
  border: none;
  color: #7c3aed;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
}

.artifacts-section {
  padding-bottom: 12px;
}

.artifact-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.artifact-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.artifact-item:active {
  background: #faf5ff;
}

.artifact-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.artifact-type {
  font-size: 11px;
  font-weight: 600;
  color: #7c3aed;
  background: #f3e8ff;
  border-radius: 4px;
  padding: 2px 6px;
}

.artifact-time {
  font-size: 11px;
  color: #94a3b8;
  flex-shrink: 0;
}

.artifact-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.4;
}

.artifact-summary {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.artifact-action {
  margin-top: 8px;
  background: none;
  border: none;
  color: #7c3aed;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
}

.entry-btn {
  width: 100%;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.entry-btn:hover {
  background: #faf5ff;
}

.entry-icon {
  font-size: 22px;
  line-height: 1;
}

.entry-info {
  flex: 1;
  min-width: 0;
}

.entry-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.entry-sub {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.entry-arrow {
  color: #7c3aed;
  font-size: 16px;
  flex-shrink: 0;
}

.activity-row {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 16px 8px;
}

.activity-item {
  flex: 1;
  text-align: center;
}

.activity-num {
  font-size: 28px;
  font-weight: 800;
  color: #4f46e5;
  line-height: 1.1;
}

.activity-label {
  margin-top: 4px;
  font-size: 11px;
  color: #64748b;
}

.activity-divider {
  width: 1px;
  height: 36px;
  background: #e2e8f0;
  flex-shrink: 0;
}

.logout-btn {
  width: 100%;
  border: 1px solid #fecaca;
  background: #fff;
  color: #ef4444;
  border-radius: 10px;
  padding: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

@media (min-width: 768px) {
  .mine-page {
    padding: 28px 24px;
    max-width: 760px;
    margin: 0 auto;
  }
}
</style>
