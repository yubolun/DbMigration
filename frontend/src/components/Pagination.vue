<template>
  <div class="pagination" v-if="total > 0">
    <div class="page-info">
      共 <strong>{{ total }}</strong> 条
    </div>
    <div class="page-controls">
      <!-- 每页条数 -->
      <div class="page-size-wrap">
        <CustomSelect :modelValue="pageSize" @update:modelValue="changeSize" :options="sizeSelectOptions" style="width: 130px;" />
      </div>
      <!-- 翻页按钮 -->
      <div class="page-btns">
        <button class="page-btn" :disabled="modelValue <= 1" @click="go(1)" title="首页">«</button>
        <button class="page-btn" :disabled="modelValue <= 1" @click="go(modelValue - 1)" title="上一页">‹</button>
        <template v-for="p in visiblePages" :key="p">
          <span v-if="p === '...'" class="page-dots">…</span>
          <button v-else class="page-btn" :class="{ active: p === modelValue }" @click="go(p)">{{ p }}</button>
        </template>
        <button class="page-btn" :disabled="modelValue >= totalPages" @click="go(modelValue + 1)" title="下一页">›</button>
        <button class="page-btn" :disabled="modelValue >= totalPages" @click="go(totalPages)" title="末页">»</button>
      </div>
      <!-- 跳转 -->
      <div class="page-jump">
        <span>跳至</span>
        <input class="jump-input" type="number" min="1" :max="totalPages"
               v-model.number="jumpPage" @keyup.enter="doJump" />
        <span>页</span>
        <button class="btn btn-sm btn-outline jump-btn" @click="doJump">GO</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import CustomSelect from './CustomSelect.vue'

const props = defineProps({
  modelValue: { type: Number, default: 1 },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 10 }
})
const emit = defineEmits(['update:modelValue', 'update:pageSize', 'change'])

const sizeOptions = [10, 20, 50, 100]
const sizeSelectOptions = sizeOptions.map(s => ({ value: s, label: `${s} 条/页` }))
const jumpPage = ref(props.modelValue)

watch(() => props.modelValue, (v) => { jumpPage.value = v })

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const visiblePages = computed(() => {
  const pages = []
  const tp = totalPages.value
  const cur = props.modelValue
  if (tp <= 7) {
    for (let i = 1; i <= tp; i++) pages.push(i)
  } else {
    pages.push(1)
    if (cur > 3) pages.push('...')
    const start = Math.max(2, cur - 1)
    const end = Math.min(tp - 1, cur + 1)
    for (let i = start; i <= end; i++) pages.push(i)
    if (cur < tp - 2) pages.push('...')
    pages.push(tp)
  }
  return pages
})

const go = (p) => {
  const page = Math.max(1, Math.min(p, totalPages.value))
  if (page !== props.modelValue) {
    emit('update:modelValue', page)
    emit('change')
  }
}

const changeSize = (val) => {
  emit('update:pageSize', val)
  emit('update:modelValue', 1)
  emit('change')
}

const doJump = () => {
  if (jumpPage.value && jumpPage.value >= 1 && jumpPage.value <= totalPages.value) {
    go(jumpPage.value)
  }
}
</script>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0 4px;
  gap: 16px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.page-info {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
}
.page-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.page-size-wrap {
  display: flex;
  align-items: center;
}
.page-btns {
  display: flex;
  gap: 4px;
  align-items: center;
}
.page-btn {
  min-width: 32px;
  height: 32px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.page-btn:hover:not(:disabled):not(.active) {
  border-color: var(--primary);
  color: var(--primary);
}
.page-btn.active {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
  font-weight: 600;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-dots {
  min-width: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}
.page-jump {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
}
.jump-input {
  width: 52px;
  height: 30px;
  border: 1px solid var(--border);
  border-radius: 6px;
  text-align: center;
  font-size: 13px;
  background: var(--bg-card);
  color: var(--text);
  outline: none;
}
.jump-input:focus {
  border-color: var(--primary);
}
.jump-input::-webkit-inner-spin-button,
.jump-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
}
.jump-btn {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
}
</style>
