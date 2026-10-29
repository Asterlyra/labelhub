import type { UserRole } from '../types/user'

export function hasRoleAccess(currentRole: UserRole, allowedRoles: UserRole[]) {
  return allowedRoles.includes(currentRole)
}
