import { readonly, shallowRef } from 'vue'
import type { AIAuditResult } from '../types/aiAgent'

export function useAIAudit() {
  const result = shallowRef<AIAuditResult | null>(null)
  const isLoading = shallowRef(false)

  function setAuditResult(nextResult: AIAuditResult) {
    result.value = nextResult
  }

  function setLoading(value: boolean) {
    isLoading.value = value
  }

  return {
    isLoading: readonly(isLoading),
    result: readonly(result),
    setAuditResult,
    setLoading,
  }
}
