import type { RouteRecordRaw } from 'vue-router'
import AuthLayout from '../layouts/AuthLayout.vue'
import BasicLayout from '../layouts/BasicLayout.vue'
import LoginPage from '../pages/login/LoginPage.vue'
import DashboardPage from '../pages/dashboard/DashboardPage.vue'
import ProjectListPage from '../pages/projects/ProjectListPage.vue'
import ProjectDetailPage from '../pages/projects/ProjectDetailPage.vue'
import DatasetsPage from '../pages/datasets/DatasetsPage.vue'
import SchemaPage from '../pages/schema/SchemaPage.vue'
import TasksPage from '../pages/tasks/TasksPage.vue'
import AssignmentPage from '../pages/tasks/AssignmentPage.vue'
import AnalyticsPage from '../pages/analytics/AnalyticsPage.vue'
import ExportPage from '../pages/export/ExportPage.vue'
import LabelerWorkbenchHomePage from '../pages/workbench/LabelerWorkbenchHomePage.vue'
import TaskMarketplacePage from '../pages/workbench/TaskMarketplacePage.vue'
import MyTasksPage from '../pages/workbench/MyTasksPage.vue'
import LabelingTaskPage from '../pages/labeling/LabelingTaskPage.vue'
import ReviewerHomePage from '../pages/review/ReviewerHomePage.vue'
import ReviewQueuePage from '../pages/review/ReviewQueuePage.vue'
import ReviewTaskPage from '../pages/review/ReviewTaskPage.vue'
import MembersPage from '../pages/settings/MembersPage.vue'
import ProfilePage from '../pages/settings/ProfilePage.vue'
import OwnerDatasetsPage from '../pages/owner/OwnerDatasetsPage.vue'
import OwnerTemplatesPage from '../pages/owner/OwnerTemplatesPage.vue'
import TemplateDesignerPage from '../pages/owner/TemplateDesignerPage.vue'
import OwnerTasksPage from '../pages/owner/OwnerTasksPage.vue'
import OwnerTaskDetailPage from '../pages/owner/OwnerTaskDetailPage.vue'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/login',
    component: AuthLayout,
    meta: { public: true, title: '登录' },
    children: [
      {
        path: '',
        name: 'login',
        component: LoginPage,
        meta: { public: true, title: '登录' },
      },
    ],
  },
  {
    path: '/',
    component: BasicLayout,
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: DashboardPage,
        meta: { title: '我的首页', roles: ['OWNER'] },
      },
      {
        path: 'workbench',
        name: 'labeler-workbench-home',
        component: LabelerWorkbenchHomePage,
        meta: { title: '我的首页', roles: ['LABELER'] },
      },
      {
        path: 'workbench/marketplace',
        name: 'task-marketplace',
        component: TaskMarketplacePage,
        meta: { title: '任务广场', roles: ['LABELER'] },
      },
      {
        path: 'workbench/tasks',
        name: 'labeler-my-tasks',
        component: MyTasksPage,
        meta: { title: '我的任务', roles: ['LABELER'] },
      },
      {
        path: 'workbench/labeling',
        redirect: '/workbench/tasks',
        meta: { title: '我的任务', roles: ['LABELER'] },
      },
      {
        path: 'workbench/labeling/:taskId',
        name: 'labeling-task',
        component: LabelingTaskPage,
        meta: { title: '人工标注工作台', roles: ['LABELER'] },
      },
      {
        path: 'workbench/review',
        name: 'review-home',
        component: ReviewerHomePage,
        meta: { title: '我的首页', roles: ['REVIEWER'] },
      },
      {
        path: 'workbench/review/list',
        name: 'review-list',
        component: ReviewQueuePage,
        meta: { title: '审核列表', roles: ['REVIEWER'] },
      },
      {
        path: 'workbench/review/:taskId',
        name: 'review-task',
        component: ReviewTaskPage,
        meta: { title: '审核详情', roles: ['REVIEWER'] },
      },
      {
        path: 'owner/tasks',
        name: 'owner-tasks',
        component: OwnerTasksPage,
        meta: {
          title: '任务管理',
          description: '管理数据生产任务、任务批次、负责人和处理状态。',
          roles: ['OWNER'],
        },
      },
      {
        path: 'owner/tasks/detail/:taskId?',
        name: 'owner-task-detail',
        component: OwnerTaskDetailPage,
        meta: {
          title: '任务详情',
          description: '查看任务详情数据。',
          roles: ['OWNER'],
        },
      },
      {
        path: 'owner/templates',
        name: 'owner-templates',
        component: OwnerTemplatesPage,
        meta: {
          title: '模板搭建',
          description: '搭建标注模板、字段结构和任务表单。',
          roles: ['OWNER'],
        },
      },
      {
        path: 'owner/templates/designer/:templateId?',
        name: 'owner-template-designer',
        component: TemplateDesignerPage,
        meta: {
          title: '模板搭建器',
          description: '通过拖拽组件搭建标注模板。',
          roles: ['OWNER'],
        },
      },
      {
        path: 'owner/datasets',
        name: 'owner-datasets',
        component: OwnerDatasetsPage,
        meta: {
          title: '数据集',
          description: '维护数据集、样本来源和数据批次。',
          roles: ['OWNER'],
        },
      },
      {
        path: 'projects',
        name: 'projects',
        component: ProjectListPage,
        meta: { title: '项目列表', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId',
        name: 'project-detail',
        component: ProjectDetailPage,
        meta: { title: '项目详情', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId/datasets',
        name: 'project-datasets',
        component: DatasetsPage,
        meta: { title: '数据导入', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId/schema',
        name: 'project-schema',
        component: SchemaPage,
        meta: { title: 'Schema 配置', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId/tasks',
        name: 'project-tasks',
        component: TasksPage,
        meta: { title: '任务进度', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId/assignment',
        name: 'project-assignment',
        component: AssignmentPage,
        meta: { title: '任务分配', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId/analytics',
        name: 'project-analytics',
        component: AnalyticsPage,
        meta: { title: '统计分析', roles: ['OWNER'] },
      },
      {
        path: 'projects/:projectId/export',
        name: 'project-export',
        component: ExportPage,
        meta: { title: '结果导出', roles: ['OWNER'] },
      },
      {
        path: 'settings/members',
        name: 'settings-members',
        component: MembersPage,
        meta: { title: '团队资料', roles: ['OWNER'] },
      },
      {
        path: 'settings/profile',
        name: 'settings-profile',
        component: ProfilePage,
        meta: { title: '个人资料', roles: ['OWNER', 'LABELER', 'REVIEWER'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]
