<template>
  <div class="settings-page">
    <div class="settings-header">
      <button class="back-btn" @click="$router.back()">← 返回</button>
      <h2>账号与安全</h2>
    </div>

    <div class="settings-body">

      <!-- ─── 登录方式 ─── -->
      <div class="section-card">
        <div class="section-title">登录方式</div>

        <!-- 手机号 -->
        <div class="setting-row">
          <div class="setting-info">
            <div class="setting-label">手机号</div>
            <div class="setting-desc">{{ maskedPhone || '未绑定' }}</div>
          </div>
          <button class="btn-link" @click="openChangePhone">{{ currentUser?.phone ? '换绑' : '绑定' }}</button>
        </div>

        <!-- 邮箱 -->
        <div class="setting-row">
          <div class="setting-info">
            <div class="setting-label">邮箱</div>
            <div class="setting-desc">
              {{ currentUser?.email || '未绑定' }}
              <span v-if="currentUser?.email && !currentUser?.emailVerified" class="badge-unverified">待验证</span>
            </div>
          </div>
          <button class="btn-link" @click="openBindEmail">{{ currentUser?.email ? '修改' : '绑定' }}</button>
        </div>

        <!-- 账号名 -->
        <div class="setting-row">
          <div class="setting-info">
            <div class="setting-label">账号名</div>
            <div class="setting-desc">{{ currentUser?.username || '未设置' }}</div>
          </div>
          <button class="btn-link" @click="openSetUsername">修改</button>
        </div>

        <!-- 密码 -->
        <div class="setting-row">
          <div class="setting-info">
            <div class="setting-label">登录密码</div>
            <div class="setting-desc">{{ currentUser?.hasPassword ? '已设置' : '未设置（仅手机验证码登录）' }}</div>
          </div>
          <button class="btn-link" @click="openSetPassword">{{ currentUser?.hasPassword ? '修改' : '设置' }}</button>
        </div>
      </div>

      <!-- ─── 会话管理 ─── -->
      <div class="section-card">
        <div class="section-title">已登录设备</div>
        <div v-if="sessionsLoading" class="loading-row">加载中...</div>
        <div v-else-if="sessions.length === 0" class="empty-row">暂无活跃会话</div>
        <div v-else>
          <div class="session-row" v-for="s in sessions" :key="s.sessionId">
            <div class="session-info">
              <div class="session-device">{{ s.deviceName || '未知设备' }}</div>
              <div class="session-meta">{{ s.ipAddress }} · {{ formatDate(s.lastActive) }}<span v-if="s.current" class="badge-current">本设备</span></div>
            </div>
            <button v-if="!s.current" class="btn-danger-link" @click="kickSession(s.sessionId)">退出</button>
          </div>
          <button class="btn-outline-danger" @click="kickAllOthers" style="margin-top:12px;">退出其他全部设备</button>
        </div>
      </div>

      <!-- ─── 账号注销 ─── -->
      <div class="section-card danger-zone">
        <div class="section-title">危险区</div>
        <div class="setting-row" v-if="accountStatus === 'CANCELLING'">
          <div class="setting-info">
            <div class="setting-label" style="color:#ef4444;">注销申请进行中</div>
            <div class="setting-desc">账号将于 7 天后永久删除，删除前可撤销</div>
          </div>
          <button class="btn-link" @click="doRevokeCancellation">撤销注销</button>
        </div>
        <div class="setting-row" v-else>
          <div class="setting-info">
            <div class="setting-label">注销账号</div>
            <div class="setting-desc">注销后数据将在 7 天内清除，操作不可逆</div>
          </div>
          <button class="btn-danger-link" @click="openCancelAccount">申请注销</button>
        </div>
      </div>
    </div>

    <!-- ─── 绑定邮箱 Modal ─── -->
    <div class="modal-mask" v-if="modal === 'email'" @click.self="closeModal">
      <div class="modal">
        <h3>绑定邮箱</h3>
        <p class="modal-tip">邮箱将作为辅助登录方式。验证邮件将在邮件服务上线后发送。</p>
        <label class="field-label">邮箱地址</label>
        <input v-model.trim="emailForm.email" type="email" placeholder="请输入邮箱" class="field-input" />
        <p v-if="modalError" class="modal-error">{{ modalError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" :disabled="submitting" @click="submitBindEmail">{{ submitting ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>

    <!-- ─── 设置账号名 Modal ─── -->
    <div class="modal-mask" v-if="modal === 'username'" @click.self="closeModal">
      <div class="modal">
        <h3>修改账号名</h3>
        <p class="modal-tip">账号名每30天只能修改一次，2-32 位中文、字母、数字或下划线。</p>
        <label class="field-label">新账号名</label>
        <input v-model.trim="usernameForm.username" type="text" placeholder="请输入新账号名" class="field-input" />
        <p v-if="modalError" class="modal-error">{{ modalError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" :disabled="submitting" @click="submitUpdateUsername">{{ submitting ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>

    <!-- ─── 设置密码 Modal ─── -->
    <div class="modal-mask" v-if="modal === 'password'" @click.self="closeModal">
      <div class="modal">
        <h3>设置登录密码</h3>
        <p class="modal-tip">需短信验证身份。修改密码后所有其他设备将下线。</p>
        <label class="field-label">新密码</label>
        <input v-model="pwdForm.newPassword" type="password" placeholder="8-64位，含字母+数字" class="field-input" />
        <template v-if="pwdForm.newPassword">
          <div class="strength-seg-bar">
            <span v-for="i in 4" :key="i" class="strength-seg"
                  :style="{ background: i <= pwdStrength.seg ? pwdStrength.color : '#e5e9f0' }"></span>
          </div>
          <div class="strength-meta">
            <span class="strength-level" :style="{ color: pwdStrength.color }">
              <span class="strength-dot" :style="{ background: pwdStrength.color }"></span>{{ pwdStrength.text }}
            </span>
            <span class="strength-hint">{{ pwdStrength.hint }}</span>
          </div>
        </template>
        <label class="field-label" style="margin-top:12px;">手机验证码</label>
        <div class="sms-row">
          <input v-model.trim="pwdForm.verifyCode" type="text" inputmode="numeric" placeholder="请输入验证码" class="field-input" style="flex:1" />
          <button class="btn-sms" :disabled="smsSending || smsCooldown > 0" @click="sendSettingsSms('password')">
            {{ smsSending ? '...' : smsCooldown > 0 ? `${smsCooldown}秒后重发` : '获取验证码' }}
          </button>
        </div>
        <p v-if="modalError" class="modal-error">{{ modalError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button class="btn-primary" :disabled="submitting" @click="submitSetPassword">{{ submitting ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>

    <!-- ─── 换绑手机号 Modal ─── -->
    <div class="modal-mask" v-if="modal === 'phone'" @click.self="closeModal">
      <div class="modal">
        <h3>换绑手机号</h3>
        <!-- Step 1: 验证旧号 -->
        <template v-if="phoneStep === 1">
          <p class="modal-tip">先验证当前手机号 {{ maskedPhone }}</p>
          <label class="field-label">当前手机验证码</label>
          <div class="sms-row">
            <input v-model.trim="phoneForm.oldVerifyCode" type="text" inputmode="numeric" placeholder="请输入验证码" class="field-input" style="flex:1" />
            <button class="btn-sms" :disabled="smsSending || smsCooldown > 0" @click="sendSettingsSms('phone-old')">
              {{ smsSending ? '...' : smsCooldown > 0 ? `${smsCooldown}秒后重发` : '获取验证码' }}
            </button>
          </div>
          <p v-if="modalError" class="modal-error">{{ modalError }}</p>
          <div class="modal-actions">
            <button class="btn-secondary" @click="closeModal">取消</button>
            <button class="btn-primary" :disabled="submitting" @click="submitVerifyOldPhone">下一步</button>
          </div>
        </template>
        <!-- Step 2: 绑定新号 -->
        <template v-else>
          <p class="modal-tip">请输入新手机号并验证</p>
          <label class="field-label">新手机号</label>
          <input v-model.trim="phoneForm.newPhone" type="tel" inputmode="numeric" maxlength="11" placeholder="请输入新手机号" class="field-input" />
          <label class="field-label" style="margin-top:12px;">新手机号验证码</label>
          <div class="sms-row">
            <input v-model.trim="phoneForm.newVerifyCode" type="text" inputmode="numeric" placeholder="请输入验证码" class="field-input" style="flex:1" />
            <button class="btn-sms" :disabled="smsSending || smsCooldown > 0" @click="sendSettingsSms('phone-new')">
              {{ smsSending ? '...' : smsCooldown > 0 ? `${smsCooldown}秒后重发` : '获取验证码' }}
            </button>
          </div>
          <p v-if="modalError" class="modal-error">{{ modalError }}</p>
          <div class="modal-actions">
            <button class="btn-secondary" @click="closeModal">取消</button>
            <button class="btn-primary" :disabled="submitting" @click="submitBindNewPhone">确认换绑</button>
          </div>
        </template>
      </div>
    </div>

    <!-- ─── 注销账号 Modal ─── -->
    <div class="modal-mask" v-if="modal === 'cancel'" @click.self="closeModal">
      <div class="modal">
        <h3 style="color:#ef4444;">注销账号</h3>
        <div class="warning-box">
          <p>⚠️ 注销后您的账号将在 <strong>7天</strong> 内永久删除，包括：</p>
          <ul><li>所有简历和求职记录</li><li>面试练习记录</li><li>AI 对话历史</li></ul>
          <p>7天内可随时撤销注销。</p>
        </div>
        <label class="field-label">输入「注销」确认</label>
        <input v-model="cancelForm.confirmText" type="text" placeholder="请输入「注销」" class="field-input" />
        <label class="field-label" style="margin-top:12px;">手机验证码</label>
        <div class="sms-row">
          <input v-model.trim="cancelForm.verifyCode" type="text" inputmode="numeric" placeholder="请输入验证码" class="field-input" style="flex:1" />
          <button class="btn-sms" :disabled="smsSending || smsCooldown > 0" @click="sendSettingsSms('cancel')">
            {{ smsSending ? '...' : smsCooldown > 0 ? `${smsCooldown}秒后重发` : '获取验证码' }}
          </button>
        </div>
        <p v-if="modalError" class="modal-error">{{ modalError }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModal">我再想想</button>
          <button class="btn-danger" :disabled="submitting" @click="submitCancelAccount">{{ submitting ? '处理中...' : '确认注销' }}</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'
import {
  bindEmail, updateUsername, setPassword,
  verifyOldPhone, bindNewPhone,
  cancelAccount, revokeCancellation,
  listSessions, revokeSession, revokeAllOtherSessions,
} from '../api/auth'
import { sendSmsCode, sendPasswordResetSms } from '../api/auth'

const router = useRouter()

const currentUser = computed(() => authStore.state.currentUser)
const accountStatus = computed(() => currentUser.value?.status || 'ACTIVE')
const maskedPhone = computed(() => {
  const p = currentUser.value?.phone || ''
  return p ? p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : ''
})

// Sessions
const sessions = ref([])
const sessionsLoading = ref(false)

// Modal state
const modal = ref('')          // '' | 'email' | 'username' | 'password' | 'phone' | 'cancel'
const modalError = ref('')
const submitting = ref(false)
const phoneStep = ref(1)
const smsSending = ref(false)
const smsCooldown = ref(0)
let smsCooldownTimer = null

// Form data
const emailForm = reactive({ email: '' })
const usernameForm = reactive({ username: '', verifyCode: '', challengeId: '' })
const pwdForm = reactive({ newPassword: '', verifyCode: '', challengeId: '' })
const phoneForm = reactive({ oldVerifyCode: '', oldChallengeId: '', oldPhoneTicket: '', newPhone: '', newVerifyCode: '', newChallengeId: '' })
const cancelForm = reactive({ confirmText: '', verifyCode: '', challengeId: '' })

onMounted(async () => {
  // 刷新最新用户信息（phone/email 等字段登录时不在 token 里）
  try { await authStore.fetchCurrentUser() } catch (e) { /* ignore */ }
  sessionsLoading.value = true
  try {
    sessions.value = await listSessions()
  } catch (e) {
    // ignore
  } finally {
    sessionsLoading.value = false
  }
})

function closeModal() { modal.value = ''; modalError.value = ''; submitting.value = false }
function openBindEmail() { emailForm.email = currentUser.value?.email || ''; modal.value = 'email' }
function openSetUsername() { usernameForm.username = currentUser.value?.username || ''; usernameForm.verifyCode = ''; modal.value = 'username' }
function openSetPassword() { pwdForm.newPassword = ''; pwdForm.verifyCode = ''; modal.value = 'password' }
function openChangePhone() { phoneStep.value = 1; Object.assign(phoneForm, { oldVerifyCode: '', oldChallengeId: '', oldPhoneTicket: '', newPhone: '', newVerifyCode: '', newChallengeId: '' }); modal.value = 'phone' }
function openCancelAccount() { cancelForm.confirmText = ''; cancelForm.verifyCode = ''; cancelForm.challengeId = ''; modal.value = 'cancel' }

async function sendSettingsSms(target) {
  const phone = target === 'phone-new' ? phoneForm.newPhone : currentUser.value?.phone
  if (!phone) { modalError.value = '未找到手机号'; return }
  smsSending.value = true
  try {
    // 设置密码走网关 reset 流程，验证码必须以 RESET scene 下发（否则网关 resetVerify 校验不到）；
    // 其余场景（账号名/换绑/注销）仍用登录 scene 验证码。
    const data = target === 'password'
      ? await sendPasswordResetSms(phone)
      : await sendSmsCode(phone)
    if (!data?.challengeId) throw new Error('发送失败')
    if (target === 'username') usernameForm.challengeId = data.challengeId
    else if (target === 'password') pwdForm.challengeId = data.challengeId
    else if (target === 'phone-old') phoneForm.oldChallengeId = data.challengeId
    else if (target === 'phone-new') phoneForm.newChallengeId = data.challengeId
    else if (target === 'cancel') cancelForm.challengeId = data.challengeId
    startSmsCooldown(data.cooldownSeconds || 60)
  } catch (e) {
    modalError.value = e?.message || '发送失败，请稍后再试'
  } finally {
    smsSending.value = false
  }
}

function startSmsCooldown(sec) {
  smsCooldown.value = sec
  if (smsCooldownTimer) clearInterval(smsCooldownTimer)
  smsCooldownTimer = setInterval(() => {
    smsCooldown.value -= 1
    if (smsCooldown.value <= 0) { clearInterval(smsCooldownTimer); smsCooldownTimer = null }
  }, 1000)
}

async function submitBindEmail() {
  modalError.value = ''
  submitting.value = true
  try {
    await bindEmail(emailForm.email)
    await authStore.fetchCurrentUser()
    closeModal()
  } catch (e) { modalError.value = e?.message || '保存失败' } finally { submitting.value = false }
}

async function submitUpdateUsername() {
  modalError.value = ''
  if (!usernameForm.username) { modalError.value = '请输入新账号名'; return }
  submitting.value = true
  try {
    // 改账号名不再需要手机验证码
    await updateUsername({ username: usernameForm.username })
    await authStore.fetchCurrentUser()
    closeModal()
  } catch (e) { modalError.value = e?.message || '修改失败' } finally { submitting.value = false }
}

async function submitSetPassword() {
  modalError.value = ''
  if (!pwdForm.challengeId) { modalError.value = '请先获取验证码'; return }
  submitting.value = true
  try {
    await setPassword({ newPassword: pwdForm.newPassword, verifyCode: pwdForm.verifyCode, challengeId: pwdForm.challengeId })
    await authStore.fetchCurrentUser()
    closeModal()
    // 密码改后其他会话已失效，刷新会话列表
    sessions.value = await listSessions().catch(() => [])
  } catch (e) { modalError.value = e?.message || '设置失败' } finally { submitting.value = false }
}

async function submitVerifyOldPhone() {
  modalError.value = ''
  if (!phoneForm.oldChallengeId) { modalError.value = '请先获取验证码'; return }
  submitting.value = true
  try {
    const ticket = await verifyOldPhone({ verifyCode: phoneForm.oldVerifyCode, challengeId: phoneForm.oldChallengeId })
    phoneForm.oldPhoneTicket = ticket
    phoneStep.value = 2
    smsCooldown.value = 0
  } catch (e) { modalError.value = e?.message || '验证失败，请重试' } finally { submitting.value = false }
}

async function submitBindNewPhone() {
  modalError.value = ''
  if (!phoneForm.newChallengeId) { modalError.value = '请先获取新手机号验证码'; return }
  submitting.value = true
  try {
    await bindNewPhone({ newPhone: phoneForm.newPhone, verifyCode: phoneForm.newVerifyCode, challengeId: phoneForm.newChallengeId, oldPhoneTicket: phoneForm.oldPhoneTicket })
    await authStore.fetchCurrentUser()
    closeModal()
  } catch (e) { modalError.value = e?.message || '换绑失败，请重试' } finally { submitting.value = false }
}

async function submitCancelAccount() {
  modalError.value = ''
  if (!cancelForm.challengeId) { modalError.value = '请先获取验证码'; return }
  submitting.value = true
  try {
    await cancelAccount({ verifyCode: cancelForm.verifyCode, challengeId: cancelForm.challengeId, confirmText: cancelForm.confirmText })
    // 应用级注销：立即登出跳登录页；30 天内重新登录会落到"注销中"中间页可恢复。
    try { sessionStorage.setItem('cm_cancel_pending', '1') } catch (e) { /* ignore */ }
    await authStore.logout()
  } catch (e) { modalError.value = e?.message || '操作失败'; submitting.value = false }
}

async function doRevokeCancellation() {
  try {
    await revokeCancellation()
    await authStore.fetchCurrentUser()
  } catch (e) {
    alert(e?.message || '撤销失败')
  }
}

async function kickSession(sessionId) {
  try {
    await revokeSession(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
  } catch (e) { alert(e?.message || '操作失败') }
}

async function kickAllOthers() {
  try {
    await revokeAllOtherSessions()
    sessions.value = sessions.value.filter(s => s.current)
  } catch (e) { alert(e?.message || '操作失败') }
}

function formatDate(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

// 密码强度
// 密码强度：6 维打分（长度≥8/≥12、小写、大写、数字、符号）→ 5 档（太短/弱/中/良/强）
const pwdStrength = computed(() => {
  const pwd = pwdForm.newPassword || ''
  const meetsMin = pwd.length >= 8 && /[a-zA-Z]/.test(pwd) && /\d/.test(pwd)
  if (!meetsMin) {
    const hint = pwd.length < 8 ? '密码太短，至少 8 位'
      : !/[a-zA-Z]/.test(pwd) ? '需包含字母'
      : '需包含数字'
    return { seg: 0, color: '#94a3b8', text: '太短', hint }
  }
  let score = 0
  if (pwd.length >= 8) score++
  if (pwd.length >= 12) score++
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  if (score >= 5) return { seg: 4, color: '#10b981', text: '强', hint: '很安全 👍' }
  if (score >= 4) return { seg: 3, color: '#3b82f6', text: '良', hint: '较安全，再长一点更佳' }
  if (score >= 3) return { seg: 2, color: '#f59e0b', text: '中', hint: '还不错，加符号会更安全' }
  return { seg: 1, color: '#ef4444', text: '弱', hint: '建议加长或加入更多字符类型' }
})
</script>

<style scoped>
.settings-page { min-height: 100vh; background: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif; }
.settings-header { padding: 16px 20px; background: #fff; border-bottom: 1px solid #e2e8f0; display: flex; align-items: center; gap: 12px; }
.settings-header h2 { margin: 0; font-size: 16px; font-weight: 700; color: #0f172a; }
.back-btn { border: 0; background: none; color: #4f46e5; font-size: 14px; cursor: pointer; padding: 0; font-family: inherit; }
.settings-body { padding: 16px 20px; max-width: 600px; margin: 0 auto; display: grid; gap: 16px; }

.section-card { background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.section-title { padding: 14px 16px 10px; font-size: 12px; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 1px solid #f1f5f9; }
.setting-row { display: flex; align-items: center; padding: 14px 16px; border-bottom: 1px solid #f1f5f9; gap: 12px; }
.setting-row:last-child { border-bottom: 0; }
.setting-info { flex: 1; min-width: 0; }
.setting-label { font-size: 14px; font-weight: 500; color: #0f172a; margin-bottom: 2px; }
.setting-desc { font-size: 12px; color: #64748b; display: flex; align-items: center; gap: 6px; }
.badge-unverified { background: #fef3c7; color: #d97706; border-radius: 4px; padding: 1px 5px; font-size: 11px; }
.badge-current { background: #ede9fe; color: #4f46e5; border-radius: 4px; padding: 1px 5px; font-size: 11px; margin-left: 6px; }
.btn-link { border: 0; background: none; color: #4f46e5; font-size: 13px; cursor: pointer; font-family: inherit; padding: 0; white-space: nowrap; }
.btn-danger-link { border: 0; background: none; color: #ef4444; font-size: 13px; cursor: pointer; font-family: inherit; padding: 0; white-space: nowrap; }

.session-row { display: flex; align-items: center; padding: 12px 16px; border-bottom: 1px solid #f1f5f9; gap: 12px; }
.session-row:last-of-type { border-bottom: 0; }
.session-info { flex: 1; }
.session-device { font-size: 13px; font-weight: 500; color: #0f172a; }
.session-meta { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.btn-outline-danger { border: 1px solid #ef4444; background: none; color: #ef4444; border-radius: 8px; padding: 8px 14px; font-size: 13px; cursor: pointer; font-family: inherit; width: 100%; }
.loading-row, .empty-row { padding: 16px; font-size: 13px; color: #94a3b8; text-align: center; }

.danger-zone .section-title { color: #ef4444; }

/* Modal */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; padding: 16px; }
.modal { background: #fff; border-radius: 16px; padding: 24px; width: min(440px, 100%); }
.modal h3 { margin: 0 0 8px; font-size: 16px; font-weight: 700; color: #0f172a; }
.modal-tip { margin: 0 0 16px; font-size: 13px; color: #64748b; line-height: 1.6; }
.field-label { display: block; font-size: 12px; font-weight: 500; color: #334155; margin-bottom: 6px; }
.field-input { width: 100%; border: 1px solid #e2e8f0; border-radius: 8px; padding: 9px 12px; font-size: 13px; font-family: inherit; box-sizing: border-box; }
.field-input:focus { outline: none; border-color: #4f46e5; }
.modal-error { margin: 8px 0 0; font-size: 12px; color: #ef4444; }
.modal-actions { display: flex; gap: 10px; margin-top: 20px; }
.modal-actions .btn-primary, .modal-actions .btn-secondary, .modal-actions .btn-danger { flex: 1; border: 0; border-radius: 8px; padding: 10px; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }
.btn-primary { background: #4f46e5; color: #fff; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.btn-secondary { background: #f8fafc; color: #334155; border: 1px solid #e2e8f0 !important; }
.btn-danger { background: #ef4444; color: #fff; }
.btn-danger:disabled { opacity: 0.6; cursor: default; }

.sms-row { display: flex; gap: 8px; }
.btn-sms { flex-shrink: 0; border: 1px solid #4f46e5; background: #fff; color: #4f46e5; border-radius: 8px; padding: 9px 10px; font-size: 12px; font-weight: 600; cursor: pointer; font-family: inherit; white-space: nowrap; }
.btn-sms:disabled { opacity: 0.6; cursor: default; }

.warning-box { background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px; font-size: 13px; color: #991b1b; }
.warning-box p { margin: 0 0 6px; }
.warning-box ul { margin: 6px 0; padding-left: 18px; }
.warning-box li { margin-bottom: 3px; }

.strength-seg-bar { display: flex; gap: 6px; margin-top: 8px; }
.strength-seg { flex: 1; height: 6px; border-radius: 4px; background: #e5e9f0; transition: background 0.25s ease; }
.strength-meta { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.strength-level { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 700; }
.strength-dot { width: 8px; height: 8px; border-radius: 50%; }
.strength-hint { font-size: 12px; color: #94a3b8; }
</style>
