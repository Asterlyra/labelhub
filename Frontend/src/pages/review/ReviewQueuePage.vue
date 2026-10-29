<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import TaskRespectDetailDialog from '../../components/tasks/TaskRespectDetailDialog.vue'
import { useTaskRespectDetail } from '../../composables/useTaskRespectDetail'
import loadingAnimation from '../../icon/loading.gif'
import { GetReviewTasks, ReviewTask } from '../../utils/api'

interface ReviewTaskListItem {
  id: string
  detailId: string
  reviewId: string
  name: string
  description: string
  status: string
  statusType: string
  deadlineText: string
  quotaText: string
  approvedCount: number
  rejectedCount: number
  totalCount: number
  approvedPercent: number
  rejectedPercent: number
  tags: string[]
}

interface BackendReviewTaskRecord {
  id?: string | number
  taskId?: string | number
  reviewTaskId?: string | number
  taskItemId?: string | number
  itemId?: string | number
  reviewItemId?: string | number
  taskTitle?: string
  taskDescription?: string
  title?: string
  name?: string
  taskName?: string
  description?: string
  desc?: string
  status?: string
  creatorName?: string
  ownerName?: string
  reviewerName?: string
  labelerName?: string
  nickname?: string
  createBy?: string
  deadline?: string
  dueTime?: string
  createTime?: string
  createdAt?: string
  updateTime?: string
  updatedAt?: string
  approvedCount?: string | number
  rejectedCount?: string | number
  totalCount?: string | number
  itemCount?: string | number
  sampleCount?: string | number
  progress?: Record<string, unknown>
  tags?: unknown
  category?: string
  scene?: string
  task?: Record<string, unknown>
  taskInfo?: Record<string, unknown>
  [key: string]: unknown
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

const reviewPageSize = 8
const reviewTabs = [{ key: 'all', label: '审核列表' }]

const reviewTasks = shallowRef<ReviewTaskListItem[]>([])
const isLoadingReviewTasks = shallowRef(false)
const reviewErrorMessage = shallowRef('')
const currentPage = shallowRef(1)
const totalReviewTasks = shallowRef(0)
const listAnimationKey = shallowRef(0)
const isBlockingLoadingOpen = shallowRef(false)
const enteringReviewId = shallowRef('')

const reviewCountText = computed(() =>
  isLoadingReviewTasks.value ? '加载中' : `共 ${totalReviewTasks.value} 个审核任务`,
)

const shouldShowInlineLoading = computed(() => isLoadingReviewTasks.value && reviewTasks.value.length === 0)
const taskDetailDialogTaskId = computed(() => {
  const id = taskDetail.value?.id ?? taskDetail.value?.taskId

  return typeof id === 'string' || typeof id === 'number' ? id : null
})
const loadingTextChars = '正在加载中.....'.split('')

let reviewListRequestSeed = 0
let blockingLoadingSeed = 0

function getResponsePayload(response: any) {
  return response?.data ?? response
}

function pickReviewTaskArray(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload

  const candidates = [
    payload,
    nestedPayload,
    payload?.records,
    payload?.list,
    payload?.items,
    payload?.content,
    nestedPayload?.records,
    nestedPayload?.list,
    nestedPayload?.items,
    nestedPayload?.content,
  ]

  return candidates.find(Array.isArray) ?? []
}

function pickPaginationTotal(response: any, fallback: number) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload
  const candidates = [
    payload?.total,
    payload?.totalCount,
    payload?.totalElements,
    payload?.count,
    nestedPayload?.total,
    nestedPayload?.totalCount,
    nestedPayload?.totalElements,
    nestedPayload?.count,
  ]
  const total = candidates.map((item) => Number(item)).find((item) => Number.isFinite(item))

  return total ?? fallback
}

function readString(value: unknown, fallback: string) {
  if (value === undefined || value === null) {
    return fallback
  }

  const text = String(value).trim()

  return text ? text : fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : fallback
}

function readTags(record: BackendReviewTaskRecord) {
  if (Array.isArray(record.tags)) {
    return record.tags.filter((tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()))
  }

  if (typeof record.tags === 'string' && record.tags.trim()) {
    try {
      const parsedTags = JSON.parse(record.tags)

      if (Array.isArray(parsedTags)) {
        return parsedTags.filter((tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()))
      }
    } catch {
      return record.tags
        .split(/[,，]/)
        .map((tag) => tag.trim())
        .filter(Boolean)
    }
  }

  return [record.scene, record.category].filter(
    (tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()),
  )
}

function formatStatus(status: unknown) {
  const statusTextMap: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    PAUSED: '已暂停',
    REVIEWING: '审核中',
    ENDED: '已结束',
    UNASSIGNED: '未领取',
    CLAIMED: '已领取',
    SUBMITTED: '待审核',
    REJECTED: '已驳回',
    REVIEWED: '已审核',
    APPROVED: '已通过',
    PENDING: '待审核',
  }
  const normalizedStatus = readString(status, 'PENDING').toUpperCase()

  return statusTextMap[normalizedStatus] ?? normalizedStatus
}

function getStatusType(status: unknown) {
  const normalizedStatus = readString(status, 'pending').toLowerCase()
  const knownStatus = [
    'draft',
    'published',
    'paused',
    'reviewing',
    'ended',
    'claimed',
    'submitted',
    'rejected',
    'reviewed',
    'approved',
    'pending',
  ]

  return knownStatus.includes(normalizedStatus) ? normalizedStatus : 'default'
}

function formatTaskDate(value: unknown) {
  const text = readString(value, '')

  if (!text) {
    return '暂无截止时间'
  }

  return text.includes('T') ? text.split('T')[0] : text
}

function formatNumberText(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('en-US') : '-'
}

function readReviewProgress(record: BackendReviewTaskRecord) {
  const progress = (record.progress && typeof record.progress === 'object' ? record.progress : {}) as Record<
    string,
    unknown
  >
  const approvedCount = readNumber(progress.approvedCount ?? record.approvedCount, 0)
  const rejectedCount = readNumber(progress.rejectedCount ?? record.rejectedCount, 0)
  const fallbackTotal = approvedCount + rejectedCount
  const totalCount = readNumber(
    progress.totalCount ?? record.totalCount ?? record.itemCount ?? record.sampleCount,
    fallbackTotal,
  )
  const approvedPercent = totalCount > 0 ? Math.min(100, (approvedCount / totalCount) * 100) : 0
  const rejectedPercent =
    totalCount > 0 ? Math.min(Math.max(0, 100 - approvedPercent), (rejectedCount / totalCount) * 100) : 0

  return {
    approvedCount,
    rejectedCount,
    totalCount,
    approvedPercent,
    rejectedPercent,
    quotaText:
      totalCount > 0
        ? `${formatNumberText(approvedCount)} / ${formatNumberText(rejectedCount)} / ${formatNumberText(totalCount)}`
        : '-',
  }
}

function normalizeReviewTaskRecord(record: unknown, index: number): ReviewTaskListItem {
  const rawRecord = (record ?? {}) as BackendReviewTaskRecord
  const nestedTask = (
    rawRecord.task && typeof rawRecord.task === 'object'
      ? rawRecord.task
      : rawRecord.taskInfo && typeof rawRecord.taskInfo === 'object'
        ? rawRecord.taskInfo
        : {}
  ) as BackendReviewTaskRecord
  const task = { ...nestedTask, ...rawRecord } as BackendReviewTaskRecord
  const rowId = String(task.id ?? task.taskItemId ?? task.itemId ?? task.taskId ?? `review-task-${index}`)
  const reviewId = String(task.taskId ?? task.reviewTaskId ?? task.id ?? rowId)
  const detailId = String(task.taskItemId ?? task.itemId ?? task.reviewItemId ?? task.id ?? reviewId)
  const progress = readReviewProgress(task)
  const deadlineText = formatTaskDate(task.deadline ?? task.dueTime ?? task.createTime ?? task.createdAt)

  return {
    id: rowId,
    detailId,
    reviewId,
    name: readString(task.taskTitle ?? task.title ?? task.name ?? task.taskName, `未命名审核任务 ${index + 1}`),
    description: readString(task.taskDescription ?? task.description ?? task.desc, '暂无任务描述'),
    status: formatStatus(task.status),
    statusType: getStatusType(task.status),
    deadlineText,
    quotaText: progress.quotaText,
    approvedCount: progress.approvedCount,
    rejectedCount: progress.rejectedCount,
    totalCount: progress.totalCount,
    approvedPercent: progress.approvedPercent,
    rejectedPercent: progress.rejectedPercent,
    tags: readTags(task),
  }
}

function normalizeReviewTaskList(response: any) {
  return pickReviewTaskArray(response).map((item, index) => normalizeReviewTaskRecord(item, index))
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

function waitForLoadingDuration(duration = 720) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, duration)
  })
}

function openBlockingLoading() {
  blockingLoadingSeed += 1
  const seed = blockingLoadingSeed

  isBlockingLoadingOpen.value = true

  return {
    seed,
    openedAt: Date.now(),
  }
}

async function closeBlockingLoading(handle: { seed: number; openedAt: number }) {
  const elapsed = Date.now() - handle.openedAt

  if (elapsed < 720) {
    await waitForLoadingDuration(720 - elapsed)
  }

  if (handle.seed === blockingLoadingSeed) {
    isBlockingLoadingOpen.value = false
  }
}

async function loadReviewTasks(pageNum = currentPage.value, showBlockingLoading = false) {
  const requestSeed = (reviewListRequestSeed += 1)
  const blockingLoadingHandle = showBlockingLoading ? openBlockingLoading() : null

  isLoadingReviewTasks.value = true
  reviewErrorMessage.value = ''

  try {
    const response = await GetReviewTasks({
      pageNum,
      pageSize: reviewPageSize,
    })
    const normalizedTasks = normalizeReviewTaskList(response)

    if (requestSeed !== reviewListRequestSeed) {
      return
    }

    reviewTasks.value = normalizedTasks.slice(0, reviewPageSize)
    totalReviewTasks.value = pickPaginationTotal(response, normalizedTasks.length)
    currentPage.value = pageNum
    listAnimationKey.value += 1
  } catch (error: any) {
    if (requestSeed !== reviewListRequestSeed) {
      return
    }

    reviewTasks.value = []
    totalReviewTasks.value = 0
    reviewErrorMessage.value = readErrorMessage(error, '审核列表加载失败')
  } finally {
    if (blockingLoadingHandle) {
      await closeBlockingLoading(blockingLoadingHandle)
    }

    if (requestSeed === reviewListRequestSeed) {
      isLoadingReviewTasks.value = false
    }
  }
}

function handlePageChange(pageNum: number) {
  loadReviewTasks(pageNum, true)
}

async function handleViewDetail(task: ReviewTaskListItem) {
  await openTaskDetail(task.reviewId, task.name, task.id)
}

async function handleEnterReview(task: ReviewTaskListItem) {
  enteringReviewId.value = task.reviewId

  try {
    const response = await ReviewTask(task.reviewId)
    const payload = getResponsePayload(response)?.data ?? getResponsePayload(response)

    try {
      window.sessionStorage.setItem(`labelhub.review.workspace.${task.reviewId}`, JSON.stringify(payload ?? {}))
    } catch {
      // 浏览器存储不可用时不阻塞进入审核页。
    }

    router.push({
      name: 'review-task',
      params: { taskId: task.reviewId },
    })
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '进入审核失败'))
  } finally {
    enteringReviewId.value = ''
  }
}

onMounted(() => {
  loadReviewTasks(1)
})
</script>

<template>
  <section class="owner-task-page review-task-page">
    <header class="owner-task-page__header">
      <div class="review-task-page__header-content">
        <div class="review-task-page__header-avatar">
          <img src="../../icon/气球.gif" alt="">
        </div>
        <div class="review-task-page__header-copy">
          <p class="owner-task-page__eyebrow">审核列表</p>
          <h1 class="owner-task-page__title">审核列表</h1>
          <p class="owner-task-page__description">查看待审核任务，查看详情并进入审核工作台。</p>
        </div>
      </div>
    </header>

    <section class="owner-task-page__toolbar">
      <div class="owner-task-page__tabs" aria-label="审核列表范围">
        <button
          v-for="tab in reviewTabs"
          :key="tab.key"
          class="owner-task-page__tab owner-task-page__tab--active"
          type="button"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="owner-task-page__toolbar-actions">
        <span class="owner-task-page__count">{{ reviewCountText }}</span>
      </div>
    </section>

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

    <p v-if="reviewErrorMessage" class="owner-task-page__error">{{ reviewErrorMessage }}</p>

    <section class="owner-task-page__table-shell">
      <div class="owner-task-page__table-scroll">
        <div class="owner-task-page__table">
          <div class="owner-task-page__table-header">
            <span>任务</span>
            <span>状态</span>
            <span>截止时间</span>
            <span>已审核 / 配额</span>
            <span>操作</span>
          </div>

          <Transition name="owner-task-panel" mode="out-in">
            <div
              :key="`review-${currentPage}-${listAnimationKey}`"
              class="owner-task-page__table-state"
            >
              <div v-if="shouldShowInlineLoading" class="owner-task-page__empty">正在加载审核任务...</div>
              <div v-else-if="!reviewTasks.length" class="owner-task-page__empty">暂无审核任务</div>

              <TransitionGroup
                v-else
                class="owner-task-page__rows"
                name="owner-task-row"
                tag="div"
              >
                <article
                  v-for="task in reviewTasks"
                  :key="`review-${task.id}-${listAnimationKey}`"
                  class="owner-task-page__row"
                >
                  <div class="owner-task-page__task-summary">
                    <h2 class="owner-task-page__task-name">{{ task.name }}</h2>
                    <div class="owner-task-page__task-meta">
                      <span class="owner-task-page__task-description">{{ task.description }}</span>
                      <el-tag
                        v-for="tag in task.tags"
                        :key="tag"
                        type="warning"
                      >
                        {{ tag }}
                      </el-tag>
                    </div>
                  </div>

                  <div class="owner-task-page__status-cell">
                    <em
                      class="owner-task-page__status-badge"
                      :class="`owner-task-page__status-badge--${task.statusType}`"
                    >
                      {{ task.status }}
                    </em>
                  </div>

                  <div class="owner-task-page__strategy-cell">
                    {{ task.deadlineText }}
                  </div>

                  <div class="owner-task-page__progress-cell">
                    <strong>{{ task.quotaText }}</strong>
                    <div
                      class="review-task-page__progress"
                      aria-label="通过、驳回、总数进度"
                    >
                      <span
                        class="review-task-page__progress-approved"
                        :style="{ width: `${task.approvedPercent}%` }"
                      ></span>
                      <span
                        class="review-task-page__progress-rejected"
                        :style="{ width: `${task.rejectedPercent}%` }"
                      ></span>
                    </div>
                    <div class="review-task-page__progress-legend">
                      <span class="review-task-page__legend-approved">通过 {{ task.approvedCount }}</span>
                      <span class="review-task-page__legend-rejected">驳回 {{ task.rejectedCount }}</span>
                      <span>总计 {{ task.totalCount }}</span>
                    </div>
                  </div>

                  <div class="owner-task-page__actions">
                    <el-button
                      size="small"
                      type="primary"
                      :loading="detailLoadingTaskId === task.id"
                      @click="handleViewDetail(task)"
                    >
                      查看详情
                    </el-button>
                    <el-button
                      size="small"
                      type="success"
                      :loading="enteringReviewId === task.reviewId"
                      @click="handleEnterReview(task)"
                    >
                      进入审核
                    </el-button>
                  </div>
                </article>
              </TransitionGroup>
            </div>
          </Transition>
        </div>
      </div>
    </section>

    <footer class="owner-task-page__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        background
        layout="total, prev, pager, next, jumper"
        :page-size="reviewPageSize"
        :total="totalReviewTasks"
        @current-change="handlePageChange"
      />
    </footer>

    <TaskRespectDetailDialog
      v-model="isTaskDetailDialogOpen"
      audience="reviewer"
      :detail="taskDetail"
      :error-message="taskDetailErrorMessage"
      :loading="isTaskDetailLoading"
      :show-ai-preview-details="true"
      :task-id="taskDetailDialogTaskId"
      :title="taskDetailTitle"
    />
  </section>
</template>

<style scoped>
.owner-task-page {
  display: flex;
  min-height: calc(100vh - 104px);
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  color: #1f2937;
  background: #f3f6f9;
}

.review-task-page__header-content {
  position: relative;
  display: flex;
  width: 600px;
  flex-wrap: wrap;
}

.review-task-page__header-avatar {
  display: flex;
  width: 110px;
  justify-content: center;
  border-radius: 100px;
}

.review-task-page__header-avatar img {
  width: 100px;
  border: 3px #bfbfbf dashed;
  border-radius: 62px;
}

.review-task-page__header-copy {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.owner-task-page__eyebrow {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 700;
  color: #4f6680;
}

.owner-task-page__title {
  margin: 0;
  font-size: 26px;
  line-height: 1.25;
  color: #1f2937;
}

.owner-task-page__description {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.owner-task-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.owner-task-page__tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
}

.owner-task-page__tab {
  min-width: 96px;
  height: 34px;
  border: 0;
  border-radius: 6px;
  color: #1296db;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
}

.owner-task-page__tab--active {
  color: #ffffff;
  background: #172033;
  font-weight: 700;
}

.owner-task-page__count {
  color: #6b7280;
  font-size: 14px;
}

.owner-task-page__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.owner-task-page__error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  background: #fff1f2;
  font-size: 14px;
}

.template-designer__loading-backdrop {
  position: fixed;
  z-index: 48;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(20, 32, 54, 0.2);
  backdrop-filter: blur(18px);
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
  color: var(--lh-text-primary, #1f2937);
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

.owner-task-page__table-shell {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.owner-task-page__table-scroll {
  overflow: hidden;
}

.owner-task-page__table {
  min-width: 980px;
}

.owner-task-page__table-header,
.owner-task-page__row {
  display: grid;
  grid-template-columns:
    minmax(220px, 1fr)
    minmax(120px, 0.75fr)
    minmax(120px, 0.75fr)
    minmax(190px, 1.1fr)
    minmax(260px, 1.25fr);
}

.owner-task-page__table-header {
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #e5e7eb;
  color: #526173;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 800;
}

.owner-task-page__table-header span {
  padding: 0 10px;
}

.owner-task-page__table-state {
  min-height: 312px;
  background: #ffffff;
}

.owner-task-page__rows {
  display: grid;
  gap: 0;
  padding: 0;
  background: #ffffff;
}

.owner-task-page__row {
  min-height: 76px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  background: #ffffff;
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.owner-task-page__row:hover {
  background: #fbfdff;
  box-shadow: inset 3px 0 0 #409eff;
}

.owner-task-page__task-summary,
.owner-task-page__status-cell,
.owner-task-page__strategy-cell,
.owner-task-page__progress-cell,
.owner-task-page__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  padding: 12px 10px;
}

.owner-task-page__task-summary {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.owner-task-page__task-name {
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

.owner-task-page__task-meta {
  display: flex;
  width: 100%;
  max-width: 100%;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
}

.owner-task-page__task-description {
  display: -webkit-box;
  width: 100%;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.owner-task-page__status-badge {
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

.owner-task-page__status-badge::before {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.owner-task-page__status-badge--draft {
  color: #64748b;
  background: #eef2f7;
}

.owner-task-page__status-badge--published,
.owner-task-page__status-badge--reviewed,
.owner-task-page__status-badge--approved {
  color: #16a34a;
  background: #dcfce7;
}

.owner-task-page__status-badge--paused,
.owner-task-page__status-badge--pending {
  color: #f97316;
  background: #fff7ed;
}

.owner-task-page__status-badge--reviewing,
.owner-task-page__status-badge--claimed,
.owner-task-page__status-badge--submitted {
  color: #2563eb;
  background: #dbeafe;
}

.owner-task-page__status-badge--ended {
  color: #64748b;
  background: #f1f5f9;
}

.owner-task-page__status-badge--rejected {
  color: #b91c1c;
  background: #ffe4e6;
}

.owner-task-page__strategy-cell {
  justify-content: flex-start;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.owner-task-page__progress-cell {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.owner-task-page__progress-cell strong {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.review-task-page__progress {
  display: flex;
  overflow: hidden;
  width: 100%;
  height: 7px;
  border-radius: 999px;
  background: #e8edf3;
}

.review-task-page__progress-approved,
.review-task-page__progress-rejected {
  display: block;
  height: 100%;
  transition: width 0.28s ease;
}

.review-task-page__progress-approved {
  background: #22c55e;
}

.review-task-page__progress-rejected {
  background: #ef4444;
}

.review-task-page__progress-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  color: #94a3b8;
  font-size: 11px;
  font-weight: 700;
}

.review-task-page__legend-approved {
  color: #16a34a;
}

.review-task-page__legend-rejected {
  color: #dc2626;
}

.owner-task-page__actions {
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
}

.owner-task-page__actions :deep(.el-button) {
  margin-left: 0;
  border-radius: 6px;
  font-weight: 700;
}

.owner-task-page__empty {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  color: #8b98a8;
  font-size: 15px;
}

.owner-task-page__pagination {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding: 14px 0 0;
  background: #f3f6f9;
}

.owner-task-panel-enter-active,
.owner-task-panel-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-panel-enter-from,
.owner-task-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.992);
}

.owner-task-row-enter-active,
.owner-task-row-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-row-move {
  transition: transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.owner-task-row-enter-from,
.owner-task-row-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.992);
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

@media (max-width: 768px) {
  .owner-task-page {
    padding: 16px;
  }

  .review-task-page__header-content {
    width: 100%;
  }

  .owner-task-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .owner-task-page__tabs {
    width: 100%;
  }

  .owner-task-page__tab {
    flex: 1;
  }

  .owner-task-page__toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .owner-task-page__pagination {
    justify-content: center;
    overflow-x: auto;
  }

}
</style>
