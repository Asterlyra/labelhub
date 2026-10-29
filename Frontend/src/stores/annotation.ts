import { shallowRef } from 'vue'
import { defineStore } from 'pinia'

export const useAnnotationStore = defineStore('annotation', () => {
  const activeDraftId = shallowRef<string | null>(null)

  function setActiveDraft(draftId: string | null) {
    activeDraftId.value = draftId
  }

  return {
    activeDraftId,
    setActiveDraft,
  }
})
