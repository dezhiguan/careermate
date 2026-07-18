<template>
  <div class="notif-page">
    <header class="notif-head">
      <button type="button" class="back-btn" @click="$router.back()">‹ 返回</button>
      <span class="notif-title">通知偏好</span>
    </header>

    <section class="notif-card">
      <div class="notif-group-title">邮件通知</div>
      <label class="notif-row">
        <span>面试/约面提醒</span>
        <input v-model="prefs.emailInterview" type="checkbox" @change="save">
      </label>
      <label class="notif-row">
        <span>Offer / 谈薪进展</span>
        <input v-model="prefs.emailOffer" type="checkbox" @change="save">
      </label>
      <label class="notif-row">
        <span>每日机会摘要</span>
        <input v-model="prefs.emailDigest" type="checkbox" @change="save">
      </label>
    </section>

    <section class="notif-card">
      <div class="notif-group-title">站内推送</div>
      <label class="notif-row">
        <span>开启浏览器推送</span>
        <input v-model="prefs.push" type="checkbox" @change="save">
      </label>
      <div class="notif-row">
        <span>推送频率</span>
        <select v-model="prefs.frequency" class="notif-sel" @change="save">
          <option value="realtime">实时</option>
          <option value="daily">每日摘要</option>
          <option value="off">关闭</option>
        </select>
      </div>
    </section>

    <p v-if="saveMsg" class="notif-msg">{{ saveMsg }}</p>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { getNotificationPrefs, saveNotificationPrefs } from '../api/notifications'

const prefs = reactive({
  emailInterview: true,
  emailOffer: true,
  emailDigest: false,
  push: false,
  frequency: 'realtime',
})
const saveMsg = ref('')
let loaded = false

onMounted(async () => {
  try {
    const data = await getNotificationPrefs()
    if (data && typeof data === 'object') {
      Object.assign(prefs, data)
    }
  } catch (e) {
    // 忽略：用默认值
  } finally {
    loaded = true
  }
})

async function save() {
  if (!loaded) return
  try {
    await saveNotificationPrefs({ ...prefs })
    saveMsg.value = '已保存'
    setTimeout(() => { saveMsg.value = '' }, 1500)
  } catch (e) {
    saveMsg.value = e?.message || '保存失败'
  }
}
</script>

<style scoped>
.notif-page { max-width: 640px; margin: 0 auto; padding: 16px; }
.notif-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back-btn { border: none; background: transparent; color: #5C6472; font-size: 14px; cursor: pointer; font-family: inherit; }
.notif-title { font-size: 18px; font-weight: 800; color: #1A1D26; }
.notif-card { background: #fff; border: 1px solid #E8EAF0; border-radius: 12px; padding: 8px 16px; margin-bottom: 14px; }
.notif-group-title { font-size: 12px; font-weight: 700; color: #9AA2AF; padding: 10px 0 6px; }
.notif-row { display: flex; align-items: center; justify-content: space-between; padding: 11px 0; border-top: 1px solid #F1F3F7; font-size: 14px; color: #1A1D26; }
.notif-row:first-of-type { border-top: none; }
.notif-sel { border: 1px solid #E8EAF0; border-radius: 8px; padding: 5px 10px; font-size: 13px; color: #1A1D26; background: #F5F6F8; }
.notif-msg { text-align: center; color: #0DA76A; font-size: 13px; }
</style>
