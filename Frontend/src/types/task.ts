import type { UserRole } from './user'

export type TaskStatus =
  | 'UNASSIGNED'
  | 'ASSIGNED'
  | 'LABELING'
  | 'DRAFT'
  | 'SUBMITTED'
  | 'AI_REVIEWING'
  | 'AI_REVIEWED'
  | 'HUMAN_REVIEWING'
  | 'APPROVED'
  | 'REJECTED'
  | 'REWORKING'
  | 'COMPLETED'

export type TaskActor = UserRole | 'AI_AGENT'

export interface TaskStatusDefinition {
  status: TaskStatus
  label: string
  description: string
  actor: TaskActor
  actionLabel: string
  nextStatus?: TaskStatus
  failureHint: string
}

export interface AnnotationTask {
  id: string
  projectId: string
  title: string
  assignee: string
  status: TaskStatus
  riskLevel: 'low' | 'medium' | 'high'
  updatedAt: string
}
