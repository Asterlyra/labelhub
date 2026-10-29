export interface ReviewDecision {
  taskId: string
  decision: 'approve' | 'reject'
  reason?: string
}

export interface ReviewTimelineItem {
  id: string
  taskId: string
  title: string
  description: string
  createdAt: string
}
