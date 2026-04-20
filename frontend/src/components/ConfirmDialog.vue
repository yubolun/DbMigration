<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div v-if="visible" class="confirm-overlay" @click.self="onCancel">
        <Transition name="confirm-scale">
          <div v-if="visible" class="confirm-dialog">
            <div class="confirm-icon" :class="typeClass">
              <svg v-if="type === 'danger'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            </div>
            <h3 class="confirm-title">{{ title }}</h3>
            <p class="confirm-message">{{ message }}</p>
            <div class="confirm-actions">
              <button class="confirm-btn confirm-btn-cancel" @click="onCancel">取消</button>
              <button class="confirm-btn" :class="confirmBtnClass" @click="onConfirm">{{ confirmText }}</button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '确定要执行此操作吗？' },
  confirmText: { type: String, default: '确定' },
  type: { type: String, default: 'danger' } // danger | warning | info
})

const emit = defineEmits(['confirm', 'cancel'])

const typeClass = computed(() => `icon-${props.type}`)
const confirmBtnClass = computed(() => ({
  'confirm-btn-danger': props.type === 'danger',
  'confirm-btn-warning': props.type === 'warning',
  'confirm-btn-primary': props.type === 'info'
}))

const onConfirm = () => emit('confirm')
const onCancel = () => emit('cancel')
</script>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.confirm-dialog {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 20px;
  padding: 32px 36px 28px;
  width: 400px;
  max-width: 90vw;
  text-align: center;
  box-shadow:
    0 25px 60px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.3) inset;
}

.confirm-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}
.icon-danger {
  background: linear-gradient(135deg, #FEE2E2, #FECACA);
  color: #DC2626;
}
.icon-warning {
  background: linear-gradient(135deg, #FEF3C7, #FDE68A);
  color: #D97706;
}
.icon-info {
  background: linear-gradient(135deg, #DBEAFE, #BFDBFE);
  color: #2563EB;
}

.confirm-title {
  font-size: 18px;
  font-weight: 700;
  color: #1E293B;
  margin-bottom: 8px;
}

.confirm-message {
  font-size: 14px;
  color: #64748B;
  line-height: 1.6;
  margin-bottom: 24px;
}

.confirm-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.confirm-btn {
  padding: 10px 28px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 100px;
}

.confirm-btn-cancel {
  background: #F1F5F9;
  color: #475569;
  border: 1px solid #E2E8F0;
}
.confirm-btn-cancel:hover {
  background: #E2E8F0;
  border-color: #CBD5E1;
}

.confirm-btn-danger {
  background: linear-gradient(135deg, #EF4444, #DC2626);
  color: #fff;
  box-shadow: 0 4px 14px rgba(239, 68, 68, 0.35);
}
.confirm-btn-danger:hover {
  background: linear-gradient(135deg, #DC2626, #B91C1C);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
}

.confirm-btn-warning {
  background: linear-gradient(135deg, #F59E0B, #D97706);
  color: #fff;
  box-shadow: 0 4px 14px rgba(245, 158, 11, 0.35);
}

.confirm-btn-primary {
  background: linear-gradient(135deg, #6366F1, #4F46E5);
  color: #fff;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
}

/* Animations */
.confirm-fade-enter-active { transition: opacity 0.2s ease; }
.confirm-fade-leave-active { transition: opacity 0.15s ease; }
.confirm-fade-enter-from,
.confirm-fade-leave-to { opacity: 0; }

.confirm-scale-enter-active { transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1); }
.confirm-scale-leave-active { transition: all 0.15s ease; }
.confirm-scale-enter-from { opacity: 0; transform: scale(0.85); }
.confirm-scale-leave-to { opacity: 0; transform: scale(0.9); }
</style>
