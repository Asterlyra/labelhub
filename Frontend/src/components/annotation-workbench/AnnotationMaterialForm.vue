<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import type { AnnotationFormData, WorkbenchItemDetail, WorkbenchMaterial, WorkbenchTask } from '../../types/workbench'

const props = defineProps<{
  task: WorkbenchTask | null
  materials: WorkbenchMaterial[]
  itemDetail: WorkbenchItemDetail | null
  modelValue: AnnotationFormData
  questionIndex?: number
  readonly?: boolean
  returnTo?: string
  rawDataCollapsible?: boolean
  rawDataDefaultExpanded?: boolean
  hideTaskEyebrow?: boolean
  swapFieldLabel?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: AnnotationFormData]
  'update:files': [field: string, files: File[]]
}>()

const isRawDataCollapsed = shallowRef(false)
const uploadPreviewUrls = shallowRef<Record<string, string[]>>({})
const uploadFileNames = shallowRef<Record<string, string[]>>({})

const sortedMaterials = computed(() =>
  [...props.materials]
    .filter((material) => !isLlmTriggerMaterial(material))
    .sort((previous, next) => readNumber(previous.sortOrder, 0) - readNumber(next.sortOrder, 0)),
)

const taskMetaItems = computed(() => {
  const task = props.task

  if (!task) {
    return []
  }

  return [
    { label: '任务 ID', value: formatValue(task.id) },
    { label: '截止时间', value: formatDate(task.deadline) },
    { label: '模板 ID', value: formatValue(task.templateId) },
    { label: '数据集 ID', value: formatValue(task.datasetId) },
  ].filter((item) => item.value)
})

void taskMetaItems

const rawDataEntries = computed(() => {
  const rawData = props.itemDetail?.rawData

  if (!rawData || typeof rawData !== 'object') {
    return []
  }

  return Object.entries(rawData).map(([key, value]) => ({
    key,
    value: formatValue(value, '-'),
  }))
})

const shouldShowRawDataTable = computed(() => !props.rawDataCollapsible || !isRawDataCollapsed.value)

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

function formatValue(value: unknown, fallback = '') {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}

function formatDate(value: unknown) {
  const text = readString(value)

  return text ? text.replace('T', ' ').split(' ')[0] : ''
}

function getConfigValue(material: WorkbenchMaterial, key: string) {
  return material.config?.[key] ?? material[key]
}

function getMaterialType(material: WorkbenchMaterial) {
  const type = readString(material.type, 'input')
  const normalizedType = type.replace(/[\s_-]/g, '').toLowerCase()
  const typeMap: Record<string, string> = {
    showitem: 'text-display',
    textdisplay: 'text-display',
    tagselect: 'select',
    richtext: 'rich-text',
    imageupload: 'image-upload',
    fileupload: 'file-upload',
    jsoneditor: 'json-editor',
    llmtrigger: 'llmInteraction',
    llminteraction: 'llmInteraction',
  }

  return typeMap[normalizedType] ?? type
}

function isLlmTriggerMaterial(material: WorkbenchMaterial) {
  const type = getMaterialType(material)

  return type === 'llmInteraction' || type === 'llm-trigger'
}

function getMaterialField(material: WorkbenchMaterial) {
  return readString(material.field ?? material.id, '')
}

function getMaterialLabel(material: WorkbenchMaterial) {
  return readString(material.label, getMaterialField(material) || '未命名字段')
}

function getMaterialTitle(material: WorkbenchMaterial) {
  return props.swapFieldLabel ? getMaterialField(material) || getMaterialLabel(material) : getMaterialLabel(material)
}

function getMaterialMeta(material: WorkbenchMaterial) {
  return props.swapFieldLabel ? getMaterialLabel(material) : getMaterialField(material)
}

function getMaterialPlaceholder(material: WorkbenchMaterial) {
  return readString(getConfigValue(material, 'placeholder'), '请输入内容')
}

function getMaterialMaxLength(material: WorkbenchMaterial) {
  const maxLength = readNumber(getConfigValue(material, 'maxLength'), 0)

  return maxLength > 0 ? maxLength : undefined
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

function isRequired(material: WorkbenchMaterial) {
  return readBoolean(getConfigValue(material, 'required'), false)
}

function isMultiple(material: WorkbenchMaterial) {
  return readBoolean(getConfigValue(material, 'multiple'), getMaterialType(material) === 'checkbox')
}

function getFieldValue(material: WorkbenchMaterial) {
  const field = getMaterialField(material)

  return field ? props.modelValue[field] : undefined
}

function getStringFieldValue(material: WorkbenchMaterial) {
  const value = getFieldValue(material)

  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}

function getJsonPlaceholder(material: WorkbenchMaterial) {
  return readString(getConfigValue(material, 'placeholder'), '{\n  "key": "value"\n}')
}

function readTextAreaValue(event: Event) {
  return event.target instanceof HTMLTextAreaElement ? event.target.value : ''
}

function getArrayFieldValue(material: WorkbenchMaterial) {
  const value = getFieldValue(material)

  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

function getNumberFieldValue(material: WorkbenchMaterial) {
  const value = Number(getFieldValue(material))

  return Number.isFinite(value) ? value : 0
}

function getFileDisplayNames(material: WorkbenchMaterial) {
  const field = getMaterialField(material)
  const localFileNames = field ? uploadFileNames.value[field] ?? [] : []

  if (localFileNames.length) {
    return localFileNames
  }

  const value = getFieldValue(material)

  if (Array.isArray(value)) {
    return value.map((item) => getFileNameFromValue(item)).filter(Boolean)
  }

  const fileName = getFileNameFromValue(value)

  return fileName ? [fileName] : []
}

function getFileNameFromValue(value: unknown) {
  const text = readString(value)

  if (!text) {
    return ''
  }

  try {
    const url = new URL(text, window.location.origin)
    const pathname = decodeURIComponent(url.pathname)
    const fileName = pathname.split('/').filter(Boolean).pop()

    return fileName || text
  } catch {
    return text.split(/[\\/]/).filter(Boolean).pop() ?? text
  }
}

function getImagePreviewUrls(material: WorkbenchMaterial) {
  const field = getMaterialField(material)
  const localUrls = field ? uploadPreviewUrls.value[field] ?? [] : []

  if (localUrls.length) {
    return localUrls
  }

  const value = getFieldValue(material)

  if (Array.isArray(value)) {
    return value.map((item) => readString(item)).filter(Boolean)
  }

  const url = readString(value)

  return url ? [url] : []
}

function getDisplayValue(material: WorkbenchMaterial) {
  const field = getMaterialField(material)
  const rawData: Record<string, unknown> = props.itemDetail?.rawData ?? {}
  const rawValue = field ? rawData[field] : undefined
  const value =
    rawValue ??
    getConfigValue(material, 'source') ??
    getConfigValue(material, 'content') ??
    getConfigValue(material, 'placeholder')

  return formatValue(value, '暂无原始内容')
}

function updateField(material: WorkbenchMaterial, value: unknown) {
  const field = getMaterialField(material)

  if (!field || props.readonly) {
    return
  }

  emit('update:modelValue', {
    ...props.modelValue,
    [field]: value,
  })
}

function handleFileChange(material: WorkbenchMaterial, event: Event) {
  const input = event.target as HTMLInputElement
  const field = getMaterialField(material)
  const files = Array.from(input.files ?? [])

  if (field) {
    emit('update:files', field, files)
    updateUploadPreviewUrls(field, files)
  }
}

function revokePreviewUrls(urls: string[]) {
  urls.forEach((url) => {
    URL.revokeObjectURL(url)
  })
}

function updateUploadPreviewUrls(field: string, files: File[]) {
  const nextPreviewUrls = { ...uploadPreviewUrls.value }
  const nextFileNames = { ...uploadFileNames.value }
  const previousUrls = nextPreviewUrls[field] ?? []

  revokePreviewUrls(previousUrls)

  if (files.length) {
    nextPreviewUrls[field] = files.map((file) => URL.createObjectURL(file))
    nextFileNames[field] = files.map((file) => file.name)
  } else {
    delete nextPreviewUrls[field]
    delete nextFileNames[field]
  }

  uploadPreviewUrls.value = nextPreviewUrls
  uploadFileNames.value = nextFileNames
}

function toggleRawDataCollapsed() {
  if (!props.rawDataCollapsible) {
    return
  }

  isRawDataCollapsed.value = !isRawDataCollapsed.value
}

function getInitialRawDataCollapsed() {
  // return Boolean(props.rawDataCollapsible && !props.rawDataDefaultExpanded)
  return false
}

watch(
  [() => props.itemDetail?.taskItemId, () => props.rawDataCollapsible, () => props.rawDataDefaultExpanded],
  () => {
    isRawDataCollapsed.value = getInitialRawDataCollapsed()
  },
  { immediate: true },
)

watch(
  () => props.itemDetail?.taskItemId,
  () => {
    Object.values(uploadPreviewUrls.value).forEach(revokePreviewUrls)
    uploadPreviewUrls.value = {}
    uploadFileNames.value = {}
  },
)

onBeforeUnmount(() => {
  Object.values(uploadPreviewUrls.value).forEach(revokePreviewUrls)
  uploadFileNames.value = {}
})
</script>

<template>
  <section class="annotation-form" aria-label="做题区域">
    <!-- <header class="annotation-form__header"> -->
      <!-- <div class="annotation-form__header-top">
        <div>
          <p v-if="!hideTaskEyebrow" class="annotation-form__eyebrow">标注任务</p>
          <div class="annotation-form__title-row" :class="{ 'annotation-form__title-row--flush': hideTaskEyebrow }">
            <RouterLink v-if="returnTo" class="annotation-form__back-link" :to="returnTo">
              返回任务列表
            </RouterLink>
          </div>
        </div>


      </div> -->

      <!-- <div class="annotation-form__meta">
        <span v-for="item in taskMetaItems" :key="item.label">
          {{ item.label }}：{{ item.value }}
        </span>
          <div class="annotation-form__question-badge">
          第 {{ questionIndex || 0 }} 题
        </div>
      </div> -->
    <!-- </header> -->

    <section v-if="rawDataEntries.length" class="annotation-form__raw-data" aria-label="原始数据">
      <header class="annotation-form__raw-data-header">
        <strong>原始数据</strong>
        <div class="annotation-form__raw-data-actions">
          <span>当前题目</span>
          <button
            v-if="rawDataCollapsible"
            class="annotation-form__raw-toggle"
            type="button"
            :aria-expanded="!isRawDataCollapsed"
            @click="toggleRawDataCollapsed"
          >
            {{ isRawDataCollapsed ? '展开内容' : '收起内容' }}
          </button>
        </div>
      </header>

      <div
        class="annotation-form__raw-table-fold"
        :class="{ 'annotation-form__raw-table-fold--collapsed': !shouldShowRawDataTable }"
        :aria-hidden="!shouldShowRawDataTable"
      >
        <div class="annotation-form__raw-table-fold-content">
          <div class="annotation-form__raw-table" role="table" aria-label="原始数据表">
            <div class="annotation-form__raw-table-head" role="row">
              <span role="columnheader">字段</span>
              <span role="columnheader">内容</span>
            </div>

            <article
              v-for="entry in rawDataEntries"
              :key="entry.key"
              class="annotation-form__raw-table-row"
              role="row"
            >
              <span role="cell">{{ entry.key }}</span>
              <strong role="cell">{{ entry.value }}</strong>
            </article>
          </div>
        </div>
      </div>
    </section>

    <div v-if="sortedMaterials.length" class="annotation-form__fields">
      <article
        v-for="material in sortedMaterials"
        :key="`${getMaterialField(material)}-${material.id ?? getMaterialLabel(material)}`"
        class="annotation-form__field"
      >
        <header class="annotation-form__field-header">
          <strong>
            {{ getMaterialTitle(material) }}
            <em v-if="isRequired(material)">*</em>
          </strong>
          <span>{{ getMaterialMeta(material) }}</span>
        </header>

        <div v-if="getMaterialType(material) === 'text-display'" class="annotation-form__source">
          {{ getDisplayValue(material) }}
        </div>

        <el-input
          v-else-if="getMaterialType(material) === 'input'"
          :disabled="readonly"
          :maxlength="getMaterialMaxLength(material)"
          :model-value="getStringFieldValue(material)"
          :placeholder="getMaterialPlaceholder(material)"
          show-word-limit
          @update:model-value="updateField(material, $event)"
        />

        <el-input
          v-else-if="getMaterialType(material) === 'textarea' || getMaterialType(material) === 'rich-text'"
          :disabled="readonly"
          :maxlength="getMaterialMaxLength(material)"
          :model-value="getStringFieldValue(material)"
          :placeholder="getMaterialPlaceholder(material)"
          :rows="5"
          show-word-limit
          type="textarea"
          @update:model-value="updateField(material, $event)"
        />

        <div v-else-if="getMaterialType(material) === 'json-editor'" class="annotation-form__json-editor">
          <textarea
            class="annotation-form__json-textarea"
            :disabled="readonly"
            :maxlength="getMaterialMaxLength(material)"
            :placeholder="getJsonPlaceholder(material)"
            
            spellcheck="false"
            @input="updateField(material, readTextAreaValue($event))"
          ></textarea>
        </div>

        <el-radio-group
          v-else-if="getMaterialType(material) === 'radio'"
          class="annotation-form__choice"
          :disabled="readonly"
          :model-value="getStringFieldValue(material)"
          @update:model-value="updateField(material, $event)"
        >
          <el-radio-button
            v-for="option in getMaterialOptions(material)"
            :key="option"
            :label="option"
          >
            {{ option }}
          </el-radio-button>
        </el-radio-group>

        <el-checkbox-group
          v-else-if="getMaterialType(material) === 'checkbox'"
          class="annotation-form__choice"
          :disabled="readonly"
          :model-value="getArrayFieldValue(material)"
          @update:model-value="updateField(material, $event)"
        >
          <el-checkbox-button
            v-for="option in getMaterialOptions(material)"
            :key="option"
            :label="option"
          >
            {{ option }}
          </el-checkbox-button>
        </el-checkbox-group>

        <el-select
          v-else-if="getMaterialType(material) === 'select'"
          :collapse-tags="isMultiple(material)"
          collapse-tags-tooltip
          :disabled="readonly"
          :model-value="isMultiple(material) ? getArrayFieldValue(material) : getStringFieldValue(material)"
          :multiple="isMultiple(material)"
          :placeholder="getMaterialPlaceholder(material)"
          @update:model-value="updateField(material, $event)"
        >
          <el-option
            v-for="option in getMaterialOptions(material)"
            :key="option"
            :label="option"
            :value="option"
          />
        </el-select>

        <div
          v-else-if="getMaterialType(material) === 'image-upload' || getMaterialType(material) === 'file-upload'"
          class="annotation-form__upload"
          :class="{ 'annotation-form__upload--image': getMaterialType(material) === 'image-upload' }"
        >
          <label>
            <input
              :accept="readString(getConfigValue(material, 'accept'))"
              :disabled="readonly"
              :multiple="isMultiple(material)"
              type="file"
              @change="handleFileChange(material, $event)"
            >
            <span>{{ getMaterialType(material) === 'image-upload' ? '选择图片' : '选择文件' }}</span>
          </label>
          <div
            v-if="getMaterialType(material) === 'image-upload' && getImagePreviewUrls(material).length"
            class="annotation-form__image-preview-list"
          >
            <figure
              v-for="url in getImagePreviewUrls(material)"
              :key="url"
              class="annotation-form__image-preview"
            >
              <el-image
                :preview-src-list="getImagePreviewUrls(material)"
                :src="url"
                fit="cover"
                hide-on-click-modal
                preview-teleported
              />
            </figure>
          </div>
          <ul v-if="getFileDisplayNames(material).length" class="annotation-form__file-list">
            <li v-for="fileName in getFileDisplayNames(material)" :key="fileName">
              {{ fileName }}
            </li>
          </ul>
          <small v-else>尚未选择文件</small>
        </div>

        <el-rate
          v-else-if="getMaterialType(material) === 'rating'"
          :disabled="readonly"
          :model-value="getNumberFieldValue(material)"
          @update:model-value="updateField(material, $event)"
        />

        <el-input
          v-else
          :disabled="readonly"
          :model-value="getStringFieldValue(material)"
          :placeholder="getMaterialPlaceholder(material)"
          @update:model-value="updateField(material, $event)"
        />
      </article>
    </div>

    <div v-else class="annotation-form__empty">
      暂无模板字段
    </div>

    <div v-if="$slots.default" class="annotation-form__extension">
      <slot></slot>
    </div>
  </section>
</template>

<style scoped>
.annotation-form {
  display: grid;
  min-width: 0;
  height: fit-content;
  align-content: start;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
}

.annotation-form__header {
  display: grid;
  gap: 14px;
  border-bottom: 1px solid #edf1f5;
  padding: 18px;
}

.annotation-form__header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.annotation-form__header-top > div {
  min-width: 0;
}

.annotation-form__title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.annotation-form__title-row--flush {
  margin-top: 0;
}

.annotation-form__back-link {
  display: inline-flex;
  min-height: 30px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  color: #2563eb;
  background: #eff6ff;
  font-size: 12px;
  font-weight: 850;
  padding: 0 10px;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background-color 0.2s ease,
    transform 0.2s ease;
}

.annotation-form__back-link:hover {
  border-color: #2563eb;
  color: #ffffff;
  background: #2563eb;
  transform: translateY(-1px);
}

.annotation-form__eyebrow,
.annotation-form__header h1,
.annotation-form__header span {
  margin: 0;
}

.annotation-form__eyebrow {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.annotation-form__header h1 {
  min-width: 0;
  color: #111827;
  font-size: 18px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.annotation-form__header span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.annotation-form__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.annotation-form__meta span {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 750;
  padding: 0 10px;
}

.annotation-form__question-badge {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe7f5;
  border-radius: 999px;
  background: #f8fbff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 850;
  padding: 0 14px;
  white-space: nowrap;
}

.annotation-form__raw-data {
  display: grid;
  gap: 8px;
  padding: 16px 18px 0;
}

.annotation-form__raw-data-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.annotation-form__raw-data-header strong {
  color: #111827;
  font-size: 14px;
  font-weight: 850;
}

.annotation-form__raw-data-header span {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.annotation-form__raw-data-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.annotation-form__raw-toggle {
  min-height: 28px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  cursor: pointer;
  font-size: 12px;
  font-weight: 850;
  padding: 0 10px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.annotation-form__raw-toggle:hover {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.annotation-form__raw-table {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.annotation-form__raw-table-fold {
  display: grid;
  grid-template-rows: 1fr;
  opacity: 1;
  transform: translateY(0);
  transition:
    grid-template-rows 0.32s cubic-bezier(0.2, 0.82, 0.2, 1),
    opacity 0.24s ease,
    transform 0.32s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.annotation-form__raw-table-fold--collapsed {
  grid-template-rows: 0fr;
  opacity: 0;
  transform: translateY(-6px);
}

.annotation-form__raw-table-fold-content {
  min-height: 0;
  overflow: hidden;
}

.annotation-form__raw-table-head,
.annotation-form__raw-table-row {
  display: grid;
  grid-template-columns: minmax(132px, 220px) minmax(0, 1fr);
  gap: 0;
}

.annotation-form__raw-table-head {
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.annotation-form__raw-table-head span {
  padding: 10px 14px;
  color: #475569;
  font-size: 12px;
  font-weight: 850;
}

.annotation-form__raw-table-row + .annotation-form__raw-table-row {
  border-top: 1px solid #eef2f7;
}

.annotation-form__raw-table-row span,
.annotation-form__raw-table-row strong {
  min-width: 0;
  padding: 10px 14px;
}

.annotation-form__raw-table-row span {
  border-right: 1px solid #eef2f7;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  background: #fbfcfe;
}

.annotation-form__raw-table-row strong {
  display: block;
  overflow-wrap: anywhere;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.55;
  background: #ffffff;
}

.annotation-form__fields {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 8px 18px 18px;
}

.annotation-form__extension {
  padding: 0 18px 18px;
}

.annotation-form__field {
  display: grid;
  gap: 10px;
}

.annotation-form__field-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.annotation-form__field-header strong {
  color: #111827;
  font-size: 14px;
  font-weight: 850;
}

.annotation-form__field-header em {
  color: #ef4444;
  font-style: normal;
}

.annotation-form__field-header span {
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.annotation-form__source {
  min-height: 72px;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #f8fafc;
  color: #111827;
  font-size: 14px;
  line-height: 1.7;
  padding: 14px;
}

.annotation-form__json-editor {
  overflow: hidden;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9);
}

.annotation-form__json-textarea {
  display: block;
  width: 100%;
  min-height: 220px;
  border: 0;
  outline: none;
  background: rgba(248, 250, 252, 0.84);
  color: #111827;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.6;
  padding: 14px;
  resize: vertical;
}

.annotation-form__json-textarea:focus {
  box-shadow: inset 3px 0 0 rgba(37, 99, 235, 0.68);
}

.annotation-form__json-textarea:disabled {
  cursor: not-allowed;
  background: rgba(241, 245, 249, 0.92);
  color: #64748b;
}

.annotation-form__json-textarea::placeholder {
  color: #94a3b8;
}

.annotation-form__choice {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.annotation-form__choice :deep(.el-radio-button__inner),
.annotation-form__choice :deep(.el-checkbox-button__inner) {
  border-radius: 999px !important;
  font-weight: 750;
}

.annotation-form__upload {
  display: grid;
  gap: 8px;
}

.annotation-form__upload label {
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

.annotation-form__upload input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.annotation-form__upload small {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.annotation-form__image-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.annotation-form__image-preview {
  display: grid;
  width: 148px;
  height: 104px;
  place-items: center;
  overflow: hidden;
  margin: 0;
  border: 1px solid #dbe3ec;
  border-radius: 8px;
  background: #f8fafc;
}

.annotation-form__image-preview :deep(.el-image) {
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}

.annotation-form__image-preview :deep(.el-image__inner) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.annotation-form__file-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.annotation-form__file-list li {
  width: fit-content;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid #dbe3ec;
  border-radius: 999px;
  background: #f8fafc;
  color: #334155;
  font-size: 12px;
  font-weight: 750;
  padding: 5px 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.annotation-form__empty {
  display: grid;
  min-height: 280px;
  place-items: center;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 750;
}

.annotation-form :deep(.el-input__wrapper),
.annotation-form :deep(.el-textarea__inner),
.annotation-form :deep(.el-select__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.annotation-form :deep(.el-input__wrapper.is-focus),
.annotation-form :deep(.el-textarea__inner:focus),
.annotation-form :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px #2563eb inset;
}

@media (max-width: 900px) {
  .annotation-form__header-top,
  .annotation-form__title-row,
  .annotation-form__raw-data-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .annotation-form__raw-data-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .annotation-form__header h1 {
    white-space: normal;
  }

  .annotation-form__raw-table-head,
  .annotation-form__raw-table-row {
    grid-template-columns: 1fr;
  }

  .annotation-form__raw-table-row span {
    border-right: 0;
    border-bottom: 1px solid #eef2f7;
  }
}
</style>
