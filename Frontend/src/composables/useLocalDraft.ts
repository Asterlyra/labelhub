import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'

export interface LocalDraftController<T> {
  draft: Ref<T>
  hasDraft: Ref<boolean>
  restore: () => boolean
  save: () => void
  clear: () => void
}

function canUseStorage() {
  return typeof window !== 'undefined' && Boolean(window.localStorage)
}

export function readLocalDraft<T>(key: string, fallback: T): T | null {
  if (!canUseStorage()) {
    return null
  }

  const rawValue = window.localStorage.getItem(key)

  if (!rawValue) {
    return null
  }

  try {
    return JSON.parse(rawValue) as T
  } catch {
    window.localStorage.removeItem(key)
    return fallback
  }
}

export function useLocalDraft<T extends object>(key: string, initialValue: T): LocalDraftController<T> {
  const draft = ref({ ...initialValue }) as Ref<T>
  const hasDraft = ref(false)
  let isRestoring = false

  const restore = () => {
    const savedValue = readLocalDraft<T>(key, initialValue)

    if (!savedValue) {
      hasDraft.value = false
      return false
    }

    isRestoring = true
    draft.value = { ...initialValue, ...savedValue }
    isRestoring = false
    hasDraft.value = true
    return true
  }

  const save = () => {
    if (!canUseStorage()) {
      return
    }

    window.localStorage.setItem(key, JSON.stringify(draft.value))
    hasDraft.value = true
  }

  const clear = () => {
    if (canUseStorage()) {
      window.localStorage.removeItem(key)
    }

    hasDraft.value = false
  }

  onMounted(() => {
    restore()
  })

  onBeforeUnmount(() => {
    if (!isRestoring) {
      save()
    }
  })

  return { draft, hasDraft, restore, save, clear }
}

export function createStorageKey(scope: string, userId: string | number, role: string) {
  return `labelhub:ui:${scope}:${String(userId)}:${role}`
}

export function writeLocalDraft<T>(key: string, value: T) {
  if (canUseStorage()) {
    window.localStorage.setItem(key, JSON.stringify(value))
  }
}

export function removeLocalDraft(key: string) {
  if (canUseStorage()) {
    window.localStorage.removeItem(key)
  }
}

export function watchDraft<T extends object>(draft: Ref<T>, save: () => void) {
  watch(draft, save, { deep: true })
}
