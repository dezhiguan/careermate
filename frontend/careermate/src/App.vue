<template>
  <div class="app-shell" :class="{ 'nav-expanded': navExpanded }">
    <header class="top-status" v-if="authStore.state.currentUser">
      <span class="user-badge">
        {{ authStore.state.currentUser.username }} / {{ authStore.state.currentUser.role }}
      </span>
      <button class="logout-btn" @click="authStore.logout">退出</button>
    </header>
    <main class="main-content">
      <router-view />
    </main>
    <div v-if="authStore.state.currentUser" class="bottom-nav-shell">
      <button
        v-if="!navExpanded"
        type="button"
        class="nav-expand-btn"
        aria-label="打开导航菜单"
        @click="setNavExpanded(true)"
      >
        <span class="nav-expand-icon">☰</span>
        <span class="nav-expand-label">功能导航</span>
      </button>
      <nav v-else class="bottom-nav">
        <button
          type="button"
          class="nav-collapse-btn"
          aria-label="收起导航"
          @click="setNavExpanded(false)"
        >
          <span class="collapse-bar" />
          <span class="collapse-text">收起</span>
        </button>
        <div class="nav-items">
          <router-link to="/" class="nav-item" active-class="active">
            <span class="nav-icon">💬</span>
            <span class="nav-label">对话台</span>
          </router-link>
          <router-link to="/resume" class="nav-item" active-class="active">
            <span class="nav-icon">📝</span>
            <span class="nav-label">简历</span>
          </router-link>
          <router-link to="/match" class="nav-item" active-class="active">
            <span class="nav-icon">🎯</span>
            <span class="nav-label">岗位匹配</span>
          </router-link>
          <router-link to="/interview" class="nav-item" active-class="active">
            <span class="nav-icon">🎤</span>
            <span class="nav-label">面试特训</span>
          </router-link>
          <router-link to="/dashboard" class="nav-item" active-class="active">
            <span class="nav-icon">📊</span>
            <span class="nav-label">求职看板</span>
          </router-link>
        </div>
      </nav>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { authStore } from './stores/authStore'

const NAV_EXPANDED_KEY = 'careermate.navExpanded'

function readNavExpanded() {
  try {
    return localStorage.getItem(NAV_EXPANDED_KEY) === 'true'
  } catch {
    return false
  }
}

const navExpanded = ref(readNavExpanded())

function setNavExpanded(expanded) {
  navExpanded.value = expanded
  try {
    localStorage.setItem(NAV_EXPANDED_KEY, expanded ? 'true' : 'false')
  } catch {
    // ignore storage failures
  }
}
</script>

<style>
:root {
  --navy: #0f172a; --slate: #1e293b; --gray: #475569;
  --light: #f1f5f9; --blue: #3b82f6; --cyan: #06b6d4;
  --green: #10b981; --amber: #f59e0b; --red: #ef4444;
  --purple: #8b5cf6; --border: #e2e8f0; --text: #1e293b;
  --text-muted: #64748b; --pink: #ec4899;
  --bottom-nav-h: 52px;
  --bottom-nav-expanded-h: 88px;
}

.app-shell {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.app-shell.nav-expanded {
  --bottom-nav-h: var(--bottom-nav-expanded-h);
}

.main-content {
  flex: 1;
  padding-bottom: calc(var(--bottom-nav-h) + env(safe-area-inset-bottom));
  padding-top: 44px;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.top-status {
  position: fixed;
  top: 8px;
  right: 12px;
  z-index: 200;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-badge {
  font-size: 12px;
  color: var(--navy);
  background: #eef2ff;
  border: 1px solid #c7d2fe;
  border-radius: 999px;
  padding: 4px 10px;
}

.logout-btn {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text-muted);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}

.bottom-nav-shell {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  width: 100%;
  max-width: 100%;
  padding-bottom: env(safe-area-inset-bottom);
}

.nav-expand-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0 auto 8px;
  min-height: 44px;
  padding: 8px 18px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: #fff;
  color: var(--text);
  box-shadow: 0 4px 16px rgba(15, 23, 42, .12);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.nav-expand-btn:hover {
  color: var(--purple);
  border-color: #ddd6fe;
  background: #faf5ff;
}

.nav-expand-icon {
  font-size: 16px;
  line-height: 1;
}

.bottom-nav {
  background: #fff;
  border-top: 1px solid var(--border);
  box-shadow: 0 -2px 12px rgba(0,0,0,.04);
  width: 100%;
  max-width: 100%;
}

.nav-collapse-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  width: 100%;
  padding: 6px 0 2px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.collapse-bar {
  width: 36px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
}

.collapse-text {
  font-size: 10px;
  line-height: 1.2;
}

.nav-items {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  padding: 0 4px 8px;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  text-decoration: none;
  color: var(--text-muted);
  font-size: 10px;
  padding: 4px 12px;
  border-radius: 8px;
  transition: all .2s;
}

.nav-item.active {
  color: var(--purple);
}

.nav-item.active .nav-icon {
  transform: scale(1.15);
}

.nav-icon {
  font-size: 20px;
  transition: transform .2s;
}

.nav-label {
  font-weight: 500;
}

.nav-item:hover {
  color: var(--purple);
  background: #f5f3ff;
}

@media (max-width: 768px) {
  .main-content {
    padding-top: 40px;
  }
}

@media (max-width: 480px) {
  .top-status {
    top: 4px;
    left: 8px;
    right: auto;
    max-width: calc(100vw - 16px);
    flex-wrap: wrap;
    gap: 6px;
  }

  .user-badge {
    max-width: min(52vw, 200px);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 10px;
    padding: 4px 8px;
  }

  .logout-btn {
    min-height: 44px;
    min-width: 44px;
    padding: 8px 12px;
    font-size: 12px;
  }

  .main-content {
    padding-top: 52px;
  }

  .nav-items {
    padding-left: 0;
    padding-right: 0;
    gap: 0;
  }

  .nav-item {
    padding: 4px 2px;
    min-height: 44px;
    min-width: 0;
    flex: 1;
    justify-content: center;
  }

  .nav-icon {
    font-size: 18px;
  }

  .nav-label {
    font-size: 10px;
    white-space: nowrap;
    line-height: 1.2;
  }
}
</style>
