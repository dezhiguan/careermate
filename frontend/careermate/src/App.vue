<template>
  <div class="app-shell">
    <header class="top-status" v-if="authStore.state.currentUser">
      <span class="user-badge">
        {{ authStore.state.currentUser.username }} / {{ authStore.state.currentUser.role }}
      </span>
      <button class="logout-btn" @click="authStore.logout">退出</button>
    </header>
    <main class="main-content">
      <router-view />
    </main>
    <nav class="bottom-nav">
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
    </nav>
  </div>
</template>

<script setup>
import { authStore } from './stores/authStore'
</script>

<style>
:root {
  --navy: #0f172a; --slate: #1e293b; --gray: #475569;
  --light: #f1f5f9; --blue: #3b82f6; --cyan: #06b6d4;
  --green: #10b981; --amber: #f59e0b; --red: #ef4444;
  --purple: #8b5cf6; --border: #e2e8f0; --text: #1e293b;
  --text-muted: #64748b; --pink: #ec4899;
}

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}

.main-content {
  flex: 1;
  padding-bottom: 72px;
  padding-top: 44px;
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

.bottom-nav {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  background: #fff;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: space-around;
  padding: 8px 0 12px;
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0,0,0,.04);
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
</style>
