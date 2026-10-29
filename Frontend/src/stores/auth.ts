import { defineStore } from 'pinia'
import { AUTH_ACCOUNTS } from '../constants/authAccounts'
import { getRoleDefinition, ROLE_DEFINITIONS } from '../constants/roles'
import { readStorage, removeStorage, writeStorage } from '../utils/storage'
import type { AppUser, UserRole } from '../types/user'

const AUTH_STORE_KEY = 'labelhub.auth.store'

interface UserInfo {
  id?: string | number
  username?: string
  nickname?: string
  name?: string
  role?: string
  [key: string]: unknown
}

interface SavedAuthState {
  username?: string
  role?: UserRole
  token?: string
  userInfo?: UserInfo
  loginResponse?: unknown
}

function isUserRole(value: unknown): value is UserRole {
  return ROLE_DEFINITIONS.some((item) => item.role === value)
}

function readSavedAuthState(): SavedAuthState {
  const rawState = readStorage(AUTH_STORE_KEY)

  if (!rawState) {
    return {}
  }

  try {
    return JSON.parse(rawState) as SavedAuthState
  } catch {
    return {}
  }
}

function getResponseData(response: any) {
  return response?.data ?? response ?? {}
}

function getUserInfo(response: any): UserInfo {
  const data = getResponseData(response)
  return data.user ?? data.userInfo ?? data
}

function getBackendRole(data: any, userInfo: UserInfo) {
  return userInfo.role ?? data.role ?? data.roles?.[0]
}

function mapBackendRole(role: unknown): UserRole | null {
  if (role === 'PROJECT_ADMIN') {
    return 'OWNER'
  }

  if (role === 'LABELER' || role === 'REVIEWER') {
    return role
  }

  return null
}

const savedState = readSavedAuthState()

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: savedState.username ?? 'admin',
    role: isUserRole(savedState.role) ? savedState.role : 'OWNER',
    token: savedState.token ?? '',
    userInfo: savedState.userInfo ?? {},
    loginResponse: savedState.loginResponse ?? null,
  }),
  actions: {
    setLoginResponse(response: any, fallbackUsername = '') {
      const data = getResponseData(response)
      const userInfo = getUserInfo(response)
      const username = String(userInfo.username ?? data.username ?? fallbackUsername)
      const account = AUTH_ACCOUNTS.find((item) => item.username === username)

      this.loginResponse = response
      this.userInfo = userInfo
      this.username = username || 'admin'
      this.role = mapBackendRole(getBackendRole(data, userInfo)) ?? account?.role ?? 'OWNER'
      this.token = String(data.satoken ?? data.token ?? data.accessToken ?? data.jwt ?? userInfo.token ?? '')
      this.persistAuthState()
    },
    signIn(role: UserRole, username?: string) {
      this.role = role
      this.username = username ?? this.username
      this.loginResponse = this.loginResponse ?? { username: this.username, role }
      this.persistAuthState()
    },
    switchRole(role: UserRole) {
      this.role = role
      this.persistAuthState()
    },
    signOut() {
      this.username = 'admin'
      this.role = 'OWNER'
      this.token = ''
      this.userInfo = {}
      this.loginResponse = null
      removeStorage(AUTH_STORE_KEY)
    },
    persistAuthState() {
      writeStorage(
        AUTH_STORE_KEY,
        JSON.stringify({
          username: this.username,
          role: this.role,
          token: this.token,
          userInfo: this.userInfo,
          loginResponse: this.loginResponse,
        }),
      )
    },
  },
  getters: {
    isAuthenticated: (state) => Boolean(state.loginResponse),
    activeRole: (state): UserRole => state.role,
    activeUsername: (state): string => state.username,
    currentUser: (state): AppUser => {
      const roleMeta = getRoleDefinition(state.role)
      const account = AUTH_ACCOUNTS.find((item) => item.username === state.username)
      const displayName =
        state.userInfo.nickname || state.userInfo.name || account?.displayName || `${roleMeta.label} Demo`

      return {
        id: String(state.userInfo.id ?? `mock-${state.role.toLowerCase()}`),
        name: String(displayName),
        role: state.role,
        title: account?.description ?? roleMeta.description,
      }
    },
    homePath: (state): string => {
      if (state.role === 'LABELER') {
        return '/workbench'
      }

      if (state.role === 'REVIEWER') {
        return '/workbench/review'
      }

      return '/dashboard'
    },
  },
})
