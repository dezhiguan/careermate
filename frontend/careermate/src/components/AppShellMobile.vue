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
        aria-label="机会：资深猎头帮你看透 JD"
        :aria-current="isActive('/opportunity') ? 'page' : undefined"
        @click="go('/opportunity')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <circle cx="12" cy="12" r="6" />
        </svg>
        <span class="nav-label">机会</span>
        <span class="nav-tagline">资深猎头帮你看透 JD</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/interview') }"
        aria-label="面试题：出题 · 判题 · 解题 · 复盘"
        :aria-current="isActive('/interview') ? 'page' : undefined"
        @click="go('/interview')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <polyline points="9 11 12 14 22 4" />
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
        </svg>
        <span class="nav-label">面试题</span>
        <span class="nav-tagline">出题 · 判题 · 解题 · 复盘</span>
      </button>

      <button
        type="button"
        class="nav-item center-ai"
        aria-label="AI 小职：求职军师 · 一句话办求职事"
        @click="go('/chat')"
      >
        <span class="ai-fab" aria-hidden="true">职</span>
        <span class="nav-label nav-ai-label" :class="{ active: isActive('/chat') }">AI 小职</span>
        <span class="nav-tagline nav-ai-tagline">求职军师 · 一句话办求职事</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/market') }"
        aria-label="市场：行业情报官，帮你判断水深"
        :aria-current="isActive('/market') ? 'page' : undefined"
        @click="go('/market')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <line x1="12" y1="20" x2="12" y2="10" />
          <line x1="18" y1="20" x2="18" y2="4" />
          <line x1="6" y1="20" x2="6" y2="16" />
        </svg>
        <span class="nav-label">市场</span>
        <span class="nav-tagline">行业情报官，帮你判断水深</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/mine') }"
        aria-label="我的：简历版本 · 训练记录 · 求职档案"
        :aria-current="isActive('/mine') ? 'page' : undefined"
        @click="go('/mine')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
        <span class="nav-label">我的</span>
        <span class="nav-tagline">简历版本 · 训练记录 · 求职档案</span>
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
  if (path === '/mine') {
    return route.path === '/mine' || route.path.startsWith('/mine/')
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
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
  overscroll-behavior-x: none;
  display: flex;
  flex-direction: column;
  background: var(--bg, #f8fafc);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --mobile-bottom-nav-space: calc(84px + env(safe-area-inset-bottom));
}

.shell-mobile-main {
  flex: 1;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
  padding-bottom: var(--mobile-bottom-nav-space);
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
  z-index: 200;
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  min-height: 72px;
  padding: 8px 0 calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #e2e8f0;
  box-shadow: 0 -4px 24px rgba(15, 23, 42, 0.08);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
  backdrop-filter: saturate(180%) blur(12px);
}

.bottom-nav::before {
  content: '';
  position: absolute;
  inset: 0;
  background: #fff;
  z-index: -1;
}

.nav-item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 2px 4px 0;
  border: none;
  background: transparent;
  color: #94a3b8;
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
  flex-shrink: 0;
}

.nav-label {
  font-size: 10px;
  font-weight: 700;
  line-height: 1.2;
  white-space: nowrap;
}

.nav-tagline {
  max-width: 100%;
  font-size: 7px;
  line-height: 1.15;
  color: #94a3b8;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-item.active .nav-tagline {
  color: #818cf8;
}

.nav-item.center-ai {
  position: relative;
  color: #4f46e5;
  font-weight: 700;
  /* 仅 FAB 圆钮可点，避免透明区域挡住页面内按钮 */
  pointer-events: none;
}

.nav-item.center-ai .ai-fab,
.nav-item.center-ai .nav-ai-label,
.nav-item.center-ai .nav-ai-tagline {
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
  box-shadow: 0 8px 22px rgba(99, 102, 241, 0.55);
  border: 4px solid #fff;
  font-weight: 800;
  font-size: 18px;
  line-height: 1;
}

.nav-ai-label {
  margin-top: 30px;
  color: #94a3b8;
}

.nav-ai-label.active {
  color: #4f46e5;
}

.nav-ai-tagline {
  margin-top: 0;
  color: #a5b4fc;
  font-weight: 600;
}

@media (max-width: 360px) {
  .nav-tagline {
    display: none;
  }

  .nav-item.center-ai .nav-ai-tagline {
    display: block;
    font-size: 6px;
  }

  .shell-mobile {
    --mobile-bottom-nav-space: calc(68px + env(safe-area-inset-bottom));
  }
}
</style>
