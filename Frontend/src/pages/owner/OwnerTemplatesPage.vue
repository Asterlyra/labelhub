<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import loadingAnimation from '../../icon/loading.gif'
import { DeleteTemplate, GetMyTemplates, GetTemplates } from '../../utils/api'

interface TemplateListItem {
  id: string
  name: string
  description: string
  creatorNickname: string
  componentCount: number
  createdAt: string
  tags: string[]
}

interface BackendTemplateRecord {
  id?: string | number
  templateId?: string | number
  name?: string
  templateName?: string
  title?: string
  description?: string
  desc?: string
  components?: unknown[]
  fields?: number
  fieldCount?: number
  componentCount?: number
  nickname?: string
  creatorNickname?: string
  creatorName?: string
  createBy?: string
  creator?: string
  updatedAt?: string
  updateTime?: string
  createdAt?: string
  createTime?: string
  tags?: unknown
  category?: string
  scene?: string
  owner?: string
  [key: string]: unknown
}

type TemplateListScope = 'all' | 'mine'

const router = useRouter()

const templatePageSize = 8
const templateStatusOptions = [
  { value: '', label: '全部状态' },
  { value: '1', label: '已启用' },
  { value: '0', label: '未启用' },
]
const templateScopeTabs: Array<{ key: TemplateListScope; label: string }> = [
  { key: 'all', label: '模板列表' },
  { key: 'mine', label: '我的模板' },
]

const templates = shallowRef<TemplateListItem[]>([])
const isLoadingTemplates = shallowRef(false)
const isPageSwitchLoadingOpen = shallowRef(false)
const templateErrorMessage = shallowRef('')
const listAnimationKey = shallowRef(0)

const currentPage = shallowRef(1)

// 这个专门用于 icon 序号显示，避免翻页请求过程中旧数据使用新页码
const displayPage = shallowRef(1)

const totalTemplates = shallowRef(0)
const deletingTemplateId = shallowRef('')
const isCreateDialogOpen = shallowRef(false)
const createErrorMessage = shallowRef('')
const activeTemplateScope = shallowRef<TemplateListScope>('all')
const templateKeyword = shallowRef('')
const templateStatusFilter = shallowRef('')

const createForm = reactive({
  name: '',
  description: '',
})

const activeTemplateScopeLabel = computed(() =>
  activeTemplateScope.value === 'mine' ? '我的模板' : '模板列表',
)

const emptyTemplateText = computed(() =>
  activeTemplateScope.value === 'mine' ? '暂无我的模板' : '暂无模板',
)

const templateCountText = computed(() =>
  isLoadingTemplates.value ? '加载中' : `共 ${totalTemplates.value} 个模板`,
)

const shouldShowInlineLoading = computed(
  () => isLoadingTemplates.value && !isPageSwitchLoadingOpen.value && templates.value.length === 0,
)

const loadingTextChars = '正在加载中.....'.split('')

let pageSwitchLoadingSeed = 0
let templateListRequestSeed = 0

function getResponsePayload(response: any) {
  return response?.data ?? response
}

function pickTemplateArray(response: any) {
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

function readString(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : fallback
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

function readTags(record: BackendTemplateRecord) {
  if (Array.isArray(record.tags)) {
    return record.tags.filter((tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()))
  }

  return [record.scene, record.category, record.owner].filter(
    (tag): tag is string => typeof tag === 'string' && Boolean(tag.trim()),
  )
}

function normalizeTemplateRecord(record: unknown, index: number): TemplateListItem {
  const template = (record ?? {}) as BackendTemplateRecord
  const components = Array.isArray(template.components) ? template.components : []

  return {
    id: String(template.id ?? template.templateId ?? `template-${index}`),
    name: readString(template.name ?? template.templateName ?? template.title, `未命名模板 ${index + 1}`),
    description: readString(template.description ?? template.desc, '暂无模板描述'),
    creatorNickname: readString(
      template.nickname ?? template.creatorNickname ?? template.creatorName ?? template.createBy ?? template.creator,
      '创建人',
    ),
    componentCount: readNumber(
      template.componentCount ?? template.fieldCount ?? template.fields,
      components.length,
    ),
    createdAt: readString(
      template.createdAt ?? template.createTime ?? template.updatedAt ?? template.updateTime,
      '暂无创建时间',
    ),
    tags: readTags(template),
  }
}

function normalizeTemplateList(response: any) {
  return pickTemplateArray(response).map((item, index) => normalizeTemplateRecord(item, index))
}

function waitForLoadingDuration() {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, 1000)
  })
}

async function playPageSwitchLoading() {
  pageSwitchLoadingSeed += 1
  const currentSeed = pageSwitchLoadingSeed

  isPageSwitchLoadingOpen.value = true
  await waitForLoadingDuration()

  if (currentSeed === pageSwitchLoadingSeed) {
    isPageSwitchLoadingOpen.value = false
  }
}

async function loadTemplates(pageNum = currentPage.value, showPageSwitchLoading = false) {
  const requestSeed = (templateListRequestSeed += 1)
  const requestScope = activeTemplateScope.value
  const requestTemplates = requestScope === 'mine' ? GetMyTemplates : GetTemplates

  isLoadingTemplates.value = true
  templateErrorMessage.value = ''

  const loadingTask = showPageSwitchLoading ? playPageSwitchLoading() : Promise.resolve()

  try {
    const response = await requestTemplates({
      pageNum,
      pageSize: templatePageSize,
      keyword: templateKeyword.value.trim() || undefined,
      status: templateStatusFilter.value ? Number(templateStatusFilter.value) : undefined,
    })

    const normalizedTemplates = normalizeTemplateList(response)
    const nextTemplates = normalizedTemplates.slice(0, templatePageSize)
    const nextTotalTemplates = pickPaginationTotal(response, normalizedTemplates.length)

    await loadingTask

    if (requestSeed !== templateListRequestSeed || requestScope !== activeTemplateScope.value) {
      return
    }

    templates.value = nextTemplates
    totalTemplates.value = nextTotalTemplates

    // 数据真正回来之后，再更新当前页和展示页
    currentPage.value = pageNum
    displayPage.value = pageNum

    listAnimationKey.value += 1
  } catch (error: any) {
    await loadingTask

    if (requestSeed !== templateListRequestSeed || requestScope !== activeTemplateScope.value) {
      return
    }

    templateErrorMessage.value =
      error?.response?.data?.message || error?.response?.data?.msg || `${activeTemplateScopeLabel.value}获取失败`

    listAnimationKey.value += 1
    ElMessage.error(templateErrorMessage.value)
  } finally {
    if (requestSeed === templateListRequestSeed) {
      isLoadingTemplates.value = false
    }
  }
}

function handleTemplateFilterSubmit() {
  currentPage.value = 1
  displayPage.value = 1
  void loadTemplates(1, true)
}

function handleTemplateFilterReset() {
  templateKeyword.value = ''
  templateStatusFilter.value = ''
  handleTemplateFilterSubmit()
}

function handlePageChange(pageNum: number) {
  loadTemplates(pageNum, true)
}

function handleTemplateScopeChange(scope: TemplateListScope) {
  if (activeTemplateScope.value === scope) {
    return
  }

  activeTemplateScope.value = scope
  templates.value = []
  totalTemplates.value = 0
  currentPage.value = 1
  displayPage.value = 1
  loadTemplates(1, true)
}

async function handleDeleteTemplate(template: TemplateListItem) {
  if (deletingTemplateId.value) {
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除模板「${template.name}」吗？`, '删除模板', {
      cancelButtonText: '取消',
      confirmButtonText: '删除',
      type: 'warning',
    })
  } catch {
    return
  }

  deletingTemplateId.value = template.id

  try {
    const response = await DeleteTemplate(template.id)
    const isSuccess = response?.code === undefined || Number(response?.code) === 200

    if (!isSuccess) {
      ElMessage.error(response?.message || response?.msg || '模板删除失败')
      return
    }

    ElMessage.success(response?.message || response?.msg || '模板删除成功')

    const nextPage =
      templates.value.length === 1 && currentPage.value > 1
        ? currentPage.value - 1
        : currentPage.value

    await loadTemplates(nextPage)
  } catch (error: any) {
    const errorMessage =
      error?.response?.data?.message || error?.response?.data?.msg || error?.message || '模板删除失败'

    ElMessage.error(errorMessage)
  } finally {
    deletingTemplateId.value = ''
  }
}

function handleCreateTemplate() {
  createErrorMessage.value = ''
  isCreateDialogOpen.value = true
}

function handleCloseCreateDialog() {
  isCreateDialogOpen.value = false
  createErrorMessage.value = ''
}

function handleDialogEscape(event: KeyboardEvent) {
  if (event.key === 'Escape' && isCreateDialogOpen.value) {
    handleCloseCreateDialog()
  }
}

function handleCreateTemplateSubmit() {
  const name = createForm.name.trim()
  const description = createForm.description.trim()

  if (!name || !description) {
    createErrorMessage.value = '请填写模板名称和模板描述。'
    return
  }

  router.push({
    path: '/owner/templates/designer',
    query: {
      name,
      description,
    },
  })
}

function handleEditTemplate(template: TemplateListItem) {
  router.push({
    name: 'owner-template-designer',
    params: {
      templateId: template.id,
    },
  })
}

onMounted(() => {
  window.addEventListener('keydown', handleDialogEscape)
  loadTemplates()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleDialogEscape)
})
</script>

<template>
  <section class="template-page">
    <header class="template-page__header">
      <p class="template-page__eyebrow">项目管理者</p>
      <h1>模板搭建</h1>
      <p>选择已有模板快速创建任务结构，或新建适合当前项目的数据标注模板。</p>
    </header>

    <div class="template-page__toolbar">
      <div class="template-page__list-summary">
        <div class="template-page__tabs" role="tablist" aria-label="模板列表筛选">
          <button
            v-for="tab in templateScopeTabs"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="activeTemplateScope === tab.key"
            :class="{ 'template-page__tab--active': activeTemplateScope === tab.key }"
            @click="handleTemplateScopeChange(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
        <span>{{ templateCountText }}</span>
      </div>

      <button class="template-page__create" type="button" @click="handleCreateTemplate">
        创建模板
      </button>
    </div>

    <form class="template-page__filters" aria-label="模板筛选" @submit.prevent="handleTemplateFilterSubmit">
      <el-input
        v-model="templateKeyword"
        class="template-page__filter-search"
        clearable
        placeholder="搜索模板名称或描述"
        aria-label="搜索模板名称或描述"
      />
      <el-select v-model="templateStatusFilter" class="template-page__filter-select" aria-label="模板状态">
        <el-option v-for="option in templateStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <button class="template-page__filter-submit" type="submit">筛选</button>
      <button class="template-page__filter-reset" type="button" @click="handleTemplateFilterReset">重置</button>
    </form>

    <Transition name="template-page-loading">
      <div
        v-if="isPageSwitchLoadingOpen"
        class="template-page__loading-backdrop"
        role="presentation"
      >
        <section class="template-page__loading-dialog" aria-label="正在加载中">
          <img class="template-page__loading-image" :src="loadingAnimation" alt="">
          <p class="template-page__loading-text" aria-live="polite">
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

    <Transition name="template-page-dialog">
      <div
        v-if="isCreateDialogOpen"
        class="template-page__dialog-backdrop"
        role="presentation"
        @click.self="handleCloseCreateDialog"
      >
        <form
          class="template-page__dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="template-create-dialog-title"
          @submit.prevent="handleCreateTemplateSubmit"
        >
          <header class="template-page__dialog-header">
            <span>创建模板</span>
            <strong id="template-create-dialog-title">填写模板基础信息</strong>
            <button
              class="template-page__dialog-close"
              type="button"
              aria-label="关闭创建模板弹窗"
              @click="handleCloseCreateDialog"
            >
              ×
            </button>
          </header>

          <label class="template-page__dialog-field">
            <span>模板名称</span>
            <input
              v-model.trim="createForm.name"
              name="templateName"
              placeholder="商品信息标注模板"
            >
          </label>

          <label class="template-page__dialog-field">
            <span>模板描述</span>
            <textarea
              v-model.trim="createForm.description"
              name="templateDescription"
              placeholder="用于商品数据清洗和标注的综合模板"
            ></textarea>
          </label>

          <p v-if="createErrorMessage" class="template-page__dialog-error" role="alert">
            {{ createErrorMessage }}
          </p>

          <footer class="template-page__dialog-actions">
            <button type="button" @click="handleCloseCreateDialog">取消</button>
            <button class="template-page__dialog-primary" type="submit">进入搭建器</button>
          </footer>
        </form>
      </div>
    </Transition>

    <div class="template-page__list" aria-label="模板数据列表">
      <Transition name="template-page-list-content" mode="out-in">
        <div :key="listAnimationKey" class="template-page__list-content">
          <div v-if="shouldShowInlineLoading" class="template-page__state">
            <strong>正在加载模板</strong>
            <span>请稍候。</span>
          </div>

          <div v-else-if="templateErrorMessage" class="template-page__state template-page__state--error">
            <strong>{{ templateErrorMessage }}</strong>
            <button type="button" @click="loadTemplates(currentPage)">重新获取</button>
          </div>

          <div v-else-if="templates.length === 0" class="template-page__state">
            <strong>{{ emptyTemplateText }}</strong>
            <span>可以点击右上角创建模板。</span>
          </div>

          <template v-else>
            <article
              v-for="(template, index) in templates"
              :key="template.id"
              class="template-page__row"
            >
              <div class="template-page__card-body">
                <div class="template-page-icon-position">
                  <img class="template-page-icon" src="../../icon/right-top.png" alt="">
                  <img class="template-page-png" src="../../icon/moban.png" alt="">
                  <span class="template-page-icon-index">
                    {{ (displayPage - 1) * templatePageSize + index + 1 }}
                  </span>
                </div>

                <strong class="template-page__name">{{ template.name }}</strong>
                <p class="template-page__description">{{ template.description }}</p>

                <div class="template-page__meta">
                  <div class="template-page__tags" aria-label="分类标签">
                    <span v-for="tag in template.tags" :key="tag">{{ tag }}</span>
                    <span v-if="template.tags.length === 0">未分类</span>
                  </div>
                  <span class="template-page__time">创建时间：{{ template.createdAt }}</span>
                </div>
              </div>

              <div class="template-page__actions">
                <button class="template-page__use" type="button" @click="handleEditTemplate(template)">
                  编辑模板
                </button>
                <button
                  class="template-page__delete"
                  type="button"
                  :disabled="deletingTemplateId === template.id"
                  @click="handleDeleteTemplate(template)"
                >
                  {{ deletingTemplateId === template.id ? '删除中' : '删除模板' }}
                </button>
              </div>
            </article>
          </template>
        </div>
      </Transition>

      <div v-if="!templateErrorMessage && totalTemplates > 0" class="template-page__pagination">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="currentPage"
          :disabled="isLoadingTemplates"
          :page-size="templatePageSize"
          :total="totalTemplates"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.template-page {
  display: grid;
  gap: 22px;
}

.template-page__header {
  display: grid;
  gap: 10px;
  max-width: 760px;
}

.template-page__eyebrow {
  margin: 0;
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 750;
}

.template-page__header h1 {
  margin: 0;
  color: var(--lh-text-primary);
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.12;
}

.template-page__header p:last-child {
  margin: 0;
  color: var(--lh-text-secondary);
  line-height: 1.7;
}

.template-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid var(--lh-border-soft);
  padding-bottom: 12px;
}

.template-page__filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.78);
  padding: 14px;
}

.template-page__filter-search {
  flex: 1 1 280px;
  min-width: 220px;
}

.template-page__filter-select {
  width: 150px;
}

.template-page__filter-submit,
.template-page__filter-reset {
  min-height: 36px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 14px;
}

.template-page__filter-submit {
  border: 1px solid var(--lh-accent);
  background: var(--lh-accent);
  color: #ffffff;
}

.template-page__filter-reset {
  border: 1px solid var(--lh-border-strong);
  background: #ffffff;
  color: var(--lh-text-secondary);
}

.template-page__list-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.template-page__tabs {
  display: inline-flex;
  gap: 4px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  padding: 3px;
}

.template-page__tabs button {
  min-height: 32px;
  border: 0;
  border-radius: 6px;
  outline: none;
  background: transparent;
  color: var(--lh-text-secondary);
  cursor: pointer;
  font-size: 13px;
  font-weight: 800;
  padding: 0 12px;
  transition:
    background 0.2s ease,
    box-shadow 0.2s ease,
    color 0.2s ease;
}

.template-page__tabs button:hover {
  background: rgba(23, 32, 51, 0.06);
  color: var(--lh-text-primary);
}

.template-page__tabs button:focus-visible {
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.14);
}

.template-page__tabs button.template-page__tab--active {
  background: #172033;
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(23, 32, 51, 0.12);
}

.template-page__list-summary span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 750;
}

.template-page__create {
  min-height: 38px;
  border: 0;
  border-radius: 999px;
  background: #172033;
  color: #ffffff;
  cursor: pointer;
  font-weight: 750;
  padding: 0 18px;
  box-shadow: 0 12px 28px rgba(23, 32, 51, 0.16);
}

.template-page__loading-backdrop {
  position: fixed;
  z-index: 34;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(20, 32, 54, 0.2);
  padding: 24px;
  backdrop-filter: blur(2px);
}

.template-page__loading-dialog {
  display: grid;
  width: min(100%, 260px);
  justify-items: center;
  gap: 12px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
  padding: 26px 28px 24px;
}

.template-page__loading-image {
  display: block;
  width: 86px;
  height: 86px;
  object-fit: contain;
}

.template-page__loading-text {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  justify-content: center;
  gap: 1px;
  margin: 0;
  color: var(--lh-text-primary);
  font-size: 16px;
  font-weight: 850;
  letter-spacing: 0;
  white-space: nowrap;
}

.template-page__loading-text span {
  display: inline-block;
  animation: template-loading-text-bounce 0.72s cubic-bezier(0.2, 0.82, 0.2, 1) infinite;
}

.template-page-loading-enter-active,
.template-page-loading-leave-active {
  transition:
    opacity 0.28s ease,
    backdrop-filter 0.28s ease;
}

.template-page-loading-enter-active .template-page__loading-dialog {
  animation: template-loading-dialog-in 0.32s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.template-page-loading-leave-active .template-page__loading-dialog {
  animation: template-loading-dialog-out 0.24s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.template-page-loading-enter-from,
.template-page-loading-leave-to {
  opacity: 0;
  backdrop-filter: blur(0);
}

.template-page__dialog-backdrop {
  position: fixed;
  z-index: 30;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(20, 32, 54, 0.22);
  padding: 24px;
  backdrop-filter: blur(18px);
}

.template-page__dialog {
  display: grid;
  width: min(100%, 480px);
  gap: 18px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
  padding: 24px;
}

.template-page__dialog-header {
  position: relative;
  display: grid;
  gap: 6px;
  padding-right: 44px;
}

.template-page__dialog-header span {
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 800;
}

.template-page__dialog-header strong {
  color: var(--lh-text-primary);
  font-size: 22px;
  line-height: 1.2;
}

.template-page__dialog-close {
  position: absolute;
  top: -4px;
  right: 0;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-secondary);
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background 0.2s ease;
}

.template-page__dialog-close:hover {
  border-color: rgba(0, 113, 227, 0.28);
  background: #ffffff;
  color: var(--lh-accent);
}

.template-page__dialog-field {
  display: grid;
  gap: 8px;
}

.template-page__dialog-field span {
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 750;
}

.template-page__dialog-field input,
.template-page__dialog-field textarea {
  width: 100%;
  border: 1px solid rgba(23, 32, 51, 0.1);
  border-radius: 14px;
  outline: none;
  background: rgba(255, 255, 255, 0.82);
  color: var(--lh-text-primary);
  padding: 0 14px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.template-page__dialog-field input {
  min-height: 46px;
}

.template-page__dialog-field textarea {
  min-height: 110px;
  padding-top: 12px;
  resize: none;
}

.template-page__dialog-field input:focus,
.template-page__dialog-field textarea:focus {
  border-color: rgba(0, 113, 227, 0.42);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.12);
}

.template-page__dialog-error {
  margin: 0;
  border: 1px solid rgba(255, 59, 48, 0.22);
  border-radius: 12px;
  background: rgba(255, 59, 48, 0.1);
  color: #9f1f18;
  font-size: 13px;
  font-weight: 650;
  padding: 10px 12px;
}

.template-page__dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.template-page__dialog-actions button {
  min-height: 40px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 750;
  padding: 0 16px;
}

.template-page__dialog-actions .template-page__dialog-primary {
  border-color: transparent;
  background: var(--lh-accent);
  color: #ffffff;
  box-shadow: 0 10px 24px rgba(0, 113, 227, 0.2);
}

.template-page-dialog-enter-active,
.template-page-dialog-leave-active {
  transition:
    opacity 0.34s ease,
    backdrop-filter 0.34s ease;
}

.template-page-dialog-enter-active .template-page__dialog {
  animation: template-dialog-in 0.38s cubic-bezier(0.2, 0.82, 0.2, 1) both;
}

.template-page-dialog-leave-active .template-page__dialog {
  animation: template-dialog-out 0.28s cubic-bezier(0.4, 0, 0.2, 1) both;
}

.template-page-dialog-enter-from,
.template-page-dialog-leave-to {
  opacity: 0;
  backdrop-filter: blur(0);
}

@keyframes template-dialog-in {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.96);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes template-dialog-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }

  to {
    opacity: 0;
    transform: translateY(12px) scale(0.97);
  }
}

@keyframes template-loading-dialog-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.96);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes template-loading-dialog-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }

  to {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }
}

@keyframes template-loading-text-bounce {
  0%,
  72%,
  100% {
    transform: translateY(0);
  }

  34% {
    transform: translateY(-7px);
  }
}

.template-page__list {
  display: grid;
  min-height: calc(100vh - 330px);
  grid-template-rows: minmax(356px, 1fr) auto;
  gap: 12px;
}

.template-page__list-content {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-content: start;
  gap: 12px;
}

.template-page-list-content-enter-active {
  transition:
    opacity 0.56s ease,
    filter 0.6s ease,
    transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.template-page-list-content-leave-active {
  transition:
    opacity 0.22s ease,
    filter 0.24s ease,
    transform 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.template-page-list-content-enter-from {
  opacity: 0;
  filter: blur(14px);
  transform: translateY(18px) scale(0.992);
}

.template-page-list-content-leave-to {
  opacity: 0;
  filter: blur(8px);
  transform: translateY(-6px) scale(0.996);
}

.template-page__state {
  display: grid;
  grid-column: 1 / -1;
  min-height: 180px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-secondary);
  box-shadow: var(--lh-shadow-card);
  text-align: center;
}

.template-page__state strong {
  color: var(--lh-text-primary);
  font-size: 16px;
}

.template-page__state span {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 650;
}

.template-page__state button {
  min-height: 36px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 750;
  padding: 0 14px;
}

.template-page__state--error {
  border-color: rgba(255, 59, 48, 0.22);
  background: rgba(255, 59, 48, 0.08);
}

.template-page__row {
  display: grid;
  min-height: 164px;
  grid-template-rows: minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid var(--lh-border-soft);
  /* border-radius: 8px; */
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--lh-shadow-card);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.template-page__row:hover {
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 12px 28px rgba(20, 32, 54, 0.08);
  transform: translateY(-1px);
}

.template-page__card-body {
  position: relative;
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 0;
  padding: 18px 20px 16px;
}

.template-page-icon-position {
  position: absolute;
  top: 0;
  right: 0;
  width: 54px;
  height: 36px;
  pointer-events: none;
}

.template-page-icon {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.template-page-png{
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.template-page-icon-index {
  position: absolute;

  top: 15%;
  right: 38%;
  color: white;
  font-size: 20px;
  font-weight: 800;
  line-height: 1;
  text-align: center;
}

.template-page__card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.template-page__avatar {
  display: block;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border: 1px solid rgba(23, 32, 51, 0.08);
  border-radius: 999px;
  background: #ffffff;
  object-fit: cover;
  box-shadow: 0 8px 16px rgba(20, 32, 54, 0.08);
}

.template-page__card-header strong {
  overflow: hidden;
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-page__name {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 20px;
  font-weight: 850;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 46px;
}

.template-page__description {
  display: -webkit-box;
  min-height: 20px;
  margin: 0;
  overflow: hidden;
  max-width: 190px;
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.template-page__meta {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.template-page__time {
  overflow: hidden;
  color: var(--lh-text-secondary);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 24px;
}

.template-page__tags span {
  max-width: 100%;
  overflow: hidden;
  border-radius: 999px;
  background: var(--lh-bg-secondary);
  color: var(--lh-text-secondary);
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-page__actions {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 0;
  border-top: 1px solid var(--lh-border-soft);
  background: rgba(248, 250, 252, 0.76);
}

.template-page__use,
.template-page__delete {
  display: grid;
  min-height: 42px;
  flex: 1 1 0;
  place-items: center;
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
  padding: 0 12px;
  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.template-page__use {
  color: var(--lh-accent);
}

.template-page__delete {
  border-left: 1px solid var(--lh-border-soft);
  color: var(--lh-danger);
}

.template-page__use:hover {
  background: rgba(0, 113, 227, 0.08);
}

.template-page__delete:hover:not(:disabled) {
  background: rgba(255, 59, 48, 0.08);
}

.template-page__delete:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.template-page__pagination {
  display: flex;
  justify-content: center;
  align-self: end;
  padding: 8px 0 0;
}

.template-page__pagination :deep(.el-pagination.is-background .btn-next),
.template-page__pagination :deep(.el-pagination.is-background .btn-prev),
.template-page__pagination :deep(.el-pagination.is-background .el-pager li) {
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 8px 18px rgba(20, 32, 54, 0.05);
  color: var(--lh-text-secondary);
  font-weight: 750;
}

.template-page__pagination :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: var(--lh-accent);
  color: #ffffff;
}

@media (max-width: 1180px) {
  .template-page__list-content {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .template-page__list {
    min-height: auto;
    grid-template-rows: auto auto;
  }

  .template-page__list-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .template-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .template-page__list-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .template-page__tabs {
    width: 100%;
  }

  .template-page__tabs button {
    flex: 1 1 0;
  }

  .template-page__create {
    width: 100%;
  }

  .template-page__filter-search,
  .template-page__filter-select {
    width: 100%;
    min-width: 0;
    flex-basis: 100%;
  }

  .template-page__filter-submit,
  .template-page__filter-reset {
    flex: 1 1 0;
  }

  .template-page__dialog-actions {
    flex-direction: column-reverse;
  }

  .template-page__dialog-actions button {
    width: 100%;
  }
}
</style>
