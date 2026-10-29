import { TASK_STATUS_LABELS } from '../constants/taskStatus'
import type { TaskStatus } from '../types/task'

export function formatTaskStatus(status: TaskStatus) {
  return TASK_STATUS_LABELS[status]
}

export function formatPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
