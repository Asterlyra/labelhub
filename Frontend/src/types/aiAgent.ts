export interface AIAuditIssue {
  field: string
  message: string
  suggestion: string
}

export interface AIAuditResult {
  taskId: string
  status: 'pass' | 'warning' | 'fail'
  confidence: number
  riskLevel: 'low' | 'medium' | 'high'
  issues: AIAuditIssue[]
  summary: string
}
