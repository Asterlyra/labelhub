<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import type { WorkbenchNavigationItem } from '../../types/workbench'

const props = defineProps<{
  items: WorkbenchNavigationItem[]
  activeTaskItemId: string
  statusLabelOverrides?: Record<string, string>
  useDisplayStatusName?: boolean
}>()

const emit = defineEmits<{
  select: [item: WorkbenchNavigationItem]
}>()

const pageSize = 15
const currentPage = shallowRef(1)
const totalCount = computed(() => props.items.length)
const pageCount = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize)))
const pageStartIndex = computed(() => (currentPage.value - 1) * pageSize)
const pagedItems = computed(() => props.items.slice(pageStartIndex.value, pageStartIndex.value + pageSize))

function getItemId(item: WorkbenchNavigationItem) {
  return String(item.taskItemId ?? item.index ?? '')
}

function getItemIndex(item: WorkbenchNavigationItem, index: number) {
  const numberValue = Number(item.index ?? pageStartIndex.value + index + 1)

  return Number.isFinite(numberValue) ? numberValue : pageStartIndex.value + index + 1
}

function getStatusMeta(item: WorkbenchNavigationItem) {
  const statusValue = String(item.displayStatus ?? 'PENDING').toUpperCase()
  const statusMap: Record<string, { label: string; type: string }> = {
    PENDING: { label: '待标注', type: 'pending' },
    DRAFT: { label: '草稿', type: 'draft' },
    SUBMITTED: { label: '已提交', type: 'submitted' },
    REJECTED: { label: '已驳回', type: 'rejected' },
    REVIEWED: { label: '已通过', type: 'reviewed' },
    APPROVED: { label: '已通过', type: 'reviewed' },
  }
  const fallbackLabel =
    (props.useDisplayStatusName && typeof item.displayStatusName === 'string' && item.displayStatusName.trim()
      ? item.displayStatusName.trim()
      : undefined) ?? props.statusLabelOverrides?.[statusValue]

  if (statusMap[statusValue]) {
    return {
      ...statusMap[statusValue],
      label: fallbackLabel ?? statusMap[statusValue].label,
    }
  }

  return { label: fallbackLabel ?? statusValue, type: 'pending' }
}

function syncPageWithActiveItem() {
  const activeIndex = props.items.findIndex((item) => getItemId(item) === props.activeTaskItemId)

  if (activeIndex >= 0) {
    currentPage.value = Math.floor(activeIndex / pageSize) + 1
    return
  }

  if (currentPage.value > pageCount.value) {
    currentPage.value = pageCount.value
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
}

watch(
  () => [props.activeTaskItemId, props.items] as const,
  () => {
    syncPageWithActiveItem()
  },
  { immediate: true },
)
</script>

<template>
  <aside class="question-nav" aria-label="题目导航">
    <header class="question-nav__header">
      <strong>题目导航</strong>
      <span>共 {{ totalCount }} 题</span>
    </header>

    <div v-if="items.length" class="question-nav__list">
      <button
        v-for="(item, index) in pagedItems"
        :key="getItemId(item) || index"
        class="question-nav__item"
        :class="{
          'question-nav__item--active': activeTaskItemId === getItemId(item),
          [`question-nav__item--${getStatusMeta(item).type}`]: true,
        }"
        type="button"
        @click="emit('select', item)"
      >
        <span class="question-nav__index">#{{ String(getItemIndex(item, index)).padStart(3, '0') }}</span>
        <span class="question-nav__status">
          <i aria-hidden="true"></i>
          {{ getStatusMeta(item).label }}
        </span>
      </button>
    </div>

    <p v-else class="question-nav__empty">暂无题目</p>

    <footer v-if="totalCount > pageSize" class="question-nav__pagination">
      <el-pagination
        small
        background
        layout="prev, pager, next"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="totalCount"
        @current-change="handlePageChange"
      />
    </footer>
  </aside>
</template>

<style scoped>
.question-nav {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr) auto;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
}

.question-nav__header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #edf1f5;
  padding: 0 14px;
}

.question-nav__header strong {
  color: #111827;
  font-size: 14px;
  font-weight: 850;
}

.question-nav__header span {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.question-nav__list {
  display: grid;
  align-content: start;
  gap: 5px;
  overflow: auto;
  padding: 12px;
}

.question-nav__item {
  position: relative;
  display: grid;
  min-height: 34px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  overflow: hidden;
  padding: 0 10px;
  text-align: left;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.22s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.question-nav__item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 3px;
  border-radius: 999px;
  background: #2563eb;
  opacity: 0;
  transform: scaleY(0.4);
  transform-origin: center;
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.question-nav__item:hover {
  border-color: #d8e4f4;
  background: #f8fbff;
  transform: translateX(1px);
}

.question-nav__item--active {
  border-color: #93c5fd;
  background: #eaf3ff;
  box-shadow: 0 10px 18px rgba(37, 99, 235, 0.08);
  transform: translateX(4px);
  animation: question-nav-active-bump 0.22s cubic-bezier(0.2, 0.82, 0.2, 1);
}

.question-nav__item--active::before {
  opacity: 1;
  transform: scaleY(1);
}

.question-nav__index {
  overflow: hidden;
  font-size: 13px;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-nav__item--active .question-nav__index {
  color: #1d4ed8;
}

.question-nav__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.question-nav__status i {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.question-nav__item--pending .question-nav__status {
  color: #949494;
}

.question-nav__item--draft .question-nav__status {
  color: #f97316;
}

.question-nav__item--submitted .question-nav__status {
  color: #16a34a;
}

.question-nav__item--rejected .question-nav__status {
  color: #ef4444;
}

.question-nav__item--reviewed .question-nav__status {
  color: #2563eb;
}

.question-nav__empty {
  display: grid;
  min-height: 160px;
  place-items: center;
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  font-weight: 750;
}

.question-nav__pagination {
  display: flex;
  justify-content: center;
  border-top: 1px solid #edf1f5;
  padding: 10px 8px;
}

.question-nav__pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: center;
}

.question-nav__pagination :deep(.el-pagination.is-background .btn-next),
.question-nav__pagination :deep(.el-pagination.is-background .btn-prev),
.question-nav__pagination :deep(.el-pagination.is-background .el-pager li) {
  min-width: 24px;
  height: 24px;
  border-radius: 6px;
  margin: 0 2px;
  font-size: 12px;
  font-weight: 700;
}

@keyframes question-nav-active-bump {
  0% {
    transform: translateX(0) scale(0.992);
  }

  100% {
    transform: translateX(4px) scale(1);
  }
}
</style>
