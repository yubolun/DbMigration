<template>
  <div>
    <div class="page-header flex justify-between items-center">
      <div>
        <h1 class="page-title">监控看板</h1>
        <p class="page-subtitle">实时数据同步状况一览</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon primary"><SvgIcon name="database" :size="22" /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.onlineDataSources || 0 }}<span class="text-muted text-sm"> / {{ stats.totalDataSources || 0 }}</span></div>
          <div class="stat-label">数据源 (在线/总数)</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon info"><SvgIcon name="refresh" :size="22" /></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.runningTasks || 0 }}<span class="text-muted text-sm"> / {{ stats.totalTasks || 0 }}</span></div>
          <div class="stat-label">任务 (运行中/总数)</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon success"><SvgIcon name="trending-up" :size="22" /></div>
        <div class="stat-info">
          <div class="stat-value">{{ formatNumber(stats.todaySyncRows || 0) }}</div>
          <div class="stat-label">今日同步行数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon warning"><SvgIcon name="zap" :size="22" /></div>
        <div class="stat-info">
          <div class="stat-value">{{ currentQps }}</div>
          <div class="stat-label">实时 QPS</div>
        </div>
      </div>
    </div>

    <!-- 实时进度面板 -->
    <div class="card mb-4" v-if="progressList.length">
      <h3 style="font-size: 16px; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; gap: 8px;"><SvgIcon name="activity" :size="18" style="color: var(--danger);" /> 运行中的任务</h3>
      <div v-for="p in progressList" :key="p.taskId" style="margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border-light);">
        <div class="flex justify-between items-center" style="margin-bottom: 8px;">
          <span style="font-weight: 600;">任务 #{{ p.taskId }}</span>
          <span class="badge badge-running">
            <span class="pulse-dot" style="background: var(--info);"></span>
            运行中
          </span>
        </div>
        <div class="progress-bar" style="margin-bottom: 8px;">
          <div class="progress-fill" :style="{ width: (p.progress || 0) + '%' }"></div>
        </div>
        <div class="flex justify-between text-sm text-muted">
          <span><SvgIcon name="check-circle" :size="13" style="color: var(--success);" /> {{ formatNumber(p.successRows || 0) }} / {{ formatNumber(p.totalRows || 0) }} 行</span>
          <span><SvgIcon name="zap" :size="13" /> {{ (p.qps || 0).toFixed(0) }} rows/s</span>
          <span><SvgIcon name="trending-up" :size="13" /> {{ (p.progress || 0).toFixed(1) }}%</span>
        </div>
      </div>
    </div>

    <!-- 最近同步日志 -->
    <div class="card" style="flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden;">
      <h3 style="font-size: 16px; font-weight: 600; margin-bottom: 16px; display: flex; align-items: center; gap: 8px;"><SvgIcon name="file-text" :size="18" style="color: var(--primary);" /> 最近同步记录</h3>
      <div class="table-wrapper" style="border: none;">
        <table>
          <thead>
            <tr>
              <th style="width: 80px;">任务ID</th>
              <th style="width: 150px;">任务名称</th>
              <th style="width: 170px;">开始时间</th>
              <th style="width: 170px;">结束时间</th>
              <th style="width: 100px;">耗时</th>
              <th style="width: 110px; text-align: right;">成功行数</th>
              <th style="width: 110px; text-align: right;">失败行数</th>
              <th style="width: 80px; text-align: right;">QPS</th>
              <th style="width: 100px; text-align: center;">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in recentLogs" :key="log.id">
              <td class="font-mono" style="color: var(--text-muted); padding-right: 16px;">#{{ log.taskId }}</td>
              <td style="font-weight: 500; width: 150px; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :title="log.taskName">{{ log.taskName || '-' }}</td>
              <td class="text-sm text-muted">{{ formatTime(log.startTime) }}</td>
              <td class="text-sm text-muted">{{ formatTime(log.endTime) }}</td>
              <td><span class="duration-tag">{{ formatDuration(log.startTime, log.endTime) }}</span></td>
              <td class="font-mono text-right text-success" style="font-weight: 600;">{{ formatNumber(log.successRows || 0) }}</td>
              <td class="font-mono text-right" :class="log.failedRows > 0 ? 'text-danger' : 'text-muted'" style="font-weight: 600;">{{ log.failedRows || 0 }}</td>
              <td class="font-mono text-right"><span class="qps-badge">{{ (log.qps || 0).toFixed(0) }}</span></td>
              <td style="text-align: center;">
                <span class="badge status-badge"
                      :class="logStatusClass(log.status)"
                      :style="log.status === 'FAILED' || log.errorMsg ? 'cursor: pointer;' : ''"
                      @click="onStatusClick(log)">
                  <span v-if="log.status === 'RUNNING'" class="pulse-dot" style="background: var(--info);"></span>
                  {{ statusLabel(log.status) }}
                </span>
              </td>
            </tr>
            <tr v-if="!recentLogs.length">
              <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 40px;">暂无同步记录</td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination v-model="logPage" v-model:pageSize="logPageSize" :total="logTotal" @change="loadLogs" />
    </div>

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
import { ref, onMounted, onUnmounted } from 'vue'
import { monitorApi } from '../api'
import Pagination from '../components/Pagination.vue'
import SvgIcon from '../components/SvgIcon.vue'

const stats = ref({})
const recentLogs = ref([])
const progressList = ref([])
const currentQps = ref('0')
const logPage = ref(1)
const logPageSize = ref(10)
const logTotal = ref(0)
const errorModal = ref({ show: false, message: '', taskId: '', logId: '', time: '' })
let wsClient = null
let refreshTimer = null

const loadDashboard = async () => {
  try {
    const res = await monitorApi.dashboard()
    stats.value = res.data || {}
  } catch (e) {
    console.error('Dashboard load error:', e)
  }
}

const loadLogs = async () => {
  try {
    const res = await monitorApi.logs({ page: logPage.value, size: logPageSize.value })
    recentLogs.value = res.data?.records || []
    logTotal.value = res.data?.total || 0
  } catch (e) {
    console.error('Logs load error:', e)
  }
}

const loadData = async () => {
  await Promise.all([loadDashboard(), loadLogs()])
}

const connectWebSocket = () => {
  try {
    // Try SockJS + STOMP for real-time progress
    if (typeof SockJS !== 'undefined' && typeof Stomp !== 'undefined') {
      const socket = new SockJS('/ws/sync-progress')
      wsClient = Stomp.over(socket)
      wsClient.debug = null
      wsClient.connect({}, () => {
        wsClient.subscribe('/topic/sync-progress', (msg) => {
          const data = JSON.parse(msg.body)
          updateProgress(data)
        })
      }, (error) => {
        console.error('WebSocket error, reconnecting...', error)
        setTimeout(connectWebSocket, 5000)
      })
      
      socket.onclose = () => {
        console.warn('WebSocket closed. Reconnecting in 5s...')
        setTimeout(connectWebSocket, 5000)
      }
    }
  } catch (e) {
    console.log('WebSocket not available, using polling')
  }
}

const updateProgress = (data) => {
  const idx = progressList.value.findIndex(p => p.taskId === data.taskId)
  if (data.status === 'RUNNING') {
    if (idx >= 0) {
      progressList.value[idx] = data
    } else {
      progressList.value.push(data)
    }
    currentQps.value = (data.qps || 0).toFixed(0)
  } else {
    if (idx >= 0) progressList.value.splice(idx, 1)
    loadData()
  }
}

const formatNumber = (n) => {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const formatTime = (t) => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

const formatDuration = (start, end) => {
  if (!start || !end) return '-'
  const diff = new Date(end) - new Date(start)
  if (diff < 0) return '0s'
  const s = Math.floor(diff / 1000)
  if (s < 60) return s + 's'
  const m = Math.floor(s / 60)
  return `${m}m ${s % 60}s`
}

const statusLabel = (status) => {
  return { RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败' }[status] || status
}

const logStatusClass = (status) => {
  return { RUNNING: 'badge-running', SUCCESS: 'badge-success', FAILED: 'badge-failed' }[status] || 'badge-idle'
}

const onStatusClick = (log) => {
  if (log.status === 'FAILED' || log.errorMsg) {
    errorModal.value = {
      show: true,
      message: log.errorMsg || '无详细错误信息',
      taskId: log.taskId,
      logId: log.id,
      time: log.startTime
    }
  }
}

const copyError = async () => {
  try {
    await navigator.clipboard.writeText(errorModal.value.message)
  } catch (e) {
    console.error('Copy failed:', e)
  }
}

onMounted(() => {
  loadData()
  connectWebSocket()
  refreshTimer = setInterval(loadData, 10000) // 每10秒刷新
})

onUnmounted(() => {
  if (wsClient) try { wsClient.disconnect() } catch (e) {}
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.status-badge {
  transition: all 0.15s;
}
.status-badge[style*="cursor: pointer"]:hover {
  filter: brightness(0.9);
  transform: scale(1.05);
}
.text-right { text-align: right; }
.duration-tag {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-hover);
  padding: 2px 6px;
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
.error-detail-bar {
  display: flex;
  gap: 8px;
  padding: 12px 24px;
  background: var(--bg-main);
  border-bottom: 1px solid var(--border-light);
}
.error-detail-tag {
  font-size: 12px;
  padding: 2px 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text-muted);
}
.error-detail-content {
  padding: 16px 24px;
  max-height: 400px;
  overflow: auto;
}
.error-detail-content pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  color: var(--danger);
  margin: 0;
  line-height: 1.6;
}
</style>
