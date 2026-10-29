<script setup lang="ts">
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, useTemplateRef, watch } from 'vue'
import reviewerAvatarImage from '../../icon/avater.gif'
import reviewerAnnouncementImage from '../../icon/reviewer_ad.png'
import { useAuthStore } from '../../stores/auth'
import { GetReviewTasks, GetUserInfo } from '../../utils/api'

interface BackendReviewTask {
  id?: string | number
  taskId?: string | number
  reviewTaskId?: string | number
  taskTitle?: string
  taskDescription?: string
  title?: string
  name?: string
  taskName?: string
  description?: string
  desc?: string
  status?: string
  totalCount?: string | number
  approvedCount?: string | number
  rejectedCount?: string | number
  pendingCount?: string | number
  reviewedCount?: string | number
  itemCount?: string | number
  sampleCount?: string | number
  deadline?: string
  dueTime?: string
  createTime?: string
  createdAt?: string
  updateTime?: string
  updatedAt?: string
  progress?: Record<string, unknown>
  [key: string]: unknown
}

interface ReviewerTask {
  id: string
  name: string
  description: string
  status: string
  statusType: string
  totalCount: number
  approvedCount: number
  rejectedCount: number
  pendingCount: number
  reviewedCount: number
  createdAt: string
}

const authStore = useAuthStore()
const reviewTasks = shallowRef<ReviewerTask[]>([])
const reviewerUserInfo = shallowRef<Record<string, unknown>>({})
const isLoadingDashboard = shallowRef(false)
const dashboardErrorMessage = shallowRef('')
const overviewChartRef = useTemplateRef<HTMLDivElement>('overviewChart')
const quotaChartRef = useTemplateRef<HTMLDivElement>('quotaChart')
const overviewChart = shallowRef<echarts.ECharts | null>(null)
const quotaChart = shallowRef<echarts.ECharts | null>(null)

const reviewerProfile = computed(() => ({
  ...(authStore.userInfo as Record<string, unknown>),
  ...reviewerUserInfo.value,
}))

const reviewerDisplayName = computed(() =>
  readString(reviewerProfile.value.nickname ?? reviewerProfile.value.name, authStore.currentUser.name),
)

const reviewerUsername = computed(() =>
  readString(reviewerProfile.value.username, authStore.activeUsername),
)

const reviewerRoleLabel = computed(() => formatRoleLabel(readRoleValue(reviewerProfile.value) ?? authStore.activeRole))

const reviewerInfoItems = computed(() => [
  { label: '用户 ID', value: readString(reviewerProfile.value.id, authStore.currentUser.id) },
  { label: '登录账号', value: reviewerUsername.value },
  { label: '邮箱', value: readString(reviewerProfile.value.email, '暂无邮箱') },
  { label: '手机号', value: readString(reviewerProfile.value.phone, '暂无手机号') },
])

const pendingTaskCount = computed(() =>
  reviewTasks.value.filter((task) => task.pendingCount > 0 || isPendingReviewStatus(task.statusType)).length,
)

const reviewedTaskCount = computed(() =>
  reviewTasks.value.filter((task) => task.reviewedCount > 0 || isReviewedStatus(task.statusType)).length,
)

const totalReviewQuota = computed(() => reviewTasks.value.reduce((sum, task) => sum + task.totalCount, 0))
const approvedTotal = computed(() => reviewTasks.value.reduce((sum, task) => sum + task.approvedCount, 0))
const rejectedTotal = computed(() => reviewTasks.value.reduce((sum, task) => sum + task.rejectedCount, 0))
const reviewedTotal = computed(() => reviewTasks.value.reduce((sum, task) => sum + task.reviewedCount, 0))
const pendingTotal = computed(() => {
  const pendingCountTotal = reviewTasks.value.reduce((sum, task) => sum + task.pendingCount, 0)

  return totalReviewQuota.value > 0
    ? Math.max(totalReviewQuota.value - reviewedTotal.value, 0)
    : pendingCountTotal
})

const overviewStats = computed(() => [
  {
    label: '待审核任务',
    value: formatCompactNumber(pendingTaskCount.value),
    tone: 'blue',
  },
  {
    label: '已审核任务',
    value: formatCompactNumber(reviewedTaskCount.value),
    tone: 'purple',
  },
  {
    label: '审核总量',
    value: formatCompactNumber(totalReviewQuota.value),
    tone: 'orange',
  },
  {
    label: '已审核数量',
    value: formatCompactNumber(reviewedTotal.value),
    tone: 'cyan',
  },
])

const pendingReviewRows = computed(() =>
  reviewTasks.value
    .filter((task) => task.pendingCount > 0 || isPendingReviewStatus(task.statusType))
    .slice(0, 5),
)

const trendData = computed(() => {
  const days = getRecentDays(7)
  const dayCounter = new Map(days.map((day) => [day.key, 0]))

  reviewTasks.value.forEach((task) => {
    const dateKey = getDateKey(task.createdAt)

    if (dateKey && dayCounter.has(dateKey)) {
      dayCounter.set(dateKey, (dayCounter.get(dateKey) ?? 0) + 1)
    }
  })

  return {
    labels: days.map((day) => day.label),
    values: days.map((day) => dayCounter.get(day.key) ?? 0),
  }
})

const reviewChartData = computed(() => [
  { name: '已通过', value: approvedTotal.value },
  { name: '已驳回', value: rejectedTotal.value },
  { name: '待审核', value: pendingTotal.value },
])

function getResponsePayload(response: any) {
  const payload = response?.data ?? response ?? {}

  return payload?.data ?? payload
}

function isApiSuccess(response: any) {
  return response?.code === undefined || Number(response.code) === 200
}

function assertApiSuccess(response: any) {
  if (!isApiSuccess(response)) {
    throw new Error(response?.message ?? response?.msg ?? '请求失败')
  }
}

function pickTaskArray(response: any) {
  const payload = response?.data ?? response ?? {}
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

function pickUserInfo(response: any) {
  const payload = getResponsePayload(response)
  const userInfo = payload?.user ?? payload?.userInfo ?? payload

  return userInfo && typeof userInfo === 'object'
    ? (userInfo as Record<string, unknown>)
    : {}
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

function readRoleValue(userInfo: Record<string, unknown>) {
  const roles = userInfo.roles

  if (Array.isArray(roles)) {
    return roles[0]
  }

  return userInfo.role
}

function formatNumberText(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('en-US') : '-'
}

function formatCompactNumber(value: number) {
  if (!Number.isFinite(value)) {
    return '-'
  }

  if (Math.abs(value) >= 10000) {
    const compactValue = value / 10000
    const fixedText = compactValue >= 100 ? compactValue.toFixed(0) : compactValue.toFixed(1)

    return `${fixedText.replace(/\.0$/, '')}w+`
  }

  return formatNumberText(value)
}

function formatRoleLabel(value: unknown) {
  const role = readString(value, '').toUpperCase()

  if (role === 'REVIEWER') {
    return '审核员'
  }

  if (role === 'LABELER') {
    return '标注员'
  }

  if (['PROJECT_ADMIN', 'OWNER', 'ADMIN'].includes(role)) {
    return '管理员'
  }

  return readString(value, '审核员')
}

function getDateKey(value: unknown) {
  const text = readString(value)

  if (!text) {
    return ''
  }

  const normalizedText = text.includes('T') ? text : text.replace(' ', 'T')
  const date = new Date(normalizedText)

  if (Number.isNaN(date.getTime())) {
    return text.slice(0, 10)
  }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function formatDate(value: unknown) {
  return getDateKey(value) || '暂无时间'
}

function getRecentDays(dayCount: number) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)

  return Array.from({ length: dayCount }, (_, index) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (dayCount - index - 1))

    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')

    return {
      key: `${year}-${month}-${day}`,
      label: `${year}-${month}-${day}`,
    }
  })
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

function isPendingReviewStatus(statusType: string) {
  return ['pending', 'submitted', 'reviewing'].includes(statusType)
}

function isReviewedStatus(statusType: string) {
  return ['reviewed', 'approved', 'rejected', 'ended'].includes(statusType)
}

function readReviewProgress(record: BackendReviewTask) {
  const progress = (record.progress && typeof record.progress === 'object' ? record.progress : {}) as Record<
    string,
    unknown
  >
  const approvedCount = readNumber(progress.approvedCount ?? record.approvedCount, 0)
  const rejectedCount = readNumber(progress.rejectedCount ?? record.rejectedCount, 0)
  const fallbackReviewed = approvedCount + rejectedCount
  const reviewedCount = readNumber(progress.reviewedCount ?? record.reviewedCount, fallbackReviewed)
  const totalCount = readNumber(
    progress.totalCount ?? record.totalCount ?? record.itemCount ?? record.sampleCount,
    Math.max(reviewedCount, fallbackReviewed),
  )
  const pendingCount = readNumber(
    progress.pendingCount ?? record.pendingCount,
    Math.max(totalCount - reviewedCount, 0),
  )

  return {
    approvedCount,
    rejectedCount,
    reviewedCount,
    totalCount,
    pendingCount,
  }
}

function normalizeReviewTaskRecord(record: unknown, index: number): ReviewerTask {
  const task = (record ?? {}) as BackendReviewTask
  const id = String(task.taskId ?? task.reviewTaskId ?? task.id ?? `review-task-${index}`)
  const progress = readReviewProgress(task)
  const statusType = getStatusType(task.status)

  return {
    id,
    name: readString(task.taskTitle ?? task.title ?? task.name ?? task.taskName, `未命名审核任务 ${index + 1}`),
    description: readString(task.taskDescription ?? task.description ?? task.desc, '暂无任务描述'),
    status: formatStatus(task.status),
    statusType,
    totalCount: progress.totalCount,
    approvedCount: progress.approvedCount,
    rejectedCount: progress.rejectedCount,
    pendingCount: progress.pendingCount,
    reviewedCount: progress.reviewedCount,
    createdAt: formatDate(task.deadline ?? task.dueTime ?? task.createTime ?? task.createdAt ?? task.updateTime ?? task.updatedAt),
  }
}

function normalizeReviewTaskList(response: any) {
  return pickTaskArray(response).map((item, index) => normalizeReviewTaskRecord(item, index))
}

function formatChartDateLabel(value: unknown) {
  const text = readString(value)

  return text.length >= 10 ? text.slice(5, 10).replace('-', '/') : text
}

function getOverviewChartOption(): echarts.EChartsOption {
  const values = trendData.value.values
  const hasData = values.some((value) => value > 0)

  return {
    color: ['#4fc3f7', '#6d63ff'],
    tooltip: {
      trigger: 'axis',
      appendToBody: true,
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderColor: 'rgba(255, 255, 255, 0.08)',
      borderWidth: 1,
      padding: [9, 12],
      textStyle: {
        color: '#ffffff',
        fontSize: 12,
        fontWeight: 700,
      },
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: '#5b7cfa',
          type: 'dashed',
          width: 1.5,
        },
      },
      formatter: (params: unknown) => {
        const item = Array.isArray(params)
          ? params[0] as { axisValue?: unknown; value?: unknown }
          : null

        return item ? `${item.axisValue}<br/>审核列表数量：${item.value}` : ''
      },
    },
    grid: {
      top: 34,
      right: 28,
      bottom: 34,
      left: 34,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.value.labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dce6f2', width: 1 } },
      axisLabel: {
        color: '#8a98aa',
        fontSize: 11,
        fontWeight: 700,
        interval: 0,
        margin: 12,
        formatter: formatChartDateLabel,
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      min: 0,
      max: (range: { max?: unknown }) => Math.max(1, Math.ceil(readNumber(range.max)) + 1),
      splitNumber: 4,
      splitLine: { lineStyle: { color: '#edf3f8', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#8a98aa',
        fontSize: 11,
        fontWeight: 700,
      },
    },
    series: [
      {
        name: '审核列表数量',
        type: 'line',
        data: values,
        smooth: true,
        clip: false,
        symbol: 'circle',
        symbolSize: 9,
        showSymbol: true,
        lineStyle: {
          width: 3.5,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#35d3df' },
            { offset: 1, color: '#6c63ff' },
          ]),
          shadowBlur: 10,
          shadowColor: 'rgba(91, 124, 250, 0.24)',
        },
        itemStyle: {
          color: '#ffffff',
          borderColor: '#5b7cfa',
          borderWidth: 3,
        },
        label: {
          show: true,
          position: 'top',
          color: '#4354d8',
          fontSize: 11,
          fontWeight: 850,
          formatter: (params: { value?: unknown }) => {
            const value = readNumber(params.value, 0)

            return value > 0 ? String(value) : ''
          },
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(76, 195, 247, 0.26)' },
            { offset: 0.56, color: 'rgba(108, 99, 255, 0.08)' },
            { offset: 1, color: 'rgba(108, 99, 255, 0)' },
          ]),
        },
      },
    ],
    graphic: hasData
      ? []
      : [
          {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '近 7 日暂无审核列表数据',
              fill: '#94a3b8',
              fontSize: 14,
            },
          },
        ],
  }
}

function getQuotaChartOption(): echarts.EChartsOption {
  const hasData = reviewChartData.value.some((item) => item.value > 0)

  return {
    color: ['#263fb3', '#25c4de', '#ff9b73'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}',
    },
    legend: {
      bottom: 2,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: {
        color: '#6b7280',
        fontSize: 11,
      },
    },
    series: [
      {
        name: '已审核 / 总量',
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '48%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 3,
          borderRadius: 4,
        },
        label: {
          color: '#64748b',
          fontSize: 11,
          formatter: '{b}\n{d}%',
        },
        labelLine: {
          length: 10,
          length2: 8,
          lineStyle: {
            color: '#cbd5e1',
          },
        },
        data: hasData ? reviewChartData.value : [{ name: '暂无数据', value: 1 }],
      },
    ],
  }
}

function initCharts() {
  if (overviewChartRef.value && !overviewChart.value) {
    overviewChart.value = echarts.init(overviewChartRef.value)
  }

  if (quotaChartRef.value && !quotaChart.value) {
    quotaChart.value = echarts.init(quotaChartRef.value)
  }

  renderCharts()
}

function renderCharts() {
  overviewChart.value?.setOption(getOverviewChartOption(), true)
  quotaChart.value?.setOption(getQuotaChartOption(), true)
}

function resizeCharts() {
  overviewChart.value?.resize()
  quotaChart.value?.resize()
}

async function loadDashboardData() {
  const satoken = localStorage.getItem('satoken')

  isLoadingDashboard.value = true
  dashboardErrorMessage.value = ''
  reviewerUserInfo.value = authStore.userInfo as Record<string, unknown>

  try {
    const userInfoRequest = satoken
      ? GetUserInfo(satoken).catch(() => null)
      : Promise.resolve(null)
    const [reviewTaskResponse, userInfoResponse] = await Promise.all([
      GetReviewTasks({ pageNum: 1, pageSize: 1000 }),
      userInfoRequest,
    ])

    assertApiSuccess(reviewTaskResponse)
    const normalizedTasks = normalizeReviewTaskList(reviewTaskResponse)
    reviewTasks.value = normalizedTasks

    if (userInfoResponse && isApiSuccess(userInfoResponse)) {
      reviewerUserInfo.value = {
        ...(authStore.userInfo as Record<string, unknown>),
        ...pickUserInfo(userInfoResponse),
      }
    }
  } catch (error: any) {
    dashboardErrorMessage.value =
      error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? '审核员首页数据加载失败'
    ElMessage.error(dashboardErrorMessage.value)
  } finally {
    isLoadingDashboard.value = false
    await nextTick()
    renderCharts()
  }
}

watch(
  [trendData, reviewChartData],
  async () => {
    await nextTick()
    renderCharts()
  },
  { deep: true },
)

onMounted(async () => {
  await nextTick()
  initCharts()
  window.addEventListener('resize', resizeCharts)
  loadDashboardData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  overviewChart.value?.dispose()
  quotaChart.value?.dispose()
  overviewChart.value = null
  quotaChart.value = null
})
</script>

<template>
  <section class="reviewer-home">
    <article class="reviewer-home__panel reviewer-home__overview">
      <header class="reviewer-home__welcome">
        <span class="reviewer-home__welcome-prefix">欢迎回来，</span>
        <h2>{{ reviewerDisplayName }}</h2>
      </header>

      <section class="reviewer-home__metric-grid" aria-label="审核任务总览统计">
        <article
          v-for="item in overviewStats"
          :key="item.label"
          class="reviewer-home__metric-card"
          :class="`reviewer-home__metric-card--${item.tone}`"
        >
          <span class="reviewer-home__metric-icon" aria-hidden="true"></span>
          <div class="reviewer-home__metric-content">
            <p>{{ item.label }}</p>
            <strong>{{ isLoadingDashboard ? '-' : item.value }}</strong>
          </div>
        </article>
      </section>

      <section class="reviewer-home__chart-section" aria-label="审核任务总览">
        <header class="reviewer-home__section-header">
          <h2>审核总览</h2>
          <RouterLink to="/workbench/review/list">查看更多</RouterLink>
        </header>
        <div ref="overviewChart" class="reviewer-home__line-chart"></div>
      </section>
    </article>

    <article class="reviewer-home__panel reviewer-home__profile-card">
      <header class="reviewer-home__profile-title">
        <h2>个人信息</h2>
      </header>

      <div class="reviewer-home__profile">
        <img class="reviewer-home__avatar" :src="reviewerAvatarImage" alt="">
        <div class="reviewer-home__profile-main">
          <h2>{{ reviewerDisplayName }}</h2>
          <span>{{ reviewerUsername }}</span>
        </div>
        <el-tag effect="plain" size="large" type="success">{{ reviewerRoleLabel }}</el-tag>
      </div>

      <dl class="reviewer-home__profile-details">
        <div v-for="item in reviewerInfoItems" :key="item.label" class="reviewer-home__profile-row">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
    </article>

    <article class="reviewer-home__panel reviewer-home__task-list-panel">
      <header class="reviewer-home__section-header reviewer-home__table-title">
        <h2>待审核任务列表</h2>
        <RouterLink to="/workbench/review/list">查看更多</RouterLink>
      </header>

      <div v-if="dashboardErrorMessage" class="reviewer-home__empty">{{ dashboardErrorMessage }}</div>
      <div v-else-if="!pendingReviewRows.length" class="reviewer-home__empty">暂无待审核任务</div>
      <div v-else class="reviewer-home__task-table">
        <div class="reviewer-home__task-head">
          <span>序号</span>
          <span>任务名称</span>
          <span>状态</span>
          <span>总量</span>
        </div>
        <article
          v-for="(task, index) in pendingReviewRows"
          :key="task.id"
          class="reviewer-home__task-row"
        >
          <span>{{ index + 1 }}</span>
          <strong>{{ task.name }}</strong>
          <el-tag
            effect="light"
            size="small"
            :type="isPendingReviewStatus(task.statusType) ? 'warning' : 'info'"
          >
            {{ task.status }}
          </el-tag>
          <span>{{ formatNumberText(task.totalCount) }}</span>
        </article>
      </div>
    </article>

    <article class="reviewer-home__panel reviewer-home__quota-panel">
      <header class="reviewer-home__section-header">
        <h2>已审核 / 总量</h2>
      </header>
      <div ref="quotaChart" class="reviewer-home__quota-chart"></div>
    </article>

    <article class="reviewer-home__panel reviewer-home__announcement">
      <img :src="reviewerAnnouncementImage" alt="LabelHub 审核员公告">
    </article>
  </section>
</template>

<style scoped>
.reviewer-home {
  display: grid;
  min-height: calc(100vh - 104px);
  grid-template-columns: minmax(0, 1.02fr) minmax(300px, 0.92fr) minmax(360px, 0.96fr);
  grid-template-rows: minmax(380px, 58vh) minmax(268px, 36vh);
  grid-template-areas:
    "overview overview profile"
    "tasks quota announcement";
  gap: 10px;
  padding: 4px;
  color: #111827;
  background: #f3f6fb;
}

.reviewer-home__panel {
  overflow: hidden;
  border: 1px solid #e7ecf4;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.reviewer-home__overview {
  display: grid;
  grid-area: overview;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
  padding: 14px 18px 12px;
}

.reviewer-home__welcome {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.reviewer-home__welcome-prefix {
  color: #273243;
  font-size: 15px;
  font-weight: 800;
}

.reviewer-home__welcome strong {
  overflow: hidden;
  color: #050505;
  font-size: 30px;
  font-weight: 950;
  line-height: 1.08;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-home__metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 70px;
}

.reviewer-home__metric-card {
  display: grid;
  min-width: 0;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border-right: 1px solid #eef2f7;
  padding: 0 18px 0 10px;
}

.reviewer-home__metric-card:last-child {
  border-right: 0;
}

.reviewer-home__metric-icon {
  position: relative;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: #eff5ff;
  box-shadow: 0 8px 18px rgba(91, 124, 250, 0.14);
}

.reviewer-home__metric-icon::before {
  content: "";
  width: 12px;
  height: 12px;
  border-radius: 4px;
  background: currentColor;
  opacity: 0.95;
}

.reviewer-home__metric-card--blue {
  color: #5b7cfa;
}

.reviewer-home__metric-card--purple {
  color: #bc6ff1;
}

.reviewer-home__metric-card--orange {
  color: #ff9b73;
}

.reviewer-home__metric-card--cyan {
  color: #51c7f4;
}

.reviewer-home__metric-content {
  min-width: 0;
}

.reviewer-home__metric-content p {
  overflow: hidden;
  margin: 0;
  color: #060606;
  font-size: 20px;
  font-weight: 950;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-home__metric-content strong {
  display: block;
  margin-top: 6px;
  color: #383f4d;
  font-size: 16px;
  font-weight: 900;
  line-height: 1.1;
}

.reviewer-home__chart-section {
  display: grid;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 4px;
}

.reviewer-home__section-header,
.reviewer-home__profile-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reviewer-home__section-header h2,
.reviewer-home__profile-title h2 {
  margin: 0;
  color: #050505;
  font-size: 24px;
  font-weight: 950;
  line-height: 1.15;
}

.reviewer-home__section-header a {
  flex: 0 0 auto;
  color: #5b7cfa;
  font-size: 12px;
  font-weight: 850;
  text-decoration: none;
}

.reviewer-home__line-chart {
  width: 100%;
  min-height: 278px;
  border: 1px solid #eef3f8;
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.96) 0%, rgba(255, 255, 255, 0.92) 100%);
}

.reviewer-home__profile-card {
  display: grid;
  grid-area: profile;
  align-content: start;
  gap: 24px;
  padding: 22px 24px 20px;
}

.reviewer-home__profile-title h2 {
  font-size: 26px;
}

.reviewer-home__profile {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 8px 0 2px;
}

.reviewer-home__avatar {
  width: 66px;
  height: 66px;
  border: 3px solid #dff2ff;
  border-radius: 50%;
  background: #f8fafc;
  object-fit: cover;
}

.reviewer-home__profile-main {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.reviewer-home__profile-main strong {
  overflow: hidden;
  color: #0b1120;
  font-size: 26px;
  font-weight: 950;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-home__profile-main span {
  overflow: hidden;
  color: #64748b;
  font-size: 15px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-home__profile-details {
  display: grid;
  margin: 0;
  padding: 8px 0 0;
}

.reviewer-home__profile-row {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-top: 1px solid #edf2f7;
}

.reviewer-home__profile-row dt,
.reviewer-home__profile-row dd {
  margin: 0;
  font-size: 12px;
  line-height: 1.35;
}

.reviewer-home__profile-row dt {
  flex: 0 0 auto;
  color: #7d8ca3;
  font-weight: 850;
}

.reviewer-home__profile-row dd {
  overflow: hidden;
  color: #070707;
  font-weight: 950;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-home__task-list-panel,
.reviewer-home__quota-panel {
  min-height: 0;
}

.reviewer-home__task-list-panel {
  display: grid;
  grid-area: tasks;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  padding: 16px 18px 14px;
}

.reviewer-home__table-title {
  padding: 0;
}

.reviewer-home__task-table,
.reviewer-home__empty {
  border: 0;
  border-radius: 0;
  background: transparent;
}

.reviewer-home__task-table {
  overflow: hidden;
  padding: 0 16px 14px;
}

.reviewer-home__task-head,
.reviewer-home__task-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 78px 78px;
  column-gap: 10px;
  align-items: center;
}

.reviewer-home__task-head {
  min-height: 36px;
  color: #111827;
  background: #f6f8fb;
  font-size: 14px;
  font-weight: 950;
}

.reviewer-home__task-head span,
.reviewer-home__task-row span,
.reviewer-home__task-row strong {
  min-width: 0;
  padding: 0 6px;
}

.reviewer-home__task-row {
  min-height: 38px;
  border-bottom: 1px solid #f0f3f7;
}

.reviewer-home__task-row:last-child {
  border-bottom: 0;
}

.reviewer-home__task-row strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reviewer-home__task-row > span {
  color: #475569;
  font-size: 13px;
  font-weight: 750;
}

.reviewer-home__empty {
  display: grid;
  min-height: 196px;
  place-items: center;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 800;
}

.reviewer-home__quota-panel {
  display: grid;
  grid-area: quota;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 8px;
  padding: 16px 18px 12px;
}

.reviewer-home__quota-chart {
  min-height: 210px;
}

.reviewer-home__announcement {
  display: grid;
  grid-area: announcement;
  min-height: 0;
  place-items: center;
  background: #f8fbff;
}

.reviewer-home__announcement img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 1160px) {
  .reviewer-home {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    grid-template-areas:
      "overview"
      "profile"
      "tasks"
      "quota"
      "announcement";
  }
}

@media (max-width: 760px) {
  .reviewer-home {
    padding: 12px;
  }

  .reviewer-home__metric-grid {
    grid-template-columns: 1fr;
  }

  .reviewer-home__metric-card {
    border-right: 0;
    border-bottom: 1px solid #edf2f7;
    padding: 10px 0;
  }

  .reviewer-home__metric-card:last-child {
    border-bottom: 0;
  }

  .reviewer-home__profile {
    grid-template-columns: 64px minmax(0, 1fr);
  }

  .reviewer-home__profile :deep(.el-tag) {
    grid-column: 1 / -1;
    width: fit-content;
  }

  .reviewer-home__task-head,
  .reviewer-home__task-row {
    grid-template-columns: 38px minmax(0, 1fr) 72px 62px;
    column-gap: 4px;
  }

  .reviewer-home__quota-panel {
    grid-column: 1;
    grid-row: auto;
  }
}
</style>
