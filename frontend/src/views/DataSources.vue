<template>
  <div>
    <div class="page-header flex justify-between items-center">
      <div>
        <h1 class="page-title">数据源管理</h1>
        <p class="page-subtitle">管理所有异构数据库连接</p>
      </div>
      <button class="btn btn-primary" @click="openCreate"><SvgIcon name="plus" :size="15" /> 添加数据源</button>
    </div>

    <!-- 筛选栏 -->
    <div class="card mb-4 filter-bar" @keyup.enter="loadList">
      <div class="filter-row">
        <div class="filter-item">
          <label class="filter-label">数据源名称</label>
          <input class="form-input filter-input" v-model="searchName" placeholder="模糊搜索..." style="width: 180px;" />
        </div>
        <div class="filter-item">
          <label class="filter-label">用户名</label>
          <input class="form-input filter-input" v-model="searchUsername" placeholder="模糊搜索..." style="width: 140px;" />
        </div>
        <div class="filter-item">
          <label class="filter-label">数据库名</label>
          <input class="form-input filter-input" v-model="searchDbName" placeholder="模糊搜索..." style="width: 140px;" />
        </div>
        <div class="filter-item" style="min-width: 180px;">
          <label class="filter-label">数据库类型</label>
          <CustomSelect v-model="searchDbType" :options="dbTypeFilterOptions" placeholder="全部类型" />
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" @click="loadList" style="padding: 8px 20px; font-size: 14px;"><SvgIcon name="search" :size="14" /> 查询</button>
          <button class="btn btn-outline" @click="resetSearch" style="padding: 8px 20px; font-size: 14px;"><SvgIcon name="rotate-ccw" :size="14" /> 重置</button>
        </div>
      </div>
    </div>

    <div class="card-grid" v-if="list.length">
      <DsCard
        v-for="ds in list"
        :key="ds.id"
        :ds="ds"
        :pinging="pingingId === ds.id"
        @ping="handlePing"
        @edit="openEdit"
        @copy="openCopy"
        @delete="handleDelete"
      />
    </div>

    <div class="empty-state" v-else>
      <div class="empty-icon"><SvgIcon name="database" :size="48" /></div>
      <div class="empty-text">{{ dsTotal > 0 ? '没有匹配的数据源' : '暂无数据源，点击右上角添加' }}</div>
      <button v-if="dsTotal" class="btn btn-outline" style="margin-top: 12px;" @click="resetSearch">重置筛选</button>
    </div>

    <Pagination v-model="dsPage" v-model:pageSize="dsPageSize" :total="dsTotal" @change="loadList" />

    <!-- 新增 / 编辑模态框 -->
    <Modal v-if="showModal" :title="editing ? '编辑数据源' : '添加数据源'" @close="showModal = false">
      <div class="form-group">
        <label class="form-label">数据源名称</label>
        <input class="form-input" v-model="form.name" placeholder="例: 生产库-MySQL" />
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">数据库类型</label>
          <CustomSelect v-model="form.dbType" :options="dbTypeSelectOptions" placeholder="请选择" />
        </div>
        <div class="form-group">
          <label class="form-label">端口</label>
          <input class="form-input" v-model.number="form.port" type="number" :placeholder="defaultPort" />
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">主机地址</label>
        <input class="form-input" v-model="form.host" placeholder="例: 192.168.1.100" />
      </div>
      <div class="form-group">
        <label class="form-label">数据库名 / SID / 服务名</label>
        <input class="form-input" v-model="form.dbName" placeholder="例: mydb" />
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input class="form-input" v-model="form.username" placeholder="用户名" />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input class="form-input" v-model="form.password" type="password" placeholder="密码" />
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">额外连接参数（可选）</label>
        <input class="form-input" v-model="form.extraParams" placeholder="例: useSSL=false&serverTimezone=Asia/Shanghai" />
      </div>
      <template #footer>
        <button class="btn btn-outline" @click="showModal = false">取消</button>
        <button class="btn btn-outline" @click="handleTestConnection" :disabled="testing" style="margin-left: auto;">
          <SvgIcon name="link" :size="14" /> {{ testing ? '测试中...' : '测试连接' }}
        </button>
        <button class="btn btn-primary" @click="handleSubmit" :disabled="submitting">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </template>
    </Modal>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { dsApi } from '../api'
import DsCard from '../components/DsCard.vue'
import Modal from '../components/Modal.vue'
import CustomSelect from '../components/CustomSelect.vue'
import SvgIcon from '../components/SvgIcon.vue'
import Pagination from '../components/Pagination.vue'
import { useToast } from '../composables/useToast'

const toast = useToast()
const list = ref([])
const showModal = ref(false)
const editing = ref(false)
const submitting = ref(false)
const pingingId = ref(null)
const searchName = ref('')
const searchUsername = ref('')
const searchDbName = ref('')
const searchDbType = ref('')
const dsPage = ref(1)
const dsPageSize = ref(10)
const dsTotal = ref(0)
const testing = ref(false)

const dbTypes = [
  { value: 'MYSQL', label: 'MySQL', port: 3306 },
  { value: 'ORACLE', label: 'Oracle', port: 1521 },
  { value: 'POSTGRESQL', label: 'PostgreSQL', port: 5432 },
  { value: 'DM', label: '达梦8', port: 5236 },
  { value: 'GAUSSDB', label: 'GaussDB', port: 8000 },
  { value: 'OCEANBASE', label: 'OceanBase', port: 2881 }
]

const dbTypeSelectOptions = dbTypes.map(t => ({
  value: t.value,
  label: t.label,
  dbType: t.value
}))

// 筛选栏的类型选项（多一个"全部"）
const dbTypeFilterOptions = [
  { value: '', label: '全部类型' },
  ...dbTypes.map(t => ({ value: t.value, label: t.label, dbType: t.value }))
]

const resetSearch = () => {
  searchName.value = ''
  searchUsername.value = ''
  searchDbName.value = ''
  searchDbType.value = ''
  dsPage.value = 1
  loadList()
}

const emptyForm = () => ({
  id: null, name: '', dbType: '', host: '', port: null,
  dbName: '', username: '', password: '', extraParams: ''
})
const form = ref(emptyForm())

const defaultPort = computed(() => {
  const t = dbTypes.find(d => d.value === form.value.dbType)
  return t ? String(t.port) : '端口'
})

const loadList = async () => {
  try {
    const params = { page: dsPage.value, size: dsPageSize.value }
    if (searchName.value.trim()) params.name = searchName.value.trim()
    if (searchUsername.value.trim()) params.username = searchUsername.value.trim()
    if (searchDbName.value.trim()) params.dbName = searchDbName.value.trim()
    if (searchDbType.value) params.dbType = searchDbType.value
    const res = await dsApi.listPage(params)
    const pageData = res.data
    list.value = pageData.records || []
    dsTotal.value = pageData.total || 0
  } catch (e) { toast.error('加载失败') }
}

const openCreate = () => {
  form.value = emptyForm()
  editing.value = false
  showModal.value = true
}

const openEdit = (ds) => {
  form.value = { ...ds }
  editing.value = true
  showModal.value = true
}

const openCopy = (ds) => {
  form.value = { ...ds, id: null, password: '', name: ds.name + ' (副本)' }
  editing.value = false
  showModal.value = true
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    if (editing.value) {
      await dsApi.update(form.value.id, form.value)
      toast.success('更新成功')
    } else {
      await dsApi.create(form.value)
      toast.success('添加成功')
    }
    showModal.value = false
    await loadList()
  } catch (e) {
    toast.error('操作失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const handleTestConnection = async () => {
  if (!form.value.dbType || !form.value.host || !form.value.username) {
    toast.error('请填写数据库类型、主机地址和用户名')
    return
  }
  testing.value = true
  try {
    const res = await dsApi.test(form.value)
    if (res.data) {
      toast.success(res.message || '连接成功')
    } else {
      toast.error(res.message || '连接失败')
    }
  } catch (e) {
    toast.error('测试失败: ' + (e.message || ''))
  } finally {
    testing.value = false
  }
}

const handlePing = async (id) => {
  pingingId.value = id
  try {
    const res = await dsApi.ping(id)
    res.data ? toast.success(res.message) : toast.error(res.message)
    await loadList()
  } catch (e) {
    toast.error('连接失败')
  } finally {
    pingingId.value = null
  }
}

const handleDelete = async (id) => {
  if (!confirm('确定要删除此数据源吗？')) return
  try {
    await dsApi.delete(id)
    toast.success('删除成功')
    await loadList()
  } catch (e) {
    toast.error('删除失败')
  }
}

onMounted(loadList)
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
</style>
