<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import loadingAnimation from '../../icon/loading.gif'
import AiSuggestionPanel from '../../components/annotation-workbench/AiSuggestionPanel.vue'
import AnnotationMaterialForm from '../../components/annotation-workbench/AnnotationMaterialForm.vue'
import QuestionNavigation from '../../components/annotation-workbench/QuestionNavigation.vue'
import TaskProgressPanel from '../../components/annotation-workbench/TaskProgressPanel.vue'
import TaskTimelinePanel from '../../components/annotation-workbench/TaskTimelinePanel.vue'
import { useAuthStore } from '../../stores/auth'
import type {
  AnnotationFormData,
  WorkbenchItemDetail,
  WorkbenchMaterial,
  WorkbenchNavigationItem,
  WorkbenchProgress,
  WorkbenchTask,
} from '../../types/workbench'
import {
  GetWorkBenchTaskItemExcel,
  InitWorkBench,
  SaveAnnotationDraft,
  SubmitAnnotationItem,
} from '../../utils/api'

const route = useRoute()
const authStore = useAuthStore()
const { currentUser } = storeToRefs(authStore)

const task = shallowRef<WorkbenchTask | null>(null)
const materials = shallowRef<WorkbenchMaterial[]>([])
const navigationItems = shallowRef<WorkbenchNavigationItem[]>([])
const progress = shallowRef<WorkbenchProgress | null>(null)
const itemDetail = shallowRef<WorkbenchItemDetail | null>(null)
const formData = shallowRef<AnnotationFormData>({})
const uploadFiles = shallowRef<Record<string, File[]>>({})
const activeTaskItemId = shallowRef('')
const isLoadingWorkbench = shallowRef(false)
const isLoadingItem = shallowRef(false)
const isSavingDraft = shallowRef(false)
const isSubmittingItem = shallowRef(false)
const errorMessage = shallowRef('')
const saveStatusText = shallowRef('')
const loadingTextChars = '正在加载中.....'.split('')

let workspaceRequestSeed = 0
let itemRequestSeed = 0

const LLM_TRIGGER_TYPE = 'llmInteraction'
const LLM_TRIGGER_BACKEND_TYPE = 'llmTrigger'
const LLM_TRIGGER_LEGACY_TYPE = 'llm-trigger'

const taskId = computed(() => readRouteText(route.params.taskId))
const annotationMaterials = computed(() => materials.value.filter((material) => !isLlmTriggerMaterial(material)))
const totalQuestionCount = computed(() => navigationItems.value.length)
const activeNavigation = computed(() =>
  navigationItems.value.find((item) => getNavigationItemId(item) === activeTaskItemId.value) ?? null,
)
const activeQuestionIndex = computed(() => {
  const item = activeNavigation.value

  if (!item) {
    return navigationItems.value.length ? 1 : 0
  }

  return readNumber(item.index, navigationItems.value.findIndex((navItem) => navItem === item) + 1)
})
const activeDisplayStatus = computed(() =>
  String(activeNavigation.value?.displayStatus ?? itemDetail.value?.displayStatus ?? 'PENDING').toUpperCase(),
)
const isReadonlyItem = computed(() => ['SUBMITTED', 'REVIEWED'].includes(activeDisplayStatus.value))
const activeStatusLabel = computed(() => getDisplayStatusMeta(activeDisplayStatus.value).label)
const canMovePrevious = computed(() => getActiveNavigationPosition() > 0)
const canMoveNext = computed(() => {
  const position = getActiveNavigationPosition()

  return position >= 0 && position < navigationItems.value.length - 1
})
const isFormBusy = computed(() =>
  isLoadingWorkbench.value || isLoadingItem.value || isSavingDraft.value || isSubmittingItem.value,
)
const workspaceTitle = computed(() => task.value?.title || '标注工作台')
const shouldShowAiSuggestion = computed(() => hasLlmTriggerInWorkbench())

function readRouteText(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
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

    if (['true', '1', 'yes', 'y', 'enabled', 'enable', 'open', '开启', '已开启'].includes(normalizedValue)) {
      return true
    }

    if (['false', '0', 'no', 'n', 'disabled', 'disable', 'close', '关闭', '未开启'].includes(normalizedValue)) {
      return false
    }
  }

  return fallback
}

function readObject(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}

function readString(value: unknown, fallback = '') {
  if (value === undefined || value === null) {
    return fallback
  }

  const text = String(value).trim()

  return text || fallback
}

function getResponseData(response: any) {
  const payload = response?.data ?? response ?? {}

  return payload?.data ?? payload
}

function assertApiSuccess(response: any) {
  const code = response?.code

  if (code !== undefined && Number(code) !== 200) {
    throw new Error(response?.message ?? response?.msg ?? '请求失败')
  }
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

function getNavigationItemId(item: WorkbenchNavigationItem) {
  return readString(item.taskItemId ?? item.index, '')
}

function getActiveNavigationPosition() {
  return navigationItems.value.findIndex((item) => getNavigationItemId(item) === activeTaskItemId.value)
}

function getDisplayStatusMeta(status: string) {
  const statusMap: Record<string, { label: string; type: string }> = {
    PENDING: { label: '待标注', type: 'pending' },
    DRAFT: { label: '草稿', type: 'draft' },
    SUBMITTED: { label: '已提交', type: 'submitted' },
    REJECTED: { label: '已驳回', type: 'rejected' },
    REVIEWED: { label: '已通过', type: 'reviewed' },
  }

  return statusMap[status] ?? { label: status, type: 'pending' }
}

function getConfigValue(material: WorkbenchMaterial, key: string) {
  return material.config?.[key] ?? material[key]
}

function getMaterialType(material: WorkbenchMaterial) {
  const type = readString(material.type, 'input')
  const normalizedType = type.replace(/[\s_-]/g, '').toLowerCase()
  const typeMap: Record<string, string> = {
    showItem: 'text-display',
    textDisplay: 'text-display',
    tagSelect: 'select',
    richText: 'rich-text',
    imageUpload: 'image-upload',
    fileUpload: 'file-upload',
    llmTrigger: LLM_TRIGGER_TYPE,
    LLMTrigger: LLM_TRIGGER_TYPE,
    llmInteraction: LLM_TRIGGER_TYPE,
    LLMInteraction: LLM_TRIGGER_TYPE,
  }

  if (normalizedType === 'llminteraction' || normalizedType === 'llmtrigger') {
    return LLM_TRIGGER_TYPE
  }

  return typeMap[type] ?? type
}

function isLlmTriggerMaterial(material: WorkbenchMaterial) {
  const type = getMaterialType(material)

  return type === LLM_TRIGGER_TYPE || type === LLM_TRIGGER_BACKEND_TYPE || type === LLM_TRIGGER_LEGACY_TYPE
}

function isUploadMaterial(material: WorkbenchMaterial) {
  const type = getMaterialType(material)

  return type === 'image-upload' || type === 'file-upload'
}

function parseMaybeJson(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  const text = value.trim()

  if (!text || (!text.startsWith('{') && !text.startsWith('['))) {
    return value
  }

  try {
    return JSON.parse(text)
  } catch {
    return value
  }
}

function isLlmTriggerRecord(source: unknown) {
  const record = readObject(parseMaybeJson(source))

  if (!record) {
    return false
  }

  return isLlmTriggerMaterial({
    ...record,
    type: record.type ?? record.componentType ?? record.component ?? record.widgetType,
  } as WorkbenchMaterial)
}

function hasLlmTriggerInSource(source: unknown, depth = 0): boolean {
  if (depth > 6 || source === undefined || source === null) {
    return false
  }

  const parsedSource = parseMaybeJson(source)

  if (Array.isArray(parsedSource)) {
    return parsedSource.some((item) => hasLlmTriggerInSource(item, depth + 1))
  }

  const record = readObject(parsedSource)

  if (!record) {
    return false
  }

  if (isLlmTriggerRecord(record)) {
    return true
  }

  return [
    'materials',
    'components',
    'fields',
    'schema',
    'items',
    'children',
    'config',
    'template',
    'templateJson',
    'templateContent',
    'templateSchema',
    'designJson',
    'content',
  ].some((key) => hasLlmTriggerInSource(record[key], depth + 1))
}

function hasLlmTriggerInWorkbench() {
  return materials.value.some((material) => isLlmTriggerMaterial(material)) || hasLlmTriggerInSource(task.value)
}

function isWorkbenchMaterialArray(value: unknown): value is WorkbenchMaterial[] {
  return (
    Array.isArray(value) &&
    value.some((item) => {
      const record = readObject(parseMaybeJson(item))

      return Boolean(
        record &&
          (record.type !== undefined ||
            record.componentType !== undefined ||
            record.widgetType !== undefined ||
            record.field !== undefined ||
            record.label !== undefined),
      )
    })
  )
}

function pickMaterialArrayFromSource(source: unknown, depth = 0): WorkbenchMaterial[] {
  if (depth > 5 || source === undefined || source === null) {
    return []
  }

  const parsedSource = parseMaybeJson(source)

  if (isWorkbenchMaterialArray(parsedSource)) {
    return parsedSource
  }

  const record = readObject(parsedSource)

  if (!record) {
    return []
  }

  const materialKeys = [
    'materials',
    'components',
    'fields',
    'items',
    'children',
    'template',
    'templateJson',
    'templateContent',
    'templateSchema',
    'schema',
    'config',
  ]

  for (const key of materialKeys) {
    const materialsFromKey = pickMaterialArrayFromSource(record[key], depth + 1)

    if (materialsFromKey.length) {
      return materialsFromKey
    }
  }

  return []
}

function getMaterialField(material: WorkbenchMaterial) {
  return readString(material.field ?? material.id, '')
}

function getMaterialLabel(material: WorkbenchMaterial) {
  return readString(material.label, getMaterialField(material) || '未命名字段')
}

function isMaterialRequired(material: WorkbenchMaterial) {
  return readBoolean(getConfigValue(material, 'required'), false)
}

function isMaterialMultiple(material: WorkbenchMaterial) {
  return readBoolean(getConfigValue(material, 'multiple'), getMaterialType(material) === 'checkbox')
}

function normalizeWorkspace(response: any) {
  const data = getResponseData(response)
  const taskRecord = readObject(data.task) ?? {}
  const taskPayload = {
    ...data,
    ...taskRecord,
    aiReviewConfig: taskRecord.aiReviewConfig ?? data.aiReviewConfig,
    aiReviewEnabled:
      taskRecord.aiReviewEnabled ??
      data.aiReviewEnabled ??
      taskRecord.aiPreReviewEnabled ??
      data.aiPreReviewEnabled ??
      taskRecord.ai_review_enabled ??
      data.ai_review_enabled,
    template: taskRecord.template ?? data.template,
    templateJson: taskRecord.templateJson ?? data.templateJson,
    templateContent: taskRecord.templateContent ?? data.templateContent,
    templateSchema: taskRecord.templateSchema ?? data.templateSchema,
  } as WorkbenchTask
  const materialSource = [
    data.materials,
    taskRecord.materials,
    data.components,
    taskRecord.components,
    data.fields,
    taskRecord.fields,
    taskPayload.template,
    taskPayload.templateJson,
    taskPayload.templateContent,
    taskPayload.templateSchema,
    data.schema,
    taskRecord.schema,
  ]
    .map((source) => pickMaterialArrayFromSource(source))
    .find((items) => items.length)

  return {
    task: taskPayload,
    materials: materialSource ?? [],
    navigation: Array.isArray(data.navigation) ? (data.navigation as WorkbenchNavigationItem[]) : [],
    progress: (data.progress ?? {}) as WorkbenchProgress,
  }
}

function normalizeProgress(data: WorkbenchProgress): WorkbenchProgress {

  return {
    ...data,
    taskId: data.taskId ?? taskId.value,
    total: readNumber(data.total),
    pending: readNumber(data.pending),
    draft: readNumber(data.draft),
    submitted: readNumber(data.submitted),
    rejected: readNumber(data.rejected),
    reviewed: readNumber(data.reviewed),
  }
}

function normalizeItemDetail(response: any): WorkbenchItemDetail {
  return (getResponseData(response) ?? {}) as WorkbenchItemDetail
}

function createInitialFormData(annotationData: Record<string, unknown> = {}) {
  const nextFormData: AnnotationFormData = {}

  annotationMaterials.value.forEach((material) => {
    const field = getMaterialField(material)
    const type = getMaterialType(material)

    if (!field || type === 'text-display') {
      return
    }

    if (annotationData[field] !== undefined) {
      nextFormData[field] = annotationData[field]
      return
    }

    if (type === 'checkbox' || (type === 'select' && isMaterialMultiple(material))) {
      nextFormData[field] = []
      return
    }

    if (type === 'rating') {
      nextFormData[field] = 0
      return
    }

    nextFormData[field] = ''
  })

  return {
    ...nextFormData,
    ...annotationData,
  }
}

function buildAnnotationData() {
  const annotationData: Record<string, unknown> = {}

  annotationMaterials.value.forEach((material) => {
    const field = getMaterialField(material)

    if (!field || getMaterialType(material) === 'text-display' || isUploadMaterial(material)) {
      return
    }

    annotationData[field] = formData.value[field]
  })

  return annotationData
}

function buildDraftAnnotationData() {
  const annotationData: Record<string, unknown> = {}

  annotationMaterials.value.forEach((material) => {
    const field = getMaterialField(material)
    const type = getMaterialType(material)

    if (!field || type === 'text-display') {
      return
    }

    if (isUploadMaterial(material) && uploadFiles.value[field]?.length) {
      return
    }

    annotationData[field] = formData.value[field]
  })

  return annotationData
}

function buildAnnotationFileList() {
  return Object.entries(uploadFiles.value).flatMap(([field, files]) =>
    files.map((file) => ({
      field,
      file,
    })),
  )
}

function handleUpdateUploadFiles(field: string, files: File[]) {
  const nextFiles = { ...uploadFiles.value }

  if (files.length) {
    nextFiles[field] = files
  } else {
    delete nextFiles[field]
  }
  uploadFiles.value = nextFiles
}

function isEmptyValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.length === 0
  }

  return value === undefined || value === null || String(value).trim() === ''
}

function getMaterialDraftValue(material: WorkbenchMaterial) {
  const field = getMaterialField(material)

  if (!field) {
    return undefined
  }

  if (isUploadMaterial(material) && uploadFiles.value[field]?.length) {
    return uploadFiles.value[field]
  }

  return formData.value[field]
}

function getMissingRequiredLabels() {
  return annotationMaterials.value
    .filter((material) => {
      const field = getMaterialField(material)

      return (
        field &&
        getMaterialType(material) !== 'text-display' &&
        isMaterialRequired(material) &&
        isEmptyValue(getMaterialDraftValue(material))
      )
    })
    .map((material) => getMaterialLabel(material))
}

function findInitialTaskItemId(preferredTaskItemId = '') {
  const preferred = navigationItems.value.find((item) => getNavigationItemId(item) === preferredTaskItemId)

  return getNavigationItemId(preferred ?? navigationItems.value[0] ?? {})
}

async function loadItemDetail(taskItemId: string) {
  if (!taskItemId) {
    itemDetail.value = null
    formData.value = createInitialFormData()
    uploadFiles.value = {}
    return
  }

  const requestSeed = (itemRequestSeed += 1)
  isLoadingItem.value = true

  try {
    const response = await GetWorkBenchTaskItemExcel(taskItemId)
    assertApiSuccess(response)

    if (requestSeed !== itemRequestSeed) {
      return
    }

    const detail = normalizeItemDetail(response)
    itemDetail.value = detail
    formData.value = createInitialFormData(
      detail.annotationData && typeof detail.annotationData === 'object' ? detail.annotationData : {},
    )
    uploadFiles.value = {}
  } catch (error: any) {
    if (requestSeed !== itemRequestSeed) {
      return
    }

    itemDetail.value = null
    formData.value = createInitialFormData()
    uploadFiles.value = {}
    ElMessage.error(readErrorMessage(error, '单题详情加载失败'))
  } finally {
    if (requestSeed === itemRequestSeed) {
      isLoadingItem.value = false
    }
  }
}

async function loadWorkbench(preferredTaskItemId = activeTaskItemId.value) {
  if (!taskId.value) {
    errorMessage.value = '缺少任务 ID'
    return
  }

  const requestSeed = (workspaceRequestSeed += 1)
  isLoadingWorkbench.value = true
  errorMessage.value = ''

  try {
    const workspaceResponse = await InitWorkBench(taskId.value)
    assertApiSuccess(workspaceResponse)

    if (requestSeed !== workspaceRequestSeed) {
      return
    }

    const workspace = normalizeWorkspace(workspaceResponse)
    task.value = workspace.task
    materials.value = workspace.materials
    navigationItems.value = workspace.navigation
    progress.value = normalizeProgress(workspace.progress)

    const nextTaskItemId = findInitialTaskItemId(preferredTaskItemId)
    activeTaskItemId.value = nextTaskItemId
    await loadItemDetail(nextTaskItemId)
  } catch (error: any) {
    if (requestSeed !== workspaceRequestSeed) {
      return
    }

    task.value = null
    materials.value = []
    navigationItems.value = []
    progress.value = null
    itemDetail.value = null
    formData.value = {}
    uploadFiles.value = {}
    errorMessage.value = readErrorMessage(error, '标注工作台加载失败')
  } finally {
    if (requestSeed === workspaceRequestSeed) {
      isLoadingWorkbench.value = false
    }
  }
}

function handleSelectNavigation(item: WorkbenchNavigationItem) {
  const nextTaskItemId = getNavigationItemId(item)

  if (!nextTaskItemId || nextTaskItemId === activeTaskItemId.value) {
    return
  }

  activeTaskItemId.value = nextTaskItemId
  saveStatusText.value = ''
  loadItemDetail(nextTaskItemId)
}

function handleMoveQuestion(offset: number) {
  const currentPosition = getActiveNavigationPosition()
  const nextItem = navigationItems.value[currentPosition + offset]

  if (nextItem) {
    handleSelectNavigation(nextItem)
  }
}

function handleAdoptAiSuggestion(annotationData: AnnotationFormData) {
  formData.value = {
    ...formData.value,
    ...annotationData,
  }
}

function getNextTaskItemIdAfterWrite() {
  const currentPosition = getActiveNavigationPosition()
  const nextItem = navigationItems.value[currentPosition + 1]

  return getNavigationItemId(nextItem ?? navigationItems.value[currentPosition] ?? {})
}

async function refreshAfterWrite(successText: string, shouldMoveNext = false) {
  const currentTaskItemId = activeTaskItemId.value
  const targetTaskItemId = shouldMoveNext ? getNextTaskItemIdAfterWrite() : activeTaskItemId.value

  saveStatusText.value = `${successText} ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`
  await loadWorkbench(targetTaskItemId)

  if (targetTaskItemId && targetTaskItemId !== currentTaskItemId) {
    saveStatusText.value = ''
  }
}

async function handleSaveDraft() {
  if (!activeTaskItemId.value || isReadonlyItem.value) {
    return
  }

  isSavingDraft.value = true

  try {
    const response = await SaveAnnotationDraft(
      activeTaskItemId.value,
      buildDraftAnnotationData(),
      buildAnnotationFileList(),
    )

    assertApiSuccess(response)
    ElMessage.success('草稿保存成功')
    await refreshAfterWrite('草稿已保存', true)
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '草稿保存失败'))
  } finally {
    isSavingDraft.value = false
  }
}

async function handleSubmitItem() {
  if (!activeTaskItemId.value || isReadonlyItem.value) {
    return
  }

  const missingLabels = getMissingRequiredLabels()

  if (missingLabels.length) {
    ElMessage.warning(`请先填写必填项：${missingLabels.join('、')}`)
    return
  }

  isSubmittingItem.value = true

  try {
    const response = await SubmitAnnotationItem(
      activeTaskItemId.value,
      buildAnnotationData(),
      buildAnnotationFileList(),
    )
    assertApiSuccess(response)
    ElMessage.success('本题提交成功')
    await refreshAfterWrite('本题已提交', true)
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '本题提交失败'))
  } finally {
    isSubmittingItem.value = false
  }
}

watch(
  taskId,
  () => {
    activeTaskItemId.value = ''
    saveStatusText.value = ''
    loadWorkbench('')
  },
  { immediate: true },
)
</script>

<template>
  <section class="labeling-workbench">
    <header class="labeling-workbench__summary">
      <div class="labeling-workbench__summary-copy">
        <RouterLink class="labeling-workbench__back-link" to="/workbench/tasks">
          返回任务列表
        </RouterLink>
        <strong>{{ workspaceTitle }}</strong>
        <small>第 {{ activeQuestionIndex }} / {{ totalQuestionCount }} 题</small>
      </div>

      <div class="labeling-workbench__top-actions">
        <span
          class="labeling-workbench__status"
          :class="`labeling-workbench__status--${getDisplayStatusMeta(activeDisplayStatus).type}`"
        >
          {{ saveStatusText || activeStatusLabel }}
        </span>
        <strong>{{ currentUser.name }} / Labeler</strong>
      </div>
    </header>

    <main class="labeling-workbench__body">
      <aside class="labeling-workbench__rail labeling-workbench__rail--left">
        <QuestionNavigation
          :active-task-item-id="activeTaskItemId"
          :items="navigationItems"
          @select="handleSelectNavigation"
        />
      </aside>

      <section class="labeling-workbench__center">
        <p v-if="errorMessage" class="labeling-workbench__error" role="alert">{{ errorMessage }}</p>

        <div v-if="isLoadingWorkbench" class="labeling-workbench__loading">正在加载标注工作台...</div>

        <template v-else>
          <div class="labeling-workbench__form-shell" :class="{ 'labeling-workbench__form-shell--loading': isLoadingItem }">
            <AnnotationMaterialForm
              v-model="formData"
              :item-detail="itemDetail"
              :materials="annotationMaterials"
              :question-index="activeQuestionIndex"
              hide-task-eyebrow
              raw-data-collapsible
              raw-data-default-expanded
              :readonly="isReadonlyItem"
              swap-field-label
              :task="task"
              @update:files="handleUpdateUploadFiles"
            >
              <AiSuggestionPanel
                v-if="shouldShowAiSuggestion"
                :materials="annotationMaterials"
                :readonly="isReadonlyItem"
                :task-item-id="activeTaskItemId"
                @adopt="handleAdoptAiSuggestion"
              />
            </AnnotationMaterialForm>
          </div>

          <footer class="labeling-workbench__footer">
            <div class="labeling-workbench__pager">
              <el-button :disabled="!canMovePrevious || isFormBusy" @click="handleMoveQuestion(-1)">
                上一题
              </el-button>
              <el-button :disabled="!canMoveNext || isFormBusy" @click="handleMoveQuestion(1)">
                下一题
              </el-button>
            </div>

            <div class="labeling-workbench__submit-actions">
              <span v-if="isReadonlyItem">当前题目为只读状态</span>
              <el-button
                :disabled="isFormBusy || isReadonlyItem || !activeTaskItemId"
                :loading="isSavingDraft"
                @click="handleSaveDraft"
              >
                保存草稿
              </el-button>
              <el-button
                type="primary"
                :disabled="isFormBusy || isReadonlyItem || !activeTaskItemId"
                :loading="isSubmittingItem"
                @click="handleSubmitItem"
              >
                提交本题
              </el-button>
            </div>
          </footer>

          <Transition name="template-designer-loading">
            <div
              v-if="isLoadingItem"
              class="template-designer__loading-backdrop labeling-workbench__item-loading"
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
        </template>
      </section>

      <aside class="labeling-workbench__rail labeling-workbench__rail--right">
        <TaskProgressPanel :progress="progress" />
        <TaskTimelinePanel :task-item-id="activeTaskItemId" />
      </aside>
    </main>
  </section>
</template>

<style scoped>
.labeling-workbench {
  display: grid;
  height: calc(100vh - 90px);
  min-height: 620px;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  color: #111827;
  background: transparent;
  overflow: hidden;
}

.labeling-workbench__summary {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
  padding: 14px 18px;
}

.labeling-workbench__summary-copy,
.labeling-workbench__top-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.labeling-workbench__summary-copy {
  flex-wrap: wrap;
}

.labeling-workbench__back-link {
  display: inline-flex;
  min-height: 30px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 850;
  padding: 0 10px;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.labeling-workbench__back-link:hover {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
  transform: translateY(-1px);
}

.labeling-workbench__summary-copy span,
.labeling-workbench__summary-copy small {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.labeling-workbench__summary-copy strong {
  overflow: hidden;
  color: #111827;
  font-size: 16px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeling-workbench__status {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-weight: 850;
  padding: 0 11px;
  white-space: nowrap;
}

.labeling-workbench__status--draft {
  background: #fff7ed;
  color: #f97316;
}

.labeling-workbench__status--submitted,
.labeling-workbench__status--reviewed {
  background: #ecfdf5;
  color: #16a34a;
}

.labeling-workbench__status--rejected {
  background: #fef2f2;
  color: #ef4444;
}

.labeling-workbench__top-actions strong {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
  white-space: nowrap;
}

.labeling-workbench__body {
  display: grid;
  min-height: 0;
  grid-template-columns: 270px minmax(0, 1fr) 260px;
  gap: 12px;
  align-items: stretch;
  overflow: hidden;
}

.labeling-workbench__rail {
  position: static;
  display: grid;
  min-height: 0;
  height: 100%;
}

.labeling-workbench__rail :deep(.question-nav),
.labeling-workbench__rail :deep(.progress-panel) {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
}

.labeling-workbench__rail--right :deep(.progress-panel) {
  height: auto;
}

.labeling-workbench__rail--right {
  align-content: start;
  gap: 12px;
  overflow: auto;
}

.labeling-workbench__center {
  position: relative;
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 10px;
  overflow: hidden;
}

.labeling-workbench__form-shell {
  position: relative;
  display: grid;
  min-width: 0;
  overflow: auto;
}

.labeling-workbench__form-shell :deep(.annotation-form) {
  min-height: 100%;
}

.labeling-workbench__form-shell--loading {
  pointer-events: none;
}

.labeling-workbench__loading {
  display: grid;
  place-items: center;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
  color: #64748b;
  font-size: 14px;
  font-weight: 800;
}

.labeling-workbench__loading {
  min-height: 420px;
}

.labeling-workbench__item-loading {
  position: absolute;
  inset: 0;
  z-index: 20;
  border-radius: 8px;
}

.template-designer__loading-backdrop {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  border-radius: 8px;
  background: rgba(20, 32, 54, 0.18);
  backdrop-filter: blur(16px);
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
  color: #1f2937;
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

.labeling-workbench__error {
  margin: 0;
  border: 1px solid #fecaca;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 750;
  padding: 10px 12px;
}

.labeling-workbench__footer {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
  padding: 0 14px;
}

.labeling-workbench__pager,
.labeling-workbench__submit-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.labeling-workbench__submit-actions span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 750;
}

.labeling-workbench__footer :deep(.el-button) {
  border-radius: 6px;
  font-weight: 800;
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

@media (max-width: 1180px) {
  .labeling-workbench__body {
    grid-template-columns: 190px minmax(0, 1fr);
  }

  .labeling-workbench__rail--right {
    position: static;
    height: auto;
    grid-column: 1 / -1;
  }

  .labeling-workbench__rail--right :deep(.progress-panel) {
    height: auto;
    overflow: visible;
  }

  .labeling-workbench__body :deep(.progress-panel) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 820px) {
  .labeling-workbench {
    height: auto;
    min-height: 0;
    grid-template-rows: auto minmax(0, 1fr);
    overflow: visible;
  }

  .labeling-workbench__summary,
  .labeling-workbench__footer {
    align-items: stretch;
    flex-direction: column;
    padding: 12px;
  }

  .labeling-workbench__body {
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .labeling-workbench__rail {
    position: static;
    height: auto;
  }

  .labeling-workbench__summary-copy,
  .labeling-workbench__top-actions,
  .labeling-workbench__pager,
  .labeling-workbench__submit-actions {
    flex-wrap: wrap;
  }
}
</style>
