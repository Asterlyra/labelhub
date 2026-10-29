<script setup lang="ts">
import { shallowRef } from 'vue'
import designerDetailImage from '../../icon/designer_detail.gif'
import type { CanvasComponent, CanvasComponentConfig, DesignerComponentType } from '../../types/template'
import { JSON_EDITOR_BASE_VALUE, getJsonEditorValidationError } from './jsonEditor'

type DetailTab = 'base' | 'validation' | 'linkage'

const props = defineProps<{
  item: CanvasComponent | null
  hasCanvasItems: boolean
}>()

const emit = defineEmits<{
  updateComponent: [id: string, configPatch: Partial<CanvasComponentConfig>]
}>()

const activeDetailTab = shallowRef<DetailTab>('base')
const detailTabs: { key: DetailTab; label: string }[] = [
  { key: 'base', label: '基础' },
  { key: 'validation', label: '校验' },
  { key: 'linkage', label: '联动' },
]

function readInputValue(event: Event) {
  return (event.target as HTMLInputElement).value
}

function updateConfig(configPatch: Partial<CanvasComponentConfig>) {
  if (!props.item) {
    return
  }

  emit('updateComponent', props.item.id, configPatch)
}

function addOption() {
  if (!props.item) {
    return
  }

  updateConfig({
    options: [...props.item.config.options, `选项${props.item.config.options.length + 1}`],
  })
}

function updateOption(optionIndex: number, value: string) {
  if (!props.item) {
    return
  }

  const nextOptions = [...props.item.config.options]
  nextOptions[optionIndex] = value
  updateConfig({ options: nextOptions })
}

function removeOption(optionIndex: number) {
  if (!props.item) {
    return
  }

  updateConfig({
    options: props.item.config.options.filter((_, index) => index !== optionIndex),
  })
}

function isChoiceComponent(type: DesignerComponentType) {
  return type === 'radio' || type === 'checkbox' || type === 'select'
}

function isUploadComponent(type: DesignerComponentType) {
  return type === 'image-upload' || type === 'file-upload'
}

function hasInputConfig(type: DesignerComponentType) {
  return type === 'input' || type === 'textarea' || type === 'rich-text'
}

function hasMaxLengthConfig(type: DesignerComponentType) {
  return hasInputConfig(type) || type === 'json-editor'
}

function hasRequiredConfig(item: CanvasComponent) {
  return item.type === 'input' || item.type === 'textarea' || item.type === 'radio' || item.type === 'json-editor' || isUploadComponent(item.type)
}

function readValue(value: unknown, fallback = '未设置') {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? String(value) : fallback
  }

  if (typeof value === 'string' && value.trim()) {
    return value
  }

  return fallback
}
</script>

<template>
  <aside
    class="component-details"
    aria-label="组件查看详情"
  >
    <Transition name="component-details-content" mode="out-in">
      <div
        :key="item ? `component-details-${item.id}` : 'component-details-empty'"
        class="component-details__content"
      >
        <header class="component-details__header">
          <strong>属性配置 · {{ item?.config.field || '请输入字段名' }}</strong>
        </header>

        <div v-if="item" class="component-details__body">
          <nav class="component-details__tabs" aria-label="属性配置分类">
            <button
              v-for="tab in detailTabs"
              :key="tab.key"
              type="button"
              :class="{ 'component-details__tab--active': activeDetailTab === tab.key }"
              @click="activeDetailTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </nav>

          <section v-show="activeDetailTab === 'base'" class="component-details__section">
            <div class="component-details__row component-details__row--editable">
              <span>字段名</span>
              <label class="component-details__edit-shell">
                <input
                  class="component-details__row-input component-details__row-input--editable"
                  :value="item.config.field"
                  type="text"
                  aria-label="字段名"
                  placeholder="请输入字段名"
                  maxlength="9"
                  :readonly="item.type === 'json-editor'"
                  @input="updateConfig({ field: readInputValue($event)})"
                >
              </label>
            </div>

            <div class="component-details__row">
              <span>标签</span>
              <strong>{{ item.config.label }}</strong>
            </div>

            <div v-if="hasRequiredConfig(item)" class="component-details__row">
              <span>必填</span>
              <el-switch
                :model-value="Boolean(item.config.required)"
                @change="updateConfig({ required: Boolean($event) })"
              />
            </div>

            <div v-if="item.type === 'select'" class="component-details__row">
              <span>允许多选</span>
              <el-switch
                :model-value="Boolean(item.config.multiple)"
                @change="updateConfig({ multiple: Boolean($event) })"
              />
            </div>

            <label v-if="hasInputConfig(item.type)" class="component-details__field">
              <span>输入内容</span>
              <input
                :value="item.config.placeholder"
                type="text"
                placeholder="请输入内容"
                @input="updateConfig({ placeholder: readInputValue($event) })"
              >
            </label>

            <label v-if="item.type === 'json-editor'" class="component-details__field">
              <span>JSON 内容（固定字段不可删除）</span>
              <pre class="component-details__json-fixed">{
  "type": "{{ JSON_EDITOR_BASE_VALUE.type }}",
  "field": "{{ JSON_EDITOR_BASE_VALUE.field }}",
  "label": "{{ JSON_EDITOR_BASE_VALUE.label }}"{{ item.config.jsonAdditionalContent?.trim() ? ',' : '' }}</pre>
              <textarea
                class="component-details__json-textarea"
                :class="{ 'component-details__json-textarea--error': getJsonEditorValidationError(item.config.jsonAdditionalContent) }"
                :maxlength="item.config.maxLength"
                placeholder='在此追加字段，例如："schema": {}'
                :value="item.config.jsonAdditionalContent"
                spellcheck="false"
                @input="updateConfig({ jsonAdditionalContent: readInputValue($event) })"
              ></textarea>
              <pre class="component-details__json-fixed component-details__json-fixed--closing">}</pre>
              <small
                v-if="getJsonEditorValidationError(item.config.jsonAdditionalContent)"
                class="component-details__json-error"
                role="alert"
              >
                {{ getJsonEditorValidationError(item.config.jsonAdditionalContent) }}
              </small>
            </label>

            <div v-if="hasMaxLengthConfig(item.type)" class="component-details__row">
              <span>最大长度</span>
              <strong>{{ readValue(item.config.maxLength) }}</strong>
            </div>

            <template v-if="isUploadComponent(item.type)">
              <div class="component-details__row">
                <span>accept</span>
                <strong>{{ readValue(item.config.accept) }}</strong>
              </div>

              <div class="component-details__row">
                <span>maxSize</span>
                <strong>{{ readValue(item.config.maxSize) }}</strong>
              </div>

              <div class="component-details__row">
                <span>maxCount</span>
                <strong>{{ readValue(item.config.maxCount) }}</strong>
              </div>
            </template>

            <div v-if="isChoiceComponent(item.type)" class="component-details__option-editor">
              <header>
                <span>选项配置</span>
                <button type="button" @click="addOption">+ 添加选项</button>
              </header>

              <p v-if="item.config.options.length === 0">暂无选项。</p>

              <div
                v-for="(option, optionIndex) in item.config.options"
                :key="`${item.id}-${optionIndex}`"
                class="component-details__option-row"
              >
                <input
                  :value="option"
                  type="text"
                  placeholder="请输入选项"
                  @input="updateOption(optionIndex, readInputValue($event))"
                >
                <button type="button" @click="removeOption(optionIndex)">删除</button>
              </div>
            </div>
          </section>

          <section v-show="activeDetailTab === 'validation'" class="component-details__section">
            <h3>校验规则</h3>

            <div class="component-details__row">
              <span>正则</span>
              <strong>暂无</strong>
            </div>

            <div class="component-details__row">
              <span>自定义函数</span>
              <strong>暂无</strong>
            </div>
          </section>

          <section v-show="activeDetailTab === 'linkage'" class="component-details__section">
            <h3>字段联动</h3>

            <div class="component-details__link-card">
              <span>暂无联动规则</span>
              <button type="button">+ 新增联动规则</button>
            </div>
          </section>
        </div>

        <div v-else class="component-details__empty">
          <img :src="designerDetailImage" alt="">
          <strong>{{ hasCanvasItems ? '请选择一个组件' : '请将左侧组件 拖入画布区域 ~' }}</strong>
          <span v-if="hasCanvasItems">点击画布中的组件后，这里会显示对应配置。</span>
        </div>
      </div>
    </Transition>
  </aside>
</template>

<style scoped>
.component-details {
  position: sticky;
  top: 96px;
  display: grid;
  align-self: start;
  width: 100%;
  min-height: calc(100vh - 180px);
  max-height: calc(100dvh - 116px);
  grid-template-rows: 1fr;
  overflow: hidden;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 48px rgba(20, 32, 54, 0.12);
  backdrop-filter: blur(18px);
  animation: component-details-fixed-in 0.44s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.component-details__content {
  display: grid;
  min-height: 0;
  grid-template-rows: auto 1fr;
  overflow: hidden;
}

.component-details-content-enter-active {
  transition:
    opacity 0.36s ease,
    filter 0.4s ease,
    transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.component-details-content-leave-active {
  transition:
    opacity 0.18s ease,
    filter 0.2s ease,
    transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.component-details-content-enter-from {
  opacity: 0;
  filter: blur(10px);
  transform: translateX(16px) scale(0.992);
}

.component-details-content-leave-to {
  opacity: 0;
  filter: blur(6px);
  transform: translateX(-8px) scale(0.996);
}

.component-details__header {
  display: flex;
  min-height: 50px;
  align-items: center;
  border-bottom: 1px solid rgba(23, 32, 51, 0.08);
  padding: 0 14px;
}

.component-details__header strong {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 14px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.component-details__body {
  display: grid;
  grid-template-rows: auto 1fr;
  align-content: stretch;
  overflow: auto;
  padding: 0 14px 16px;

}



.component-details__tabs {
  position: sticky;
  z-index: 1;
  top: 0;
  display: flex;
  gap: 24px;
  border-bottom: 1px solid rgba(23, 32, 51, 0.08);
  background: rgba(255, 255, 255, 0.96);
  padding-top: 2px;
}

.component-details__tabs button {
  position: relative;
  min-height: 42px;
  border: 0;
  background: transparent;
  color: var(--lh-text-tertiary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 850;
  padding: 0;
}

.component-details__tabs button::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 999px;
  background: transparent;
  content: '';
}

.component-details__tabs .component-details__tab--active {
  color: var(--lh-accent);
}

.component-details__tabs .component-details__tab--active::after {
  background: var(--lh-accent);
}

.component-details__section {
  display: grid;
  align-content: start;
  gap: 0;
  padding-top: 10px;
}

.component-details__section h3 {
  margin: 14px 0 8px;
  color: var(--lh-text-primary);
  font-size: 14px;
  font-weight: 850;
}

.component-details__row {
  display: grid;
  min-height: 38px;
  grid-template-columns: minmax(90px, 2.4fr) minmax(50px, 1.2fr);
  align-items: center;
  gap: 12px;
  border-bottom: 1px dashed rgba(23, 32, 51, 0.08);
}

.component-details__row--editable {
  min-height: 54px;
}

.component-details__row > span,
.component-details__field > span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 800;
}

.component-details__row > strong,
.component-details__row-input {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 13px;
  font-weight: 760;
  line-height: 1.45;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.component-details__row-input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  padding: 0;
}

.component-details__edit-shell {
  display: flex;
    width: 100%;
    min-width: 0;
    align-items: center;
    justify-self: stretch;
    min-height: 38px;
    border: 1px solid rgb(163 163 163 / 18%);
    border-radius: 4px;
    /* background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 255, 0.98)); */
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.94), 0 6px 16px rgb(163 163 163 / 4%);
    padding: 0 12px;
    transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.component-details__edit-shell:hover {
  border-color: rgba(0, 113, 227, 0.34);
  background: #ffffff;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    0 10px 22px rgba(15, 23, 42, 0.06);
}

.component-details__edit-shell:focus-within {
  border-color: rgba(0, 113, 227, 0.54);
  background: #ffffff;
  box-shadow:
    0 0 0 4px rgba(0, 113, 227, 0.1),
    0 12px 26px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.component-details__row-input--editable {
  text-align: center;
  cursor: text;
}

.component-details__row-input--editable::placeholder {
  color: #8aa0b8;
}

.component-details__field {
  display: grid;
  gap: 8px;
  border-bottom: 1px dashed rgba(23, 32, 51, 0.08);
  padding: 10px 0;
}

.component-details__field input,
.component-details__field textarea,
.component-details__option-row input {
  width: 100%;
  height: 38px;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 8px;
  outline: none;
  background: rgba(248, 250, 252, 0.92);
  color: var(--lh-text-primary);
  font-size: 13px;
  font-weight: 700;
  padding: 0 12px;
}

.component-details__field input:focus,
.component-details__field textarea:focus,
.component-details__option-row input:focus {
  border-color: rgba(0, 113, 227, 0.42);
  background: #ffffff;
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.1);
}

.component-details__field textarea {
  min-height: 148px;
  padding: 10px 12px;
  font-family: Consolas, 'Courier New', monospace;
  line-height: 1.6;
  resize: vertical;
}

.component-details__json-fixed {
  margin: 0;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
  background: rgba(248, 250, 252, 0.92);
  color: var(--lh-text-primary);
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 10px 12px 0;
  white-space: pre-wrap;
}

.component-details__json-fixed--closing {
  border-top: 0;
  border-bottom: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 0 0 8px 8px;
  padding: 0 12px 10px;
}

.component-details__json-textarea {
  border-radius: 0;
}

.component-details__json-textarea--error,
.component-details__json-textarea--error:focus {
  border-color: rgba(239, 68, 68, 0.68);
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.1);
}

.component-details__json-error {
  color: #ef4444;
  font-size: 12px;
  font-weight: 750;
  line-height: 1.5;
}

.component-details :deep(.el-switch) {
  justify-self: end;
}

.component-details__option-editor {
  display: grid;
  gap: 10px;
  margin-top: 16px;
  border-top: 1px solid rgba(23, 32, 51, 0.08);
  padding-top: 14px;
}

.component-details__option-editor header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.component-details__option-editor header button,
.component-details__option-row button {
  min-height: 30px;
  border: 1px solid rgba(0, 113, 227, 0.18);
  border-radius: 8px;
  background: rgba(245, 249, 255, 0.9);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 750;
  padding: 0 10px;
}

.component-details__option-editor header button {
  color: var(--lh-accent);
}

.component-details__option-editor header span {
  color: var(--lh-text-primary);
  font-size: 14px;
  font-weight: 850;
}

.component-details__option-editor p {
  margin: 0;
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.component-details__option-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.component-details__link-card {
  display: grid;
  gap: 12px;
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.9);
  padding: 12px;
}

.component-details__link-card span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 750;
}

.component-details__link-card button {
  min-height: 38px;
  border: 1px dashed rgba(23, 32, 51, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-secondary);
  cursor: default;
  font-weight: 800;
}

.component-details__empty {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  padding: 28px;
  background-color: #fcfafc;
  text-align: center;
}

.component-details__empty img {
  display: block;
  width: min(82%, 230px);
  max-height: 190px;
  background-color: #FCFAFC;
  object-fit: contain;
}

.component-details__empty strong {
  color: var(--lh-text-primary);
  font-size: 16px;
  line-height: 1.5;
}

.component-details__empty span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.5;
}

@keyframes component-details-fixed-in {
  from {
    opacity: 0;
    transform: translateX(16px) scale(0.985);
  }

  to {
    opacity: 1;
    transform: translateX(0) scale(1);
  }
}

@media (max-width: 1080px) {
  .component-details {
    position: static;
    min-height: 420px;
    max-height: none;
  }
}
</style>
