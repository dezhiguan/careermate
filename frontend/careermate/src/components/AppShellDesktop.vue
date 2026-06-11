<template>
  <div class="shell-desktop">
    <aside class="sidebar" aria-label="侧边导航">
      <div class="sidebar-logo">C</div>

      <div class="sidebar-nav">
        <button
          type="button"
          class="sidebar-item"
          :class="{ active: isActive('/opportunity') }"
          @click="go('/opportunity')"
        >
          <svg class="sidebar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <circle cx="12" cy="12" r="6" />
          </svg>
          <span>机会</span>
        </button>

        <button
          type="button"
          class="sidebar-item"
          :class="{ active: isChatActive }"
          @click="go('/chat')"
        >
          <svg class="sidebar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          <span>小职</span>
        </button>

        <button
          type="button"
          class="sidebar-item"
          :class="{ active: isActive('/interview') }"
          @click="go('/interview')"
        >
          <svg class="sidebar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <polyline points="9 11 12 14 22 4" />
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
          </svg>
          <span>题库</span>
        </button>

        <button
          type="button"
          class="sidebar-item"
          :class="{ active: isActive('/market') }"
          @click="go('/market')"
        >
          <svg class="sidebar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <line x1="12" y1="20" x2="12" y2="10" />
            <line x1="18" y1="20" x2="18" y2="4" />
            <line x1="6" y1="20" x2="6" y2="16" />
          </svg>
          <span>市场</span>
        </button>
      </div>

      <button type="button" class="sidebar-ai-fab" aria-label="打开 AI 小职" @click="go('/chat')">
        <svg class="sidebar-ai-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
        </svg>
        <span class="sidebar-ai-badge">3</span>
      </button>

      <button type="button" class="sidebar-avatar" aria-label="我的" @click="go('/mine')">
        {{ avatarInitial }}
        <span class="online-dot" aria-hidden="true" />
      </button>
    </aside>

    <div class="main-column">
      <header v-if="showUserBar" class="user-bar">
        <div class="user-bar-title">{{ pageTitle }}</div>
        <div class="user-bar-search">
          <svg class="search-icon" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <span class="search-placeholder">搜 JD / 公司 / 技能</span>
          <span class="search-kbd">⌘K</span>
        </div>
        <div class="user-bar-spacer" />
        <button type="button" class="user-bar-icon-btn" aria-label="通知">
          <svg class="bar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          <span class="notify-badge">5</span>
        </button>
        <button type="button" class="user-bar-icon-btn" aria-label="设置">
          <svg class="bar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </button>
      </header>

      <main class="main-content" :class="{ 'chat-layout': isChatActive }">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'

const props = defineProps({
  showUserBar: { type: Boolean, default: true },
})

const route = useRoute()
const router = useRouter()

const pageTitle = computed(() => route.meta?.title || 'CareerMate')

const avatarInitial = computed(() => {
  const name = authStore.state.currentUser?.username || '用'
  return name.charAt(0).toUpperCase()
})

const isChatActive = computed(
  () => route.path === '/chat' || route.path.startsWith('/chat/')
)

function isActive(path) {
  return route.path === path
}

function go(path) {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.shell-desktop {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #0f172a;
}

.sidebar {
  width: 72px;
  flex-shrink: 0;
  background: #020617;
  color: #cbd5e1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0;
}

.sidebar-logo {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  font-size: 14px;
  margin-bottom: 18px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  width: 100%;
}

.sidebar-item {
  width: 100%;
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-family: inherit;
  font-size: 9px;
}

.sidebar-item.active {
  color: #a5b4fc;
  background: rgba(99, 102, 241, 0.15);
  border-left: 3px solid #6366f1;
}

.sidebar-icon {
  width: 22px;
  height: 22px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.sidebar-ai-fab {
  position: relative;
  margin-top: 8px;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  box-shadow: 0 6px 18px rgba(99, 102, 241, 0.45);
  cursor: pointer;
}

.sidebar-ai-icon {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.sidebar-ai-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  background: #ef4444;
  color: #fff;
  font-size: 8px;
  padding: 1px 5px;
  border-radius: 8px;
  line-height: 1.2;
}

.sidebar-avatar {
  position: relative;
  margin-top: 10px;
  width: 40px;
  height: 40px;
  background: #fff;
  border: 2px solid #6366f1;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #4338ca;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
}

.online-dot {
  position: absolute;
  bottom: 0;
  right: -1px;
  width: 10px;
  height: 10px;
  background: #10b981;
  border: 2px solid #020617;
  border-radius: 50%;
}

.main-column {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.user-bar {
  height: 56px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-bar-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.user-bar-search {
  flex: 0 1 360px;
  max-width: 360px;
  margin-left: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 10px;
  display: flex;
  gap: 6px;
  align-items: center;
  color: #94a3b8;
  font-size: 12px;
}

.search-icon {
  width: 14px;
  height: 14px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  flex-shrink: 0;
}

.search-placeholder {
  flex: 1;
  min-width: 0;
}

.search-kbd {
  margin-left: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
}

.user-bar-spacer {
  flex: 1;
}

.user-bar-icon-btn {
  position: relative;
  border: none;
  background: transparent;
  color: #475569;
  cursor: pointer;
  padding: 4px;
  display: grid;
  place-items: center;
}

.bar-icon {
  width: 20px;
  height: 20px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.notify-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  background: #ef4444;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 8px;
  line-height: 1.2;
}

.main-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.main-content.chat-layout {
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
