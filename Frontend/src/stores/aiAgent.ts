import { computed, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { mockAIAuditResults } from '../mocks/aiAgent.mock'

export const useAIAgentStore = defineStore('aiAgent', () => {
  const auditResults = shallowRef([...mockAIAuditResults])

  const riskyResults = computed(() =>
    auditResults.value.filter((result) => result.riskLevel !== 'low'),
  )

  return {
    auditResults,
    riskyResults,
  }
})
