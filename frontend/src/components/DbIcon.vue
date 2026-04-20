<template>
  <span class="db-icon-wrap" :style="{ width: size + 'px', height: size + 'px' }">
    <img :src="iconSrc" :alt="type" :width="size" :height="size" class="db-icon-img" />
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: '' },
  size: { type: Number, default: 20 }
})

/*
 * 图片放在 frontend/public/db-icons/ 目录下
 * 文件名对应（小写）：
 *   mysql.png
 *   oracle.png
 *   postgresql.png
 *   dm.png
 *   gaussdb.png
 *   oceanbase.png
 *   default.png   (兜底)
 */
const iconSrc = computed(() => {
  const name = (props.type || '').toLowerCase()
  const supported = ['mysql', 'oracle', 'postgresql', 'dm', 'gaussdb', 'oceanbase']
  const file = supported.includes(name) ? name : 'default'
  return `/db-icons/${file}.png`
})
</script>

<style scoped>
.db-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  vertical-align: middle;
}
.db-icon-img {
  display: block;
  object-fit: contain;
  border-radius: 4px;
}
</style>
