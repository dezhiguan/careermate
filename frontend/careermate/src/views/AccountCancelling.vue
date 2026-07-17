<template>
  <div class="cancelling-page">
    <div class="cancelling-card">
      <div class="ic">!</div>
      <h2>账号注销中</h2>
      <p class="desc">你的 CareerMate 账号已申请注销，将于</p>
      <p class="desc"><span class="big">{{ remainingDays }} 天后</span>（{{ deleteDate }}）永久删除</p>
      <p class="sub">届时简历、对话等个人信息将被彻底清除，不可恢复。注销仅影响 CareerMate，不影响你的其他应用。</p>
      <button class="btn-primary" :disabled="busy" @click="restore">{{ busy ? '处理中...' : '恢复账号，继续使用' }}</button>
      <button class="btn-ghost" :disabled="busy" @click="leave">确认离开</button>
      <p v-if="err" class="err">{{ err }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'
import { revokeCancellation } from '../api/auth'

const router = useRouter()
const busy = ref(false)
const err = ref('')

const currentUser = computed(() => authStore.state.currentUser)
const scheduledAt = computed(() => currentUser.value?.deletionScheduledAt || null)

const deleteDate = computed(() => {
  const s = scheduledAt.value
  if (!s) return '30 天后'
  const d = new Date(s)
  return Number.isNaN(d.getTime()) ? '30 天后' : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})
const remainingDays = computed(() => {
  const s = scheduledAt.value
  if (!s) return 30
  const diff = new Date(s).getTime() - Date.now()
  return Math.max(0, Math.ceil(diff / 86400000))
})

async function restore() {
  err.value = ''
  busy.value = true
  try {
    await revokeCancellation()
    await authStore.fetchCurrentUser()
    await router.replace('/chat')
  } catch (e) {
    err.value = e?.message || '恢复失败，请稍后再试'
  } finally {
    busy.value = false
  }
}

async function leave() {
  await authStore.logout()
}
</script>

<style scoped>
.cancelling-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f1f5f9; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif; }
.cancelling-card { background: #fff; border-radius: 18px; box-shadow: 0 4px 24px rgba(15,23,42,.1); padding: 36px 28px; max-width: 420px; width: 100%; text-align: center; }
.ic { width: 60px; height: 60px; border-radius: 50%; background: #fff7ed; color: #ea580c; font-size: 30px; font-weight: 800; display: flex; align-items: center; justify-content: center; margin: 0 auto 18px; }
h2 { margin: 0 0 14px; font-size: 20px; color: #1A1D26; }
.desc { margin: 0 0 6px; font-size: 14px; color: #475569; }
.big { font-size: 17px; font-weight: 800; color: #E5484D; }
.sub { margin: 10px 0 24px; font-size: 12.5px; color: #94a3b8; line-height: 1.7; }
.btn-primary { width: 100%; border: 0; border-radius: 10px; padding: 12px; font-size: 15px; font-weight: 700; background: #4E5BEF; color: #fff; cursor: pointer; }
.btn-primary:disabled { opacity: .6; cursor: default; }
.btn-ghost { width: 100%; margin-top: 10px; border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; font-size: 14px; background: #fff; color: #64748b; cursor: pointer; }
.err { color: #ef4444; font-size: 12px; margin-top: 12px; }
</style>
