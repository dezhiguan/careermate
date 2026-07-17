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
        :class="{ active: isActive('/pipeline') }"
        aria-label="准备：投递看板，按阶段推进机会"
        :aria-current="isActive('/pipeline') ? 'page' : undefined"
        @click="go('/pipeline')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <rect x="3" y="4" width="5" height="16" rx="1" />
          <rect x="10" y="4" width="5" height="10" rx="1" />
          <rect x="17" y="4" width="4" height="13" rx="1" />
        </svg>
        <span class="nav-label">准备</span>
        <span class="nav-tagline">投递看板，按阶段推进</span>
      </button>

      <button
        type="button"
        class="nav-item"
        :class="{ active: isActive('/chat') }"
        aria-label="小职：求职军师 · 一句话办求职事"
        :aria-current="isActive('/chat') ? 'page' : undefined"
        @click="go('/chat')"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
        <span class="nav-label">小职</span>
        <span class="nav-tagline">求职军师 · 一句话办求职事</span>
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
  background: var(--bg, #F5F6F8);
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
  color: #4E5BEF;
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

@media (max-width: 360px) {
  .nav-tagline {
    display: none;
  }

  .shell-mobile {
    --mobile-bottom-nav-space: calc(68px + env(safe-area-inset-bottom));
  }
}
</style>
