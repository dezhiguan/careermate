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
          <span>面试准备</span>
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

      <button type="button" class="sidebar-avatar" aria-label="我的" @click="go('/mine')">
        {{ avatarInitial }}
        <span class="online-dot" aria-hidden="true" />
      </button>
    </aside>

    <div class="main-column">
      <header v-if="showUserBar" class="user-bar">
        <div class="user-bar-title">{{ pageTitle }}</div>
        <div v-if="showTopSearch" class="user-bar-search">
          <svg class="search-icon" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            v-model="searchQuery"
            type="search"
            class="search-input"
            placeholder="搜 JD / 公司 / 技能"
            aria-label="搜索"
            @keydown.enter="handleSearch"
          >
          <button
            type="button"
            class="search-submit-btn"
            :disabled="!searchQuery.trim()"
            @click="handleSearch"
          >
            搜索
          </button>
        </div>
      </header>

      <main class="main-content" :class="{ 'chat-layout': isChatActive }">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authStore } from '../stores/authStore'

const props = defineProps({
  showUserBar: { type: Boolean, default: true },
})

const route = useRoute()
const router = useRouter()
const searchQuery = ref('')

const pageTitle = computed(() => route.meta?.title || 'CareerMate')

const showTopSearch = computed(() => route.path !== '/market')

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

function handleSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  router.push({ path: '/opportunity', query: { keyword: q, t: String(Date.now()) } })
}

watch(
  () => [route.path, route.query.keyword],
  ([path, keyword]) => {
    if (path === '/opportunity' && keyword) {
      searchQuery.value = String(keyword)
    }
  }
)
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
  flex: 1;
  min-width: 0;
  margin-left: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 12px;
  display: flex;
  gap: 8px;
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

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  outline: none;
  font-size: 12px;
  color: #0f172a;
  font-family: inherit;
}

.search-input::placeholder {
  color: #94a3b8;
}

.search-submit-btn {
  flex-shrink: 0;
  border: none;
  background: #4f46e5;
  color: #fff;
  border-radius: 6px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}

.search-submit-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
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
