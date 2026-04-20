import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// Response interceptor
api.interceptors.response.use(
  res => res.data,
  err => {
    console.error('API Error:', err)
    return Promise.reject(err)
  }
)

// ==================== DataSource API ====================
export const dsApi = {
  list: () => api.get('/datasource'),
  listPage: (params) => api.get('/datasource/page', { params }),
  getById: (id) => api.get(`/datasource/${id}`),
  create: (data) => api.post('/datasource', data),
  update: (id, data) => api.put(`/datasource/${id}`, data),
  delete: (id) => api.delete(`/datasource/${id}`),
  ping: (id) => api.post(`/datasource/${id}/ping`),
  test: (data) => api.post('/datasource/test', data)
}

// ==================== Metadata API ====================
export const metaApi = {
  listSchemas: (dsId) => api.get(`/metadata/${dsId}/schemas`),
  listTables: (dsId, schema) => api.get(`/metadata/${dsId}/tables`, { params: schema ? { schema } : {} }),
  listColumns: (dsId, table, schema) => api.get(`/metadata/${dsId}/tables/${table}/columns`, { params: schema ? { schema } : {} }),
  listFunctions: (dsId, schema) => api.get(`/metadata/${dsId}/functions`, { params: schema ? { schema } : {} }),
  listProcedures: (dsId, schema) => api.get(`/metadata/${dsId}/procedures`, { params: schema ? { schema } : {} }),
  listViews: (dsId, schema) => api.get(`/metadata/${dsId}/views`, { params: schema ? { schema } : {} }),
  batchCreateTables: (data) => api.post('/metadata/batch-create-tables', data)
}

// ==================== Sync Task API ====================
export const taskApi = {
  list: () => api.get('/sync/tasks'),
  listPage: (params) => api.get('/sync/tasks/page', { params }),
  getById: (id) => api.get(`/sync/tasks/${id}`),
  getMappings: (id) => api.get(`/sync/tasks/${id}/mappings`),
  create: (data) => api.post('/sync/tasks', data),
  createBatch: (data) => api.post('/sync/tasks/batch', data),
  updateBatch: (id, data) => api.put(`/sync/tasks/batch/${id}`, data),
  update: (id, data) => api.put(`/sync/tasks/${id}`, data),
  delete: (id) => api.delete(`/sync/tasks/${id}`),
  execute: (id) => api.post(`/sync/tasks/${id}/execute`),
  stop: (id) => api.post(`/sync/tasks/${id}/stop`)
}

// ==================== Monitor API ====================
export const monitorApi = {
  dashboard: () => api.get('/monitor/dashboard'),
  logs: (params) => api.get('/sync/logs', { params })
}

export default api
