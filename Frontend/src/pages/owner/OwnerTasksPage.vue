<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import TaskRespectDetailDialog from '../../components/tasks/TaskRespectDetailDialog.vue'
import { useTaskRespectDetail } from '../../composables/useTaskRespectDetail'
import loadingAnimation from '../../icon/loading.gif'
import {
  API_BASE_URL,
  CreateOutputTask,
  DeleteTask,
  EndTask,
  GetMyTasks,
  GetOutpuTaskHistoryDetail,
  GetOutputTaskHistory,
  GetTasks,
  PauseTask,
  PostTask,
  PreviewOutputTask,
  ResumeTask,
} from '../../utils/api'

interface TaskListItem {
  id: string
  detailId: string
  deleteId: string
  name: string
  description: string
  taskCode: string
  ownerText: string
  datasetName: string
  templateName: string
  status: string
  statusType: string
  progressText: string
  quotaText: string
  progressPercent: number
  progressColor: string
  distributionStrategy: string
  createdAt: string
  createdDate: string
  tags: string[]
}

interface BackendTaskRecord {
  id?: string | number
  taskId?: string | number
  datasetItemId?: string | number
  title?: string
  name?: string
  taskName?: string
  description?: string
  desc?: string
  datasetId?: string | number
  datasetName?: string
  templateId?: string | number
  templateName?: string
  status?: string
  creatorId?: string | number
  creatorName?: string
  createBy?: string
  ownerName?: string
  nickname?: string
  distributionStrategy?: string
  strategyType?: string
  distributionConfig?: Record<string, unknown>
  quota?: string | number
  totalCount?: string | number
  itemCount?: string | number
  sampleCount?: string | number
  claimedCount?: string | number
  claimCount?: string | number
  completedCount?: string | number
  finishedCount?: string | number
  reviewedCount?: string | number
  submittedCount?: string | number
  progress?: Record<string, unknown>
  createTime?: string
  createdAt?: string
  updateTime?: string
  updatedAt?: string
  tags?: unknown
  category?: string
  scene?: string
  owner?: string
  reviewComment?: string
  [key: string]: unknown
}

type TaskListScope = 'all' | 'mine'
type TaskActionType = 'publish' | 'pause' | 'resume' | 'end'
type OutputFormat = 'json' | 'jsonl' | 'csv' | 'xlsx'

interface OutputPreviewFieldRecord {
  sourceName?: string
  sampleValue?: unknown
}

interface OutputPreviewRow {
  id: string
  sourceName: string
  targetName: string
  sampleValue: string
  enabled: boolean
}

interface OutputHistoryRecord {
  id?: string | number
  exportId?: string | number
  outputTaskId?: string | number
  taskId?: string | number
  format?: string
  outputFormat?: string
  status?: string
  statusName?: string
  displayStatus?: string
  displayStatusName?: string
  fileUrl?: string
  downloadUrl?: string
  url?: string
  createTime?: string
  createdAt?: string
  updateTime?: string
  updatedAt?: string
  finishTime?: string
  completedAt?: string
  [key: string]: unknown
}

interface OutputHistoryRow {
  rowKey: string
  id: string
  detailId: string
  format: string
  status: string
  statusType: string
  createTime: string
  updateTime: string
  fileUrl: string
  downloadUrl: string
  sortTime: number
}

interface OutputHistoryFieldRow {
  id: string
  sourceName: string
  targetName: string
  enabled: boolean
  enabledText: string
}

const router = useRouter()
const {
  isTaskDetailDialogOpen,
  isTaskDetailLoading,
  taskDetailErrorMessage,
  taskDetailTitle,
  taskDetail,
  detailLoadingTaskId,
  openTaskDetail,
} = useTaskRespectDetail()

const taskPageSize = 8
const taskStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'PAUSED', label: '已暂停' },
  { value: 'ENDED', label: '已结束' },
]
const taskScopeTabs: Array<{ key: TaskListScope; label: string }> = [
  { key: 'all', label: '任务列表' },
  { key: 'mine', label: '我的任务' },
]
const outputFormatOptions: OutputFormat[] = ['json', 'jsonl', 'csv', 'xlsx']

const tasks = shallowRef<TaskListItem[]>([])
const isLoadingTasks = shallowRef(false)
const taskErrorMessage = shallowRef('')
const activeTaskScope = shallowRef<TaskListScope>('all')
const taskKeyword = shallowRef('')
const taskStatusFilter = shallowRef('')
const currentPage = shallowRef(1)
const totalTasks = shallowRef(0)
const deletingTaskId = shallowRef('')
const actionTaskKey = shallowRef('')
const listAnimationKey = shallowRef(0)
const isBlockingLoadingOpen = shallowRef(false)
const isOutputDialogOpen = shallowRef(false)
const isPreviewingOutputTask = shallowRef(false)
const isCreatingOutputTask = shallowRef(false)
const selectedOutputTask = shallowRef<TaskListItem | null>(null)
const outputPreviewRows = shallowRef<OutputPreviewRow[]>([])
const outputFormat = shallowRef<OutputFormat>('json')
const outputPreviewErrorMessage = shallowRef('')
const isOutputHistoryDialogOpen = shallowRef(false)
const isLoadingOutputHistory = shallowRef(false)
const selectedOutputHistoryTask = shallowRef<TaskListItem | null>(null)
const outputHistoryRows = shallowRef<OutputHistoryRow[]>([])
const outputHistoryErrorMessage = shallowRef('')
const expandedOutputHistoryRowKeys = shallowRef<string[]>([])
const closingOutputHistoryRowKeys = shallowRef<string[]>([])
const loadingOutputHistoryDetailRowKey = shallowRef('')
const downloadingOutputHistoryRowKey = shallowRef('')
const outputHistoryDetailRows = shallowRef<Record<string, OutputHistoryFieldRow[]>>({})
const outputHistoryDetailErrorMap = shallowRef<Record<string, string>>({})

const emptyTaskText = computed(() => (activeTaskScope.value === 'mine' ? '暂无我的任务' : '暂无任务'))

const taskCountText = computed(() =>
  isLoadingTasks.value ? '加载中' : `共 ${totalTasks.value} 个任务`,
)

const shouldShowInlineLoading = computed(() => isLoadingTasks.value && tasks.value.length === 0)

const outputTaskTitle = computed(() => selectedOutputTask.value?.name ?? '导出任务')

const outputHistoryTitle = computed(() => selectedOutputHistoryTask.value?.name ?? '导出历史')

const taskDetailDialogTaskId = computed(() => {
  const id = taskDetail.value?.id ?? taskDetail.value?.taskId

  return typeof id === 'string' || typeof id === 'number' ? id : null
})

const loadingTextChars = '正在加载中.....'.split('')

let taskListRequestSeed = 0
let blockingLoadingSeed = 0
let outputPreviewRequestSeed = 0
let outputHistoryRequestSeed = 0
let outputHistoryDetailRequestSeed = 0
let outputHistoryCollapseTimer = 0

function getResponsePayload(response: any) {
  return response?.data ?? response
}

function pickTaskArray(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  const candidates = [
    payload,
    nestedPayload,
    payload?.records,
    payload?.list,
    payload?.items,
    payload?.content,
    nestedPayload?.records,
    nestedPayload?.list,
    nestedPayload?.items,
    nestedPayload?.content,
  ]

  return candidates.find(Array.isArray) ?? []
}

function pickOutputPreviewArray(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  const candidates = [
    payload,
    nestedPayload,
    payload?.fields,
    payload?.records,
    payload?.list,
    nestedPayload?.fields,
    nestedPayload?.records,
    nestedPayload?.list,
  ]

  return candidates.find(Array.isArray) ?? []
}

function pickOutputHistoryArray(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  const candidates = [
    payload,
    nestedPayload,
    payload?.records,
    payload?.list,
    payload?.items,
    payload?.content,
    nestedPayload?.records,
    nestedPayload?.list,
    nestedPayload?.items,
    nestedPayload?.content,
  ]

  return candidates.find(Array.isArray) ?? []
}

function pickPaginationTotal(response: any, fallback: number) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  const candidates = [
    payload?.total,
    payload?.totalCount,
    payload?.totalElements,
    payload?.count,
    nestedPayload?.total,
    nestedPayload?.claimedCount,
    nestedPayload?.totalCount,
    nestedPayload?.totalElements,
    nestedPayload?.count,
  ]

  const total = candidates.map((item) => Number(item)).find((item) => Number.isFinite(item))

  return total ?? fallback
}

function readString(value: unknown, fallback: string) {
  if (value === undefined || value === null) {
    return fallback
  }

  const text = String(value).trim()

  return text ? text : fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : fallback
}

function readBoolean(value: unknown, fallback = true) {
  if (typeof value === 'boolean') {
    return value
  }

  if (typeof value === 'number') {
    return value !== 0
  }

  if (typeof value === 'string') {
    const normalizedValue = value.trim().toLowerCase()

    if (['true', '1', 'yes', 'enabled', 'on', '启用'].includes(normalizedValue)) {
      return true
    }

    if (['false', '0', 'no', 'disabled', 'off', '禁用'].includes(normalizedValue)) {
      return false
    }
  }

  return fallback
}

function parseMaybeJsonRecord(value: unknown) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }

  if (typeof value === 'string') {
    try {
      const parsedValue = JSON.parse(value)

      return parsedValue && typeof parsedValue === 'object' && !Array.isArray(parsedValue)
        ? parsedValue as Record<string, unknown>
        : {}
    } catch {
      return {}
    }
  }

  return {}
}

function readMaybeArray(value: unknown) {
  if (Array.isArray(value)) {
    return value
  }

  if (typeof value === 'string') {
    try {
      const parsedValue = JSON.parse(value)

      return Array.isArray(parsedValue) ? parsedValue : []
    } catch {
      return []
    }
  }

  return []
}

function formatPreviewValue(value: unknown) {
  if (value === undefined || value === null) {
    return ''
  }

  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }

  return String(value)
}

function readTags(record: BackendTaskRecord) {
  if (Array.isArray(record.tags)) {
    return record.tags.filter((tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()))
  }

  return [record.scene, record.category, record.owner].filter(
    (tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()),
  )
}

function formatStatus(status: unknown) {
  const statusTextMap: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    PAUSED: '已暂停',
    REVIEWING: '审核中',
    ENDED: '已结束',
    END: '已结束',
    FINISHED: '已结束',
    COMPLETED: '已结束',
    UNASSIGNED: '未领取',
    CLAIMED: '已领取',
    SUBMITTED: '已提交',
    REJECTED: '已驳回',
    REVIEWED: '已审核',
  }
  const normalizedStatus = readString(status, 'UNKNOWN').toUpperCase()

  return statusTextMap[normalizedStatus] ?? normalizedStatus
}

function getStatusType(status: unknown) {
  const rawStatus = readString(status, 'default')
  const statusTypeMap: Record<string, string> = {
    已结束: 'ended',
    已完成: 'ended',
    草稿: 'draft',
    已发布: 'published',
    发布中: 'published',
    已暂停: 'paused',
    暂停中: 'paused',
    审核中: 'reviewing',
    已领取: 'claimed',
    已提交: 'submitted',
    已驳回: 'rejected',
    已审核: 'reviewed',
  }

  if (statusTypeMap[rawStatus]) {
    return statusTypeMap[rawStatus]
  }

  const normalizedStatus = rawStatus.toLowerCase()
  const aliasStatusMap: Record<string, string> = {
    end: 'ended',
    finished: 'ended',
    completed: 'ended',
  }
  const knownStatus = ['draft', 'published', 'paused', 'reviewing', 'ended', 'claimed', 'submitted', 'rejected', 'reviewed']

  return aliasStatusMap[normalizedStatus] ?? (knownStatus.includes(normalizedStatus) ? normalizedStatus : 'default')
}

function formatTaskCode(value: unknown) {
  const text = readString(value, '')
  const numberValue = Number(text)

  if (Number.isFinite(numberValue)) {
    return `T-${String(numberValue).padStart(4, '0')}`
  }

  return text ? `T-${text}` : 'T-0000'
}

function formatTaskDate(value: unknown) {
  const text = readString(value, '')

  if (!text) {
    return '暂无创建时间'
  }

  return text.includes('T') ? text.split('T')[0] : text
}

function formatOutputDateTime(value: unknown) {
  const text = readString(value, '')

  if (!text) {
    return '-'
  }

  return text.replace('T', ' ').replace(/\.\d+$/, '')
}

function getDateTimeValue(value: unknown) {
  const text = readString(value, '')

  if (!text) {
    return 0
  }

  const timeValue = new Date(text).getTime()

  return Number.isFinite(timeValue) ? timeValue : 0
}

function formatNumberText(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('en-US') : '-'
}

function formatOutputStatus(status: unknown, fallback = '-') {
  const statusTextMap: Record<string, string> = {
    PENDING: '等待中',
    CREATED: '已创建',
    RUNNING: '处理中',
    PROCESSING: '处理中',
    SUCCESS: '已完成',
    SUCCEEDED: '已完成',
    COMPLETED: '已完成',
    FINISHED: '已完成',
    FAILED: '失败',
    ERROR: '失败',
  }
  const normalizedStatus = readString(status, fallback).toUpperCase()

  return statusTextMap[normalizedStatus] ?? readString(status, fallback)
}

function getOutputStatusType(status: unknown) {
  const normalizedStatus = readString(status, '').toLowerCase()
  const statusTypeMap: Record<string, string> = {
    pending: 'pending',
    created: 'pending',
    running: 'processing',
    processing: 'processing',
    success: 'success',
    succeeded: 'success',
    completed: 'success',
    finished: 'success',
    failed: 'failed',
    error: 'failed',
    等待中: 'pending',
    已创建: 'pending',
    处理中: 'processing',
    已完成: 'success',
    成功: 'success',
    失败: 'failed',
  }

  return statusTypeMap[normalizedStatus] ?? 'default'
}

function formatDistributionStrategy(value: unknown) {
  const configValue = value && typeof value === 'object' ? (value as Record<string, unknown>) : null
  const rawValue = readString(configValue ? configValue.strategyType ?? configValue.type : value, '')
  const normalizedValue = rawValue.toUpperCase()
  const strategyTextMap: Record<string, string> = {
    FIRST_COME_FIRST_SERVED: '先到先得',
    ASSIGNED: '指派',
    MANUAL_ASSIGN: '指派',
    RANDOM: '随机分配',
    GRAB: '配额抢单',
    QUOTA_GRAB: '配额抢单',
  }

  return strategyTextMap[normalizedValue] ?? (rawValue || '-')
}

function getProgressColor(status: unknown) {
  const normalizedStatus = readString(status, '').toLowerCase()

  return ['ended', 'reviewed'].includes(normalizedStatus) ? '#22c55e' : '#3b82f6'
}

function readTaskProgress(record: BackendTaskRecord) {
  const progress = (record.progress && typeof record.progress === 'object' ? record.progress : {}) as Record<
    string,
    unknown
  >
  const totalCount = readNumber(
    progress.totalCount ?? record.totalCount ?? record.itemCount ?? record.sampleCount ?? record.quota,
    0,
  )
  const completedCount = readNumber(
    progress.claimedCount ??
      progress.claimCount ??
      record.claimedCount ??
      record.claimCount ??
      progress.reviewedCount ??
      progress.submittedCount ??
      record.reviewedCount ??
      record.submittedCount ??
      record.completedCount ??
      record.finishedCount,
    0,
  )
  const progressPercent = totalCount > 0 ? Math.min(100, Math.round((completedCount / totalCount) * 100)) : 0

  return {
    progressText: totalCount > 0 ? `${completedCount}/${totalCount}` : '暂无进度',
    quotaText: totalCount > 0 ? `${formatNumberText(completedCount)} / ${formatNumberText(totalCount)}` : '-',
    progressPercent,
  }
}

function normalizeTaskRecord(record: unknown, index: number): TaskListItem {
  const task = (record ?? {}) as BackendTaskRecord
  const rowId = String(task.id ?? task.taskId ?? `task-${index}`)
  const taskId = String(task.taskId ?? task.id ?? rowId)
  const progress = readTaskProgress(task)
  const createdAt = readString(task.createTime ?? task.createdAt ?? task.updateTime ?? task.updatedAt, '暂无创建时间')

  return {
    id: rowId,
    detailId: taskId,
    deleteId: taskId,
    taskCode: formatTaskCode(taskId),
    name: readString(
      task.title ?? task.name ?? task.taskName,
      activeTaskScope.value === 'mine' ? `任务数据项 ${rowId}` : `未命名任务 ${index + 1}`,
    ),
    ownerText: readString(
      task.creatorName ?? task.ownerName ?? task.nickname ?? task.owner ?? task.createBy ?? task.creatorId,
      '未知',
    ),
    description: readString(task.description ?? task.desc ?? task.reviewComment, '暂无任务描述'),
    datasetName: readString(
      task.datasetName ?? task.datasetId,
      task.datasetItemId ? `数据项 ${task.datasetItemId}` : '暂无数据集',
    ),
    templateName: readString(task.templateName ?? task.templateId, '暂无模板'),
    status: formatStatus(task.status),
    statusType: getStatusType(task.status),
    progressText: progress.progressText,
    quotaText: progress.quotaText,
    progressPercent: progress.progressPercent,
    progressColor: getProgressColor(task.status),
    distributionStrategy: formatDistributionStrategy(
      task.distributionStrategy ?? task.strategyType ?? task.distributionConfig,
    ),
    createdAt,
    createdDate: formatTaskDate(createdAt),
    tags: readTags(task),
  }
}

function normalizeTaskList(response: any) {
  return pickTaskArray(response).map((item, index) => normalizeTaskRecord(item, index))
}

function normalizeOutputPreviewRows(response: any): OutputPreviewRow[] {
  return pickOutputPreviewArray(response).map((item, index) => {
    const field = (item ?? {}) as OutputPreviewFieldRecord
    const sourceName = readString(field.sourceName, `字段 ${index + 1}`)

    return {
      id: `${sourceName}-${index}`,
      sourceName,
      targetName: sourceName,
      sampleValue: formatPreviewValue(field.sampleValue),
      enabled: true,
    }
  })
}

function normalizeOutputHistoryRows(response: any): OutputHistoryRow[] {
  return pickOutputHistoryArray(response)
    .map((item, index) => {
      const record = (item ?? {}) as OutputHistoryRecord
      const detailId = readString(record.id ?? record.exportId ?? record.outputTaskId, '')
      const id = detailId || `导出任务 ${index + 1}`
      const rawStatus = record.displayStatusName ?? record.statusName ?? record.displayStatus ?? record.status
      const createTime = record.createTime ?? record.createdAt
      const updateTime = record.finishTime ?? record.completedAt ?? record.updateTime ?? record.updatedAt
      const fileUrl = normalizeDownloadUrl(readString(record.fileUrl ?? record.url, ''))

      return {
        rowKey: `${id}-${index}`,
        id,
        detailId: detailId || id,
        format: readString(record.format ?? record.outputFormat, '-'),
        status: formatOutputStatus(rawStatus),
        statusType: getOutputStatusType(rawStatus),
        createTime: formatOutputDateTime(createTime),
        updateTime: formatOutputDateTime(updateTime),
        fileUrl,
        downloadUrl: normalizeDownloadUrl(readString(record.downloadUrl, '')),
        sortTime: getDateTimeValue(createTime),
      }
    })
    .sort((left, right) => right.sortTime - left.sortTime)
}

function normalizeDownloadUrl(url: string) {
  if (!url) {
    return ''
  }
  if (/^https?:\/\//i.test(url)) {
    return url
  }
  if (url.startsWith('/api/')) {
    const normalizedApiBaseUrl = API_BASE_URL.replace(/\/+$/, '')
    if (normalizedApiBaseUrl.endsWith('/api')) {
      return `${normalizedApiBaseUrl}${url.slice('/api'.length)}`
    }
    const apiBaseUrl = new URL(normalizedApiBaseUrl, window.location.href)
    return new URL(url, apiBaseUrl.origin).toString()
  }
  return url
}

function pickOutputHistoryDetailFields(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload
  const detailRecord = parseMaybeJsonRecord(nestedPayload)
  const configRecord = parseMaybeJsonRecord(detailRecord.config)

  return readMaybeArray(configRecord.fields ?? detailRecord.fields)
}

function normalizeOutputHistoryDetailRows(response: any): OutputHistoryFieldRow[] {
  return pickOutputHistoryDetailFields(response).map((item, index) => {
    const field = parseMaybeJsonRecord(item)
    const sourceName = readString(field.sourceName, `字段 ${index + 1}`)
    const targetName = readString(field.targetName, sourceName)
    const enabled = readBoolean(field.enabled, true)

    return {
      id: `${sourceName}-${targetName}-${index}`,
      sourceName,
      targetName,
      enabled,
      enabledText: enabled ? '启用' : '禁用',
    }
  })
}

function getOutputHistoryFileUrlFromDetail(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload
  const detailRecord = parseMaybeJsonRecord(nestedPayload)

  return normalizeDownloadUrl(readString(detailRecord.fileUrl ?? detailRecord.url, ''))
}

function updateOutputHistoryRowFileUrl(rowKey: string, fileUrl: string) {
  if (!fileUrl) {
    return
  }

  outputHistoryRows.value = outputHistoryRows.value.map((row) =>
    row.rowKey === rowKey ? { ...row, fileUrl } : row,
  )
}

async function resolveOutputHistoryFileUrl(row: OutputHistoryRow) {
  if (row.fileUrl) {
    return row.fileUrl
  }

  if (!row.detailId) {
    return ''
  }

  const response = await GetOutpuTaskHistoryDetail(row.detailId)
  const responseErrorMessage = getResponseErrorMessage(response, '导出历史详情加载失败')

  if (responseErrorMessage) {
    throw new Error(responseErrorMessage)
  }

  const fileUrl = getOutputHistoryFileUrlFromDetail(response)
  updateOutputHistoryRowFileUrl(row.rowKey, fileUrl)

  return fileUrl
}

function getOutputHistoryFileExtension(format: unknown) {
  const normalizedFormat = readString(format, 'json').toLowerCase()
  const supportedFormats = ['json', 'jsonl', 'csv', 'xlsx']

  return supportedFormats.includes(normalizedFormat) ? normalizedFormat : 'json'
}

function sanitizeDownloadFileName(value: string) {
  return value.replace(/[\\/:*?"<>|]/g, '_').trim()
}

function readContentDispositionFileName(value: string | null) {
  if (!value) {
    return ''
  }

  const encodedFileNameMatch = value.match(/filename\*=UTF-8''([^;]+)/i)

  if (encodedFileNameMatch?.[1]) {
    try {
      return decodeURIComponent(encodedFileNameMatch[1])
    } catch {
      return encodedFileNameMatch[1]
    }
  }

  const fileNameMatch = value.match(/filename="?([^";]+)"?/i)

  return fileNameMatch?.[1] ?? ''
}

function getOutputHistoryFileNameFromUrl(url: string) {
  try {
    const parsedUrl = new URL(url, window.location.href)
    const urlFileName = parsedUrl.pathname.split('/').filter(Boolean).pop() ?? ''

    return decodeURIComponent(urlFileName)
  } catch {
    return ''
  }
}

function buildOutputHistoryDownloadFileName(row: OutputHistoryRow, response?: Response) {
  const extension = getOutputHistoryFileExtension(row.format)
  const headerFileName = readContentDispositionFileName(response?.headers.get('content-disposition') ?? null)
  const urlFileName = getOutputHistoryFileNameFromUrl(row.fileUrl || row.downloadUrl)
  const fallbackFileName = `task-output-${sanitizeDownloadFileName(row.id || row.detailId || 'history')}.${extension}`
  const rawFileName = headerFileName || urlFileName || fallbackFileName
  const fileName = sanitizeDownloadFileName(rawFileName) || fallbackFileName

  return fileName.includes('.') ? fileName : `${fileName}.${extension}`
}

function triggerDownload(downloadUrl: string, fileName: string) {
  const downloadLink = document.createElement('a')
  downloadLink.href = downloadUrl
  downloadLink.download = fileName
  downloadLink.rel = 'noreferrer'
  downloadLink.style.display = 'none'
  document.body.appendChild(downloadLink)
  downloadLink.click()
  document.body.removeChild(downloadLink)
}

function triggerBlobDownload(blob: Blob, fileName: string) {
  const objectUrl = URL.createObjectURL(blob)

  try {
    triggerDownload(objectUrl, fileName)
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  }
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.message ?? fallback
}

function getResponseErrorMessage(response: any, fallback: string) {
  const code = Number(response?.code)

  if (!Number.isFinite(code) || code === 200) {
    return ''
  }

  return response?.message ?? response?.msg ?? fallback
}

function isDraftTask(task: TaskListItem) {
  return task.statusType === 'draft'
}

function isPublishedTask(task: TaskListItem) {
  return task.statusType === 'published'
}

function isPausedTask(task: TaskListItem) {
  return task.statusType === 'paused'
}

function isEndedTask(task: TaskListItem) {
  return task.statusType === 'ended'
}

function isTaskActionLoading(task: TaskListItem, action: TaskActionType) {
  return actionTaskKey.value === `${task.id}-${action}`
}

function getTaskActionConfirmText(task: TaskListItem, action: TaskActionType) {
  const actionTextMap: Record<TaskActionType, string> = {
    publish: `确认发布任务「${task.name}」吗？`,
    pause: `确认暂停任务「${task.name}」吗？`,
    resume: `确认恢复任务「${task.name}」吗？`,
    end: `确认结束任务「${task.name}」吗？`,
  }

  return actionTextMap[action]
}

function getTaskActionTitle(action: TaskActionType) {
  const actionTitleMap: Record<TaskActionType, string> = {
    publish: '发布任务',
    pause: '暂停任务',
    resume: '恢复任务',
    end: '结束任务',
  }

  return actionTitleMap[action]
}

function getTaskActionSuccessMessage(action: TaskActionType) {
  const actionMessageMap: Record<TaskActionType, string> = {
    publish: '发布任务成功',
    pause: '暂停任务成功',
    resume: '恢复任务成功',
    end: '结束任务成功',
  }

  return actionMessageMap[action]
}

function waitForLoadingDuration(duration = 720) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, duration)
  })
}

function openBlockingLoading() {
  blockingLoadingSeed += 1
  const seed = blockingLoadingSeed

  isBlockingLoadingOpen.value = true

  return {
    seed,
    openedAt: Date.now(),
  }
}

async function closeBlockingLoading(handle: { seed: number; openedAt: number }) {
  const elapsed = Date.now() - handle.openedAt

  if (elapsed < 720) {
    await waitForLoadingDuration(720 - elapsed)
  }

  if (handle.seed === blockingLoadingSeed) {
    isBlockingLoadingOpen.value = false
  }
}

async function loadTasks(pageNum = currentPage.value, showBlockingLoading = false) {
  const requestSeed = (taskListRequestSeed += 1)
  const requestScope = activeTaskScope.value
  const requestTasks = requestScope === 'mine' ? GetMyTasks : GetTasks
  const blockingLoadingHandle = showBlockingLoading ? openBlockingLoading() : null

  isLoadingTasks.value = true
  taskErrorMessage.value = ''

  try {
    const response = await requestTasks({
      pageNum,
      pageSize: taskPageSize,
      keyword: taskKeyword.value.trim() || undefined,
      status: taskStatusFilter.value || undefined,
    })
    const normalizedTasks = normalizeTaskList(response)

    if (requestSeed !== taskListRequestSeed || requestScope !== activeTaskScope.value) {
      return
    }

    tasks.value = normalizedTasks.slice(0, taskPageSize)
    totalTasks.value = pickPaginationTotal(response, normalizedTasks.length)
    currentPage.value = pageNum
    listAnimationKey.value += 1
  } catch (error: any) {
    if (requestSeed !== taskListRequestSeed) {
      return
    }

    tasks.value = []
    totalTasks.value = 0
    taskErrorMessage.value = readErrorMessage(error, '任务列表加载失败')
  } finally {
    if (blockingLoadingHandle) {
      await closeBlockingLoading(blockingLoadingHandle)
    }

    if (requestSeed === taskListRequestSeed) {
      isLoadingTasks.value = false
    }
  }
}

function handleTaskFilterSubmit() {
  currentPage.value = 1
  void loadTasks(1, true)
}

function handleTaskFilterReset() {
  taskKeyword.value = ''
  taskStatusFilter.value = ''
  handleTaskFilterSubmit()
}

function handleTaskScopeChange(scope: TaskListScope) {
  if (activeTaskScope.value === scope) {
    return
  }

  activeTaskScope.value = scope
  currentPage.value = 1
  tasks.value = []
  totalTasks.value = 0
  loadTasks(1, true)
}

function handlePageChange(pageNum: number) {
  loadTasks(pageNum, true)
}

function handleCreateTask() {
  router.push({ name: 'owner-task-detail' })
}

function handleViewTask(task: TaskListItem) {
  openTaskDetail(task.detailId, task.name, task.id)
}

function resetOutputTaskDialog() {
  outputPreviewRequestSeed += 1
  selectedOutputTask.value = null
  outputPreviewRows.value = []
  outputFormat.value = 'json'
  outputPreviewErrorMessage.value = ''
  isPreviewingOutputTask.value = false
  isCreatingOutputTask.value = false
}

function handleCloseOutputTaskDialog() {
  isOutputDialogOpen.value = false
  resetOutputTaskDialog()
}

function resetOutputHistoryDialog() {
  outputHistoryRequestSeed += 1
  outputHistoryDetailRequestSeed += 1
  window.clearTimeout(outputHistoryCollapseTimer)
  selectedOutputHistoryTask.value = null
  outputHistoryRows.value = []
  outputHistoryErrorMessage.value = ''
  expandedOutputHistoryRowKeys.value = []
  closingOutputHistoryRowKeys.value = []
  loadingOutputHistoryDetailRowKey.value = ''
  downloadingOutputHistoryRowKey.value = ''
  outputHistoryDetailRows.value = {}
  outputHistoryDetailErrorMap.value = {}
  isLoadingOutputHistory.value = false
}

function handleCloseOutputHistoryDialog() {
  isOutputHistoryDialogOpen.value = false
  resetOutputHistoryDialog()
}

function updateOutputTargetName(rowId: string, value: string) {
  outputPreviewRows.value = outputPreviewRows.value.map((row) =>
    row.id === rowId
      ? {
          ...row,
          targetName: value,
        }
      : row,
  )
}

function updateOutputEnabled(rowId: string, value: boolean) {
  outputPreviewRows.value = outputPreviewRows.value.map((row) =>
    row.id === rowId
      ? {
          ...row,
          enabled: value,
        }
      : row,
  )
}

function isOutputHistoryRowExpanded(row: OutputHistoryRow) {
  return expandedOutputHistoryRowKeys.value.includes(row.rowKey)
}

function isOutputHistoryRowClosing(row: OutputHistoryRow) {
  return closingOutputHistoryRowKeys.value.includes(row.rowKey)
}

function setOutputHistoryDetailRows(rowKey: string, rows: OutputHistoryFieldRow[]) {
  outputHistoryDetailRows.value = {
    ...outputHistoryDetailRows.value,
    [rowKey]: rows,
  }
}

function setOutputHistoryDetailError(rowKey: string, message: string) {
  outputHistoryDetailErrorMap.value = {
    ...outputHistoryDetailErrorMap.value,
    [rowKey]: message,
  }
}

function clearOutputHistoryDetailError(rowKey: string) {
  const nextErrorMap = { ...outputHistoryDetailErrorMap.value }
  delete nextErrorMap[rowKey]
  outputHistoryDetailErrorMap.value = nextErrorMap
}

async function handleToggleOutputHistoryDetail(row: OutputHistoryRow) {
  if (isOutputHistoryRowExpanded(row)) {
    window.clearTimeout(outputHistoryCollapseTimer)
    closingOutputHistoryRowKeys.value = [row.rowKey]
    outputHistoryCollapseTimer = window.setTimeout(() => {
      expandedOutputHistoryRowKeys.value = expandedOutputHistoryRowKeys.value.filter((rowKey) => rowKey !== row.rowKey)
      closingOutputHistoryRowKeys.value = []
    }, 320)
    return
  }

  window.clearTimeout(outputHistoryCollapseTimer)
  closingOutputHistoryRowKeys.value = []
  expandedOutputHistoryRowKeys.value = [row.rowKey]
  clearOutputHistoryDetailError(row.rowKey)

  if (outputHistoryDetailRows.value[row.rowKey]) {
    return
  }

  const requestSeed = (outputHistoryDetailRequestSeed += 1)
  loadingOutputHistoryDetailRowKey.value = row.rowKey

  try {
    const response = await GetOutpuTaskHistoryDetail(row.detailId)

    if (requestSeed !== outputHistoryDetailRequestSeed) {
      return
    }

    const responseErrorMessage = getResponseErrorMessage(response, '导出历史详情加载失败')

    if (responseErrorMessage) {
      setOutputHistoryDetailError(row.rowKey, responseErrorMessage)
      ElMessage.error(responseErrorMessage)
      return
    }

    setOutputHistoryDetailRows(row.rowKey, normalizeOutputHistoryDetailRows(response))
  } catch (error: any) {
    if (requestSeed !== outputHistoryDetailRequestSeed) {
      return
    }

    const errorMessage = readErrorMessage(error, '导出历史详情加载失败')
    setOutputHistoryDetailError(row.rowKey, errorMessage)
    ElMessage.error(errorMessage)
  } finally {
    if (requestSeed === outputHistoryDetailRequestSeed) {
      loadingOutputHistoryDetailRowKey.value = ''
    }
  }
}

async function fetchOutputHistoryDownloadFile(downloadUrl: string) {
  const satoken = localStorage.getItem('satoken')
  const response = await fetch(downloadUrl, {
    credentials: 'include',
    headers: satoken
      ? {
          satoken: `LabelHub ${satoken}`,
        }
      : undefined,
  })

  if (response.ok) {
    return response
  }

  if (!satoken) {
    return response
  }

  return fetch(downloadUrl, {
    credentials: 'include',
    headers: {
      satoken: `LabelHub ${satoken}`,
    },
  })
}

async function fetchOutputHistoryFileUrl(fileUrl: string) {
  const response = await fetch(fileUrl)

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  return response
}

async function handleDownloadOutputHistoryFile(row: OutputHistoryRow) {
  if ((!row.fileUrl && !row.downloadUrl) || downloadingOutputHistoryRowKey.value) {
    return
  }

  downloadingOutputHistoryRowKey.value = row.rowKey

  try {
    const fileUrl = await resolveOutputHistoryFileUrl(row)

    if (fileUrl) {
      try {
        const response = await fetchOutputHistoryFileUrl(fileUrl)
        const blob = await response.blob()
        triggerBlobDownload(blob, buildOutputHistoryDownloadFileName({ ...row, fileUrl }, response))
        return
      } catch {
        if (!row.downloadUrl) {
          throw new Error('File download failed')
        }
      }
    }

    const response = await fetchOutputHistoryDownloadFile(row.downloadUrl)

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const blob = await response.blob()
    triggerBlobDownload(blob, buildOutputHistoryDownloadFileName(row, response))
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '文件下载失败，请检查文件地址或跨域配置'))
  } finally {
    if (downloadingOutputHistoryRowKey.value === row.rowKey) {
      downloadingOutputHistoryRowKey.value = ''
    }
  }
}

function buildOutputTaskPayload() {
  return {
    format: outputFormat.value,
    fields: outputPreviewRows.value.map((row) => ({
      sourceName: row.sourceName,
      targetName: row.targetName.trim() || row.sourceName,
      enabled: row.enabled,
    })),
  }
}

async function handleOpenOutputTaskDialog(task: TaskListItem) {
  const requestSeed = (outputPreviewRequestSeed += 1)

  selectedOutputTask.value = task
  outputPreviewRows.value = []
  outputFormat.value = 'json'
  outputPreviewErrorMessage.value = ''
  isOutputDialogOpen.value = true
  isPreviewingOutputTask.value = true

  try {
    const response = await PreviewOutputTask(task.detailId)

    if (requestSeed !== outputPreviewRequestSeed) {
      return
    }

    const responseErrorMessage = getResponseErrorMessage(response, '导出任务预览失败')

    if (responseErrorMessage) {
      outputPreviewErrorMessage.value = responseErrorMessage
      ElMessage.error(responseErrorMessage)
      return
    }

    outputPreviewRows.value = normalizeOutputPreviewRows(response)
  } catch (error: any) {
    if (requestSeed !== outputPreviewRequestSeed) {
      return
    }

    outputPreviewErrorMessage.value = readErrorMessage(error, '导出任务预览失败')
    ElMessage.error(outputPreviewErrorMessage.value)
  } finally {
    if (requestSeed === outputPreviewRequestSeed) {
      isPreviewingOutputTask.value = false
    }
  }
}

async function handleCreateOutputTask() {
  const task = selectedOutputTask.value

  if (!task) {
    ElMessage.warning('请先选择需要导出的任务')
    return
  }

  if (!outputPreviewRows.value.length) {
    ElMessage.warning('暂无可导出的字段')
    return
  }

  const emptyEnabledRow = outputPreviewRows.value.find((row) => row.enabled && !row.targetName.trim())

  if (emptyEnabledRow) {
    ElMessage.warning('已启用字段的 targetName 不能为空')
    return
  }

  isCreatingOutputTask.value = true

  try {
    const response = await CreateOutputTask(task.detailId, buildOutputTaskPayload())
    const responseErrorMessage = getResponseErrorMessage(response, '导出任务失败')

    if (responseErrorMessage) {
      ElMessage.error(responseErrorMessage)
      return
    }

    ElMessage.success('导出任务创建成功')
    handleCloseOutputTaskDialog()
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '导出任务失败'))
  } finally {
    isCreatingOutputTask.value = false
  }
}

async function handleOpenOutputHistoryDialog(task: TaskListItem) {
  const requestSeed = (outputHistoryRequestSeed += 1)

  selectedOutputHistoryTask.value = task
  outputHistoryRows.value = []
  outputHistoryErrorMessage.value = ''
  expandedOutputHistoryRowKeys.value = []
  closingOutputHistoryRowKeys.value = []
  downloadingOutputHistoryRowKey.value = ''
  outputHistoryDetailRows.value = {}
  outputHistoryDetailErrorMap.value = {}
  isOutputHistoryDialogOpen.value = true
  isLoadingOutputHistory.value = true

  try {
    const response = await GetOutputTaskHistory(task.detailId, {
      pageNum: 1,
      pageSize: 100,
    })

    if (requestSeed !== outputHistoryRequestSeed) {
      return
    }

    const responseErrorMessage = getResponseErrorMessage(response, '导出历史加载失败')

    if (responseErrorMessage) {
      outputHistoryErrorMessage.value = responseErrorMessage
      ElMessage.error(responseErrorMessage)
      return
    }

    outputHistoryRows.value = normalizeOutputHistoryRows(response)
  } catch (error: any) {
    if (requestSeed !== outputHistoryRequestSeed) {
      return
    }

    outputHistoryErrorMessage.value = readErrorMessage(error, '导出历史加载失败')
    ElMessage.error(outputHistoryErrorMessage.value)
  } finally {
    if (requestSeed === outputHistoryRequestSeed) {
      isLoadingOutputHistory.value = false
    }
  }
}

function handleEditTask(task: TaskListItem) {
  router.push({
    name: 'owner-task-detail',
    query: {
      mode: 'edit',
      taskId: task.detailId,
    },
  })
}

async function handleTaskAction(task: TaskListItem, action: TaskActionType) {
  try {
    await ElMessageBox.confirm(getTaskActionConfirmText(task, action), getTaskActionTitle(action), {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: action === 'end' ? 'warning' : 'info',
    })
  } catch {
    return
  }

  const actionRequestMap: Record<TaskActionType, (id: string | number) => Promise<any>> = {
    publish: PostTask,
    pause: PauseTask,
    resume: ResumeTask,
    end: EndTask,
  }

  actionTaskKey.value = `${task.id}-${action}`

  try {
    await actionRequestMap[action](task.deleteId)
    ElMessage.success(getTaskActionSuccessMessage(action))
    await loadTasks(currentPage.value, true)
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, `${getTaskActionTitle(action)}失败`))
  } finally {
    actionTaskKey.value = ''
  }
}

async function handleDeleteTask(task: TaskListItem) {
  try {
    await ElMessageBox.confirm(`确认删除任务「${task.name}」吗？`, '删除任务', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  deletingTaskId.value = task.deleteId

  try {
    await DeleteTask(task.deleteId)
    ElMessage.success('删除任务成功')

    const nextPage = tasks.value.length === 1 && currentPage.value > 1 ? currentPage.value - 1 : currentPage.value
    await loadTasks(nextPage, true)
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '删除任务失败'))
  } finally {
    deletingTaskId.value = ''
  }
}

onMounted(() => {
  loadTasks(1)
})
</script>

<template>
  <section class="owner-task-page">
    <header class="owner-task-page__header">
      <div class="owner-task-page__header-copy">
        <p class="owner-task-page__eyebrow">任务管理</p>
        <h1 class="owner-task-page__title">任务管理</h1>
        <p class="owner-task-page__description">集中管理数据生产任务、分发策略与质量进度。</p>
      </div>
      <img class="owner-task-page__header-illustration" src="../../icon/气球.gif" alt="">
    </header>

    <section class="owner-task-page__toolbar">
      <div class="owner-task-page__tabs" aria-label="任务列表范围">
        <button
          v-for="tab in taskScopeTabs"
          :key="tab.key"
          class="owner-task-page__tab"
          :class="{ 'owner-task-page__tab--active': activeTaskScope === tab.key }"
          type="button"
          @click="handleTaskScopeChange(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="owner-task-page__toolbar-actions">
        <span class="owner-task-page__count">{{ taskCountText }}</span>
        <el-button class="owner-task-page__create-button" type="primary" @click="handleCreateTask">
          创建任务
        </el-button>
      </div>
    </section>

    <form class="owner-task-page__filters" aria-label="任务筛选" @submit.prevent="handleTaskFilterSubmit">
      <el-input
        v-model="taskKeyword"
        class="owner-task-page__filter-search"
        clearable
        placeholder="搜索任务名称或描述"
        aria-label="搜索任务名称或描述"
      />
      <el-select v-model="taskStatusFilter" class="owner-task-page__filter-select" aria-label="任务状态">
        <el-option v-for="option in taskStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <button class="owner-task-page__filter-submit" type="submit">筛选</button>
      <button class="owner-task-page__filter-reset" type="button" @click="handleTaskFilterReset">重置</button>
    </form>

    <Transition name="template-designer-loading">
      <div
        v-if="isBlockingLoadingOpen"
        class="template-designer__loading-backdrop"
        role="presentation"
      >
        <section class="template-designer__loading-dialog" aria-label="正在加载中">
          <img class="template-designer__loading-image" :src="loadingAnimation" alt="">
          <p class="template-designer__loading-text" aria-live="polite">
            <span
              v-for="(char, index) in loadingTextChars"
              :key="`${char}-${index}`"
              :style="{ animationDelay: `${index * 0.08}s` }"
            >
              {{ char }}
            </span>
          </p>
        </section>
      </div>
    </Transition>

    <p v-if="taskErrorMessage" class="owner-task-page__error">{{ taskErrorMessage }}</p>

    <section class="owner-task-page__table-shell">
      <div class="owner-task-page__table-scroll">
        <div class="owner-task-page__table">
          <div class="owner-task-page__table-header">
            <span>任务</span>
            <span>状态</span>
            <span>截止时间</span>
            <span>已完成 / 配额</span>
            <span>操作</span>
          </div>

          <Transition name="owner-task-panel" mode="out-in">
            <div
              :key="`${activeTaskScope}-${currentPage}-${listAnimationKey}`"
              class="owner-task-page__table-state"
            >
              <div v-if="shouldShowInlineLoading" class="owner-task-page__empty">正在加载任务...</div>
              <div v-else-if="!tasks.length" class="owner-task-page__empty">{{ emptyTaskText }}</div>

              <TransitionGroup
                v-else
                class="owner-task-page__rows"
                name="owner-task-row"
                tag="div"
              >
                <article
                  v-for="task in tasks"
                  :key="`${activeTaskScope}-${task.id}-${listAnimationKey}`"
                  class="owner-task-page__row"
                >

                  <div class="owner-task-page__task-summary">
                    <h2 class="owner-task-page__task-name">{{ task.name }}</h2>
                    <div class="owner-task-page__task-meta">
                      <span>{{ task.taskCode }}</span>


                      <span>Owner：{{ task.ownerText }}</span>
                      <el-tag
                        v-for="tag in task.tags"
                        :key="tag"
                        type="warning"
                      >
                        {{ tag }}
                      </el-tag>

                    </div>
                  </div>

                  <div class="owner-task-page__status-cell">
                    <em
                      class="owner-task-page__status-badge"
                      :class="`owner-task-page__status-badge--${task.statusType}`"
                    >
                      {{ task.status }}
                    </em>
                  </div>
                  <div class="owner-task-page__strategy-cell">
                    {{ task.createdAt.split('T')[0] }}
                  </div>

                  <div class="owner-task-page__progress-cell">
                    
                    <strong>{{ task.quotaText }}</strong>
                    <el-progress
                      :color="task.progressColor"
                      :percentage="task.progressPercent"
                      :show-text="false"
                    />
                  </div>

                  <div class="owner-task-page__actions">
                    <el-button
                      size="small"
                      type="primary"
                      :loading="detailLoadingTaskId === task.id"
                      @click="handleViewTask(task)"
                    >
                      查看详情
                    </el-button>
                    <el-button
                      v-if="isEndedTask(task)"
                      size="small"
                      type="success"
                      plain
                      @click="handleOpenOutputTaskDialog(task)"
                    >
                      导出任务
                    </el-button>
                    <el-button
                      v-if="isEndedTask(task)"
                      size="small"
                      type="info"
                      plain
                      @click="handleOpenOutputHistoryDialog(task)"
                    >
                      导出历史
                    </el-button>
                    <el-button
                      v-if="isDraftTask(task)"
                      size="small"
                      type="primary"
                      plain
                      @click="handleEditTask(task)"
                    >
                      修改任务
                    </el-button>
                    <el-button
                      v-if="isDraftTask(task)"
                      size="small"
                      type="success"
                      :loading="isTaskActionLoading(task, 'publish')"
                      @click="handleTaskAction(task, 'publish')"
                    >
                      发布任务
                    </el-button>
                    <el-button
                      v-if="isPublishedTask(task)"
                      size="small"
                      type="warning"
                      :loading="isTaskActionLoading(task, 'pause')"
                      @click="handleTaskAction(task, 'pause')"
                    >
                      暂停任务
                    </el-button>
                    <el-button
                      v-if="isPausedTask(task)"
                      size="small"
                      type="warning"
                      :loading="isTaskActionLoading(task, 'resume')"
                      @click="handleTaskAction(task, 'resume')"
                    >
                      恢复任务
                    </el-button>
                    <el-button
                      v-if="isPublishedTask(task) || isPausedTask(task)"
                      size="small"
                      type="danger"
                      plain
                      :loading="isTaskActionLoading(task, 'end')"
                      @click="handleTaskAction(task, 'end')"
                    >
                      结束任务
                    </el-button>
                    <el-button
                      v-if="isDraftTask(task)"
                      size="small"
                      type="danger"
                      :loading="deletingTaskId === task.deleteId"
                      @click="handleDeleteTask(task)"
                    >
                      删除任务
                    </el-button>
                  </div>
                </article>
              </TransitionGroup>
            </div>
          </Transition>
        </div>
      </div>
    </section>

    <footer class="owner-task-page__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        background
        layout="total, prev, pager, next, jumper"
        :page-size="taskPageSize"
        :total="totalTasks"
        @current-change="handlePageChange"
      />
    </footer>

    <TaskRespectDetailDialog
      v-model="isTaskDetailDialogOpen"
      audience="owner"
      :detail="taskDetail"
      :error-message="taskDetailErrorMessage"
      :loading="isTaskDetailLoading"
      :show-item-details="true"
      :task-id="taskDetailDialogTaskId"
      :title="taskDetailTitle"
    />

    <Transition name="owner-task-output-dialog">
      <div
        v-if="isOutputDialogOpen"
        class="owner-task-page__output-backdrop"
        role="presentation"
        @click.self="handleCloseOutputTaskDialog"
      >
        <section class="owner-task-page__output-dialog" aria-label="导出任务预览">
          <header class="owner-task-page__output-header">
            <div>
              <span>导出任务预览</span>
              <strong>{{ outputTaskTitle }}</strong>
            </div>
            <button
              class="owner-task-page__output-close"
              type="button"
              aria-label="关闭导出任务预览"
              @click="handleCloseOutputTaskDialog"
            >
              ×
            </button>
          </header>

          <div v-if="isPreviewingOutputTask" class="owner-task-page__output-state">正在加载导出字段...</div>

          <p v-else-if="outputPreviewErrorMessage" class="owner-task-page__output-error" role="alert">
            {{ outputPreviewErrorMessage }}
          </p>

          <el-table
            v-else
            :data="outputPreviewRows"
            stripe
            class="owner-task-page__output-table"
            empty-text="暂无导出字段"
          >
            <el-table-column label="sourceName" min-width="180" align="center" header-align="center">
              <template #default="{ row }">
                <span class="owner-task-page__output-source">{{ row.sourceName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="targetName" min-width="220" align="center" header-align="center">
              <template #default="{ row }">
                <el-input
                  :model-value="row.targetName"
                  placeholder="请输入导出后的字段名"
                  @update:model-value="updateOutputTargetName(row.id, String($event))"
                />
              </template>
            </el-table-column>
            <el-table-column label="sampleValue" min-width="260" align="center" header-align="center" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="owner-task-page__output-sample">{{ row.sampleValue }}</span>
              </template>
            </el-table-column>
            <el-table-column label="是否启用" width="160" align="center" header-align="center">
              <template #default="{ row }">
                <button
                  class="owner-task-page__output-switch"
                  :class="{ 'owner-task-page__output-switch--on': row.enabled }"
                  type="button"
                  role="switch"
                  :aria-checked="row.enabled"
                  @click="updateOutputEnabled(row.id, !row.enabled)"
                >
                  <span class="owner-task-page__output-switch-text">{{ row.enabled ? 'on' : 'off' }}</span>
                  <span class="owner-task-page__output-switch-knob" aria-hidden="true"></span>
                </button>
              </template>
            </el-table-column>
          </el-table>

          <section class="owner-task-page__output-format" aria-label="格式选择">
            <span>格式选择</span>
            <el-radio-group v-model="outputFormat" class="owner-task-page__output-format-options">
              <el-radio-button
                v-for="format in outputFormatOptions"
                :key="format"
                :label="format"
              >
                {{ format }}
              </el-radio-button>
            </el-radio-group>
          </section>

          <footer class="owner-task-page__output-actions">
            <el-button :disabled="isCreatingOutputTask" @click="handleCloseOutputTaskDialog">
              取消
            </el-button>
            <el-button
              type="primary"
              :disabled="isPreviewingOutputTask || !outputPreviewRows.length"
              :loading="isCreatingOutputTask"
              @click="handleCreateOutputTask"
            >
              导出任务
            </el-button>
          </footer>
        </section>
      </div>
    </Transition>

    <Transition name="owner-task-output-dialog">
      <div
        v-if="isOutputHistoryDialogOpen"
        class="owner-task-page__output-backdrop"
        role="presentation"
        @click.self="handleCloseOutputHistoryDialog"
      >
        <section class="owner-task-page__output-dialog owner-task-page__history-dialog" aria-label="导出历史">
          <header class="owner-task-page__output-header">
            <div>
              <span>导出历史</span>
              <strong>{{ outputHistoryTitle }}</strong>
            </div>
            <button
              class="owner-task-page__output-close"
              type="button"
              aria-label="关闭导出历史"
              @click="handleCloseOutputHistoryDialog"
            >
              ×
            </button>
          </header>

          <div v-if="isLoadingOutputHistory" class="owner-task-page__output-state">正在加载导出历史...</div>

          <p v-else-if="outputHistoryErrorMessage" class="owner-task-page__output-error" role="alert">
            {{ outputHistoryErrorMessage }}
          </p>

          <el-table
            v-else
            :data="outputHistoryRows"
            :expand-row-keys="expandedOutputHistoryRowKeys"
            row-key="rowKey"
            stripe
            class="owner-task-page__output-table owner-task-page__history-table"
            empty-text="暂无导出历史"
          >
            <el-table-column type="expand" width="1">
              <template #default="{ row }">
                <Transition name="owner-task-history-fields" appear>
                  <section
                    class="owner-task-page__history-fields-panel"
                    :class="{ 'owner-task-page__history-fields-panel--closing': isOutputHistoryRowClosing(row) }"
                  >
                    <div
                      v-if="loadingOutputHistoryDetailRowKey === row.rowKey"
                      class="owner-task-page__history-fields-state"
                    >
                      正在加载字段详情...
                    </div>
                    <p
                      v-else-if="outputHistoryDetailErrorMap[row.rowKey]"
                      class="owner-task-page__history-fields-error"
                    >
                      {{ outputHistoryDetailErrorMap[row.rowKey] }}
                    </p>
                    <el-table
                      v-else
                      :data="outputHistoryDetailRows[row.rowKey] ?? []"
                      stripe
                      class="owner-task-page__history-fields-table"
                      empty-text="暂无字段配置"
                    >
                      <el-table-column prop="sourceName" label="sourceName" min-width="180" align="center" header-align="center" show-overflow-tooltip />
                      <el-table-column prop="targetName" label="targetName" min-width="180" align="center" header-align="center" show-overflow-tooltip />
                      <el-table-column label="enabled" width="120" align="center" header-align="center">
                        <template #default="{ row: fieldRow }">
                          <el-tag :type="fieldRow.enabled ? 'primary' : 'info'" effect="light">
                            {{ fieldRow.enabledText }}
                          </el-tag>
                        </template>
                      </el-table-column>
                    </el-table>
                  </section>
                </Transition>
              </template>
            </el-table-column>
            <el-table-column prop="id" label="导出任务ID" min-width="150" align="center" header-align="center" show-overflow-tooltip />
            <el-table-column prop="format" label="导出格式" width="120" align="center" header-align="center" />
            <el-table-column label="状态" width="130" align="center" header-align="center">
              <template #default="{ row }">
                <el-tag
                  :class="`owner-task-page__history-status--${row.statusType}`"
                  effect="light"
                >
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="180" align="center" header-align="center" />
            <el-table-column prop="updateTime" label="完成/更新时间" min-width="180" align="center" header-align="center" />
            <el-table-column label="文件" width="220" align="center" header-align="center">
              <template #default="{ row }">
                <div class="owner-task-page__history-file-actions">
                  <button
                    class="owner-task-page__history-detail-button"
                    type="button"
                    :disabled="loadingOutputHistoryDetailRowKey === row.rowKey"
                    @click.stop="handleToggleOutputHistoryDetail(row)"
                  >
                    {{
                      loadingOutputHistoryDetailRowKey === row.rowKey
                        ? '加载中'
                        : isOutputHistoryRowExpanded(row)
                          ? '收起内容'
                          : '查看详情'
                    }}
                  </button>
                  <button
                    v-if="row.fileUrl || row.downloadUrl"
                    class="owner-task-page__history-download"
                    type="button"
                    :disabled="downloadingOutputHistoryRowKey === row.rowKey"
                    @click.stop="handleDownloadOutputHistoryFile(row)"
                  >
                    {{ downloadingOutputHistoryRowKey === row.rowKey ? '下载中' : '下载' }}
                  </button>
                  <span v-else class="owner-task-page__history-empty">-</span>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <footer class="owner-task-page__output-actions">
            <el-button @click="handleCloseOutputHistoryDialog">关闭</el-button>
          </footer>
        </section>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.owner-task-page {
  display: flex;
  min-height: calc(100vh - 104px);
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  color: #1f2937;
  background: #f3f6f9;
}

.owner-task-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 112px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: linear-gradient(135deg, #ffffff 0%, #f7fbff 100%);
  padding: 22px 26px;
}

.owner-task-page__header-copy {
  min-width: 0;
}

.owner-task-page__header-illustration {
  width: 84px;
  height: 84px;
  flex: 0 0 auto;
  border: 2px dashed #cbd5e1;
  border-radius: 50%;
  object-fit: cover;
}

.owner-task-page__eyebrow {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 700;
  color: #4f6680;
}

.owner-task-page__title {
  margin: 0;
  font-size: 26px;
  line-height: 1.25;
  color: #1f2937;
}

.owner-task-page__description {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.owner-task-page__create-button {
  min-width: 108px;
  height: 38px;
  font-size: 16px;
  font-weight: 800;
  background-color: #172033;
  border-radius: 20px;
  border: none  ;
}

.owner-task-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.owner-task-page__filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  border: 1px solid #d9e2ec;
  border-radius: 14px;
  background: #ffffff;
  padding: 14px;
}

.owner-task-page__filter-search {
  flex: 1 1 280px;
  min-width: 220px;
}

.owner-task-page__filter-select {
  width: 150px;
}

.owner-task-page__filter-submit,
.owner-task-page__filter-reset {
  min-height: 36px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
}

.owner-task-page__filter-submit {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.owner-task-page__filter-reset {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #64748b;
}

.owner-task-page__tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
}

.owner-task-page__tab {
  min-width: 96px;
  height: 34px;
  border: 0;
  border-radius: 6px;
  color: #1296DB;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}

.owner-task-page__tab--active {
  color: #ffffff;
  background: #172033;
  font-weight: 700;
}

.owner-task-page__count {
  color: #6b7280;
  font-size: 14px;
}

.owner-task-page__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.owner-task-page__error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  background: #fff1f2;
  font-size: 14px;
}

.template-designer__loading-backdrop {
  position: fixed;
  z-index: 48;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(20, 32, 54, 0.2);
  backdrop-filter: blur(18px);
}

.template-designer__loading-dialog {
  display: grid;
  width: min(100%, 260px);
  justify-items: center;
  gap: 12px;
  padding: 26px 28px 24px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
}

.template-designer__loading-image {
  display: block;
  width: 86px;
  height: 86px;
  object-fit: contain;
}

.template-designer__loading-text {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  justify-content: center;
  gap: 1px;
  margin: 0;
  color: var(--lh-text-primary, #1f2937);
  font-size: 16px;
  font-weight: 850;
  letter-spacing: 0;
  white-space: nowrap;
}

.template-designer__loading-text span {
  display: inline-block;
  animation: template-designer-loading-text-bounce 0.72s cubic-bezier(0.2, 0.82, 0.2, 1) infinite;
}

.template-designer-loading-enter-active,
.template-designer-loading-leave-active {
  transition:
    opacity 0.28s ease,
    backdrop-filter 0.28s ease;
}

.template-designer-loading-enter-active .template-designer__loading-dialog {
  animation: template-designer-loading-dialog-in 0.32s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.template-designer-loading-leave-active .template-designer__loading-dialog {
  animation: template-designer-loading-dialog-out 0.24s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.template-designer-loading-enter-from,
.template-designer-loading-leave-to {
  opacity: 0;
  backdrop-filter: blur(0);
}

.owner-task-page__table-shell {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.owner-task-page__table-scroll {
  overflow: hidden;
}

.owner-task-page__table {
  min-width: 980px;
}

.owner-task-page__table-header,
.owner-task-page__row {
  display: grid;
  grid-template-columns:
    minmax(220px, 1fr)
    minmax(120px, 0.75fr)
    minmax(120px, 0.75fr)
    minmax(190px, 1.1fr)
    minmax(320px, 1.55fr)
}

.owner-task-page__table-header {
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #e5e7eb;
  color: #526173;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 800;
}

.owner-task-page__table-header span {
  padding: 0 10px;
}

.owner-task-page__table-state {
  min-height: 312px;
  background: #ffffff;
}

.owner-task-page__rows {
  display: grid;
  gap: 0;
  padding: 0;
  background: #ffffff;
}

.owner-task-page__row {
  min-height: 76px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  background: #ffffff;
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.owner-task-page__row:hover {
  background: #fbfdff;
  box-shadow: inset 3px 0 0 #409eff;
}

.owner-task-page__task-summary,
.owner-task-page__status-cell,
.owner-task-page__strategy-cell,
.owner-task-page__progress-cell,
.owner-task-page__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  padding: 12px 8px;
}

.owner-task-page__task-summary {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.owner-task-page__task-name {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.owner-task-page__task-meta {
  display: flex;
  width: 120px;
  max-width: 100%;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
}

.owner-task-page__status-badge {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  color: #4b5563;
  background: #eef2f7;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.owner-task-page__status-badge::before {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.owner-task-page__status-badge--draft {
  color: #64748b;
  background: #eef2f7;
}

.owner-task-page__status-badge--published,
.owner-task-page__status-badge--reviewed {
  color: #16a34a;
  background: #dcfce7;
}

.owner-task-page__status-badge--paused {
  color: #f97316;
  background: #fff7ed;
}

.owner-task-page__status-badge--reviewing,
.owner-task-page__status-badge--claimed,
.owner-task-page__status-badge--submitted {
  color: #2563eb;
  background: #dbeafe;
}

.owner-task-page__status-badge--ended {
  color: #64748b;
  background: #f1f5f9;
}

.owner-task-page__status-badge--rejected {
  color: #b91c1c;
  background: #ffe4e6;
}

.owner-task-page__strategy-cell {
  justify-content: flex-start;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.owner-task-page__progress-cell {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.owner-task-page__progress-cell strong {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.owner-task-page__progress-cell :deep(.el-progress) {
  width: 100%;
}

.owner-task-page__progress-cell :deep(.el-progress-bar__outer) {
  height: 5px !important;
  background: #e8edf3;
}

.owner-task-page__actions {
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
}

.owner-task-page__actions :deep(.el-button) {
  margin-left: 0;
  border-radius: 6px;
  font-weight: 700;
}

.owner-task-page__output-backdrop {
  position: fixed;
  z-index: 62;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.28);
  backdrop-filter: blur(14px);
}

.owner-task-page__output-dialog {
  display: grid;
  width: min(960px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  gap: 18px;
  overflow: hidden auto;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.22);
  padding: 22px;
}

.owner-task-page__history-dialog {
  width: min(1040px, 100%);
}

.owner-task-page__output-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e5e7eb;
}

.owner-task-page__output-header div {
  display: grid;
  gap: 6px;
}

.owner-task-page__output-header span {
  color: #1296db;
  font-size: 13px;
  font-weight: 850;
}

.owner-task-page__output-header strong {
  color: #172033;
  font-size: 20px;
  line-height: 1.25;
}

.owner-task-page__output-close {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid #e2e8f0;
  border-radius: 50%;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  transition:
    background-color 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease;
}

.owner-task-page__output-close:hover {
  background: #eff6ff;
  color: #2563eb;
  transform: translateY(-1px);
}

.owner-task-page__output-state {
  display: grid;
  min-height: 220px;
  place-items: center;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  color: #64748b;
  background: #f8fafc;
  font-size: 14px;
  font-weight: 750;
}

.owner-task-page__output-error {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  color: #b91c1c;
  background: #fff1f2;
  font-size: 14px;
}

.owner-task-page__output-table {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.owner-task-page__output-table :deep(.el-table__cell) {
  color: #475569;
  font-size: 13px;
}

.owner-task-page__output-table :deep(th.el-table__cell) {
  color: #172033;
  background: #f8fafc;
  font-weight: 850;
}

.owner-task-page__output-table :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.owner-task-page__output-table :deep(.el-input__inner) {
  text-align: center;
}

.owner-task-page__history-table {
  min-height: 260px;
}

.owner-task-page__history-table :deep(.el-table__expand-column .cell) {
  display: none;
}

.owner-task-page__history-table :deep(.el-table__expanded-cell) {
  padding: 0 18px 18px;
  background: #f8fafc;
}

.owner-task-page__history-table :deep(.el-tag) {
  border: 0;
  border-radius: 999px;
  font-weight: 850;
}

.owner-task-page__history-status--pending {
  color: #ca8a04;
  background: #fef9c3;
}

.owner-task-page__history-status--processing {
  color: #2563eb;
  background: #dbeafe;
}

.owner-task-page__history-status--success {
  color: #16a34a;
  background: #dcfce7;
}

.owner-task-page__history-status--failed {
  color: #dc2626;
  background: #fee2e2;
}

.owner-task-page__history-status--default {
  color: #64748b;
  background: #f1f5f9;
}

.owner-task-page__history-download {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  border: 0;
  border-radius: 999px;
  color: #ffffff;
  background: #172033;
  cursor: pointer;
  font-size: 12px;
  font-weight: 850;
  text-decoration: none;
  transition:
    background-color 0.18s ease,
    transform 0.18s ease;
}

.owner-task-page__history-file-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.owner-task-page__history-detail-button {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  cursor: pointer;
  font-size: 12px;
  font-weight: 850;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}

.owner-task-page__history-detail-button:hover:not(:disabled) {
  border-color: #2563eb;
  background: #dbeafe;
  transform: translateY(-1px);
}

.owner-task-page__history-detail-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.owner-task-page__history-download:hover:not(:disabled) {
  background: #2563eb;
  transform: translateY(-1px);
}

.owner-task-page__history-download:disabled {
  cursor: wait;
  opacity: 0.72;
  transform: none;
}

.owner-task-page__history-empty {
  color: #94a3b8;
  font-weight: 800;
}

.owner-task-page__history-fields-panel {
  overflow: hidden;
  max-height: 720px;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  padding: 14px;
  transform-origin: top center;
  transition:
    max-height 0.32s cubic-bezier(0.2, 0.82, 0.2, 1),
    opacity 0.24s ease,
    padding 0.32s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.32s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-page__history-fields-panel--closing {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
  transform: translateY(-10px) scaleY(0.96);
}

.owner-task-page__history-fields-state {
  display: grid;
  min-height: 96px;
  place-items: center;
  color: #64748b;
  font-size: 13px;
  font-weight: 800;
}

.owner-task-page__history-fields-error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  color: #b91c1c;
  background: #fff1f2;
  font-size: 13px;
  font-weight: 750;
}

.owner-task-page__history-fields-table {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.owner-task-page__history-fields-table :deep(th.el-table__cell) {
  color: #334155;
  background: #f1f5f9;
  font-weight: 850;
}

.owner-task-history-fields-enter-active,
.owner-task-history-fields-leave-active {
  transition:
    max-height 0.32s cubic-bezier(0.2, 0.82, 0.2, 1),
    opacity 0.24s ease,
    padding 0.32s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.32s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-history-fields-enter-from,
.owner-task-history-fields-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
  transform: translateY(-10px) scaleY(0.96);
}

.owner-task-history-fields-enter-to,
.owner-task-history-fields-leave-from {
  max-height: 720px;
  opacity: 1;
  transform: translateY(0) scaleY(1);
}

.owner-task-page__output-source,
.owner-task-page__output-sample {
  display: block;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.owner-task-page__output-switch {
  position: relative;
  display: inline-flex;
  width: 70px;
  height: 31px;
  align-items: center;
  justify-content: flex-end;
  overflow: hidden;
  padding: 0 10px;
  border: 3px solid rgba(71, 79, 72, 0.26);
  border-radius: 999px;
  background: #8f9990;
  color: #ffffff;
  box-shadow:
    inset 0 2px 5px rgba(0, 0, 0, 0.18),
    0 6px 14px rgba(15, 23, 42, 0.12);
  cursor: pointer;
  text-transform: lowercase;
  transition:
    background 0.22s ease,
    border-color 0.22s ease,
    box-shadow 0.22s ease;
}

.owner-task-page__output-switch--on {
  justify-content: flex-start;
  border-color: rgba(0, 110, 255, 0.34);
  background: #2a85fc;
  box-shadow:
    inset 0 2px 5px rgba(255, 255, 255, 0.16),
    0 8px 18px rgba(49, 210, 121, 0.26);
}

.owner-task-page__output-switch-text {
  position: relative;
  z-index: 1;
  font-size: 16px;
  font-weight: 900;
  letter-spacing: 0;
  line-height: 1;
  text-shadow: 0 1px 2px rgba(15, 23, 42, 0.2);
}

.owner-task-page__output-switch-knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 34% 28%, #ffffff 0, #ffffff 30%, #f1f5f9 68%, #e2e8f0 100%);
  box-shadow:
    0 4px 10px rgba(15, 23, 42, 0.22),
    inset 0 1px 2px rgba(255, 255, 255, 0.92);
  transition:
    left 0.22s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.22s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-page__output-switch--on .owner-task-page__output-switch-knob {
  left: calc(100% - 25px);
}

.owner-task-page__output-switch:hover .owner-task-page__output-switch-knob {
  transform: scale(1.04);
}

.owner-task-page__output-format {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.owner-task-page__output-format > span {
  color: #334155;
  font-size: 14px;
  font-weight: 850;
}

.owner-task-page__output-format-options {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.owner-task-page__output-format-options :deep(.el-radio-button__inner) {
  min-width: 76px;
  border-color: #dbe5ee;
  color: #475569;
  font-weight: 800;
}

.owner-task-page__output-format-options :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #172033;
  background: #172033;
  box-shadow: none;
  color: #ffffff;
}

.owner-task-page__output-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 2px;
}

.owner-task-output-dialog-enter-active,
.owner-task-output-dialog-leave-active {
  transition:
    opacity 0.24s ease,
    backdrop-filter 0.24s ease;
}

.owner-task-output-dialog-enter-active .owner-task-page__output-dialog {
  animation: owner-task-output-dialog-in 0.28s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.owner-task-output-dialog-leave-active .owner-task-page__output-dialog {
  animation: owner-task-output-dialog-out 0.2s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.owner-task-output-dialog-enter-from,
.owner-task-output-dialog-leave-to {
  opacity: 0;
  backdrop-filter: blur(0);
}

.owner-task-page__empty {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  color: #8b98a8;
  font-size: 15px;
}

.owner-task-page__pagination {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding: 14px 0 0;
  background: #f3f6f9;
}

.owner-task-panel-enter-active,
.owner-task-panel-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-panel-enter-from,
.owner-task-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.992);
}

.owner-task-row-enter-active,
.owner-task-row-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-row-move {
  transition: transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-row-enter-from,
.owner-task-row-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.992);
}

@keyframes template-designer-loading-dialog-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.96);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes template-designer-loading-dialog-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }

  to {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
}

@keyframes template-designer-loading-text-bounce {
  0%,
  72%,
  100% {
    transform: translateY(0);
  }

  34% {
    transform: translateY(-7px);
  }
}

@keyframes owner-task-output-dialog-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.97);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes owner-task-output-dialog-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }

  to {
    opacity: 0;
    transform: translateY(8px) scale(0.985);
  }
}

@media (max-width: 768px) {
  .owner-task-page {
    padding: 16px;
  }

  .owner-task-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .owner-task-page__create-button {
    width: 100%;
  }

  .owner-task-page__tabs {
    width: 100%;
  }

  .owner-task-page__tab {
    flex: 1;
  }

  .owner-task-page__toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .owner-task-page__filter-search,
  .owner-task-page__filter-select {
    width: 100%;
    min-width: 0;
    flex-basis: 100%;
  }

  .owner-task-page__filter-submit,
  .owner-task-page__filter-reset {
    flex: 1 1 0;
  }

  .owner-task-page__pagination {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>
