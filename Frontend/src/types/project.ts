import type { UserRole } from './user'

export interface ProjectSummary {
  id: string
  name: string
  description: string
  owner: string
  currentStage: string
  progress: number
  members: {
    id: string
    name: string
    role: UserRole
  }[]
}
