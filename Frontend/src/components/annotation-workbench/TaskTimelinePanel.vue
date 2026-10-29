<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { GetTaskTimeline } from '../../utils/api'

type TimelineTone = 'success' | 'danger' | 'primary' | 'neutral'

interface TimelineEntry {
  id?: string | number
  taskItemId?: string | number
  operatorName?: string
  operatorAction?: string
  operatorOrder?: string | number
  comment?: string | null
  createTime?: string
  [key: string]: unknown
}

const props = defineProps<{
  taskItemId?: string | number | null
}>()

const isExpanded = shallowRef(false)
const isLoading = shallowRef(false)
const errorMessage = shallowRef('')
const loadedTaskItemId = shallowRef('')
const timelineEntries = shallowRef<TimelineEntry[]>([])

let requestSeed = 0

const currentTaskItemId = computed(() => readText(props.taskItemId))

watch(
  currentTaskItemId,
  () => {
    requestSeed += 1
    isExpanded.value = false
    isLoading.value = false
    errorMessage.value = ''
    loadedTaskItemId.value = ''
    timelineEntries.value = []
  },
  { immediate: true },
)

function readText(value: unknown, fallback = '') {
  if (typeof value === 'string') {
    return value.trim() || fallback
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return String(value)
  }

  return fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

function readTimelineEntries(response: unknown) {
  if (Array.isArray(response)) {
    return response as TimelineEntry[]
  }

  if (response && typeof response === 'object' && Array.isArray((response as { data?: unknown }).data)) {
    return (response as { data: TimelineEntry[] }).data
  }

  return []
}

function getActionMeta(value: unknown): { label: string; tone: TimelineTone } {
  const action = readText(value, 'UNKNOWN').toUpperCase()
  const actionMeta: Record<string, { label: string; tone: TimelineTone }> = {
    SUBMIT: { label: '已提交', tone: 'success' },
    AI_PRE_REVIEW: { label: 'AI 预审', tone: 'primary' },
    REJECT: { label: '已驳回', tone: 'danger' },
    APPROVE: { label: '已通过', tone: 'success' },
    MODIFY: { label: '修改并通过', tone: 'success' },
  }

  return actionMeta[action] ?? { label: readText(value, '操作记录'), tone: 'neutral' }
}

function formatTimelineTime(value: unknown) {
  const text = readText(value)

  if (!text) {
    return '-'
  }

  return text.replace('T', ' ').slice(0, 16)
}

function getEntryKey(entry: TimelineEntry, index: number) {
  return readText(entry.id, `${readText(entry.operatorOrder, String(index))}-${index}`)
}

async function loadTimeline() {
  const taskItemId = currentTaskItemId.value

  if (!taskItemId) {
    timelineEntries.value = []
    return
  }

  const currentRequestSeed = ++requestSeed
  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await GetTaskTimeline(taskItemId)

    if (currentRequestSeed !== requestSeed) {
      return
    }

    timelineEntries.value = readTimelineEntries(response)
      .slice()
      .sort((first, second) => readNumber(first.operatorOrder) - readNumber(second.operatorOrder))
    loadedTaskItemId.value = taskItemId
  } catch {
    if (currentRequestSeed === requestSeed) {
      errorMessage.value = '审计时间线加载失败'
      timelineEntries.value = []
    }
  } finally {
    if (currentRequestSeed === requestSeed) {
      isLoading.value = false
    }
  }
}

function handleToggle() {
  isExpanded.value = !isExpanded.value

  if (isExpanded.value && loadedTaskItemId.value !== currentTaskItemId.value) {
    void loadTimeline()
  }
}
</script>

<template>
  <section class="timeline-panel">
    <button
      class="timeline-panel__toggle"
      type="button"
      :aria-expanded="isExpanded"
      :disabled="!currentTaskItemId"
      @click="handleToggle"
    >
      <span>审计时间线</span>
      <span class="timeline-panel__toggle-action">
        {{ isExpanded ? '收起' : '展开' }}
        <i class="timeline-panel__chevron" aria-hidden="true" />
      </span>
    </button>

    <Transition name="timeline-expand">
      <div v-if="isExpanded" class="timeline-panel__body">
        <p v-if="isLoading" class="timeline-panel__notice">正在加载时间线...</p>

        <div v-else-if="errorMessage" class="timeline-panel__notice timeline-panel__notice--error">
          <span>{{ errorMessage }}</span>
          <el-button link type="primary" @click="loadTimeline">重新加载</el-button>
        </div>

        <p v-else-if="!timelineEntries.length" class="timeline-panel__notice">暂无流转记录</p>

        <ol v-else class="timeline-panel__list">
          <li
            v-for="(entry, index) in timelineEntries"
            :key="getEntryKey(entry, index)"
            class="timeline-panel__item"
            :class="`timeline-panel__item--${getActionMeta(entry.operatorAction).tone}`"
          >
            <span class="timeline-panel__dot" aria-hidden="true" />
            <div class="timeline-panel__content">
              <p class="timeline-panel__meta">
                <strong>{{ readText(entry.operatorName, '系统') }}</strong>
                <time>{{ formatTimelineTime(entry.createTime) }}</time>
              </p>
              <p class="timeline-panel__action">{{ getActionMeta(entry.operatorAction).label }}</p>
              <p v-if="readText(entry.comment)" class="timeline-panel__comment">{{ entry.comment }}</p>
            </div>
          </li>
        </ol>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.timeline-panel {
  min-width: 0;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
}

.timeline-panel__toggle {
  display: flex;
  width: 100%;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 0;
  background: transparent;
  padding: 0 16px;
  color: #111827;
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 850;
  text-align: left;
}

.timeline-panel__toggle:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.timeline-panel__toggle-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.timeline-panel__chevron {
  width: 8px;
  height: 8px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(45deg) translateY(-2px);
  transition: transform 0.2s ease;
}

.timeline-panel__toggle[aria-expanded='true'] .timeline-panel__chevron {
  transform: rotate(225deg) translate(-2px, -2px);
}

.timeline-panel__body {
  overflow: hidden;
  border-top: 1px solid #edf0f4;
  padding: 6px 16px 14px;
}

.timeline-panel__notice {
  margin: 0;
  padding: 16px 0 8px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.timeline-panel__notice--error {
  display: grid;
  justify-items: center;
  gap: 4px;
  color: #ef4444;
}

.timeline-panel__list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline-panel__item {
  --timeline-color: #94a3b8;
  position: relative;
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  padding: 14px 0;
}

.timeline-panel__item:not(:last-child)::after {
  position: absolute;
  top: 29px;
  bottom: -1px;
  left: 4px;
  width: 1px;
  background: #e5e7eb;
  content: '';
}

.timeline-panel__item--success {
  --timeline-color: #16a34a;
}

.timeline-panel__item--danger {
  --timeline-color: #ef4444;
}

.timeline-panel__item--primary {
  --timeline-color: #2563eb;
}

.timeline-panel__dot {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border-radius: 50%;
  background: var(--timeline-color);
  box-shadow: 0 0 0 3px #ffffff;
}

.timeline-panel__content {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.timeline-panel__meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.timeline-panel__meta strong {
  color: #334155;
  font-weight: 850;
}

.timeline-panel__action {
  margin: 0;
  color: var(--timeline-color);
  font-size: 12px;
  font-weight: 800;
}

.timeline-panel__comment {
  margin: 2px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.timeline-expand-enter-active,
.timeline-expand-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.timeline-expand-enter-from,
.timeline-expand-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
