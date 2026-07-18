<template>
  <div class="export-page">
    <header class="export-head">
      <button type="button" class="back-btn" @click="$router.back()">‹ 返回</button>
      <span class="export-title">数据导出</span>
    </header>

    <section class="export-card">
      <p class="export-desc">导出你在 CareerMate 的全部数据（求职画像、简历版本、面试记录、八股题库、投递记录、长期记忆），保存为一份 JSON 文件，归你所有。</p>
      <ul class="export-list">
        <li>📋 求职画像</li>
        <li>📄 简历版本</li>
        <li>🎤 面试记录</li>
        <li>📚 八股题库</li>
        <li>📌 投递记录</li>
        <li>🧠 长期记忆</li>
      </ul>
      <button type="button" class="export-btn" :disabled="busy" @click="doExport">
        {{ busy ? '正在导出…' : '导出我的数据 (JSON)' }}
      </button>
      <p v-if="msg" class="export-msg">{{ msg }}</p>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { request } from '../api/http'

const busy = ref(false)
const msg = ref('')

async function doExport() {
  busy.value = true
  msg.value = ''
  try {
    const data = await request('/user/data-export')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const stamp = new Date().toISOString().slice(0, 10)
    a.href = url
    a.download = `careermate-data-${stamp}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    msg.value = '已导出，请查看下载文件'
  } catch (e) {
    msg.value = e?.message || '导出失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.export-page { max-width: 640px; margin: 0 auto; padding: 16px; }
.export-head { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.back-btn { border: none; background: transparent; color: #5C6472; font-size: 14px; cursor: pointer; font-family: inherit; }
.export-title { font-size: 18px; font-weight: 800; color: #1A1D26; }
.export-card { background: #fff; border: 1px solid #E8EAF0; border-radius: 12px; padding: 18px; }
.export-desc { font-size: 13.5px; color: #5C6472; line-height: 1.6; margin: 0 0 12px; }
.export-list { list-style: none; padding: 0; margin: 0 0 16px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 13px; color: #1A1D26; }
.export-btn { width: 100%; border: none; border-radius: 10px; padding: 11px; font-size: 14px; font-weight: 600; color: #fff; background: linear-gradient(135deg, #4E5BEF, #8B5CF6); cursor: pointer; font-family: inherit; }
.export-btn:disabled { opacity: .55; cursor: default; }
.export-msg { text-align: center; color: #0DA76A; font-size: 13px; margin-top: 10px; }
</style>
