export type UserRole = 'OWNER' | 'LABELER' | 'REVIEWER'

export interface AppUser {
  id: string
  name: string
  role: UserRole
  title: string
}

export interface RoleDefinition {
  role: UserRole
  label: string
  description: string
  defaultHome: string
  accent: string
}
