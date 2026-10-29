import type { UserRole } from '../types/user'

export interface NavItem {
  label: string
  path: string
  roles: UserRole[]
}

export interface NavGroup {
  label: string
  items: NavItem[]
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: '',
    items: [{ label: '我的首页', path: '/dashboard', roles: ['OWNER'] }],
  },
  {
    label: '标注工作台',
    items: [
      { label: '我的首页', path: '/workbench', roles: ['LABELER'] },
      { label: '任务广场', path: '/workbench/marketplace', roles: ['LABELER'] },
      { label: '我的任务', path: '/workbench/tasks', roles: ['LABELER'] },
      { label: '个人资料', path: '/settings/profile', roles: ['LABELER'] },
    ],
  },
  {
    label: '数据生产',
    items: [
      { label: '任务管理', path: '/owner/tasks', roles: ['OWNER'] },
      { label: '模板搭建', path: '/owner/templates', roles: ['OWNER'] },
      { label: '数据集', path: '/owner/datasets', roles: ['OWNER'] },
    ],
  },
  {
    label: '个人设置',
    items: [
      { label: '个人资料', path: '/settings/profile', roles: ['OWNER'] },
      { label: '团队资料', path: '/settings/members', roles: ['OWNER'] },
    ],
  },
  {
    label: '审核工作台',
    items: [
      { label: '我的首页', path: '/workbench/review', roles: ['REVIEWER'] },
      { label: '审核列表', path: '/workbench/review/list', roles: ['REVIEWER'] },
      { label: '个人设置', path: '/settings/profile', roles: ['REVIEWER'] },
    ],
  },
]
