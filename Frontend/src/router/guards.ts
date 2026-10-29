import type { Router } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { GetUserInfoPlus } from '../utils/api'

export function setupRouterGuards(router: Router) {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore()
    const satoken = localStorage.getItem('satoken')

    // 公开页面直接放行，比如 login
    if (to.meta.public) {
      return true
    }


    // 没有 token，直接回登录页
    if (!satoken) {
      return {
        name: 'login',
        query: { redirect: to.fullPath },
      }
    }

    try {
      const response = await GetUserInfoPlus(satoken)
      console.log(response)
      // 如果后端返回 code 不是 200，禁止访问
      if (response.code !== 200) {
        localStorage.removeItem('satoken')
        authStore.signOut()
        return {
          name: 'login',
          query: {
            redirect: to.fullPath,
            reason: 'session-expired',
          },
        }
      }
    } catch {
      // 网络异常时保留令牌，避免用户被迫重新登录；登录页会给出可理解的重试提示。
      return {
        name: 'login',
        query: {
          redirect: to.fullPath,
          reason: 'service-unavailable',
        },
      }
    }

    //你原来的登录状态判断
    if (!authStore.isAuthenticated) {
      return {
        name: 'login',
        query: { redirect: to.fullPath },
      }
    }

    const allowedRoles = to.meta.roles

    if (allowedRoles?.length && !allowedRoles.includes(authStore.activeRole)) {
      return authStore.homePath
    }

    return true
  })

  router.afterEach(() => {
    document.title = 'LabelHub—AI数据标注平台'
  })
}
