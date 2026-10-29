<script setup lang="ts">
import { computed, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import TaskRespectDetailDialog from '../../components/tasks/TaskRespectDetailDialog.vue'
import { useTaskRespectDetail } from '../../composables/useTaskRespectDetail'
import { GetLabelerTasks } from '../../utils/api'

interface BackendTaskItem {
  id?: string | number
  taskId?: string | number
  datasetItemId?: string | number
  title?: string
  name?: string
  taskName?: string
  description?: string
  desc?: string
  deadline?: string
  quota?: string | number
  quote?: string | number
  availableCount?: string | number
  claimedCount?: string | number
  annotatedCount?: string | number
  status?: string
  annotationData?: unknown
  reviewComment?: string
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

interface MyTaskItem {
  id: string
  taskId: string
  datasetItemId: string
  title: string
  description: string
  status: string
  statusType: string
  deadline: string
  quotaText: string
  progressPercent: number
  progressColor: string
  availableCount: number
}

const router = useRouter()
const {
  isTaskDetailDialogOpen,
  isTaskDetailLoading,
  taskDetailErrorMessage,
  taskDetailTitle,
  taskDetail,
  detailLoadingTaskId,
  openTaskDetail,
} = useTaskRespectDetail()

const pageSize = 10

const tasks = shallowRef<MyTaskItem[]>([])
const activeStatus = shallowRef('ALL')
const currentPage = shallowRef(1)
const totalTasks = shallowRef(0)
const isLoading = shallowRef(false)
const errorMessage = shallowRef('')
const listAnimationKey = shallowRef(0)

const statusOptions = [
  { key: 'ALL', label: '全部' },
  { key: 'CLAIMED', label: '待标注' },
  { key: 'DRAFT', label: '草稿' },
  { key: 'SUBMITTED', label: '已提交' },
  { key: 'REJECTED', label: '返工' },
]

const filteredTasks = computed(() => {
  if (activeStatus.value === 'ALL') {
    return tasks.value
  }

  return tasks.value.filter((task) => task.statusType === activeStatus.value.toLowerCase())
})

const statusSummary = computed(() =>
  statusOptions.map((option) => ({
    ...option,
    count:
      option.key === 'ALL'
        ? tasks.value.length
        : tasks.value.filter((task) => task.statusType === option.key.toLowerCase()).length,
  })),
)

function getPayload(response: any) {
  return response?.data ?? response ?? {}
}

function assertApiSuccess(response: any) {
  const code = response?.code

  if (code !== undefined && Number(code) !== 200) {
    throw new Error(response?.message ?? response?.msg ?? '请求失败')
  }
}

function pickArray(response: any) {
  const payload = getPayload(response)
  const nestedPayload = payload?.data ?? payload
  const candidates = [
    nestedPayload?.records,
    nestedPayload?.items,
    nestedPayload?.list,
    payload?.records,
    payload?.items,
    payload?.list,
    nestedPayload,
  ]

  return candidates.find(Array.isArray) ?? []
}

function pickTotal(response: any, fallback: number) {
  const payload = getPayload(response)
  const nestedPayload = payload?.data ?? payload
  const total = Number(nestedPayload?.total ?? payload?.total ?? fallback)

  return Number.isFinite(total) ? total : fallback
}

function readString(value: unknown, fallback: string) {
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

function formatDate(value: unknown) {
  const text = readString(value, '')

  if (!text) {
    return '未设置'
  }

  return text.replace('T', ' ').split(' ')[0]
}

function formatNumberText(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('en-US') : '-'
}

function normalizeStatus(value: unknown, hasDraft: boolean) {
  const status = readString(value, 'CLAIMED').toUpperCase()

  if (status === 'CLAIMED' && hasDraft) {
    return {
      status: '草稿',
      statusType: 'draft',
    }
  }

  const statusTextMap: Record<string, string> = {
    CLAIMED: '待标注',
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    REJECTED: '返工',
    REVIEWED: '已审核',
    APPROVED: '已通过',
  }

  return {
    status: statusTextMap[status] ?? status,
    statusType: status.toLowerCase(),
  }
}

function normalizeTaskItem(record: unknown, index: number): MyTaskItem {
  const task = (record ?? {}) as BackendTaskItem
  const id = String(task.id ?? `item-${index}`)
  const taskId = String(task.taskId ?? id)
  const datasetItemId = String(task.datasetItemId ?? id)
  const hasDraft = Boolean(task.annotationData)
  const status = normalizeStatus(task.status, hasDraft)
  const quota = readNumber(task.quota ?? task.quote)
  const claimedCount = readNumber(task.claimedCount)
  const annotatedCount = readNumber(task.annotatedCount)
  const availableCount = readNumber(task.availableCount, Math.max(quota - claimedCount, 0))
  const progressPercent = claimedCount > 0 ? Math.min(100, Math.round((annotatedCount / claimedCount) * 100)) : 0

  return {
    id,
    taskId,
    datasetItemId,
    title: readString(task.title ?? task.name ?? task.taskName, `未命名任务 ${index + 1}`),
    description: readString(task.description ?? task.desc, '暂无简述'),
    status: status.status,
    statusType: status.statusType,
    deadline: formatDate(task.deadline),
    quotaText: `${formatNumberText(annotatedCount)} / ${formatNumberText(claimedCount)}`,
    progressPercent,
    progressColor: availableCount > 0 ? '#3b82f6' : '#22c55e',
    availableCount,
  }
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

async function loadTasks(pageNum = 1) {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await GetLabelerTasks({
      pageNum,
      pageSize,
    })
    assertApiSuccess(response)
    const normalizedTasks = pickArray(response).map((item, index) => normalizeTaskItem(item, index))

    tasks.value = normalizedTasks
    totalTasks.value = pickTotal(response, normalizedTasks.length)
    currentPage.value = pageNum
    listAnimationKey.value += 1
  } catch (error: any) {
    tasks.value = []
    totalTasks.value = 0
    errorMessage.value = readErrorMessage(error, '我的任务加载失败')
  } finally {
    isLoading.value = false
  }
}

function handleStatusChange(status: string) {
  if (activeStatus.value === status) {
    return
  }

  activeStatus.value = status
  listAnimationKey.value += 1
}

function handlePageChange(pageNum: number) {
  loadTasks(pageNum)
}

function handleOpenTask(task: MyTaskItem) {
  router.push({
    name: 'labeling-task',
    params: { taskId: task.taskId },
  })
}

function handleViewTask(task: MyTaskItem) {
  openTaskDetail(task.taskId, task.title, task.id)
}

onMounted(() => {
  loadTasks()
})
</script>

<template>
  <section class="my-tasks-page">
    <header class="my-tasks-page__header">
      <div>
        <p class="my-tasks-page__eyebrow">My Tasks</p>
        <h1>我的任务</h1>
        <p>集中查看已领取的数据项，继续标注草稿、提交中的任务或处理返工意见。</p>
      </div>

      <RouterLink class="my-tasks-page__marketplace" to="/workbench/marketplace">任务广场</RouterLink>
    </header>

    <section class="my-tasks-page__summary" aria-label="任务状态统计">
      <button
        v-for="item in statusSummary"
        :key="item.key"
        class="my-tasks-page__summary-card"
        :class="{ 'my-tasks-page__summary-card--active': activeStatus === item.key }"
        type="button"
        @click="handleStatusChange(item.key)"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
      </button>
    </section>

    <p v-if="errorMessage" class="my-tasks-page__error" role="alert">{{ errorMessage }}</p>

    <section class="my-tasks-page__table-shell">
      <div class="my-tasks-page__table-scroll">
        <div class="my-tasks-page__table">
          <div class="my-tasks-page__table-header">
            <span>任务</span>
            <span>状态</span>
            <span>截止时间</span>
            <span>已完成标注/已领取配额</span>
            <span>操作</span>
          </div>

          <Transition name="my-task-panel" mode="out-in" appear>
            <div
              :key="`${activeStatus}-${currentPage}-${listAnimationKey}`"
              class="my-tasks-page__table-state"
            >
              <div v-if="isLoading" class="my-tasks-page__empty">正在加载任务...</div>
              <div v-else-if="!filteredTasks.length" class="my-tasks-page__empty">暂无任务</div>

              <TransitionGroup
                v-else
                class="my-tasks-page__rows"
                name="my-task-row"
                tag="div"
              >
                <article
                  v-for="task in filteredTasks"
                  :key="`${task.id}-${activeStatus}-${listAnimationKey}`"
                  class="my-tasks-page__row"
                >
                  <div class="my-tasks-page__task-summary">
                    <h2 class="my-tasks-page__task-name">{{ task.title }}</h2>
                    <p class="my-tasks-page__task-desc">{{ task.description }}</p>
                  </div>

                  <div class="my-tasks-page__status-cell">
                    <span :class="`my-tasks-page__status my-tasks-page__status--${task.statusType}`">
                      {{ task.status }}
                    </span>
                  </div>

                  <div class="my-tasks-page__date-cell">
                    {{ task.deadline }}
                  </div>

                  <div class="my-tasks-page__progress-cell">
                    <strong>{{ task.quotaText }}</strong>
                    <el-progress
                      :color="task.progressColor"
                      :percentage="task.progressPercent"
                      :show-text="false"
                    />
                  </div>

                  <div class="my-tasks-page__actions">
                    <el-button size="small" type="primary" @click="handleOpenTask(task)">
                      {{ task.statusType === 'submitted' ? '查看任务' : '继续标注' }}
                    </el-button>
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :loading="detailLoadingTaskId === task.id"
                      @click="handleViewTask(task)"
                    >
                      查看详情
                    </el-button>
                  </div>
                </article>
              </TransitionGroup>
            </div>
          </Transition>
        </div>
      </div>
    </section>

    <TaskRespectDetailDialog
      v-model="isTaskDetailDialogOpen"
      audience="labeler"
      :detail="taskDetail"
      :error-message="taskDetailErrorMessage"
      :loading="isTaskDetailLoading"
      :title="taskDetailTitle"
    />

    <el-pagination
      v-if="totalTasks > pageSize"
      class="my-tasks-page__pagination"
      background
      layout="prev, pager, next"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="totalTasks"
      @current-change="handlePageChange"
    />
  </section>
</template>

<style scoped>
.my-tasks-page {
  display: grid;
  gap: 18px;
}

.my-tasks-page__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}

.my-tasks-page__eyebrow {
  margin: 0 0 8px;
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 800;
}

.my-tasks-page__header h1 {
  margin: 0;
  color: var(--lh-text-primary);
  font-size: clamp(32px, 5vw, 48px);
  line-height: 1.08;
}

.my-tasks-page__header p {
  max-width: 720px;
  margin: 12px 0 0;
  color: var(--lh-text-secondary);
  line-height: 1.7;
}

.my-tasks-page__marketplace {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  border: 1px solid var(--lh-border-soft);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--lh-text-primary);
  font-weight: 750;
  padding: 0 16px;
}

.my-tasks-page__summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.my-tasks-page__summary-card {
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--lh-shadow-card);
  backdrop-filter: blur(20px);
}

.my-tasks-page__summary-card {
  display: grid;
  gap: 8px;
  min-height: 96px;
  cursor: pointer;
  padding: 16px;
  text-align: left;
  transition:
    border-color 0.22s ease,
    background-color 0.22s ease,
    box-shadow 0.22s ease,
    transform 0.22s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.my-tasks-page__summary-card:hover {
  transform: translateY(-1px);
}

.my-tasks-page__summary-card--active {
  border-color: rgba(0, 113, 227, 0.32);
  background: var(--lh-accent-soft);
  box-shadow: 0 14px 30px rgba(0, 113, 227, 0.1);
  transform: translateY(-2px);
}

.my-tasks-page__summary-card span {
  color: var(--lh-text-tertiary);
  font-size: 12px;
  font-weight: 750;
}

.my-tasks-page__summary-card strong {
  color: var(--lh-text-primary);
  font-size: 28px;
  line-height: 1;
}

.my-tasks-page__error {
  margin: 0;
  border: 1px solid rgba(255, 59, 48, 0.24);
  border-radius: 8px;
  background: rgba(255, 59, 48, 0.08);
  color: #9f1f18;
  font-weight: 700;
  padding: 12px 14px;
}

.my-tasks-page__table-shell {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.my-tasks-page__table-scroll {
  overflow: hidden;
}

.my-tasks-page__table {
  min-width: 980px;
}

.my-tasks-page__table-header,
.my-tasks-page__row {
  display: grid;
  grid-template-columns:
    minmax(220px, 1fr)
    minmax(120px, 0.75fr)
    minmax(120px, 0.75fr)
    minmax(190px, 1.1fr)
    minmax(320px, 1.55fr);
}

.my-tasks-page__table-header {
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #e5e7eb;
  color: #526173;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 800;
}

.my-tasks-page__table-header span {
  padding: 0 10px;
}

.my-tasks-page__table-state {
  min-height: 312px;
  background: #ffffff;
}

.my-tasks-page__rows {
  display: grid;
  gap: 0;
  padding: 0;
  background: #ffffff;
}

.my-tasks-page__row {
  min-height: 76px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  background: #ffffff;
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.my-tasks-page__row:hover {
  background: #fbfdff;
  box-shadow: inset 3px 0 0 #409eff;
}

.my-tasks-page__task-summary,
.my-tasks-page__status-cell,
.my-tasks-page__date-cell,
.my-tasks-page__progress-cell,
.my-tasks-page__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  padding: 12px 10px;
}

.my-tasks-page__task-summary {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.my-tasks-page__task-name {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.my-tasks-page__task-desc {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.my-tasks-page__status {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  color: #4b5563;
  background: #eef2f7;
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.my-tasks-page__status::before {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.my-tasks-page__status--claimed,
.my-tasks-page__status--draft,
.my-tasks-page__status--reworking {
  color: #2563eb;
  background: #dbeafe;
}

.my-tasks-page__status--submitted,
.my-tasks-page__status--reviewed,
.my-tasks-page__status--approved {
  color: #16a34a;
  background: #dcfce7;
}

.my-tasks-page__status--rejected {
  color: #f97316;
  background: #fff7ed;
}

.my-tasks-page__date-cell,
.my-tasks-page__progress-cell {
  justify-content: flex-start;
}

.my-tasks-page__date-cell {
  justify-content: flex-start;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.my-tasks-page__progress-cell {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.my-tasks-page__progress-cell strong {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.my-tasks-page__progress-cell :deep(.el-progress) {
  width: 100%;
}

.my-tasks-page__progress-cell :deep(.el-progress-bar__outer) {
  height: 5px !important;
  background: #e8edf3;
}

.my-tasks-page__actions {
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
}

.my-tasks-page__actions :deep(.el-button) {
  margin-left: 0;
  border-radius: 6px;
  min-height: 32px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 700;
}

.my-tasks-page__empty {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  color: #8b98a8;
  font-size: 15px;
}

.my-tasks-page__pagination {
  justify-self: end;
}

.my-task-panel-enter-active,
.my-task-panel-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.my-task-panel-enter-from,
.my-task-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.992);
}

.my-task-row-enter-active,
.my-task-row-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.my-task-row-move {
  transition: transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.my-task-row-enter-from,
.my-task-row-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.992);
}

@media (max-width: 980px) {
  .my-tasks-page__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .my-tasks-page__table-scroll {
    overflow-x: auto;
  }

}

@media (max-width: 640px) {
  .my-tasks-page__header {
    align-items: stretch;
    flex-direction: column;
  }

  .my-tasks-page__summary {
    grid-template-columns: 1fr;
  }
}
</style>
