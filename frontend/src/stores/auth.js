import { defineStore } from 'pinia'
import { login as loginApi, fetchProfile, logout as logoutApi } from '../api/auth'
import { closeSocket } from '../utils/socket'

const TOKEN_KEY = 'lab_token'
const USER_KEY = 'lab_user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

/**
 * 登录状态：token + 当前用户。
 * 持久化到 localStorage，刷新页面后仍然保持登录。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
  }),

  getters: {
    isLogin: (state) => Boolean(state.token),
    role: (state) => state.user?.role || '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    // 管理员、教师可以维护设备台账
    isStaff: (state) => state.user?.role === 'ADMIN' || state.user?.role === 'TEACHER',
  },

  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(USER_KEY, JSON.stringify(data.user))
      return data.user
    },

    async loadProfile() {
      if (!this.token) return null
      const user = await fetchProfile()
      this.user = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
      return user
    },

    async logout() {
      try {
        if (this.token) await logoutApi()
      } catch {
        // 后端不可用时也要允许前端退出
      }
      this.clear()
    },

    clear() {
      closeSocket()
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
