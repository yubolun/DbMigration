<template>
  <div class="custom-select" :class="{ open: isOpen, disabled }" ref="selectRef">
    <div class="custom-select-trigger" @click="toggle">
      <span class="custom-select-value" :class="{ placeholder: !modelValue }">
        <slot name="selected" :value="modelValue" :label="selectedLabel">
          <DbIcon v-if="selectedOption && selectedOption.dbType" :type="selectedOption.dbType" :size="18" style="margin-right: 8px;" />
          <SvgIcon v-else-if="selectedOption && selectedOption.icon" :name="selectedOption.icon" :size="18" class="option-icon-inline" :style="{ color: selectedOption.color }" style="margin-right: 8px;" />
          {{ selectedLabel || placeholder }}
        </slot>
      </span>
      <svg class="custom-select-arrow" width="12" height="12" viewBox="0 0 12 12" fill="none">
        <path d="M2.5 4.5L6 8L9.5 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
    <Teleport to="body">
      <div v-if="isOpen" class="custom-select-overlay" @click="isOpen = false"></div>
      <div v-if="isOpen" class="custom-select-dropdown" :style="dropdownStyle" ref="dropdownRef">
        <div v-if="searchable" class="custom-select-search">
          <input
            ref="searchInput"
            class="custom-select-search-input"
            v-model="searchQuery"
            placeholder="搜索..."
            @click.stop
          />
        </div>
        <div class="custom-select-options">
          <div
            v-for="opt in filteredOptions"
            :key="opt.value"
            class="custom-select-option"
            :class="{ active: opt.value === modelValue }"
            @click="selectOption(opt)"
          >
            <slot name="option" :option="opt">
              <DbIcon v-if="opt.dbType" :type="opt.dbType" :size="22" />
              <span v-else-if="opt.icon" class="option-icon" :style="{ color: opt.color }"><SvgIcon :name="opt.icon" :size="18" /></span>
              <span class="option-label">{{ opt.label }}</span>
            </slot>
            <svg v-if="opt.value === modelValue" class="option-check" width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M3 7L6 10L11 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <div v-if="filteredOptions.length === 0" class="custom-select-empty">
            无匹配项
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import DbIcon from './DbIcon.vue'
import SvgIcon from './SvgIcon.vue'

const props = defineProps({
  modelValue: { default: null },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false },
  searchable: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isOpen = ref(false)
const searchQuery = ref('')
const selectRef = ref(null)
const dropdownRef = ref(null)
const searchInput = ref(null)
const dropdownStyle = ref({})

const selectedOption = computed(() => {
  return props.options.find(o => o.value === props.modelValue) || null
})

const selectedLabel = computed(() => {
  const opt = selectedOption.value
  return opt ? opt.label : ''
})

const filteredOptions = computed(() => {
  if (!searchQuery.value) return props.options
  const q = searchQuery.value.toLowerCase()
  return props.options.filter(o => o.label.toLowerCase().includes(q))
})

const toggle = () => {
  if (props.disabled) return
  isOpen.value = !isOpen.value
}

const selectOption = (opt) => {
  emit('update:modelValue', opt.value)
  emit('change', opt.value)
  isOpen.value = false
  searchQuery.value = ''
}

const updatePosition = () => {
  if (!selectRef.value) return
  const rect = selectRef.value.getBoundingClientRect()
  const spaceBelow = window.innerHeight - rect.bottom
  const openUp = spaceBelow < 260 && rect.top > 260

  dropdownStyle.value = {
    position: 'fixed',
    left: rect.left + 'px',
    width: rect.width + 'px',
    zIndex: 9999,
    ...(openUp
      ? { bottom: (window.innerHeight - rect.top + 4) + 'px' }
      : { top: (rect.bottom + 4) + 'px' })
  }
}

watch(isOpen, async (val) => {
  if (val) {
    updatePosition()
    await nextTick()
    if (props.searchable && searchInput.value) {
      searchInput.value.focus()
    }
  } else {
    searchQuery.value = ''
  }
})

onMounted(() => {
  window.addEventListener('scroll', () => { if (isOpen.value) updatePosition() }, true)
})
</script>

<style scoped>
.custom-select {
  position: relative;
  width: 100%;
}
.custom-select.disabled {
  opacity: 0.5;
  pointer-events: none;
}

.custom-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 14px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  cursor: pointer;
  font-size: 14px;
  font-family: inherit;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  color: var(--text-primary);
}
.custom-select-trigger:hover {
  border-color: var(--primary-light);
}
.custom-select.open .custom-select-trigger {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}

.custom-select-value {
  flex: 1;
  display: flex;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.custom-select-value.placeholder {
  color: var(--text-muted);
  font-weight: 400;
}
.option-icon-inline {
  margin-right: 8px;
  font-size: 16px;
  flex-shrink: 0;
}

.custom-select-arrow {
  color: var(--text-muted);
  flex-shrink: 0;
  transition: transform 0.2s ease;
  margin-left: 8px;
}
.custom-select.open .custom-select-arrow {
  transform: rotate(180deg);
  color: var(--primary);
}
</style>

<style>
/* Global styles for teleported dropdown */
.custom-select-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  z-index: 9998;
}

.custom-select-dropdown {
  background: white;
  border: 1px solid #E2E8F0;
  border-radius: 10px;
  box-shadow: 0 12px 36px rgba(0,0,0,0.1), 0 4px 12px rgba(0,0,0,0.06);
  overflow: hidden;
  animation: csDropIn 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes csDropIn {
  from { opacity: 0; transform: translateY(-6px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.custom-select-search {
  padding: 8px 10px;
  border-bottom: 1px solid #F1F5F9;
}
.custom-select-search-input {
  width: 100%;
  height: 34px;
  padding: 0 12px;
  border: 1.5px solid #E2E8F0;
  border-radius: 8px;
  font-size: 13px;
  font-family: inherit;
  outline: none;
  background: #F8FAFC;
  transition: all 0.2s ease;
}
.custom-select-search-input:focus {
  border-color: #6366F1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  background: white;
}

.custom-select-options {
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
}
.custom-select-options::-webkit-scrollbar { width: 4px; }
.custom-select-options::-webkit-scrollbar-thumb { background: #CBD5E1; border-radius: 2px; }

.custom-select-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  transition: all 0.12s ease;
}
.custom-select-option:hover {
  background: #F1F5F9;
}
.custom-select-option.active {
  background: rgba(99, 102, 241, 0.08);
  color: #6366F1;
  font-weight: 600;
}

.option-icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
}
.option-label {
  flex: 1;
}
.option-check {
  color: #6366F1;
  flex-shrink: 0;
}

.custom-select-empty {
  padding: 20px;
  text-align: center;
  color: #94A3B8;
  font-size: 13px;
}
</style>
