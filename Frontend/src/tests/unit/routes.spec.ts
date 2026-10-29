import { describe, expect, it } from 'vitest'
import type { RouteRecordRaw } from 'vue-router'
import { routes } from '../../router/routes'

function joinRoutePath(parentPath: string, routePath: string) {
  if (routePath === '') return parentPath || '/'
  if (routePath.startsWith('/')) return routePath

  const normalizedParent = parentPath === '/' ? '' : parentPath
  return `${normalizedParent}/${routePath}`.replace(/\/{2,}/g, '/')
}

function collectPaths(routeRecords: RouteRecordRaw[], parentPath = ''): string[] {
  return routeRecords.flatMap((route) => {
    const routePath = joinRoutePath(parentPath, route.path)
    const children = route.children ? collectPaths(route.children, routePath) : []
    return [routePath, ...children]
  })
}

describe('routes', () => {
  it('registers the required initial LabelHub routes', () => {
    const paths = collectPaths(routes)

    expect(paths).toEqual(
      expect.arrayContaining([
        '/login',
        '/dashboard',
        '/projects',
        '/projects/:projectId',
        '/projects/:projectId/datasets',
        '/projects/:projectId/schema',
        '/projects/:projectId/tasks',
        '/projects/:projectId/assignment',
        '/projects/:projectId/analytics',
        '/projects/:projectId/export',
        '/workbench',
        '/workbench/marketplace',
        '/workbench/tasks',
        '/workbench/labeling',
        '/workbench/labeling/:taskId',
        '/workbench/review',
        '/workbench/review/list',
        '/workbench/review/:taskId',
        '/settings/members',
        '/settings/profile',
        '/owner/datasets',
        '/owner/templates',
        '/owner/templates/designer/:templateId?',
        '/owner/tasks',
        '/owner/tasks/detail/:taskId?',
      ]),
    )
  })
})
