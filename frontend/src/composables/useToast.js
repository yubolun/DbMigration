import { ref } from 'vue'

// 全局 toast 实例引用
const toastRef = ref(null)

export function useToast() {
  return {
    success: (msg) => toastRef.value?.success(msg),
    error: (msg) => toastRef.value?.error(msg),
    info: (msg) => toastRef.value?.info(msg),
    warning: (msg) => toastRef.value?.warning(msg)
  }
}

export { toastRef }
