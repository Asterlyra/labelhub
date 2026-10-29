<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, shallowRef, useTemplateRef, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import adminAvatarImage from '../../icon/avater.gif'
import { GetDataset, GetTasks, GetUserInfo } from '../../utils/api'

interface BackendTaskRecord {
  id?: string | number
  taskId?: string | number
  title?: string
  name?: string
  taskName?: string
  description?: string
  desc?: string
  status?: string
  createTime?: string
  createdAt?: string
  publishTime?: string
  publishedAt?: string
  releaseTime?: string
  tags?: unknown
  [key: string]: unknown
}

interface BackendDatasetRecord {
  sourceFormat?: string
  format?: string
  fileType?: string
  [key: string]: unknown
}

interface DashboardTaskItem {
  id: string
  title: string
  description: string
  createTime: string
  tags: string[]
}

const authStore = useAuthStore()
const tasks = shallowRef<BackendTaskRecord[]>([])
const datasets = shallowRef<BackendDatasetRecord[]>([])
const adminUserInfo = shallowRef<Record<string, unknown>>({})
const totalTasks = shallowRef(0)
const isLoadingDashboard = shallowRef(false)
const dashboardErrorMessage = shallowRef('')
const datasetChartRef = useTemplateRef<HTMLDivElement>('datasetChart')
const taskTrendChartRef = useTemplateRef<HTMLDivElement>('taskTrendChart')
const datasetChart = shallowRef<echarts.ECharts | null>(null)
const taskTrendChart = shallowRef<echarts.ECharts | null>(null)

const taskStatusStats = computed(() => {
  const stats = {
    published: 0,
    draft: 0,
    ended: 0,
  }

  tasks.value.forEach((task) => {
    const status = normalizeTaskStatusType(task.status)

    if (status === 'published') {
      stats.published += 1
    }

    if (status === 'draft') {
      stats.draft += 1
    }

    if (status === 'ended') {
      stats.ended += 1
    }
  })

  return stats
})

const latestTasks = computed<DashboardTaskItem[]>(() =>
  tasks.value.slice(0, 2).map((task, index) => ({
    id: String(task.taskId ?? task.id ?? `task-${index}`),
    title: readString(task.title ?? task.name ?? task.taskName, `未命名任务 ${index + 1}`),
    description: readString(task.description ?? task.desc, '暂无任务描述'),
    createTime: formatDateText(task.createTime ?? task.createdAt),
    tags: readTags(task.tags),
  })),
)

const datasetPieData = computed(() => {
  const formatCounter = new Map<string, number>()

  datasets.value.forEach((dataset) => {
    const sourceFormat = readString(dataset.sourceFormat ?? dataset.format ?? dataset.fileType, 'UNKNOWN').toUpperCase()
    formatCounter.set(sourceFormat, (formatCounter.get(sourceFormat) ?? 0) + 1)
  })

  return Array.from(formatCounter.entries()).map(([name, value]) => ({ name, value }))
})

const adminProfile = computed(() => ({
  ...(authStore.userInfo as Record<string, unknown>),
  ...adminUserInfo.value,
}))

const adminDisplayName = computed(() =>
  readString(adminProfile.value.nickname ?? adminProfile.value.name, authStore.currentUser.name),
)

const adminUsername = computed(() =>
  readString(adminProfile.value.username, authStore.activeUsername),
)

const adminRoleLabel = computed(() => formatAdminRole(readRoleValue(adminProfile.value) ?? authStore.activeRole))

const adminInfoItems = computed(() => [
  { label: '用户 ID', value: readString(adminProfile.value.id, authStore.currentUser.id) },
  { label: '登录账号', value: adminUsername.value },
  { label: '邮箱', value: readString(adminProfile.value.email, '暂无邮箱') },
  { label: '手机号', value: readString(adminProfile.value.phone, '暂无手机号') },
])

const taskTrendData = computed(() => {
  const days = getRecentDays(7)
  const dayCounter = new Map(days.map((day) => [day.key, 0]))

  tasks.value.forEach((task) => {
    const dateKey = getDateKey(task.publishTime ?? task.publishedAt ?? task.releaseTime ?? task.createTime ?? task.createdAt)

    if (dateKey && dayCounter.has(dateKey)) {
      dayCounter.set(dateKey, (dayCounter.get(dateKey) ?? 0) + 1)
    }
  })

  return {
    labels: days.map((day) => day.label),
    values: days.map((day) => dayCounter.get(day.key) ?? 0),
  }
})

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

function formatAdminRole(value: unknown) {
  const role = readString(value, '').toUpperCase()

  if (['PROJECT_ADMIN', 'OWNER', 'ADMIN'].includes(role)) {
    return '管理员'
  }

  if (role === 'LABELER') {
    return '标注员'
  }

  if (role === 'REVIEWER') {
    return '审核员'
  }

  return readString(value, '管理员')
}

function normalizeTaskStatusType(value: unknown) {
  const status = readString(value, '').toUpperCase()

  if (['PUBLISHED', 'PUBLISHING'].includes(status) || status.includes('发布')) {
    return 'published'
  }

  if (status === 'DRAFT' || status.includes('草稿')) {
    return 'draft'
  }

  if (['ENDED', 'END', 'COMPLETED'].includes(status) || status.includes('结束')) {
    return 'ended'
  }

  return status.toLowerCase()
}

function getResponsePayload(response: any) {
  const payload = response?.data ?? response

  return payload?.data ?? payload
}

function isApiSuccess(response: any) {
  return response?.code === undefined || Number(response.code) === 200
}

function pickUserInfo(response: any) {
  const payload = getResponsePayload(response)
  const userInfo = payload?.user ?? payload?.userInfo ?? payload

  return userInfo && typeof userInfo === 'object'
    ? (userInfo as Record<string, unknown>)
    : {}
}

function pickResponseArray(response: any) {
  const payload = response?.data ?? response
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
  const candidates = [payload?.total, payload?.totalCount, payload?.totalElements, payload?.count]
  const total = candidates.map((item) => readNumber(item, Number.NaN)).find((item) => Number.isFinite(item))

  return total ?? fallback
}

function readTags(value: unknown) {
  if (Array.isArray(value)) {
    return value.map((tag) => readString(tag)).filter((tag) => Boolean(tag))
  }

  if (typeof value === 'string') {
    try {
      const parsedTags = JSON.parse(value)

      if (Array.isArray(parsedTags)) {
        return readTags(parsedTags)
      }
    } catch {
      return value
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => Boolean(tag))
    }
  }

  return []
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

function formatDateText(value: unknown) {
  const dateKey = getDateKey(value)

  return dateKey || '暂无创建时间'
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
      label: `${month}-${day}`,
    }
  })
}

function getDatasetPieOption(): echarts.EChartsOption {
  const data = datasetPieData.value

  return {
    color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}<br/>{c} 个 ({d}%)',
    },
    legend: {
      type: 'scroll',
      top: 8,
      left: 'center',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: {
        color: '#526173',
        fontSize: 12,
      },
    },
    series: [
      {
        name: '数据集类型',
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['50%', '58%'],
        avoidLabelOverlap: false,
        padAngle: 5,
        itemStyle: {
          borderRadius: 10,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            formatter: '{b}\n{d}%',
            color: '#1f2937',
            fontSize: 18,
            fontWeight: 'bold',
          },
          itemStyle: {
            shadowBlur: 12,
            shadowOffsetX: 0,
            shadowColor: 'rgba(15, 23, 42, 0.22)',
          },
        },
        labelLine: {
          show: false,
        },
        data,
      },
    ],
    graphic: data.length
      ? []
      : [
          {
            type: 'text',
            left: 'center',
            top: 'middle',
            style: {
              text: '暂无数据',
              fill: '#94a3b8',
              fontSize: 14,
            },
          },
        ],
  }
}

function getTaskTrendOption(): echarts.EChartsOption {
  return {
    color: ['#5b7cfa'],
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const item = Array.isArray(params)
          ? params[0] as { axisValue?: unknown; value?: unknown }
          : null

        return item ? `${item.axisValue}<br/>任务数量：${item.value}` : ''
      },
    },
    grid: {
      top: 42,
      right: 28,
      bottom: 32,
      left: 42,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: taskTrendData.value.labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#d9e2ef' } },
      axisLabel: { color: '#718096' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf2f7' } },
      axisLabel: { color: '#718096' },
    },
    series: [
      {
        name: '任务数量',
        type: 'line',
        data: taskTrendData.value.values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 3,
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(91, 124, 250, 0.28)' },
            { offset: 1, color: 'rgba(91, 124, 250, 0.03)' },
          ]),
        },
      },
    ],
  }
}

function initCharts() {
  if (datasetChartRef.value && !datasetChart.value) {
    datasetChart.value = echarts.init(datasetChartRef.value)
  }

  if (taskTrendChartRef.value && !taskTrendChart.value) {
    taskTrendChart.value = echarts.init(taskTrendChartRef.value)
  }

  renderCharts()
}

function renderCharts() {
  datasetChart.value?.setOption(getDatasetPieOption(), true)
  taskTrendChart.value?.setOption(getTaskTrendOption(), true)
}

function resizeCharts() {
  datasetChart.value?.resize()
  taskTrendChart.value?.resize()
}

async function loadDashboardData() {
  const satoken = localStorage.getItem('satoken')

  isLoadingDashboard.value = true
  dashboardErrorMessage.value = ''
  adminUserInfo.value = authStore.userInfo as Record<string, unknown>

  try {
    const userInfoRequest = satoken
      ? GetUserInfo(satoken).catch(() => null)
      : Promise.resolve(null)
    const [taskResponse, datasetResponse, userInfoResponse] = await Promise.all([
      GetTasks({ pageNum: 1, pageSize: 1000 }),
      GetDataset({ pageNum: 1, pageSize: 1000 }),
      userInfoRequest,
    ])
    const taskList = pickResponseArray(taskResponse) as BackendTaskRecord[]
    const datasetList = pickResponseArray(datasetResponse) as BackendDatasetRecord[]
    const fetchedUserInfo = userInfoResponse && isApiSuccess(userInfoResponse)
      ? pickUserInfo(userInfoResponse)
      : {}

    tasks.value = taskList
    datasets.value = datasetList
    adminUserInfo.value = {
      ...(authStore.userInfo as Record<string, unknown>),
      ...fetchedUserInfo,
    }
    totalTasks.value = pickPaginationTotal(taskResponse, taskList.length)

    if (Object.keys(fetchedUserInfo).length) {
      authStore.userInfo = adminUserInfo.value
      authStore.persistAuthState()
    }

    await nextTick()
    renderCharts()
  } catch (error: any) {
    dashboardErrorMessage.value = error?.response?.data?.message ?? error?.message ?? '首页数据加载失败'
    ElMessage.error(dashboardErrorMessage.value)
  } finally {
    isLoadingDashboard.value = false
  }
}

onMounted(async () => {
  await nextTick()
  initCharts()
  window.addEventListener('resize', resizeCharts)
  await loadDashboardData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  datasetChart.value?.dispose()
  taskTrendChart.value?.dispose()
})

watch([datasetPieData, taskTrendData], () => {
  nextTick(renderCharts)
})
</script>

<template>
  <section class="dashboard-page" v-loading="isLoadingDashboard">
    <p v-if="dashboardErrorMessage" class="dashboard-page__error">{{ dashboardErrorMessage }}</p>

    <section class="dashboard-page__dashboard-grid">
      <article class="dashboard-page__panel dashboard-page__panel--tasks">
        <header class="dashboard-page__panel-header">
          <h2>任务列表</h2>
        </header>

        <div class="dashboard-page__task-summary">
          <div class="dashboard-page__task-total">
            <span>当前任务总数</span>
            <strong>{{ totalTasks }}</strong>
          </div>

          <div class="dashboard-page__status-grid">
            <div>
              <strong>{{ taskStatusStats.published }}</strong>
              <span>已发布</span>
            </div>
            <div>
              <strong>{{ taskStatusStats.draft }}</strong>
              <span>草稿</span>
            </div>
            <div>
              <strong>{{ taskStatusStats.ended }}</strong>
              <span>已结束</span>
            </div>
          </div>
        </div>

        <div class="dashboard-page__task-list">
          <div v-if="!latestTasks.length" class="dashboard-page__empty">暂无任务数据</div>
          <div v-else class="dashboard-page__task-table">
            <div class="dashboard-page__task-table-head">
              <span>任务名称</span>
              <span>任务描述</span>
              <span>创建时间</span>
              <span>标签</span>
              <span>操作</span>
            </div>
            <article v-for="task in latestTasks" :key="task.id" class="dashboard-page__task-row">
              <strong>{{ task.title }}</strong>
              <p>{{ task.description }}</p>
              <time>{{ task.createTime }}</time>
              <div class="dashboard-page__task-tags">
                <el-tag
                  v-for="tag in task.tags.slice(0, 2)"
                  :key="tag"
                  class="dashboard-page__task-tag"
                  effect="plain"
                  size="small"
                >
                  {{ tag }}
                </el-tag>
                <el-tag
                  v-if="!task.tags.length"
                  class="dashboard-page__task-tag"
                  effect="plain"
                  size="small"
                  type="info"
                >
                  暂无标签
                </el-tag>
              </div>
              <RouterLink
                class="dashboard-page__task-detail"
                :to="{ name: 'owner-task-detail', params: { taskId: task.id } }"
              >
                详情
              </RouterLink>
            </article>
          </div>
        </div>
      </article>

      <article class="dashboard-page__panel dashboard-page__admin-card">
        <header class="dashboard-page__panel-header">
          <h2>个人信息</h2>
        </header>

        <div class="dashboard-page__admin-profile">
          <img class="dashboard-page__admin-avatar" :src="adminAvatarImage" alt="">
          <div class="dashboard-page__admin-main">
            <strong>{{ adminDisplayName }}</strong>
            <span>{{ adminUsername }}</span>
          </div>
          <el-tag effect="plain" size="large" type="success" >{{ adminRoleLabel }}</el-tag>
        </div>

        <dl class="dashboard-page__admin-details">
          <div v-for="item in adminInfoItems" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section class="dashboard-page__dashboard-grid dashboard-page__analytics-grid">
      <article class="dashboard-page__panel dashboard-page__trend-panel">
        <header class="dashboard-page__panel-header">
          <h2>任务时间统计表</h2>
          <span>最近7日</span>
        </header>
        <div ref="taskTrendChart" class="dashboard-page__line-chart" />
      </article>

      <article class="dashboard-page__panel dashboard-page__dataset-panel">
        <header class="dashboard-page__panel-header">
          <h2>数据集统计</h2>
        </header>
        <div ref="datasetChart" class="dashboard-page__pie-chart" />
      </article>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 18px;
  min-height: calc(100vh - 104px);
  padding: 24px;
  color: #1f2937;
  background: #f3f6f9;
}

.dashboard-page__dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 18px;
  align-items: stretch;
}

.dashboard-page__panel {
  overflow: hidden;
  border: 1px solid #e5eaf2;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
}

.dashboard-page__panel--tasks {
  display: grid;
  gap: 18px;
  padding-bottom: 12px;
}

.dashboard-page__admin-card {
  display: grid;
  align-content: start;
  gap: 22px;
  padding-bottom: 20px;
}

.dashboard-page__panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 0;
}

.dashboard-page__panel-header h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 1.25;
}

.dashboard-page__panel-header span {
  border: 1px solid #dde6f3;
  border-radius: 6px;
  padding: 6px 10px;
  color: #5b7cfa;
  background: #f7f9ff;
  font-size: 13px;
  font-weight: 800;
}

.dashboard-page__task-summary {
  display: grid;
  grid-template-columns: minmax(170px, 0.9fr) minmax(0, 1.5fr);
  gap: 18px;
  padding: 0 20px 16px;
  border-bottom: 1px solid #edf2f7;
}

.dashboard-page__task-total {
  display: grid;
  gap: 8px;
  align-content: center;
  min-height: 104px;
  border-right: 1px solid #edf2f7;
}

.dashboard-page__task-total span,
.dashboard-page__status-grid span {
  color: #718096;
  font-size: 13px;
  font-weight: 700;
}

.dashboard-page__task-total strong {
  color: #1f2937;
  font-size: 44px;
  line-height: 1;
}

.dashboard-page__status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.dashboard-page__status-grid div {
  display: grid;
  gap: 8px;
  justify-items: center;
  min-height: 82px;
  align-content: center;
  border-radius: 8px;
  background: #f8fafc;
}

.dashboard-page__status-grid strong {
  color: #1f2937;
  font-size: 28px;
  line-height: 1;
}

.dashboard-page__admin-profile {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 4px 20px 0;
}

.dashboard-page__admin-avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  border: 3px solid #e0f2fe;
  background: #f8fafc;
  object-fit: cover;
}

.dashboard-page__admin-main {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.dashboard-page__admin-main strong {
  overflow: hidden;
  color: #111827;
  font-size: 20px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__admin-main span {
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__admin-details {
  display: grid;
  margin: 0;
  padding: 0 20px;
}

.dashboard-page__admin-details div {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid #edf2f7;
}

.dashboard-page__admin-details dt,
.dashboard-page__admin-details dd {
  margin: 0;
  font-size: 13px;
  line-height: 1.35;
}

.dashboard-page__admin-details dt {
  flex: 0 0 auto;
  color: #8492a6;
  font-weight: 700;
}

.dashboard-page__admin-details dd {
  overflow: hidden;
  color: #1f2937;
  font-weight: 800;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__task-list {
  display: grid;
  gap: 10px;
  padding: 0 20px;
}

.dashboard-page__task-list header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dashboard-page__task-list h3 {
  margin: 0;
  color: #1f2937;
  font-size: 16px;
}

.dashboard-page__task-table {
  overflow: hidden;
  border-top: 1px solid #edf2f7;
}

.dashboard-page__task-table-head,
.dashboard-page__task-row {
  display: grid;
  grid-template-columns: minmax(128px, 1.3fr) minmax(112px, 1fr) 92px minmax(76px, 0.75fr) 56px;
  column-gap: 12px;
  align-items: center;
}

.dashboard-page__task-table-head {
  min-height: 34px;
  color: #8492a6;
  font-size: 12px;
  font-weight: 800;
}

.dashboard-page__task-table-head span,
.dashboard-page__task-row strong,
.dashboard-page__task-row p,
.dashboard-page__task-row time,
.dashboard-page__task-tags {
  min-width: 0;
}

.dashboard-page__task-row {
  min-height: 42px;
  border-bottom: 1px solid #f0f3f7;
}

.dashboard-page__task-row:last-child {
  border-bottom: 0;
}

.dashboard-page__task-row strong {
  display: block;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__task-row p {
  overflow: hidden;
  margin: 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__task-row time {
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__task-tags {
  display: flex;
  overflow: hidden;
  min-width: 0;
  flex-wrap: nowrap;
  gap: 6px;
}

.dashboard-page__task-tag {
  max-width: 74px;
  min-width: 0;
  flex: 0 1 auto;
}

.dashboard-page__task-tag :deep(.el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-page__task-detail {
  display: inline-flex;
  width: 48px;
  height: 24px;
  align-items: center;
  justify-content: center;
  justify-self: end;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  color: #3b82f6;
  background: #eff6ff;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  text-decoration: none;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background-color 0.2s ease;
}

.dashboard-page__task-detail:hover {
  border-color: #3b82f6;
  color: #ffffff;
  background: #3b82f6;
}

.dashboard-page__pie-chart {
  height: 320px;
  margin-top: 8px;
}

.dashboard-page__analytics-grid {
  align-items: stretch;
}

.dashboard-page__dataset-panel,
.dashboard-page__trend-panel {
  min-height: 390px;
}

.dashboard-page__line-chart {
  height: 320px;
}

.dashboard-page__empty {
  display: flex;
  min-height: 120px;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 14px;
}

.dashboard-page__error {
  margin: 0;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 10px 12px;
  color: #b91c1c;
  background: #fff1f2;
}

@media (max-width: 1080px) {
  .dashboard-page__dashboard-grid,
  .dashboard-page__task-summary {
    grid-template-columns: 1fr;
  }

  .dashboard-page__task-total {
    border-right: 0;
    border-bottom: 1px solid #edf2f7;
    padding-bottom: 16px;
  }

  .dashboard-page__task-table {
    overflow-x: auto;
  }

  .dashboard-page__task-table-head,
  .dashboard-page__task-row {
    min-width: 620px;
  }
}

@media (max-width: 640px) {
  .dashboard-page {
    padding: 16px;
  }

  .dashboard-page__status-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-page__pie-chart,
  .dashboard-page__line-chart {
    height: 280px;
  }
}
</style>
