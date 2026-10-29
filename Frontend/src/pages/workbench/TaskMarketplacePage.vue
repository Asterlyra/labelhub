<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, shallowRef } from 'vue'
import TaskRespectDetailDialog from '../../components/tasks/TaskRespectDetailDialog.vue'
import { useTaskRespectDetail } from '../../composables/useTaskRespectDetail'
import headerIcon from '../../icon/气球.gif'
import loadingAnimation from '../../icon/loading.gif'
import { GetTaskMarket, GetTaskMarketTags, PostMarketTask } from '../../utils/api'

interface BackendMarketplaceTask {
  id?: string | number
  taskId?: string | number
  title?: string
  name?: string
  taskName?: string
  description?: string
  desc?: string
  tags?: unknown
  quota?: string | number
  totalCount?: string | number
  claimedCount?: string | number
  availableCount?: string | number
  rewardRule?: string
  deadline?: string
  templateName?: string
  templateId?: string | number
  creatorName?: string
  creatorId?: string | number
  createTime?: string
  createdAt?: string
  updateTime?: string
  updatedAt?: string
  [key: string]: unknown
}

interface MarketplaceTask {
  id: string
  claimId: string
  taskCode: string
  name: string
  ownerText: string
  templateName: string
  status: string
  statusType: string
  quotaText: string
  progressPercent: number
  progressColor: string
  availableCount: number
  createdAt: string
  tags: string[]
}

const taskPageSize = 8

const {
  isTaskDetailDialogOpen,
  isTaskDetailLoading,
  taskDetailErrorMessage,
  taskDetailTitle,
  taskDetail,
  detailLoadingTaskId,
  openTaskDetail,
} = useTaskRespectDetail()

const selectedTag = shallowRef('')
const tasks = shallowRef<MarketplaceTask[]>([])
const marketplaceTags = shallowRef<string[]>([])
const currentPage = shallowRef(1)
const totalTasks = shallowRef(0)
const isLoadingTasks = shallowRef(false)
const taskErrorMessage = shallowRef('')
const isFilterPanelOpen = shallowRef(false)
const claimingTaskId = shallowRef('')
const listAnimationKey = shallowRef(0)
const isBlockingLoadingOpen = shallowRef(false)

const emptyTaskText = computed(() =>
  selectedTag.value ? `暂无“${selectedTag.value}”标签任务` : '暂无任务',
)

const shouldShowInlineLoading = computed(() => isLoadingTasks.value && tasks.value.length === 0)
const hasActiveTagFilter = computed(() => Boolean(selectedTag.value))

const loadingTextChars = '正在加载中....'.split('')

let taskListRequestSeed = 0
let blockingLoadingSeed = 0

function getResponsePayload(response: any) {
  return response?.data ?? response ?? {}
}

function assertApiSuccess(response: any) {
  const code = response?.code

  if (code !== undefined && Number(code) !== 200) {
    throw new Error(response?.message ?? response?.msg ?? '请求失败')
  }
}

function pickTaskArray(response: any) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload
  const candidates = [
    nestedPayload?.records,
    nestedPayload?.list,
    nestedPayload?.items,
    nestedPayload?.content,
    payload?.records,
    payload?.list,
    payload?.items,
    payload?.content,
    nestedPayload,
  ]

  return candidates.find(Array.isArray) ?? []
}

function pickPaginationTotal(response: any, fallback: number) {
  const payload = getResponsePayload(response)
  const nestedPayload = payload?.data ?? payload
  const candidates = [
    nestedPayload?.total,
    nestedPayload?.totalCount,
    nestedPayload?.totalElements,
    payload?.total,
    payload?.totalCount,
    payload?.totalElements,
    payload?.count,
  ]
  const total = candidates.map((item) => Number(item)).find((item) => Number.isFinite(item))

  return total ?? fallback
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

function readTags(value: unknown) {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }

  if (typeof value === 'string') {
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  }

  return []
}

function formatTaskCode(value: unknown) {
  const text = readString(value, '')
  const numberValue = Number(text)

  if (Number.isFinite(numberValue)) {
    return `T-${String(numberValue).padStart(4, '0')}`
  }

  return text ? `T-${text}` : 'T-0000'
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

function normalizeTaskRecord(record: unknown, index: number): MarketplaceTask {
  const task = (record ?? {}) as BackendMarketplaceTask
  const rowId = String(task.id ?? task.taskId ?? `market-task-${index}`)
  const taskId = String(task.taskId ?? task.id ?? rowId)
  const claimedCount = readNumber(task.claimedCount)
  const availableCount = readNumber(task.availableCount)
  const configuredQuota = readNumber(task.quota)
  // quota=0 表示任务不限额，列表展示的总额应优先使用实际任务项总数。
  const derivedTotalCount = claimedCount + availableCount
  const explicitTotalCount = readNumber(task.totalCount)
  const totalCount = explicitTotalCount > 0
    ? explicitTotalCount
    : derivedTotalCount > 0
      ? derivedTotalCount
      : configuredQuota
  const progressPercent = totalCount > 0 ? Math.min(100, Math.round((claimedCount / totalCount) * 100)) : 0

  return {
    id: rowId,
    claimId: taskId,
    taskCode: formatTaskCode(taskId),
    name: readString(task.title ?? task.name ?? task.taskName, `未命名任务 ${index + 1}`),
    ownerText: readString(task.creatorName ?? task.creatorId, '未知管理员'),
    templateName: readString(task.templateName ?? task.templateId, '未绑定模板'),
    status: availableCount > 0 ? '可领取' : '已领完',
    statusType: availableCount > 0 ? 'published' : 'ended',
    quotaText: `${formatNumberText(claimedCount)} / ${formatNumberText(totalCount)}`,
    progressPercent,
    progressColor: availableCount > 0 ? '#3b82f6' : '#22c55e',
    availableCount,
    createdAt: formatDate(task.createTime ?? task.createdAt ?? task.updateTime ?? task.updatedAt),
    tags: readTags(task.tags),
  }
}

function normalizeTaskList(response: any) {
  return pickTaskArray(response).map((item, index) => normalizeTaskRecord(item, index))
}

function readErrorMessage(error: any, fallback: string) {
  return error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? fallback
}

function buildTaskQuery(pageNum: number) {
  return {
    tags: selectedTag.value || undefined,
    pageNum,
    pageSize: taskPageSize,
  }
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

async function loadTasks(pageNum = currentPage.value, showBlockingLoading = false) {
  const requestSeed = (taskListRequestSeed += 1)
  const blockingLoadingHandle = showBlockingLoading ? openBlockingLoading() : null

  isLoadingTasks.value = true
  taskErrorMessage.value = ''

  try {
    const response = await GetTaskMarket(buildTaskQuery(pageNum))
    assertApiSuccess(response)
    const normalizedTasks = normalizeTaskList(response)

    if (requestSeed !== taskListRequestSeed) {
      return
    }

    tasks.value = normalizedTasks.slice(0, taskPageSize)
    totalTasks.value = pickPaginationTotal(response, normalizedTasks.length)
    currentPage.value = pageNum
    listAnimationKey.value += 1
  } catch (error: any) {
    if (requestSeed !== taskListRequestSeed) {
      return
    }

    tasks.value = []
    totalTasks.value = 0
    taskErrorMessage.value = readErrorMessage(error, '任务广场加载失败')
  } finally {
    if (blockingLoadingHandle) {
      await closeBlockingLoading(blockingLoadingHandle)
    }

    if (requestSeed === taskListRequestSeed) {
      isLoadingTasks.value = false
    }
  }
}

async function loadMarketplaceTags() {
  try {
    const response = await GetTaskMarketTags()
    assertApiSuccess(response)
    marketplaceTags.value = pickTaskArray(response).map((tag) => String(tag).trim()).filter(Boolean)
  } catch {
    marketplaceTags.value = []
  }
}

function handleTagChange(tag: string) {
  selectedTag.value = selectedTag.value === tag ? '' : tag
  currentPage.value = 1
  tasks.value = []
  totalTasks.value = 0
  isFilterPanelOpen.value = false
  loadTasks(1, true)
}

function handleResetTaskList() {
  if (!selectedTag.value && !isFilterPanelOpen.value) {
    return
  }

  selectedTag.value = ''
  currentPage.value = 1
  tasks.value = []
  totalTasks.value = 0
  isFilterPanelOpen.value = false
  loadTasks(1, true)
}

function toggleFilterPanel() {
  isFilterPanelOpen.value = !isFilterPanelOpen.value
}

function handlePageChange(pageNum: number) {
  loadTasks(pageNum, true)
}

function handleViewTask(task: MarketplaceTask) {
  void openTaskDetail(task.claimId, task.name, task.id)
}

async function handleClaimTask(task: MarketplaceTask) {
  claimingTaskId.value = task.id

  try {
    const response = await PostMarketTask(task.claimId)
    assertApiSuccess(response)
    ElMessage.success('领取任务成功')
    await loadTasks(currentPage.value, true)
  } catch (error: any) {
    ElMessage.error(readErrorMessage(error, '领取任务失败'))
  } finally {
    claimingTaskId.value = ''
  }
}

onMounted(() => {
  loadMarketplaceTags()
  loadTasks(1)
})
</script>

<template>
  <section class="market-task-page">
    <header class="market-task-page__header">
      <div class="market-task-page__header-copy">
        <div class="market-task-page__header-icon">
          <img :src="headerIcon" alt="">
        </div>
        <div class="market-task-page__heading">
          <p class="market-task-page__eyebrow">任务广场</p>
          <h1 class="market-task-page__title">任务广场</h1>
          <p class="market-task-page__description">浏览已发布任务，按标签筛选后领取可处理的数据项。</p>
        </div>
      </div>
    </header>

    <section class="market-task-page__toolbar">
      <div class="market-task-page__tabs" aria-label="任务列表">
        <button
          class="market-task-page__tab market-task-page__tab--active"
          type="button"
          @click="handleResetTaskList"
        >
          任务列表
        </button>
      </div>

      <div class="market-task-page__toolbar-actions">
        <button
          class="market-task-page__filter-button"
          :class="{ 'market-task-page__filter-button--active': hasActiveTagFilter || isFilterPanelOpen }"
          type="button"
          @click="toggleFilterPanel"
        >
          标签筛选
        </button>

        <Transition name="market-task-filter">
          <section v-if="isFilterPanelOpen" class="market-task-page__filter-shell">
            <aside class="market-task-page__filter-panel" aria-label="标签筛选面板">
              <header class="market-task-page__filter-header">
                <strong>标签筛选</strong>
                <span>{{ selectedTag || '未选择标签' }}</span>
              </header>

              <div v-if="marketplaceTags.length" class="market-task-page__filter-tags">
                <button
                  v-for="tag in marketplaceTags"
                  :key="tag"
                  class="market-task-page__filter-tag"
                  :class="{ 'market-task-page__filter-tag--active': selectedTag === tag }"
                  type="button"
                  @click="handleTagChange(tag)"
                >
                  {{ tag }}
                </button>
              </div>

              <p v-else class="market-task-page__filter-empty">暂无可用标签</p>
            </aside>
          </section>
        </Transition>
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

    <p v-if="taskErrorMessage" class="market-task-page__error">{{ taskErrorMessage }}</p>

    <section class="market-task-page__table-shell">
      <div class="market-task-page__table-scroll">
        <div class="market-task-page__table">
          <div class="market-task-page__table-header">
            <span>任务</span>
            <span>状态</span>
            <span>截止时间</span>
            <span>已领取 / 总额</span>
            <span>操作</span>
          </div>

          <Transition name="market-task-panel" mode="out-in">
            <div
              :key="`${selectedTag}-${currentPage}-${listAnimationKey}`"
              class="market-task-page__table-state"
            >
              <div v-if="shouldShowInlineLoading" class="market-task-page__empty">正在加载任务...</div>
              <div v-else-if="!tasks.length" class="market-task-page__empty">{{ emptyTaskText }}</div>

              <TransitionGroup
                v-else
                class="market-task-page__rows"
                name="market-task-row"
                tag="div"
              >
                <article
                  v-for="task in tasks"
                  :key="`${task.id}-${listAnimationKey}`"
                  class="market-task-page__row"
                >
                  <div class="market-task-page__task-summary">
                    <h2 class="market-task-page__task-name">{{ task.name }}</h2>
                    <div class="market-task-page__task-meta">
                      <span>{{ task.taskCode }}</span>
                      <span>Owner: {{ task.ownerText }}</span>
                      <el-tag
                        v-for="tag in task.tags"
                        :key="tag"
                        type="warning"
                      >
                        {{ tag }}
                      </el-tag>
                      <el-tag v-if="!task.tags.length" type="info">无标签</el-tag>
                    </div>
                  </div>

                  <div class="market-task-page__status-cell">
                    <em
                      class="market-task-page__status-badge"
                      :class="`market-task-page__status-badge--${task.statusType}`"
                    >
                      {{ task.status }}
                    </em>
                  </div>

                  <div class="market-task-page__date-cell">
                    {{ task.createdAt }}
                  </div>

                  <div class="market-task-page__progress-cell">
                    <strong>{{ task.quotaText }}</strong>
                    <el-progress
                      :color="task.progressColor"
                      :percentage="task.progressPercent"
                      :show-text="false"
                    />
                  </div>

                  <div class="market-task-page__actions">
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="task.availableCount <= 0"
                      :loading="claimingTaskId === task.id"
                      @click="handleClaimTask(task)"
                    >
                      {{ task.availableCount > 0 ? '领取任务' : '已领完' }}
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

    <footer class="market-task-page__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        background
        layout="total, prev, pager, next, jumper"
        :page-size="taskPageSize"
        :total="totalTasks"
        @current-change="handlePageChange"
      />
    </footer>
  </section>
</template>

<style scoped>
.market-task-page {
  display: flex;
  min-height: calc(100vh - 104px);
  flex-direction: column;
  /* gap: 18px; */
  padding: 24px;
  color: #1f2937;
  background: #f3f6f9;
}

.market-task-page__header-copy {
  position: relative;
  display: flex;
  width: min(100%, 600px);
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.market-task-page__header-icon {
  display: grid;
  width: 110px;
  place-items: center;
  border-radius: 100px;
}

.market-task-page__header-icon img {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border: 3px #bfbfbf dashed;
  border-radius: 999px;
}

.market-task-page__heading {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.market-task-page__eyebrow {
  margin: 0 0 6px;
  color: #4f6680;
  font-size: 13px;
  font-weight: 700;
}

.market-task-page__title {
  margin: 0;
  color: #1f2937;
  font-size: 26px;
  line-height: 1.25;
}

.market-task-page__description {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.market-task-page__toolbar {
  display: flex;
  align-items: center;
  /* justify-content: space-between; */
  gap: 16px;
}

.market-task-page__tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: #ffffff;
}

.market-task-page__tab {
  min-width: 108px;
  height: 34px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  background: #172033;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.market-task-page__toolbar-actions {
  position: relative;
  display: flex;
  align-items: center;
}

.market-task-page__filter-button {
  min-width: 108px;
  height: 38px;
  border: 1px solid #d9e2ec;
  border-radius: 999px;
  background: #ffffff;
  color: #334155;
  cursor: pointer;
  font-size: 14px;
  font-weight: 800;
  padding: 0 18px;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.market-task-page__filter-button:hover {
  border-color: #bfd0e0;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
}

.market-task-page__filter-button--active {
  border-color: #172033;
  color: #ffffff;
  background: #172033;
  box-shadow: 0 12px 28px rgba(23, 32, 51, 0.14);
}

.market-task-page__filter-shell {
  position: absolute;
  z-index: 12;
  top: 0;
  left: calc(100% + 5px);
}

.market-task-page__filter-panel {
  display: grid;
  width: 360px;
  gap: 14px;
  padding: 16px;
  border: 1px solid #dbe3ec;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
}

.market-task-page__filter-header {
  display: grid;
  gap: 4px;
}

.market-task-page__filter-header strong {
  color: #1f2937;
  font-size: 14px;
  font-weight: 800;
}

.market-task-page__filter-header span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.market-task-page__filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.market-task-page__filter-tag {
  min-height: 32px;
  border: 1px solid #d9e2ec;
  border-radius: 999px;
  background: #f8fafc;
  color: #334155;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  padding: 0 12px;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.market-task-page__filter-tag:hover {
  border-color: #bfd0e0;
  background: #ffffff;
}

.market-task-page__filter-tag--active {
  border-color: #172033;
  color: #ffffff;
  background: #172033;
  box-shadow: 0 10px 20px rgba(23, 32, 51, 0.12);
}

.market-task-page__filter-empty {
  margin: 0;
  color: #8b98a8;
  font-size: 13px;
  font-weight: 700;
}

.market-task-page__error {
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

.market-task-page__table-shell {
  overflow: hidden;
  margin-top: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.market-task-page__table-scroll {
  overflow: hidden;
}

.market-task-page__table {
  min-width: 980px;
}

.market-task-page__table-header,
.market-task-page__row {
  display: grid;
  grid-template-columns:
    minmax(220px, 1fr)
    minmax(120px, 0.75fr)
    minmax(120px, 0.75fr)
    minmax(190px, 1.1fr)
    minmax(320px, 1.55fr);
}

.market-task-page__table-header {
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  border-bottom: 1px solid #e5e7eb;
  color: #526173;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 800;
}

.market-task-page__table-header span {
  padding: 0 10px;
}

.market-task-page__table-state {
  min-height: 312px;
  background: #ffffff;
}

.market-task-page__rows {
  display: grid;
  gap: 0;
  padding: 0;
  background: #ffffff;
}

.market-task-page__row {
  min-height: 76px;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #edf0f3;
  background: #ffffff;
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.market-task-page__row:hover {
  background: #fbfdff;
  box-shadow: inset 3px 0 0 #409eff;
}

.market-task-page__task-summary,
.market-task-page__status-cell,
.market-task-page__date-cell,
.market-task-page__progress-cell,
.market-task-page__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  padding: 12px 10px;
}

.market-task-page__task-summary {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.market-task-page__task-name {
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

.market-task-page__task-meta {
  display: flex;
  width: 140px;
  max-width: 100%;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
}

.market-task-page__task-meta :deep(.el-tag) {
  min-height: 24px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 800;
}

.market-task-page__status-badge {
  display: inline-flex;
  width: fit-content;
  flex: 0 0 auto;
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

.market-task-page__status-badge::before {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.market-task-page__status-badge--published {
  color: #16a34a;
  background: #dcfce7;
}

.market-task-page__status-badge--ended {
  color: #64748b;
  background: #f1f5f9;
}

.market-task-page__date-cell {
  justify-content: flex-start;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.market-task-page__progress-cell {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.market-task-page__progress-cell strong {
  color: #334155;
  font-size: 13px;
  font-weight: 800;
}

.market-task-page__progress-cell :deep(.el-progress) {
  width: 100%;
}

.market-task-page__progress-cell :deep(.el-progress-bar__outer) {
  height: 5px !important;
  background: #e8edf3;
}

.market-task-page__actions {
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
}

.market-task-page__actions :deep(.el-button) {
  margin-left: 0;
  border-radius: 6px;
  min-height: 32px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 700;
}

.market-task-page__empty {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  color: #8b98a8;
  font-size: 15px;
}

.market-task-page__pagination {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding: 14px 0 0;
  background: #f3f6f9;
}

.market-task-filter-enter-active,
.market-task-filter-leave-active {
  transition:
    opacity 0.24s ease,
    transform 0.24s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.market-task-filter-enter-from,
.market-task-filter-leave-to {
  opacity: 0;
  /* transform: scale(0.992); */
}

.market-task-panel-enter-active,
.market-task-panel-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

/* .market-task-panel-enter-from,
.market-task-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.992);
} */

/* .market-task-row-enter-active,
.market-task-row-leave-active {
  transition:
    opacity 0.28s cubic-bezier(0.2, 0.82, 0.2, 1),
    transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
} */

.market-task-row-move {
  transition: transform 0.28s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.market-task-row-enter-from,
.market-task-row-leave-to {
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
  .market-task-page {
    padding: 16px;
  }

  .market-task-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .market-task-page__tabs {
    width: 100%;
  }

  .market-task-page__pagination {
    justify-content: center;
    overflow-x: auto;
  }

}
</style>
