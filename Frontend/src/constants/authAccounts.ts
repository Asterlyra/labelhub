import type { UserRole } from '../types/user'

export interface AuthAccount {
  username: string
  password: string
  role: UserRole
  label: string
  displayName: string
  description: string
}

export const AUTH_ACCOUNTS: AuthAccount[] = []

export function findAuthAccount(username: string, password: string) {
  const normalizedUsername = username.trim()

  return AUTH_ACCOUNTS.find(
    (account) => account.username === normalizedUsername && account.password === password,
  )
}
