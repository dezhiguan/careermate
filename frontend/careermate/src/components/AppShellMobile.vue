<template>
  <div class="shell-mobile">
    <main class="shell-mobile-main" :class="{ 'no-bottom-nav': hideBottomNav }">
      <slot />
    </main>

    <nav v-if="!hideBottomNav" class="bottom-nav" aria-label="主导航">
      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/opportunity') }"
        @click="go('/opportunity')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <circle cx="12" cy="12" r="6" />
        </svg>
        <span>看机会</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/interview') }"
        @click="go('/interview')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <polyline points="9 11 12 14 22 4" />
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
        </svg>
        <span>面试准备</span>
      </button>

      <button type="button" class="nav-item center-ai" @click="go('/chat')">
        <span class="ai-fab" aria-hidden="true">职</span>
        <span class="nav-ai-label" :class="{ active: isActive('/chat') }">AI 小职</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/market') }"
        @click="go('/market')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <line x1="12" y1="20" x2="12" y2="10" />
          <line x1="18" y1="20" x2="18" y2="4" />
          <line x1="6" y1="20" x2="6" y2="16" />
        </svg>
        <span>市场行情</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/mine') }"
        @click="go('/mine')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
        <span>我的</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'

defineProps({
  hideBottomNav: { type: Boolean, default: false },
})

const route = useRoute()
const router = useRouter()

function isActive(path) {
  if (path === '/chat') {
    return route.path === '/chat' || route.path.startsWith('/chat/')
  }
  return route.path === path
}

function go(path) {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.shell-mobile {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--bg, #f8fafc);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.shell-mobile-main {
  flex: 1;
  padding-bottom: 88px;
  min-height: 0;
}

.shell-mobile-main.no-bottom-nav {
  padding-bottom: 0;
}

.bottom-nav {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  height: 62px;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  padding: 6px 0 calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #e2e8f0;
}

.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 10px;
  cursor: pointer;
  font-family: inherit;
}

.nav-item.active {
  color: #4f46e5;
}

.nav-icon {
  width: 22px;
  height: 22px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.nav-item.center-ai {
  position: relative;
  color: #4f46e5;
  font-weight: 700;
  /* 仅 FAB 圆钮可点，避免透明区域挡住页面内按钮（如「用 AI 准备」） */
  pointer-events: none;
}

.nav-item.center-ai .ai-fab,
.nav-item.center-ai .nav-ai-label {
  pointer-events: auto;
  cursor: pointer;
}

.ai-fab {
  position: absolute;
  top: -22px;
  left: 50%;
  transform: translateX(-50%);
  width: 54px;
  height: 54px;
  background: linear-gradient(135deg, #4f46e5, #8b5cf6);
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  box-shadow: 0 6px 18px rgba(99, 102, 241, 0.5);
  border: 4px solid #fff;
  font-weight: 800;
  font-size: 18px;
  line-height: 1;
}

.nav-ai-label {
  margin-top: 34px;
  font-size: 10px;
  color: #94a3b8;
  font-weight: 700;
}

.nav-ai-label.active {
  color: #4f46e5;
}
</style>
