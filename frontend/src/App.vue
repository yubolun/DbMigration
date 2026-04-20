<template>
  <div class="app-layout" v-if="showLayout">
    <AppLayout />
  </div>
  <router-view v-else />
  <Toast ref="toastInstance" />
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from './components/AppLayout.vue'
import Toast from './components/Toast.vue'
import { toastRef } from './composables/useToast'

const route = useRoute()
const showLayout = computed(() => true)

const toastInstance = ref(null)
onMounted(() => {
  toastRef.value = toastInstance.value
})
</script>
