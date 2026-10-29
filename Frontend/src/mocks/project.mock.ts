import type { ProjectSummary } from '../types/project'

export const mockProjects: ProjectSummary[] = [
  {
    id: 'demo-project',
    name: '客户意图识别标注',
    description: '演示从数据导入、Schema 配置、任务分配到 AI 审核的完整闭环。',
    owner: 'Owner Demo',
    currentStage: 'Schema 已配置',
    progress: 62,
    members: [
      { id: 'owner-1', name: 'Owner Demo', role: 'OWNER' },
      { id: 'labeler-1', name: 'Labeler Demo', role: 'LABELER' },
      { id: 'reviewer-1', name: 'Reviewer Demo', role: 'REVIEWER' },
    ],
  },
]
