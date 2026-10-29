import { readonly, shallowRef } from 'vue'

export function useUpload() {
  const progress = shallowRef(0)
  const errorMessage = shallowRef('')

  function setProgress(value: number) {
    progress.value = Math.min(100, Math.max(0, value))
  }

  function setError(message: string) {
    errorMessage.value = message
  }

  function reset() {
    progress.value = 0
    errorMessage.value = ''
  }

  return {
    errorMessage: readonly(errorMessage),
    progress: readonly(progress),
    reset,
    setError,
    setProgress,
  }
}
