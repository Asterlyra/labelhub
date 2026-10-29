<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, reactive, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import loadingAnimation from '../../icon/loading.gif'
import AiPreReviewResultPanel from '../../components/annotation-workbench/AiPreReviewResultPanel.vue'
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
  GetReviewTaskDetail,
  ReviewApprove,
  ReviewModify,
  ReviewReject,
  ReviewTask,
} from '../../utils/api'

type ReviewActionType = '' | 'approve' | 'modify' | 'reject'

const route = useRoute()
const authStore = useAuthStore()
const { currentUser } = storeToRefs(authStore)

const task = shallowRef<WorkbenchTask | null>(null)
const materials = shallowRef<WorkbenchMaterial[]>([])
const navigationItems = shallowRef<WorkbenchNavigationItem[]>([])
const progress = shallowRef<WorkbenchProgress | null>(null)
const itemDetail = shallowRef<WorkbenchItemDetail | null>(null)
const formData = shallowRef<AnnotationFormData>({})
const activeTaskItemId = shallowRef('')
const isLoadingWorkbench = shallowRef(false)
const isLoadingItem = shallowRef(false)
const errorMessage = shallowRef('')
const reviewStatusText = shallowRef('')
const actionLoading = shallowRef<ReviewActionType>('')
const isRejectDialogOpen = shallowRef(false)
const isModifyDialogOpen = shallowRef(false)
const loadingTextChars = '正在加载中.....'.split('')

const rejectForm = reactive({
  reviewComment: '',
})

const modifyForm = reactive({
  field: '',
  value: null as unknown,
})

let workspaceRequestSeed = 0
let itemRequestSeed = 0

const reviewStatusLabelOverrides = {
  PENDING: '待审核',
  REJECTED: '已驳回',
  REVIEWED: '已通过',
  APPROVED: '已通过',
}

const taskId = computed(() => readRouteText(route.params.taskId))
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
  normalizeReviewDisplayStatus(activeNavigation.value?.displayStatus ?? itemDetail.value?.displayStatus),
)
const activeStatusLabel = computed(() =>
  readString(
    activeNavigation.value?.displayStatusName ?? itemDetail.value?.displayStatusName,
    getDisplayStatusMeta(activeDisplayStatus.value).label,
  ),
)
const isReviewedItem = computed(() => ['REVIEWED', 'APPROVED'].includes(activeDisplayStatus.value))
const canMovePrevious = computed(() => getActiveNavigationPosition() > 0)
const canMoveNext = computed(() => {
  const position = getActiveNavigationPosition()

  return position >= 0 && position < navigationItems.value.length - 1
})
const isFormBusy = computed(() => isLoadingWorkbench.value || isLoadingItem.value || Boolean(actionLoading.value))
const workspaceTitle = computed(() => task.value?.title || '审核工作台')
const editableMaterials = computed(() =>
  materials.value.filter((material) => getMaterialField(material) && getMaterialType(material) !== 'text-display'),
)
const selectedModifyMaterial = computed(
  () => editableMaterials.value.find((material) => getMaterialField(material) === modifyForm.field) ?? null,
)
const selectedModifyMaterialType = computed(() =>
  selectedModifyMaterial.value ? getMaterialType(selectedModifyMaterial.value) : '',
)

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
    return value === 'true' || value === '1'
  }

  return fallback
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

function normalizeReviewDisplayStatus(status: unknown, statusName: unknown = '') {
  const rawStatus = readString(status, '').toUpperCase()
  const rawStatusName = readString(statusName, '')

  if (rawStatus === 'REJECTED' || rawStatusName.includes('驳回')) {
    return 'REJECTED'
  }

  if (['REVIEWED', 'APPROVED'].includes(rawStatus) || rawStatusName.includes('通过')) {
    return 'REVIEWED'
  }

  return 'PENDING'
}

function getDisplayStatusMeta(status: string) {
  const statusMap: Record<string, { label: string; type: string }> = {
    PENDING: { label: '待审核', type: 'pending' },
    REJECTED: { label: '已驳回', type: 'rejected' },
    REVIEWED: { label: '已通过', type: 'reviewed' },
    APPROVED: { label: '已通过', type: 'reviewed' },
  }

  return statusMap[status] ?? { label: status, type: 'pending' }
}

function getConfigValue(material: WorkbenchMaterial, key: string) {
  return material.config?.[key] ?? material[key]
}

function getMaterialType(material: WorkbenchMaterial) {
  const type = readString(material.type, 'input')
  const typeMap: Record<string, string> = {
    showItem: 'text-display',
    textDisplay: 'text-display',
    tagSelect: 'select',
    richText: 'rich-text',
    imageUpload: 'image-upload',
    fileUpload: 'file-upload',
  }

  return typeMap[type] ?? type
}

function getMaterialField(material: WorkbenchMaterial) {
  return readString(material.field ?? material.id, '')
}

function getMaterialLabel(material: WorkbenchMaterial) {
  return readString(material.label, getMaterialField(material) || '未命名字段')
}

function isMaterialMultiple(material: WorkbenchMaterial) {
  return readBoolean(getConfigValue(material, 'multiple'), getMaterialType(material) === 'checkbox')
}

function getMaterialOptions(material: WorkbenchMaterial) {
  const options = getConfigValue(material, 'options')

  if (Array.isArray(options)) {
    return options.map((option) => String(option).trim()).filter(Boolean)
  }

  if (typeof options === 'string') {
    return options
      .split(',')
      .map((option) => option.trim())
      .filter(Boolean)
  }

  return []
}

function getMaterialPlaceholder(material: WorkbenchMaterial) {
  return readString(getConfigValue(material, 'placeholder'), '请输入修改后的数据')
}

function getMaterialMaxLength(material: WorkbenchMaterial) {
  const maxLength = readNumber(getConfigValue(material, 'maxLength'), 0)

  return maxLength > 0 ? maxLength : undefined
}

function normalizeReviewNavigation(item: unknown, index: number): WorkbenchNavigationItem {
  const navigationItem = (item ?? {}) as WorkbenchNavigationItem
  const displayStatus = normalizeReviewDisplayStatus(navigationItem.displayStatus ?? navigationItem.status, navigationItem.displayStatusName)

  return {
    ...navigationItem,
    index: navigationItem.index ?? index + 1,
    displayStatus,
    displayStatusName: readString(navigationItem.displayStatusName, getDisplayStatusMeta(displayStatus).label),
  }
}

function normalizeWorkspace(response: any) {
  const data = getResponseData(response)
  const navigation =
    data.navigation ??
    data.taskItems ??
    data.items ??
    data.list ??
    data.records ??
    []
  const materialList = data.materials ?? data.fields ?? data.templateFields ?? []

  return {
    task: (data.task ?? data.taskInfo ?? {}) as WorkbenchTask,
    materials: Array.isArray(materialList) ? (materialList as WorkbenchMaterial[]) : [],
    navigation: Array.isArray(navigation)
      ? (navigation as WorkbenchNavigationItem[]).map((item, index) => normalizeReviewNavigation(item, index))
      : [],
    progress: (data.progress ?? data.statistic ?? data.stats ?? {}) as WorkbenchProgress,
  }
}

function normalizeProgress(data: WorkbenchProgress): WorkbenchProgress {
  const pendingCount = readNumber(data.pendingCount ?? data.pending)
  const approvedCount = readNumber(data.approvedCount ?? data.reviewed)
  const rejectedCount = readNumber(data.rejectedCount ?? data.rejected)
  const totalCount = readNumber(data.totalCount ?? data.total, pendingCount + approvedCount + rejectedCount)

  return {
    ...data,
    taskId: data.taskId ?? taskId.value,
    totalCount,
    pendingCount,
    approvedCount,
    rejectedCount,
    total: totalCount,
    pending: pendingCount,
    reviewed: approvedCount,
    rejected: rejectedCount,
  }
}

function normalizeItemDetail(response: any): WorkbenchItemDetail {
  const data = getResponseData(response)
  const detail = (data.item ?? data.detail ?? data) as WorkbenchItemDetail
  const displayStatus = normalizeReviewDisplayStatus(detail.displayStatus ?? detail.status, detail.displayStatusName)

  return {
    ...detail,
    displayStatus,
    displayStatusName: readString(detail.displayStatusName, getDisplayStatusMeta(displayStatus).label),
  }
}

function createInitialFormData(annotationData: Record<string, unknown> = {}) {
  const nextFormData: AnnotationFormData = {}

  materials.value.forEach((material) => {
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

  materials.value.forEach((material) => {
    const field = getMaterialField(material)

    if (!field || getMaterialType(material) === 'text-display') {
      return
    }

    annotationData[field] = formData.value[field]
  })

  return annotationData
}

function findInitialTaskItemId(preferredTaskItemId = '') {
  const preferred = navigationItems.value.find((item) => getNavigationItemId(item) === preferredTaskItemId)

  return getNavigationItemId(preferred ?? navigationItems.value[0] ?? {})
}

async function loadItemDetail(taskItemId: string) {
  if (!taskItemId) {
    itemDetail.value = null
    formData.value = createInitialFormData()
    return
  }

  const requestSeed = (itemRequestSeed += 1)
  isLoadingItem.value = true

  try {
    const response = await GetReviewTaskDetail(taskItemId)
    assertApiSuccess(response)

    if (requestSeed !== itemRequestSeed) {
      return
    }

    const detail = normalizeItemDetail(response)
    itemDetail.value = detail
    formData.value = createInitialFormData(
      detail.annotationData && typeof detail.annotationData === 'object' ? detail.annotationData : {},
    )
  } catch (error: any) {
    if (requestSeed !== itemRequestSeed) {
      return
    }

    itemDetail.value = null
    formData.value = createInitialFormData()
    ElMessage.error(readErrorMessage(error, '审核题目详情加载失败'))
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
    const workspaceResponse = await ReviewTask(taskId.value)
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
    errorMessage.value = readErrorMessage(error, '审核工作台加载失败')
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
  reviewStatusText.value = ''
  loadItemDetail(nextTaskItemId)
}

function handleMoveQuestion(offset: number) {
  const currentPosition = getActiveNavigationPosition()
  const nextItem = navigationItems.value[currentPosition + offset]

  if (nextItem) {
    handleSelectNavigation(nextItem)
  }
}

function getNextTaskItemIdAfterReview() {
  const currentPosition = getActiveNavigationPosition()
  const nextItem = navigationItems.value[currentPosition + 1]

  return getNavigationItemId(nextItem ?? navigationItems.value[currentPosition] ?? {})
}

async function refreshAfterReview(successText: string) {
  const currentTaskItemId = activeTaskItemId.value
  const targetTaskItemId = getNextTaskItemIdAfterReview()

  reviewStatusText.value = `${successText} ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`
  await loadWorkbench(targetTaskItemId)

  if (targetTaskItemId && targetTaskItemId !== currentTaskItemId) {
    reviewStatusText.value = ''
  }
}

function getCurrentFieldValue(field: string, material: WorkbenchMaterial | null = null) {
  const value = formData.value[field]
  const materialType = material ? getMaterialType(material) : ''

  if (material && (materialType === 'checkbox' || (materialType === 'select' && isMaterialMultiple(material)))) {
    return Array.isArray(value) ? [...value] : []
  }

  if (materialType === 'rating') {
    const numberValue = Number(value)
    return Number.isFinite(numberValue) ? numberValue : 0
  }

  if (value === undefined || value === null) {
    return ''
  }

  return String(value)
}

function handleModifyFieldChange(field: string) {
  modifyForm.field = field
  modifyForm.value = getCurrentFieldValue(field, selectedModifyMaterial.value)
}

function openRejectDialog() {
  if (!activeTaskItemId.value || isReviewedItem.value) {
    return
  }

  rejectForm.reviewComment = ''
  isRejectDialogOpen.value = true
}

function openModifyDialog() {
  if (!activeTaskItemId.value || isReviewedItem.value) {
    return
  }

  const firstField = getMaterialField(editableMaterials.value[0] ?? {})
  const hasSelectedField = editableMaterials.value.some(
    (material) => getMaterialField(material) === modifyForm.field,
  )
  modifyForm.field = hasSelectedField ? modifyForm.field : firstField
  modifyForm.value = modifyForm.field
    ? getCurrentFieldValue(modifyForm.field, selectedModifyMaterial.value)
    : ''
  isModifyDialogOpen.value = true
}

function normalizeModifyValue(value: unknown, material: WorkbenchMaterial | null) {
  if (!material) {
    return value
  }

  const materialType = getMaterialType(material)

  if (materialType === 'rating') {
    const numberValue = Number(value)
    return Number.isFinite(numberValue) ? numberValue : 0
  }

  if (materialType === 'checkbox' || (materialType === 'select' && isMaterialMultiple(material))) {
    return Array.isArray(value) ? [...value] : []
  }

  return value
}

function isEmptyModifyValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.length === 0
  }

  return value === undefined || value === null || String(value).trim() === ''
}

function handleModifyFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const fileNames = Array.from(input.files ?? []).map((file) => file.name)
  const material = selectedModifyMaterial.value

  modifyForm.value = material && isMaterialMultiple(material) ? fileNames : fileNames[0] ?? ''
}

function formatModifyFileValue(value: unknown) {
  return Array.isArray(value) ? value.join('、') : readString(value, '尚未选择文件')
}

async function handleApprove() {
  if (!activeTaskItemId.value || isReviewedItem.value) {
    return
  }

  actionLoading.value = 'approve'

  try {
    const response = await ReviewApprove(activeTaskItemId.value)
    assertApiSuccess(response)
    ElMessage.success('审核已通过')
    await refreshAfterReview('审核已通过')
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '审核通过失败'))
  } finally {
    actionLoading.value = ''
  }
}

async function handleReject() {
  const reviewComment = rejectForm.reviewComment.trim()

  if (!activeTaskItemId.value || isReviewedItem.value) {
    return
  }

  if (!reviewComment) {
    ElMessage.warning('请输入驳回原因')
    return
  }

  actionLoading.value = 'reject'

  try {
    const response = await ReviewReject(activeTaskItemId.value, { reviewComment })
    assertApiSuccess(response)
    ElMessage.success('审核已驳回')
    isRejectDialogOpen.value = false
    await refreshAfterReview('审核已驳回')
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '审核驳回失败'))
  } finally {
    actionLoading.value = ''
  }
}

async function handleModifyAndApprove() {
  const field = modifyForm.field
  const material = selectedModifyMaterial.value

  if (!activeTaskItemId.value || isReviewedItem.value) {
    return
  }

  if (!field) {
    ElMessage.warning('请选择需要修改的字段')
    return
  }

  if (isEmptyModifyValue(modifyForm.value)) {
    ElMessage.warning('请输入修改数据')
    return
  }

  actionLoading.value = 'modify'

  try {
    const annotationData = {
      ...buildAnnotationData(),
      [field]: normalizeModifyValue(modifyForm.value, material),
    }
    const response = await ReviewModify(activeTaskItemId.value, { annotationData })
    assertApiSuccess(response)
    ElMessage.success('修改并通过成功')
    isModifyDialogOpen.value = false
    await refreshAfterReview('修改并通过成功')
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '修改并通过失败'))
  } finally {
    actionLoading.value = ''
  }
}

watch(
  taskId,
  () => {
    activeTaskItemId.value = ''
    reviewStatusText.value = ''
    loadWorkbench('')
  },
  { immediate: true },
)
</script>

<template>
  <section class="labeling-workbench review-workbench">
    <header class="labeling-workbench__summary">
      <div class="labeling-workbench__summary-copy">
        <RouterLink class="labeling-workbench__back-link" to="/workbench/review/list">
          返回任务列表
        </RouterLink>
        <span>审核员工作台</span>
        <strong>{{ workspaceTitle }}</strong>
        <small>第 {{ activeQuestionIndex }} / {{ totalQuestionCount }} 题</small>
      </div>

      <div class="labeling-workbench__top-actions">
        <span
          class="labeling-workbench__status"
          :class="`labeling-workbench__status--${getDisplayStatusMeta(activeDisplayStatus).type}`"
        >
          {{ reviewStatusText || activeStatusLabel }}
        </span>
        <strong>{{ currentUser.name }} / Reviewer</strong>
      </div>
    </header>

    <main class="labeling-workbench__body">
      <aside class="labeling-workbench__rail labeling-workbench__rail--left">
        <QuestionNavigation
          :active-task-item-id="activeTaskItemId"
          :items="navigationItems"
          :status-label-overrides="reviewStatusLabelOverrides"
          use-display-status-name
          @select="handleSelectNavigation"
        />
      </aside>

      <section class="labeling-workbench__center">
        <p v-if="errorMessage" class="labeling-workbench__error" role="alert">{{ errorMessage }}</p>

        <div v-if="isLoadingWorkbench" class="labeling-workbench__loading">正在加载审核工作台...</div>

        <template v-else>
          <div class="labeling-workbench__form-shell" :class="{ 'labeling-workbench__form-shell--loading': isLoadingItem }">
            <AnnotationMaterialForm
              v-model="formData"
              :item-detail="itemDetail"
              :materials="materials"
              :question-index="activeQuestionIndex"
              raw-data-collapsible
              readonly
              swap-field-label
              :task="task"
            >
              <AiPreReviewResultPanel
                v-if="itemDetail?.aiPreReviewResult"
                :result="itemDetail.aiPreReviewResult"
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
              <span v-if="isReviewedItem">当前题目已通过</span>
              <el-button
                type="danger"
                :disabled="isFormBusy || isReviewedItem || !activeTaskItemId"
                @click="openRejectDialog"
              >
                驳回
              </el-button>
              <el-button
                type="warning"
                :disabled="isFormBusy || isReviewedItem || !activeTaskItemId || !editableMaterials.length"
                @click="openModifyDialog"
              >
                修改并通过
              </el-button>
              <el-button
                type="primary"
                :disabled="isFormBusy || isReviewedItem || !activeTaskItemId"
                :loading="actionLoading === 'approve'"
                @click="handleApprove"
              >
                通过
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
        <TaskProgressPanel :progress="progress" variant="review" />
        <TaskTimelinePanel :task-item-id="activeTaskItemId" />
      </aside>
    </main>

    <el-dialog v-model="isRejectDialogOpen" title="驳回原因" width="520px" destroy-on-close>
      <el-input
        v-model="rejectForm.reviewComment"
        maxlength="300"
        placeholder="请输入驳回原因"
        :rows="5"
        show-word-limit
        type="textarea"
      />
      <template #footer>
        <div class="review-workbench__dialog-actions">
          <el-button :disabled="actionLoading === 'reject'" @click="isRejectDialogOpen = false">取消</el-button>
          <el-button type="danger" :loading="actionLoading === 'reject'" @click="handleReject">提交驳回</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="isModifyDialogOpen" title="修改并通过" width="560px" destroy-on-close>
      <section class="review-workbench__modify-form">
        <label>
          <span>修改字段</span>
          <el-select
            v-model="modifyForm.field"
            placeholder="请选择字段"
            @change="handleModifyFieldChange"
          >
            <el-option
              v-for="material in editableMaterials"
              :key="getMaterialField(material)"
              :label="`${getMaterialField(material)}（${getMaterialLabel(material)}）`"
              :value="getMaterialField(material)"
            />
          </el-select>
        </label>

        <div v-if="selectedModifyMaterial" class="review-workbench__modify-control">
          <span>修改数据</span>
          <el-input
            v-if="selectedModifyMaterialType === 'input'"
            :maxlength="getMaterialMaxLength(selectedModifyMaterial)"
            :model-value="String(modifyForm.value ?? '')"
            :placeholder="getMaterialPlaceholder(selectedModifyMaterial)"
            show-word-limit
            @update:model-value="modifyForm.value = $event"
          />
          <el-input
            v-else-if="selectedModifyMaterialType === 'textarea' || selectedModifyMaterialType === 'rich-text'"
            :maxlength="getMaterialMaxLength(selectedModifyMaterial)"
            :model-value="String(modifyForm.value ?? '')"
            :placeholder="getMaterialPlaceholder(selectedModifyMaterial)"
            :rows="5"
            show-word-limit
            type="textarea"
            @update:model-value="modifyForm.value = $event"
          />
          <el-radio-group
            v-else-if="selectedModifyMaterialType === 'radio'"
            :model-value="String(modifyForm.value ?? '')"
            @update:model-value="modifyForm.value = $event"
          >
            <el-radio-button
              v-for="option in getMaterialOptions(selectedModifyMaterial)"
              :key="option"
              :label="option"
            >
              {{ option }}
            </el-radio-button>
          </el-radio-group>
          <el-checkbox-group
            v-else-if="selectedModifyMaterialType === 'checkbox'"
            :model-value="Array.isArray(modifyForm.value) ? modifyForm.value : []"
            @update:model-value="modifyForm.value = $event"
          >
            <el-checkbox-button
              v-for="option in getMaterialOptions(selectedModifyMaterial)"
              :key="option"
              :label="option"
            >
              {{ option }}
            </el-checkbox-button>
          </el-checkbox-group>
          <el-select
            v-else-if="selectedModifyMaterialType === 'select'"
            :model-value="isMaterialMultiple(selectedModifyMaterial)
              ? (Array.isArray(modifyForm.value) ? modifyForm.value : [])
              : String(modifyForm.value ?? '')"
            :multiple="isMaterialMultiple(selectedModifyMaterial)"
            :placeholder="getMaterialPlaceholder(selectedModifyMaterial)"
            @update:model-value="modifyForm.value = $event"
          >
            <el-option
              v-for="option in getMaterialOptions(selectedModifyMaterial)"
              :key="option"
              :label="option"
              :value="option"
            />
          </el-select>
          <el-rate
            v-else-if="selectedModifyMaterialType === 'rating'"
            :model-value="Number(modifyForm.value) || 0"
            @update:model-value="modifyForm.value = $event"
          />
          <div
            v-else-if="selectedModifyMaterialType === 'image-upload' || selectedModifyMaterialType === 'file-upload'"
            class="review-workbench__modify-upload"
          >
            <label>
              <input
                :accept="readString(getConfigValue(selectedModifyMaterial, 'accept'))"
                :multiple="isMaterialMultiple(selectedModifyMaterial)"
                type="file"
                @change="handleModifyFileChange"
              >
              <span>{{ selectedModifyMaterialType === 'image-upload' ? '选择图片' : '选择文件' }}</span>
            </label>
            <small>{{ formatModifyFileValue(modifyForm.value) }}</small>
          </div>
          <el-input
            v-else
            :model-value="String(modifyForm.value ?? '')"
            :placeholder="getMaterialPlaceholder(selectedModifyMaterial)"
            @update:model-value="modifyForm.value = $event"
          />
        </div>
      </section>
      <template #footer>
        <div class="review-workbench__dialog-actions">
          <el-button :disabled="actionLoading === 'modify'" @click="isModifyDialogOpen = false">取消</el-button>
          <el-button type="warning" :loading="actionLoading === 'modify'" @click="handleModifyAndApprove">
            提交修改并通过
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.labeling-workbench {
  display: grid;
  height: calc(100vh - 80px);
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

.labeling-workbench__status--pending {
  background: #fff7ed;
  color: #f97316;
}

.labeling-workbench__status--rejected {
  background: #fee2e2;
  color: #ef4444;
}

.labeling-workbench__status--reviewed {
  background: #ecfdf5;
  color: #16a34a;
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

.review-workbench .labeling-workbench__rail--left :deep(.question-nav__item--rejected .question-nav__status) {
  color: #ef4444;
}

.review-workbench .labeling-workbench__rail--left :deep(.question-nav__item--reviewed .question-nav__status) {
  color: #16a34a;
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
  min-height: 420px;
  place-items: center;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.88);
  color: #64748b;
  font-size: 14px;
  font-weight: 800;
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

.review-workbench__modify-form {
  display: grid;
  gap: 16px;
}

.review-workbench__modify-form > label,
.review-workbench__modify-control {
  display: grid;
  gap: 8px;
}

.review-workbench__modify-form > label > span,
.review-workbench__modify-control > span {
  color: #334155;
  font-size: 13px;
  font-weight: 850;
}

.review-workbench__modify-control :deep(.el-radio-group),
.review-workbench__modify-control :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-workbench__modify-upload {
  display: grid;
  gap: 8px;
}

.review-workbench__modify-upload label {
  display: inline-flex;
  width: fit-content;
  min-height: 36px;
  align-items: center;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #2563eb;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
}

.review-workbench__modify-upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.review-workbench__modify-upload small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.review-workbench__dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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
