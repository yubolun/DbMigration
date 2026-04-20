<template>
  <div>
    <div class="page-header">
      <h1 class="page-title">同步日志</h1>
      <p class="page-subtitle">查看所有数据同步执行记录</p>
    </div>

    <!-- 筛选栏 -->
    <div class="card mb-4 filter-bar" @keyup.enter="loadLogs">
      <div class="filter-row">
        <div class="filter-item">
          <label class="filter-label">任务ID</label>
          <input class="form-input filter-input" v-model.number="filter.taskId" placeholder="全部" type="number" style="width: 120px;" />
        </div>
        <div class="filter-item">
          <label class="filter-label">任务名称</label>
          <input class="form-input filter-input" v-model="filter.taskName" placeholder="模糊搜索" style="width: 220px;" />
        </div>
        <div class="filter-item" style="min-width: 180px;">
          <label class="filter-label">状态</label>
          <CustomSelect v-model="filter.status" :options="statusOptions" placeholder="全部状态" />
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" @click="loadLogs" style="padding: 8px 20px; font-size: 14px;"><SvgIcon name="search" :size="14" /> 查询</button>
          <button class="btn btn-outline" @click="resetFilter" style="padding: 8px 20px; font-size: 14px;"><SvgIcon name="rotate-ccw" :size="14" /> 重置</button>
        </div>
      </div>
    </div>

    <!-- 日志表格 -->
    <div class="table-wrapper">
      <table>
        <thead>
          <tr>
            <th style="min-width: 180px;">任务名称</th>
            <th style="width: 170px;">开始时间</th>
            <th style="width: 170px;">结束时间</th>
            <th style="width: 100px;">耗时</th>
            <th style="width: 120px; text-align: right;">总行数</th>
            <th style="width: 110px; text-align: right;">成功</th>
            <th style="width: 110px; text-align: right;">失败</th>
            <th style="width: 100px; text-align: right;">QPS</th>
            <th style="width: 120px; text-align: center;">状态</th>
            <th style="min-width: 200px;">错误信息</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="text-left" style="font-weight: 500; min-width: 180px; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :title="log.taskName">{{ log.taskName || '-' }}</td>
            <td class="text-sm text-muted">{{ formatTime(log.startTime) }}</td>
            <td class="text-sm text-muted">{{ formatTime(log.endTime) }}</td>
            <td><span class="duration-tag">{{ formatDuration(log.startTime, log.endTime) }}</span></td>
            <td class="font-mono text-right" style="font-weight: 600;">{{ formatNumber(log.totalRows) }}</td>
            <td class="font-mono text-right text-success" style="font-weight: 600;">{{ formatNumber(log.successRows) }}</td>
            <td class="font-mono text-right" :class="log.failedRows > 0 ? 'text-danger' : 'text-muted'" style="font-weight: 600;">{{ log.failedRows || 0 }}</td>
            <td class="font-mono text-right"><span class="qps-badge">{{ (log.qps || 0).toFixed(0) }}</span></td>
            <td style="text-align: center;">
              <span class="badge" :class="statusClass(log.status)">
                <span v-if="log.status === 'RUNNING'" class="pulse-dot" style="background:var(--info);"></span>
                {{ statusLabel(log.status) }}
              </span>
            </td>
            <td>
              <div v-if="log.errorMsg" class="error-cell">
                <span class="error-preview">{{ truncateError(log.errorMsg) }}</span>
                <button class="error-expand-btn" @click="showError(log)" title="查看完整错误">
                  <SvgIcon name="external-link" :size="13" />
                </button>
              </div>
              <span v-else class="text-muted">-</span>
            </td>
          </tr>
          <tr v-if="!logs.length">
            <td colspan="11" style="text-align: center; color: var(--text-muted); padding: 40px;">暂无日志记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-model="currentPage" v-model:pageSize="pageSize" :total="total" @change="loadLogs" />

    <!-- 错误详情弹窗 -->
    <div class="modal-overlay" v-if="errorModal.show" @click.self="errorModal.show = false">
      <div class="modal-content" style="max-width: 680px;">
        <div class="modal-header">
          <div class="modal-title"><SvgIcon name="x-circle" :size="18" style="color: var(--danger);" /> 错误详情</div>
          <button class="modal-close" @click="errorModal.show = false">✕</button>
        </div>
        <div class="error-detail-bar">
          <span class="error-detail-tag">任务 #{{ errorModal.taskId }}</span>
          <span class="error-detail-tag">日志 #{{ errorModal.logId }}</span>
          <span class="error-detail-tag">{{ formatTime(errorModal.time) }}</span>
        </div>
        <div class="error-detail-content">
          <pre>{{ errorModal.message }}</pre>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline btn-sm" @click="copyError"><SvgIcon name="copy" :size="13" /> 复制错误</button>
          <button class="btn btn-primary btn-sm" @click="errorModal.show = false">关闭</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { monitorApi } from '../api'
import { useToast } from '../composables/useToast'
import CustomSelect from '../components/CustomSelect.vue'
import Pagination from '../components/Pagination.vue'
import SvgIcon from '../components/SvgIcon.vue'

const toast = useToast()
const logs = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusOptions = [
  { value: '', label: '全部状态', icon: '●', color: '#6366F1' },
  { value: 'RUNNING', label: '运行中', icon: '●', color: '#3B82F6' },
  { value: 'SUCCESS', label: '成功', icon: '●', color: '#10B981' },
  { value: 'FAILED', label: '失败', icon: '●', color: '#EF4444' }
]
const filter = ref({ taskId: null, status: '', taskName: '' })
const errorModal = ref({ show: false, message: '', taskId: '', logId: '', time: '' })

const resetFilter = () => {
  filter.value = { taskId: null, status: '', taskName: '' }
  currentPage.value = 1
  loadLogs()
}

const loadLogs = async () => {
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    if (filter.value.taskId && filter.value.taskId > 0) params.taskId = filter.value.taskId
    if (filter.value.status) params.status = filter.value.status
    if (filter.value.taskName && filter.value.taskName.trim()) params.taskName = filter.value.taskName.trim()
    const res = await monitorApi.logs(params)
    logs.value = res.data?.records || res.data || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('Load logs error:', e)
  }
}

// 错误信息显示
const truncateError = (msg) => {
  if (!msg) return ''
  return msg.length > 35 ? msg.substring(0, 35) + '...' : msg
}

const showError = (log) => {
  errorModal.value = {
    show: true,
    message: log.errorMsg,
    taskId: log.taskId,
    logId: log.id,
    time: log.endTime || log.startTime
  }
}

const copyError = async () => {
  try {
    await navigator.clipboard.writeText(errorModal.value.message)
    toast.success('已复制到剪贴板')
  } catch (e) {
    toast.error('复制失败')
  }
}

const formatTime = (t) => t ? t.replace('T', ' ').substring(0, 19) : '-'
const formatNumber = (n) => { if (!n && n !== 0) return '-'; return n.toLocaleString(); }
const formatDuration = (start, end) => {
  if (!start || !end) return '-'
  const diff = new Date(end) - new Date(start)
  if (diff < 0) return '0s'
  const s = Math.floor(diff / 1000)
  if (s < 60) return s + 's'
  const m = Math.floor(s / 60)
  return `${m}m ${s % 60}s`
}
const statusClass = (s) => ({ RUNNING: 'badge-running', SUCCESS: 'badge-success', FAILED: 'badge-failed' })[s] || 'badge-idle'
const statusLabel = (s) => ({ RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败' })[s] || s


onMounted(() => {
  loadLogs()
})

onUnmounted(() => {
})
</script>

<style scoped>
.filter-bar {
  padding: 16px 20px;
}
.filter-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.filter-input {
  height: 40px;
  font-size: 14px;
}
.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-bottom: 2px;
}
.text-right { text-align: right; }
.duration-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
}
.qps-badge {
  font-weight: 700;
  color: var(--primary);
  background: var(--primary-bg);
  padding: 2px 6px;
  border-radius: 4px;
}

/* ---- Status Dots ---- */
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-all { background: var(--text-muted); }
.dot-RUNNING { background: var(--info); animation: pulse 2s ease-in-out infinite; }
.dot-SUCCESS { background: var(--success); }
.dot-FAILED { background: var(--danger); }

/* ---- Error Cell ---- */
.error-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.error-preview {
  font-size: 12px;
  color: var(--danger);
  background: var(--danger-bg);
  padding: 4px 10px;
  border-radius: var(--radius-xs);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'SF Mono', Monaco, monospace;
  line-height: 1.4;
}
.error-expand-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 16px;
  padding: 2px;
  border-radius: 4px;
  transition: all 0.15s ease;
  opacity: 0.6;
}
.error-expand-btn:hover {
  opacity: 1;
  background: var(--bg-hover);
  transform: scale(1.1);
}

/* ---- Error Detail Modal ---- */
.error-detail-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.error-detail-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  background: var(--bg-hover);
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}
.error-detail-content {
  background: #1E1E2E;
  border-radius: var(--radius-sm);
  padding: 20px;
  max-height: 400px;
  overflow-y: auto;
}
.error-detail-content pre {
  color: #F38BA8;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
