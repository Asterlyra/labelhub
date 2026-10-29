import type { AIAuditResult } from '../types/aiAgent'

export const mockAIAuditResults: AIAuditResult[] = [
  {
    taskId: 'task-1001',
    status: 'warning',
    confidence: 0.82,
    riskLevel: 'medium',
    issues: [
      {
        field: 'intent',
        message: '意图分类与上下文存在轻微冲突。',
        suggestion: '建议复核是否应标记为售后咨询。',
      },
    ],
    summary: '整体可进入人工复核，建议 Reviewer 优先检查意图字段。',
  },
]
