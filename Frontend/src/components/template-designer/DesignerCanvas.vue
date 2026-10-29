<script setup lang="ts">
import { shallowRef } from 'vue'
import RichTextEditor from './RichTextEditor.vue'
import { JSON_EDITOR_BASE_VALUE, getJsonEditorValidationError } from './jsonEditor'
import type {
  CanvasComponent,
  CanvasComponentConfig,
  DesignerComponentType,
} from '../../types/template'

const props = defineProps<{
  items: CanvasComponent[]
  selectedItemId: string
}>()

const emit = defineEmits<{
  dropComponent: [type: DesignerComponentType]
  updateComponent: [id: string, configPatch: Partial<CanvasComponentConfig>]
  reorderComponents: [items: CanvasComponent[]]
  removeComponent: [id: string]
  viewDetails: [id: string]
}>()

const isDraggingOver = shallowRef(false)
const activeCanvasDragId = shallowRef('')
const canvasDropTargetId = shallowRef('')
const selectPreviewValues = shallowRef<Record<string, string | string[]>>({})
const componentTypes: DesignerComponentType[] = [
  'text-display',
  'input',
  'textarea',
  'radio',
  'checkbox',
  'select',
  'rich-text',
  'image-upload',
  'file-upload',
  'json-editor',
  'llmInteraction',
  'rating',
]
const ratingSteps = [1, 2, 3, 4, 5]

function isDesignerComponentType(value: string): value is DesignerComponentType {
  return componentTypes.includes(value as DesignerComponentType)
}

function hasTransferType(event: DragEvent, type: string) {
  return Array.from(event.dataTransfer?.types ?? []).includes(type)
}

function handleDragOver(event: DragEvent) {
  event.preventDefault()

  if (event.dataTransfer) {
    const isCanvasItem = hasTransferType(event, 'application/x-labelhub-canvas-item')
    event.dataTransfer.dropEffect = isCanvasItem ? 'move' : 'copy'

    if (!isCanvasItem) {
      isDraggingOver.value = true
    }
  }
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDraggingOver.value = false

  if (hasTransferType(event, 'application/x-labelhub-canvas-item')) {
    resetCanvasDragState()
    return
  }

  const componentType = event.dataTransfer?.getData('application/x-labelhub-component') ?? ''

  if (isDesignerComponentType(componentType)) {
    emit('dropComponent', componentType)
  }
}

function handleCanvasDragStart(event: DragEvent, id: string) {
  if (!event.dataTransfer) {
    return
  }

  const sourceItem = props.items.find((item) => item.id === id)

  if (sourceItem?.type === 'llmInteraction') {
    event.preventDefault()
    return
  }

  activeCanvasDragId.value = id
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/x-labelhub-canvas-item', id)
}

function handleCanvasItemDragOver(event: DragEvent, targetId: string) {
  if (!hasTransferType(event, 'application/x-labelhub-canvas-item')) {
    return
  }

  event.preventDefault()

  if (targetId !== activeCanvasDragId.value) {
    canvasDropTargetId.value = targetId
  }

  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function handleCanvasItemDrop(event: DragEvent, targetId: string) {
  if (!hasTransferType(event, 'application/x-labelhub-canvas-item')) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  const sourceId = event.dataTransfer?.getData('application/x-labelhub-canvas-item') ?? ''

  if (!sourceId || sourceId === targetId) {
    resetCanvasDragState()
    return
  }

  const sourceIndex = props.items.findIndex((item) => item.id === sourceId)
  const targetIndex = props.items.findIndex((item) => item.id === targetId)

  if (sourceIndex < 0 || targetIndex < 0) {
    resetCanvasDragState()
    return
  }

  const orderedItems = [...props.items]
  const [sourceItem] = orderedItems.splice(sourceIndex, 1)
  const targetRect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const targetIndexAfterSourceRemoval = sourceIndex < targetIndex ? targetIndex - 1 : targetIndex
  const shouldPlaceAfterTarget = event.clientY > targetRect.top + targetRect.height / 2
  const insertIndex = shouldPlaceAfterTarget
    ? targetIndexAfterSourceRemoval + 1
    : targetIndexAfterSourceRemoval

  orderedItems.splice(insertIndex, 0, sourceItem)
  emit('reorderComponents', orderedItems)
  resetCanvasDragState()
}

function resetCanvasDragState() {
  activeCanvasDragId.value = ''
  canvasDropTargetId.value = ''
}

function readInputValue(event: Event) {
  return (event.target as HTMLInputElement).value
}

function updateComponentConfig(id: string, configPatch: Partial<CanvasComponentConfig>) {
  emit('updateComponent', id, configPatch)
}

function getJsonEditorError(item: CanvasComponent) {
  return getJsonEditorValidationError(item.config.jsonAdditionalContent)
}

function hasJsonEditorAdditionalContent(item: CanvasComponent) {
  return Boolean(item.config.jsonAdditionalContent?.trim())
}

function isChoiceComponent(type: DesignerComponentType) {
  return type === 'radio' || type === 'checkbox' || type === 'select'
}

function isUploadComponent(type: DesignerComponentType) {
  return type === 'image-upload' || type === 'file-upload'
}

function isLlmTriggerComponent(type: DesignerComponentType) {
  return type === 'llmInteraction'
}

function getComponentPlaceholder(item: CanvasComponent) {
  return item.config.placeholder || '请输入内容'
}

function getUploadTitle(item: CanvasComponent) {
  return item.config.uploadFileName || (item.type === 'image-upload' ? '选择图片' : '选择文件')
}

function getUploadDescription(item: CanvasComponent) {
  if (item.config.uploadFileName) {
    return item.type === 'image-upload' ? '图片已选择' : '文件已选择'
  }

  return item.type === 'image-upload' ? '支持图片物料上传' : '支持文档物料上传'
}

function getUploadAccept(item: CanvasComponent) {
  if (item.config.accept) {
    return item.config.accept
  }

  if (item.type === 'image-upload') {
    return 'image/*'
  }

  return undefined
}

function handleUploadFileChange(item: CanvasComponent, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null

  if (item.config.uploadPreviewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(item.config.uploadPreviewUrl)
  }

  updateComponentConfig(item.id, {
    uploadFile: file,
    uploadFileName: file?.name ?? '',
    uploadPreviewUrl: item.type === 'image-upload' && file ? URL.createObjectURL(file) : '',
  })
}

function getSelectPreviewValue(item: CanvasComponent) {
  const value = selectPreviewValues.value[item.id]

  if (item.config.multiple) {
    return Array.isArray(value) ? value : []
  }

  return typeof value === 'string' ? value : ''
}

function handleSelectPreviewChange(item: CanvasComponent, value: unknown) {
  const nextValue = Array.isArray(value)
    ? value.filter((option): option is string => typeof option === 'string')
    : typeof value === 'string'
      ? value
      : item.config.multiple
        ? []
        : ''

  selectPreviewValues.value = {
    ...selectPreviewValues.value,
    [item.id]: nextValue,
  }
}
</script>

<template>
  <section class="designer-canvas" aria-label="画布区">
    <header class="designer-canvas__header">
      <div>
        <strong>画布区</strong>
        <span>拖拽放置</span>
      </div>
      <small>{{ items.length }} 个组件</small>
    </header>

    <div
      class="designer-canvas__dropzone"
      :class="{ 'designer-canvas__dropzone--active': isDraggingOver }"
      @dragover="handleDragOver"
      @dragleave="isDraggingOver = false"
      @drop="handleDrop"
    >
      <div v-if="items.length === 0" class="designer-canvas__empty">
        <strong>将左侧组件拖入画布</strong>
        <span>生成后的模板组件会按拖入顺序排列</span>
      </div>

      <article
        v-for="item in items"
        :key="item.id"
        class="designer-canvas__block"
        :class="{
          'designer-canvas__block--dragging': activeCanvasDragId === item.id,
          'designer-canvas__block--target': canvasDropTargetId === item.id,
          'designer-canvas__block--selected': selectedItemId === item.id,
          'designer-canvas__block--llm': isLlmTriggerComponent(item.type),
        }"
        @click="emit('viewDetails', item.id)"
        @dragover="handleCanvasItemDragOver($event, item.id)"
        @drop="handleCanvasItemDrop($event, item.id)"
      >
        <button
          v-if="!isLlmTriggerComponent(item.type)"
          class="designer-canvas__drag-handle"
          type="button"
          draggable="true"
          aria-label="拖动调整顺序"
          @dragstart.stop="handleCanvasDragStart($event, item.id)"
          @dragend="resetCanvasDragState"
        >
          <span aria-hidden="true"></span>
        </button>

        <div class="designer-canvas__tools">
          <button
            class="designer-canvas__remove"
            type="button"
            aria-label="删除组件"
            @click.stop="emit('removeComponent', item.id)"
          >
            ×
          </button>
        </div>

        <div class="designer-canvas__content">
          <textarea
            v-if="item.type === 'text-display'"
            class="designer-canvas__textarea"
            :value="item.config.source"
            placeholder="请输入需要展示的文本"
            @input="updateComponentConfig(item.id, { source: readInputValue($event) })"
          ></textarea>

          <input
            v-else-if="item.type === 'input'"
            class="designer-canvas__input"
            type="text"
            :maxlength="item.config.maxLength"
            :placeholder="getComponentPlaceholder(item)"
            :value="item.config.placeholder"
            @input="updateComponentConfig(item.id, { placeholder: readInputValue($event) })"
          >

          <textarea
            v-else-if="item.type === 'textarea'"
            class="designer-canvas__textarea"
            :maxlength="item.config.maxLength"
            :placeholder="item.config.placeholder || '请输入描述'"
            :value="item.config.placeholder"
            @input="updateComponentConfig(item.id, { placeholder: readInputValue($event) })"
          ></textarea>

          <RichTextEditor
            v-else-if="item.type === 'rich-text'"
            :item="item"
            @update-config="updateComponentConfig"
          />

          <div v-else-if="item.type === 'json-editor'" class="designer-canvas__json-editor">
            <div class="designer-canvas__json-shell">
              <pre class="designer-canvas__json-fixed">{
  "type": "{{ JSON_EDITOR_BASE_VALUE.type }}",
  "field": "{{ JSON_EDITOR_BASE_VALUE.field }}",
  "label": "{{ JSON_EDITOR_BASE_VALUE.label }}"{{ hasJsonEditorAdditionalContent(item) ? ',' : '' }}</pre>
            <textarea
              class="designer-canvas__json-textarea"
              :class="{ 'designer-canvas__json-textarea--error': getJsonEditorError(item) }"
              :maxlength="item.config.maxLength"
              placeholder='在此追加字段，例如："schema": {}'
              :value="item.config.jsonAdditionalContent"
              spellcheck="false"
              @input="updateComponentConfig(item.id, { jsonAdditionalContent: readInputValue($event) })"
            ></textarea>
              <pre class="designer-canvas__json-fixed">}</pre>
            </div>
            <p
              v-if="getJsonEditorError(item)"
              class="designer-canvas__json-error"
              role="alert"
            >
              {{ getJsonEditorError(item) }}
            </p>
          </div>

          <div v-else-if="isUploadComponent(item.type)" class="designer-canvas__upload">
            <label class="designer-canvas__upload-box">
              <input
                class="designer-canvas__upload-input"
                type="file"
                :accept="getUploadAccept(item)"
                @change="handleUploadFileChange(item, $event)"
              >
              <span class="designer-canvas__upload-icon">
                {{ item.type === 'image-upload' ? 'IMG' : 'FILE' }}
              </span>
              <span class="designer-canvas__upload-copy">
                <strong>{{ getUploadTitle(item) }}</strong>
                <small>{{ getUploadDescription(item) }}</small>
              </span>
              <img
                v-if="item.type === 'image-upload' && item.config.uploadPreviewUrl"
                class="designer-canvas__upload-preview"
                :src="item.config.uploadPreviewUrl"
                alt=""
              >
            </label>
          </div>

          <div v-else-if="isChoiceComponent(item.type)" class="designer-canvas__choice-preview">
            <p v-if="item.config.options.length === 0">暂无选项，点击查看详情添加。</p>

            <template v-else-if="item.type === 'radio'">
              <label v-for="option in item.config.options" :key="option">
                <input type="radio" :name="item.id">
                <span>{{ option }}</span>
              </label>
            </template>

            <template v-else-if="item.type === 'checkbox'">
              <label v-for="option in item.config.options" :key="option">
                <input type="checkbox">
                <span>{{ option }}</span>
              </label>
            </template>

            <el-select
              v-else
              class="designer-canvas__select-control"
              :collapse-tags="item.config.multiple"
              collapse-tags-tooltip
              clearable
              :model-value="getSelectPreviewValue(item)"
              :multiple="item.config.multiple"
              placeholder="请选择"
              @update:model-value="handleSelectPreviewChange(item, $event)"
            >
              <el-option
                v-for="option in item.config.options"
                :key="option"
                :label="option"
                :value="option"
              />
            </el-select>
          </div>

          <div v-else-if="isLlmTriggerComponent(item.type)" class="designer-canvas__llm-trigger">
            <strong>{{ item.config.label }}</strong>
            <span>AI预审触发组件</span>
          </div>

          <div v-else class="designer-canvas__rating" aria-label="评分预览">
            <button v-for="step in ratingSteps" :key="step" type="button" disabled>
              ★
            </button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.designer-canvas {
  display: grid;
  min-width: 0;
  min-height: calc(100vh - 180px);
  grid-template-rows: auto 1fr;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--lh-shadow-card);
  overflow: hidden;
  backdrop-filter: blur(22px);
}

.designer-canvas__header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--lh-border-soft);
  padding: 0 20px;
}

.designer-canvas__header div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.designer-canvas__header strong {
  color: var(--lh-accent);
  font-size: 18px;
  font-weight: 850;
}

.designer-canvas__header span,
.designer-canvas__header small {
  color: var(--lh-text-tertiary);
  font-size: 12px;
  font-weight: 750;
}

.designer-canvas__dropzone {
  display: grid;
  align-content: start;
  gap: 14px;
  min-height: 520px;
  margin: 18px;
  border: 1px dashed rgba(0, 113, 227, 0.28);
  border-radius: 16px;
  background:
    linear-gradient(rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.72)),
    repeating-linear-gradient(
      90deg,
      rgba(0, 113, 227, 0.06) 0,
      rgba(0, 113, 227, 0.06) 1px,
      transparent 1px,
      transparent 34px
    );
  padding: 18px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.designer-canvas__dropzone--active {
  border-color: rgba(0, 113, 227, 0.58);
  box-shadow: inset 0 0 0 1px rgba(0, 113, 227, 0.16);
  background:
    linear-gradient(rgba(245, 251, 255, 0.9), rgba(245, 251, 255, 0.9)),
    repeating-linear-gradient(
      90deg,
      rgba(0, 113, 227, 0.09) 0,
      rgba(0, 113, 227, 0.09) 1px,
      transparent 1px,
      transparent 34px
    );
}

.designer-canvas__empty {
  display: grid;
  min-height: 260px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border-radius: 14px;
  color: var(--lh-text-secondary);
  text-align: center;
}

.designer-canvas__empty strong {
  color: var(--lh-text-primary);
  font-size: 18px;
}

.designer-canvas__empty span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.designer-canvas__block {
  position: relative;
  display: grid;
  gap: 16px;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(249, 251, 255, 0.94)),
    #ffffff;
  padding: 20px 70px 20px 56px;
  box-shadow:
    0 16px 34px rgba(20, 32, 54, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    opacity 0.2s ease,
    transform 0.2s ease;
}

.designer-canvas__block:hover {
  border-color: rgba(0, 113, 227, 0.22);
  box-shadow:
    0 18px 40px rgba(20, 32, 54, 0.09),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
  transform: translateY(-1px);
}

.designer-canvas__block--selected {
  border-color: rgba(0, 113, 227, 0.58);
  box-shadow:
    0 18px 42px rgba(0, 113, 227, 0.12),
    inset 0 0 0 1px rgba(0, 113, 227, 0.28);
}

.designer-canvas__block--dragging {
  opacity: 0.52;
}

.designer-canvas__block--target {
  border-color: rgba(0, 113, 227, 0.42);
  box-shadow:
    0 12px 30px rgba(20, 32, 54, 0.06),
    inset 0 0 0 1px rgba(0, 113, 227, 0.14);
  transform: translateY(-1px);
}

.designer-canvas__block--llm {
  border-color: rgba(138, 114, 182, 0.38);
  background: #fbf8ff;
  padding-left: 24px;
}

.designer-canvas__block--llm:hover,
.designer-canvas__block--llm.designer-canvas__block--selected {
  border-color: rgba(138, 114, 182, 0.72);
  box-shadow:
    0 16px 34px rgba(138, 114, 182, 0.12),
    inset 0 0 0 1px rgba(138, 114, 182, 0.2);
}

.designer-canvas__drag-handle,
.designer-canvas__remove {
  position: absolute;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.designer-canvas__drag-handle {
  top: 18px;
  left: 18px;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 7px;
  background: rgba(23, 32, 51, 0.05);
}

.designer-canvas__drag-handle span {
  width: 13px;
  height: 13px;
  background-image: radial-gradient(rgba(23, 32, 51, 0.5) 1.4px, transparent 1.4px);
  background-position:
    0 0,
    6px 0,
    0 6px,
    6px 6px;
  background-size: 6px 6px;
}

.designer-canvas__drag-handle:hover {
  background: rgba(0, 113, 227, 0.1);
}

.designer-canvas__tools {
  position: absolute;
  top: 14px;
  right: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.designer-canvas__remove {
  position: static;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 999px;
  background: var(--lh-danger);
  color: #ffffff;
  font-size: 18px;
  font-weight: 850;
  line-height: 1;
  box-shadow: 0 8px 18px rgba(255, 59, 48, 0.24);
}

.designer-canvas__remove:hover {
  filter: brightness(1.06);
}

.designer-canvas__content {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.designer-canvas__input,
.designer-canvas__textarea {
  width: 100%;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 12px;
  outline: none;
  background: rgba(255, 255, 255, 0.92);
  color: var(--lh-text-primary);
  padding: 0 12px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 650;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.designer-canvas__input {
  min-height: 44px;
}

.designer-canvas__textarea {
  min-height: 116px;
  padding: 13px 14px;
  line-height: 1.7;
  resize: vertical;
}

.designer-canvas__input:focus,
.designer-canvas__textarea:focus {
  border-color: rgba(0, 113, 227, 0.42);
  background: #ffffff;
  box-shadow:
    0 0 0 4px rgba(0, 113, 227, 0.1),
    0 12px 24px rgba(20, 32, 54, 0.06);
}

.designer-canvas__json-editor {
  display: grid;
  gap: 8px;
}

.designer-canvas__json-shell {
  overflow: hidden;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9);
}

.designer-canvas__json-fixed {
  margin: 0;
  color: var(--lh-text-primary);
  font-family: Consolas, 'Courier New', monospace;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.6;
  padding: 12px 14px 0;
  white-space: pre-wrap;
}

.designer-canvas__json-fixed:last-child {
  padding: 0 14px 12px;
}

.designer-canvas__json-textarea {
  display: block;
  width: 100%;
  min-height: 180px;
  border: 0;
  outline: none;
  background: rgba(248, 250, 252, 0.84);
  color: var(--lh-text-primary);
  font-family: Consolas, 'Courier New', monospace;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.6;
  margin: 8px 0;
  padding: 12px 14px;
  resize: vertical;
}

.designer-canvas__json-textarea--error,
.designer-canvas__json-textarea--error:focus {
  background: rgba(254, 242, 242, 0.92);
  box-shadow: inset 3px 0 0 rgba(239, 68, 68, 0.72);
}

.designer-canvas__json-error {
  margin: 0;
  color: #ef4444;
  font-size: 12px;
  font-weight: 750;
  line-height: 1.5;
}

.designer-canvas__choice-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-height: 44px;
}

.designer-canvas__choice-preview p {
  margin: 0;
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.designer-canvas__choice-preview label {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--lh-text-primary);
  font-size: 14px;
  font-weight: 750;
  padding: 0 13px;
  box-shadow: 0 8px 18px rgba(20, 32, 54, 0.04);
}

.designer-canvas__choice-preview input[type='radio'],
.designer-canvas__choice-preview input[type='checkbox'] {
  width: 16px;
  height: 16px;
  accent-color: var(--lh-accent);
}

.designer-canvas__select-control {
  width: 100%;
}

.designer-canvas__select-control :deep(.el-select__wrapper) {
  min-height: 44px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow:
    0 0 0 1px rgba(23, 32, 51, 0.08) inset,
    0 1px 0 rgba(255, 255, 255, 0.9);
  transition:
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.designer-canvas__select-control.is-focused :deep(.el-select__wrapper),
.designer-canvas__select-control :deep(.el-select__wrapper.is-focused),
.designer-canvas__select-control :deep(.el-select__wrapper:hover) {
  background: #ffffff;
  box-shadow:
    0 0 0 1px rgba(0, 113, 227, 0.42) inset,
    0 0 0 4px rgba(0, 113, 227, 0.1),
    0 12px 24px rgba(20, 32, 54, 0.06);
}

.designer-canvas__select-control :deep(.el-select__placeholder),
.designer-canvas__select-control :deep(.el-select__selected-item) {
  font-size: 14px;
  font-weight: 650;
}

.designer-canvas__select-control :deep(.el-tag) {
  border: 0;
  border-radius: 999px;
  background: var(--lh-accent-soft);
  color: var(--lh-accent);
  font-weight: 750;
}

.designer-canvas__upload {
  display: grid;
}

.designer-canvas__upload-box {
  position: relative;
  display: grid;
  min-height: 104px;
  grid-template-columns: 56px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  border: 1px dashed rgba(0, 113, 227, 0.28);
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(245, 249, 255, 0.9)),
    #ffffff;
  cursor: pointer;
  padding: 16px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.designer-canvas__upload-box:hover {
  border-color: rgba(0, 113, 227, 0.42);
  box-shadow: 0 14px 30px rgba(20, 32, 54, 0.08);
  transform: translateY(-1px);
}

.designer-canvas__upload-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.designer-canvas__upload-icon {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 14px;
  background: rgba(0, 113, 227, 0.1);
  color: var(--lh-accent);
  font-size: 12px;
  font-weight: 850;
}

.designer-canvas__upload-copy {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.designer-canvas__upload-copy strong,
.designer-canvas__upload-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.designer-canvas__upload-copy strong {
  color: var(--lh-text-primary);
  font-size: 15px;
  font-weight: 850;
}

.designer-canvas__upload-copy small {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.designer-canvas__upload-preview {
  width: 92px;
  height: 72px;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 12px;
  object-fit: cover;
  box-shadow: 0 8px 18px rgba(20, 32, 54, 0.08);
}

.designer-canvas__llm-trigger {
  display: grid;
  gap: 6px;
  border: 1px dashed rgba(138, 114, 182, 0.36);
  border-radius: 12px;
  background: #eee5fa;
  color: #8a72b6;
  padding: 14px 16px;
}

.designer-canvas__llm-trigger strong {
  color: #8a72b6;
  font-size: 14px;
  font-weight: 900;
}

.designer-canvas__llm-trigger span {
  color: rgba(90, 72, 130, 0.82);
  font-size: 12px;
  font-weight: 750;
}

.designer-canvas__rating {
  display: flex;
  gap: 7px;
}

.designer-canvas__rating button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgba(255, 159, 10, 0.2);
  border-radius: 8px;
  background: rgba(255, 159, 10, 0.12);
  color: var(--lh-warning);
  font-size: 17px;
}

@media (max-width: 920px) {
  .designer-canvas__block {
    padding-right: 18px;
    padding-top: 56px;
  }

  .designer-canvas__upload-box {
    grid-template-columns: 48px minmax(0, 1fr);
  }

  .designer-canvas__upload-preview {
    grid-column: 1 / -1;
    width: 100%;
    height: 160px;
  }
}
</style>
