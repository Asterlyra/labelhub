<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import TaskAiPreviewDetailPanel from './TaskAiPreviewDetailPanel.vue'
import TaskItemsDetailPanel from './TaskItemsDetailPanel.vue'

type TaskDetailAudience = 'owner' | 'labeler' | 'reviewer'

interface Props {
  modelValue: boolean
  loading?: boolean
  errorMessage?: string
  title?: string
  detail?: Record<string, unknown> | null
  audience: TaskDetailAudience
  showItemDetails?: boolean
  showAiPreviewDetails?: boolean
  taskId?: string | number | null
}

interface DetailRow {
  label: string
  value: string
  kind?: 'text' | 'status' | 'tags' | 'progress'
  tags?: string[]
  percent?: number
  tone?: 'primary' | 'success' | 'info' | 'warning' | 'danger'
  color?: string
}

interface ProgressMetric {
  key: string
  label: string
  value: number
  color: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const detail = computed(() => props.detail ?? {})
const progress = computed(() => readObject(detail.value.progress))
const tags = computed(() => readTags(detail.value.tags))
const isOwnerView = computed(() => props.audience === 'owner')
const isLabelerView = computed(() => props.audience === 'labeler')
const isReviewerView = computed(() => props.audience === 'reviewer')
const dialogTitle = computed(() => readString(detail.value.title, props.title || '任务详情'))

const shouldShowItemDetails = computed(() => isOwnerView.value && Boolean(props.showItemDetails))
const shouldShowAiPreviewDetails = computed(() => isReviewerView.value && Boolean(props.showAiPreviewDetails))
const shouldShowTabs = computed(() => shouldShowItemDetails.value || shouldShowAiPreviewDetails.value)
const currentTaskId = computed(() => readString(props.taskId ?? detail.value.id ?? detail.value.taskId))
const activeTab = shallowRef<'overview' | 'items' | 'aiPreview'>('overview')

const progressMetrics = computed<ProgressMetric[]>(() => [
  { key: 'unassignedCount', label: '未分配', value: readNumber(progress.value.unassignedCount), color: '#94a3b8' },
  { key: 'claimedCount', label: '已领取', value: readNumber(progress.value.claimedCount), color: '#3b82f6' },
  { key: 'submittedCount', label: '已提交', value: readNumber(progress.value.submittedCount), color: '#f59e0b' },
  { key: 'rejectedCount', label: '已驳回', value: readNumber(progress.value.rejectedCount), color: '#ef4444' },
  { key: 'reviewedCount', label: '已通过', value: readNumber(progress.value.reviewedCount), color: '#22c55e' },
])

const totalCount = computed(() => readNumber(progress.value.totalCount ?? detail.value.quota))

const progressSegments = computed(() =>
  progressMetrics.value.map((metric) => ({
    ...metric,
    width: totalCount.value > 0 ? Math.min(100, (metric.value / totalCount.value) * 100) : 0,
  })),
)

const baseRows = computed<DetailRow[]>(() =>
  [
    ['任务编号', detail.value.id],
    ['任务配额', detail.value.quota],
    ['截止时间', formatDate(detail.value.deadline)],
  ]
    .map(([label, value]) => ({ label: String(label), value: formatValue(value, '-') }))
    .filter((row) => row.value !== '-'),
)

const labelerDetailRows = computed<DetailRow[]>(() => [
  createTextRow('任务名称', detail.value.title ?? props.title),
  createTextRow('任务描述', detail.value.description),
  createTextRow('截止时间', formatDate(detail.value.deadline)),
  createStatusRow('任务状态', detail.value.status),
  createTextRow('数据集', detail.value.datasetName),
  createTextRow('标注模板', detail.value.templateName),
  createTagsRow('任务标签', tags.value),
  createTextRow('总数', progress.value.totalCount ?? detail.value.quota),
  createProgressRow('未分配', progress.value.unassignedCount, '#94a3b8'),
  createProgressRow('已领取', progress.value.claimedCount, '#3b82f6'),
  createProgressRow('已提交', progress.value.submittedCount, '#f59e0b'),
  createProgressRow('已驳回', progress.value.rejectedCount, '#ef4444'),
  createProgressRow('已通过', progress.value.reviewedCount, '#22c55e'),
].filter((row) => row.value !== '-' || row.kind === 'tags'))

const ownerRows = computed<DetailRow[]>(() =>
  [
    ['任务状态', formatStatus(detail.value.status)],
    ['数据集', detail.value.datasetName],
    ['标注模板', detail.value.templateName],
    ['审核员 ID', detail.value.reviewerIds],
    ['AI 预审', readBoolean(detail.value.aiReviewEnabled) ? '已启用' : '未启用'],
  ]
    .map(([label, value]) => ({ label: String(label), value: formatValue(value, '-') }))
    .filter((row) => row.value !== '-'),
)

const distributionConfig = computed(() => readObject(detail.value.distributionConfig))
const distributionInnerConfig = computed(() => {
  const config = readObject(distributionConfig.value.config)

  return Object.keys(config).length ? config : distributionConfig.value
})
const distributionRows = computed<DetailRow[]>(() =>
  [
    ['分发策略', formatStrategy(distributionConfig.value.strategyType)],
    ['单人领取上限', distributionInnerConfig.value.maxClaimPerUser],
    ['单次领取数量', distributionInnerConfig.value.claimCount],
  ]
    .map(([label, value]) => ({ label: String(label), value: formatValue(value, '-') }))
    .filter((row) => row.value !== '-'),
)

const aiReviewConfig = computed(() => readObject(detail.value.aiReviewConfig))
const aiDimensions = computed(() => {
  const dimensions = aiReviewConfig.value.dimensions

  return Array.isArray(dimensions)
    ? dimensions.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
    : []
})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      activeTab.value = 'overview'
    }
  },
)

watch(currentTaskId, () => {
  activeTab.value = 'overview'
})

function createTextRow(label: string, value: unknown): DetailRow {
  return {
    label,
    value: formatValue(value, '-'),
    kind: 'text',
  }
}

function createStatusRow(label: string, value: unknown): DetailRow {
  return {
    label,
    value: formatStatus(value),
    kind: 'status',
    tone: getStatusTagTone(value),
  }
}

function createTagsRow(label: string, value: string[]): DetailRow {
  return {
    label,
    value: value.length ? value.join('、') : '暂无标签',
    kind: 'tags',
    tags: value,
  }
}

function createProgressRow(label: string, value: unknown, color: string): DetailRow {
  const count = readNumber(value)
  const total = totalCount.value
  const percent = total > 0 ? Math.min(100, Math.max(0, Math.round((count / total) * 100))) : 0

  return {
    label,
    value: total > 0 ? `${count.toLocaleString('en-US')} / ${total.toLocaleString('en-US')}` : count.toLocaleString('en-US'),
    kind: 'progress',
    percent,
    color,
  }
}

function readObject(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {}
}

function readString(value: unknown, fallback = '') {
  if (value === undefined || value === null) {
    return fallback
  }

  const text = String(value).trim()

  return text || fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

function readBoolean(value: unknown) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

function readTags(value: unknown) {
  if (Array.isArray(value)) {
    return value.map((item) => readString(item)).filter(Boolean)
  }

  if (typeof value === 'string') {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  }

  return []
}

function formatValue(value: unknown, fallback = '-'): string {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  if (Array.isArray(value)) {
    return value.map((item) => formatValue(item, '')).filter(Boolean).join('、') || fallback
  }

  return String(value)
}

function formatDate(value: unknown) {
  const text = readString(value)

  return text ? text.replace('T', ' ') : ''
}

function formatStatus(value: unknown) {
  const status = readString(value, '-').toUpperCase()
  const statusTextMap: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    PAUSED: '已暂停',
    REVIEWING: '审核中',
    ENDED: '已结束',
  }

  return statusTextMap[status] ?? status
}

function getStatusTagTone(value: unknown): DetailRow['tone'] {
  const status = readString(value, '').toUpperCase()

  if (['PUBLISHED', 'REVIEWING', 'CLAIMED', 'SUBMITTED', 'REVIEWED'].includes(status)) {
    return 'success'
  }

  if (['DRAFT', 'PAUSED'].includes(status)) {
    return 'warning'
  }

  if (['REJECTED', 'FAILED'].includes(status)) {
    return 'danger'
  }

  if (['ENDED', 'COMPLETED'].includes(status)) {
    return 'info'
  }

  return 'primary'
}

function formatStrategy(value: unknown) {
  const strategy = readString(value, '-').toUpperCase()
  const strategyTextMap: Record<string, string> = {
    FIRST_COME_FIRST_SERVED: '先到先得',
    ASSIGNED: '指派',
    MANUAL_ASSIGN: '人工指派',
    RANDOM: '随机分配',
    QUOTA_GRAB: '配额抢单',
  }

  return strategyTextMap[strategy] ?? strategy
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    class="task-respect-detail"
    :width="shouldShowTabs ? '1120px' : '920px'"
    destroy-on-close
  >
    <template #header>
      <header class="task-respect-detail__header">
        <div>
          <span>Task Detail</span>
          <h2>{{ dialogTitle }}</h2>
        </div>
        <el-tag v-if="isOwnerView && detail.status" type="primary">
          {{ formatStatus(detail.status) }}
        </el-tag>
      </header>
    </template>

    <el-skeleton v-if="loading" :rows="8" animated />

    <p v-else-if="errorMessage" class="task-respect-detail__error" role="alert">
      {{ errorMessage }}
    </p>

    <template v-else>
      <el-tabs
        v-if="shouldShowTabs"
        v-model="activeTab"
        class="task-respect-detail__tabs"
      >
        <el-tab-pane
          :label="shouldShowAiPreviewDetails ? '\u5ba1\u6838\u603b\u89c8' : '\u4efb\u52a1\u603b\u89c8'"
          name="overview"
        />
        <el-tab-pane
          v-if="shouldShowItemDetails"
          :label="'\u6570\u636e\u9879\u8be6\u60c5'"
          name="items"
        />
        <el-tab-pane
          v-if="shouldShowAiPreviewDetails"
          :label="'AI\u9884\u5ba1\u8be6\u60c5'"
          name="aiPreview"
        />
      </el-tabs>

      <Transition name="task-respect-detail-tab" mode="out-in">
        <section
          v-if="!shouldShowTabs || activeTab === 'overview'"
          key="overview"
          class="task-respect-detail__body"
        >
      <div class="task-respect-detail__intro">
        <p>{{ formatValue(detail.description, '暂无任务描述') }}</p>
        <div v-if="tags.length" class="task-respect-detail__tags">
          <el-tag v-for="tag in tags" :key="tag" type="warning">{{ tag }}</el-tag>
        </div>
      </div>

      <section class="task-respect-detail__section">
        <header class="task-respect-detail__section-header">
          <div>
            <span>Global Progress</span>
            <h3>任务全局进度</h3>
          </div>
          <strong>{{ totalCount }}</strong>
        </header>

        <div class="task-respect-detail__progress-track" aria-label="任务全局进度">
          <span
            v-for="metric in progressSegments"
            :key="metric.key"
            :style="{ backgroundColor: metric.color, width: `${metric.width}%` }"
          ></span>
        </div>

        <div class="task-respect-detail__metrics">
          <article v-for="metric in progressMetrics" :key="metric.key">
            <i :style="{ backgroundColor: metric.color }"></i>
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
          </article>
        </div>
      </section>

      <section class="task-respect-detail__section">
        <header class="task-respect-detail__section-header">
          <div>
            <span>Overview</span>
            <h3>基础信息</h3>
          </div>
        </header>
        <div class="task-respect-detail__grid">
          <article v-for="row in baseRows" :key="row.label">
            <span>{{ row.label }}</span>
            <strong>{{ row.value }}</strong>
          </article>
        </div>
      </section>

      <section v-if="isLabelerView && labelerDetailRows.length" class="task-respect-detail__section">
        <header class="task-respect-detail__section-header">
          <div>
            <span>Detail Table</span>
            <h3>任务详情表</h3>
          </div>
          <el-tag type="primary" effect="light">标注员视图</el-tag>
        </header>
        <el-table
          :data="labelerDetailRows"
          class="task-respect-detail__info-table"
          row-key="label"
          stripe
        >
          <el-table-column prop="label" label="字段" width="168" header-align="center">
            <template #default="{ row }">
              <span class="task-respect-detail__info-label">{{ row.label }}</span>
            </template>
          </el-table-column>
          <el-table-column label="内容">
            <template #default="{ row }">
              <div
                class="task-respect-detail__info-value"
                :class="`task-respect-detail__info-value--${row.kind || 'text'}`"
              >
                <el-tag v-if="row.kind === 'status'" :type="row.tone" effect="light">
                  {{ row.value }}
                </el-tag>
                <div v-else-if="row.kind === 'tags'" class="task-respect-detail__info-tags">
                  <el-tag
                    v-for="tag in row.tags"
                    :key="tag"
                    type="warning"
                    effect="light"
                  >
                    {{ tag }}
                  </el-tag>
                  <el-tag v-if="!row.tags?.length" type="info" effect="plain">暂无标签</el-tag>
                </div>
                <div v-else-if="row.kind === 'progress'" class="task-respect-detail__info-progress">
                  <strong>{{ row.value }}</strong>
                  <el-progress
                    :color="row.color"
                    :percentage="row.percent"
                    :show-text="false"
                  />
                  <span>{{ row.percent }}%</span>
                </div>
                <span v-else>{{ row.value }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <template v-if="isOwnerView">
        <section class="task-respect-detail__section">
          <header class="task-respect-detail__section-header">
            <div>
              <span>Administration</span>
              <h3>管理员配置</h3>
            </div>
          </header>
          <div class="task-respect-detail__grid">
            <article v-for="row in ownerRows" :key="row.label">
              <span>{{ row.label }}</span>
              <strong>{{ row.value }}</strong>
            </article>
          </div>
        </section>

        <section v-if="distributionRows.length" class="task-respect-detail__section">
          <header class="task-respect-detail__section-header">
            <div>
              <span>Distribution</span>
              <h3>分发策略</h3>
            </div>
          </header>
          <div class="task-respect-detail__grid">
            <article v-for="row in distributionRows" :key="row.label">
              <span>{{ row.label }}</span>
              <strong>{{ row.value }}</strong>
            </article>
          </div>
        </section>

        <section v-if="readBoolean(detail.aiReviewEnabled)" class="task-respect-detail__section">
          <header class="task-respect-detail__section-header">
            <div>
              <span>AI Review</span>
              <h3>AI 预审配置</h3>
            </div>
          </header>
          <div class="task-respect-detail__grid">
            <article>
              <span>通过分数</span>
              <strong>{{ formatValue(aiReviewConfig.passThreshold) }}</strong>
            </article>
            <article>
              <span>人工评审分数</span>
              <strong>{{ formatValue(aiReviewConfig.reviewThreshold) }}</strong>
            </article>
          </div>
          <article class="task-respect-detail__prompt">
            <span>审核提示词</span>
            <p>{{ formatValue(aiReviewConfig.reviewPrompt, '暂无审核提示词') }}</p>
          </article>
          <el-table v-if="aiDimensions.length" :data="aiDimensions" stripe>
            <el-table-column prop="field" label="字段" />
            <el-table-column prop="weight" label="权重" />
            <el-table-column prop="passScore" label="通过分数" />
          </el-table>
        </section>
      </template>
        </section>

        <TaskItemsDetailPanel
          v-else-if="shouldShowItemDetails && activeTab === 'items'"
          key="items"
          :task-id="currentTaskId"
        />

        <TaskAiPreviewDetailPanel
          v-else
          key="aiPreview"
          :task-id="currentTaskId"
        />
      </Transition>
    </template>
  </el-dialog>
</template>

<style scoped>
.task-respect-detail__header,
.task-respect-detail__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.task-respect-detail__header span,
.task-respect-detail__section-header span {
  color: #94a3b8;
  font-size: 11px;
  font-weight: 850;
  letter-spacing: 0;
  text-transform: uppercase;
}

.task-respect-detail__header h2,
.task-respect-detail__section-header h3 {
  margin: 4px 0 0;
  color: #172033;
}

.task-respect-detail__header h2 {
  font-size: 20px;
}

.task-respect-detail__section-header h3 {
  font-size: 15px;
}

.task-respect-detail__section-header > strong {
  color: #172033;
  font-size: 25px;
}

.task-respect-detail__body {
  display: grid;
  max-height: min(72vh, 760px);
  gap: 14px;
  overflow: auto;
  padding-right: 4px;
}

.task-respect-detail__tabs {
  margin-top: -4px;
}

.task-respect-detail__tabs :deep(.el-tabs__header) {
  margin: 0 0 14px;
}

.task-respect-detail__tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e2e8f0;
}

.task-respect-detail__tabs :deep(.el-tabs__item) {
  color: #64748b;
  font-weight: 850;
}

.task-respect-detail__tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
}

.task-respect-detail__tabs :deep(.el-tabs__active-bar) {
  border-radius: 999px;
  background: #2563eb;
}

.task-respect-detail-tab-enter-active,
.task-respect-detail-tab-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.task-respect-detail-tab-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.task-respect-detail-tab-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.task-respect-detail__intro,
.task-respect-detail__section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  padding: 14px;
}

.task-respect-detail__intro {
  display: grid;
  gap: 10px;
  background: #f8fafc;
}

.task-respect-detail__intro p,
.task-respect-detail__prompt p {
  margin: 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.65;
}

.task-respect-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.task-respect-detail__section {
  display: grid;
  gap: 12px;
}

.task-respect-detail__progress-track {
  display: flex;
  overflow: hidden;
  height: 8px;
  border-radius: 999px;
  background: #edf2f7;
}

.task-respect-detail__progress-track span {
  display: block;
  height: 100%;
  transition: width 0.28s ease;
}

.task-respect-detail__metrics,
.task-respect-detail__grid {
  display: grid;
  gap: 8px;
}

.task-respect-detail__metrics {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.task-respect-detail__grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.task-respect-detail__metrics article,
.task-respect-detail__grid article {
  display: grid;
  min-width: 0;
  gap: 5px;
  border: 1px solid #edf1f5;
  border-radius: 6px;
  background: #f8fafc;
  padding: 10px;
}

.task-respect-detail__metrics i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.task-respect-detail__metrics span,
.task-respect-detail__grid span,
.task-respect-detail__prompt span {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.task-respect-detail__metrics strong,
.task-respect-detail__grid strong {
  overflow-wrap: anywhere;
  color: #1f2937;
  font-size: 14px;
  font-weight: 850;
}

.task-respect-detail__info-table {
  overflow: hidden;
  border: 1px solid #edf1f5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.035);
}

.task-respect-detail__info-table :deep(th.el-table__cell) {
  color: #334155;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  font-size: 13px;
  font-weight: 850;
}

.task-respect-detail__info-table :deep(.el-table__cell) {
  padding: 10px 0;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.task-respect-detail__info-table :deep(.el-table__row .el-table__cell:first-child) {
  background: rgba(248, 250, 252, 0.72);
}

.task-respect-detail__info-label {
  display: inline-flex;
  min-width: 82px;
  align-items: center;
  justify-content: center;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
  font-weight: 850;
  padding: 4px 10px;
}

.task-respect-detail__info-value {
  display: flex;
  min-width: 0;
  align-items: center;
  color: #334155;
  line-height: 1.6;
}

.task-respect-detail__info-value--text {
  overflow-wrap: anywhere;
  font-weight: 750;
}

.task-respect-detail__info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.task-respect-detail__info-tags :deep(.el-tag),
.task-respect-detail__info-value :deep(.el-tag) {
  border-radius: 999px;
  font-weight: 850;
}

.task-respect-detail__info-progress {
  display: grid;
  width: min(100%, 420px);
  grid-template-columns: 96px minmax(120px, 1fr) 42px;
  align-items: center;
  gap: 10px;
}

.task-respect-detail__info-progress strong,
.task-respect-detail__info-progress span {
  color: #334155;
  font-size: 12px;
  font-weight: 850;
}

.task-respect-detail__info-progress span {
  color: #64748b;
  text-align: right;
}

.task-respect-detail__info-progress :deep(.el-progress) {
  width: 100%;
}

.task-respect-detail__info-progress :deep(.el-progress-bar__outer) {
  height: 7px !important;
  background: #e8edf3;
}

.task-respect-detail__prompt {
  display: grid;
  gap: 6px;
  border: 1px solid #edf1f5;
  border-radius: 6px;
  background: #f8fafc;
  padding: 10px;
}

.task-respect-detail__error {
  margin: 0;
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fff1f2;
  color: #b91c1c;
  font-size: 13px;
  padding: 12px;
}

@media (max-width: 760px) {
  .task-respect-detail__metrics,
  .task-respect-detail__grid {
    grid-template-columns: 1fr;
  }
}
</style>
