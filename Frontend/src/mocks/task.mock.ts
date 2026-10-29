import type { AnnotationTask } from '../types/task'

export const mockTasks: AnnotationTask[] = [
  {
    id: 'task-1001',
    projectId: 'demo-project',
    title: '样本批次 1001',
    assignee: 'Labeler Demo',
    status: 'AI_REVIEWED',
    riskLevel: 'medium',
    updatedAt: '2026-05-21 10:00',
  },
  {
    id: 'task-1002',
    projectId: 'demo-project',
    title: '样本批次 1002',
    assignee: 'Labeler Demo',
    status: 'DRAFT',
    riskLevel: 'low',
    updatedAt: '2026-05-21 10:16',
  },
]
