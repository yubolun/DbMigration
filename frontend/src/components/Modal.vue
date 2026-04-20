<template>
  <div class="modal-overlay" @keydown.esc="$emit('close')">
    <div class="modal-content" :style="{ maxWidth: width }">
      <div class="modal-header">
        <h3 class="modal-title">{{ title }}</h3>
        <button class="modal-close" @click="$emit('close')">✕</button>
      </div>
      <slot />
      <div class="modal-footer" v-if="$slots.footer">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'

defineProps({
  title: { type: String, default: '' },
  width: { type: String, default: '560px' }
})
const emit = defineEmits(['close'])

const handleEsc = (e) => {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', handleEsc))
onUnmounted(() => document.removeEventListener('keydown', handleEsc))
</script>
