<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import loadingAnimation from '../../icon/loading.gif'
import { DeleteDataset, GetDataset, GetDatasetDetail, GetMyDataset, PostDataset, PreviewDataset } from '../../utils/api'

interface DatasetListItem {
  id: string
  name: string
  description: string
  sourceFormat: string
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  createdAt: string
  tags: string[]
}

interface BackendDatasetRecord {
  id?: string | number
  datasetId?: string | number
  name?: string
  datasetName?: string
  title?: string
  description?: string
  desc?: string
  sourceFormat?: string
  format?: string
  fileType?: string
  status?: string
  totalCount?: number | string
  sampleCount?: number | string
  itemCount?: number | string
  count?: number | string
  successCount?: number | string
  failedCount?: number | string
  createdAt?: string
  createTime?: string
  updatedAt?: string
  updateTime?: string
  tags?: unknown
  category?: string
  scene?: string
  owner?: string
  [key: string]: unknown
}

interface BackendDatasetFileRecord {
  id?: string | number
  datasetId?: string | number
  originalName?: string
  fileUrl?: string
  storagePath?: string
  contentType?: string
  fileSize?: string | number
  fileHash?: string
  format?: string
  createTime?: string
  createdAt?: string
  [key: string]: unknown
}

interface BackendDatasetDetailRecord extends BackendDatasetRecord {
  schema?: unknown
  createdBy?: string | number
  files?: BackendDatasetFileRecord[]
}

interface DatasetDetailTableRow {
  label: string
  value: string
}

interface DatasetFileTableRow {
  id: string
  originalName: string
  format: string
  contentType: string
  fileSize: string
  createTime: string
  storagePath: string
}

interface DatasetSchemaFieldTableRow {
  enabled: boolean
  enabledText: string
  sourceName: string
  targetName: string
}

interface DatasetPreviewFieldRecord {
  sourceName?: string
  sampleValue?: unknown
  [key: string]: unknown
}

interface DatasetPreviewFieldRow {
  id: string
  originalSourceName: string
  sourceName: string
  sampleValue: string
  enabled: boolean
}

type DatasetListScope = 'all' | 'mine'

const router = useRouter()

const datasetPageSize = 8
const datasetStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PARSED', label: '已解析' },
  { value: 'PARSING', label: '解析中' },
  { value: 'FAILED', label: '解析失败' },
]
const datasetFormatOptions = [
  { value: '', label: '全部格式' },
  { value: 'xlsx', label: 'Excel (.xlsx)' },
  { value: 'csv', label: 'CSV' },
  { value: 'json', label: 'JSON' },
  { value: 'jsonl', label: 'JSONL' },
]
const datasetScopeTabs: Array<{ key: DatasetListScope; label: string }> = [
  { key: 'all', label: '数据集列表' },
  { key: 'mine', label: '我的数据集' },
]

const datasets = shallowRef<DatasetListItem[]>([])
const isLoadingDatasets = shallowRef(false)
const isPageSwitchLoadingOpen = shallowRef(false)
const datasetErrorMessage = shallowRef('')
const listAnimationKey = shallowRef(0)
const currentPage = shallowRef(1)
const displayPage = shallowRef(1)
const totalDatasets = shallowRef(0)
const activeDatasetScope = shallowRef<DatasetListScope>('all')
const datasetKeyword = shallowRef('')
const datasetStatusFilter = shallowRef('')
const datasetFormatFilter = shallowRef('')
const deletingDatasetId = shallowRef('')
const isUploadDialogOpen = shallowRef(false)
const isUploadingDataset = shallowRef(false)
const uploadErrorMessage = shallowRef('')
const uploadFile = shallowRef<File | null>(null)
const isDatasetPreviewOpen = shallowRef(false)
const isPreviewingDataset = shallowRef(false)
const previewDatasetSourceFormat = shallowRef('')
const previewDatasetRows = shallowRef<DatasetPreviewFieldRow[]>([])
const savedPreviewRows = shallowRef<DatasetPreviewFieldRow[]>([])
const previewSchema = shallowRef('')
const isPreviewSchemaSaved = shallowRef(false)
const previewErrorMessage = shallowRef('')
const isDetailDialogOpen = shallowRef(false)
const isLoadingDatasetDetail = shallowRef(false)
const datasetDetailErrorMessage = shallowRef('')
const selectedDataset = shallowRef<DatasetListItem | null>(null)
const datasetDetail = shallowRef<BackendDatasetDetailRecord | null>(null)

const uploadForm = reactive({
  name: '',
  description: '',
})

const activeDatasetScopeLabel = computed(() =>
  activeDatasetScope.value === 'mine' ? '我的数据集' : '数据集列表',
)

const emptyDatasetText = computed(() =>
  activeDatasetScope.value === 'mine' ? '暂无我的数据集' : '暂无数据集',
)

const datasetCountText = computed(() =>
  isLoadingDatasets.value ? '加载中' : `共 ${totalDatasets.value} 个数据集`,
)

const shouldShowInlineLoading = computed(
  () => isLoadingDatasets.value && !isPageSwitchLoadingOpen.value && datasets.value.length === 0,
)

const isDatasetLoadingDialogOpen = computed(() =>
  isPageSwitchLoadingOpen.value || isUploadingDataset.value,
)

const uploadFileName = computed(() => uploadFile.value?.name ?? '请选择数据文件')
const previewDatasetSourceFormatText = computed(() =>
  previewDatasetSourceFormat.value ? formatSourceFormat(previewDatasetSourceFormat.value) : '-',
)
const datasetPreviewButtonText = computed(() => {
  if (isPreviewingDataset.value) {
    return '预览中'
  }

  if (isDatasetPreviewOpen.value) {
    return '收起字段预览'
  }

  return '展开字段预览'
})

const datasetDetailTitle = computed(() =>
  selectedDataset.value?.name ? `数据集详情：${selectedDataset.value.name}` : '数据集详情',
)

const datasetDownloadFile = computed(() => {
  const files = Array.isArray(datasetDetail.value?.files) ? datasetDetail.value.files : []

  return files.find((file) => typeof file.fileUrl === 'string' && Boolean(file.fileUrl.trim())) ?? null
})

const datasetDownloadUrl = computed(() => datasetDownloadFile.value?.fileUrl?.trim() ?? '')

const datasetDownloadFileName = computed(() =>
  readString(datasetDownloadFile.value?.originalName, selectedDataset.value?.name ?? 'dataset'),
)

const datasetDetailRows = computed<DatasetDetailTableRow[]>(() => {
  const detail = datasetDetail.value

  if (!detail) {
    return []
  }

  const rows: DatasetDetailTableRow[] = [
    { label: '数据集ID', value: formatDetailValue(detail.id) },
    { label: '数据集名称', value: formatDetailValue(detail.name ?? detail.datasetName ?? detail.title) },
    { label: '数据集描述', value: formatDetailValue(detail.description ?? detail.desc) },
    { label: '文件格式', value: formatSourceFormat(detail.sourceFormat ?? detail.format ?? detail.fileType) },
    { label: '解析状态', value: formatDatasetStatus(detail.status) },
    { label: '数据总量', value: formatDetailValue(detail.totalCount ?? detail.sampleCount ?? detail.itemCount ?? detail.count) },
    { label: '成功数量', value: formatDetailValue(detail.successCount) },
    { label: '失败数量', value: formatDetailValue(detail.failedCount) },
    { label: '创建人ID', value: formatDetailValue(detail.createdBy) },
    { label: '创建时间', value: formatDetailValue(detail.createTime ?? detail.createdAt) },
    { label: '更新时间', value: formatDetailValue(detail.updateTime ?? detail.updatedAt) },
  ]

  return rows.filter((row) => row.value !== '-')
})

const datasetSchemaFieldRows = computed<DatasetSchemaFieldTableRow[]>(() =>
  normalizeSchemaFields(datasetDetail.value?.schema),
)

const datasetFileRows = computed<DatasetFileTableRow[]>(() => {
  const files = Array.isArray(datasetDetail.value?.files) ? datasetDetail.value.files : []

  return files.map((file, index) => ({
    id: formatDetailValue(file.id ?? index + 1),
    originalName: formatDetailValue(file.originalName),
    format: formatSourceFormat(file.format),
    contentType: formatDetailValue(file.contentType),
    fileSize: formatFileSize(file.fileSize),
    createTime: formatDetailValue(file.createTime ?? file.createdAt),
    storagePath: formatDetailValue(file.storagePath),
  }))
})

const loadingTextChars = '正在加载中.....'.split('')

let pageSwitchLoadingSeed = 0
let datasetListRequestSeed = 0
let datasetDetailRequestSeed = 0
let datasetPreviewRequestSeed = 0

function getResponsePayload(response: any) {
  return response?.data ?? response
}

function pickDatasetArray(response: any) {
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

function readString(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : fallback
}

function formatDetailValue(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return '-'
  }

  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return String(value)
    }
  }

  return String(value)
}

function formatFileSize(value: unknown) {
  const bytes = Number(value)

  if (!Number.isFinite(bytes)) {
    return '-'
  }

  if (bytes < 1024) {
    return `${bytes} B`
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }

  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function parseJsonLikeValue(value: unknown) {
  if (typeof value !== 'string') {
    return value
  }

  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function normalizeSchemaFields(schema: unknown): DatasetSchemaFieldTableRow[] {
  const parsedSchema = parseJsonLikeValue(schema)

  if (!parsedSchema || typeof parsedSchema !== 'object') {
    return []
  }

  if (Array.isArray(parsedSchema)) {
    return parsedSchema.map((field, index) => normalizeSchemaField(field, index))
  }

  const schemaRecord = parsedSchema as Record<string, unknown>
  const fields = schemaRecord.fields
  const parsedFields = parseJsonLikeValue(fields)

  if (Array.isArray(parsedFields)) {
    return parsedFields.map((field, index) => normalizeSchemaField(field, index))
  }

  return []
}

function normalizeSchemaField(field: unknown, index: number): DatasetSchemaFieldTableRow {
  if (!field || typeof field !== 'object') {
    return {
      enabled: false,
      enabledText: '禁用',
      sourceName: `字段 ${index + 1}`,
      targetName: formatDetailValue(field),
    }
  }

  const fieldRecord = field as Record<string, unknown>
  const enabled = readEnabledValue(fieldRecord.enabled)

  return {
    enabled,
    enabledText: enabled ? '启用' : '禁用',
    sourceName: readString(fieldRecord.sourceName, `字段 ${index + 1}`),
    targetName: readString(fieldRecord.targetName, '-'),
  }
}

function readEnabledValue(value: unknown) {
  if (typeof value === 'boolean') {
    return value
  }

  if (typeof value === 'number') {
    return value === 1
  }

  if (typeof value === 'string') {
    return value.toLowerCase() === 'true' || value === '1'
  }

  return false
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
    nestedPayload?.totalCount,
    nestedPayload?.totalElements,
    nestedPayload?.count,
  ]

  const total = candidates.map((item) => Number(item)).find((item) => Number.isFinite(item))

  return total ?? fallback
}

function formatSourceFormat(value: unknown) {
  const rawFormat = readString(value, '未知格式')
  const upperFormat = rawFormat.toUpperCase()
  const formatMap: Record<string, string> = {
    CSV: 'CSV',
    EXCEL: 'Excel',
    XLS: 'Excel',
    XLSX: 'Excel',
    JSON: 'JSON',
    JSONL: 'JSONL',
  }

  return formatMap[upperFormat] ?? rawFormat
}

function formatDatasetStatus(value: unknown) {
  const rawStatus = readString(value, '未知状态')
  const upperStatus = rawStatus.toUpperCase()
  const statusMap: Record<string, string> = {
    PENDING: '待解析',
    PARSING: '解析中',
    PARSED: '已解析',
    FAILED: '解析失败',
    FAIL: '解析失败',
  }

  return statusMap[upperStatus] ?? rawStatus
}

function readTags(record: BackendDatasetRecord, sourceFormat: string, status: string, totalCount: number) {
  if (Array.isArray(record.tags)) {
    return record.tags.filter((tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()))
  }

  return [
    sourceFormat,
    status,
    totalCount >= 0 ? `${totalCount} 条` : '',
    record.scene,
    record.category,
    record.owner,
  ].filter((tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()))
}

function normalizeDatasetRecord(record: unknown, index: number): DatasetListItem {
  const dataset = (record ?? {}) as BackendDatasetRecord
  const sourceFormat = formatSourceFormat(dataset.sourceFormat ?? dataset.format ?? dataset.fileType)
  const status = formatDatasetStatus(dataset.status)
  const totalCount = readNumber(
    dataset.totalCount ?? dataset.sampleCount ?? dataset.itemCount ?? dataset.count,
    0,
  )

  return {
    id: String(dataset.id ?? dataset.datasetId ?? `dataset-${index}`),
    name: readString(dataset.name ?? dataset.datasetName ?? dataset.title, `未命名数据集 ${index + 1}`),
    description: readString(dataset.description ?? dataset.desc, '暂无数据集描述'),
    sourceFormat,
    status,
    totalCount,
    successCount: readNumber(dataset.successCount, 0),
    failedCount: readNumber(dataset.failedCount, 0),
    createdAt: readString(
      dataset.createdAt ?? dataset.createTime ?? dataset.updatedAt ?? dataset.updateTime,
      '暂无创建时间',
    ),
    tags: readTags(dataset, sourceFormat, status, totalCount),
  }
}

function normalizeDatasetList(response: any) {
  return pickDatasetArray(response).map((item, index) => normalizeDatasetRecord(item, index))
}

function pickDatasetDetail(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  return nestedPayload && typeof nestedPayload === 'object' && !Array.isArray(nestedPayload)
    ? nestedPayload as BackendDatasetDetailRecord
    : null
}

function pickDatasetPreview(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  return nestedPayload && typeof nestedPayload === 'object' && !Array.isArray(nestedPayload)
    ? nestedPayload as { sourceFormat?: string; fields?: DatasetPreviewFieldRecord[] }
    : { sourceFormat: '', fields: [] }
}

function clonePreviewRows(rows: DatasetPreviewFieldRow[]) {
  return rows.map((row) => ({ ...row }))
}

function normalizePreviewRows(fields: unknown): DatasetPreviewFieldRow[] {
  if (!Array.isArray(fields)) {
    return []
  }

  return fields.map((field, index) => {
    const fieldRecord = (field ?? {}) as DatasetPreviewFieldRecord
    const sourceName = readString(fieldRecord.sourceName, `字段 ${index + 1}`)

    return {
      id: `${sourceName}-${index}`,
      originalSourceName: sourceName,
      sourceName,
      sampleValue: formatDetailValue(fieldRecord.sampleValue),
      enabled: true,
    }
  })
}

function resetDatasetPreview() {
  datasetPreviewRequestSeed += 1
  isDatasetPreviewOpen.value = false
  isPreviewingDataset.value = false
  previewDatasetSourceFormat.value = ''
  previewDatasetRows.value = []
  savedPreviewRows.value = []
  previewSchema.value = ''
  isPreviewSchemaSaved.value = false
  previewErrorMessage.value = ''
}

function markPreviewSchemaDirty() {
  previewSchema.value = ''
  isPreviewSchemaSaved.value = false
}

function updatePreviewSourceName(rowId: string, value: string) {
  previewDatasetRows.value = previewDatasetRows.value.map((row) =>
    row.id === rowId
      ? {
          ...row,
          sourceName: value,
        }
      : row,
  )
  markPreviewSchemaDirty()
}

function updatePreviewEnabled(rowId: string, value: boolean) {
  previewDatasetRows.value = previewDatasetRows.value.map((row) =>
    row.id === rowId
      ? {
          ...row,
          enabled: value,
        }
      : row,
  )
  markPreviewSchemaDirty()
}

function buildPreviewSchemaText() {
  return JSON.stringify({
    fields: previewDatasetRows.value.map((row) => ({
      sourceName: row.originalSourceName,
      targetName: row.sourceName.trim() || row.originalSourceName,
      enabled: row.enabled,
    })),
  })
}

function handleSaveDatasetPreview() {
  if (!previewDatasetRows.value.length) {
    ElMessage.warning('暂无可保存的预览字段')
    return
  }

  const emptyField = previewDatasetRows.value.find((row) => !row.sourceName.trim())

  if (emptyField) {
    ElMessage.warning('字段名称不能为空')
    return
  }

  previewSchema.value = buildPreviewSchemaText()
  savedPreviewRows.value = clonePreviewRows(previewDatasetRows.value)
  isPreviewSchemaSaved.value = true
  previewErrorMessage.value = ''
  ElMessage.success('字段预览配置已保存')
}

function handleCancelDatasetPreview() {
  if (savedPreviewRows.value.length) {
    previewDatasetRows.value = clonePreviewRows(savedPreviewRows.value)
    isPreviewSchemaSaved.value = true
    previewSchema.value = buildPreviewSchemaText()
  } else {
    resetDatasetPreview()
  }

  isDatasetPreviewOpen.value = false
  previewErrorMessage.value = ''
}

async function handleToggleDatasetPreview() {
  const file = uploadFile.value

  if (!file) {
    ElMessage.warning('请上传数据集')
    previewErrorMessage.value = '请上传数据集'
    return
  }

  if (isDatasetPreviewOpen.value) {
    isDatasetPreviewOpen.value = false
    return
  }

  if (previewDatasetRows.value.length) {
    isDatasetPreviewOpen.value = true
    return
  }

  isPreviewingDataset.value = true
  isDatasetPreviewOpen.value = true
  previewErrorMessage.value = ''
  const requestSeed = (datasetPreviewRequestSeed += 1)

  try {
    const response = await PreviewDataset(file)

    if (requestSeed !== datasetPreviewRequestSeed) {
      return
    }

    const isSuccess = response?.code === undefined || Number(response?.code) === 200

    if (!isSuccess) {
      previewErrorMessage.value = response?.message || response?.msg || '数据集字段预览失败'
      ElMessage.error(previewErrorMessage.value)
      return
    }

    const previewData = pickDatasetPreview(response)
    previewDatasetSourceFormat.value = readString(previewData.sourceFormat, '')
    previewDatasetRows.value = normalizePreviewRows(previewData.fields)
    savedPreviewRows.value = []
    previewSchema.value = ''
    isPreviewSchemaSaved.value = false
  } catch (error: any) {
    if (requestSeed !== datasetPreviewRequestSeed) {
      return
    }

    previewErrorMessage.value =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || '数据集字段预览失败'
    ElMessage.error(previewErrorMessage.value)
  } finally {
    if (requestSeed === datasetPreviewRequestSeed) {
      isPreviewingDataset.value = false
    }
  }
}

function waitForLoadingDuration() {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, 1000)
  })
}

async function playPageSwitchLoading() {
  pageSwitchLoadingSeed += 1
  const currentSeed = pageSwitchLoadingSeed

  isPageSwitchLoadingOpen.value = true
  await waitForLoadingDuration()

  if (currentSeed === pageSwitchLoadingSeed) {
    isPageSwitchLoadingOpen.value = false
  }
}

async function loadDatasets(pageNum = currentPage.value, showPageSwitchLoading = false) {
  const requestSeed = (datasetListRequestSeed += 1)
  const requestScope = activeDatasetScope.value
  const requestDatasets = requestScope === 'mine' ? GetMyDataset : GetDataset

  isLoadingDatasets.value = true
  datasetErrorMessage.value = ''

  const loadingTask = showPageSwitchLoading ? playPageSwitchLoading() : Promise.resolve()

  try {
    const response = await requestDatasets({
      pageNum,
      pageSize: datasetPageSize,
      keyword: datasetKeyword.value.trim() || undefined,
      status: datasetStatusFilter.value || undefined,
      sourceFormat: datasetFormatFilter.value || undefined,
    })

    const normalizedDatasets = normalizeDatasetList(response)
    const nextDatasets = normalizedDatasets.slice(0, datasetPageSize)
    const nextTotalDatasets = pickPaginationTotal(response, normalizedDatasets.length)

    await loadingTask

    if (requestSeed !== datasetListRequestSeed || requestScope !== activeDatasetScope.value) {
      return
    }

    datasets.value = nextDatasets
    totalDatasets.value = nextTotalDatasets
    currentPage.value = pageNum
    displayPage.value = pageNum
    listAnimationKey.value += 1
  } catch (error: any) {
    await loadingTask

    if (requestSeed !== datasetListRequestSeed || requestScope !== activeDatasetScope.value) {
      return
    }

    datasetErrorMessage.value =
      error?.response?.data?.message || error?.response?.data?.msg || `${activeDatasetScopeLabel.value}获取失败`

    listAnimationKey.value += 1
    ElMessage.error(datasetErrorMessage.value)
  } finally {
    if (requestSeed === datasetListRequestSeed) {
      isLoadingDatasets.value = false
    }
  }
}

function handleDatasetFilterSubmit() {
  currentPage.value = 1
  displayPage.value = 1
  void loadDatasets(1, true)
}

function handleDatasetFilterReset() {
  datasetKeyword.value = ''
  datasetStatusFilter.value = ''
  datasetFormatFilter.value = ''
  handleDatasetFilterSubmit()
}

function handlePageChange(pageNum: number) {
  loadDatasets(pageNum, true)
}

function handleDatasetScopeChange(scope: DatasetListScope) {
  if (activeDatasetScope.value === scope) {
    return
  }

  activeDatasetScope.value = scope
  datasets.value = []
  totalDatasets.value = 0
  currentPage.value = 1
  displayPage.value = 1
  loadDatasets(1, true)
}

function handleOpenUploadDialog() {
  uploadErrorMessage.value = ''
  isUploadDialogOpen.value = true
}

function resetUploadForm() {
  uploadForm.name = ''
  uploadForm.description = ''
  uploadFile.value = null
  uploadErrorMessage.value = ''
  resetDatasetPreview()
}

function handleCloseUploadDialog() {
  if (isUploadingDataset.value) {
    return
  }

  isUploadDialogOpen.value = false
  resetUploadForm()
}

function handleDatasetFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  uploadFile.value = input.files?.[0] ?? null
  resetDatasetPreview()
}

async function handleUploadDatasetSubmit() {
  const name = uploadForm.name.trim()
  const description = uploadForm.description.trim()
  const file = uploadFile.value

  if (!name || !description || !file) {
    uploadErrorMessage.value = '请填写数据集名称、描述并选择数据文件。'
    return
  }

  if (previewDatasetRows.value.length && !isPreviewSchemaSaved.value) {
    uploadErrorMessage.value = '请先保存字段预览配置。'
    ElMessage.warning(uploadErrorMessage.value)
    return
  }

  isUploadingDataset.value = true
  uploadErrorMessage.value = ''

  try {
    const response = await PostDataset(name, description, file, previewSchema.value || undefined)
    const isSuccess = response?.code === undefined || Number(response?.code) === 200

    if (!isSuccess) {
      isUploadingDataset.value = false
      uploadErrorMessage.value = response?.message || response?.msg || '数据集上传失败'
      ElMessage.error(uploadErrorMessage.value)
      return
    }

    isUploadingDataset.value = false
    ElMessage.success(response?.message || response?.msg || '数据集上传成功')
    isUploadDialogOpen.value = false
    resetUploadForm()
    await router.push({ name: 'owner-datasets' })
    await loadDatasets(1)
  } catch (error: any) {
    isUploadingDataset.value = false
    uploadErrorMessage.value =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || '数据集上传失败'

    ElMessage.error(uploadErrorMessage.value)
  } finally {
    if (isUploadingDataset.value) {
      isUploadingDataset.value = false
    }
  }
}

function handleCloseDetailDialog() {
  datasetDetailRequestSeed += 1
  isDetailDialogOpen.value = false
  isLoadingDatasetDetail.value = false
  selectedDataset.value = null
  datasetDetail.value = null
  datasetDetailErrorMessage.value = ''
}

function handleDialogEscape(event: KeyboardEvent) {
  if (event.key !== 'Escape') {
    return
  }

  if (isUploadDialogOpen.value) {
    handleCloseUploadDialog()
    return
  }

  if (isDetailDialogOpen.value) {
    handleCloseDetailDialog()
  }
}

async function handleViewDataset(dataset: DatasetListItem) {
  if (isLoadingDatasetDetail.value) {
    return
  }

  selectedDataset.value = dataset
  datasetDetail.value = null
  datasetDetailErrorMessage.value = ''
  isDetailDialogOpen.value = true
  isLoadingDatasetDetail.value = true
  const requestSeed = (datasetDetailRequestSeed += 1)

  try {
    const response = await GetDatasetDetail(dataset.id)

    if (requestSeed !== datasetDetailRequestSeed) {
      return
    }

    datasetDetail.value = pickDatasetDetail(response)

    if (!datasetDetail.value) {
      datasetDetailErrorMessage.value = '数据集详情为空'
    }
  } catch (error: any) {
    if (requestSeed !== datasetDetailRequestSeed) {
      return
    }

    datasetDetailErrorMessage.value =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || '数据集详情获取失败'

    ElMessage.error(datasetDetailErrorMessage.value)
  } finally {
    if (requestSeed === datasetDetailRequestSeed) {
      isLoadingDatasetDetail.value = false
    }
  }
}

async function handleDeleteDataset(dataset: DatasetListItem) {
  if (deletingDatasetId.value) {
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除数据集「${dataset.name}」吗？`, '删除数据集', {
      cancelButtonText: '取消',
      confirmButtonText: '删除',
      type: 'warning',
    })
  } catch {
    return
  }

  deletingDatasetId.value = dataset.id

  try {
    const response = await DeleteDataset(dataset.id)
    const isSuccess = response?.code === undefined || Number(response?.code) === 200

    if (!isSuccess) {
      ElMessage.error(response?.message || response?.msg || '数据集删除失败')
      return
    }

    ElMessage.success(response?.message || response?.msg || '数据集删除成功')

    const nextPage =
      datasets.value.length === 1 && currentPage.value > 1
        ? currentPage.value - 1
        : currentPage.value

    await loadDatasets(nextPage)
  } catch (error: any) {
    const errorMessage =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || '数据集删除失败'

    ElMessage.error(errorMessage)
  } finally {
    deletingDatasetId.value = ''
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleDialogEscape)
  loadDatasets()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleDialogEscape)
})
</script>

<template>
  <section class="dataset-page">
    <header class="dataset-page__header">
      <p class="dataset-page__eyebrow">项目管理者</p>
      <h1>数据集</h1>
      <p>维护数据集、样本来源和数据批次，快速查看解析状态与数据规模。</p>
    </header>

    <div class="dataset-page__toolbar">
      <div class="dataset-page__list-summary">
        <div class="dataset-page__tabs" role="tablist" aria-label="数据集列表筛选">
          <button
            v-for="tab in datasetScopeTabs"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="activeDatasetScope === tab.key"
            :class="{ 'dataset-page__tab--active': activeDatasetScope === tab.key }"
            @click="handleDatasetScopeChange(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
        <span>{{ datasetCountText }}</span>
      </div>

      <button class="dataset-page__create" type="button" @click="handleOpenUploadDialog">
        上传数据集
      </button>
    </div>

    <form class="dataset-page__filters" aria-label="数据集筛选" @submit.prevent="handleDatasetFilterSubmit">
      <el-input
        v-model="datasetKeyword"
        class="dataset-page__filter-search"
        clearable
        placeholder="搜索数据集名称或描述"
        aria-label="搜索数据集名称或描述"
        @keyup.enter="handleDatasetFilterSubmit"
      />
      <el-select v-model="datasetStatusFilter" class="dataset-page__filter-select" aria-label="解析状态">
        <el-option v-for="option in datasetStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-select v-model="datasetFormatFilter" class="dataset-page__filter-select" aria-label="文件格式">
        <el-option v-for="option in datasetFormatOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <button class="dataset-page__filter-submit" type="submit">筛选</button>
      <button class="dataset-page__filter-reset" type="button" @click="handleDatasetFilterReset">重置</button>
    </form>

    <Transition name="dataset-page-loading">
      <div
        v-if="isDatasetLoadingDialogOpen"
        class="dataset-page__loading-backdrop"
        role="presentation"
      >
        <section class="dataset-page__loading-dialog" aria-label="正在加载中">
          <img class="dataset-page__loading-image" :src="loadingAnimation" alt="">
          <p class="dataset-page__loading-text" aria-live="polite">
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

    <Transition name="dataset-page-dialog">
      <div
        v-if="isUploadDialogOpen"
        class="dataset-page__dialog-backdrop"
        role="presentation"
        @click.self="handleCloseUploadDialog"
      >
        <form
          class="dataset-page__dialog dataset-page__upload-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="dataset-upload-dialog-title"
          @submit.prevent="handleUploadDatasetSubmit"
        >
          <header class="dataset-page__dialog-header">
            <span>上传数据集</span>
            <strong id="dataset-upload-dialog-title">填写数据集基础信息</strong>
            <button
              class="dataset-page__dialog-close"
              type="button"
              aria-label="关闭上传数据集弹窗"
              :disabled="isUploadingDataset"
              @click="handleCloseUploadDialog"
            >
              ×
            </button>
          </header>

          <label class="dataset-page__dialog-field">
            <span>数据集名称</span>
            <input
              v-model.trim="uploadForm.name"
              name="datasetName"
              placeholder="商品评价数据集"
            >
          </label>

          <label class="dataset-page__dialog-field">
            <span>数据集描述</span>
            <textarea
              v-model.trim="uploadForm.description"
              name="datasetDescription"
              placeholder="用于商品评价清洗和标注的数据集"
            ></textarea>
          </label>

          <div class="dataset-page__dialog-file-row">
            <label class="dataset-page__dialog-file">
              <span>数据文件</span>
              <strong>{{ uploadFileName }}</strong>
              <input
                type="file"
                accept=".csv,.json,.jsonl,.xlsx"
                @change="handleDatasetFileChange"
              >
            </label>
            <button
              class="dataset-page__preview-toggle"
              type="button"
              :disabled="isPreviewingDataset"
              @click="handleToggleDatasetPreview"
            >
              {{ datasetPreviewButtonText }}
            </button>
          </div>

          <Transition name="dataset-preview">
            <section v-if="isDatasetPreviewOpen" class="dataset-page__preview-panel" aria-label="字段预览">
              <header class="dataset-page__preview-header">
                <div>
                  <span>文件类型</span>
                  <strong>{{ previewDatasetSourceFormatText }}</strong>
                </div>
                <em v-if="isPreviewSchemaSaved">已保存</em>
                <em v-else-if="previewDatasetRows.length">未保存</em>
              </header>

              <div v-if="isPreviewingDataset" class="dataset-page__preview-state">正在预览字段...</div>

              <p v-else-if="previewErrorMessage" class="dataset-page__dialog-error" role="alert">
                {{ previewErrorMessage }}
              </p>

              <el-table
                v-else
                :data="previewDatasetRows"
                stripe
                class="dataset-page__preview-table"
                empty-text="暂无字段预览"
              >
                <el-table-column label="映射字段" min-width="180" align="center" header-align="center">
                  <template #default="{ row }">
                    <el-input
                      :model-value="row.sourceName"
                      placeholder="请输入字段名"
                      @update:model-value="updatePreviewSourceName(row.id, String($event))"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="字段示例" min-width="240" align="center" header-align="center" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="dataset-page__preview-sample">{{ row.sampleValue }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="是否启用" width="170" align="center" header-align="center">
                  <template #default="{ row }">
                    <button
                      class="dataset-page__preview-switch"
                      :class="{ 'dataset-page__preview-switch--on': row.enabled }"
                      type="button"
                      role="switch"
                      :aria-checked="row.enabled"
                      @click="updatePreviewEnabled(row.id, !row.enabled)"
                    >
                      <span class="dataset-page__preview-switch-text">{{ row.enabled ? 'on' : 'off' }}</span>
                      <span class="dataset-page__preview-switch-knob" aria-hidden="true"></span>
                    </button>
                  </template>
                </el-table-column>
              </el-table>

              <footer v-if="!isPreviewingDataset && previewDatasetRows.length" class="dataset-page__preview-actions">
                <button type="button" @click="handleCancelDatasetPreview">取消</button>
                <button class="dataset-page__dialog-primary" type="button" @click="handleSaveDatasetPreview">
                  保存
                </button>
              </footer>
            </section>
          </Transition>

          <p v-if="uploadErrorMessage" class="dataset-page__dialog-error" role="alert">
            {{ uploadErrorMessage }}
          </p>

          <footer class="dataset-page__dialog-actions">
            <button type="button" :disabled="isUploadingDataset" @click="handleCloseUploadDialog">取消</button>
            <button class="dataset-page__dialog-primary" type="submit" :disabled="isUploadingDataset">
              {{ isUploadingDataset ? '上传中' : '上传并解析数据集' }}
            </button>
          </footer>
        </form>
      </div>
    </Transition>

    <Transition name="dataset-page-dialog">
      <div
        v-if="isDetailDialogOpen"
        class="dataset-page__dialog-backdrop"
        role="presentation"
        @click.self="handleCloseDetailDialog"
      >
        <section
          class="dataset-page__dialog dataset-page__detail-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="dataset-detail-dialog-title"
        >
          <header class="dataset-page__dialog-header">
            <span>查看详情</span>
            <div class="dataset-page__detail-title-row">
              <strong id="dataset-detail-dialog-title">{{ datasetDetailTitle }}</strong>
              <a
                v-if="datasetDownloadUrl"
                class="dataset-page__download-button"
                :href="datasetDownloadUrl"
                :download="datasetDownloadFileName"
                target="_blank"
                rel="noopener noreferrer"
              >
                下载数据集
              </a>
              <button
                class="dataset-page__dialog-close"
                type="button"
                aria-label="关闭数据集详情弹窗"
                @click="handleCloseDetailDialog"
              >
                ×
              </button>
            </div>
          </header>

          <div v-if="isLoadingDatasetDetail" class="dataset-page__detail-state">
            <strong>正在获取数据集详情</strong>
            <span>请稍候。</span>
          </div>

          <div v-else-if="datasetDetailErrorMessage" class="dataset-page__detail-state dataset-page__detail-state--error">
            <strong>{{ datasetDetailErrorMessage }}</strong>
            <button type="button" @click="selectedDataset && handleViewDataset(selectedDataset)">重新获取</button>
          </div>

          <div v-else class="dataset-page__detail-content">
            <section class="dataset-page__detail-section">
              <strong>基础信息</strong>
              <el-table :data="datasetDetailRows" stripe class="dataset-page__detail-table">
                <el-table-column prop="label" label="字段" width="140" />
                <el-table-column prop="value" label="内容">
                  <template #default="{ row }">
                    <span class="dataset-page__detail-value">{{ row.value }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section v-if="datasetSchemaFieldRows.length > 0" class="dataset-page__detail-section">
              <strong>Schema 字段</strong>
              <el-table :data="datasetSchemaFieldRows" stripe class="dataset-page__detail-table">
                <el-table-column label="enabled（是否启用）" width="150" align="center" header-align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'primary' : 'info'" effect="light">
                      {{ row.enabledText }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="targetName" label="targetName" min-width="180" align="center" header-align="center" show-overflow-tooltip />
                <el-table-column prop="sourceName" label="sourceName" min-width="180" align="center" header-align="center" show-overflow-tooltip />
              </el-table>
            </section>

            <section v-if="datasetFileRows.length > 0" class="dataset-page__detail-section">
              <strong>文件明细</strong>
              <el-table :data="datasetFileRows" stripe class="dataset-page__detail-table">
                <el-table-column prop="originalName" label="文件名" min-width="160" show-overflow-tooltip />
                <el-table-column prop="format" label="格式" width="90" />
                <el-table-column prop="contentType" label="类型" min-width="130" show-overflow-tooltip />
                <el-table-column prop="fileSize" label="大小" width="100" />
                <el-table-column prop="createTime" label="创建时间" min-width="170" show-overflow-tooltip />
                <el-table-column prop="storagePath" label="存储路径" min-width="220" show-overflow-tooltip />
              </el-table>
            </section>
          </div>

          <footer class="dataset-page__dialog-actions">
            <button type="button" @click="handleCloseDetailDialog">关闭</button>
          </footer>
        </section>
      </div>
    </Transition>

    <div class="dataset-page__list" aria-label="数据集数据列表">
      <Transition name="dataset-page-list-content" mode="out-in">
        <div :key="listAnimationKey" class="dataset-page__list-content">
          <div v-if="shouldShowInlineLoading" class="dataset-page__state">
            <strong>正在加载数据集</strong>
            <span>请稍候。</span>
          </div>

          <div v-else-if="datasetErrorMessage" class="dataset-page__state dataset-page__state--error">
            <strong>{{ datasetErrorMessage }}</strong>
            <button type="button" @click="loadDatasets(currentPage)">重新获取</button>
          </div>

          <div v-else-if="datasets.length === 0" class="dataset-page__state">
            <strong>{{ emptyDatasetText }}</strong>
            <span>可以点击右上角上传数据集。</span>
          </div>

          <template v-else>
            <article
              v-for="(dataset, index) in datasets"
              :key="dataset.id"
              class="dataset-page__row"
            >

              <div class="dataset-page__card-body">
                <div class="dataset-page-icon-position">
                  <img class="dataset-page-icon" src="../../icon/right-top.png" alt="">
                  <img
  v-if="dataset.tags?.[0] === 'Excel'"
  class="dataset-page-png"
  src="../../icon/excel.png"
  alt=""
>

<img
  v-else-if="dataset.tags?.[0] === 'JSON'"
  class="dataset-page-png"
  src="../../icon/json.png"
  alt=""
>

<img
  v-else-if="dataset.tags?.[0] === 'JSONL'"
  class="dataset-page-png"
  src="../../icon/json.png"
  alt=""
>
                  <span class="dataset-page-icon-index">
                    {{ (displayPage - 1) * datasetPageSize + index + 1 }}
                  </span>
                </div>

                <strong class="dataset-page__name">{{ dataset.name }}</strong>
                <p class="dataset-page__description">{{ dataset.description }}</p>

                <div class="dataset-page__meta">
                  <div class="dataset-page__tags" aria-label="分类标签">
                    <span v-for="tag in dataset.tags" :key="tag">{{ tag }}</span>
                    <span v-if="dataset.tags.length === 0">未分类</span>
                  </div>
                  <span class="dataset-page__time">创建时间：{{ dataset.createdAt }}</span>
                </div>
              </div>

              <div class="dataset-page__actions">
                <button class="dataset-page__use" type="button" @click="handleViewDataset(dataset)">
                  查看数据集
                </button>
                <button
                  class="dataset-page__delete"
                  type="button"
                  :disabled="deletingDatasetId === dataset.id"
                  @click="handleDeleteDataset(dataset)"
                >
                  {{ deletingDatasetId === dataset.id ? '删除中' : '删除数据集' }}
                </button>
              </div>
            </article>
          </template>
        </div>
      </Transition>

      <div v-if="!datasetErrorMessage && totalDatasets > 0" class="dataset-page__pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="currentPage"
          :disabled="isLoadingDatasets"
          :page-size="datasetPageSize"
          :total="totalDatasets"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.dataset-page {
  display: grid;
  gap: 22px;
}

.dataset-page__header {
  display: grid;
  gap: 10px;
  max-width: 760px;
}

.dataset-page__eyebrow {
  margin: 0;
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 750;
}

.dataset-page__header h1 {
  margin: 0;
  color: var(--lh-text-primary);
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.12;
}

.dataset-page__header p:last-child {
  margin: 0;
  color: var(--lh-text-secondary);
  line-height: 1.7;
}

.dataset-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--lh-border-soft);
  padding-bottom: 12px;
}

.dataset-page__filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.78);
  padding: 14px;
}

.dataset-page__filter-search {
  flex: 1 1 280px;
  min-width: 220px;
}

.dataset-page__filter-select {
  width: 150px;
}

.dataset-page__filter-submit,
.dataset-page__filter-reset {
  min-height: 36px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
}

.dataset-page__filter-submit {
  border: 1px solid var(--lh-accent);
  background: var(--lh-accent);
  color: #ffffff;
}

.dataset-page__filter-reset {
  border: 1px solid var(--lh-border-strong);
  background: #ffffff;
  color: var(--lh-text-secondary);
}

.dataset-page__list-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.dataset-page__tabs {
  display: inline-flex;
  gap: 4px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  padding: 3px;
}

.dataset-page__tabs button {
  min-height: 32px;
  border: 0;
  border-radius: 6px;
  outline: none;
  background: transparent;
  color: var(--lh-text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 12px;
  transition:
    background 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease;
}

.dataset-page__tabs button:hover {
  background: rgba(23, 32, 51, 0.06);
  color: var(--lh-text-primary);
}

.dataset-page__tabs button:focus-visible {
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.14);
}

.dataset-page__tabs button.dataset-page__tab--active {
  background: #172033;
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(23, 32, 51, 0.12);
}

.dataset-page__list-summary span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 750;
}

.dataset-page__create {
  min-height: 38px;
  border: 0;
  border-radius: 999px;
  background: #172033;
  color: #ffffff;
  cursor: pointer;
  font-weight: 750;
  padding: 0 18px;
  box-shadow: 0 12px 28px rgba(23, 32, 51, 0.16);
}

.dataset-page__loading-backdrop {
  position: fixed;
  z-index: 34;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(20, 32, 54, 0.2);
  padding: 24px;
  backdrop-filter: blur(2px);
}

.dataset-page__loading-dialog {
  display: grid;
  width: min(100%, 260px);
  justify-items: center;
  gap: 12px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
  padding: 26px 28px 24px;
}

.dataset-page__loading-image {
  display: block;
  width: 86px;
  height: 86px;
  object-fit: contain;
}

.dataset-page__loading-text {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  justify-content: center;
  gap: 1px;
  margin: 0;
  color: var(--lh-text-primary);
  font-size: 16px;
  font-weight: 850;
  letter-spacing: 0;
  white-space: nowrap;
}

.dataset-page__loading-text span {
  display: inline-block;
  animation: dataset-loading-text-bounce 0.72s cubic-bezier(0.2, 0.82, 0.2, 1) infinite;
}

.dataset-page-loading-enter-active,
.dataset-page-loading-leave-active {
  transition:
    opacity 0.28s ease,
    backdrop-filter 0.28s ease;
}

.dataset-page-loading-enter-active .dataset-page__loading-dialog {
  animation: dataset-loading-dialog-in 0.32s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.dataset-page-loading-leave-active .dataset-page__loading-dialog {
  animation: dataset-loading-dialog-out 0.24s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.dataset-page-loading-enter-from,
.dataset-page-loading-leave-to {
  opacity: 0;
  backdrop-filter: blur(0);
}

.dataset-page__dialog-backdrop {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(20, 32, 54, 0.22);
  padding: 24px;
  backdrop-filter: blur(3px);
}

.dataset-page__dialog {
  display: grid;
  width: min(100%, 480px);
  gap: 18px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
  padding: 24px;
}

.dataset-page__detail-dialog {
  width: min(100%, 920px);
  max-height: calc(100vh - 48px);
  overflow: hidden;
}

.dataset-page__upload-dialog {
  width: min(100%, 860px);
  max-height: calc(100vh - 48px);
  overflow: auto;
}

.dataset-page__dialog-header {
  position: relative;
  display: grid;
  gap: 6px;
  padding-right: 44px;
}

.dataset-page__dialog-header span {
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 800;
}

.dataset-page__dialog-header strong {
  color: var(--lh-text-primary);
  font-size: 22px;
  line-height: 1.2;
}

.dataset-page__dialog-close {
  position: absolute;
  top: -4px;
  right: 0;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-secondary);
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background 0.2s ease;
}

.dataset-page__dialog-close:hover {
  border-color: rgba(0, 113, 227, 0.28);
  background: #ffffff;
  color: var(--lh-accent);
}

.dataset-page__detail-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dataset-page__download-button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--lh-accent);
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
  padding: 0 16px;
  text-decoration: none;
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.2);
  transition:
    background 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
}

.dataset-page__download-button:hover {
  background: #005bbf;
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(0, 113, 227, 0.24);
}

.dataset-page__dialog-field,
.dataset-page__dialog-file {
  display: grid;
  gap: 8px;
}

.dataset-page__dialog-field span,
.dataset-page__dialog-file span {
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 750;
}

.dataset-page__dialog-field input,
.dataset-page__dialog-field textarea {
  width: 100%;
  border: 1px solid rgba(23, 32, 51, 0.1);
  border-radius: 14px;
  outline: none;
  background: rgba(255, 255, 255, 0.82);
  color: var(--lh-text-primary);
  padding: 0 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.dataset-page__dialog-field input {
  min-height: 46px;
}

.dataset-page__dialog-field textarea {
  min-height: 110px;
  padding-top: 12px;
  resize: none;
}

.dataset-page__dialog-field input:focus,
.dataset-page__dialog-field textarea:focus {
  border-color: rgba(0, 113, 227, 0.42);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.12);
}

.dataset-page__dialog-file {
  position: relative;
  overflow: hidden;
  border: 1px dashed rgba(23, 32, 51, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  cursor: pointer;
  padding: 14px;
}

.dataset-page__dialog-file-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: stretch;
  gap: 12px;
}

.dataset-page__dialog-file strong {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 13px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-page__dialog-file input {
  position: absolute;
  inset: 0;
  cursor: pointer;
  opacity: 0;
}

.dataset-page__preview-toggle {
  min-width: 132px;
  border: 1px solid rgba(0, 113, 227, 0.22);
  border-radius: 14px;
  background: rgba(245, 249, 255, 0.92);
  color: var(--lh-accent);
  cursor: pointer;
  font-size: 13px;
  font-weight: 850;
  padding: 0 16px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.dataset-page__preview-toggle:hover:not(:disabled) {
  border-color: rgba(0, 113, 227, 0.38);
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.1);
  transform: translateY(-1px);
}

.dataset-page__preview-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.dataset-page__dialog-error {
  margin: 0;
  border: 1px solid rgba(255, 59, 48, 0.22);
  border-radius: 12px;
  background: rgba(255, 59, 48, 0.1);
  color: #9f1f18;
  font-size: 13px;
  font-weight: 650;
  padding: 10px 12px;
}

.dataset-page__preview-panel {
  display: grid;
  gap: 14px;
  border: 1px solid rgba(0, 113, 227, 0.12);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.82);
  padding: 14px;
}

.dataset-page__preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dataset-page__preview-header div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dataset-page__preview-header span {
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 750;
}

.dataset-page__preview-header strong {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  border-radius: 999px;
  background: var(--lh-accent-soft);
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 850;
  padding: 0 10px;
}

.dataset-page__preview-header em {
  font-size: 12px;
  font-style: normal;
  font-weight: 850;
  color: #16a34a;
}

.dataset-page__preview-state {
  display: grid;
  min-height: 120px;
  place-items: center;
  color: var(--lh-text-secondary);
  font-size: 14px;
  font-weight: 750;
}

.dataset-page__preview-table {
  overflow: hidden;
  border: 1px solid var(--lh-border-soft);
  border-radius: 12px;
}

.dataset-page__preview-table :deep(.el-table__cell) {
  color: var(--lh-text-secondary);
  font-size: 13px;
}

.dataset-page__preview-table :deep(th.el-table__cell) {
  color: var(--lh-text-primary);
  font-weight: 850;
}

.dataset-page__preview-table :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.dataset-page__preview-table :deep(.el-input__inner) {
  text-align: center;
}

.dataset-page__preview-sample {
  display: block;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-page__preview-switch {
  position: relative;
    display: inline-flex;
    width: 70px;
    height: 31px;
    align-items: center;
    justify-content: flex-end;
    border: 3px solid rgba(71, 79, 72, 0.26);
    border-radius: 999px;
    background: #8f9990;
    color: #ffffff;
    cursor: pointer;
    overflow: hidden;
    padding: 0 10px;
    text-transform: lowercase;
    box-shadow: inset 0 2px 5px rgba(0, 0, 0, 0.18), 0 6px 14px rgba(15, 23, 42, 0.12);
    transition: background 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.dataset-page__preview-switch--on {
  justify-content: flex-start;
  border-color: rgba(0, 110, 255, 0.34);
  background: #2a85fc;
  box-shadow:
    inset 0 2px 5px rgba(255, 255, 255, 0.16),
    0 8px 18px rgba(49, 210, 121, 0.26);
}

.dataset-page__preview-switch-text {
  position: relative;
  z-index: 1;
  font-size: 16px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
  text-shadow: 0 1px 2px rgba(15, 23, 42, 0.2);
}

.dataset-page__preview-switch-knob {
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

.dataset-page__preview-switch--on .dataset-page__preview-switch-knob {
  left: calc(100% - 25px);
}

.dataset-page__preview-switch:hover .dataset-page__preview-switch-knob {
  transform: scale(1.04);
}

.dataset-page__preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.dataset-page__preview-actions button {
  min-height: 36px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 800;
  padding: 0 14px;
}

.dataset-page__preview-actions .dataset-page__dialog-primary {
  border-color: transparent;
  background: var(--lh-accent);
  color: #ffffff;
}

.dataset-preview-enter-active,
.dataset-preview-leave-active {
  transition:
    opacity 0.24s ease,
    transform 0.24s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.dataset-preview-enter-from,
.dataset-preview-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.dataset-page__detail-content {
  display: grid;
  max-height: min(62vh, 620px);
  gap: 18px;
  overflow: auto;
  padding-right: 4px;
}

.dataset-page__detail-section {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.dataset-page__detail-section > strong {
  color: var(--lh-text-primary);
  font-size: 15px;
  font-weight: 850;
}

.dataset-page__detail-table {
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--lh-border-soft);
  border-radius: 8px;
}

.dataset-page__detail-table :deep(.el-table__cell) {
  color: var(--lh-text-secondary);
  font-size: 13px;
}

.dataset-page__detail-table :deep(th.el-table__cell) {
  color: var(--lh-text-primary);
  font-weight: 800;
}

.dataset-page__detail-value {
  display: block;
  max-height: 180px;
  overflow: auto;
  color: var(--lh-text-primary);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.dataset-page__detail-state {
  display: grid;
  min-height: 180px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-secondary);
  text-align: center;
}

.dataset-page__detail-state strong {
  color: var(--lh-text-primary);
  font-size: 16px;
}

.dataset-page__detail-state span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.dataset-page__detail-state button {
  min-height: 36px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 750;
  padding: 0 14px;
}

.dataset-page__detail-state--error {
  border-color: rgba(255, 59, 48, 0.22);
  background: rgba(255, 59, 48, 0.08);
}

.dataset-page__dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.dataset-page__dialog-actions button {
  min-height: 40px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 750;
  padding: 0 16px;
}

.dataset-page__dialog-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.dataset-page__dialog-actions .dataset-page__dialog-primary {
  border-color: transparent;
  background: var(--lh-accent);
  color: #ffffff;
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.2);
}

.dataset-page-dialog-enter-active,
.dataset-page-dialog-leave-active {
  transition:
    opacity 0.34s ease,
    backdrop-filter 0.34s ease;
}

.dataset-page-dialog-enter-active .dataset-page__dialog {
  animation: dataset-dialog-in 0.38s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.dataset-page-dialog-leave-active .dataset-page__dialog {
  animation: dataset-dialog-out 0.28s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.dataset-page-dialog-enter-from,
.dataset-page-dialog-leave-to {
  opacity: 0;
  backdrop-filter: blur(0);
}

@keyframes dataset-dialog-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.96);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes dataset-dialog-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }

  to {
    opacity: 0;
    transform: translateY(12px) scale(0.97);
  }
}

@keyframes dataset-loading-dialog-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.96);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes dataset-loading-dialog-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }

  to {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
}

@keyframes dataset-loading-text-bounce {
  0%,
  72%,
  100% {
    transform: translateY(0);
  }

  34% {
    transform: translateY(-7px);
  }
}

.dataset-page__list {
  display: grid;
  min-height: calc(100vh - 330px);
  grid-template-rows: minmax(356px, 1fr) auto;
  gap: 12px;
}

.dataset-page__list-content {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-content: start;
  gap: 12px;
}

.dataset-page-list-content-enter-active {
  transition:
    opacity 0.56s ease,
    filter 0.6s ease,
    transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.dataset-page-list-content-leave-active {
  transition:
    opacity 0.22s ease,
    filter 0.24s ease,
    transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.dataset-page-list-content-enter-from {
  opacity: 0;
  filter: blur(14px);
  transform: translateY(18px) scale(0.992);
}

.dataset-page-list-content-leave-to {
  opacity: 0;
  filter: blur(8px);
  transform: translateY(-6px) scale(0.996);
}

.dataset-page__state {
  display: grid;
  grid-column: 1 / -1;
  min-height: 180px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-secondary);
  box-shadow: var(--lh-shadow-card);
  text-align: center;
}

.dataset-page__state strong {
  color: var(--lh-text-primary);
  font-size: 16px;
}

.dataset-page__state span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.dataset-page__state button {
  min-height: 36px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 750;
  padding: 0 14px;
}

.dataset-page__state--error {
  border-color: rgba(255, 59, 48, 0.22);
  background: rgba(255, 59, 48, 0.08);
}

.dataset-page__row {
  display: grid;
  min-height: 164px;
  grid-template-rows: minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid var(--lh-border-soft);
  /* border-radius: 8px; */
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--lh-shadow-card);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.dataset-page__row:hover {
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 12px 28px rgba(20, 32, 54, 0.08);
  transform: translateY(-1px);
}

.dataset-page__card-body {
  position: relative;
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  padding: 18px 20px 16px;
}

.dataset-page-icon-position {
  position: absolute;
  top: 0;
  right: 0;
  width: 54px;
  height: 36px;
  pointer-events: none;
}

.dataset-page-icon {
  display: block;
  width: 100%;
  height: 100%;
  /* object-fit: contain; */
}
.dataset-page-png{
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.dataset-page-icon-index {
  position: absolute;
  top: 15%;
  right: 38%;
  color: white;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  text-align: center;
}

.dataset-page__name {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 20px;
  font-weight: 850;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 46px;
}

.dataset-page__description {
  display: -webkit-box;
  min-height: 20px;
  margin: 0;
  overflow: hidden;
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.dataset-page__meta {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.dataset-page__time {
  overflow: hidden;
  color: var(--lh-text-secondary);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 24px;
}

.dataset-page__tags span {
  max-width: 100%;
  overflow: hidden;
  border-radius: 999px;
  background: var(--lh-bg-secondary);
  color: var(--lh-text-secondary);
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dataset-page__actions {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 0;
  border-top: 1px solid var(--lh-border-soft);
  background: rgba(248, 250, 252, 0.76);
}

.dataset-page__use,
.dataset-page__delete {
  display: grid;
  min-height: 42px;
  flex: 1 1 0;
  place-items: center;
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
  padding: 0 12px;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.dataset-page__use {
  color: var(--lh-accent);
}

.dataset-page__delete {
  border-left: 1px solid var(--lh-border-soft);
  color: var(--lh-danger);
}

.dataset-page__use:hover {
  background: rgba(0, 113, 227, 0.08);
}

.dataset-page__delete:hover:not(:disabled) {
  background: rgba(255, 59, 48, 0.08);
}

.dataset-page__delete:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.dataset-page__pagination {
  display: flex;
  justify-content: center;
  align-self: end;
  padding: 8px 0 0;
}

.dataset-page__pagination :deep(.el-pagination.is-background .btn-next),
.dataset-page__pagination :deep(.el-pagination.is-background .btn-prev),
.dataset-page__pagination :deep(.el-pagination.is-background .el-pager li) {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8px 18px rgba(20, 32, 54, 0.05);
  color: var(--lh-text-secondary);
  font-weight: 750;
}

.dataset-page__pagination :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--lh-accent);
  color: #ffffff;
}

@media (max-width: 1180px) {
  .dataset-page__list-content {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dataset-page__list {
    min-height: auto;
    grid-template-rows: auto auto;
  }

  .dataset-page__list-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .dataset-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .dataset-page__list-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .dataset-page__tabs {
    width: 100%;
  }

  .dataset-page__tabs button {
    flex: 1 1 0;
  }

  .dataset-page__create {
    width: 100%;
  }

  .dataset-page__filter-search,
  .dataset-page__filter-select {
    width: 100%;
    min-width: 0;
    flex-basis: 100%;
  }

  .dataset-page__filter-submit,
  .dataset-page__filter-reset {
    flex: 1 1 0;
  }

  .dataset-page__dialog-actions {
    flex-direction: column-reverse;
  }

  .dataset-page__dialog-actions button {
    width: 100%;
  }
}
</style>
