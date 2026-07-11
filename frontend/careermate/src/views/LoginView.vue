<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- 左侧品牌区 -->
      <section class="login-hero">
        <div class="hero-brand">
          <div class="hero-logo">C</div>
          <div class="hero-name">CareerMate</div>
        </div>
        <h1 class="hero-title">看 JD · 改简历 · 练面试<br>一个对话搞定</h1>
        <p class="hero-desc">AI 把你的求职流程接管 80%，你只做 AI 做不了的事：开口答题、点投递、做决策</p>
        <div class="hero-quote">
          <div class="hero-quote-label">本周用户故事</div>
          "广州 Java 3 年，14 天投 28 家，拿 3 个 Offer 最高 32K。AI 帮我做了 23 次面试模拟。" — @小李
        </div>
      </section>

      <!-- 右侧登录卡 -->
      <section class="login-card">
        <!-- Onboarding 遮层 -->
        <div v-if="showOnboarding" class="onboarding-overlay">
          <div class="onboarding-card">
            <div class="onboarding-step" v-if="onboardingStep === 0">
              <div class="onboarding-icon">🎉</div>
              <h3>欢迎加入 CareerMate！</h3>
              <p>AI 将陪你走过每一步求职旅程——改简历、投 JD、练面试，一个对话全搞定。</p>
              <button class="btn-primary" @click="onboardingStep = 1">开始 →</button>
            </div>
            <div class="onboarding-step" v-else-if="onboardingStep === 1">
              <div class="onboarding-icon">📄</div>
              <h3>上传你的简历（可跳过）</h3>
              <p>让 AI 了解你的背景，获得更精准的建议。</p>
              <div class="onboarding-actions">
                <button class="btn-secondary" @click="onboardingStep = 2">稍后再说</button>
                <button class="btn-primary" @click="onboardingStep = 2">去上传</button>
              </div>
            </div>
            <div class="onboarding-step" v-else-if="onboardingStep === 2">
              <div class="onboarding-icon">🎯</div>
              <h3>设置求职目标（可跳过）</h3>
              <p>告诉 AI 你想去哪个方向，匹配更准确的 JD 和建议。</p>
              <div class="onboarding-actions">
                <button class="btn-secondary" @click="finishOnboarding">跳过</button>
                <button class="btn-primary" @click="finishOnboarding">设置目标</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 忘记密码面板 -->
        <template v-if="view === 'forgot'">
          <h2 class="card-title">找回密码</h2>
          <form class="form" @submit.prevent="submitPasswordReset">
            <label>
              <span class="field-label">手机号</span>
              <input v-model.trim="resetForm.phone" type="tel" inputmode="numeric" maxlength="11" placeholder="请输入注册手机号" required />
            </label>
            <label>
              <span class="field-label">验证码</span>
              <div class="sms-row">
                <input v-model.trim="resetForm.verifyCode" type="text" inputmode="numeric" maxlength="8" placeholder="请输入验证码" required />
                <button type="button" class="btn-sms" :disabled="resetSmsSending || resetCooldown > 0" @click="sendResetCode">
                  {{ resetSmsSending ? '发送中...' : resetCooldown > 0 ? `${resetCooldown}秒后重发` : '获取验证码' }}
                </button>
              </div>
            </label>
            <label>
              <span class="field-label">新密码</span>
              <input v-model="resetForm.newPassword" type="password" placeholder="8-64位，含字母+数字" required />
              <div v-if="resetForm.newPassword" class="strength-bar">
                <div class="strength-fill" :style="strengthStyle(resetForm.newPassword)"></div>
              </div>
              <span v-if="resetForm.newPassword" class="strength-label" :style="{ color: strengthColor(resetForm.newPassword) }">
                {{ strengthText(resetForm.newPassword) }}
              </span>
            </label>
            <label>
              <span class="field-label">确认新密码</span>
              <input v-model="resetForm.confirmPassword" type="password" placeholder="再次输入新密码" required />
            </label>
            <button class="btn-primary" type="submit" :disabled="authStore.state.loading">
              {{ authStore.state.loading ? '处理中...' : '确认重置' }}
            </button>
          </form>
          <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
          <p v-if="successMsg" class="success">{{ successMsg }}</p>
          <div class="method-switch"><button class="method-switch-btn" @click="view = 'login'">← 返回登录</button></div>
        </template>

        <!-- 登录面板 -->
        <template v-else>
          <!-- Tab 切换 -->
          <div class="tab-bar">
            <button :class="['tab-btn', activeTab === 'sms' && 'active']" type="button" @click="activeTab = 'sms'">验证码登录</button>
            <button :class="['tab-btn', activeTab === 'password' && 'active']" type="button" @click="activeTab = 'password'">密码登录</button>
          </div>

          <!-- 手机号验证码 Tab -->
          <form v-if="activeTab === 'sms'" class="form" @submit.prevent="submitSmsLogin">
            <label>
              <span class="field-label">手机号</span>
              <input v-model.trim="smsForm.phone" type="tel" inputmode="numeric" maxlength="11" placeholder="请输入手机号" required />
            </label>
            <label>
              <span class="field-label">验证码</span>
              <div class="sms-row">
                <input v-model.trim="smsForm.verifyCode" type="text" inputmode="numeric" maxlength="8" placeholder="请输入验证码" required />
                <button type="button" class="btn-sms" :disabled="smsSending || cooldown > 0" @click="sendCode">
                  {{ smsSending ? '发送中...' : cooldown > 0 ? `${cooldown}秒后重发` : '获取验证码' }}
                </button>
              </div>
            </label>
            <div class="row-between">
              <label class="checkbox-label">
                <input type="checkbox" v-model="smsForm.rememberMe" />
                <span>30天内免登录</span>
              </label>
            </div>
            <label class="checkbox-label" :class="{ 'checkbox-error': termsError }">
              <input type="checkbox" v-model="termsAgreed" />
              <span>我已阅读并同意<a href="#" @click.prevent="showTerms = true">《用户协议》</a>和<a href="#" @click.prevent="showPrivacy = true">《隐私政策》</a></span>
            </label>
            <p v-if="termsError" class="error" style="margin-top:0">请先勾选同意协议才能继续</p>
            <button class="btn-primary" type="submit" :disabled="authStore.state.loading">
              {{ authStore.state.loading ? '处理中...' : '登录 / 注册' }}
            </button>
          </form>

          <!-- 密码登录 Tab -->
          <form v-else class="form" @submit.prevent="submitPasswordLogin">
            <label>
              <span class="field-label">手机号 / 邮箱 / 账号名</span>
              <input v-model.trim="pwdForm.account" type="text" placeholder="请输入手机号、邮箱或账号名" autocomplete="username" required />
            </label>
            <label>
              <span class="field-label">密码</span>
              <input v-model="pwdForm.password" type="password" placeholder="请输入密码" autocomplete="current-password" required />
            </label>
            <!-- 图形验证码：仅当后端要求且已下发验证码图片时才显示（避免残留状态显示空框挡登录）-->
            <template v-if="captchaRequired && captchaImage">
              <label>
                <span class="field-label">图形验证码</span>
                <div class="sms-row">
                  <input v-model.trim="pwdForm.captcha" type="text" placeholder="请输入图形验证码" maxlength="6" />
                  <img :src="captchaImage" class="captcha-img" @click="refreshCaptcha" title="点击刷新" />
                </div>
              </label>
            </template>
            <div class="row-between">
              <label class="checkbox-label">
                <input type="checkbox" v-model="pwdForm.rememberMe" />
                <span>30天内免登录</span>
              </label>
              <button type="button" class="forgot-password-btn" @click="view = 'forgot'">忘记密码？</button>
            </div>
            <label class="checkbox-label" :class="{ 'checkbox-error': termsError }">
              <input type="checkbox" v-model="termsAgreed" />
              <span>我已阅读并同意<a href="#" @click.prevent="showTerms = true">《用户协议》</a>和<a href="#" @click.prevent="showPrivacy = true">《隐私政策》</a></span>
            </label>
            <p v-if="termsError" class="error" style="margin-top:0">请先勾选同意协议才能继续</p>
            <button class="btn-primary" type="submit" :disabled="authStore.state.loading">
              {{ authStore.state.loading ? '处理中...' : '登录' }}
            </button>
          </form>

          <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
          <p v-if="successMsg" class="success">{{ successMsg }}</p>
        </template>
      </section>
    </div>

    <!-- 用户协议弹窗 -->
    <div v-if="showTerms" class="legal-mask" @click.self="showTerms = false">
      <div class="legal-modal">
        <div class="legal-header">
          <h3>用户协议</h3>
          <button class="legal-close" @click="showTerms = false">✕</button>
        </div>
        <div class="legal-body">
          <p class="legal-updated">生效日期：2026 年 7 月 10 日</p>

          <h4>一、服务说明</h4>
          <p>CareerMate（以下简称"本平台"）是由广州灵犀科技有限公司运营的 AI 求职辅助平台。本平台提供简历优化、JD 分析、面试模拟等服务。使用本平台即表示您同意本协议的全部条款。</p>

          <h4>二、账号注册与安全</h4>
          <p>1. 您须提供真实、准确的手机号完成注册，并对账号安全负责。<br>
          2. 严禁利用本平台从事违法活动，包括但不限于散布虚假信息、侵犯他人权益。<br>
          3. 账号不得转让、出借或共享给第三方使用。</p>

          <h4>三、服务内容与限制</h4>
          <p>1. 本平台 AI 生成内容仅供参考，不构成法律、医疗、财务等专业建议。<br>
          2. 每日使用次数可能根据账号等级有所限制，具体以平台公告为准。<br>
          3. 本平台保留对违规账号进行封禁的权利。</p>

          <h4>四、知识产权</h4>
          <p>平台所有内容（含算法、界面、文案）归本平台所有。用户上传的简历等个人材料归用户本人所有，平台仅用于提供服务。</p>

          <h4>五、免责声明</h4>
          <p>因网络故障、不可抗力等原因导致的服务中断，本平台不承担责任。AI 生成结果的准确性由用户自行判断。</p>

          <h4>六、协议变更</h4>
          <p>本平台有权修改本协议，修改后将在平台内公告，继续使用即视为同意。如有疑问请联系：support@careermate.cn</p>
        </div>
        <div class="legal-footer">
          <button class="btn-primary" style="width:auto;padding:8px 32px;" @click="showTerms = false">我已阅读</button>
        </div>
      </div>
    </div>

    <!-- 隐私政策弹窗 -->
    <div v-if="showPrivacy" class="legal-mask" @click.self="showPrivacy = false">
      <div class="legal-modal">
        <div class="legal-header">
          <h3>隐私政策</h3>
          <button class="legal-close" @click="showPrivacy = false">✕</button>
        </div>
        <div class="legal-body">
          <p class="legal-updated">生效日期：2026 年 7 月 10 日</p>

          <h4>一、信息收集</h4>
          <p>我们收集以下信息以提供服务：<br>
          · <strong>账号信息</strong>：手机号、邮箱（可选）、账号名<br>
          · <strong>使用数据</strong>：您上传的简历、输入的 JD 及对话内容<br>
          · <strong>设备信息</strong>：IP 地址、浏览器类型、操作系统（用于安全防护）</p>

          <h4>二、信息使用</h4>
          <p>您的信息仅用于：提供 AI 求职辅助服务、保障账号安全、改善产品体验。我们<strong>不会</strong>将您的简历或对话内容出售给第三方。</p>

          <h4>三、信息存储与保护</h4>
          <p>数据存储于中国境内的服务器，采用 TLS 加密传输、AES-256 静态加密。您的密码经不可逆哈希处理，我们无法查看明文密码。</p>

          <h4>四、信息共享</h4>
          <p>以下情况可能涉及信息共享：<br>
          · 经您明确授权的第三方服务（如简历投递平台）<br>
          · 法律法规要求或司法机关依法调取<br>
          · 为保护用户或公众安全的紧急情况</p>

          <h4>五、您的权利（PIPL 合规）</h4>
          <p>根据《个人信息保护法》，您有权：查阅、复制、更正、删除您的个人信息，以及撤回授权同意。请通过「账号设置 → 注销账号」或发送邮件至 privacy@careermate.cn 行使权利。</p>

          <h4>六、Cookie 与追踪</h4>
          <p>本平台使用必要 Cookie 维持登录状态，不使用跨站追踪 Cookie。</p>

          <h4>七、联系我们</h4>
          <p>隐私相关问题：privacy@careermate.cn<br>
          地址：广州市天河区xxx科技园</p>
        </div>
        <div class="legal-footer">
          <button class="btn-primary" style="width:auto;padding:8px 32px;" @click="showPrivacy = false">我已阅读</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'
import { agreeTerms, getCaptcha } from '../api/auth'

const PHONE_PATTERN = /^1[3-9]\d{9}$/
const VERIFY_CODE_PATTERN = /^\d{4,8}$/

const router = useRouter()
const view = ref('login')          // 'login' | 'forgot'
const activeTab = ref('sms')       // 'sms' | 'password'
const errorMsg = ref('')
const successMsg = ref('')
const termsAgreed = ref(false)
const termsError = ref(false)
const showTerms = ref(false)
const showPrivacy = ref(false)

// Onboarding
const showOnboarding = ref(false)
const onboardingStep = ref(0)

// SMS 登录表单
const smsForm = reactive({ phone: '', verifyCode: '', challengeId: '', rememberMe: false })
const smsSending = ref(false)
const cooldown = ref(0)
let cooldownTimer = null

// 密码登录表单
const pwdForm = reactive({ account: '', password: '', rememberMe: false, captcha: '' })
const captchaRequired = ref(false)
const captchaImage = ref('')
const captchaChallengeId = ref('')

// 忘记密码表单
const resetForm = reactive({ phone: '', verifyCode: '', challengeId: '', newPassword: '', confirmPassword: '' })
const resetSmsSending = ref(false)
const resetCooldown = ref(0)
let resetCooldownTimer = null

function clearMessages() {
  errorMsg.value = ''
  successMsg.value = ''
  termsError.value = false
}

function startCooldown(seconds) {
  const duration = Math.max(0, Number(seconds) || 60)
  cooldown.value = duration
  if (cooldownTimer) clearInterval(cooldownTimer)
  if (duration <= 0) return
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) { clearInterval(cooldownTimer); cooldownTimer = null }
  }, 1000)
}

function startResetCooldown(seconds) {
  const duration = Math.max(0, Number(seconds) || 60)
  resetCooldown.value = duration
  if (resetCooldownTimer) clearInterval(resetCooldownTimer)
  if (duration <= 0) return
  resetCooldownTimer = setInterval(() => {
    resetCooldown.value -= 1
    if (resetCooldown.value <= 0) { clearInterval(resetCooldownTimer); resetCooldownTimer = null }
  }, 1000)
}

watch(() => smsForm.phone, () => { smsForm.verifyCode = ''; smsForm.challengeId = '' })
watch(() => resetForm.phone, () => { resetForm.verifyCode = ''; resetForm.challengeId = '' })

async function sendCode() {
  clearMessages()
  if (!checkTerms()) return
  if (!PHONE_PATTERN.test(smsForm.phone)) {
    errorMsg.value = '请输入正确的手机号'
    return
  }
  smsSending.value = true
  try {
    const data = await authStore.sendMobileSmsCode(smsForm.phone)
    if (!data?.challengeId) throw new Error('验证码发送失败，请重试')
    smsForm.challengeId = data.challengeId
    successMsg.value = '验证码已发送，60秒内有效'
    startCooldown(data.cooldownSeconds || 60)
  } catch (e) {
    errorMsg.value = e?.message || '验证码发送失败，请稍后再试'
  } finally {
    smsSending.value = false
  }
}

function checkTerms() {
  if (!termsAgreed.value) {
    termsError.value = true
    errorMsg.value = ''
    return false
  }
  return true
}

async function submitSmsLogin() {
  clearMessages()
  if (!checkTerms()) return
  if (!PHONE_PATTERN.test(smsForm.phone)) { errorMsg.value = '请输入正确的手机号'; return }
  if (!VERIFY_CODE_PATTERN.test(smsForm.verifyCode)) { errorMsg.value = '请输入4-8位验证码'; return }
  if (!smsForm.challengeId) { errorMsg.value = '请先获取验证码'; return }
  try {
    const result = await authStore.mobileLogin(smsForm.phone, smsForm.verifyCode, smsForm.challengeId, { rememberMe: smsForm.rememberMe })
    await agreeTerms('v1').catch(() => {})
    if (result?.isNewUser || result?.onboardingCompleted === false) {
      showOnboarding.value = true
      onboardingStep.value = 0
    } else {
      await router.replace('/')
    }
  } catch (e) {
    errorMsg.value = e?.message || '登录/注册失败，请重试'
  }
}

async function submitPasswordLogin() {
  clearMessages()
  if (!checkTerms()) return
  if (!pwdForm.account) { errorMsg.value = '请输入账号'; return }
  if (!pwdForm.password) { errorMsg.value = '请输入密码'; return }
  // 仅当确有验证码图片时才强制填写（避免残留状态误挡）
  if (captchaRequired.value && captchaImage.value && !pwdForm.captcha) { errorMsg.value = '请输入图形验证码'; return }
  try {
    const result = await authStore.login(pwdForm.account, pwdForm.password, {
      rememberMe: pwdForm.rememberMe,
      captcha: pwdForm.captcha,
      captchaChallengeId: captchaChallengeId.value,
    })
    await agreeTerms('v1').catch(() => {})
    resetCaptcha()
    if (result?.isNewUser || result?.onboardingCompleted === false) {
      showOnboarding.value = true
      onboardingStep.value = 0
    } else {
      await router.replace('/')
    }
  } catch (e) {
    const msg = e?.message || ''
    // 后端(透传 auth-gateway)在需要图形验证码时，将 {captchaRequired, captchaImage, challengeId} 放在响应 data 里
    const d = e?.payload?.data
    if (d?.captchaRequired && d?.captchaImage) {
      captchaRequired.value = true
      captchaImage.value = d.captchaImage
      captchaChallengeId.value = d.challengeId || ''
      pwdForm.captcha = ''
    } else {
      // 本次响应不需要图形验证码：清空残留状态，避免空验证码框挡住后续登录
      resetCaptcha()
    }
    errorMsg.value = msg || '账号或密码错误，请重试'
  }
}

function resetCaptcha() {
  captchaRequired.value = false
  captchaImage.value = ''
  captchaChallengeId.value = ''
  pwdForm.captcha = ''
}

// 切换登录方式 Tab 时清掉图形验证码残留状态
watch(activeTab, () => { resetCaptcha() })

async function refreshCaptcha() {
  try {
    const data = await getCaptcha()
    captchaImage.value = data?.captchaImage || ''
    captchaChallengeId.value = data?.challengeId || ''
    pwdForm.captcha = ''
  } catch (e) {
    // ignore
  }
}

async function sendResetCode() {
  clearMessages()
  if (!PHONE_PATTERN.test(resetForm.phone)) { errorMsg.value = '请输入正确的手机号'; return }
  resetSmsSending.value = true
  try {
    const data = await authStore.sendPasswordResetSms(resetForm.phone)
    if (!data?.challengeId) throw new Error('发送失败，请重试')
    resetForm.challengeId = data.challengeId
    successMsg.value = '如果该手机号已注册，验证码将发送到您的手机'
    startResetCooldown(data.cooldownSeconds || 60)
  } catch (e) {
    errorMsg.value = e?.message || '验证码发送失败，请稍后再试'
  } finally {
    resetSmsSending.value = false
  }
}

async function submitPasswordReset() {
  clearMessages()
  if (!PHONE_PATTERN.test(resetForm.phone)) { errorMsg.value = '请输入正确的手机号'; return }
  if (!VERIFY_CODE_PATTERN.test(resetForm.verifyCode)) { errorMsg.value = '请输入4-8位验证码'; return }
  if (!resetForm.challengeId) { errorMsg.value = '请先获取验证码'; return }
  if (!/^(?=.*[a-zA-Z])(?=.*\d).{8,64}$/.test(resetForm.newPassword)) { errorMsg.value = '密码至少8位且包含字母和数字'; return }
  if (resetForm.newPassword !== resetForm.confirmPassword) { errorMsg.value = '两次输入的密码不一致'; return }
  try {
    await authStore.resetPassword({
      phone: resetForm.phone, verifyCode: resetForm.verifyCode,
      challengeId: resetForm.challengeId, newPassword: resetForm.newPassword,
    })
    successMsg.value = '密码已重置，请使用新密码登录'
    Object.assign(resetForm, { phone: '', verifyCode: '', challengeId: '', newPassword: '', confirmPassword: '' })
    resetCaptcha()
    view.value = 'login'
    activeTab.value = 'password'
  } catch (e) {
    errorMsg.value = e?.message || '重置失败，请重试'
  }
}

async function finishOnboarding() {
  await authStore.markOnboardingComplete()
  showOnboarding.value = false
  await router.replace('/')
}

// ─── 密码强度 ─────────────────────────────────────────────────────────────────
function strengthScore(pwd) {
  if (!pwd) return 0
  let score = 0
  if (pwd.length >= 8) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  else if (/[a-zA-Z]/.test(pwd)) score += 0.5
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  return Math.min(score, 3)
}
function strengthText(pwd) {
  const s = strengthScore(pwd)
  if (s < 1.5) return '弱'
  if (s < 2.5) return '中'
  return '强'
}
function strengthColor(pwd) {
  const s = strengthScore(pwd)
  if (s < 1.5) return '#ef4444'
  if (s < 2.5) return '#f59e0b'
  return '#10b981'
}
function strengthStyle(pwd) {
  const s = strengthScore(pwd)
  const w = s < 1.5 ? '33%' : s < 2.5 ? '66%' : '100%'
  return { width: w, background: strengthColor(pwd) }
}

onMounted(() => {
  const resize = () => {}
  window.addEventListener('resize', resize)
})
onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
  if (resetCooldownTimer) clearInterval(resetCooldownTimer)
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  padding: 24px;
  display: grid;
  place-items: center;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.login-layout {
  width: min(920px, 100%);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr 1fr;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
}

.login-hero {
  padding: 40px 36px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 24px;
}
.hero-brand { display: flex; align-items: center; gap: 10px; }
.hero-logo { width: 36px; height: 36px; background: #fff; border-radius: 10px; display: grid; place-items: center; color: #4f46e5; font-weight: 800; }
.hero-name { font-size: 18px; font-weight: 700; }
.hero-title { margin: 0; font-size: 28px; font-weight: 800; line-height: 1.3; }
.hero-desc { margin: 0; font-size: 13px; opacity: 0.85; line-height: 1.7; }
.hero-quote { background: rgba(255,255,255,0.12); backdrop-filter: blur(10px); border: 1px solid rgba(255,255,255,0.18); border-radius: 12px; padding: 14px; font-size: 12px; line-height: 1.7; }
.hero-quote-label { font-size: 11px; opacity: 0.7; margin-bottom: 6px; }

.login-card {
  padding: 40px 36px;
  position: relative;
}
.card-title { margin: 0 0 20px; font-size: 18px; font-weight: 700; color: #0f172a; }

/* Tab */
.tab-bar { display: flex; gap: 0; margin-bottom: 20px; border-bottom: 1px solid #e2e8f0; }
.tab-btn { flex: 1; border: 0; background: none; padding: 10px 0; font-size: 13px; font-weight: 500; color: #64748b; cursor: pointer; font-family: inherit; border-bottom: 2px solid transparent; margin-bottom: -1px; }
.tab-btn.active { color: #4f46e5; font-weight: 700; border-bottom-color: #4f46e5; }

/* Form */
.form { display: grid; gap: 14px; }
label { display: grid; gap: 6px; }
.field-label { font-size: 12px; color: #334155; font-weight: 500; }
input[type="text"], input[type="tel"], input[type="password"] {
  border: 1px solid #e2e8f0; border-radius: 8px; padding: 9px 12px; font-size: 13px; font-family: inherit;
}
input:focus { outline: none; border-color: #4f46e5; box-shadow: 0 0 0 2px rgba(79,70,229,0.12); }

.sms-row { display: flex; gap: 8px; }
.sms-row input { flex: 1; min-width: 0; }
.btn-sms { flex-shrink: 0; border: 1px solid #4f46e5; background: #fff; color: #4f46e5; border-radius: 8px; padding: 9px 10px; font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit; white-space: nowrap; }
.btn-sms:disabled { opacity: 0.6; cursor: default; }

.row-between { display: flex; justify-content: space-between; align-items: center; }

.checkbox-label { display: flex !important; flex-direction: row !important; gap: 6px; align-items: flex-start; font-size: 12px; color: #475569; cursor: pointer; }
.checkbox-label input[type="checkbox"] { margin-top: 2px; width: 14px; height: 14px; flex-shrink: 0; accent-color: #4f46e5; }
.checkbox-label a { color: #4f46e5; text-decoration: none; }
.checkbox-label.checkbox-error { color: #ef4444; }

.btn-primary { width: 100%; border: 0; border-radius: 8px; padding: 11px 14px; font-size: 14px; font-weight: 600; background: #4f46e5; color: #fff; cursor: pointer; font-family: inherit; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.btn-secondary { width: 100%; border: 1px solid #e2e8f0; border-radius: 8px; padding: 11px 14px; font-size: 14px; font-weight: 500; background: #f8fafc; color: #334155; cursor: pointer; font-family: inherit; }

.method-switch { margin-top: 12px; text-align: center; }
.method-switch-btn { border: 0; background: none; color: #4f46e5; cursor: pointer; font-family: inherit; font-size: 12px; padding: 0; }
.method-switch-btn:hover { text-decoration: underline; }
.forgot-password-btn { border: 0; background: none; color: #4f46e5; cursor: pointer; font-family: inherit; font-size: 12px; padding: 0; }

.captcha-img { height: 36px; border-radius: 6px; cursor: pointer; border: 1px solid #e2e8f0; }

/* 密码强度条 */
.strength-bar { height: 4px; background: #e2e8f0; border-radius: 2px; overflow: hidden; margin-top: 4px; }
.strength-fill { height: 100%; border-radius: 2px; transition: width 0.3s ease, background 0.3s ease; }
.strength-label { font-size: 11px; font-weight: 500; }

.error { margin: 8px 0 0; color: #ef4444; font-size: 12px; }
.success { margin: 8px 0 0; color: #10b981; font-size: 12px; }

/* Onboarding 遮层 */
.onboarding-overlay {
  position: absolute; inset: 0; background: rgba(255,255,255,0.97);
  display: flex; align-items: center; justify-content: center; z-index: 10; border-radius: 0 12px 12px 0;
}
.onboarding-card { max-width: 280px; text-align: center; }
.onboarding-icon { font-size: 48px; margin-bottom: 12px; }
.onboarding-card h3 { margin: 0 0 10px; font-size: 18px; font-weight: 700; color: #0f172a; }
.onboarding-card p { margin: 0 0 20px; font-size: 13px; color: #475569; line-height: 1.7; }
.onboarding-actions { display: flex; gap: 10px; }
.onboarding-actions .btn-primary, .onboarding-actions .btn-secondary { flex: 1; }

/* Legal Modals */
.legal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 16px; }
.legal-modal { background: #fff; border-radius: 16px; width: min(560px, 100%); max-height: 85vh; display: flex; flex-direction: column; }
.legal-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px 14px; border-bottom: 1px solid #e2e8f0; }
.legal-header h3 { margin: 0; font-size: 16px; font-weight: 700; color: #0f172a; }
.legal-close { border: none; background: none; font-size: 16px; color: #94a3b8; cursor: pointer; padding: 4px 8px; line-height: 1; }
.legal-close:hover { color: #475569; }
.legal-body { flex: 1; overflow-y: auto; padding: 16px 20px; font-size: 13px; color: #334155; line-height: 1.7; }
.legal-body h4 { font-size: 13px; font-weight: 700; color: #0f172a; margin: 16px 0 6px; }
.legal-body h4:first-of-type { margin-top: 8px; }
.legal-body p { margin: 0 0 8px; }
.legal-updated { color: #94a3b8; font-size: 12px; margin-bottom: 12px !important; }
.legal-footer { padding: 12px 20px 16px; border-top: 1px solid #e2e8f0; display: flex; justify-content: flex-end; }

@media (max-width: 768px) {
  .login-page { padding: 16px; align-content: start; padding-top: 32px; }
  .login-layout { grid-template-columns: 1fr; width: min(380px, 100%); }
  .login-hero { padding: 24px 20px; }
  .hero-title { font-size: 22px; }
  .hero-quote { display: none; }
  .login-card { padding: 24px 20px 28px; }
  input[type="text"], input[type="tel"], input[type="password"], .btn-primary, .btn-sms { min-height: 44px; font-size: 16px; }
  .btn-sms { padding: 9px 8px; font-size: 14px; }
  .field-label { font-size: 12px; }
}
</style>
