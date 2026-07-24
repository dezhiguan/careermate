<template>
  <div class="shell-desktop">
    <aside class="sidebar" aria-label="侧边导航">
      <div class="sidebar-logo"><span class="logo-mark">C</span><span class="logo-text">CareerMate</span></div>

      <div class="sidebar-nav">
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
          :class="{ active: isActive('/pipeline') }"
          @click="go('/pipeline')"
        >
          <svg class="sidebar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <rect x="3" y="4" width="5" height="16" rx="1" />
            <rect x="10" y="4" width="5" height="10" rx="1" />
            <rect x="17" y="4" width="4" height="13" rx="1" />
          </svg>
          <span>准备</span>
        </button>

        <button
          type="button"
          class="sidebar-item"
          :class="{ active: isActive('/assets') }"
          @click="go('/assets')"
        >
          <svg class="sidebar-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 4h16v4H4z" />
            <path d="M4 10h16v10H4z" />
            <line x1="8" y1="14" x2="16" y2="14" />
          </svg>
          <span>资产</span>
        </button>

        <!-- v3.4：「我的」收进导航组，与前四项连排（原 sidebar-grow 会把它挤到侧栏底部，已移除） -->
        <button
          type="button"
          class="sidebar-item"
          :class="{ active: isActive('/mine') }"
          @click="go('/mine')"
        >
          <span class="sidebar-ava">{{ avatarInitial }}</span>
          <span>我的</span>
        </button>
      </div>
    </aside>

    <div class="main-column">
      <header v-if="showUserBarFinal" class="user-bar">
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

// 这些主页面已有自己的定稿页头（mhead），隐藏 app 级顶栏避免标题重复
const OWN_HEADER_ROUTES = ['/opportunity', '/pipeline', '/assets', '/mine']
const showUserBarFinal = computed(() => props.showUserBar && !OWN_HEADER_ROUTES.includes(route.path))

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
  background: #F5F6F8;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #1A1D26;
}

/* 照 09 定稿 sideV：浅色宽侧栏 */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #FFFFFF;
  border-right: 1px solid #E8EAF0;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 800;
  font-size: 15px;
  color: #1A1D26;
  padding: 2px 10px 16px;
}
.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #4E5BEF, #8B5CF6);
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  width: 100%;
}

.sidebar-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #5C6472;
  cursor: pointer;
  font-family: inherit;
  font-size: 13.5px;
  text-align: left;
}

.sidebar-item:hover {
  background: #F1F3F7;
}

.sidebar-item.active {
  background: #EEF0FE;
  color: #4E5BEF;
  font-weight: 700;
}

.sidebar-icon {
  width: 20px;
  height: 20px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  flex-shrink: 0;
}

.sidebar-grow {
  flex: 1;
}

.sidebar-ava {
  width: 20px;
  height: 20px;
  border-radius: 7px;
  background: #EEF1F6;
  color: #3E4654;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 11px;
  flex-shrink: 0;
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
  color: #1A1D26;
}

.user-bar-search {
  flex: 1;
  min-width: 0;
  margin-left: 18px;
  background: #F5F6F8;
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
  color: #1A1D26;
  font-family: inherit;
}

.search-input::placeholder {
  color: #94a3b8;
}

.search-submit-btn {
  flex-shrink: 0;
  border: none;
  background: #4E5BEF;
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
