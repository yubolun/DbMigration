<template>
  <div class="card ds-card" :class="{ 'ds-card-selected': selected }">
    <!-- 顶部区：图标 + 名称 + 状态 -->
    <div class="ds-card-header">
      <div class="ds-card-icon">
        <DbIcon :type="ds.dbType" :size="22" />
      </div>
      <div class="ds-card-info">
        <div class="ds-card-name">{{ ds.name }}</div>
        <div class="ds-card-type">{{ ds.dbType }}</div>
      </div>
      <div class="ds-card-badge" :class="statusBadgeClass">
        <span class="ds-dot"></span>
        {{ statusText }}
      </div>
    </div>

    <!-- 中部：连接信息 -->
    <div class="ds-card-body">
      <div class="ds-detail-row">
        <SvgIcon name="server" :size="14" class="ds-detail-icon" />
        <span class="ds-detail-label">地址</span>
        <span class="ds-detail-value font-mono">{{ ds.host }}:{{ ds.port }}</span>
      </div>
      <div class="ds-detail-row">
        <SvgIcon name="database" :size="14" class="ds-detail-icon" />
        <span class="ds-detail-label">库名</span>
        <span class="ds-detail-value font-mono">{{ ds.dbName }}</span>
      </div>
      <div class="ds-detail-row">
        <SvgIcon name="user" :size="14" class="ds-detail-icon" />
        <span class="ds-detail-label">用户</span>
        <span class="ds-detail-value">{{ ds.username }}</span>
      </div>
      <div class="ds-detail-row" v-if="ds.lastPingTime">
        <SvgIcon name="clock" :size="14" class="ds-detail-icon" />
        <span class="ds-detail-label">检测</span>
        <span class="ds-detail-value text-muted">{{ formatTime(ds.lastPingTime) }}</span>
      </div>
    </div>

    <!-- 底部：操作按钮 -->
    <div class="ds-card-footer" v-if="showActions">
      <button class="ds-action" @click.stop="$emit('ping', ds.id)" :disabled="pinging">
        <SvgIcon name="activity" :size="14" />
        {{ pinging ? '检测中' : 'Ping' }}
      </button>
      <button class="ds-action" @click.stop="$emit('copy', ds)">
        <SvgIcon name="copy" :size="14" />
        复制
      </button>
      <button class="ds-action" @click.stop="$emit('edit', ds)">
        <SvgIcon name="edit" :size="14" />
        编辑
      </button>
      <button class="ds-action ds-action-danger" @click.stop="$emit('delete', ds.id)">
        <SvgIcon name="trash" :size="14" />
        删除
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DbIcon from './DbIcon.vue'
import SvgIcon from './SvgIcon.vue'

const props = defineProps({
  ds: { type: Object, required: true },
  showActions: { type: Boolean, default: true },
  selected: { type: Boolean, default: false },
  pinging: { type: Boolean, default: false }
})

defineEmits(['ping', 'edit', 'copy', 'delete'])

const brandColors = {
  MYSQL: ['#00758F', '#00B4D8'],
  ORACLE: ['#F80000', '#FF6B35'],
  POSTGRESQL: ['#336791', '#5B9BD5'],
  DM: ['#0052CC', '#4C9AFF'],
  GAUSSDB: ['#E60012', '#FF4D6A'],
  OCEANBASE: ['#006AFF', '#4DA6FF']
}

const brandGradient = computed(() => {
  const [c1, c2] = brandColors[props.ds.dbType] || ['#94A3B8', '#CBD5E1']
  return `linear-gradient(135deg, ${c1}, ${c2})`
})

const statusBadgeClass = computed(() => {
  if (props.ds.status === 1) return 'ds-badge-online'
  if (props.ds.status === 2) return 'ds-badge-offline'
  return 'ds-badge-unknown'
})

const statusText = computed(() => {
  if (props.ds.status === 1) return '在线'
  if (props.ds.status === 2) return '离线'
  return '未知'
})

const formatTime = (t) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.ds-card {
  cursor: default;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.ds-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.ds-card-selected {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-bg);
}

/* Header */
.ds-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 20px 14px;
}

.ds-card-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: white;
  border: 1px solid var(--border-light);
}

.ds-card-info {
  flex: 1;
  min-width: 0;
}

.ds-card-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-card-type {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

/* Status Badge */
.ds-card-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.ds-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.ds-badge-online {
  background: #ECFDF5;
  color: #059669;
}
.ds-badge-online .ds-dot {
  background: #059669;
  box-shadow: 0 0 0 2px rgba(5, 150, 105, 0.2);
  animation: pulse-dot 2s infinite;
}

.ds-badge-offline {
  background: #FEF2F2;
  color: #DC2626;
}
.ds-badge-offline .ds-dot {
  background: #DC2626;
}

.ds-badge-unknown {
  background: var(--bg-hover);
  color: var(--text-muted);
}
.ds-badge-unknown .ds-dot {
  background: var(--text-muted);
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Body Details */
.ds-card-body {
  padding: 0 20px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.ds-detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1;
}

.ds-detail-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.ds-detail-label {
  color: var(--text-muted);
  width: 32px;
  flex-shrink: 0;
  font-size: 12px;
}

.ds-detail-value {
  color: var(--text-secondary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Footer Actions */
.ds-card-footer {
  display: flex;
  border-top: 1px solid var(--border-light);
}

.ds-action {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  background: none;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
}
.ds-action:not(:last-child) {
  border-right: 1px solid var(--border-light);
}
.ds-action:hover {
  color: var(--primary);
  background: var(--primary-bg);
}
.ds-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ds-action-danger:hover {
  color: var(--danger);
  background: #FEF2F2;
}
</style>
