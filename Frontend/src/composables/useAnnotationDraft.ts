import { readonly, shallowRef } from 'vue'
import type { AnnotationDraft } from '../types/annotation'

export function useAnnotationDraft() {
  const draft = shallowRef<AnnotationDraft | null>(null)

  function saveDraft(nextDraft: AnnotationDraft) {
    draft.value = nextDraft
  }

  function clearDraft() {
    draft.value = null
  }

  return {
    draft: readonly(draft),
    saveDraft,
    clearDraft,
  }
}
