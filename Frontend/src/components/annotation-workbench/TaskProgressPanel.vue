<script setup lang="ts">
import { computed } from 'vue'
import type { WorkbenchProgress } from '../../types/workbench'

const props = defineProps<{
  progress: WorkbenchProgress | null
  variant?: 'labeling' | 'review'
}>()

const isReviewVariant = computed(() => props.variant === 'review')
const totalCount = computed(() => readNumber(props.progress?.totalCount ?? props.progress?.total))
const pendingCount = computed(() => readNumber(props.progress?.pendingCount ?? props.progress?.pending))
const draftCount = computed(() => readNumber(props.progress?.draft))
const submittedCount = computed(() => readNumber(props.progress?.submitted))
const rejectedCount = computed(() => readNumber(props.progress?.rejectedCount ?? props.progress?.rejected))
const reviewedCount = computed(() => readNumber(props.progress?.approvedCount ?? props.progress?.reviewed))
const handledCount = computed(() =>
  isReviewVariant.value
    ? rejectedCount.value + reviewedCount.value
    : draftCount.value + submittedCount.value + rejectedCount.value + reviewedCount.value,
)
const progressPercent = computed(() =>
  totalCount.value > 0 ? Math.min(100, Math.round((handledCount.value / totalCount.value) * 100)) : 0,
)
const overviewLabel = computed(() => (isReviewVariant.value ? '审核进度' : '标注进度'))
const progressItems = computed(() => {
  if (isReviewVariant.value) {
    return [
      { label: '任务总数', value: totalCount.value, type: 'total' },
      { label: '待审核', value: pendingCount.value, type: 'pending' },
      { label: '已通过', value: reviewedCount.value, type: 'approved' },
      { label: '已驳回', value: rejectedCount.value, type: 'rejected' },
    ]
  }

  return [
    { label: '配额总数', value: totalCount.value, type: 'total' },
    { label: '待标注', value: pendingCount.value, type: 'pending' },
    { label: '草稿', value: draftCount.value, type: 'draft' },
    { label: '已提交', value: submittedCount.value, type: 'submitted' },
    { label: '已驳回', value: rejectedCount.value, type: 'rejected' },
    { label: '已通过', value: reviewedCount.value, type: 'reviewed' },
  ]
})

function readNumber(value: unknown, fallback = 0) {
  const numberValue = Number(value)

  return Number.isFinite(numberValue) ? numberValue : fallback
}

function formatNumber(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('en-US') : '0'
}
</script>

<template>
  <aside class="progress-panel" aria-label="总体情况查看">
    <header class="progress-panel__header">
      <strong>总体情况</strong>
      <span>任务 ID：{{ progress?.taskId ?? '-' }}</span>
    </header>

    <section class="progress-panel__overview">
      <div>
        <span>{{ overviewLabel }}</span>
        <strong>{{ formatNumber(handledCount) }} / {{ formatNumber(totalCount) }}</strong>
      </div>
      <el-progress :percentage="progressPercent" :show-text="false" color="#2563eb" />
    </section>

    <section class="progress-panel__grid">
      <article
        v-for="item in progressItems"
        :key="item.type"
        class="progress-panel__item"
        :class="`progress-panel__item--${item.type}`"
      >
        <span>{{ item.label }}</span>
        <strong>{{ formatNumber(item.value) }}</strong>
      </article>
    </section>
  </aside>
</template>

<style scoped>
.progress-panel {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: 14px;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
}

.progress-panel__header {
  display: grid;
  gap: 4px;
}

.progress-panel__header strong {
  color: #111827;
  font-size: 14px;
  font-weight: 850;
}

.progress-panel__header span {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.progress-panel__overview {
  display: grid;
  gap: 10px;
  border-radius: 8px;
  background: #f8fafc;
  padding: 14px;
}

.progress-panel__overview div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.progress-panel__overview span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.progress-panel__overview strong {
  color: #111827;
  font-size: 18px;
  font-weight: 900;
}

.progress-panel__overview :deep(.el-progress-bar__outer) {
  height: 6px !important;
  background: #e2e8f0;
}

.progress-panel__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.progress-panel__item {
  display: grid;
  gap: 4px;
  min-height: 70px;
  align-content: center;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  padding: 12px;
}

.progress-panel__item span {
  color: #64748b;
  font-size: 12px;
  font-weight: 750;
}

.progress-panel__item strong {
  color: #111827;
  font-size: 22px;
  font-weight: 900;
  line-height: 1;
}

.progress-panel__item--pending strong {
  color: #94a3b8;
}

.progress-panel__item--draft strong {
  color: #f97316;
}

.progress-panel__item--submitted strong {
  color: #16a34a;
}

.progress-panel__item--approved strong {
  color: #16a34a;
}

.progress-panel__item--rejected strong {
  color: #ef4444;
}

.progress-panel__item--reviewed strong {
  color: #7c3aed;
}
</style>
