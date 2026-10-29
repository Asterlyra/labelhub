<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { createStorageKey, readLocalDraft, removeLocalDraft, writeLocalDraft } from '../../composables/useLocalDraft'
import ComponentDetailsPanel from '../../components/template-designer/ComponentDetailsPanel.vue'
import DesignerCanvas from '../../components/template-designer/DesignerCanvas.vue'
import DesignerPalette from '../../components/template-designer/DesignerPalette.vue'
import {
  buildJsonEditorTemplateItem,
  getJsonEditorAdditionalContent,
  getJsonEditorValidationError,
} from '../../components/template-designer/jsonEditor'
import loadingAnimation from '../../icon/loading.gif'
import { designerComponents } from '../../mocks/designerComponents.mock'
import type {
  CanvasComponent,
  CanvasComponentConfig,
  DesignerComponentType,
} from '../../types/template'
import { GetTemplateDetail, PostDesignTemplate, UpdateDesignTemplate } from '../../utils/api'

interface PreviewTemplateItem {
  type: string
  field: string
  label: string
  source?: string
  required?: boolean
  maxLength?: number
  placeholder?: string
  multiple?: boolean
  accept?: string
  maxSize?: number
  maxCount?: number
  options?: string[]
  url?: string
  content?: string
}

interface BackendTemplateDetail {
  id?: string | number
  name?: string
  templateName?: string
  title?: string
  description?: string
  desc?: string
  components?: unknown[]
  [key: string]: unknown
}

interface BackendTemplateComponent {
  id?: string | number
  type?: string
  field?: string
  label?: string
  source?: string
  required?: boolean | number | string
  maxLength?: number | string
  placeholder?: string
  content?: string
  multiple?: boolean | number | string
  accept?: string
  maxSize?: number | string
  maxCount?: number | string
  options?: unknown
  url?: string
  uploadFileName?: string
  fileName?: string
  uploadPreviewUrl?: string
  sortOrder?: number
  [key: string]: unknown
}

const LLM_TRIGGER_TYPE: DesignerComponentType = 'llmInteraction'
const LLM_TRIGGER_FIELD = 'aiSummary'
const LLM_TRIGGER_LABEL = 'AI智能摘要'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const canvasItems = shallowRef<CanvasComponent[]>([])
const isPreviewOpen = shallowRef(false)
const selectedComponentId = shallowRef('')
const isSubmittingTemplate = shallowRef(false)
const isLoadingTemplateDetail = shallowRef(false)
const isPublishLoadingOpen = shallowRef(false)
const templateDetailErrorMessage = shallowRef('')
const loadedTemplateName = shallowRef('')
const loadedTemplateDescription = shallowRef('')
const hasLocalTemplateDraft = shallowRef(false)
const isLocalDraftReady = shallowRef(false)
const loadingTextChars = '正在加载中.....'.split('')

let componentSeed = 0

const templateId = computed(() => getRouteText(route.params.templateId) || getQueryText(route.query.id))
const isEditMode = computed(() => Boolean(templateId.value))
const isBlockingLoadingOpen = computed(() => isPublishLoadingOpen.value || isLoadingTemplateDetail.value)
const componentCountText = computed(() => `${canvasItems.value.length} 个组件`)
const templateName = computed(() => loadedTemplateName.value || getQueryText(route.query.name) || '未命名模板')
const templateDescription = computed(() => loadedTemplateDescription.value || getQueryText(route.query.description))
const templateDraftKey = computed(() =>
  createStorageKey(
    'template-designer',
    `${authStore.currentUser.id}:${templateId.value || getQueryText(route.query.name) || 'new'}`,
    authStore.activeRole,
  ),
)
const previewTemplate = computed(() => ({
  name: templateName.value,
  description: templateDescription.value,
  components: canvasItems.value.map((item) => toPreviewTemplateItem(item)),
}))
const previewJson = computed(() => JSON.stringify(previewTemplate.value, null, 2))
const uploadFileList = computed(() =>
  canvasItems.value.flatMap((item) => {
    if (!isUploadComponent(item.type) || !item.config.uploadFile) {
      return []
    }

    return [
      {
        file: item.config.uploadFile,
        field: item.config.field,
      },
    ]
  }),
)
const selectedComponent = computed(
  () => canvasItems.value.find((item) => item.id === selectedComponentId.value) ?? null,
)
const primaryActionText = computed(() => {
  if (isSubmittingTemplate.value) {
    return isEditMode.value ? '更新中' : '发布中'
  }

  return isEditMode.value ? '更新模板' : '发布模板'
})

function getQueryText(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}

function getRouteText(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }

  return typeof value === 'string' ? value : ''
}

function createDefaultConfig(type: DesignerComponentType, fallbackLabel: string): CanvasComponentConfig {
  if (type === 'text-display') {
    return {
      field: 'rawTitle',
      label: '文本说明',
      source: '',
      options: [],
    }
  }

  if (type === 'input') {
    return {
      field: 'cleanTitle',
      label: '输入框',
      required: true,
      maxLength: 35,
      placeholder: '请输入内容',
      options: [],
    }
  }

  if (type === 'textarea') {
    return {
      field: 'productDesc',
      label: '多行文本框',
      required: true,
      maxLength: 500,
      placeholder: '请输入描述',
      options: [],
    }
  }

  if (type === 'radio') {
    return {
      field: 'category',
      label: '单选框',
      required: true,
      options: [],
    }
  }

  if (type === 'checkbox') {
    return {
      field: 'attributes',
      label: '多选框',
      options: [],
    }
  }

  if (type === 'select') {
    return {
      field: 'sellingPoints',
      label: '下拉选项框',
      multiple: true,
      options: [],
    }
  }

  if (type === 'rich-text') {
    return {
      field: 'detailHtml',
      label: '详情页内容',
      content: '请输入详情内容',
      maxLength: 2000,
      placeholder: '请输入详情内容',
      options: [],
    }
  }

  if (type === 'image-upload') {
    return {
      field: 'testImage',
      label: '图片组件',
      required: true,
      accept: '.jpg,.png,.webp',
      maxSize: 5,
      maxCount: 9,
      uploadFile: null,
      uploadFileName: '',
      uploadPreviewUrl: '',
      options: [],
    }
  }

  if (type === 'file-upload') {
    return {
      field: 'testFile',
      label: '文件组件',
      required: true,
      accept: '.pdf,.doc,.docx',
      maxSize: 10,
      maxCount: 5,
      uploadFile: null,
      uploadFileName: '',
      uploadPreviewUrl: '',
      options: [],
    }
  }

  if (type === 'json-editor') {
    return {
      field: 'json',
      label: 'JSON编辑器',
      required: true,
      maxLength: 20000,
      placeholder: '请输入 JSON 内容',
      jsonAdditionalContent: '',
      options: [],
    }
  }

  if (type === LLM_TRIGGER_TYPE) {
    return {
      field: LLM_TRIGGER_FIELD,
      label: LLM_TRIGGER_LABEL,
      required: false,
      options: [],
    }
  }

  return {
    field: 'rating',
    label: fallbackLabel,
    required: false,
    options: [],
  }
}

function readString(value: unknown, fallback = '') {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function readNumber(value: unknown, fallback?: number) {
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

function readStringList(value: unknown, fallback: string[] = []) {
  if (!Array.isArray(value)) {
    return fallback
  }

  return value.filter((item): item is string => typeof item === 'string')
}

function getDesignerType(type: unknown): DesignerComponentType {
  if (type === 'showItem') {
    return 'text-display'
  }

  if (type === 'tagSelect') {
    return 'select'
  }

  if (type === 'richText') {
    return 'rich-text'
  }

  if (type === 'imageUpload') {
    return 'image-upload'
  }

  if (type === 'fileUpload') {
    return 'file-upload'
  }

  if (type === 'JsonEditor' || type === 'jsonEditor') {
    return 'json-editor'
  }

  if (type === 'LLMTrigger' || type === 'LlmTrigger' || type === 'llmTrigger' || type === 'llm-trigger') {
    return LLM_TRIGGER_TYPE
  }

  if (type === 'llmInteraction' || type === 'LLMInteraction') {
    return LLM_TRIGGER_TYPE
  }

  const typeValue = typeof type === 'string' ? type : ''
  const component = designerComponents.find((item) => item.type === typeValue)

  return component?.type ?? 'input'
}

function getDesignerLabel(type: DesignerComponentType) {
  return designerComponents.find((item) => item.type === type)?.label ?? '组件'
}

function pickTemplateDetail(response: any): BackendTemplateDetail {
  const payload = response?.data ?? response
  return (payload?.data ?? payload ?? {}) as BackendTemplateDetail
}

function toCanvasComponent(component: BackendTemplateComponent, index: number): CanvasComponent {
  const type = getDesignerType(component.type)
  const defaultConfig = createDefaultConfig(type, getDesignerLabel(type))
  const content = readString(component.content ?? component.placeholder, defaultConfig.content ?? '')
  const config: CanvasComponentConfig = {
    ...defaultConfig,
    field:
      type === 'json-editor'
        ? 'json'
        : readString(component.field, defaultConfig.field),
    label:
      type === 'json-editor'
        ? 'JSON编辑器'
        : type === LLM_TRIGGER_TYPE
          ? LLM_TRIGGER_LABEL
          : readString(component.label, defaultConfig.label),
    source: readString(component.source, defaultConfig.source ?? ''),
    required: readBoolean(component.required, Boolean(defaultConfig.required)),
    maxLength: readNumber(component.maxLength, defaultConfig.maxLength),
    placeholder: readString(component.placeholder, defaultConfig.placeholder ?? content),
    multiple: readBoolean(component.multiple, Boolean(defaultConfig.multiple)),
    accept: readString(component.accept, defaultConfig.accept ?? ''),
    maxSize: readNumber(component.maxSize, defaultConfig.maxSize),
    maxCount: readNumber(component.maxCount, defaultConfig.maxCount),
    content,
    jsonAdditionalContent:
      type === 'json-editor'
        ? getJsonEditorAdditionalContent(component)
        : defaultConfig.jsonAdditionalContent,
    options: readStringList(component.options, defaultConfig.options),
    uploadFile: null,
    uploadFileName: readString(component.uploadFileName ?? component.fileName, defaultConfig.uploadFileName ?? ''),
    uploadPreviewUrl: readString(component.uploadPreviewUrl ?? component.url, defaultConfig.uploadPreviewUrl ?? ''),
  }
  const componentId = component.id ?? `${type}-${index + 1}`

  return {
    id: `loaded-${componentId}`,
    type,
    label: config.label,
    fieldName: config.field,
    config,
  }
}

function normalizeTemplateComponents(components: unknown[]) {
  return normalizeCanvasOrder(
    components.map((component, index) => toCanvasComponent((component ?? {}) as BackendTemplateComponent, index)),
  )
}

function isLlmTriggerComponent(item: CanvasComponent) {
  return item.type === LLM_TRIGGER_TYPE
}

function normalizeCanvasOrder(items: CanvasComponent[]) {
  const llmTrigger = items.find(isLlmTriggerComponent)
  const ordinaryItems = items.filter((item) => !isLlmTriggerComponent(item))

  return llmTrigger ? [...ordinaryItems, llmTrigger] : ordinaryItems
}

function toPreviewTemplateItem(item: CanvasComponent): PreviewTemplateItem {
  const config = item.config
  const baseItem = {
    field: config.field,
    label: config.label,
  }

  if (item.type === 'text-display') {
    return {
      type: 'showItem',
      ...baseItem,
      source: config.source ?? '',
    }
  }

  if (item.type === 'input') {
    return {
      type: 'input',
      ...baseItem,
      required: Boolean(config.required),
      maxLength: Number(config.maxLength ?? 35),
      placeholder: config.placeholder ?? '',
    }
  }

  if (item.type === 'textarea') {
    return {
      type: 'textarea',
      ...baseItem,
      required: Boolean(config.required),
      maxLength: Number(config.maxLength ?? 500),
      placeholder: config.placeholder ?? '',
    }
  }

  if (item.type === 'radio') {
    return {
      type: 'radio',
      ...baseItem,
      required: Boolean(config.required),
      options: config.options,
    }
  }

  if (item.type === 'checkbox') {
    return {
      type: 'checkbox',
      ...baseItem,
      options: config.options,
    }
  }

  if (item.type === 'select') {
    return {
      type: 'tagSelect',
      ...baseItem,
      multiple: Boolean(config.multiple),
      options: config.options,
    }
  }

  if (item.type === 'rich-text') {
    return {
      type: 'richText',
      ...baseItem,
      maxLength: Number(config.maxLength ?? 2000),
      placeholder: config.content ?? '',
    }
  }

  if (item.type === 'image-upload') {
    return {
      type: 'imageUpload',
      ...baseItem,
      required: Boolean(config.required),
      accept: config.accept ?? '.jpg,.png,.webp',
      maxSize: Number(config.maxSize ?? 5),
      maxCount: Number(config.maxCount ?? 9),
      url: getPersistedUploadUrl(config),
    }
  }

  if (item.type === 'file-upload') {
    return {
      type: 'fileUpload',
      ...baseItem,
      required: Boolean(config.required),
      accept: config.accept ?? '.pdf,.doc,.docx',
      maxSize: Number(config.maxSize ?? 10),
      maxCount: Number(config.maxCount ?? 5),
      url: getPersistedUploadUrl(config),
    }
  }

  if (item.type === 'json-editor') {
    return buildJsonEditorTemplateItem(config.jsonAdditionalContent)
  }

  if (item.type === LLM_TRIGGER_TYPE) {
    return {
      type: 'llmInteraction',
      field: config.field,
      label: LLM_TRIGGER_LABEL,
    }
  }

  return {
    type: item.type,
    ...baseItem,
  }
}

function isUploadComponent(type: DesignerComponentType) {
  return type === 'image-upload' || type === 'file-upload'
}

function getPersistedUploadUrl(config: CanvasComponentConfig) {
  const uploadPreviewUrl = config.uploadPreviewUrl

  return uploadPreviewUrl && !uploadPreviewUrl.startsWith('blob:') ? uploadPreviewUrl : undefined
}

function revokeUploadPreviewUrl(item: CanvasComponent) {
  if (item.config.uploadPreviewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(item.config.uploadPreviewUrl)
  }
}

function replaceCanvasItems(items: CanvasComponent[]) {
  canvasItems.value.forEach((item) => {
    revokeUploadPreviewUrl(item)
  })

  canvasItems.value = normalizeCanvasOrder(items)
  selectedComponentId.value = canvasItems.value[0]?.id ?? ''
  componentSeed = canvasItems.value.length
}

function resetDesignerForCreateMode() {
  loadedTemplateName.value = ''
  loadedTemplateDescription.value = ''
  templateDetailErrorMessage.value = ''
  replaceCanvasItems([])
}

function saveTemplateDraftLocally() {
  if (!isLocalDraftReady.value || isEditMode.value) {
    return
  }

  writeLocalDraft(templateDraftKey.value, {
    components: JSON.parse(JSON.stringify(canvasItems.value)),
  })
  hasLocalTemplateDraft.value = true
}

function restoreTemplateDraftLocally() {
  if (isEditMode.value) {
    hasLocalTemplateDraft.value = false
    isLocalDraftReady.value = true
    return
  }

  const savedDraft = readLocalDraft<{ components?: unknown[] }>(templateDraftKey.value, {})

  if (!savedDraft || !Array.isArray(savedDraft.components)) {
    hasLocalTemplateDraft.value = false
    isLocalDraftReady.value = true
    return
  }

  const restoredItems = savedDraft.components.filter((item): item is CanvasComponent => {
    if (!item || typeof item !== 'object') {
      return false
    }

    const candidate = item as Partial<CanvasComponent>
    return typeof candidate.id === 'string' && typeof candidate.type === 'string' && Boolean(candidate.config)
  })

  replaceCanvasItems(restoredItems)
  hasLocalTemplateDraft.value = restoredItems.length > 0
  isLocalDraftReady.value = true
}

function clearTemplateDraftLocally() {
  removeLocalDraft(templateDraftKey.value)
  hasLocalTemplateDraft.value = false
}

function handleSaveTemplateDraft() {
  saveTemplateDraftLocally()
  ElMessage.success('模板草稿已保存到当前浏览器')
}

async function loadTemplateDetail(id: string) {
  if (!id) {
    resetDesignerForCreateMode()
    return
  }

  isLoadingTemplateDetail.value = true
  templateDetailErrorMessage.value = ''

  try {
    const response = await GetTemplateDetail(id)
    const detail = pickTemplateDetail(response)
    const nextComponents = normalizeTemplateComponents(Array.isArray(detail.components) ? detail.components : [])

    loadedTemplateName.value = readString(detail.name ?? detail.templateName ?? detail.title, '未命名模板')
    loadedTemplateDescription.value = readString(detail.description ?? detail.desc, '')
    replaceCanvasItems(nextComponents)
  } catch (error: any) {
    templateDetailErrorMessage.value =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || '模板详情获取失败'
    ElMessage.error(templateDetailErrorMessage.value)
  } finally {
    isLoadingTemplateDetail.value = false
  }
}

function normalizeFieldName(field: string) {
  return field.trim()
}

function createUniqueFieldName(field: string, currentId = '') {
  const baseField = normalizeFieldName(field)

  if (!baseField) {
    return ''
  }

  const usedFields = new Set(
    canvasItems.value
      .filter((item) => item.id !== currentId)
      .map((item) => item.config.field),
  )

  if (!usedFields.has(baseField)) {
    return baseField
  }

  let fieldIndex = 1
  let nextField = `${baseField}${fieldIndex}`

  while (usedFields.has(nextField)) {
    fieldIndex += 1
    nextField = `${baseField}${fieldIndex}`
  }

  return nextField
}

function handleDropComponent(type: DesignerComponentType) {
  const component = designerComponents.find((item) => item.type === type)

  if (!component) {
    return
  }

  if (type === 'json-editor') {
    const existingJsonEditor = canvasItems.value.find((item) => item.type === 'json-editor')

    if (existingJsonEditor) {
      selectedComponentId.value = existingJsonEditor.id
      ElMessage.warning('JSON编辑器仅支持添加一个')
      return
    }
  }

  if (type === LLM_TRIGGER_TYPE) {
    const existingLlmTrigger = canvasItems.value.find(isLlmTriggerComponent)

    if (existingLlmTrigger) {
      selectedComponentId.value = existingLlmTrigger.id
      ElMessage.warning('LLM触发器仅支持添加一个')
      return
    }
  }

  componentSeed += 1

  const config = createDefaultConfig(type, component.label)
  config.field =
    type === 'json-editor'
      ? 'json'
      : createUniqueFieldName(config.field)
  const nextComponent: CanvasComponent = {
    id: `${type}-${componentSeed}`,
    type,
    label: component.label,
    fieldName: config.field,
    config,
  }

  canvasItems.value = normalizeCanvasOrder([...canvasItems.value, nextComponent])
  selectedComponentId.value = nextComponent.id
}

function handleUpdateComponent(id: string, configPatch: Partial<CanvasComponentConfig>) {
  canvasItems.value = canvasItems.value.map((item) => {
    if (item.id !== id) {
      return item
    }

    const nextConfigPatch =
      item.type === 'json-editor'
        ? {
            ...configPatch,
            field: 'json',
          }
        : typeof configPatch.field === 'string'
        ? {
            ...configPatch,
            field: createUniqueFieldName(configPatch.field, id),
          }
        : configPatch
    const config = {
      ...item.config,
      ...nextConfigPatch,
    }

    return {
      ...item,
      fieldName: config.field,
      config,
    }
  })
}

function handleReorderComponents(items: CanvasComponent[]) {
  canvasItems.value = normalizeCanvasOrder(items)
}

function handleRemoveComponent(id: string) {
  const removedItem = canvasItems.value.find((item) => item.id === id)

  if (removedItem) {
    revokeUploadPreviewUrl(removedItem)
  }

  const nextItems = canvasItems.value.filter((item) => item.id !== id)
  canvasItems.value = nextItems

  if (selectedComponentId.value === id) {
    selectedComponentId.value = nextItems[0]?.id ?? ''
  }
}

function handleOpenPreview() {
  isPreviewOpen.value = true
}

function handleClosePreview() {
  isPreviewOpen.value = false
}

function handlePreviewEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && isPreviewOpen.value) {
    handleClosePreview()
  }
}

function handleViewDetails(id: string) {
  selectedComponentId.value = id
}

function waitForPublishLoadingDuration() {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, 1500)
  })
}

function validateJsonEditorComponents() {
  const invalidItem = canvasItems.value.find((item) => {
    return item.type === 'json-editor' && getJsonEditorValidationError(item.config.jsonAdditionalContent)
  })

  if (!invalidItem) {
    return true
  }

  selectedComponentId.value = invalidItem.id
  ElMessage.error(`JSON编辑器：${getJsonEditorValidationError(invalidItem.config.jsonAdditionalContent)}`)
  return false
}

async function handleSubmitTemplate(redirectAfterSuccess = false) {
  if (isSubmittingTemplate.value) {
    return
  }

  if (!validateJsonEditorComponents()) {
    return
  }

  isSubmittingTemplate.value = true
  const loadingTask = redirectAfterSuccess ? waitForPublishLoadingDuration() : Promise.resolve()
  const currentTemplateId = templateId.value
  const successMessage = isEditMode.value ? '模板更新成功' : '模板发布成功'
  const failureMessage = isEditMode.value ? '模板更新失败' : '模板发布失败'

  if (redirectAfterSuccess) {
    isPublishLoadingOpen.value = true
  }

  try {
    const response = isEditMode.value && currentTemplateId
      ? await UpdateDesignTemplate(currentTemplateId, previewTemplate.value, uploadFileList.value)
      : await PostDesignTemplate(previewTemplate.value, uploadFileList.value)
    const isSuccessfulResponse = response?.code === undefined || Number(response?.code) === 200

    if (!isSuccessfulResponse) {
      await loadingTask
      ElMessage.error(response?.message || response?.msg || failureMessage)
      return
    }

    await loadingTask
    ElMessage.success(response?.message || response?.msg || successMessage)

    if (redirectAfterSuccess) {
      clearTemplateDraftLocally()
      await router.push('/owner/templates')
    }
  } catch (error: any) {
    await loadingTask
    const errorMessage =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || failureMessage
    ElMessage.error(errorMessage)
  } finally {
    isPublishLoadingOpen.value = false
    isSubmittingTemplate.value = false
  }
}

watch(
  templateId,
  (id) => {
    if (id) {
      isLocalDraftReady.value = false
      loadTemplateDetail(id)
      return
    }

    resetDesignerForCreateMode()
    restoreTemplateDraftLocally()
  },
  { immediate: true },
)

watch(
  canvasItems,
  () => {
    saveTemplateDraftLocally()
  },
  { deep: true },
)

onUnmounted(() => {
  window.removeEventListener('keydown', handlePreviewEscape)
  canvasItems.value.forEach((item) => {
    revokeUploadPreviewUrl(item)
  })
})

onMounted(() => {
  window.addEventListener('keydown', handlePreviewEscape)
})
</script>

<template>
  <section class="template-designer">
    <header class="template-designer__topbar">
      <div class="template-designer__title">
        <RouterLink class="template-designer__back" to="/owner/templates" aria-label="返回模板列表">
          返回
        </RouterLink>
        <div>
          <p>模板搭建器</p>
          <h1>{{ templateName }}</h1>
          <span v-if="templateDescription" class="template-designer__description">
            {{ templateDescription }}
          </span>
        </div>
      </div>

      <div class="template-designer__actions" aria-label="模板操作">
        <span>{{ componentCountText }}</span>
        <span v-if="hasLocalTemplateDraft" class="template-designer__draft-status" role="status">
          本地草稿已保存
        </span>
        <button type="button" @click="handleOpenPreview">JSON预览</button>
        <button type="button" :disabled="isEditMode" @click="handleSaveTemplateDraft">保存草稿</button>
        <button
          class="template-designer__primary"
          type="button"
          :disabled="isSubmittingTemplate"
          @click="handleSubmitTemplate(true)"
        >
          {{ primaryActionText }}
        </button>
      </div>
    </header>

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

    <p v-if="templateDetailErrorMessage" class="template-designer__error" role="alert">
      {{ templateDetailErrorMessage }}
    </p>

    <p v-if="hasLocalTemplateDraft" class="template-designer__draft-notice" role="status">
      当前草稿只保存在本浏览器，发布模板后才会写入系统。
      <button type="button" @click="clearTemplateDraftLocally">清除本地草稿</button>
    </p>

    <div class="template-designer__workspace">
      <DesignerPalette :components="designerComponents" />
      <DesignerCanvas
        :items="canvasItems"
        :selected-item-id="selectedComponentId"
        @drop-component="handleDropComponent"
        @update-component="handleUpdateComponent"
        @reorder-components="handleReorderComponents"
        @remove-component="handleRemoveComponent"
        @view-details="handleViewDetails"
      />
      <ComponentDetailsPanel
        :has-canvas-items="canvasItems.length > 0"
        :item="selectedComponent"
        @update-component="handleUpdateComponent"
      />
    </div>

    <div
      v-if="isPreviewOpen"
      class="template-designer__dialog-backdrop"
      role="presentation"
      @click.self="handleClosePreview"
    >
      <section
        class="template-designer__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="template-json-dialog-title"
      >
        <header class="template-designer__dialog-header">
          <span>预览</span>
          <strong id="template-json-dialog-title">模板 JSON</strong>
          <button
            class="template-designer__dialog-close"
            type="button"
            aria-label="关闭模板 JSON 预览"
            @click="handleClosePreview"
          >
            ×
          </button>
        </header>

        <pre class="template-designer__json">{{ previewJson }}</pre>

        <footer class="template-designer__dialog-actions">
          <button type="button" @click="handleClosePreview">关闭</button>
        </footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.template-designer {
  display: grid;
  gap: 18px;
  animation: template-designer-page-in 0.46s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.template-designer__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--lh-shadow-card);
  padding: 14px 16px;
  animation: template-designer-section-in 0.48s 0.04s cubic-bezier(0.2, 0.82, 0.2, 1) both;
  backdrop-filter: blur(22px);
}

.template-designer__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

.template-designer__back {
  display: grid;
  width: 44px;
  height: 36px;
  place-items: center;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 800;
  transition:
    border-color 0.2s ease,
    color 0.2s ease;
}

.template-designer__back:hover {
  border-color: rgba(0, 113, 227, 0.28);
  color: var(--lh-accent);
}

.template-designer__title p,
.template-designer__title h1 {
  margin: 0;
}

.template-designer__title p {
  color: var(--lh-accent);
  font-size: 12px;
  font-weight: 800;
}

.template-designer__title h1 {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 20px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-designer__description {
  display: block;
  overflow: hidden;
  max-width: 680px;
  margin-top: 5px;
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-designer__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.template-designer__actions span {
  color: var(--lh-text-tertiary);
  font-size: 12px;
  font-weight: 800;
  margin-right: 4px;
}

.template-designer__draft-status {
  color: #5b3db2 !important;
}

.template-designer__actions button {
  min-height: 36px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.template-designer__actions button:hover {
  border-color: rgba(0, 113, 227, 0.24);
  box-shadow: 0 8px 18px rgba(20, 32, 54, 0.08);
  transform: translateY(-1px);
}

.template-designer__actions button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
  transform: none;
}

.template-designer__actions .template-designer__primary {
  border-color: transparent;
  background: var(--lh-accent);
  color: #ffffff;
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.2);
}

.template-designer__error {
  margin: 0;
  border: 1px solid rgba(255, 59, 48, 0.22);
  border-radius: 12px;
  background: rgba(255, 59, 48, 0.08);
  color: #9f1f18;
  font-size: 13px;
  font-weight: 700;
  padding: 10px 14px;
}

.template-designer__draft-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0;
  border: 1px solid rgba(142, 92, 247, 0.2);
  border-radius: var(--lh-radius-sm);
  background: var(--lh-ai-soft);
  color: #5b3db2;
  font-size: 13px;
  line-height: 1.5;
  padding: 10px 12px;
}

.template-designer__draft-notice button {
  flex: 0 0 auto;
  border: 0;
  background: transparent;
  color: #5b3db2;
  cursor: pointer;
  font-weight: 800;
  text-decoration: underline;
}

.template-designer__loading-backdrop {
  position: fixed;
  z-index: 48;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(20, 32, 54, 0.2);
  padding: 24px;
  backdrop-filter: blur(18px);
}

.template-designer__loading-dialog {
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
  color: var(--lh-text-primary);
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

.template-designer__workspace {
  display: grid;
  grid-template-columns: minmax(220px, 250px) minmax(0, 1fr) minmax(300px, 360px);
  gap: 16px;
  align-items: start;
  animation: template-designer-section-in 0.52s 0.12s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

@keyframes template-designer-page-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes template-designer-section-in {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.985);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
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

.template-designer__dialog-backdrop {
  position: fixed;
  z-index: 40;
  inset: 0;
  display: grid;
  place-items: start center;
  background: rgba(20, 32, 54, 0.24);
  padding: 24px;
  backdrop-filter: blur(2px);
}

.template-designer__dialog {
  display: grid;
  width: min(100%, 760px);
  max-height: min(760px, calc(100vh - 48px));
  gap: 18px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
  padding: 24px;
}

.template-designer__dialog-header {
  position: relative;
  display: grid;
  gap: 6px;
  padding-right: 44px;
}

.template-designer__dialog-header span {
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 800;
}

.template-designer__dialog-header strong {
  color: var(--lh-text-primary);
  font-size: 22px;
  line-height: 1.2;
}

.template-designer__dialog-close {
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

.template-designer__dialog-close:hover {
  border-color: rgba(0, 113, 227, 0.28);
  background: #ffffff;
  color: var(--lh-accent);
}

.template-designer__json {
  min-height: 360px;
  max-height: 520px;
  margin: 0;
  overflow: auto;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 14px;
  background: #fdf6f6;
  color: #000000;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 16px;
  line-height: 2;
  padding: 16px;
  white-space: pre-wrap;
  font-family: Cambria, Cochin, Georgia, Times, 'Times New Roman', serif;
  font-weight: 600;
}

.template-designer__dialog-actions {
  display: flex;
  justify-content: flex-end;
}

.template-designer__dialog-actions button {
  min-height: 40px;
  border: 0;
  border-radius: 999px;
  background: var(--lh-accent);
  color: #ffffff;
  cursor: pointer;
  font-weight: 750;
  padding: 0 18px;
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.2);
}

@media (max-width: 1080px) {
  .template-designer__workspace {
    grid-template-columns: 1fr;
  }

  .template-designer__topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .template-designer__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .template-designer__title {
    align-items: flex-start;
    flex-direction: column;
  }

  .template-designer__title h1 {
    white-space: normal;
  }

  .template-designer__description {
    white-space: normal;
  }
}
</style>
