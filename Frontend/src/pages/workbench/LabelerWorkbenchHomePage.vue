<script setup lang="ts">
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, useTemplateRef, watch } from 'vue'
import labelerAvatarImage from '../../icon/avater.gif'
import labelerAnnouncementImage from '../../icon/labler_ad.png'
import { useAuthStore } from '../../stores/auth'
import { GetTaskMarket, GetUserInfo } from '../../utils/api'

interface BackendMarketTask {
  id?: string | number
  taskId?: string | number
  title?: string
  name?: string
  taskName?: string
  status?: string
  quota?: string | number
  totalCount?: string | number
  total?: string | number
  claimedCount?: string | number
  availableCount?: string | number
  createTime?: string
  createdAt?: string
  updateTime?: string
  updatedAt?: string
  [key: string]: unknown
}

interface LabelerMarketTask {
  id: string
  name: string
  status: string
  statusType: 'available' | 'ended'
  quota: number
  claimedCount: number
  availableCount: number
  createdAt: string
}

const authStore = useAuthStore()
const marketTasks = shallowRef<LabelerMarketTask[]>([])
const marketTotal = shallowRef(0)
const labelerUserInfo = shallowRef<Record<string, unknown>>({})
const isLoadingDashboard = shallowRef(false)
const dashboardErrorMessage = shallowRef('')
const overviewChartRef = useTemplateRef<HTMLDivElement>('overviewChart')
const quotaChartRef = useTemplateRef<HTMLDivElement>('quotaChart')
const overviewChart = shallowRef<echarts.ECharts | null>(null)
const quotaChart = shallowRef<echarts.ECharts | null>(null)

const labelerProfile = computed(() => ({
  ...(authStore.userInfo as Record<string, unknown>),
  ...labelerUserInfo.value,
}))

const labelerDisplayName = computed(() =>
  readString(labelerProfile.value.nickname ?? labelerProfile.value.name, authStore.currentUser.name),
)

const labelerUsername = computed(() =>
  readString(labelerProfile.value.username, authStore.activeUsername),
)

const labelerRoleLabel = computed(() => formatRoleLabel(readRoleValue(labelerProfile.value) ?? authStore.activeRole))

const labelerInfoItems = computed(() => [
  { label: '用户 ID', value: readString(labelerProfile.value.id, authStore.currentUser.id) },
  { label: '登录账号', value: labelerUsername.value },
  { label: '邮箱', value: readString(labelerProfile.value.email, '暂无邮箱') },
  { label: '手机号', value: readString(labelerProfile.value.phone, '暂无手机号') },
])

const availableTaskCount = computed(() => {
  const count = marketTasks.value.filter((task) => task.availableCount > 0).length

  return count || marketTotal.value
})
const claimedTaskCount = computed(() => marketTasks.value.filter((task) => task.claimedCount > 0).length)
const totalQuota = computed(() => marketTasks.value.reduce((sum, task) => sum + task.quota, 0))
const claimedQuota = computed(() => marketTasks.value.reduce((sum, task) => sum + task.claimedCount, 0))
const remainingQuota = computed(() => Math.max(totalQuota.value - claimedQuota.value, 0))

const overviewStats = computed(() => [
  {
    label: '可领取任务',
    value: formatCompactNumber(availableTaskCount.value),
    tone: 'blue',
  },
  {
    label: '已领取任务',
    value: formatCompactNumber(claimedTaskCount.value),
    tone: 'purple',
  },
  {
    label: '任务总额度',
    value: formatCompactNumber(totalQuota.value),
    tone: 'orange',
  },
  {
    label: '已领取额度',
    value: formatCompactNumber(claimedQuota.value),
    tone: 'cyan',
  },
])

const availableTaskRows = computed(() =>
  marketTasks.value.filter((task) => task.availableCount > 0).slice(0, 5),
)

const trendData = computed(() => {
  const days = getRecentDays(7)
  const dayCounter = new Map(days.map((day) => [day.key, 0]))

  marketTasks.value.forEach((task) => {
    if (task.availableCount <= 0) {
      return
    }

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

const quotaChartData = computed(() => [
  { name: '已领取', value: claimedQuota.value },
  { name: '可领取', value: remainingQuota.value },
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

function pickPaginationTotal(response: any, fallback: number) {
  const payload = response?.data ?? response ?? {}
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

  if (role === 'LABELER') {
    return '标注员'
  }

  if (role === 'REVIEWER') {
    return '审核员'
  }

  if (['PROJECT_ADMIN', 'OWNER', 'ADMIN'].includes(role)) {
    return '管理员'
  }

  return readString(value, '标注员')
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

function normalizeTaskRecord(record: unknown, index: number): LabelerMarketTask {
  const task = (record ?? {}) as BackendMarketTask
  const id = String(task.taskId ?? task.id ?? `market-task-${index}`)
  const claimedCount = readNumber(task.claimedCount)
  const quota = readNumber(task.quota ?? task.totalCount ?? task.total)
  const availableFallback = quota > 0 ? Math.max(0, quota - claimedCount) : 0
  const availableCount = readNumber(task.availableCount, availableFallback)

  return {
    id,
    name: readString(task.title ?? task.name ?? task.taskName, `未命名任务 ${index + 1}`),
    status: availableCount > 0 ? '可领取' : '已领完',
    statusType: availableCount > 0 ? 'available' : 'ended',
    quota,
    claimedCount,
    availableCount,
    createdAt: formatDate(task.createTime ?? task.createdAt ?? task.updateTime ?? task.updatedAt),
  }
}

function normalizeTaskList(response: any) {
  return pickTaskArray(response).map((item, index) => normalizeTaskRecord(item, index))
}

function getOverviewChartOption(): echarts.EChartsOption {
  const values = trendData.value.values
  const hasData = values.some((value) => value > 0)

  return {
    color: ['#4fc3f7', '#6d63ff'],
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const item = Array.isArray(params)
          ? params[0] as { axisValue?: unknown; value?: unknown }
          : null

        return item ? `${item.axisValue}<br/>可领取任务：${item.value}` : ''
      },
    },
    grid: {
      top: 26,
      right: 14,
      bottom: 24,
      left: 42,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.value.labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e3e8f2' } },
      axisLabel: { color: '#9aa6b8', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef3f8' } },
      axisLabel: { color: '#9aa6b8', fontSize: 10 },
    },
    series: [
      {
        name: '可领取任务',
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 3,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#35d3df' },
            { offset: 1, color: '#6c63ff' },
          ]),
        },
        itemStyle: {
          color: '#ffffff',
          borderColor: '#5b7cfa',
          borderWidth: 3,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(76, 195, 247, 0.24)' },
            { offset: 1, color: 'rgba(108, 99, 255, 0.02)' },
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
              text: '近 7 日暂无可领取任务',
              fill: '#94a3b8',
              fontSize: 14,
            },
          },
        ],
  }
}

function getQuotaChartOption(): echarts.EChartsOption {
  const hasData = quotaChartData.value.some((item) => item.value > 0)

  return {
    color: ['#263fb3', '#25c4de'],
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
        name: '已领取 / 总额',
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
        data: hasData ? quotaChartData.value : [{ name: '暂无数据', value: 1 }],
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
  labelerUserInfo.value = authStore.userInfo as Record<string, unknown>

  try {
    const userInfoRequest = satoken
      ? GetUserInfo(satoken).catch(() => null)
      : Promise.resolve(null)
    const [marketResponse, userInfoResponse] = await Promise.all([
      GetTaskMarket({ pageNum: 1, pageSize: 1000 }),
      userInfoRequest,
    ])

    assertApiSuccess(marketResponse)
    const normalizedTasks = normalizeTaskList(marketResponse)
    marketTasks.value = normalizedTasks
    marketTotal.value = pickPaginationTotal(marketResponse, normalizedTasks.length)

    if (userInfoResponse && isApiSuccess(userInfoResponse)) {
      labelerUserInfo.value = {
        ...(authStore.userInfo as Record<string, unknown>),
        ...pickUserInfo(userInfoResponse),
      }
    }
  } catch (error: any) {
    dashboardErrorMessage.value =
      error?.response?.data?.message ?? error?.response?.data?.msg ?? error?.message ?? '标注员首页数据加载失败'
    ElMessage.error(dashboardErrorMessage.value)
  } finally {
    isLoadingDashboard.value = false
    await nextTick()
    renderCharts()
  }
}

watch(
  [trendData, quotaChartData],
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
  <section class="labeler-home">
    <article class="labeler-home__panel labeler-home__overview">
      <header class="labeler-home__welcome">
        <span class="labeler-home__welcome-prefix">欢迎回来，</span>
        <strong>{{ labelerDisplayName }}</strong>
      </header>

      <section class="labeler-home__metric-grid" aria-label="任务总览统计">
        <article
          v-for="item in overviewStats"
          :key="item.label"
          class="labeler-home__metric-card"
          :class="`labeler-home__metric-card--${item.tone}`"
        >
          <span class="labeler-home__metric-icon" aria-hidden="true"></span>
          <div class="labeler-home__metric-content">
            <p>{{ item.label }}</p>
            <strong>{{ isLoadingDashboard ? '-' : item.value }}</strong>
          </div>
        </article>
      </section>

      <section class="labeler-home__chart-section" aria-label="任务总览">
        <header class="labeler-home__section-header">
          <h2>任务总览</h2>
          <RouterLink to="/workbench/marketplace">查看更多</RouterLink>
        </header>
        <div ref="overviewChart" class="labeler-home__line-chart"></div>
      </section>
    </article>

    <article class="labeler-home__panel labeler-home__profile-card">
      <header class="labeler-home__profile-title">
        <h2>个人信息</h2>
      </header>

      <div class="labeler-home__profile">
        <img class="labeler-home__avatar" :src="labelerAvatarImage" alt="">
        <div class="labeler-home__profile-main">
          <strong>{{ labelerDisplayName }}</strong>
          <span>{{ labelerUsername }}</span>
        </div>
        <el-tag effect="plain" size="large" type="success">{{ labelerRoleLabel }}</el-tag>
      </div>

      <dl class="labeler-home__profile-details">
        <div v-for="item in labelerInfoItems" :key="item.label" class="labeler-home__profile-row">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
    </article>

    <article class="labeler-home__panel labeler-home__task-list-panel">
      <header class="labeler-home__section-header labeler-home__table-title">
        <h2>可领取任务列表</h2>
        <RouterLink to="/workbench/marketplace">查看更多</RouterLink>
      </header>

      <div v-if="dashboardErrorMessage" class="labeler-home__empty">{{ dashboardErrorMessage }}</div>
      <div v-else-if="!availableTaskRows.length" class="labeler-home__empty">暂无可领取任务</div>
      <div v-else class="labeler-home__task-table">
        <div class="labeler-home__task-head">
          <span>序号</span>
          <span>任务名称</span>
          <span>状态</span>
          <span>总额</span>
        </div>
        <article
          v-for="(task, index) in availableTaskRows"
          :key="task.id"
          class="labeler-home__task-row"
        >
          <span>{{ index + 1 }}</span>
          <strong>{{ task.name }}</strong>
          <el-tag
            effect="light"
            size="small"
            :type="task.statusType === 'available' ? 'success' : 'info'"
          >
            {{ task.status }}
          </el-tag>
          <span>{{ formatNumberText(task.quota) }}</span>
        </article>
      </div>
    </article>

    <article class="labeler-home__panel labeler-home__quota-panel">
      <header class="labeler-home__section-header">
        <h2>已领取 / 总额</h2>
      </header>
      <div ref="quotaChart" class="labeler-home__quota-chart"></div>
    </article>

    <article class="labeler-home__panel labeler-home__announcement">
      <img :src="labelerAnnouncementImage" alt="LabelHub 标注员公告">
    </article>
  </section>
</template>

<style scoped>
.labeler-home {
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

.labeler-home__panel {
  overflow: hidden;
  border: 1px solid #e7ecf4;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.labeler-home__overview {
  display: grid;
  grid-area: overview;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 14px;
  padding: 14px 18px 12px;
}

.labeler-home__welcome {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.labeler-home__welcome-prefix {
  color: #273243;
  font-size: 15px;
  font-weight: 800;
}

.labeler-home__welcome strong {
  overflow: hidden;
  color: #050505;
  font-size: 30px;
  font-weight: 950;
  line-height: 1.08;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeler-home__metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 70px;
}

.labeler-home__metric-card {
  display: grid;
  min-width: 0;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border-right: 1px solid #eef2f7;
  padding: 0 18px 0 10px;
}

.labeler-home__metric-card:last-child {
  border-right: 0;
}

.labeler-home__metric-icon {
  position: relative;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: #eff5ff;
  box-shadow: 0 8px 18px rgba(91, 124, 250, 0.14);
}

.labeler-home__metric-icon::before {
  content: "";
  width: 12px;
  height: 12px;
  border-radius: 4px;
  background: currentColor;
  opacity: 0.95;
}

.labeler-home__metric-card--blue {
  color: #5b7cfa;
}

.labeler-home__metric-card--purple {
  color: #bc6ff1;
}

.labeler-home__metric-card--orange {
  color: #ff9b73;
}

.labeler-home__metric-card--cyan {
  color: #51c7f4;
}

.labeler-home__metric-content {
  min-width: 0;
}

.labeler-home__metric-content p {
  overflow: hidden;
  margin: 0;
  color: #060606;
  font-size: 20px;
  font-weight: 950;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeler-home__metric-content strong {
  display: block;
  margin-top: 6px;
  color: #383f4d;
  font-size: 16px;
  font-weight: 900;
  line-height: 1.1;
}

.labeler-home__chart-section {
  display: grid;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 4px;
}

.labeler-home__section-header,
.labeler-home__profile-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.labeler-home__section-header h2,
.labeler-home__profile-title h2 {
  margin: 0;
  color: #050505;
  font-size: 24px;
  font-weight: 950;
  line-height: 1.15;
}

.labeler-home__section-header a {
  flex: 0 0 auto;
  color: #5b7cfa;
  font-size: 12px;
  font-weight: 850;
  text-decoration: none;
}

.labeler-home__line-chart {
  min-height: 260px;
}

.labeler-home__profile-card {
  display: grid;
  grid-area: profile;
  align-content: start;
  gap: 24px;
  padding: 22px 24px 20px;
}

.labeler-home__profile-title h2 {
  font-size: 30px;
}

.labeler-home__profile {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 8px 0 2px;
}

.labeler-home__avatar {
  width: 66px;
  height: 66px;
  border: 3px solid #dff2ff;
  border-radius: 50%;
  background: #f8fafc;
  object-fit: cover;
}

.labeler-home__profile-main {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.labeler-home__profile-main strong {
  overflow: hidden;
  color: #0b1120;
  font-size: 26px;
  font-weight: 950;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeler-home__profile-main span {
  overflow: hidden;
  color: #64748b;
  font-size: 15px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeler-home__profile-details {
  display: grid;
  margin: 0;
  padding: 8px 0 0;
}

.labeler-home__profile-row {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  border-top: 1px solid #edf2f7;
}

.labeler-home__profile-row dt,
.labeler-home__profile-row dd {
  margin: 0;
  font-size: 16px;
  line-height: 1.35;
}

.labeler-home__profile-row dt {
  flex: 0 0 auto;
  color: #7d8ca3;
  font-weight: 850;
}

.labeler-home__profile-row dd {
  overflow: hidden;
  color: #070707;
  font-weight: 950;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeler-home__task-list-panel,
.labeler-home__quota-panel {
  min-height: 0;
}

.labeler-home__task-list-panel {
  display: grid;
  grid-area: tasks;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  padding: 16px 18px 14px;
}

.labeler-home__table-title {
  padding: 0;
}

.labeler-home__task-table,
.labeler-home__empty {
  border: 0;
  border-radius: 0;
  background: transparent;
}

.labeler-home__task-table {
  overflow: hidden;
  padding: 0 16px 14px;
}

.labeler-home__task-head,
.labeler-home__task-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 78px 78px;
  column-gap: 10px;
  align-items: center;
}

.labeler-home__task-head {
  min-height: 36px;
  color: #111827;
  background: #f6f8fb;
  font-size: 14px;
  font-weight: 950;
}

.labeler-home__task-head span,
.labeler-home__task-row span,
.labeler-home__task-row strong {
  min-width: 0;
  padding: 0 6px;
}

.labeler-home__task-row {
  min-height: 38px;
  border-bottom: 1px solid #f0f3f7;
}

.labeler-home__task-row:last-child {
  border-bottom: 0;
}

.labeler-home__task-row strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.labeler-home__task-row > span {
  color: #475569;
  font-size: 13px;
  font-weight: 750;
}

.labeler-home__empty {
  display: grid;
  min-height: 196px;
  place-items: center;
  color: #94a3b8;
  font-size: 14px;
  font-weight: 800;
}

.labeler-home__quota-panel {
  display: grid;
  grid-area: quota;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 8px;
  padding: 16px 18px 12px;
}

.labeler-home__quota-chart {
  min-height: 210px;
}

.labeler-home__announcement {
  display: grid;
  grid-area: announcement;
  min-height: 0;
  place-items: center;
  background: #f8fbff;
}

.labeler-home__announcement img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

@media (max-width: 1160px) {
  .labeler-home {
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
  .labeler-home {
    padding: 12px;
  }

  .labeler-home__metric-grid {
    grid-template-columns: 1fr;
  }

  .labeler-home__metric-card {
    border-right: 0;
    border-bottom: 1px solid #edf2f7;
    padding: 10px 0;
  }

  .labeler-home__metric-card:last-child {
    border-bottom: 0;
  }

  .labeler-home__profile {
    grid-template-columns: 64px minmax(0, 1fr);
  }

  .labeler-home__profile :deep(.el-tag) {
    grid-column: 1 / -1;
    width: fit-content;
  }

  .labeler-home__task-head,
  .labeler-home__task-row {
    grid-template-columns: 38px minmax(0, 1fr) 72px 62px;
    column-gap: 4px;
  }

  .labeler-home__quota-panel {
    grid-column: 1;
    grid-row: auto;
  }
}
</style>
