<template>
  <Teleport to="body">
    <TransitionGroup name="toast" tag="div" class="toast-container">
      <div
        v-for="item in toasts"
        :key="item.id"
        class="toast-item"
        :class="'toast-' + item.type"
        @mouseenter="pauseTimer(item)"
        @mouseleave="resumeTimer(item)"
      >
        <div class="toast-inner">
          <div class="toast-icon-wrap">
            <!-- 成功 -->
            <svg v-if="item.type === 'success'" width="22" height="22" viewBox="0 0 22 22" fill="none">
              <circle cx="11" cy="11" r="11" fill="rgba(255,255,255,0.25)"/>
              <path d="M7 11.5L9.5 14L15 8.5" stroke="white" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <!-- 失败 -->
            <svg v-else-if="item.type === 'error'" width="22" height="22" viewBox="0 0 22 22" fill="none">
              <circle cx="11" cy="11" r="11" fill="rgba(255,255,255,0.25)"/>
              <path d="M8 8L14 14M14 8L8 14" stroke="white" stroke-width="2.2" stroke-linecap="round"/>
            </svg>
            <!-- 警告 -->
            <svg v-else-if="item.type === 'warning'" width="22" height="22" viewBox="0 0 22 22" fill="none">
              <circle cx="11" cy="11" r="11" fill="rgba(255,255,255,0.25)"/>
              <path d="M11 7V12.5" stroke="white" stroke-width="2.2" stroke-linecap="round"/>
              <circle cx="11" cy="15.5" r="1.3" fill="white"/>
            </svg>
            <!-- 提示 -->
            <svg v-else width="22" height="22" viewBox="0 0 22 22" fill="none">
              <circle cx="11" cy="11" r="11" fill="rgba(255,255,255,0.25)"/>
              <circle cx="11" cy="7.5" r="1.3" fill="white"/>
              <path d="M11 10.5V15" stroke="white" stroke-width="2.2" stroke-linecap="round"/>
            </svg>
          </div>
          <div class="toast-text">
            <div class="toast-title">{{ titleMap[item.type] }}</div>
            <div class="toast-msg">{{ item.message }}</div>
          </div>
          <button class="toast-close" @click="remove(item.id)" aria-label="关闭">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
              <path d="M3 3L9 9M9 3L3 9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <div class="toast-bar-track">
          <div
            class="toast-bar"
            :style="{ animationDuration: item.duration + 'ms', animationPlayState: item.paused ? 'paused' : 'running' }"
          ></div>
        </div>
      </div>
    </TransitionGroup>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'

const toasts = ref([])
let nextId = 0

const titleMap = {
  success: '操作成功',
  error: '操作失败',
  info: '提示信息',
  warning: '注意'
}

const add = (message, type = 'info', duration = 3500) => {
  const id = nextId++
  const item = { id, message, type, duration, paused: false, timer: null }
  item.timer = setTimeout(() => remove(id), duration)
  toasts.value.push(item)
  if (toasts.value.length > 5) remove(toasts.value[0].id)
}

const remove = (id) => {
  const idx = toasts.value.findIndex(t => t.id === id)
  if (idx > -1) {
    clearTimeout(toasts.value[idx].timer)
    toasts.value.splice(idx, 1)
  }
}

const pauseTimer = (item) => { clearTimeout(item.timer); item.paused = true }
const resumeTimer = (item) => { item.paused = false; item.timer = setTimeout(() => remove(item.id), 1500) }

defineExpose({
  success: (msg) => add(msg, 'success'),
  error: (msg) => add(msg, 'error'),
  info: (msg) => add(msg, 'info'),
  warning: (msg) => add(msg, 'warning'),
  add
})
</script>

<style>
.toast-container {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast-item {
  min-width: 320px;
  max-width: 420px;
  border-radius: 14px;
  pointer-events: auto;
  position: relative;
  overflow: hidden;
  box-shadow: 0 8px 32px -4px var(--t-shadow), 0 2px 8px rgba(0,0,0,0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.toast-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 40px -4px var(--t-shadow), 0 4px 12px rgba(0,0,0,0.08);
}

.toast-inner {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
}

.toast-icon-wrap {
  flex-shrink: 0;
  margin-top: 1px;
}

.toast-text {
  flex: 1;
  min-width: 0;
}

.toast-title {
  font-size: 15px;
  font-weight: 800;
  color: #fff;
  margin-bottom: 3px;
  letter-spacing: -0.01em;
  text-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.toast-msg {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  line-height: 1.4;
  word-break: break-word;
  text-shadow: 0 1px 2px rgba(0,0,0,0.1);
}

.toast-close {
  background: rgba(255,255,255,0.15);
  border: none;
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  width: 24px;
  height: 24px;
  padding: 0;
  border-radius: 8px;
  transition: all 0.15s ease;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toast-close:hover {
  background: rgba(255,255,255,0.3);
  color: #fff;
}

.toast-bar-track {
  height: 3px;
  background: rgba(255,255,255,0.15);
}
.toast-bar {
  height: 100%;
  background: rgba(255,255,255,0.5);
  animation: toastDown linear forwards;
}

/* ====== 柔和色调 ====== */
.toast-success {
  background: linear-gradient(135deg, #34D399, #10B981);
  --t-shadow: rgba(16, 185, 129, 0.3);
}
.toast-error {
  background: linear-gradient(135deg, #F87171, #EF4444);
  --t-shadow: rgba(239, 68, 68, 0.3);
}
.toast-info {
  background: linear-gradient(135deg, #818CF8, #6366F1);
  --t-shadow: rgba(99, 102, 241, 0.3);
}
.toast-warning {
  background: linear-gradient(135deg, #FBBF24, #F59E0B);
  --t-shadow: rgba(245, 158, 11, 0.3);
}

/* ====== 动画 ====== */
@keyframes toastDown {
  from { width: 100%; }
  to { width: 0%; }
}

.toast-enter-active {
  animation: toastIn 0.4s cubic-bezier(0.21, 1.02, 0.73, 1);
}
.toast-leave-active {
  animation: toastOut 0.28s cubic-bezier(0.06, 0.71, 0.55, 1) forwards;
}
.toast-move {
  transition: transform 0.3s cubic-bezier(0.21, 1.02, 0.73, 1);
}

@keyframes toastIn {
  from {
    opacity: 0;
    transform: translateX(30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}
@keyframes toastOut {
  0% {
    opacity: 1;
    transform: translateX(0) scale(1);
    max-height: 100px;
  }
  100% {
    opacity: 0;
    transform: translateX(50px) scale(0.92);
    max-height: 0;
    margin-bottom: -10px;
  }
}
</style>
