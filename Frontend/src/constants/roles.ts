import type { RoleDefinition, UserRole } from '../types/user'

export const ROLE_DEFINITIONS: RoleDefinition[] = [
  {
    role: 'OWNER',
    label: 'Owner',
    description: '创建项目、导入数据、配置规则、分配任务并查看统计。',
    defaultHome: '/owner/tasks',
    accent: '#0071e3',
  },
  {
    role: 'LABELER',
    label: 'Labeler',
    description: '领取任务、阅读说明、完成标注并提交结果。',
    defaultHome: '/workbench',
    accent: '#34c759',
  },
  {
    role: 'REVIEWER',
    label: 'Reviewer',
    description: '查看标注结果、参考 AI 建议、通过或驳回任务。',
    defaultHome: '/workbench/review',
    accent: '#8e5cf7',
  },
]

export const ROLE_LABELS: Record<UserRole, string> = ROLE_DEFINITIONS.reduce(
  (labels, item) => ({
    ...labels,
    [item.role]: item.label,
  }),
  {} as Record<UserRole, string>,
)

export function getRoleDefinition(role: UserRole) {
  return ROLE_DEFINITIONS.find((item) => item.role === role) ?? ROLE_DEFINITIONS[0]
}
