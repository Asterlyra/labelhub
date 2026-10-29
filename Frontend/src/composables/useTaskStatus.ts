import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { TASK_STATUS_DEFINITIONS } from '../constants/taskStatus'
import type { TaskStatus } from '../types/task'

export function useTaskStatus(status: MaybeRefOrGetter<TaskStatus>) {
  const definition = computed(() =>
    TASK_STATUS_DEFINITIONS.find((item) => item.status === toValue(status)),
  )

  const nextStatus = computed(() => definition.value?.nextStatus)

  return {
    definition,
    nextStatus,
  }
}
