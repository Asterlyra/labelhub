<script setup lang="ts">
import { computed } from 'vue'

interface AiPreReviewScore {
  dimension: string
  passScore: number
  reason: string
  score: number
  weight: number
}

interface AiPreReviewResult {
  auditStatus?: string
  confidence?: number
  lastError?: string
  scores: AiPreReviewScore[]
  status?: string
  summary?: string
  taskId?: string
  taskItemId?: string
  weightedScore?: number
}

const props = defineProps<{
  result?: unknown
}>()

const reviewResult = computed(() => normalizeReviewResult(props.result))
const hasReviewResult = computed(() => Boolean(reviewResult.value))
const scoreRows = computed(() => reviewResult.value?.scores ?? [])
const resultMetaItems = computed(() => {
  const result = reviewResult.value

  if (!result) {
    return []
  }

  return [
    { label: 'AI 判定', value: formatAuditStatus(result.auditStatus), tone: getAuditTone(result.auditStatus) },
    { label: '生成状态', value: formatStatus(result.status), tone: getStatusTone(result.status) },
    { label: '置信度', value: formatPercent(result.confidence), tone: 'blue' },
    { label: '加权得分', value: formatNumber(result.weightedScore), tone: getScoreTone(result.weightedScore, 80) },
    { label: '任务 ID', value: readString(result.taskId), tone: 'gray' },
    { label: '题目 ID', value: readString(result.taskItemId), tone: 'gray' },
  ].filter((item) => item.value)
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

function readObject(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : null
}

function parseMaybeJson(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  const text = value.trim()

  if (!text || (!text.startsWith('{') && !text.startsWith('['))) {
    return value
  }

  try {
    return JSON.parse(text)
  } catch {
    return value
  }
}

function normalizeReviewResult(value: unknown): AiPreReviewResult | null {
  const record = readObject(parseMaybeJson(value))

  if (!record) {
    return null
  }

  const scores = Array.isArray(record.scores)
    ? record.scores.map((item) => normalizeScore(item)).filter((item): item is AiPreReviewScore => Boolean(item))
    : []

  return {
    auditStatus: readString(record.auditStatus),
    confidence: readNumber(record.confidence, Number.NaN),
    lastError: readString(record.lastError),
    scores,
    status: readString(record.status),
    summary: readString(record.summary),
    taskId: readString(record.taskId),
    taskItemId: readString(record.taskItemId),
    weightedScore: readNumber(record.weightedScore, Number.NaN),
  }
}

function normalizeScore(value: unknown): AiPreReviewScore | null {
  const record = readObject(parseMaybeJson(value))

  if (!record) {
    return null
  }

  const dimension = readString(record.dimension ?? record.field ?? record.name, '未命名维度')

  return {
    dimension,
    passScore: readNumber(record.passScore),
    reason: readString(record.reason),
    score: readNumber(record.score),
    weight: readNumber(record.weight),
  }
}

function clampPercent(value: number) {
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.min(100, Math.max(0, value))
}

function formatNumber(value: unknown, fallback = '') {
  const numberValue = Number(value)

  if (!Number.isFinite(numberValue)) {
    return fallback
  }

  return Number.isInteger(numberValue) ? String(numberValue) : numberValue.toFixed(2).replace(/\.?0+$/, '')
}

function formatPercent(value: unknown) {
  const numberValue = Number(value)

  if (!Number.isFinite(numberValue)) {
    return ''
  }

  const percentValue = numberValue <= 1 ? numberValue * 100 : numberValue

  return `${formatNumber(percentValue)}%`
}

function formatAuditStatus(value: unknown) {
  const status = readString(value).toLowerCase()
  const statusMap: Record<string, string> = {
    pass: '建议通过',
    approve: '建议通过',
    approved: '建议通过',
    reject: '建议打回',
    rejected: '建议打回',
    review: '建议复审',
    manual_review: '建议人工复审',
  }

  return statusMap[status] ?? readString(value)
}

function formatStatus(value: unknown) {
  const status = readString(value).toLowerCase()
  const statusMap: Record<string, string> = {
    completed: '已完成',
    complete: '已完成',
    pending: '生成中',
    running: '生成中',
    failed: '失败',
    error: '失败',
  }

  return statusMap[status] ?? readString(value)
}

function getAuditTone(value: unknown) {
  const status = readString(value).toLowerCase()

  if (['pass', 'approve', 'approved'].includes(status)) {
    return 'green'
  }

  if (['reject', 'rejected'].includes(status)) {
    return 'red'
  }

  return 'orange'
}

function getStatusTone(value: unknown) {
  const status = readString(value).toLowerCase()

  if (['completed', 'complete', 'success'].includes(status)) {
    return 'green'
  }

  if (['failed', 'error'].includes(status)) {
    return 'red'
  }

  return 'blue'
}

function getScoreTone(score: unknown, passScore: unknown) {
  const scoreValue = readNumber(score, Number.NaN)
  const passScoreValue = readNumber(passScore, 80)

  if (!Number.isFinite(scoreValue)) {
    return 'gray'
  }

  if (scoreValue >= passScoreValue) {
    return 'green'
  }

  if (scoreValue >= Math.max(60, passScoreValue * 0.75)) {
    return 'orange'
  }

  return 'red'
}

function getBarStyle(score: number) {
  return {
    width: `${clampPercent(score)}%`,
  }
}
</script>

<template>
  <section v-if="hasReviewResult" class="ai-pre-review" aria-label="AI预审结果">
    <header class="ai-pre-review__header">
      <div>
        <strong>AI 预审结果</strong>
        <span>维度评分（共 100）</span>
      </div>
      <span
        v-if="reviewResult?.auditStatus"
        class="ai-pre-review__audit"
        :class="`ai-pre-review__audit--${getAuditTone(reviewResult.auditStatus)}`"
      >
        {{ formatAuditStatus(reviewResult.auditStatus) }}
      </span>
    </header>

    <div v-if="scoreRows.length" class="ai-pre-review__score-table" role="table" aria-label="AI预审维度评分">
      <div class="ai-pre-review__score-head" role="row" style="text-align: center;">
        <span role="columnheader">维度</span>
        <span role="columnheader">得分</span>
        <span role="columnheader" >通过线</span>
        <span role="columnheader" style="text-align: center;">权重</span>
      </div>

      <article
        v-for="(score, index) in scoreRows"
        :key="`${score.dimension}-${index}`"
        class="ai-pre-review__score-row"
        :class="`ai-pre-review__score-row--${getScoreTone(score.score, score.passScore)}`"
        role="row"
      >
        <strong role="cell" style="text-align: center;">{{ score.dimension }}</strong>
        <div class="ai-pre-review__score-bar-cell" role="cell">
          <span class="ai-pre-review__score-track">
            <span class="ai-pre-review__score-bar" :style="getBarStyle(score.score)"></span>
          </span>
          <em>{{ formatNumber(score.score, '0') }}</em>
        </div>
        <span role="cell">{{ formatNumber(score.passScore, '0') }}</span>
        <span role="cell">{{ formatNumber(score.weight, '0') }}</span>
      </article>
    </div>

    <!-- <section v-if="scoreRows.some((score) => score.reason)" class="ai-pre-review__reasons">
      <h3>AI 评语</h3>
      <article v-for="(score, index) in scoreRows" :key="`${score.dimension}-reason-${index}`">
        <strong>{{ score.dimension }}</strong>
        <p>{{ score.reason || '暂无原因说明' }}</p>
      </article>
    </section> -->

    <section v-if="reviewResult?.summary || reviewResult?.lastError" class="ai-pre-review__summary">
      <h3>综合结论</h3>
      <p v-if="reviewResult.summary">{{ reviewResult.summary }}</p>
      <!-- <p v-if="reviewResult.lastError" class="ai-pre-review__error">{{ reviewResult.lastError }}</p> -->
    </section>

    <dl v-if="resultMetaItems.length" class="ai-pre-review__meta">
      <div
        v-for="item in (resultMetaItems || []).filter((_, index) => index === 0 || index === 2)"
        :key="item.label"
        class="ai-pre-review__meta-item"
        :class="`ai-pre-review__meta-item--${item.tone}`"
      >
        <dt>{{ item.label }}</dt>
        <dd style="font-size: 15px;">{{ item.value }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.ai-pre-review {
  display: grid;
  gap: 14px;
  border: 1px solid rgba(239, 68, 68, 0.18);
  border-radius: 8px;
  background: linear-gradient(180deg, #fff7f7 0%, #ffffff 58%);
  color: #1f2937;
  padding: 14px;
}

.ai-pre-review__header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-pre-review__header div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.ai-pre-review__header strong {
  color: #111827;
  font-size: 15px;
  font-weight: 900;
  line-height: 1.25;
}

.ai-pre-review__header span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.ai-pre-review__audit,
.ai-pre-review__meta-item dd {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  padding: 0 10px;
  white-space: nowrap;
}

.ai-pre-review__audit--green,
.ai-pre-review__meta-item--green dd {
  background: #dcfce7;
  color: #16a34a;
}

.ai-pre-review__audit--orange,
.ai-pre-review__meta-item--orange dd {
  background: #fff7ed;
  color: #f97316;
}

.ai-pre-review__audit--red,
.ai-pre-review__meta-item--red dd {
  background: #fee2e2;
  color: #ef4444;
}

.ai-pre-review__meta-item--blue dd {
  background: #eff6ff;
  color: #2563eb;
}

.ai-pre-review__meta-item--gray dd {
  background: #f1f5f9;
  color: #64748b;
}

.ai-pre-review__score-table {
  display: grid;
  overflow: hidden;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
}

.ai-pre-review__score-head,
.ai-pre-review__score-row {
  display: grid;
  grid-template-columns: minmax(110px, 1.1fr) minmax(170px, 1.8fr) 78px 66px;
  column-gap: 12px;
  align-items: center;
  min-width: 0;
  border-bottom: 1px solid #f1f5f9;
  padding: 9px 12px;
}

.ai-pre-review__score-row:last-child {
  border-bottom: 0;
}

.ai-pre-review__score-head {
  color: #64748b;
  background: #f8fafc;
  font-size: 12px;
  font-weight: 900;
}

.ai-pre-review__score-row strong,
.ai-pre-review__score-row span {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-pre-review__score-bar-cell {
  display: grid;
  text-align: center;
  grid-template-columns: minmax(0, 1fr) 42px;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ai-pre-review__score-track {
  display: block;
  overflow: hidden;
  height: 6px;
  border-radius: 999px;
  background: #eef2f7;
}

.ai-pre-review__score-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #f97316;
}

.ai-pre-review__score-row--green .ai-pre-review__score-bar {
  background: #16a34a;
}

.ai-pre-review__score-row--red .ai-pre-review__score-bar {
  background: #ef4444;
}

.ai-pre-review__score-row--orange .ai-pre-review__score-bar {
  background: #f97316;
}

.ai-pre-review__score-bar-cell em {
  color: #f97316;
  font-size: 13px;
  font-style: normal;
  font-weight: 950;
  text-align: right;
}

.ai-pre-review__score-row--green .ai-pre-review__score-bar-cell em {
  color: #16a34a;
}

.ai-pre-review__score-row--red .ai-pre-review__score-bar-cell em {
  color: #ef4444;
}

.ai-pre-review__reasons,
.ai-pre-review__summary {
  display: grid;
  gap: 10px;
  border: 1px solid rgba(239, 68, 68, 0.14);
  border-radius: 8px;
  background: rgba(254, 242, 242, 0.62);
  padding: 12px;
}

.ai-pre-review__reasons h3,
.ai-pre-review__summary h3 {
  margin: 0;
  color: #7f1d1d;
  font-size: 13px;
  font-weight: 900;
}

.ai-pre-review__reasons article {
  display: grid;
  gap: 5px;
  border-top: 1px solid rgba(239, 68, 68, 0.12);
  padding-top: 10px;
}

.ai-pre-review__reasons article:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.ai-pre-review__reasons strong {
  color: #991b1b;
  font-size: 13px;
  font-weight: 900;
}

.ai-pre-review__reasons p,
.ai-pre-review__summary p {
  margin: 0;
  color: #374151;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.ai-pre-review__error {
  color: #b91c1c !important;
}

.ai-pre-review__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
  margin: 0;
}

.ai-pre-review__meta-item {
  display: grid;
  min-width: 0;
  gap: 6px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #ffffff;
  padding: 9px;
}

.ai-pre-review__meta-item dt,
.ai-pre-review__meta-item dd {
  margin: 0;
}

.ai-pre-review__meta-item dt {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-pre-review__meta-item dd {
  width: fit-content;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 720px) {
  .ai-pre-review__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .ai-pre-review__score-head {
    display: none;
  }

  .ai-pre-review__score-row {
    grid-template-columns: 1fr;
    row-gap: 8px;
  }
}
</style>
