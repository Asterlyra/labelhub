<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import type { AnnotationFormData, WorkbenchMaterial } from '../../types/workbench'
import { CreatAiSuggestion, GetAiSuggestion } from '../../utils/api'

interface AiSuggestionRecord {
  taskId?: string | number
  taskItemId?: string | number
  suggestionId: string
  status: string
  annotationData: AnnotationFormData
  lastError?: string | null
}

const props = defineProps<{
  taskItemId?: string | number
  materials: WorkbenchMaterial[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  adopt: [value: AnnotationFormData]
}>()

const suggestionCache = new Map<string, AiSuggestionRecord>()
const suggestion = shallowRef<AiSuggestionRecord | null>(null)
const isCreating = shallowRef(false)
const errorMessage = shallowRef('')
let pollTimer: number | null = null
let requestSeed = 0

const currentTaskItemId = computed(() => readString(props.taskItemId))
const normalizedStatus = computed(() => readString(suggestion.value?.status, '').toLowerCase())
const isPending = computed(() => isSuggestionPending(suggestion.value))
const isCompleted = computed(() => isSuggestionCompleted(suggestion.value))
const canGenerate = computed(() => Boolean(currentTaskItemId.value) && !isCreating.value && !isPending.value)
const suggestionTitle = computed(() => (isCompleted.value ? 'AI 预审组件' : 'AI 预审组件'))
const generateButtonText = computed(() => {
  if (isCreating.value) {
    return '生成中'
  }

  return suggestion.value ? '重新生成' : '生成预审参考'
})
const statusText = computed(() => {
  if (isCreating.value || isPending.value) {
    return '正在生成中'
  }

  if (isCompleted.value) {
    return '已生成'
  }

  if (suggestion.value?.status) {
    return suggestion.value.lastError ? '生成失败' : suggestion.value.status
  }

  return '未生成'
})
const suggestionEntries = computed(() => {
  const annotationData = suggestion.value?.annotationData

  if (!annotationData || typeof annotationData !== 'object') {
    return []
  }

  return Object.entries(annotationData).map(([field, value]) => {
    const material = props.materials.find((item) => getMaterialField(item) === field)

    return {
      field,
      label: material ? getMaterialLabel(material) : field,
      value: formatValue(value, '-'),
    }
  })
})
const suggestionSummary = computed(() => {
  if (!suggestionEntries.value.length) {
    return ''
  }

  return suggestionEntries.value
    .map((entry) => `${entry.label}：${entry.value}`)
    .join('；')
})

void suggestionSummary

function readString(value: unknown, fallback = '') {
  if (value === undefined || value === null) {
    return fallback
  }

  const text = String(value).trim()

  return text || fallback
}

function formatValue(value: unknown, fallback = ''): string {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  if (Array.isArray(value)) {
    return value.map((item) => formatValue(item, '-')).join('、')
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}

function getResponseData(response: any) {
  const payload = response?.data ?? response ?? {}

  return payload?.data ?? payload
}

function assertApiSuccess(response: any) {
  const code = response?.code

  if (code !== undefined && Number(code) !== 200) {
    throw new Error(response?.message ?? response?.msg ?? 'AI 预审请求失败')
  }
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
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

function normalizeAnnotationData(value: unknown): AnnotationFormData {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as AnnotationFormData : {}
}

function isSuggestionPending(value: AiSuggestionRecord | null) {
  const status = readString(value?.status).toLowerCase()

  return ['pending', 'running', 'processing', 'in_progress'].includes(status)
}

function isSuggestionCompleted(value: AiSuggestionRecord | null) {
  const status = readString(value?.status).toLowerCase()

  return ['completed', 'complete', 'success', 'succeeded', 'done'].includes(status)
}

function normalizeSuggestion(response: any, fallback: Partial<AiSuggestionRecord> = {}): AiSuggestionRecord {
  const data = getResponseData(response) ?? {}

  return {
    taskId: data.taskId ?? fallback.taskId,
    taskItemId: data.taskItemId ?? fallback.taskItemId ?? currentTaskItemId.value,
    suggestionId: readString(data.suggestionId ?? fallback.suggestionId),
    status: readString(data.status ?? fallback.status, 'pending'),
    annotationData: normalizeAnnotationData(data.annotationData),
    lastError: data.lastError ?? fallback.lastError ?? '',
  }
}

function cacheSuggestion(nextSuggestion: AiSuggestionRecord) {
  const taskItemId = readString(nextSuggestion.taskItemId, currentTaskItemId.value)

  if (!taskItemId) {
    return
  }

  suggestionCache.set(taskItemId, nextSuggestion)

  if (taskItemId === currentTaskItemId.value) {
    suggestion.value = nextSuggestion
  }
}

function clearPollTimer() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

function schedulePoll(nextSuggestion: AiSuggestionRecord) {
  clearPollTimer()

  if (!isSuggestionPending(nextSuggestion) || !nextSuggestion.suggestionId) {
    return
  }

  const taskItemId = readString(nextSuggestion.taskItemId, currentTaskItemId.value)

  if (!taskItemId || taskItemId !== currentTaskItemId.value) {
    return
  }

  pollTimer = window.setTimeout(() => {
    void querySuggestion(taskItemId, nextSuggestion.suggestionId, requestSeed)
  }, 4000)
}

async function querySuggestion(taskItemId: string, suggestionId: string, seed: number) {
  try {
    const response = await GetAiSuggestion(taskItemId, suggestionId)
    assertApiSuccess(response)

    if (seed !== requestSeed || taskItemId !== currentTaskItemId.value) {
      return
    }

    const nextSuggestion = normalizeSuggestion(response, {
      taskItemId,
      suggestionId,
      status: 'pending',
    })
    cacheSuggestion(nextSuggestion)
    errorMessage.value = ''

    if (isSuggestionPending(nextSuggestion)) {
      schedulePoll(nextSuggestion)
    }
  } catch (error: any) {
    if (seed !== requestSeed || taskItemId !== currentTaskItemId.value) {
      return
    }

    errorMessage.value = readErrorMessage(error, 'AI 预审刷新失败')
    ElMessage.error(errorMessage.value)
  }
}

async function handleGenerateSuggestion() {
  const taskItemId = currentTaskItemId.value

  if (!taskItemId || !canGenerate.value) {
    return
  }

  requestSeed += 1
  const seed = requestSeed
  clearPollTimer()
  isCreating.value = true
  errorMessage.value = ''

  try {
    const response = await CreatAiSuggestion(taskItemId)
    assertApiSuccess(response)

    if (seed !== requestSeed || taskItemId !== currentTaskItemId.value) {
      return
    }

    const createdSuggestion = normalizeSuggestion(response, {
      taskItemId,
      status: 'pending',
    })

    if (!createdSuggestion.suggestionId) {
      throw new Error('AI 预审未返回 suggestionId')
    }

    cacheSuggestion(createdSuggestion)

    await querySuggestion(taskItemId, createdSuggestion.suggestionId, seed)
  } catch (error: any) {
    if (seed !== requestSeed || taskItemId !== currentTaskItemId.value) {
      return
    }

    errorMessage.value = readErrorMessage(error, 'AI 预审生成失败')
    ElMessage.error(errorMessage.value)
  } finally {
    if (seed === requestSeed) {
      isCreating.value = false
    }
  }
}

function normalizeAdoptData() {
  const annotationData = suggestion.value?.annotationData ?? {}
  const nextData: AnnotationFormData = {}

  props.materials.forEach((material) => {
    const field = getMaterialField(material)
    const type = getMaterialType(material)

    if (!field || type === 'text-display' || annotationData[field] === undefined) {
      return
    }

    if ((type === 'checkbox' || (type === 'select' && isMaterialMultiple(material))) && !Array.isArray(annotationData[field])) {
      nextData[field] = [annotationData[field]]
      return
    }

    nextData[field] = annotationData[field]
  })

  return nextData
}

function handleAdoptSuggestion() {
  if (props.readonly || !isCompleted.value) {
    return
  }

  const nextData = normalizeAdoptData()

  if (!Object.keys(nextData).length) {
    ElMessage.warning('暂无可采纳的 AI 预审内容')
    return
  }

  emit('adopt', nextData)
  ElMessage.success('已采纳 AI 预审参考')
}

watch(
  currentTaskItemId,
  (taskItemId) => {
    requestSeed += 1
    clearPollTimer()
    errorMessage.value = ''
    isCreating.value = false
    suggestion.value = taskItemId ? suggestionCache.get(taskItemId) ?? null : null

    if (suggestion.value) {
      schedulePoll(suggestion.value)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  requestSeed += 1
  clearPollTimer()
})
</script>

<template>
  <section class="ai-suggestion" aria-label="AI预审组件">
    <header class="ai-suggestion__header">
      <div class="ai-suggestion__title">
        <strong>{{ suggestionTitle }}</strong>
        <span>调用模型生成参考标注，可一键填入上方模板字段</span>
      </div>

      <div class="ai-suggestion__actions">
        <span class="ai-suggestion__status" :class="`ai-suggestion__status--${normalizedStatus || 'idle'}`">
          {{ statusText }}
        </span>
        <button
          v-if="suggestion"
          class="ai-suggestion__ghost-button"
          type="button"
          :disabled="!canGenerate"
          @click="handleGenerateSuggestion"
        >
          {{ generateButtonText }}
        </button>
      </div>
    </header>

    <p v-if="errorMessage || suggestion?.lastError" class="ai-suggestion__error" role="alert">
      {{ errorMessage || suggestion?.lastError }}
    </p>

    <div v-if="isPending || isCreating" class="ai-suggestion__pending">
      <span aria-hidden="true"></span>
      <p>正在生成中</p>
    </div>

    <div v-else-if="isCompleted" class="ai-suggestion__result">
      <!-- <p v-if="suggestionSummary" class="ai-suggestion__summary">
        {{ suggestionSummary }}
      </p> -->

      <div v-if="suggestionEntries.length" class="ai-suggestion__entries">
        <article v-for="entry in suggestionEntries" :key="entry.field" class="ai-suggestion__entry">
          <span>{{ entry.field }}</span>
          <strong>{{ entry.value }}</strong>
       
        </article>
      </div>

      <button
        class="ai-suggestion__primary-button"
        type="button"
        :disabled="readonly || !suggestionEntries.length"
        @click="handleAdoptSuggestion"
      >
        采纳为标注结果 →
      </button>
    </div>

    <div v-else class="ai-suggestion__empty">
      <button
        class="ai-suggestion__primary-button ai-suggestion__primary-button--compact"
        type="button"
        :disabled="!canGenerate"
        @click="handleGenerateSuggestion"
      >
        生成预审参考
      </button>
      <span>AI 预审内容将在生成完成后展示在这里</span>
    </div>
  </section>
</template>

<style scoped>
.ai-suggestion {
  display: grid;
  gap: 10px;
  border: 1px dashed rgba(109, 40, 217, 0.34);
  border-radius: 8px;
  background: #fbf7ff;
  box-shadow: none;
  color: #1f2937;
  padding: 12px;
}

.ai-suggestion__header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-suggestion__title {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.ai-suggestion__title strong {
  color: #5b21b6;
  font-size: 14px;
  font-weight: 900;
}

.ai-suggestion__title strong::before {
  content: "✦";
  margin-right: 6px;
}

.ai-suggestion__title span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
}

.ai-suggestion__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.ai-suggestion__status {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  font-weight: 850;
  padding: 0 10px;
  white-space: nowrap;
}

.ai-suggestion__status--pending {
  background: #fef3c7;
  color: #b45309;
}

.ai-suggestion__status--completed {
  background: #dcfce7;
  color: #15803d;
}

.ai-suggestion__ghost-button,
.ai-suggestion__primary-button {
  min-height: 34px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 900;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
  white-space: nowrap;
}

.ai-suggestion__ghost-button {
  border: 1px solid rgba(109, 40, 217, 0.28);
  background: rgba(255, 255, 255, 0.86);
  color: #6d28d9;
  padding: 0 14px;
}

.ai-suggestion__ghost-button:hover:not(:disabled) {
  border-color: rgba(109, 40, 217, 0.52);
  box-shadow: 0 10px 20px rgba(109, 40, 217, 0.12);
  transform: translateY(-1px);
}

.ai-suggestion__primary-button {
  width: fit-content;
  border: 1px solid transparent;
  background: #6d28d9;
  color: #ffffff;
  padding: 0 18px;
}

.ai-suggestion__primary-button:hover:not(:disabled) {
  background: #5b21b6;
  box-shadow: 0 12px 22px rgba(109, 40, 217, 0.24);
  transform: translateY(-1px);
}

.ai-suggestion__primary-button--compact {
  min-width: 112px;
}

.ai-suggestion__ghost-button:disabled,
.ai-suggestion__primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.54;
  transform: none;
}

.ai-suggestion__error {
  margin: 0;
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 6px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 750;
  padding: 10px 12px;
}

.ai-suggestion__pending {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(109, 40, 217, 0.12);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.62);
  color: #5b21b6;
  font-size: 14px;
  font-weight: 850;
  padding: 0 14px;
}

.ai-suggestion__pending span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #7c3aed;
  box-shadow: 0 0 0 0 rgba(124, 58, 237, 0.35);
  animation: ai-suggestion-pulse 1s ease-in-out infinite;
}

.ai-suggestion__pending p {
  margin: 0;
}

.ai-suggestion__result {
  display: grid;
  gap: 10px;
}

.ai-suggestion__summary {
  margin: 0;
  color: #374151;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.ai-suggestion__entries {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.ai-suggestion__entry {
  display: grid;
  min-width: 0;
  gap: 5px;
  border: 1px solid rgba(109, 40, 217, 0.12);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  padding: 9px 10px;
}

.ai-suggestion__entry span,
.ai-suggestion__entry small {
  overflow: hidden;
  color: #7c3aed;
  font-size: 12px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-suggestion__entry strong {
  min-width: 0;
  color: #111827;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.ai-suggestion__empty {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid rgba(109, 40, 217, 0.12);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.62);
  padding: 10px;
}

.ai-suggestion__empty span {
  color: #6b7280;
  font-size: 13px;
  font-weight: 750;
}

@keyframes ai-suggestion-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(124, 58, 237, 0.36);
    transform: scale(0.92);
  }

  70% {
    box-shadow: 0 0 0 10px rgba(124, 58, 237, 0);
    transform: scale(1);
  }

  100% {
    box-shadow: 0 0 0 0 rgba(124, 58, 237, 0);
    transform: scale(0.92);
  }
}

@media (max-width: 720px) {
  .ai-suggestion__header,
  .ai-suggestion__actions,
  .ai-suggestion__empty {
    align-items: stretch;
    flex-direction: column;
  }

  .ai-suggestion__entry {
    grid-template-columns: 1fr;
  }
}
</style>
