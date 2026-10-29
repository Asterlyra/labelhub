import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { hasRoleAccess } from '../utils/permission'
import type { UserRole } from '../types/user'

export function usePermission(
  currentRole: MaybeRefOrGetter<UserRole>,
  allowedRoles: MaybeRefOrGetter<UserRole[]>,
) {
  const canAccess = computed(() => hasRoleAccess(toValue(currentRole), toValue(allowedRoles)))

  return {
    canAccess,
  }
}
