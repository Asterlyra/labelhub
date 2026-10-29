import type { UserRole } from './user'

export interface RouteAction {
  label: string
  path: string
  variant?: 'primary' | 'secondary'
}

export interface PagePlan {
  key: string
  path: string
  eyebrow: string
  title: string
  objective: string
  roles: UserRole[]
  currentTask: string
  status: string
  nextAction: string
  exceptionHandling: string
  coreFeatures: string[]
  mainComponents: string[]
  interactions: string[]
  emptyState: string
  errorState: string
  routeRelations: string[]
  primaryAction?: RouteAction
  secondaryActions?: RouteAction[]
}
