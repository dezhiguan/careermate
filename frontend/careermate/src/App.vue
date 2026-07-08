<template>
  <router-view v-if="isLoginRoute" />

  <AppShellDesktop v-else-if="isDesktop" :show-user-bar="!isChatRoute">
    <div v-if="routeLoading" class="route-loading" data-testid="route-loading">
      <span class="spinner"></span><span>加载中…</span>
    </div>
    <router-view />
  </AppShellDesktop>

  <AppShellMobile v-else :hide-bottom-nav="isChatRoute">
    <div v-if="routeLoading" class="route-loading" data-testid="route-loading">
      <span class="spinner"></span><span>加载中…</span>
    </div>
    <router-view />
  </AppShellMobile>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShellDesktop from './components/AppShellDesktop.vue'
import AppShellMobile from './components/AppShellMobile.vue'
import { routeLoading } from './router'

const DESKTOP_MIN = 768

const route = useRoute()
const isDesktop = ref(false)

const isLoginRoute = computed(() => route.path === '/login')

const isChatRoute = computed(
  () => route.path === '/chat' || route.path.startsWith('/chat/')
)

function updateViewport() {
  isDesktop.value = window.innerWidth >= DESKTOP_MIN
}

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
})
</script>

<style>
.route-loading {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #64748b;
  font-size: 14px;
  background: rgba(248, 250, 252, 0.72);
  z-index: 50;
  pointer-events: none;
}
.route-loading .spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #e2e8f0;
  border-top-color: #635bff;
  border-radius: 50%;
  animation: route-spin 0.8s linear infinite;
}
@keyframes route-spin {
  to { transform: rotate(360deg); }
}

:root {
  --primary: #4f46e5;
  --primary-50: #eef2ff;
  --primary-100: #e0e7ff;
  --accent: #8b5cf6;
  --success: #10b981;
  --warning: #f59e0b;
  --danger: #ef4444;
  --ink: #0f172a;
  --ink-2: #334155;
  --ink-3: #64748b;
  --line: #e2e8f0;
  --bg: #f8fafc;
}

*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body,
#app {
  margin: 0;
  min-height: 100%;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
  overscroll-behavior-x: none;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--bg);
  color: var(--ink);
  position: relative;
  touch-action: pan-y pinch-zoom;
}
</style>
