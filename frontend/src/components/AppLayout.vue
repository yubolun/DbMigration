<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <div class="brand-icon">DB</div>
      <div>
        <div class="brand-text">DB Migration</div>
        <div class="brand-sub">异构数据库同步平台</div>
      </div>
    </div>
    <nav class="sidebar-nav">
      <router-link
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ active: isActive(item.path) }"
      >
        <SvgIcon :name="item.icon" :size="18" class="nav-icon" />
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
    <div style="padding: 16px; border-top: 1px solid var(--border-light);">
      <div class="text-sm text-muted" style="text-align: center; display: flex; align-items: center; justify-content: center; gap: 6px;">
        <span style="width: 6px; height: 6px; background: var(--success); border-radius: 50%; display: inline-block;"></span>
        v2.0.0 · Spring Boot 3
      </div>
    </div>
  </aside>
  <main class="main-content">
    <router-view />
  </main>
</template>

<script setup>
import { useRoute } from 'vue-router'
import SvgIcon from './SvgIcon.vue'

const route = useRoute()

const navItems = [
  { path: '/dashboard', icon: 'dashboard', label: '监控看板' },
  { path: '/datasources', icon: 'database', label: '数据源管理' },
  { path: '/tasks', icon: 'refresh', label: '同步任务' },
  { path: '/logs', icon: 'file-text', label: '同步日志' }
]

const isActive = (path) => {
  if (path === '/tasks') {
    return route.path.startsWith('/tasks')
  }
  return route.path === path
}
</script>
