<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { GetAISuggestionList, GetAISuggestionListDetail } from '../../utils/api'

type PreviewTone = 'success' | 'warning' | 'danger' | 'primary' | 'neutral'

interface AiPreviewRecord {
  taskItemId?: string | number
  taskId?: string | number
  status?: string
  auditStatus?: string
  [key: string]: unknown
}

interface AiPreviewRow {
  id: string
  taskItemId: string
  taskId: string
  status: string
  statusTone: PreviewTone
  auditStatus: string
  auditTone: PreviewTone
}

interface AiPreviewScoreRow {
  id: string
  dimension: string
  passScore: number
  reason: string
  score: number
  weight: number
}

interface AiPreviewDetail {
  auditStatus: string
  auditTone: PreviewTone
  confidence: number
  lastError: string
  mediaResult: unknown
  scores: AiPreviewScoreRow[]
  status: string
  statusTone: PreviewTone
  summary: string
  taskId: string
  taskItemId: string
  weightedScore: number
}

const props = defineProps<{
  taskId?: string | number | null
}>()

const text = {
  aiPreReviewDetails: '\u0041\u0049\u9884\u5ba1\u8be6\u60c5',
  resultList: '\u0041\u0049\u9884\u5ba1\u7ed3\u679c\u5217\u8868',
  noTaskId: '\u6682\u65e0\u4efb\u52a1 ID\uff0c\u65e0\u6cd5\u52a0\u8f7d AI \u9884\u5ba1\u8be6\u60c5',
  noResults: '\u6682\u65e0 AI \u9884\u5ba1\u7ed3\u679c',
  loadingList: '\u6b63\u5728\u52a0\u8f7d AI \u9884\u5ba1\u7ed3\u679c...',
  loadingDetail: '\u6b63\u5728\u52a0\u8f7d AI \u9884\u5ba1\u8be6\u60c5...',
  selectResult: '\u8bf7\u9009\u62e9\u5de6\u4fa7 AI \u9884\u5ba1\u7ed3\u679c\u67e5\u770b\u8be6\u60c5',
  reload: '\u91cd\u65b0\u52a0\u8f7d',
  listLoadFailed: 'AI \u9884\u5ba1\u7ed3\u679c\u52a0\u8f7d\u5931\u8d25',
  detailLoadFailed: 'AI \u9884\u5ba1\u8be6\u60c5\u52a0\u8f7d\u5931\u8d25',
  viewDetail: '\u67e5\u770bAI\u9884\u5ba1\u8be6\u60c5',
  executionStatus: '\u6267\u884c\u72b6\u6001',
  auditStatus: '\u9884\u5ba1\u7ed3\u8bba',
  taskItemId: '\u4efb\u52a1\u6570\u636e\u9879 ID',
  taskId: '\u6240\u5c5e\u4efb\u52a1 ID',
  confidence: '\u7f6e\u4fe1\u5ea6',
  weightedScore: '\u52a0\u6743\u603b\u5206',
  dimensionScores: '\u7ef4\u5ea6\u8bc4\u5206',
  dimensionReasons: '\u7ef4\u5ea6\u539f\u56e0',
  summary: '\u7efc\u5408\u7ed3\u679c',
  mediaResult: '\u591a\u6a21\u6001\u9644\u52a0\u4fe1\u606f',
} as const

const currentTaskId = computed(() => readText(props.taskId))
const previewRows = shallowRef<AiPreviewRow[]>([])
const selectedTaskItemId = shallowRef('')
const selectedDetail = shallowRef<AiPreviewDetail | null>(null)
const isLoadingList = shallowRef(false)
const isLoadingDetail = shallowRef(false)
const listErrorMessage = shallowRef('')
const detailErrorMessage = shallowRef('')

const selectedRow = computed(() =>
  previewRows.value.find((row) => row.taskItemId === selectedTaskItemId.value) ?? null,
)
const detailMetaItems = computed(() => {
  const detail = selectedDetail.value

  if (!detail) {
    return []
  }

  return [
    { label: text.executionStatus, value: detail.status, tone: detail.statusTone },
    { label: text.auditStatus, value: detail.auditStatus, tone: detail.auditTone },
    { label: text.weightedScore, value: formatNumber(detail.weightedScore, '-'), tone: getScoreTone(detail.weightedScore, 80) },
  ]
})
const detailScoreRows = computed(() => selectedDetail.value?.scores ?? [])
const detailReasonRows = computed(() => detailScoreRows.value.filter((row) => row.reason))
const detailMediaResultText = computed(() => stringifyDetailValue(selectedDetail.value?.mediaResult))

let listRequestSeed = 0
let detailRequestSeed = 0

watch(
  currentTaskId,
  () => {
    resetPanel()

    if (currentTaskId.value) {
      void loadPreviewRows()
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

function readNumber(value: unknown, fallback = Number.NaN) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

function readObject(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function parseMaybeJson(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  const textValue = value.trim()

  if (!textValue || (!textValue.startsWith('{') && !textValue.startsWith('['))) {
    return value
  }

  try {
    return JSON.parse(textValue)
  } catch {
    return value
  }
}

function getResponsePayload(response: unknown) {
  const responseRecord = readObject(response)
  const payload = responseRecord.data ?? response
  const payloadRecord = readObject(payload)

  return payloadRecord.data ?? payload
}

function getPreviewRecords(response: unknown) {
  const payload = getResponsePayload(response)
  const payloadRecord = readObject(payload)
  const candidates = [payload, payloadRecord.records, payloadRecord.list, payloadRecord.items, payloadRecord.content]

  return (candidates.find(Array.isArray) as AiPreviewRecord[] | undefined) ?? []
}

function getDetailRecord(response: unknown) {
  return readObject(parseMaybeJson(getResponsePayload(response)))
}

function formatNumber(value: unknown, fallback = '') {
  const numberValue = Number(value)

  if (!Number.isFinite(numberValue)) {
    return fallback
  }

  return Number.isInteger(numberValue) ? String(numberValue) : numberValue.toFixed(2).replace(/\.?0+$/, '')
}

function clampPercent(value: unknown) {
  const numberValue = Number(value)

  if (!Number.isFinite(numberValue)) {
    return 0
  }

  return Math.min(100, Math.max(0, numberValue))
}

function stringifyDetailValue(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return ''
  }

  if (typeof value === 'string') {
    return value
  }

  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function getResponseErrorMessage(response: unknown, fallback: string) {
  const responseRecord = readObject(response)
  const code = Number(responseRecord.code)

  if (!Number.isFinite(code) || code === 200) {
    return ''
  }

  return readText(responseRecord.message ?? responseRecord.msg, fallback)
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

function formatExecutionStatus(value: unknown) {
  const status = readText(value).toLowerCase()
  const statusMap: Record<string, string> = {
    pending: '\u7b49\u5f85\u4e2d',
    processing: '\u751f\u6210\u4e2d',
    running: '\u751f\u6210\u4e2d',
    completed: '\u5df2\u5b8c\u6210',
    complete: '\u5df2\u5b8c\u6210',
    success: '\u5df2\u5b8c\u6210',
    failed: '\u5931\u8d25',
    error: '\u5931\u8d25',
  }

  return statusMap[status] ?? readText(value, '\u672a\u77e5\u72b6\u6001')
}

function getExecutionTone(value: unknown): PreviewTone {
  const status = readText(value).toLowerCase()

  if (['completed', 'complete', 'success'].includes(status)) {
    return 'success'
  }

  if (['pending', 'processing', 'running'].includes(status)) {
    return 'warning'
  }

  if (['failed', 'error'].includes(status)) {
    return 'danger'
  }

  return 'neutral'
}

function formatAuditStatus(value: unknown) {
  const status = readText(value).toLowerCase()
  const statusMap: Record<string, string> = {
    pass: '\u5efa\u8bae\u901a\u8fc7',
    approve: '\u5efa\u8bae\u901a\u8fc7',
    approved: '\u5efa\u8bae\u901a\u8fc7',
    review: '\u5efa\u8bae\u590d\u6838',
    manual_review: '\u5efa\u8bae\u590d\u6838',
    reject: '\u5efa\u8bae\u9a73\u56de',
    rejected: '\u5efa\u8bae\u9a73\u56de',
  }

  return statusMap[status] ?? readText(value, '\u6682\u65e0\u7ed3\u8bba')
}

function getAuditTone(value: unknown): PreviewTone {
  const status = readText(value).toLowerCase()

  if (['pass', 'approve', 'approved'].includes(status)) {
    return 'success'
  }

  if (['review', 'manual_review'].includes(status)) {
    return 'warning'
  }

  if (['reject', 'rejected'].includes(status)) {
    return 'danger'
  }

  return 'neutral'
}

function getScoreTone(score: unknown, passScore: unknown): PreviewTone {
  const scoreValue = readNumber(score)
  const passScoreValue = readNumber(passScore, 80)

  if (!Number.isFinite(scoreValue)) {
    return 'neutral'
  }

  if (scoreValue >= passScoreValue) {
    return 'success'
  }

  if (scoreValue >= Math.max(60, passScoreValue * 0.75)) {
    return 'warning'
  }

  return 'danger'
}

function normalizePreviewRecord(record: AiPreviewRecord, index: number): AiPreviewRow {
  const taskItemId = readText(record.taskItemId, `item-${index + 1}`)
  const taskId = readText(record.taskId, currentTaskId.value || '-')

  return {
    id: `${taskId}-${taskItemId}-${index}`,
    taskItemId,
    taskId,
    status: formatExecutionStatus(record.status),
    statusTone: getExecutionTone(record.status),
    auditStatus: formatAuditStatus(record.auditStatus),
    auditTone: getAuditTone(record.auditStatus),
  }
}

function normalizeScoreRecord(value: unknown, index: number): AiPreviewScoreRow {
  const record = readObject(parseMaybeJson(value))
  const dimension = readText(record.dimension ?? record.field ?? record.name, `\u7ef4\u5ea6 ${index + 1}`)

  return {
    id: `${dimension}-${index}`,
    dimension,
    passScore: readNumber(record.passScore, 0),
    reason: readText(record.reason),
    score: readNumber(record.score, 0),
    weight: readNumber(record.weight, 0),
  }
}

function normalizeDetail(response: unknown): AiPreviewDetail {
  const detail = getDetailRecord(response)
  const scores = Array.isArray(detail.scores)
    ? detail.scores.map((item, index) => normalizeScoreRecord(item, index))
    : []

  return {
    auditStatus: formatAuditStatus(detail.auditStatus),
    auditTone: getAuditTone(detail.auditStatus),
    confidence: readNumber(detail.confidence),
    lastError: readText(detail.lastError),
    mediaResult: detail.mediaResult,
    scores,
    status: formatExecutionStatus(detail.status),
    statusTone: getExecutionTone(detail.status),
    summary: readText(detail.summary),
    taskId: readText(detail.taskId, selectedRow.value?.taskId ?? currentTaskId.value ?? '-'),
    taskItemId: readText(detail.taskItemId, selectedRow.value?.taskItemId ?? '-'),
    weightedScore: readNumber(detail.weightedScore),
  }
}

function resetPanel() {
  listRequestSeed += 1
  detailRequestSeed += 1
  previewRows.value = []
  selectedTaskItemId.value = ''
  selectedDetail.value = null
  isLoadingList.value = false
  isLoadingDetail.value = false
  listErrorMessage.value = ''
  detailErrorMessage.value = ''
}

async function loadPreviewRows() {
  const taskId = currentTaskId.value

  if (!taskId) {
    return
  }

  const requestSeed = ++listRequestSeed
  isLoadingList.value = true
  listErrorMessage.value = ''

  try {
    const response = await GetAISuggestionList(taskId, {
      pageNum: 1,
      pageSize: 1000,
    })

    if (requestSeed !== listRequestSeed) {
      return
    }

    const responseErrorMessage = getResponseErrorMessage(response, text.listLoadFailed)

    if (responseErrorMessage) {
      previewRows.value = []
      selectedTaskItemId.value = ''
      selectedDetail.value = null
      listErrorMessage.value = responseErrorMessage
      return
    }

    const rows = getPreviewRecords(response).map((record, index) => normalizePreviewRecord(record, index))
    previewRows.value = rows
    selectedTaskItemId.value = rows[0]?.taskItemId ?? ''

    if (selectedTaskItemId.value) {
      void loadPreviewDetail(selectedTaskItemId.value)
    } else {
      selectedDetail.value = null
    }
  } catch (error: any) {
    if (requestSeed !== listRequestSeed) {
      return
    }

    previewRows.value = []
    selectedTaskItemId.value = ''
    selectedDetail.value = null
    listErrorMessage.value = readErrorMessage(error, text.listLoadFailed)
  } finally {
    if (requestSeed === listRequestSeed) {
      isLoadingList.value = false
    }
  }
}

async function loadPreviewDetail(taskItemId = selectedTaskItemId.value) {
  if (!taskItemId) {
    selectedDetail.value = null
    return
  }

  const requestSeed = ++detailRequestSeed
  selectedTaskItemId.value = taskItemId
  isLoadingDetail.value = true
  detailErrorMessage.value = ''

  try {
    const response = await GetAISuggestionListDetail(taskItemId)

    if (requestSeed !== detailRequestSeed) {
      return
    }

    const responseErrorMessage = getResponseErrorMessage(response, text.detailLoadFailed)

    if (responseErrorMessage) {
      selectedDetail.value = null
      detailErrorMessage.value = responseErrorMessage
      return
    }

    selectedDetail.value = normalizeDetail(response)
  } catch (error: any) {
    if (requestSeed !== detailRequestSeed) {
      return
    }

    selectedDetail.value = null
    detailErrorMessage.value = readErrorMessage(error, text.detailLoadFailed)
  } finally {
    if (requestSeed === detailRequestSeed) {
      isLoadingDetail.value = false
    }
  }
}

function handleViewDetail(row: AiPreviewRow) {
  void loadPreviewDetail(row.taskItemId)
}

function getRowClassName({ row }: { row: AiPreviewRow }) {
  return row.taskItemId === selectedTaskItemId.value ? 'task-ai-preview__row--active' : ''
}
</script>

<template>
  <section class="task-ai-preview" :aria-label="text.aiPreReviewDetails">
    <section class="task-ai-preview__list-card">
      <header class="task-ai-preview__toolbar">
        <div>
          <span>AI Results</span>
          <h3>{{ text.resultList }}</h3>
        </div>
        <el-button
          size="small"
          type="primary"
          plain
          :disabled="!currentTaskId"
          :loading="isLoadingList"
          @click="loadPreviewRows"
        >
          {{ text.reload }}
        </el-button>
      </header>

      <p v-if="!currentTaskId" class="task-ai-preview__notice">{{ text.noTaskId }}</p>
      <p v-else-if="listErrorMessage" class="task-ai-preview__notice task-ai-preview__notice--error">
        {{ listErrorMessage }}
      </p>

      <el-table
        v-else
        v-loading="isLoadingList"
        :data="previewRows"
        :empty-text="isLoadingList ? text.loadingList : text.noResults"
        :row-class-name="getRowClassName"
        class="task-ai-preview__table"
        height="100%"
        stripe
        @row-click="handleViewDetail"
      >
        <el-table-column :label="text.executionStatus" min-width="108" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :class="`task-ai-preview__tag--${row.statusTone}`" effect="light">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="text.auditStatus" min-width="108" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :class="`task-ai-preview__tag--${row.auditTone}`" effect="light">
              {{ row.auditStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="150" align="center" header-align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="isLoadingDetail && selectedTaskItemId === row.taskItemId"
              @click.stop="handleViewDetail(row)"
            >
              {{ text.viewDetail }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <aside class="task-ai-preview__detail-card">
      <header class="task-ai-preview__detail-header">
        <div>
          <span>AI Detail</span>
          <h3>{{ text.aiPreReviewDetails }}</h3>
        </div>
      </header>

      <p v-if="!selectedTaskItemId" class="task-ai-preview__notice">{{ text.selectResult }}</p>
      <p v-else-if="isLoadingDetail" class="task-ai-preview__notice">{{ text.loadingDetail }}</p>
      <div v-else-if="detailErrorMessage" class="task-ai-preview__notice task-ai-preview__notice--error">
        <span>{{ detailErrorMessage }}</span>
        <el-button link type="primary" @click="loadPreviewDetail()">{{ text.reload }}</el-button>
      </div>
      <p v-else-if="!selectedDetail" class="task-ai-preview__notice">{{ text.selectResult }}</p>

      <div v-else class="task-ai-preview__detail-content">
        <section class="task-ai-preview__meta-grid" aria-label="AI 预审详情摘要">
          <article
            v-for="item in detailMetaItems"
            :key="item.label"
            class="task-ai-preview__meta-card"
            :class="`task-ai-preview__meta-card--${item.tone}`"
          >
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </section>

        <section class="task-ai-preview__section">
          <header class="task-ai-preview__section-header">
            <h4>{{ text.dimensionScores }}</h4>
            <span>score / passScore / weight</span>
          </header>
          <el-table
            :data="detailScoreRows"
            stripe
            class="task-ai-preview__score-table"
            empty-text="暂无维度评分"
            max-height="220"
          >
            <el-table-column prop="dimension" label="维度" min-width="126" show-overflow-tooltip />
            <el-table-column label="得分" min-width="170">
              <template #default="{ row }">
                <div class="task-ai-preview__score-cell">
                  <span class="task-ai-preview__score-track">
                    <span
                      class="task-ai-preview__score-bar"
                      :class="`task-ai-preview__score-bar--${getScoreTone(row.score, row.passScore)}`"
                      :style="{ width: `${clampPercent(row.score)}%` }"
                    ></span>
                  </span>
                  <strong>{{ formatNumber(row.score, '0') }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="通过分" width="82" align="center" header-align="center">
              <template #default="{ row }">
                {{ formatNumber(row.passScore, '0') }}
              </template>
            </el-table-column>
            <el-table-column label="权重" width="72" align="center" header-align="center">
              <template #default="{ row }">
                {{ formatNumber(row.weight, '0') }}
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="detailReasonRows.length" class="task-ai-preview__section">
          <header class="task-ai-preview__section-header">
            <h4>{{ text.dimensionReasons }}</h4>
            <span>dimension reason</span>
          </header>
          <div class="task-ai-preview__reason-list">
            <article v-for="row in detailReasonRows" :key="`${row.id}-reason`">
              <strong>{{ row.dimension }}</strong>
              <p>{{ row.reason }}</p>
            </article>
          </div>
        </section>

        <section
          v-if="selectedDetail.summary || selectedDetail.lastError || detailMediaResultText"
          class="task-ai-preview__section task-ai-preview__summary-section"
        >
          <header class="task-ai-preview__section-header">
            <h4>{{ text.summary }}</h4>
            <span>summary / lastError / mediaResult</span>
          </header>
          <p v-if="selectedDetail.summary" class="task-ai-preview__summary-text">{{ selectedDetail.summary }}</p>
          <p v-if="selectedDetail.lastError" class="task-ai-preview__error-text">{{ selectedDetail.lastError }}</p>
          <pre v-if="detailMediaResultText" class="task-ai-preview__media-result">{{ detailMediaResultText }}</pre>
        </section>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.task-ai-preview {
  box-sizing: border-box;
  display: grid;
  width: 1060px;
  height: 620px;
  grid-template-columns: 590px 456px;
  gap: 14px;
  overflow: hidden;
}

.task-ai-preview__list-card,
.task-ai-preview__detail-card {
  display: grid;
  min-height: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.task-ai-preview__list-card {
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
}

.task-ai-preview__detail-card {
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  padding: 14px;
}

.task-ai-preview__toolbar,
.task-ai-preview__detail-header,
.task-ai-preview__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-ai-preview__toolbar {
  padding: 14px;
}

.task-ai-preview__detail-header {
  border-bottom: 1px solid #edf1f5;
  padding: 0 0 12px;
}

.task-ai-preview__toolbar span,
.task-ai-preview__detail-header span,
.task-ai-preview__section-header span {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0;
  text-transform: uppercase;
}

.task-ai-preview__toolbar h3,
.task-ai-preview__detail-header h3,
.task-ai-preview__section-header h4 {
  margin: 4px 0 0;
  color: #172033;
  font-size: 15px;
}

.task-ai-preview__section-header h4 {
  margin: 0;
  font-size: 14px;
}

.task-ai-preview__table {
  min-height: 0;
  border-top: 1px solid #edf1f5;
}

.task-ai-preview__table :deep(.el-table__body-wrapper) {
  cursor: pointer;
}

.task-ai-preview__table :deep(.task-ai-preview__row--active > .el-table__cell) {
  background: #f5f0ff !important;
}

.task-ai-preview__table :deep(th.el-table__cell) {
  color: #172033;
  background: #f8fafc;
  font-weight: 850;
}

.task-ai-preview__table :deep(.el-table__cell) {
  color: #475569;
  font-size: 12px;
}

.task-ai-preview__tag--neutral,
.task-ai-preview__tag--primary,
.task-ai-preview__tag--warning,
.task-ai-preview__tag--danger,
.task-ai-preview__tag--success {
  border: 0;
  border-radius: 999px;
  font-weight: 850;
}

.task-ai-preview__tag--neutral {
  color: #64748b;
  background: #f1f5f9;
}

.task-ai-preview__tag--primary {
  color: #2563eb;
  background: #dbeafe;
}

.task-ai-preview__tag--warning {
  color: #d97706;
  background: #fef3c7;
}

.task-ai-preview__tag--danger {
  color: #dc2626;
  background: #fee2e2;
}

.task-ai-preview__tag--success {
  color: #16a34a;
  background: #dcfce7;
}

.task-ai-preview__notice {
  display: grid;
  min-height: 112px;
  place-items: center;
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 750;
  text-align: center;
}

.task-ai-preview__notice--error {
  gap: 4px;
  color: #dc2626;
}

.task-ai-preview__detail-content {
  display: grid;
  min-height: 0;
  align-content: start;
  gap: 12px;
  overflow-y: auto;
  padding: 14px 8px 4px 0;
}

.task-ai-preview__meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.task-ai-preview__meta-card {
  display: grid;
  min-width: 0;
  gap: 6px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px;
}

.task-ai-preview__meta-card span {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-ai-preview__meta-card strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-ai-preview__meta-card--success {
  border-color: rgba(22, 163, 74, 0.18);
  background: #f0fdf4;
}

.task-ai-preview__meta-card--warning {
  border-color: rgba(249, 115, 22, 0.18);
  background: #fff7ed;
}

.task-ai-preview__meta-card--danger {
  border-color: rgba(239, 68, 68, 0.18);
  background: #fff1f2;
}

.task-ai-preview__meta-card--primary {
  border-color: rgba(37, 99, 235, 0.18);
  background: #eff6ff;
}

.task-ai-preview__section {
  display: grid;
  gap: 10px;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #ffffff;
  padding: 12px;
}

.task-ai-preview__score-table {
  overflow: hidden;
  border: 1px solid #eef2f7;
  border-radius: 8px;
}

.task-ai-preview__score-table :deep(.el-table__cell) {
  padding: 7px 0;
  font-size: 12px;
}

.task-ai-preview__score-table :deep(th.el-table__cell) {
  color: #334155;
  background: #f8fafc;
  font-weight: 850;
}

.task-ai-preview__score-cell {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr) 42px;
  align-items: center;
  gap: 8px;
}

.task-ai-preview__score-track {
  display: block;
  overflow: hidden;
  height: 7px;
  border-radius: 999px;
  background: #eef2f7;
}

.task-ai-preview__score-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #f97316;
}

.task-ai-preview__score-bar--success {
  background: #16a34a;
}

.task-ai-preview__score-bar--warning {
  background: #f97316;
}

.task-ai-preview__score-bar--danger {
  background: #ef4444;
}

.task-ai-preview__score-cell strong {
  color: #334155;
  font-size: 12px;
  font-weight: 950;
  text-align: right;
}

.task-ai-preview__reason-list {
  display: grid;
  gap: 8px;
}

.task-ai-preview__reason-list article {
  display: grid;
  gap: 6px;
  border: 1px solid #f0e3e3;
  border-radius: 8px;
  background: #fffafa;
  padding: 10px;
}

.task-ai-preview__reason-list strong {
  color: #991b1b;
  font-size: 13px;
  font-weight: 900;
}

.task-ai-preview__reason-list p,
.task-ai-preview__summary-text,
.task-ai-preview__error-text,
.task-ai-preview__media-result {
  margin: 0;
  color: #374151;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.task-ai-preview__summary-section {
  background: linear-gradient(180deg, #fff7f7 0%, #ffffff 100%);
}

.task-ai-preview__error-text {
  color: #b91c1c;
}

.task-ai-preview__media-result {
  max-height: 168px;
  overflow: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  padding: 10px;
  white-space: pre-wrap;
}

</style>
