import { shallowRef } from 'vue'
import { GetRespectTask } from '../utils/api'

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

function normalizeTaskDetail(response: any) {
  const payload = response?.data ?? response ?? {}
  const detail = payload?.data ?? payload

  if (detail && typeof detail === 'object' && !Array.isArray(detail)) {
    return detail as Record<string, unknown>
  }

  return null
}

export function useTaskRespectDetail() {
  const isTaskDetailDialogOpen = shallowRef(false)
  const isTaskDetailLoading = shallowRef(false)
  const taskDetailErrorMessage = shallowRef('')
  const taskDetailTitle = shallowRef('')
  const taskDetail = shallowRef<Record<string, unknown> | null>(null)
  const detailLoadingTaskId = shallowRef('')

  let detailRequestSeed = 0

  async function openTaskDetail(taskId: string | number, title = '', loadingTaskId = String(taskId)) {
    const requestSeed = (detailRequestSeed += 1)

    isTaskDetailDialogOpen.value = true
    isTaskDetailLoading.value = true
    taskDetailErrorMessage.value = ''
    taskDetailTitle.value = title
    taskDetail.value = null
    detailLoadingTaskId.value = loadingTaskId

    try {
      const response = await GetRespectTask(taskId)

      if (response?.code !== undefined && Number(response.code) !== 200) {
        throw new Error(response?.message ?? response?.msg ?? '任务详情加载失败')
      }

      if (requestSeed !== detailRequestSeed) {
        return
      }

      taskDetail.value = normalizeTaskDetail(response)

      if (!taskDetail.value) {
        taskDetailErrorMessage.value = '任务详情数据为空'
      }
    } catch (error: any) {
      if (requestSeed !== detailRequestSeed) {
        return
      }

      taskDetailErrorMessage.value = readErrorMessage(error, '任务详情加载失败')
    } finally {
      if (requestSeed === detailRequestSeed) {
        isTaskDetailLoading.value = false
        detailLoadingTaskId.value = ''
      }
    }
  }

  return {
    isTaskDetailDialogOpen,
    isTaskDetailLoading,
    taskDetailErrorMessage,
    taskDetailTitle,
    taskDetail,
    detailLoadingTaskId,
    openTaskDetail,
  }
}
