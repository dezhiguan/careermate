<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">📊 求职看板</h2>
      <p class="page-sub">你的求职全景</p>
    </div>

    <!-- Stats Cards -->
    <div class="stats-grid">
      <div class="stat-card" v-for="stat in stats" :key="stat.label">
        <div class="stat-value">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </div>
    </div>

    <!-- Agent Suggestions -->
    <div class="section-title">📋 Agent 建议的下一步</div>
    <div class="suggestions-grid">
      <div
        v-for="card in suggestionCards"
        :key="card.title"
        class="suggestion-card"
        :class="card.priority"
      >
        <div class="card-priority-badge">{{ card.badge }}</div>
        <div class="card-text">{{ card.text }}</div>
        <router-link :to="card.route" class="card-link">{{ card.action }}</router-link>
      </div>
    </div>

    <!-- Timeline -->
    <div class="section-title">📅 最近活动</div>
    <div class="timeline">
      <div v-for="(item, i) in activities" :key="i" class="timeline-item">
        <div class="timeline-dot" :class="item.type"></div>
        <div class="timeline-content">
          <div class="timeline-text">{{ item.text }}</div>
          <div class="timeline-time">{{ item.time }}</div>
        </div>
      </div>
    </div>

    <!-- Footer Stats -->
    <div class="footer-note">
      🤖 所有指标和建议均由 Agent 自动分析生成 · 基于你的简历 + 6 份 JD + 8 道面试题
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const stats = ref([
  { value: '6', label: '匹配岗位' },
  { value: '78%', label: '最高匹配度' },
  { value: '3', label: '简历优化建议' },
  { value: '8/8', label: '面试题完成' },
])

const suggestionCards = ref([
  {
    priority: 'high',
    badge: '⚠️ 高优先级',
    text: '补充简历中的分布式系统项目经验',
    action: '→ 去优化简历',
    route: '/resume',
  },
  {
    priority: 'mid',
    badge: '📝 建议完成',
    text: '针对字节JD做一次完整模拟面试',
    action: '→ 开始面试特训',
    route: '/interview',
  },
  {
    priority: 'low',
    badge: '💡 可选',
    text: '了解 2026 年后端开发薪资行情',
    action: '→ 问 Agent',
    route: '/',
  },
])

const activities = ref([
  { text: '完成字节跳动 后端开发 岗位匹配分析', time: '10 分钟前', type: 'match' },
  { text: '上传并解析简历: 后端开发_3年.pdf', time: '30 分钟前', type: 'resume' },
  { text: '完成 8 道面试题练习', time: '1 小时前', type: 'interview' },
  { text: 'Agent 生成了 3 条简历优化建议', time: '2 小时前', type: 'resume' },
  { text: '匹配了美团、腾讯、小红书等岗位', time: '2 小时前', type: 'match' },
])
</script>

<style scoped>
.page-container { max-width: 960px; margin: 0 auto; padding: 24px 20px; }
.page-header { margin-bottom: 18px; }
.page-title { font-size: 22px; font-weight: 700; color: var(--navy); }
.page-sub { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

/* Stats */
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 22px; }
.stat-card {
  background: var(--navy); border-radius: 10px; padding: 16px 12px; color: #fff; text-align: center;
  transition: transform .2s;
}
.stat-card:hover { transform: translateY(-2px); }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 10px; opacity: .5; margin-top: 2px; }

/* Suggestions */
.section-title { font-weight: 600; font-size: 12px; margin-bottom: 10px; }
.suggestions-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 22px; }

.suggestion-card {
  border-radius: 10px; padding: 14px; font-size: 11px; display: flex;
  flex-direction: column; gap: 8px; transition: transform .2s; background: #fff;
}
.suggestion-card:hover { transform: translateY(-2px); }
.suggestion-card.high { border: 2px solid var(--red); }
.suggestion-card.mid { border: 2px solid var(--amber); }
.suggestion-card.low { border: 1px solid var(--border); }

.card-priority-badge { font-weight: 600; }
.card-text { color: var(--gray); line-height: 1.5; }
.card-link {
  color: var(--purple); font-size: 10px; text-decoration: none; font-weight: 500;
  transition: color .2s;
}
.card-link:hover { color: #7c3aed; }

/* Timeline */
.timeline { margin-bottom: 22px; }
.timeline-item { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--border); align-items: flex-start; }
.timeline-dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; margin-top: 4px;
}
.timeline-dot.match { background: var(--purple); }
.timeline-dot.resume { background: var(--pink); }
.timeline-dot.interview { background: var(--green); }
.timeline-text { font-size: 12px; color: var(--slate); }
.timeline-time { font-size: 10px; color: var(--text-muted); margin-top: 2px; }

.footer-note { text-align: center; font-size: 10px; color: var(--text-muted); padding: 10px 0; }
</style>
