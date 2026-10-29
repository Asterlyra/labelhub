import type { TaskStatus, TaskStatusDefinition } from '../types/task'

export const TASK_STATUS_DEFINITIONS: TaskStatusDefinition[] = [
  {
    status: 'UNASSIGNED',
    label: '未分配',
    description: '任务已生成但还没有负责人。',
    actor: 'OWNER',
    actionLabel: '分配标注员',
    nextStatus: 'ASSIGNED',
    failureHint: '成员容量不足时提示 Owner 调整任务批次。',
  },
  {
    status: 'ASSIGNED',
    label: '已分配',
    description: '任务已经分配给标注员，等待开始。',
    actor: 'LABELER',
    actionLabel: '开始标注',
    nextStatus: 'LABELING',
    failureHint: '任务被撤回或权限变化时提示重新领取。',
  },
  {
    status: 'LABELING',
    label: '标注中',
    description: '标注员正在处理样本。',
    actor: 'LABELER',
    actionLabel: '保存草稿',
    nextStatus: 'DRAFT',
    failureHint: '保存失败时保留本地草稿并提示重试。',
  },
  {
    status: 'DRAFT',
    label: '草稿',
    description: '标注结果已暂存但未提交。',
    actor: 'LABELER',
    actionLabel: '提交校验',
    nextStatus: 'SUBMITTED',
    failureHint: 'Schema 校验失败时定位到字段级错误。',
  },
  {
    status: 'SUBMITTED',
    label: '已提交',
    description: '标注员已提交结果，等待 AI 审核。',
    actor: 'AI_AGENT',
    actionLabel: '触发 AI 审核',
    nextStatus: 'AI_REVIEWING',
    failureHint: 'AI 队列不可用时显示人工兜底入口。',
  },
  {
    status: 'AI_REVIEWING',
    label: 'AI 审核中',
    description: 'AI Agent 正在检查字段缺失、规则冲突和风险项。',
    actor: 'AI_AGENT',
    actionLabel: '生成审核结果',
    nextStatus: 'AI_REVIEWED',
    failureHint: '超时后允许重新触发或转人工审核。',
  },
  {
    status: 'AI_REVIEWED',
    label: 'AI 已审核',
    description: 'AI 审核结果已经生成，可进入人工复核。',
    actor: 'REVIEWER',
    actionLabel: '进入人工复核',
    nextStatus: 'HUMAN_REVIEWING',
    failureHint: '审核结果缺失时提示重新触发 AI 审核。',
  },
  {
    status: 'HUMAN_REVIEWING',
    label: '人工复核中',
    description: 'Reviewer 正在结合 AI 建议做最终判断。',
    actor: 'REVIEWER',
    actionLabel: '通过或驳回',
    nextStatus: 'APPROVED',
    failureHint: '必须填写驳回原因后才能退回返工。',
  },
  {
    status: 'APPROVED',
    label: '已通过',
    description: '结果通过人工复核，等待归档。',
    actor: 'REVIEWER',
    actionLabel: '归档结果',
    nextStatus: 'COMPLETED',
    failureHint: '归档失败时保留审核结论并提示重试。',
  },
  {
    status: 'REJECTED',
    label: '已驳回',
    description: 'Reviewer 驳回任务，需要标注员返工。',
    actor: 'LABELER',
    actionLabel: '进入返工',
    nextStatus: 'REWORKING',
    failureHint: '驳回原因缺失时阻止进入返工。',
  },
  {
    status: 'REWORKING',
    label: '返工中',
    description: '标注员根据驳回原因修订标注结果。',
    actor: 'LABELER',
    actionLabel: '重新提交',
    nextStatus: 'SUBMITTED',
    failureHint: '返工提交失败时保留原驳回上下文。',
  },
  {
    status: 'COMPLETED',
    label: '已完成',
    description: '任务完成，可计入导出与统计。',
    actor: 'OWNER',
    actionLabel: '查看统计',
    failureHint: '统计未刷新时提示 Owner 手动重算。',
  },
]

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = TASK_STATUS_DEFINITIONS.reduce(
  (labels, item) => ({
    ...labels,
    [item.status]: item.label,
  }),
  {} as Record<TaskStatus, string>,
)

export function getTaskStatusDefinition(status: TaskStatus) {
  return TASK_STATUS_DEFINITIONS.find((item) => item.status === status)
}
