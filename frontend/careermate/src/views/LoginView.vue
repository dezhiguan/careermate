<template>
  <div class="login-page">
    <div class="login-layout">
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

      <section class="login-card">
        <template v-if="isMobile">
          <h2 class="card-title">手机号登录</h2>
          <p class="card-sub">验证码登录，新用户将自动注册</p>

          <form class="form" @submit.prevent="submitMobile">
            <label>
              <span class="field-label">手机号</span>
              <input
                v-model.trim="mobileForm.phone"
                type="tel"
                inputmode="numeric"
                maxlength="11"
                placeholder="请输入手机号"
                required
              />
            </label>

            <label>
              <span class="field-label">验证码</span>
              <div class="sms-row">
                <input
                  v-model.trim="mobileForm.verifyCode"
                  type="text"
                  inputmode="numeric"
                  maxlength="8"
                  placeholder="请输入验证码"
                  required
                />
                <button
                  type="button"
                  class="btn-sms"
                  :disabled="authStore.state.smsSending || cooldown > 0"
                  @click="sendSms"
                >
                  {{
                    authStore.state.smsSending
                      ? '发送中...'
                      : cooldown > 0
                        ? `${cooldown}s`
                        : '发送验证码'
                  }}
                </button>
              </div>
            </label>

            <button class="btn-primary" type="submit" :disabled="authStore.state.loading">
              {{ authStore.state.loading ? '处理中...' : '手机号登录' }}
            </button>
          </form>
        </template>

        <template v-else>
          <h2 class="card-title">账号登录</h2>
          <p class="card-sub">使用现有账号进入 CareerMate</p>

          <div class="mode-toggle">
            <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
            <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
          </div>

          <form class="form" @submit.prevent="submit">
            <label>
              <span class="field-label">用户名</span>
              <input v-model.trim="form.username" required minlength="3" maxlength="64" />
            </label>

            <label v-if="mode === 'register'">
              <span class="field-label">邮箱</span>
              <input v-model.trim="form.email" type="email" />
            </label>

            <label>
              <span class="field-label">密码</span>
              <input v-model="form.password" type="password" required minlength="8" maxlength="64" />
            </label>

            <button class="btn-primary" type="submit" :disabled="authStore.state.loading">
              {{ authStore.state.loading ? '处理中...' : mode === 'login' ? '登录' : '注册并进入' }}
            </button>
          </form>
        </template>

        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="success">{{ successMsg }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'

const PHONE_PATTERN = /^1[3-9]\d{9}$/
const VERIFY_CODE_PATTERN = /^\d{4,8}$/

const router = useRouter()
const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 768)
const mode = ref(isMobile.value ? 'mobile' : 'login')
const errorMsg = ref('')
const successMsg = ref('')
const form = reactive({
  username: '',
  email: '',
  password: '',
})
const mobileForm = reactive({
  phone: '',
  verifyCode: '',
})
const challengeId = ref('')
const smsSent = ref(false)
const cooldown = ref(0)
let cooldownTimer = null

function updateViewport() {
  const mobile = window.innerWidth < 768
  isMobile.value = mobile
  if (mobile) {
    mode.value = 'mobile'
  } else if (mode.value === 'mobile') {
    mode.value = 'login'
  }
}

function startCooldown(seconds) {
  const duration = Math.max(0, Number(seconds) || 60)
  cooldown.value = duration
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  if (duration <= 0) return
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

function isValidPhone(phone) {
  return PHONE_PATTERN.test(phone)
}

function isValidVerifyCode(code) {
  return VERIFY_CODE_PATTERN.test(code)
}

async function sendSms() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!isValidPhone(mobileForm.phone)) {
    errorMsg.value = '请输入正确的手机号'
    return
  }
  try {
    const data = await authStore.sendMobileSmsCode(mobileForm.phone)
    if (!data?.challengeId) {
      throw new Error('验证码发送失败，请重试')
    }
    challengeId.value = data.challengeId
    smsSent.value = true
    successMsg.value = '验证码已发送'
    startCooldown(data.cooldownSeconds)
  } catch (e) {
    errorMsg.value = e?.message || '发送验证码失败'
  }
}

async function submitMobile() {
  errorMsg.value = ''
  successMsg.value = ''
  if (!isValidPhone(mobileForm.phone)) {
    errorMsg.value = '请输入正确的手机号'
    return
  }
  if (!isValidVerifyCode(mobileForm.verifyCode)) {
    errorMsg.value = '请输入4-8位验证码'
    return
  }
  if (!smsSent.value || !challengeId.value) {
    errorMsg.value = '请先发送验证码'
    return
  }
  try {
    await authStore.mobileLogin(mobileForm.phone, mobileForm.verifyCode, challengeId.value)
    successMsg.value = '登录成功，正在进入...'
    await router.replace('/opportunity')
  } catch (e) {
    errorMsg.value = e?.message || '登录失败'
  }
}

async function submit() {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    if (mode.value === 'login') {
      await authStore.login(form.username, form.password)
      successMsg.value = '登录成功，正在进入...'
    } else {
      await authStore.register(form.username, form.password, form.email)
      successMsg.value = '注册成功，正在进入...'
    }
    await router.replace('/opportunity')
  } catch (e) {
    errorMsg.value = e?.message || '操作失败'
  }
}

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateViewport)
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
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

.hero-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-logo {
  width: 36px;
  height: 36px;
  background: #fff;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #4f46e5;
  font-weight: 800;
}

.hero-name {
  font-size: 18px;
  font-weight: 700;
}

.hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.3;
}

.hero-desc {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
  line-height: 1.7;
}

.hero-quote {
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 12px;
  padding: 14px;
  font-size: 12px;
  line-height: 1.7;
}

.hero-quote-label {
  font-size: 11px;
  opacity: 0.7;
  margin-bottom: 6px;
}

.login-card {
  padding: 40px 36px;
}

.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.card-sub {
  margin: 4px 0 24px;
  font-size: 12px;
  color: #94a3b8;
}

.mode-toggle {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.mode-toggle button {
  flex: 1;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
}

.mode-toggle button.active {
  background: #eef2ff;
  border-color: #4f46e5;
  color: #4f46e5;
  font-weight: 600;
}

.form {
  display: grid;
  gap: 14px;
}

label {
  display: grid;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

input {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 13px;
  font-family: inherit;
}

.sms-row {
  display: flex;
  gap: 8px;
}

.sms-row input {
  flex: 1;
  min-width: 0;
}

.btn-sms {
  flex-shrink: 0;
  border: 1px solid #4f46e5;
  background: #fff;
  color: #4f46e5;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
}

.btn-sms:disabled {
  opacity: 0.6;
  cursor: default;
}

.btn-primary {
  width: 100%;
  border: 0;
  border-radius: 8px;
  padding: 11px 14px;
  font-size: 14px;
  font-weight: 600;
  background: #4f46e5;
  color: #fff;
  cursor: pointer;
  font-family: inherit;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: default;
}

.error {
  margin: 12px 0 0;
  color: #ef4444;
  font-size: 12px;
}

.success {
  margin: 12px 0 0;
  color: #10b981;
  font-size: 12px;
}

@media (max-width: 768px) {
  .login-page {
    padding: 16px;
    align-content: start;
    padding-top: 32px;
  }

  .login-layout {
    grid-template-columns: 1fr;
    width: min(380px, 100%);
  }

  .login-hero {
    padding: 24px 20px;
  }

  .hero-title {
    font-size: 22px;
  }

  .hero-quote {
    display: none;
  }

  .login-card {
    padding: 24px 20px 28px;
  }

  input,
  .btn-primary,
  .btn-sms,
  .mode-toggle button {
    min-height: 44px;
    font-size: 16px;
  }

  .field-label {
    font-size: 12px;
  }
}
</style>
