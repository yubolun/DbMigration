<template>
  <div>
    <div class="page-header flex justify-between items-center">
      <div>
        <h1 class="page-title">同步任务</h1>
        <p class="page-subtitle">管理和执行数据同步任务</p>
      </div>
      <router-link to="/tasks/create" class="btn btn-primary"><SvgIcon name="plus" :size="15" /> 新建任务</router-link>
    </div>

    <!-- 筛选栏 -->
    <div class="card mb-4 filter-bar" @keyup.enter="loadTasks">
      <div class="filter-row">
        <div class="filter-item">
          <label class="filter-label">任务名称</label>
          <input class="form-input filter-input" v-model="searchName" placeholder="模糊搜索..." style="width: 220px;" />
        </div>
        <div class="filter-item" style="min-width: 180px;">
          <label class="filter-label">任务类型</label>
          <CustomSelect v-model="searchType" :options="taskTypeFilterOptions" placeholder="全部类型" />
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" @click="loadTasks" style="padding: 8px 20px; font-size: 14px;"><SvgIcon name="search" :size="14" /> 查询</button>
          <button class="btn btn-outline" @click="resetSearch" style="padding: 8px 20px; font-size: 14px;"><SvgIcon name="rotate-ccw" :size="14" /> 重置</button>
        </div>
      </div>
    </div>

    <div class="table-wrapper" v-if="tasks.length">
      <table>
        <thead>
          <tr>
            <th style="width: 80px;">ID</th>
            <th style="min-width: 200px;">任务名称</th>
            <th style="width: 140px;">类型</th>
            <th style="min-width: 320px;">源库 → 目标库</th>
            <th style="width: 100px; text-align: center;">状态</th>
            <th style="width: 175px;">最后同步</th>
            <th style="width: 140px; text-align: center;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in tasks" :key="task.id">
            <td class="font-mono text-left" style="color: var(--text-muted);">#{{ task.id }}</td>
            <td class="text-left" style="font-weight: 500;">{{ task.taskName }}</td>
            <td class="text-left">
              <span class="badge" :class="taskTypeClass(task.taskType)">
                {{ taskTypeIcon(task.taskType) }} {{ taskTypeLabel(task.taskType) }}
              </span>
            </td>
            <td class="text-sm">
              <span class="ds-tag"><DbIcon :type="getDsType(task.sourceDsId)" :size="16" /> {{ getDsLabel(task.sourceDsId) }}</span>
              <span style="margin: 0 6px; color: var(--text-muted);">→</span>
              <span class="ds-tag"><DbIcon :type="getDsType(task.targetDsId)" :size="16" /> {{ getDsLabel(task.targetDsId) }}</span>
            </td>
            <td style="text-align: center;">
              <span class="badge" :class="statusClass(task.status)">
                <span v-if="task.status === 'RUNNING'" class="pulse-dot" style="background:var(--info);"></span>
                {{ statusLabel(task.status) }}
              </span>
            </td>
            <td class="text-sm text-muted" style="font-family: var(--font-mono);">{{ formatTime(task.lastSyncTime) }}</td>
            <td>
              <div class="flex gap-2">
                <router-link :to="`/tasks/edit/${task.id}`" class="btn btn-sm btn-outline"
                        :class="{ disabled: task.status === 'RUNNING' }">
                  <SvgIcon name="edit" :size="13" /> 编辑
                </router-link>
                <button class="btn btn-sm btn-primary" @click="executeTask(task.id)"
                        :disabled="task.status === 'RUNNING'">
                  <SvgIcon name="play" :size="13" /> 执行
                </button>
                <button class="btn btn-sm btn-outline" v-if="task.status === 'RUNNING'"
                        @click="stopTask(task.id)">
                  <SvgIcon name="stop" :size="13" /> 停止
                </button>
                <button class="btn btn-sm btn-ghost text-danger" @click="deleteTask(task)"
                        :disabled="task.status === 'RUNNING'">
                  <SvgIcon name="trash" :size="13" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="empty-state" v-else>
      <div class="empty-icon"><SvgIcon name="refresh" :size="48" /></div>
      <div class="empty-text">{{ taskTotal > 0 ? '没有匹配的任务' : '暂无同步任务' }}</div>
      <router-link v-if="!taskTotal" to="/tasks/create" class="btn btn-primary" style="margin-top: 16px;">创建第一个任务</router-link>
      <button v-else class="btn btn-outline" style="margin-top: 12px;" @click="resetSearch">重置筛选</button>
    </div>

    <Pagination v-model="taskPage" v-model:pageSize="taskPageSize" :total="taskTotal" @change="loadTasks" />

    <!-- 删除确认弹窗 -->
    <ConfirmDialog
      :visible="deleteConfirm.show"
      title="删除任务"
      :message="`确定要删除任务「${deleteConfirm.taskName}」吗？此操作不可撤销。`"
      confirmText="删除"
      type="danger"
      @confirm="confirmDelete"
      @cancel="deleteConfirm.show = false"
    />

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { taskApi, dsApi } from '../api'
import { useToast } from '../composables/useToast'
import DbIcon from '../components/DbIcon.vue'
import CustomSelect from '../components/CustomSelect.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import Pagination from '../components/Pagination.vue'
import SvgIcon from '../components/SvgIcon.vue'

const toast = useToast()
const tasks = ref([])
const dataSources = ref([])
const searchName = ref('')
const searchType = ref('')
const taskPage = ref(1)
const taskPageSize = ref(10)
const taskTotal = ref(0)
let refreshTimer = null

// 类型筛选选项
const taskTypeFilterOptions = [
  { value: '', label: '全部类型' },
  { value: 'FULL_DATA', label: '全库数据' },
  { value: 'FULL_SCHEMA', label: '全库结构' },
  { value: 'SELECTIVE', label: '按需同步' },
  { value: 'DATA_ONLY', label: '纯数据' }
]

const resetSearch = () => {
  searchName.value = ''
  searchType.value = ''
  taskPage.value = 1
  loadTasks()
}

const loadTasks = async () => {
  try {
    const params = { page: taskPage.value, size: taskPageSize.value }
    if (searchName.value.trim()) params.taskName = searchName.value.trim()
    if (searchType.value) params.taskType = searchType.value
    const res = await taskApi.listPage(params)
    const pageData = res.data
    tasks.value = pageData.records || []
    taskTotal.value = pageData.total || 0
  } catch (e) { toast.error('加载失败') }
}

const loadDs = async () => {
  try {
    const res = await dsApi.list()
    dataSources.value = res.data || []
  } catch (e) { /* ignore */ }
}

const executeTask = async (id) => {
  try {
    await taskApi.execute(id)
    toast.success('任务已提交执行')
    setTimeout(loadTasks, 1000)
  } catch (e) { toast.error('执行失败') }
}

const stopTask = async (id) => {
  try {
    await taskApi.stop(id)
    toast.info('已发送停止请求')
    setTimeout(loadTasks, 2000)
  } catch (e) { toast.error('停止失败') }
}

const deleteConfirm = reactive({ show: false, id: null, taskName: '' })

const deleteTask = (task) => {
  deleteConfirm.id = task.id
  deleteConfirm.taskName = task.taskName
  deleteConfirm.show = true
}

const confirmDelete = async () => {
  deleteConfirm.show = false
  try {
    await taskApi.delete(deleteConfirm.id)
    toast.success('已删除')
    await loadTasks()
  } catch (e) { toast.error('删除失败') }
}

// ---- 数据源名称解析 ----
const getDsLabel = (dsId) => {
  const ds = dataSources.value.find(d => d.id === dsId)
  return ds ? `${ds.name}` : `DS#${dsId}`
}
const getDsType = (dsId) => {
  const ds = dataSources.value.find(d => d.id === dsId)
  return ds ? ds.dbType : ''
}

// ---- 状态/类型映射 ----
const statusClass = (s) => ({
  IDLE: 'badge-idle', RUNNING: 'badge-running',
  SUCCESS: 'badge-success', FAILED: 'badge-failed'
})[s] || 'badge-idle'

const statusLabel = (s) => ({
  IDLE: '待执行', RUNNING: '运行中',
  SUCCESS: '成功', FAILED: '失败'
})[s] || s

const taskTypeLabel = (t) => ({
  FULL_DATA: '全库数据', FULL_SCHEMA: '全库结构',
  SELECTIVE: '按需', DATA_ONLY: '纯数据'
})[t] || '按需'

const taskTypeIcon = (t) => ({
  FULL_DATA: '▣', FULL_SCHEMA: '▧',
  SELECTIVE: '◎', DATA_ONLY: '⇄'
})[t] || '◎'

const taskTypeClass = (t) => ({
  FULL_DATA: 'badge-fulldata', FULL_SCHEMA: 'badge-fullschema',
  SELECTIVE: 'badge-idle', DATA_ONLY: 'badge-dataonly'
})[t] || 'badge-idle'

const formatTime = (t) => t ? t.replace('T', ' ').substring(0, 19) : '-'

onMounted(async () => {
  await Promise.all([loadTasks(), loadDs()])
  refreshTimer = setInterval(loadTasks, 5000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
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


.ds-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  background: linear-gradient(135deg, #F8FAFC, #F1F5F9);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: var(--transition);
}
.ds-tag:hover {
  border-color: var(--primary-light);
  background: var(--primary-bg);
  color: var(--primary);
}
.badge-fulldata {
  background: linear-gradient(135deg, #DBEAFE, #EEF2FF);
  color: #3730A3;
  border: 1px solid rgba(99, 102, 241, 0.15);
}
.badge-fullschema {
  background: linear-gradient(135deg, #FEF3C7, #FFFBEB);
  color: #92400E;
  border: 1px solid rgba(245, 158, 11, 0.15);
}
.badge-dataonly {
  background: linear-gradient(135deg, #CCFBF1, #F0FDFA);
  color: #115E59;
  border: 1px solid rgba(20, 184, 166, 0.15);
}
</style>
