<template>
  <div class="login-page">
    <div class="login-card">
      <h1>CareerMate</h1>
      <p class="subtitle">AI 求职智能体</p>

      <div class="mode-toggle">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form class="form" @submit.prevent="submit">
        <label>
          用户名
          <input v-model.trim="form.username" required minlength="3" maxlength="64" />
        </label>

        <label v-if="mode === 'register'">
          邮箱
          <input v-model.trim="form.email" type="email" />
        </label>

        <label>
          密码
          <input v-model="form.password" type="password" required minlength="8" maxlength="64" />
        </label>

        <button class="primary" type="submit" :disabled="authStore.state.loading">
          {{ authStore.state.loading ? '处理中...' : mode === 'login' ? '登录' : '注册并进入' }}
        </button>
      </form>

      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="success">{{ successMsg }}</p>

      <div class="single-user-tip">
        <p>当前为本地单用户模式，可直接进入。</p>
        <button class="secondary" @click="enterSingleUser" :disabled="authStore.state.loading">进入 CareerMate</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'

const router = useRouter()
const mode = ref('login')
const errorMsg = ref('')
const successMsg = ref('')
const form = reactive({
  username: '',
  email: '',
  password: '',
})

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
    await router.replace('/')
  } catch (e) {
    errorMsg.value = e?.message || '操作失败'
  }
}

async function enterSingleUser() {
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await authStore.fetchCurrentUser()
    successMsg.value = '已获取本地用户，正在进入...'
    await router.replace('/')
  } catch (e) {
    errorMsg.value = e?.message || '当前模式不支持直接进入，请先登录'
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f8fafc;
  padding: 24px;
}
.login-card {
  width: min(460px, 100%);
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}
h1 {
  margin: 0;
  color: #0f172a;
}
.subtitle {
  margin: 8px 0 16px;
  color: #64748b;
}
.mode-toggle {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.mode-toggle button {
  flex: 1;
  border: 1px solid #dbe4ee;
  background: #f8fafc;
  color: #334155;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
}
.mode-toggle button.active {
  background: #ede9fe;
  border-color: #8b5cf6;
  color: #6d28d9;
}
.form {
  display: grid;
  gap: 12px;
}
label {
  display: grid;
  gap: 6px;
  color: #334155;
  font-size: 14px;
}
input {
  border: 1px solid #dbe4ee;
  border-radius: 10px;
  padding: 10px 12px;
}
.primary,
.secondary {
  border: 0;
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
}
.primary {
  background: #8b5cf6;
  color: #fff;
}
.secondary {
  background: #0f172a;
  color: #fff;
}
.error {
  margin: 12px 0 0;
  color: #dc2626;
}
.success {
  margin: 12px 0 0;
  color: #059669;
}
.single-user-tip {
  margin-top: 16px;
  border-top: 1px dashed #cbd5e1;
  padding-top: 12px;
}
.single-user-tip p {
  margin: 0 0 8px;
  color: #92400e;
  word-break: break-word;
  overflow-wrap: anywhere;
}

@media (max-width: 768px) {
  .login-page {
    min-height: calc(100dvh - env(safe-area-inset-bottom));
    padding: 20px 16px calc(96px + env(safe-area-inset-bottom));
    align-content: center;
  }

  .login-card {
    width: calc(100vw - 32px);
    max-width: 420px;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding-left: 16px;
    padding-right: 16px;
  }

  .login-card {
    width: calc(100vw - 32px);
    padding: 20px 16px;
  }

  .mode-toggle button {
    min-height: 44px;
    padding: 10px 8px;
    font-size: 13px;
  }

  input {
    min-height: 44px;
    font-size: 16px;
    width: 100%;
    max-width: 100%;
  }

  .primary,
  .secondary {
    min-height: 44px;
    width: 100%;
    font-size: 14px;
  }

  .error,
  .success {
    word-break: break-word;
    overflow-wrap: anywhere;
  }
}
</style>
