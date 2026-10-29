<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CreateTask,
  GetDataset,
  GetLabelers,
  GetReviewers,
  GetTaskDetail,
  GetTemplateDetail,
  GetTemplates,
  UpdateTask,
} from '../../utils/api'

interface DetailTableRow {
  label: string
  value: string
}

interface JsonDetailBlock {
  label: string
  value: string
}

interface TaskOption {
  id: number | string
  name: string
  description?: string
}

interface TaskCreateStep {
  key: string
  title: string
  description: string
}

interface AiReviewDimension {
  field: string
  weight: number
  passScore: number
}

const route = useRoute()
const router = useRouter()

const taskDetail = shallowRef<Record<string, unknown> | null>(null)
const isLoadingTaskDetail = shallowRef(false)
const taskDetailErrorMessage = shallowRef('')
const currentCreateStep = shallowRef(0)
const datasetOptions = shallowRef<TaskOption[]>([])
const templateOptions = shallowRef<TaskOption[]>([])
const reviewerOptions = shallowRef<TaskOption[]>([])
const labelerOptions = shallowRef<TaskOption[]>([])
const isLoadingCreateOptions = shallowRef(false)
const isLoadingLabelers = shallowRef(false)
const isLoadingTemplateFields = shallowRef(false)
const isSubmittingCreateTask = shallowRef(false)
const isHydratingTaskForm = shallowRef(false)
const createOptionsErrorMessage = shallowRef('')
const labelerOptionsErrorMessage = shallowRef('')
const templateFieldsErrorMessage = shallowRef('')

const taskCreateForm = reactive({
  title: '',
  description: '',
  datasetId: '',
  templateId: '',
  quota: 0,
  rewardRule: '',
  deadline: '',
  tags: [] as string[],
  reviewerIds: [] as Array<number | string>,
  distributionConfig: {
    strategyType: 'FIRST_COME_FIRST_SERVED',
    maxClaimPerUser: 50,
    claimCount: 5,
    labelerIds: [] as Array<number | string>,
  },
  aiReviewEnabled: false,
  aiReviewConfig: {
    reviewPrompt: '请根据标注结果与原始物料内容进行一致性审核，并按每个维度给出0到100分。',
    passThreshold: 85,
    reviewThreshold: 70,
    dimensions: [] as AiReviewDimension[],
  },
})

const createSteps: TaskCreateStep[] = [
  {
    key: 'base',
    title: '基础信息',
    description: '填写任务名称与任务描述',
  },
  {
    key: 'resource',
    title: '资源配置',
    description: '选择数据集、模板、配额与发布时间',
  },
  {
    key: 'distribution',
    title: '分类与分发',
    description: '设置标签、审核员与分发策略',
  },
  {
    key: 'aiReview',
    title: '质量设置（可选）',
    description: '人工审核为主，AI 仅作为辅助提示',
  },
]

const distributionStrategyOptions = [
  { value: 'FIRST_COME_FIRST_SERVED', label: '先到先得' },
  { value: 'ASSIGNMENT', label: '指派' },
  // { value: 'RANDOM', label: '随机分配' },
  // { value: 'QUOTA_GRAB', label: '配额抢单' },
]

const LLM_INTERACTION_COMPONENT_TYPE = 'llmInteraction'

const taskId = computed(() => {
  return getRouteText(route.params.taskId) || getRouteText(route.query.taskId) || getRouteText(route.query.id)
})

const hasTaskId = computed(() => Boolean(taskId.value))

const isCreateMode = computed(() => !hasTaskId.value)

const isEditMode = computed(() => hasTaskId.value && getRouteText(route.query.mode) === 'edit')

const isCreateFormMode = computed(() => isCreateMode.value || isEditMode.value)

const pageTitle = computed(() => {
  if (isEditMode.value) {
    return '修改任务'
  }

  return hasTaskId.value ? '任务详情' : '创建任务'
})

const pageSubtitle = computed(() => {
  if (isEditMode.value) {
    return '在原有任务数据基础上调整四个步骤配置，提交后更新当前任务。'
  }

  return hasTaskId.value ? '当前页面先渲染接口返回的任务详情数据。' : '分步填写任务信息，最后生成创建任务所需的请求结构。'
})

const detailRows = computed<DetailTableRow[]>(() => {
  if (!taskDetail.value) {
    return []
  }

  return Object.entries(taskDetail.value)
    .filter(([, value]) => !isObjectLike(value))
    .map(([key, value]) => ({
      label: formatDetailKey(key),
      value: formatDetailValue(value),
    }))
})

const jsonDetailBlocks = computed<JsonDetailBlock[]>(() => {
  if (!taskDetail.value) {
    return []
  }

  return Object.entries(taskDetail.value)
    .filter(([, value]) => isObjectLike(value))
    .map(([key, value]) => ({
      label: formatDetailKey(key),
      value: formatDetailValue(value),
    }))
})

const createPayloadPreview = computed(() => ({
  title: taskCreateForm.title,
  description: taskCreateForm.description,
  datasetId: normalizeNumberValue(taskCreateForm.datasetId),
  templateId: normalizeNumberValue(taskCreateForm.templateId),
  quota: taskCreateForm.quota,
  rewardRule: taskCreateForm.rewardRule,
  deadline: formatDeadlineValue(taskCreateForm.deadline),
  tags: taskCreateForm.tags,
  reviewerIds: taskCreateForm.reviewerIds.map(normalizeNumberValue),
  distributionConfig: buildDistributionConfigPayload(),
  aiReviewEnabled: taskCreateForm.aiReviewEnabled,
  ...(taskCreateForm.aiReviewEnabled
    ? {
        aiReviewConfig: {
          reviewPrompt: taskCreateForm.aiReviewConfig.reviewPrompt,
          passThreshold: taskCreateForm.aiReviewConfig.passThreshold,
          reviewThreshold: taskCreateForm.aiReviewConfig.reviewThreshold,
          dimensions: taskCreateForm.aiReviewConfig.dimensions.map((dimension) => ({
            field: dimension.field,
            weight: dimension.weight,
            passScore: dimension.passScore,
          })),
        },
      }
    : {}),
}))

const createPayloadPreviewText = computed(() => JSON.stringify(createPayloadPreview.value, null, 2))

void createPayloadPreviewText

const selectedDatasetQuotaMax = shallowRef(0)

const aiReviewWeightTotal = computed(() =>
  taskCreateForm.aiReviewConfig.dimensions.reduce((total, dimension) => total + readNumber(dimension.weight, 0), 0),
)

const aiReviewWeightTotalText = computed(() => aiReviewWeightTotal.value.toFixed(2))

const quotaLimitText = computed(() => {
  if (!taskCreateForm.datasetId) {
    return '请先选择数据集后再设置固定配额'
  }

  return `当前数据集最大可用配额：${selectedDatasetQuotaMax.value}`
})

const selectedReviewerChain = computed(() =>
  taskCreateForm.reviewerIds.map((reviewerId, index) => {
    const reviewer = reviewerOptions.value.find((option) => String(option.id) === String(reviewerId))

    return reviewer ?? {
      id: reviewerId,
      name: `审核员 ${index + 1}`,
    }
  }),
)

const reviewerChainStyle = computed(() => ({
  '--reviewer-count': String(Math.max(selectedReviewerChain.value.length, 1)),
}))

const selectedDistributionStrategyIndex = computed(() => {
  const index = distributionStrategyOptions.findIndex(
    (option) => option.value === taskCreateForm.distributionConfig.strategyType,
  )

  return index >= 0 ? index : 0
})

const distributionStrategyStyle = computed(() => ({
  '--strategy-index': String(selectedDistributionStrategyIndex.value),
}))

const isAssignmentDistribution = computed(
  () => normalizeDistributionStrategy(taskCreateForm.distributionConfig.strategyType) === 'ASSIGNMENT',
)

let taskDetailRequestSeed = 0
let createOptionsRequestSeed = 0
let labelerOptionsRequestSeed = 0
let datasetQuotaRequestSeed = 0
let templateFieldsRequestSeed = 0

function getRouteText(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}

function handleSelectDistributionStrategy(strategy: string) {
  taskCreateForm.distributionConfig.strategyType = normalizeDistributionStrategy(strategy)
}

function handleLabelerSelectVisibleChange(isVisible: boolean) {
  if (isVisible) {
    void loadLabelerOptions()
  }
}

function getResponsePayload(response: any) {
  const payload = response?.data ?? response

  if (payload?.data !== undefined) {
    return payload.data
  }

  return payload
}

function pickResponseArray(response: any) {
  const payload = getResponsePayload(response)

  const candidates = [
    payload,
    payload?.records,
    payload?.list,
    payload?.items,
    payload?.content,
    payload?.data,
    payload?.data?.records,
    payload?.data?.list,
    payload?.data?.items,
    payload?.data?.content,
  ]

  return candidates.find(Array.isArray) ?? []
}

function isObjectLike(value: unknown) {
  return typeof value === 'object' && value !== null
}

function normalizeOptionRecord(record: unknown, index: number, fallbackName: string): TaskOption {
  const option = (record ?? {}) as Record<string, unknown>
  const id = option.id ?? option.userId ?? option.reviewerId ?? option.datasetId ?? option.templateId ?? index + 1
  const name =
    option.name ??
    option.nickname ??
    option.username ??
    option.realName ??
    option.displayName ??
    option.datasetName ??
    option.templateName ??
    option.title ??
    `${fallbackName} ${index + 1}`

  return {
    id: id as number | string,
    name: String(name),
    description: typeof option.description === 'string' ? option.description : undefined,
  }
}

function normalizeDistributionStrategy(value: unknown) {
  const normalizedValue = typeof value === 'string' ? value.trim().toUpperCase() : ''

  if (normalizedValue === 'ASSIGNED') {
    return 'ASSIGNMENT'
  }

  return distributionStrategyOptions.some((option) => option.value === normalizedValue)
    ? normalizedValue
    : 'FIRST_COME_FIRST_SERVED'
}

function normalizeNumberValue(value: string | number) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : value
}

function buildDistributionConfigPayload() {
  const strategyType = normalizeDistributionStrategy(taskCreateForm.distributionConfig.strategyType)

  if (strategyType === 'ASSIGNMENT') {
    return {
      strategyType,
      labelerIds: taskCreateForm.distributionConfig.labelerIds.map(normalizeNumberValue),
    }
  }

  return {
    strategyType,
    maxClaimPerUser: taskCreateForm.distributionConfig.maxClaimPerUser,
    claimCount: taskCreateForm.distributionConfig.claimCount,
  }
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

function formatDeadlineValue(value: string) {
  if (!value) {
    return ''
  }

  return value.includes('T') ? value : value.replace(' ', 'T')
}

function getTodayStart() {
  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)

  return todayStart
}

function isPastDeadlineDateDisabled(time: Date) {
  return time.getTime() < getTodayStart().getTime()
}

function isDeadlineSelectable() {
  if (!taskCreateForm.deadline) {
    return false
  }

  const deadlineDate = new Date(taskCreateForm.deadline)

  if (Number.isNaN(deadlineDate.getTime())) {
    return false
  }

  return deadlineDate.getTime() >= getTodayStart().getTime()
}

function readText(value: unknown, fallback = '') {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }

  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function isScoreInRange(value: unknown) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) && numberValue >= 0 && numberValue <= 100
}

function readBoolean(value: unknown, fallback = false) {
  if (typeof value === 'boolean') {
    return value
  }

  if (typeof value === 'number') {
    return value === 1
  }

  if (typeof value === 'string') {
    const normalizedValue = value.trim().toLowerCase()

    if (['true', '1', 'yes', 'enabled'].includes(normalizedValue)) {
      return true
    }

    if (['false', '0', 'no', 'disabled'].includes(normalizedValue)) {
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

function readStringList(value: unknown) {
  if (Array.isArray(value)) {
    return value
      .map((item) => readText(item))
      .filter((item) => Boolean(item))
  }

  if (typeof value === 'string') {
    try {
      const parsedValue = JSON.parse(value)

      if (Array.isArray(parsedValue)) {
        return readStringList(parsedValue)
      }
    } catch {
      // Fall back to comma-separated tags.
    }

    return value
      .split(',')
      .map((item) => item.trim())
      .filter((item) => Boolean(item))
  }

  return []
}

function readIdList(value: unknown) {
  if (typeof value === 'string') {
    try {
      return readIdList(JSON.parse(value))
    } catch {
      return value
        .split(',')
        .map((item) => item.trim())
        .filter((item) => Boolean(item))
    }
  }

  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => {
      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>
        return record.id ?? record.userId ?? record.reviewerId ?? record.labelerId
      }

      return item
    })
    .filter((item): item is string | number => typeof item === 'string' || typeof item === 'number')
}

function normalizeDeadlineInput(value: unknown) {
  const deadlineText = readText(value)

  return deadlineText ? formatDeadlineValue(deadlineText) : ''
}

function readAiReviewDimensions(value: unknown) {
  if (typeof value === 'string') {
    try {
      return readAiReviewDimensions(JSON.parse(value))
    } catch {
      return []
    }
  }

  if (!Array.isArray(value)) {
    return []
  }

  return value
    .map((item) => {
      const dimension = parseMaybeJsonRecord(item)
      const field = readText(dimension.field)

      if (!field) {
        return null
      }

      return {
        field,
        weight: readNumber(dimension.weight, 0),
        passScore: readNumber(dimension.passScore, 0),
      }
    })
    .filter((item): item is AiReviewDimension => Boolean(item))
}

function collectTemplateFieldNames(source: unknown, fieldNames = new Set<string>(), depth = 0) {
  if (depth > 5 || source === null || source === undefined) {
    return fieldNames
  }

  if (typeof source === 'string') {
    try {
      collectTemplateFieldNames(JSON.parse(source), fieldNames, depth + 1)
    } catch {
      // Ignore plain strings; template detail may include non-JSON text.
    }

    return fieldNames
  }

  if (Array.isArray(source)) {
    source.forEach((item) => collectTemplateFieldNames(item, fieldNames, depth + 1))
    return fieldNames
  }

  if (typeof source !== 'object') {
    return fieldNames
  }

  const record = source as Record<string, unknown>
  const componentType = readText(record.type).trim()

  if (componentType.toLowerCase() === LLM_INTERACTION_COMPONENT_TYPE.toLowerCase()) {
    return fieldNames
  }

  const field = readText(record.field ?? record.fieldName)

  if (field) {
    fieldNames.add(field)
  }

  ;['components', 'fields', 'schema', 'items', 'children', 'config'].forEach((key) => {
    if (record[key] !== undefined) {
      collectTemplateFieldNames(record[key], fieldNames, depth + 1)
    }
  })

  return fieldNames
}

function buildDefaultAiReviewDimensions(fields: string[]) {
  if (!fields.length) {
    return []
  }

  const baseWeight = Math.floor((100 / fields.length) * 100) / 100
  let usedWeight = 0

  return fields.map((field, index) => {
    const isLast = index === fields.length - 1
    const weight = isLast ? Number((100 - usedWeight).toFixed(2)) : baseWeight

    usedWeight += weight

    return {
      field,
      weight,
      passScore: 80,
    }
  })
}

function isAiReviewConfigCompleted() {
  if (!taskCreateForm.aiReviewEnabled) {
    return true
  }

  const config = taskCreateForm.aiReviewConfig
  const hasValidScores = isScoreInRange(config.passThreshold) && isScoreInRange(config.reviewThreshold)
  const hasValidDimensions =
    config.dimensions.length > 0 &&
    config.dimensions.every(
      (dimension) =>
        Boolean(dimension.field) &&
        isScoreInRange(dimension.weight) &&
        isScoreInRange(dimension.passScore),
    )
  const hasExactWeightTotal = Math.abs(aiReviewWeightTotal.value - 100) < 0.001

  return Boolean(config.reviewPrompt && hasValidScores && hasValidDimensions && hasExactWeightTotal)
}

function formatDetailKey(key: string) {
  const keyMap: Record<string, string> = {
    id: '任务ID',
    taskId: '任务ID',
    title: '任务标题',
    name: '任务名称',
    description: '任务描述',
    datasetId: '数据集ID',
    datasetName: '数据集名称',
    templateId: '模板ID',
    templateName: '模板名称',
    status: '任务状态',
    quota: '任务配额',
    rewardRule: '奖励规则',
    deadline: '截止时间',
    creatorId: '创建人ID',
    createTime: '创建时间',
    createdAt: '创建时间',
    updateTime: '更新时间',
    updatedAt: '更新时间',
    tags: '任务标签',
    progress: '任务进度',
    reviewerIds: '审核员ID',
    distributionConfig: '分发配置',
  }

  return keyMap[key] ?? key
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

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.message ?? fallback
}

function getResponseErrorMessage(response: any, fallback = '创建任务失败') {
  const code = Number(response?.code)

  if (!Number.isFinite(code) || code === 200) {
    return ''
  }

  return response?.message ?? response?.msg ?? fallback
}

function isQuotaWithinDatasetLimit() {
  if (!taskCreateForm.datasetId) {
    return false
  }

  const quotaValue = readNumber(taskCreateForm.quota, -1)

  return quotaValue >= 0 && quotaValue <= selectedDatasetQuotaMax.value
}

function clampTaskQuota() {
  if (isHydratingTaskForm.value) {
    return
  }

  const normalizedQuota = readNumber(taskCreateForm.quota, 0)

  if (normalizedQuota < 0) {
    taskCreateForm.quota = 0
    return
  }

  if (taskCreateForm.datasetId && normalizedQuota > selectedDatasetQuotaMax.value) {
    taskCreateForm.quota = selectedDatasetQuotaMax.value
  }
}

async function loadSelectedDatasetQuota(datasetId: string | number) {
  if (!datasetId) {
    selectedDatasetQuotaMax.value = 0
    clampTaskQuota()
    return
  }

  const requestSeed = (datasetQuotaRequestSeed += 1)

  try {
    const response = await GetDataset({ pageNum: 1, pageSize: 100 })

    if (requestSeed !== datasetQuotaRequestSeed) {
      return
    }

    const matchedDataset = pickResponseArray(response).find((item) => {
      const datasetRecord = (item ?? {}) as Record<string, unknown>
      const currentId = datasetRecord.id ?? datasetRecord.datasetId

      return String(currentId ?? '') === String(datasetId)
    }) as Record<string, unknown> | undefined

    selectedDatasetQuotaMax.value = readNumber(matchedDataset?.successCount, 0)
    clampTaskQuota()
  } catch (error: any) {
    if (requestSeed !== datasetQuotaRequestSeed) {
      return
    }

    selectedDatasetQuotaMax.value = 0
    clampTaskQuota()
    ElMessage.error(readErrorMessage(error, '数据集配额上限加载失败'))
  }
}

async function loadSelectedTemplateReviewFields(templateId: string | number) {
  taskCreateForm.aiReviewConfig.dimensions = []
  templateFieldsErrorMessage.value = ''

  if (!templateId) {
    templateFieldsRequestSeed += 1
    isLoadingTemplateFields.value = false
    return
  }

  const requestSeed = (templateFieldsRequestSeed += 1)
  isLoadingTemplateFields.value = true

  try {
    const response = await GetTemplateDetail(templateId)
    const detail = getResponsePayload(response)
    const fields = Array.from(collectTemplateFieldNames(detail))

    if (requestSeed !== templateFieldsRequestSeed) {
      return
    }

    taskCreateForm.aiReviewConfig.dimensions = buildDefaultAiReviewDimensions(fields)
  } catch (error: any) {
    if (requestSeed !== templateFieldsRequestSeed) {
      return
    }

    templateFieldsErrorMessage.value = readErrorMessage(error, '模板字段加载失败')
    ElMessage.error(templateFieldsErrorMessage.value)
  } finally {
    if (requestSeed === templateFieldsRequestSeed) {
      isLoadingTemplateFields.value = false
    }
  }
}

function applyTaskDetailToCreateForm(detail: unknown) {
  const task = parseMaybeJsonRecord(detail)
  const datasetRecord = parseMaybeJsonRecord(task.dataset)
  const templateRecord = parseMaybeJsonRecord(task.template)
  const distributionConfig = parseMaybeJsonRecord(task.distributionConfig)
  const distributionConfigDetail = parseMaybeJsonRecord(distributionConfig.config)
  const aiReviewConfig = parseMaybeJsonRecord(task.aiReviewConfig)
  const aiReviewDimensions = readAiReviewDimensions(aiReviewConfig.dimensions ?? task.dimensions)
  const nextTemplateId = task.templateId ?? templateRecord.id ?? ''

  isHydratingTaskForm.value = true
  taskCreateForm.title = readText(task.title ?? task.name ?? task.taskName)
  taskCreateForm.description = readText(task.description ?? task.desc)
  taskCreateForm.datasetId = readText(task.datasetId ?? datasetRecord.id)
  taskCreateForm.templateId = readText(nextTemplateId)
  taskCreateForm.quota = readNumber(task.quota ?? task.totalCount ?? task.itemCount ?? task.sampleCount, 0)
  taskCreateForm.rewardRule = readText(task.rewardRule)
  taskCreateForm.deadline = normalizeDeadlineInput(task.deadline ?? task.publishTime ?? task.releaseTime)
  taskCreateForm.tags = readStringList(task.tags)
  taskCreateForm.reviewerIds = readIdList(task.reviewerIds ?? task.reviewers ?? task.reviewerList)
  taskCreateForm.distributionConfig.strategyType = normalizeDistributionStrategy(
    distributionConfig.strategyType ?? distributionConfig.type ?? task.strategyType ?? task.distributionStrategy,
  )
  taskCreateForm.distributionConfig.labelerIds = readIdList(
    distributionConfig.labelerIds ??
      distributionConfigDetail.labelerIds ??
      task.labelerIds ??
      task.labelers ??
      task.labelerList,
  )
  taskCreateForm.distributionConfig.maxClaimPerUser = readNumber(
    distributionConfig.maxClaimPerUser ?? distributionConfigDetail.maxClaimPerUser ?? task.maxClaimPerUser,
    50,
  )
  taskCreateForm.distributionConfig.claimCount = readNumber(
    distributionConfig.claimCount ?? distributionConfigDetail.claimCount ?? task.claimCount,
    5,
  )
  taskCreateForm.aiReviewEnabled = readBoolean(task.aiReviewEnabled, Object.keys(aiReviewConfig).length > 0)
  taskCreateForm.aiReviewConfig.reviewPrompt = readText(
    aiReviewConfig.reviewPrompt ?? task.reviewPrompt,
    '请根据标注结果与原始物料内容进行一致性审核，并按每个维度给出0到100分。',
  )
  taskCreateForm.aiReviewConfig.passThreshold = readNumber(aiReviewConfig.passThreshold ?? task.passThreshold, 85)
  taskCreateForm.aiReviewConfig.reviewThreshold = readNumber(aiReviewConfig.reviewThreshold ?? task.reviewThreshold, 70)
  taskCreateForm.aiReviewConfig.dimensions = aiReviewDimensions
  isHydratingTaskForm.value = false

  if (taskCreateForm.templateId && !aiReviewDimensions.length) {
    void loadSelectedTemplateReviewFields(taskCreateForm.templateId)
  }

  if (taskCreateForm.distributionConfig.strategyType === 'ASSIGNMENT') {
    void loadLabelerOptions()
  }
}

async function loadCreateOptions() {
  if (!isCreateFormMode.value) {
    return
  }

  const requestSeed = (createOptionsRequestSeed += 1)
  isLoadingCreateOptions.value = true
  createOptionsErrorMessage.value = ''

  try {
    const [datasetResponse, templateResponse, reviewerResponse] = await Promise.all([
      GetDataset({ pageNum: 1, pageSize: 100 }),
      GetTemplates({ pageNum: 1, pageSize: 100 }),
      GetReviewers(),
    ])

    if (requestSeed !== createOptionsRequestSeed) {
      return
    }

    datasetOptions.value = pickResponseArray(datasetResponse).map((item, index) =>
      normalizeOptionRecord(item, index, '数据集'),
    )
    templateOptions.value = pickResponseArray(templateResponse).map((item, index) =>
      normalizeOptionRecord(item, index, '模板'),
    )
    reviewerOptions.value = pickResponseArray(reviewerResponse).map((item, index) =>
      normalizeOptionRecord(item, index, '审核员'),
    )
  } catch (error: any) {
    if (requestSeed !== createOptionsRequestSeed) {
      return
    }

    createOptionsErrorMessage.value = readErrorMessage(error, '数据集、模板或审核员选项加载失败')
  } finally {
    if (requestSeed === createOptionsRequestSeed) {
      isLoadingCreateOptions.value = false
    }
  }
}

async function loadLabelerOptions(force = false) {
  if (!isCreateFormMode.value || (!force && labelerOptions.value.length)) {
    return
  }

  const requestSeed = (labelerOptionsRequestSeed += 1)
  isLoadingLabelers.value = true
  labelerOptionsErrorMessage.value = ''

  try {
    const response = await GetLabelers({ pageNum: 1, pageSize: 100 })

    if (requestSeed !== labelerOptionsRequestSeed) {
      return
    }

    labelerOptions.value = pickResponseArray(response).map((item, index) =>
      normalizeOptionRecord(item, index, '标注员'),
    )
  } catch (error: any) {
    if (requestSeed !== labelerOptionsRequestSeed) {
      return
    }

    labelerOptionsErrorMessage.value = readErrorMessage(error, '标注员列表加载失败')
    ElMessage.error(labelerOptionsErrorMessage.value)
  } finally {
    if (requestSeed === labelerOptionsRequestSeed) {
      isLoadingLabelers.value = false
    }
  }
}

async function loadTaskDetail() {
  const currentTaskId = taskId.value

  if (!currentTaskId) {
    taskDetail.value = null
    taskDetailErrorMessage.value = ''
    isLoadingTaskDetail.value = false
    return
  }

  const requestSeed = (taskDetailRequestSeed += 1)
  isLoadingTaskDetail.value = true
  taskDetailErrorMessage.value = ''

  try {
    const response = await GetTaskDetail(currentTaskId)
    const detail = getResponsePayload(response)

    if (requestSeed !== taskDetailRequestSeed) {
      return
    }

    taskDetail.value = detail && typeof detail === 'object' ? detail : { value: detail }

    if (isEditMode.value) {
      applyTaskDetailToCreateForm(taskDetail.value)
    }
  } catch (error: any) {
    if (requestSeed !== taskDetailRequestSeed) {
      return
    }

    taskDetail.value = null
    taskDetailErrorMessage.value = readErrorMessage(error, '任务详情加载失败')
    ElMessage.error(taskDetailErrorMessage.value)
  } finally {
    if (requestSeed === taskDetailRequestSeed) {
      isLoadingTaskDetail.value = false
    }
  }
}

function handleBack() {
  router.push({ name: 'owner-tasks' })
}

function isCreateStepCompleted(index: number) {
  if (index === 0) {
    return Boolean(taskCreateForm.title.trim() && taskCreateForm.description.trim())
  }

  if (index === 1) {
    return Boolean(
      taskCreateForm.datasetId &&
        taskCreateForm.templateId &&
        isQuotaWithinDatasetLimit() &&
        isDeadlineSelectable(),
    )
  }

  if (index === 2) {
    return Boolean(
      taskCreateForm.tags.length &&
        taskCreateForm.reviewerIds.length &&
        taskCreateForm.distributionConfig.strategyType &&
        (isAssignmentDistribution.value
          ? taskCreateForm.distributionConfig.labelerIds.length
          : taskCreateForm.distributionConfig.maxClaimPerUser > 0 &&
            taskCreateForm.distributionConfig.claimCount > 0),
    )
  }

  return isAiReviewConfigCompleted()
}

function isCreateStepActive(index: number) {
  return currentCreateStep.value === index
}

function isCreateStepHighlighted(index: number) {
  return index <= currentCreateStep.value || isCreateStepDisplayCompleted(index)
}

function isCreateStepDisplayCompleted(index: number) {
  if (index === 3) {
    return false
  }

  return isCreateStepCompleted(index)
}

function canGoCreateStep(index: number) {
  if (index <= currentCreateStep.value) {
    return true
  }

  for (let stepIndex = 0; stepIndex < index; stepIndex += 1) {
    if (!isCreateStepCompleted(stepIndex)) {
      return false
    }
  }

  return true
}

function getCreateStepMarker(index: number) {
  if (index === 3) {
    return index + 1
  }

  return isCreateStepDisplayCompleted(index) ? '✓' : index + 1
}

function goCreateStep(index: number) {
  if (!canGoCreateStep(index)) {
    const firstIncompleteStepIndex = createSteps.findIndex(
      (_, stepIndex) => stepIndex < index && !isCreateStepCompleted(stepIndex),
    )

    currentCreateStep.value = Math.max(0, firstIncompleteStepIndex)
    ElMessage.warning('请按顺序完成前面的步骤')
    return
  }

  currentCreateStep.value = index
}

function handlePrevCreateStep() {
  currentCreateStep.value = Math.max(0, currentCreateStep.value - 1)
}

function handleNextCreateStep() {
  if (!isCreateStepCompleted(currentCreateStep.value)) {
    ElMessage.warning('请先完成当前步骤的必填内容')
    return
  }

  currentCreateStep.value = Math.min(createSteps.length - 1, currentCreateStep.value + 1)
}

async function handleCreateTaskSubmit() {
  if (!isCreateStepCompleted(0)) {
    currentCreateStep.value = 0
    ElMessage.warning('请先填写任务名称与任务描述')
    return
  }

  if (!isCreateStepCompleted(1)) {
    currentCreateStep.value = 1
    ElMessage.warning('请先完成数据集、模板、配额与时间配置')
    return
  }

  if (!isCreateStepCompleted(2)) {
    currentCreateStep.value = 2
    ElMessage.warning('请先完成分类与分发配置')
    return
  }

  if (!isCreateStepCompleted(3)) {
    currentCreateStep.value = 3
    ElMessage.warning('请先完成质量辅助配置')
    return
  }

  isSubmittingCreateTask.value = true

  try {
    const response = isEditMode.value && taskId.value
      ? await UpdateTask(taskId.value, createPayloadPreview.value)
      : await CreateTask(createPayloadPreview.value)
    const responseErrorMessage = getResponseErrorMessage(response, isEditMode.value ? '更新任务失败' : '创建任务失败')

    if (responseErrorMessage) {
      ElMessage.error(responseErrorMessage)
      return
    }

    ElMessage.success(isEditMode.value ? '更新任务成功' : '创建任务成功')
    router.push({ name: 'owner-tasks' })
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, isEditMode.value ? '更新任务失败' : '创建任务失败'))
  } finally {
    isSubmittingCreateTask.value = false
  }
}

onMounted(() => {
  loadTaskDetail()
  loadCreateOptions()
})

watch([taskId, isEditMode], () => {
  loadTaskDetail()
  loadCreateOptions()
})

watch(
  () => taskCreateForm.datasetId,
  (datasetId) => {
    void loadSelectedDatasetQuota(datasetId)
  },
  { immediate: true },
)

watch(
  () => taskCreateForm.quota,
  () => {
    clampTaskQuota()
  },
)

watch(
  () => taskCreateForm.templateId,
  (templateId) => {
    if (isHydratingTaskForm.value) {
      return
    }

    void loadSelectedTemplateReviewFields(templateId)
  },
  { immediate: true, flush: 'sync' },
)

watch(
  () => taskCreateForm.distributionConfig.strategyType,
  (strategyType) => {
    const normalizedStrategy = normalizeDistributionStrategy(strategyType)

    if (normalizedStrategy !== strategyType) {
      taskCreateForm.distributionConfig.strategyType = normalizedStrategy
      return
    }

    if (normalizedStrategy === 'ASSIGNMENT') {
      void loadLabelerOptions()
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="owner-task-detail-page">
    <header class="owner-task-detail-page__header">
      <div>
        <el-button text class="owner-task-detail-page__back" @click="handleBack">返回任务管理</el-button>
        <h1 class="owner-task-detail-page__title">{{ pageTitle }}</h1>
        <p class="owner-task-detail-page__description">{{ pageSubtitle }}</p>
      </div>
    </header>

    <main class="owner-task-detail-page__content">
      <div v-if="isLoadingTaskDetail" class="owner-task-detail-page__empty">正在加载任务详情...</div>

      <p v-else-if="taskDetailErrorMessage" class="owner-task-detail-page__error">
        {{ taskDetailErrorMessage }}
      </p>

      <section v-else-if="hasTaskId && !isEditMode && taskDetail" class="owner-task-detail-page__detail">
        <el-table class="owner-task-detail-page__table" :data="detailRows" border stripe>
          <el-table-column prop="label" label="字段" width="220" />
          <el-table-column prop="value" label="内容" show-overflow-tooltip />
        </el-table>

        <section v-if="jsonDetailBlocks.length" class="owner-task-detail-page__json-grid">
          <article v-for="block in jsonDetailBlocks" :key="block.label" class="owner-task-detail-page__json-block">
            <h2>{{ block.label }}</h2>
            <pre>{{ block.value }}</pre>
          </article>
        </section>
      </section>

      <section v-else class="owner-task-detail-page__create">
        <aside class="owner-task-detail-page__stepper" aria-label="创建任务步骤">
          <button
            v-for="(step, index) in createSteps"
            :key="step.key"
            class="owner-task-detail-page__step"
            :class="{
              'owner-task-detail-page__step--active': isCreateStepActive(index),
              'owner-task-detail-page__step--highlighted': isCreateStepHighlighted(index),
              'owner-task-detail-page__step--completed': isCreateStepDisplayCompleted(index),
              'owner-task-detail-page__step--locked': !canGoCreateStep(index),
            }"
            :disabled="!canGoCreateStep(index)"
            type="button"
            @click="goCreateStep(index)"
          >
            <span class="owner-task-detail-page__step-marker">
              {{ getCreateStepMarker(index) }}
            </span>
            <span class="owner-task-detail-page__step-content">
              <strong>{{ step.title }}</strong>
              <small>{{ step.description }}</small>
            </span>
          </button>
        </aside>

        <section class="owner-task-detail-page__form-shell">
          <p v-if="createOptionsErrorMessage" class="owner-task-detail-page__error">
            {{ createOptionsErrorMessage }}
          </p>

          <Transition name="owner-task-create-step" mode="out-in">
            <section
              v-if="currentCreateStep === 0"
              key="base"
              class="owner-task-detail-page__form-step"
            >
              <header class="owner-task-detail-page__form-header">
                <span>步骤 1</span>
                <h2>填写任务名称与任务描述</h2>
              </header>

              <el-form label-position="top">
                <el-form-item label="任务名称">
                  <el-input
                    v-model="taskCreateForm.title"
                    maxlength="80"
                    placeholder="请输入任务名称"
                    show-word-limit
                  />
                </el-form-item>

                <el-form-item label="任务描述">
                  <el-input
                    v-model="taskCreateForm.description"
                    maxlength="300"
                    placeholder="请输入任务描述"
                    :rows="7"
                    show-word-limit
                    type="textarea"
                  />
                </el-form-item>
              </el-form>
            </section>

            <section
              v-else-if="currentCreateStep === 1"
              key="resource"
              class="owner-task-detail-page__form-step"
            >
              <header class="owner-task-detail-page__form-header">
                <span>步骤 2</span>
                <h2>选择数据集、模板、配额与时间</h2>
              </header>

              <el-form label-position="top">
                <div class="owner-task-detail-page__form-grid">
                  <el-form-item label="选择数据集">
                    <el-select
                      v-model="taskCreateForm.datasetId"
                      :loading="isLoadingCreateOptions"
                      filterable
                      placeholder="请选择数据集"
                    >
                      <el-option
                        v-for="dataset in datasetOptions"
                        :key="dataset.id"
                        :label="dataset.name"
                        :value="dataset.id"
                      />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="选择模板">
                    <el-select
                      v-model="taskCreateForm.templateId"
                      :loading="isLoadingCreateOptions"
                      filterable
                      placeholder="请选择模板"
                    >
                      <el-option
                        v-for="template in templateOptions"
                        :key="template.id"
                        :label="template.name"
                        :value="template.id"
                      />
                    </el-select>
                  </el-form-item>

                  <el-form-item :label="taskCreateForm.datasetId ? `固定配额（0 - ${selectedDatasetQuotaMax}）` : '固定配额'">
                    <el-input-number
                      v-model="taskCreateForm.quota"
                      :disabled="!taskCreateForm.datasetId"
                      :min="0"
                      :max="selectedDatasetQuotaMax"
                      :step="1"
                      controls-position="right"
                    />
                    <p class="owner-task-detail-page__field-tip">
                      {{ quotaLimitText }}
                    </p>
                  </el-form-item>

                  <el-form-item label="截止时间">
                    <el-date-picker
                      v-model="taskCreateForm.deadline"
                      :disabled-date="isPastDeadlineDateDisabled"
                      format="YYYY-MM-DD HH:mm:ss"
                      placeholder="请选择截止时间"
                      type="datetime"
                      value-format="YYYY-MM-DDTHH:mm:ss"
                    />
                  </el-form-item>
                </div>

                <el-form-item label="奖励规则">
                  <el-input
                    v-model="taskCreateForm.rewardRule"
                    placeholder="例如：每条0.1元"
                  />
                </el-form-item>
              </el-form>
            </section>

            <section
              v-else-if="currentCreateStep === 2"
              key="distribution"
              class="owner-task-detail-page__form-step"
            >
              <header class="owner-task-detail-page__form-header">
                <span>步骤 3</span>
                <h2>分类、审核员与分发策略</h2>
              </header>

              <el-form label-position="top">
                <el-form-item label="任务分类标签">
                  <el-input-tag
                    v-model="taskCreateForm.tags"
                    placeholder="输入标签后回车"
                  />
                </el-form-item>

                <el-form-item label="选择审核员">
                  <el-select
                    v-model="taskCreateForm.reviewerIds"
                    filterable
                    :loading="isLoadingCreateOptions"
                    multiple
                    placeholder="请按审核顺序选择审核员"
                  >
                    <el-option
                      v-for="reviewer in reviewerOptions"
                      :key="reviewer.id"
                      :label="reviewer.name"
                      :value="reviewer.id"
                    />
                  </el-select>
                  <p class="owner-task-detail-page__field-tip">
                    审核员将按照选择顺序依次进入审核链路。
                  </p>
                </el-form-item>

                <section
                  class="owner-task-detail-page__reviewer-chain-panel"
                  aria-label="审核员链路"
                >
                  <header class="owner-task-detail-page__reviewer-chain-header">
                    <strong>审核员链路</strong>
                    <span>共 {{ selectedReviewerChain.length }} 个节点</span>
                  </header>

                  <div class="owner-task-detail-page__reviewer-chain-scroll">
                    <Transition name="owner-task-reviewer-chain-state" mode="out-in">
                      <div
                        v-if="selectedReviewerChain.length"
                        key="reviewer-chain"
                        class="owner-task-detail-page__reviewer-chain"
                        :style="reviewerChainStyle"
                      >
                        <span
                          v-if="selectedReviewerChain.length > 1"
                          class="owner-task-detail-page__reviewer-chain-line"
                          aria-hidden="true"
                        ></span>
                        <TransitionGroup name="owner-task-reviewer-node">
                          <article
                            v-for="(reviewer, index) in selectedReviewerChain"
                            :key="reviewer.id"
                            class="owner-task-detail-page__reviewer-node"
                          >
                            <small>{{ index === 0 ? '初审' : '复审' }}</small>
                            <span>{{ index + 1 }}</span>
                            <strong>{{ reviewer.name }}</strong>
                          </article>
                        </TransitionGroup>
                      </div>
                      <p
                        v-else
                        key="reviewer-chain-empty"
                        class="owner-task-detail-page__reviewer-chain-empty"
                      >
                        请选择审核员
                      </p>
                    </Transition>
                  </div>
                </section>

                <el-form-item label="分发策略">
                  <div class="owner-task-detail-page__strategy-scroll">
                    <div
                      class="owner-task-detail-page__strategy-switch"
                      :style="distributionStrategyStyle"
                      role="radiogroup"
                      aria-label="分发策略"
                    >
                      <span class="owner-task-detail-page__strategy-slider" aria-hidden="true"></span>
                      <button
                        v-for="strategy in distributionStrategyOptions"
                        :key="strategy.value"
                        class="owner-task-detail-page__strategy-button"
                        :class="{
                          'owner-task-detail-page__strategy-button--active':
                            taskCreateForm.distributionConfig.strategyType === strategy.value,
                        }"
                        type="button"
                        role="radio"
                        :aria-checked="taskCreateForm.distributionConfig.strategyType === strategy.value"
                        @click="handleSelectDistributionStrategy(strategy.value)"
                      >
                        {{ strategy.label }}
                      </button>
                    </div>
                  </div>
                </el-form-item>

                <Transition name="owner-task-assignment-fields" mode="out-in">
                  <el-form-item
                    v-if="isAssignmentDistribution"
                    key="assignment-labelers"
                    label="指定标注员"
                  >
                    <el-select
                      v-model="taskCreateForm.distributionConfig.labelerIds"
                      :loading="isLoadingLabelers"
                      filterable
                      multiple
                      placeholder="请选择标注员"
                      @visible-change="handleLabelerSelectVisibleChange"
                    >
                      <el-option
                        v-for="labeler in labelerOptions"
                        :key="labeler.id"
                        :label="labeler.name"
                        :value="labeler.id"
                      />
                    </el-select>
                  
                    <p v-if="labelerOptionsErrorMessage" class="owner-task-detail-page__field-error">
                      {{ labelerOptionsErrorMessage }}
                    </p>
                  </el-form-item>

                  <div
                    v-else
                    key="claim-quota"
                    class="owner-task-detail-page__form-grid"
                  >
                    <el-form-item label="每人最大配额">
                      <el-input-number
                        v-model="taskCreateForm.distributionConfig.maxClaimPerUser"
                        :min="1"
                        :step="5"
                        controls-position="right"
                      />
                    </el-form-item>

                    <el-form-item label="单次领取配额个数">
                      <el-input-number
                        v-model="taskCreateForm.distributionConfig.claimCount"
                        :min="1"
                        :step="1"
                        controls-position="right"
                      />
                    </el-form-item>
                  </div>
                </Transition>

                <!-- <section class="owner-task-detail-page__payload-preview">
                  <h3>请求 JSON 预览</h3>
                  <pre>{{ createPayloadPreviewText }}</pre>
                </section> -->
              </el-form>
            </section>

            <section
              v-else
              key="aiReview"
              class="owner-task-detail-page__form-step"
            >
              <header class="owner-task-detail-page__form-header">
                <span>步骤 4</span>
                <h2>质量设置（可选）</h2>
              </header>

              <p class="owner-task-detail-page__quality-notice" role="note">
                人工标注与人工审核是最终业务依据；AI 只提供可追溯的辅助提示，不会替代审核结论。
              </p>

              <el-form label-position="top">
                <el-form-item label="是否开启 AI 辅助预审">
                  <el-radio-group v-model="taskCreateForm.aiReviewEnabled" class="owner-task-detail-page__radio-group">
                    <el-radio-button :label="false">关闭</el-radio-button>
                    <el-radio-button :label="true">开启</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <section
                  v-if="taskCreateForm.aiReviewEnabled"
                  class="owner-task-detail-page__ai-review"
                >
                  <el-form-item label="审核提示词">
                    <el-input
                      v-model="taskCreateForm.aiReviewConfig.reviewPrompt"
                      maxlength="1000"
                      placeholder="请输入 AI 预审提示词"
                      :rows="4"
                      show-word-limit
                      type="textarea"
                    />
                  </el-form-item>

                  <div class="owner-task-detail-page__form-grid">
                    <el-form-item label="通过分数">
                      <el-input-number
                        v-model="taskCreateForm.aiReviewConfig.passThreshold"
                        :max="100"
                        :min="0"
                        :precision="2"
                        :step="1"
                        controls-position="right"
                      />
                    </el-form-item>

                    <el-form-item label="需要评审的分数">
                      <el-input-number
                        v-model="taskCreateForm.aiReviewConfig.reviewThreshold"
                        :max="100"
                        :min="0"
                        :precision="2"
                        :step="1"
                        controls-position="right"
                      />
                    </el-form-item>
                  </div>

                  <section class="owner-task-detail-page__dimension-panel">
                    <header class="owner-task-detail-page__dimension-header">
                      <div>
                        <h3>字段维度配置</h3>
                        <p>字段来源于第2步选择的模板，所有 weight 之和必须等于 100。</p>
                      </div>
                      <span :class="{ 'owner-task-detail-page__weight-total--valid': isAiReviewConfigCompleted() }">
                        当前权重合计：{{ aiReviewWeightTotalText }}
                      </span>
                    </header>

                    <p v-if="templateFieldsErrorMessage" class="owner-task-detail-page__error">
                      {{ templateFieldsErrorMessage }}
                    </p>
                    <div v-else-if="isLoadingTemplateFields" class="owner-task-detail-page__empty">
                      正在加载模板字段...
                    </div>
                    <div v-else-if="!taskCreateForm.templateId" class="owner-task-detail-page__empty">
                      请先在第2步选择模板
                    </div>
                    <div v-else-if="!taskCreateForm.aiReviewConfig.dimensions.length" class="owner-task-detail-page__empty">
                      当前模板没有可配置的 field 字段
                    </div>
                    <div v-else class="owner-task-detail-page__dimension-list">
                      <div
                        v-for="dimension in taskCreateForm.aiReviewConfig.dimensions"
                        :key="dimension.field"
                        class="owner-task-detail-page__dimension-row"
                      >
                        <strong>{{ dimension.field }}</strong>
                        <label>
                          <span>weight</span>
                          <el-input-number
                            v-model="dimension.weight"
                            :max="100"
                            :min="0"
                            :precision="2"
                            :step="1"
                            controls-position="right"
                          />
                        </label>
                        <label>
                          <span>passScore</span>
                          <el-input-number
                            v-model="dimension.passScore"
                            :max="100"
                            :min="0"
                            :precision="2"
                            :step="1"
                            controls-position="right"
                          />
                        </label>
                      </div>
                    </div>
                  </section>
                </section>
              </el-form>
            </section>
          </Transition>

          <footer class="owner-task-detail-page__form-actions">
            <el-button :disabled="currentCreateStep === 0" @click="handlePrevCreateStep">
              上一步
            </el-button>
            <el-button
              v-if="currentCreateStep < createSteps.length - 1"
              type="primary"
              @click="handleNextCreateStep"
            >
              下一步
            </el-button>
            <el-button
              v-else
              type="primary"
              :loading="isSubmittingCreateTask"
              :disabled="isSubmittingCreateTask"
              @click="handleCreateTaskSubmit"
            >
              {{ isEditMode ? '更新任务' : '提交' }}
            </el-button>
          </footer>
        </section>
      </section>
    </main>
  </section>
</template>

<style scoped>
.owner-task-detail-page {
  min-height: calc(100vh - 104px);
  padding: 24px;
  color: #1f2937;
  background: #f3f6f9;
}

.owner-task-detail-page__header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.owner-task-detail-page__back {
  margin-bottom: 8px;
  padding-left: 0;
  color: #4f6680;
}

.owner-task-detail-page__title {
  margin: 0;
  font-size: 26px;
  line-height: 1.25;
}

.owner-task-detail-page__description {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.owner-task-detail-page__content {
  min-height: 420px;
  padding: 18px;
  border: 1px solid #d8dde4;
  background: #ffffff;
}

.owner-task-detail-page__table {
  width: 100%;
}

.owner-task-detail-page__json-grid {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.owner-task-detail-page__json-block {
  border: 1px solid #d8dde4;
  background: #ffffff;
}

.owner-task-detail-page__json-block h2 {
  margin: 0;
  padding: 12px 14px;
  color: #ffffff;
  background: #4f6680;
  font-size: 16px;
}

.owner-task-detail-page__json-block pre {
  max-height: 360px;
  margin: 0;
  overflow: auto;
  padding: 14px;
  color: #374151;
  background: #f9fafb;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.owner-task-detail-page__empty {
  display: flex;
  min-height: 320px;
  align-items: center;
  justify-content: center;
  color: #8b98a8;
  font-size: 15px;
  text-align: center;
}

.owner-task-detail-page__error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  background: #fff1f2;
  font-size: 14px;
}

.owner-task-detail-page__quality-notice {
  margin: 0 0 18px;
  border: 1px solid rgba(14, 116, 144, 0.18);
  border-radius: 10px;
  background: rgba(14, 116, 144, 0.07);
  color: #155e75;
  font-size: 13px;
  line-height: 1.6;
  padding: 10px 12px;
}

.owner-task-detail-page__create {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 24px;
  min-height: 560px;
}

.owner-task-detail-page__stepper {
  position: relative;
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 12px 4px 12px 0;
}

.owner-task-detail-page__stepper::before {
  position: absolute;
  top: 34px;
  bottom: 34px;
  left: 19px;
  width: 2px;
  background: #d8e5e8;
  content: '';
}

.owner-task-detail-page__step {
  position: relative;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 12px;
  min-height: 108px;
  padding: 10px 10px 10px 0;
  border: 0;
  color: #9aa6b2;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.owner-task-detail-page__step-marker {
  position: relative;
  z-index: 1;
  display: inline-flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border: 2px solid #dbe5ee;
  border-radius: 50%;
  color: #8b98a8;
  background: #ffffff;
  font-size: 14px;
  font-weight: 900;
  transition:
    border-color 0.22s ease,
    background 0.22s ease,
    color 0.22s ease,
    box-shadow 0.22s ease;
}

.owner-task-detail-page__step-content {
  display: grid;
  gap: 8px;
  padding-top: 2px;
}

.owner-task-detail-page__step-content strong {
  color: #8a96a6;
  font-size: 16px;
  line-height: 1.25;
  transition: color 0.22s ease;
}

.owner-task-detail-page__step-content small {
  color: #aeb7c2;
  font-size: 12px;
  line-height: 1.45;
}

.owner-task-detail-page__step--highlighted .owner-task-detail-page__step-marker {
  border-color: #2dd4bf;
  color: #0f766e;
  background: #ecfdf5;
  box-shadow: 0 0 0 6px rgba(45, 212, 191, 0.12);
}

.owner-task-detail-page__step--highlighted .owner-task-detail-page__step-content strong {
  color: #0f766e;
}

.owner-task-detail-page__step--active .owner-task-detail-page__step-marker {
  color: #ffffff;
  background: #14b8a6;
}

.owner-task-detail-page__step--completed .owner-task-detail-page__step-marker {
  color: #ffffff;
  background: #22c55e;
  border-color: #22c55e;
}

.owner-task-detail-page__step--locked {
  cursor: not-allowed;
  opacity: 0.58;
}

.owner-task-detail-page__step:disabled {
  cursor: not-allowed;
}

.owner-task-detail-page__form-shell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.owner-task-detail-page__form-step {
  min-height: 430px;
}

.owner-task-detail-page__form-header {
  margin-bottom: 22px;
}

.owner-task-detail-page__form-header span {
  color: #14b8a6;
  font-size: 13px;
  font-weight: 900;
}

.owner-task-detail-page__form-header h2 {
  margin: 6px 0 0;
  color: #1f2937;
  font-size: 22px;
  line-height: 1.3;
}

.owner-task-detail-page__form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 18px;
}

.owner-task-detail-page__form-shell :deep(.el-select),
.owner-task-detail-page__form-shell :deep(.el-date-editor),
.owner-task-detail-page__form-shell :deep(.el-input-number),
.owner-task-detail-page__form-shell :deep(.el-input-tag) {
  width: 100%;
}

.owner-task-detail-page__field-tip {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.4;
}

.owner-task-detail-page__field-error {
  margin: 8px 0 0;
  color: #dc2626;
  font-size: 12px;
  line-height: 1.4;
}

.owner-task-detail-page__reviewer-chain-panel {
  display: grid;
  gap: 14px;
  margin: -2px 0 18px;
  overflow: hidden;
  border: 1px solid #dbe6f2;
  border-radius: 12px;
  background: rgba(248, 251, 255, 0.92);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  padding: 16px;
}

.owner-task-detail-page__reviewer-chain-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.owner-task-detail-page__reviewer-chain-header strong {
  color: #334155;
  font-size: 14px;
  font-weight: 850;
}

.owner-task-detail-page__reviewer-chain-header span {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.owner-task-detail-page__reviewer-chain-scroll,
.owner-task-detail-page__strategy-scroll {
  overflow-x: auto;
  padding-bottom: 2px;
}

.owner-task-detail-page__reviewer-chain {
  position: relative;
  display: flex;
  min-width: max(100%, calc(var(--reviewer-count) * 136px));
  padding: 4px 0 2px;
}

.owner-task-detail-page__reviewer-chain-line {
  position: absolute;
  top: 48px;
  right: calc(50% / var(--reviewer-count));
  left: calc(50% / var(--reviewer-count));
  height: 4px;
  border-radius: 999px;
  background: #1296db;
  transition:
    right 0.36s cubic-bezier(0.16, 1, 0.3, 1),
    left 0.36s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-detail-page__reviewer-node {
  position: relative;
  z-index: 1;
  display: grid;
  min-width: 136px;
  flex: 1 0 136px;
  justify-items: center;
  gap: 8px;
  text-align: center;
}

.owner-task-detail-page__reviewer-node small {
  min-height: 16px;
  color: #1296db;
  font-size: 12px;
  font-weight: 850;
  line-height: 16px;
}

.owner-task-detail-page__reviewer-node > span {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 3px solid #1296db;
  border-radius: 50%;
  background: #ffffff;
  color: #47708c;
  box-shadow: 0 5px 14px rgba(18, 150, 219, 0.12);
  font-size: 17px;
  font-weight: 750;
  transition:
    border-color 0.28s ease,
    box-shadow 0.28s ease,
    transform 0.32s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-detail-page__reviewer-node:hover > span {
  border-color: #0071e3;
  box-shadow: 0 8px 18px rgba(0, 113, 227, 0.18);
  transform: translateY(-2px);
}

.owner-task-detail-page__reviewer-node strong {
  overflow: hidden;
  max-width: 124px;
  color: #475569;
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.owner-task-detail-page__reviewer-chain-empty {
  display: grid;
  min-height: 92px;
  place-items: center;
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 800;
}

.owner-task-detail-page__strategy-switch {
  --strategy-index: 0;

  position: relative;
  display: grid;
  min-width: 560px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  background: #eef2f6;
  box-shadow: inset 0 1px 3px rgba(15, 23, 42, 0.06);
  padding: 4px;
}

.owner-task-detail-page__strategy-slider {
  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 4px;
  width: calc((100% - 8px) / 2);
  border-radius: 10px;
  background: #ffffff;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.08),
    0 7px 18px rgba(15, 23, 42, 0.1);
  transform: translateX(calc(var(--strategy-index) * 100%));
  transition: transform 0.42s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-detail-page__strategy-button {
  position: relative;
  z-index: 1;
  min-height: 42px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0;
  transition:
    color 0.28s ease,
    transform 0.28s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-detail-page__strategy-button:hover {
  color: #334155;
}

.owner-task-detail-page__strategy-button--active {
  color: #0071e3;
  transform: scale(1.015);
}

.owner-task-assignment-fields-enter-active,
.owner-task-assignment-fields-leave-active {
  transition:
    opacity 0.24s ease,
    transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-assignment-fields-enter-from,
.owner-task-assignment-fields-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.992);
}

.owner-task-reviewer-chain-state-enter-active,
.owner-task-reviewer-chain-state-leave-active {
  transition:
    opacity 0.28s ease,
    transform 0.34s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-reviewer-chain-state-enter-from,
.owner-task-reviewer-chain-state-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.992);
}

.owner-task-reviewer-node-enter-active,
.owner-task-reviewer-node-leave-active {
  transition:
    opacity 0.28s ease,
    transform 0.34s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-reviewer-node-move {
  transition: transform 0.34s cubic-bezier(0.16, 1, 0.3, 1);
}

.owner-task-reviewer-node-enter-from,
.owner-task-reviewer-node-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.92);
}

.owner-task-detail-page__radio-group {
  width: 100%;
}

.owner-task-detail-page__radio-group :deep(.el-radio-button__inner) {
  min-width: 112px;
}

.owner-task-detail-page__ai-review {
  display: grid;
  gap: 14px;
}

.owner-task-detail-page__dimension-panel {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.owner-task-detail-page__dimension-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.owner-task-detail-page__dimension-header h3 {
  margin: 0;
  color: #1f2937;
  font-size: 15px;
}

.owner-task-detail-page__dimension-header p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.45;
}

.owner-task-detail-page__dimension-header span {
  flex: 0 0 auto;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.5;
}

.owner-task-detail-page__weight-total--valid {
  color: #047857 !important;
}

.owner-task-detail-page__dimension-list {
  display: grid;
}

.owner-task-detail-page__dimension-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(150px, 220px) minmax(150px, 220px);
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #eef2f7;
}

.owner-task-detail-page__dimension-row:last-child {
  border-bottom: 0;
}

.owner-task-detail-page__dimension-row strong {
  color: #334155;
  font-size: 14px;
  word-break: break-word;
}

.owner-task-detail-page__dimension-row label {
  display: grid;
  gap: 6px;
}

.owner-task-detail-page__dimension-row label > span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.owner-task-detail-page__payload-preview {
  margin-top: 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.owner-task-detail-page__payload-preview h3 {
  margin: 0;
  padding: 10px 12px;
  border-bottom: 1px solid #e5e7eb;
  color: #526173;
  font-size: 14px;
}

.owner-task-detail-page__payload-preview pre {
  max-height: 220px;
  margin: 0;
  overflow: auto;
  padding: 12px;
  color: #334155;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.owner-task-detail-page__form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #edf0f3;
}

.owner-task-create-step-enter-active,
.owner-task-create-step-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.owner-task-create-step-enter-from,
.owner-task-create-step-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  .owner-task-detail-page {
    padding: 16px;
  }

  .owner-task-detail-page__content {
    padding: 12px;
  }

  .owner-task-detail-page__create {
    grid-template-columns: 1fr;
  }

  .owner-task-detail-page__stepper {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .owner-task-detail-page__stepper::before {
    display: none;
  }

  .owner-task-detail-page__step {
    grid-template-columns: 1fr;
    min-height: auto;
    justify-items: center;
    padding: 8px;
    text-align: center;
  }

  .owner-task-detail-page__form-grid {
    grid-template-columns: 1fr;
  }

  .owner-task-detail-page__dimension-header,
  .owner-task-detail-page__dimension-row {
    grid-template-columns: 1fr;
  }

  .owner-task-detail-page__dimension-header {
    display: grid;
  }

  .owner-task-detail-page__form-actions {
    flex-direction: column;
  }
}
</style>
