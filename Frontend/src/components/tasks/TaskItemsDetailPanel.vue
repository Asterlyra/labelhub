<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { GetTaskItemsDetail, GetTaskTimeline } from '../../utils/api'

type TimelineTone = 'success' | 'danger' | 'primary' | 'neutral'

interface TaskItemRecord {
  id?: string | number
  taskItemId?: string | number
  taskId?: string | number
  datasetItemId?: string | number
  labelerId?: string | number | null
  labelerName?: string | null
  status?: string
  annotationData?: unknown
  reviewerId?: string | number | null
  reviewerName?: string | null
  reviewComment?: string | null
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

interface TaskItemRow {
  id: string
  taskId: string
  datasetItemId: string
  labelerName: string
  status: string
  statusLabel: string
  statusType: string
  annotationDataText: string
  reviewerName: string
  reviewComment: string
  createTime: string
  updateTime: string
}

interface TimelineEntry {
  id?: string | number
  taskItemId?: string | number
  operatorName?: string
  operatorAction?: string
  operatorOrder?: string | number
  comment?: string | null
  createTime?: string
  [key: string]: unknown
}

const props = defineProps<{
  taskId?: string | number | null
}>()

const text = {
  allStatus: '\u5168\u90e8\u72b6\u6001',
  unassigned: '\u672a\u5206\u914d',
  claimed: '\u5df2\u9886\u53d6',
  draft: '\u8349\u7a3f',
  submitted: '\u5df2\u63d0\u4ea4',
  reviewing: '\u5ba1\u6838\u4e2d',
  rejected: '\u5df2\u9a73\u56de',
  reviewed: '\u5df2\u901a\u8fc7',
  aiPreReview: 'AI \u9884\u5ba1',
  rejectAction: '\u6253\u56de',
  approveAction: '\u901a\u8fc7',
  modifyAction: '\u4fee\u6539\u5e76\u901a\u8fc7',
  actionRecord: '\u64cd\u4f5c\u8bb0\u5f55',
  dataItemDetails: '\u6570\u636e\u9879\u8be6\u60c5',
  status: '\u72b6\u6001',
  labeler: '\u6807\u6ce8\u5458',
  reviewer: '\u5ba1\u6838\u5458',
  annotationData: '\u6807\u6ce8\u6570\u636e',
  reviewComment: '\u5ba1\u6838\u610f\u89c1',
  updateTime: '\u66f4\u65b0\u65f6\u95f4',
  datasetItem: '\u6570\u636e\u96c6\u9879',
  noTaskId: '\u6682\u65e0\u4efb\u52a1 ID\uff0c\u65e0\u6cd5\u52a0\u8f7d\u6570\u636e\u9879\u8be6\u60c5',
  noItems: '\u6682\u65e0\u6570\u636e\u9879',
  auditTimeline: '\u5ba1\u8ba1\u65f6\u95f4\u7ebf',
  selectItem: '\u8bf7\u9009\u62e9\u5de6\u4fa7\u6570\u636e\u9879\u67e5\u770b\u6d41\u8f6c\u8bb0\u5f55',
  loadingTimeline: '\u6b63\u5728\u52a0\u8f7d\u5ba1\u8ba1\u65f6\u95f4\u7ebf...',
  reload: '\u91cd\u65b0\u52a0\u8f7d',
  noTimeline: '\u6682\u65e0\u6d41\u8f6c\u8bb0\u5f55',
  system: '\u7cfb\u7edf',
  itemLoadFailed: '\u6570\u636e\u9879\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25',
  timelineLoadFailed: '\u5ba1\u8ba1\u65f6\u95f4\u7ebf\u52a0\u8f7d\u5931\u8d25',
} as const

const statusOptions = [
  { label: text.allStatus, value: '' },
  { label: text.unassigned, value: 'UNASSIGNED' },
  { label: text.claimed, value: 'CLAIMED' },
  { label: text.draft, value: 'DRAFT' },
  { label: text.submitted, value: 'SUBMITTED' },
  { label: text.reviewing, value: 'REVIEWING' },
  { label: text.rejected, value: 'REJECTED' },
  { label: text.reviewed, value: 'REVIEWED' },
]

const currentTaskId = computed(() => readText(props.taskId))
const taskItems = shallowRef<TaskItemRow[]>([])
const selectedTaskItemId = shallowRef('')
const timelineEntries = shallowRef<TimelineEntry[]>([])
const statusFilter = shallowRef('')
const currentPage = shallowRef(1)
const pageSize = shallowRef(10)
const totalItems = shallowRef(0)
const isLoadingItems = shallowRef(false)
const isLoadingTimeline = shallowRef(false)
const itemErrorMessage = shallowRef('')
const timelineErrorMessage = shallowRef('')

let itemRequestSeed = 0
let timelineRequestSeed = 0

const selectedTaskItem = computed(() =>
  taskItems.value.find((item) => item.id === selectedTaskItemId.value) ?? null,
)

watch(
  currentTaskId,
  () => {
    resetPanel()

    if (currentTaskId.value) {
      void loadItems()
    }
  },
  { immediate: true },
)

function readText(value: unknown, fallback = '') {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }

  if (typeof value === 'string') {
    return value.trim() || fallback
  }

  return fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

function readObject(value: unknown) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }

  return {}
}

function getResponsePayload(response: unknown) {
  const responseRecord = readObject(response)
  const payload = responseRecord.data ?? response
  const payloadRecord = readObject(payload)

  return payloadRecord.data ?? payload
}

function getTaskItemRecords(response: unknown) {
  const payload = getResponsePayload(response)
  const payloadRecord = readObject(payload)
  const candidates = [payload, payloadRecord.records, payloadRecord.list, payloadRecord.items]

  return (candidates.find(Array.isArray) as TaskItemRecord[] | undefined) ?? []
}

function getPaginationTotal(response: unknown, fallback: number) {
  const payload = getResponsePayload(response)
  const payloadRecord = readObject(payload)

  return readNumber(payloadRecord.total ?? payloadRecord.count, fallback)
}

function getTimelineRecords(response: unknown) {
  const payload = getResponsePayload(response)

  return Array.isArray(payload) ? payload as TimelineEntry[] : []
}

function formatDateTime(value: unknown) {
  const currentText = readText(value)

  return currentText ? currentText.replace('T', ' ').replace(/\.\d+$/, '') : '-'
}

function formatJsonValue(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return '-'
  }

  if (typeof value === 'string') {
    return value.trim() || '-'
  }

  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

function getStatusMeta(value: unknown) {
  const status = readText(value, 'UNKNOWN').toUpperCase()
  const statusMeta: Record<string, { label: string; type: string }> = {
    UNASSIGNED: { label: text.unassigned, type: 'neutral' },
    CLAIMED: { label: text.claimed, type: 'primary' },
    DRAFT: { label: text.draft, type: 'neutral' },
    SUBMITTED: { label: text.submitted, type: 'warning' },
    REVIEWING: { label: text.reviewing, type: 'primary' },
    REJECTED: { label: text.rejected, type: 'danger' },
    REVIEWED: { label: text.reviewed, type: 'success' },
  }

  return statusMeta[status] ?? { label: readText(value, '-'), type: 'neutral' }
}

function getActionMeta(value: unknown): { label: string; tone: TimelineTone } {
  const action = readText(value, 'UNKNOWN').toUpperCase()
  const actionMeta: Record<string, { label: string; tone: TimelineTone }> = {
    SUBMIT: { label: text.submitted, tone: 'success' },
    AI_PRE_REVIEW: { label: text.aiPreReview, tone: 'primary' },
    REJECT: { label: text.rejectAction, tone: 'danger' },
    APPROVE: { label: text.approveAction, tone: 'success' },
    MODIFY: { label: text.modifyAction, tone: 'success' },
  }

  return actionMeta[action] ?? { label: readText(value, text.actionRecord), tone: 'neutral' }
}

function normalizeTaskItem(record: TaskItemRecord, index: number): TaskItemRow {
  const id = readText(record.id ?? record.taskItemId, String(index + 1))
  const statusMeta = getStatusMeta(record.status)

  return {
    id,
    taskId: readText(record.taskId, '-'),
    datasetItemId: readText(record.datasetItemId, '-'),
    labelerName: readText(record.labelerName ?? record.labelerId, '-'),
    status: readText(record.status, '-'),
    statusLabel: statusMeta.label,
    statusType: statusMeta.type,
    annotationDataText: formatJsonValue(record.annotationData),
    reviewerName: readText(record.reviewerName ?? record.reviewerId, '-'),
    reviewComment: readText(record.reviewComment, '-'),
    createTime: formatDateTime(record.createTime),
    updateTime: formatDateTime(record.updateTime),
  }
}

function resetPanel() {
  itemRequestSeed += 1
  timelineRequestSeed += 1
  taskItems.value = []
  selectedTaskItemId.value = ''
  timelineEntries.value = []
  statusFilter.value = ''
  currentPage.value = 1
  totalItems.value = 0
  isLoadingItems.value = false
  isLoadingTimeline.value = false
  itemErrorMessage.value = ''
  timelineErrorMessage.value = ''
}

async function loadItems() {
  const taskId = currentTaskId.value

  if (!taskId) {
    taskItems.value = []
    selectedTaskItemId.value = ''
    timelineEntries.value = []
    return
  }

  const requestSeed = ++itemRequestSeed
  isLoadingItems.value = true
  itemErrorMessage.value = ''

  try {
    const response = await GetTaskItemsDetail(taskId, {
      status: statusFilter.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })

    if (requestSeed !== itemRequestSeed) {
      return
    }

    const rows = getTaskItemRecords(response).map(normalizeTaskItem)
    taskItems.value = rows
    totalItems.value = getPaginationTotal(response, rows.length)

    const nextSelectedId = rows.some((item) => item.id === selectedTaskItemId.value)
      ? selectedTaskItemId.value
      : rows[0]?.id ?? ''

    selectedTaskItemId.value = nextSelectedId

    if (nextSelectedId) {
      void loadTimeline(nextSelectedId)
    } else {
      timelineEntries.value = []
    }
  } catch (error: any) {
    if (requestSeed !== itemRequestSeed) {
      return
    }

    itemErrorMessage.value = readErrorMessage(error, text.itemLoadFailed)
    taskItems.value = []
    totalItems.value = 0
    selectedTaskItemId.value = ''
    timelineEntries.value = []
  } finally {
    if (requestSeed === itemRequestSeed) {
      isLoadingItems.value = false
    }
  }
}

async function loadTimeline(taskItemId = selectedTaskItemId.value) {
  if (!taskItemId) {
    timelineEntries.value = []
    return
  }

  const requestSeed = ++timelineRequestSeed
  isLoadingTimeline.value = true
  timelineErrorMessage.value = ''

  try {
    const response = await GetTaskTimeline(taskItemId)

    if (requestSeed !== timelineRequestSeed) {
      return
    }

    timelineEntries.value = getTimelineRecords(response)
      .slice()
      .sort((left, right) => readNumber(left.operatorOrder) - readNumber(right.operatorOrder))
  } catch (error: any) {
    if (requestSeed !== timelineRequestSeed) {
      return
    }

    timelineErrorMessage.value = readErrorMessage(error, text.timelineLoadFailed)
    timelineEntries.value = []
  } finally {
    if (requestSeed === timelineRequestSeed) {
      isLoadingTimeline.value = false
    }
  }
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

function handleStatusChange() {
  currentPage.value = 1
  selectedTaskItemId.value = ''
  timelineEntries.value = []
  void loadItems()
}

function handlePageChange(page: number) {
  currentPage.value = page
  selectedTaskItemId.value = ''
  timelineEntries.value = []
  void loadItems()
}

function handleSelectItem(row: TaskItemRow) {
  if (selectedTaskItemId.value === row.id) {
    return
  }

  selectedTaskItemId.value = row.id
  void loadTimeline(row.id)
}

function getRowClassName({ row }: { row: TaskItemRow }) {
  return row.id === selectedTaskItemId.value ? 'task-items-detail__row--active' : ''
}

function getTimelineEntryKey(entry: TimelineEntry, index: number) {
  return readText(entry.id, `${readText(entry.operatorOrder, String(index))}-${index}`)
}
</script>

<template>
  <section class="task-items-detail" :aria-label="text.dataItemDetails">
    <section class="task-items-detail__table-card">
      <header class="task-items-detail__toolbar">
        <div>
          <span>Data Items</span>
          <h3>{{ text.dataItemDetails }}</h3>
        </div>
        <el-select
          v-model="statusFilter"
          class="task-items-detail__status-select"
          :placeholder="text.allStatus"
          @change="handleStatusChange"
        >
          <el-option
            v-for="option in statusOptions"
            :key="option.value || 'all'"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </header>

      <p v-if="!currentTaskId" class="task-items-detail__notice">{{ text.noTaskId }}</p>
      <p v-else-if="itemErrorMessage" class="task-items-detail__notice task-items-detail__notice--error">
        {{ itemErrorMessage }}
      </p>

      <el-table
        v-else
        v-loading="isLoadingItems"
        :data="taskItems"
        :empty-text="text.noItems"
        :row-class-name="getRowClassName"
        class="task-items-detail__table"
        stripe
        @row-click="handleSelectItem"
      >
        <el-table-column prop="id" label="ID" width="76" align="center" header-align="center" />
        <el-table-column prop="datasetItemId" :label="text.datasetItem" width="96" align="center" header-align="center" />
        <el-table-column :label="text.status" width="104" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :class="`task-items-detail__status--${row.statusType}`" effect="light">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="labelerName" :label="text.labeler" min-width="110" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="reviewerName" :label="text.reviewer" min-width="110" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="annotationDataText" :label="text.annotationData" min-width="160" show-overflow-tooltip />
        <el-table-column prop="reviewComment" :label="text.reviewComment" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" :label="text.updateTime" min-width="150" align="center" header-align="center" />
      </el-table>

      <footer class="task-items-detail__pagination">
        <el-pagination
          v-model:current-page="currentPage"
          background
          layout="total, prev, pager, next"
          :page-size="pageSize"
          :total="totalItems"
          @current-change="handlePageChange"
        />
      </footer>
    </section>

    <aside class="task-items-detail__timeline-card">
      <header class="task-items-detail__timeline-header">
        <div>
          <span>Audit Timeline</span>
          <h3>{{ text.auditTimeline }}</h3>
        </div>
        <el-tag v-if="selectedTaskItem" type="primary" effect="light">
          #{{ selectedTaskItem.id }}
        </el-tag>
      </header>

      <p v-if="!selectedTaskItemId" class="task-items-detail__notice">{{ text.selectItem }}</p>
      <p v-else-if="isLoadingTimeline" class="task-items-detail__notice">{{ text.loadingTimeline }}</p>
      <div v-else-if="timelineErrorMessage" class="task-items-detail__notice task-items-detail__notice--error">
        <span>{{ timelineErrorMessage }}</span>
        <el-button link type="primary" @click="loadTimeline()">{{ text.reload }}</el-button>
      </div>
      <p v-else-if="!timelineEntries.length" class="task-items-detail__notice">{{ text.noTimeline }}</p>

      <ol v-else class="task-items-detail__timeline-list">
        <li
          v-for="(entry, index) in timelineEntries"
          :key="getTimelineEntryKey(entry, index)"
          class="task-items-detail__timeline-item"
          :class="`task-items-detail__timeline-item--${getActionMeta(entry.operatorAction).tone}`"
        >
          <span class="task-items-detail__timeline-dot" aria-hidden="true" />
          <div class="task-items-detail__timeline-content">
            <p class="task-items-detail__timeline-meta">
              <strong>{{ readText(entry.operatorName, text.system) }}</strong>
              <time>{{ formatDateTime(entry.createTime) }}</time>
            </p>
            <p class="task-items-detail__timeline-action">{{ getActionMeta(entry.operatorAction).label }}</p>
            <p v-if="readText(entry.comment)" class="task-items-detail__timeline-comment">
              {{ entry.comment }}
            </p>
          </div>
        </li>
      </ol>
    </aside>
  </section>
</template>

<style scoped>
.task-items-detail {
  display: grid;
  max-height: min(72vh, 760px);
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.9fr);
  gap: 14px;
  overflow: hidden;
}

.task-items-detail__table-card,
.task-items-detail__timeline-card {
  display: grid;
  min-height: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.task-items-detail__table-card {
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.task-items-detail__timeline-card {
  align-content: start;
  overflow: auto;
  padding: 14px;
}

.task-items-detail__toolbar,
.task-items-detail__timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
}

.task-items-detail__timeline-header {
  padding: 0 0 12px;
  border-bottom: 1px solid #edf1f5;
}

.task-items-detail__toolbar span,
.task-items-detail__timeline-header span {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0;
  text-transform: uppercase;
}

.task-items-detail__toolbar h3,
.task-items-detail__timeline-header h3 {
  margin: 4px 0 0;
  color: #172033;
  font-size: 15px;
}

.task-items-detail__status-select {
  width: 132px;
}

.task-items-detail__table {
  min-height: 0;
  border-top: 1px solid #edf1f5;
}

.task-items-detail__table :deep(.el-table__body-wrapper) {
  cursor: pointer;
}

.task-items-detail__table :deep(.task-items-detail__row--active > .el-table__cell) {
  background: #eff6ff !important;
}

.task-items-detail__table :deep(th.el-table__cell) {
  color: #172033;
  background: #f8fafc;
  font-weight: 850;
}

.task-items-detail__table :deep(.el-table__cell) {
  color: #475569;
  font-size: 12px;
}

.task-items-detail__status--neutral,
.task-items-detail__status--primary,
.task-items-detail__status--warning,
.task-items-detail__status--danger,
.task-items-detail__status--success {
  border: 0;
  border-radius: 999px;
  font-weight: 850;
}

.task-items-detail__status--neutral {
  color: #64748b;
  background: #f1f5f9;
}

.task-items-detail__status--primary {
  color: #2563eb;
  background: #dbeafe;
}

.task-items-detail__status--warning {
  color: #d97706;
  background: #fef3c7;
}

.task-items-detail__status--danger {
  color: #dc2626;
  background: #fee2e2;
}

.task-items-detail__status--success {
  color: #16a34a;
  background: #dcfce7;
}

.task-items-detail__pagination {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #edf1f5;
  padding: 10px 14px;
}

.task-items-detail__notice {
  display: grid;
  min-height: 96px;
  place-items: center;
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 750;
  text-align: center;
}

.task-items-detail__notice--error {
  gap: 4px;
  color: #dc2626;
}

.task-items-detail__timeline-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.task-items-detail__timeline-item {
  --timeline-color: #94a3b8;
  position: relative;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  padding: 14px 0;
}

.task-items-detail__timeline-item:not(:last-child)::after {
  position: absolute;
  top: 29px;
  bottom: -1px;
  left: 4px;
  width: 1px;
  background: #e5e7eb;
  content: '';
}

.task-items-detail__timeline-item--success {
  --timeline-color: #16a34a;
}

.task-items-detail__timeline-item--danger {
  --timeline-color: #ef4444;
}

.task-items-detail__timeline-item--primary {
  --timeline-color: #2563eb;
}

.task-items-detail__timeline-dot {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border-radius: 50%;
  background: var(--timeline-color);
  box-shadow: 0 0 0 3px #ffffff;
}

.task-items-detail__timeline-content {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.task-items-detail__timeline-meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.task-items-detail__timeline-meta strong {
  color: #334155;
  font-weight: 850;
}

.task-items-detail__timeline-action {
  margin: 0;
  color: var(--timeline-color);
  font-size: 12px;
  font-weight: 850;
}

.task-items-detail__timeline-comment {
  margin: 2px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

@media (max-width: 980px) {
  .task-items-detail {
    max-height: none;
    grid-template-columns: 1fr;
    overflow: visible;
  }
}
</style>
