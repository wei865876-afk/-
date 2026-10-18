import { defineStore } from 'pinia'
import { postForm, get } from '@/api/http'

/** 登录态（基于 Spring Session，前后端分离下靠 Cookie 维持） */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    checked: false,
    loading: false,
    error: null
  }),
  getters: {
    isLogin: (state) => !!state.user,
    displayName: (state) => state.user?.realName || state.user?.username || '访客',
    roleLabel: (state) => {
      const role = state.user?.role
      if (!role) return '访客'
      if (role === 'admin' || role === '教务') return '教务管理员'
      return '学生'
    }
  },
  actions: {
    async login(username, password, role) {
      this.loading = true
      this.error = null
      try {
        const data = await postForm('/user/login', { username, password, role })
        this.user = data?.user ?? data
        this.checked = true
        return true
      } catch (e) {
        this.error = e.message || '登录失败'
        return false
      } finally {
        this.loading = false
      }
    },
    async check() {
      try {
        this.user = await get('/user/current')
      } catch (e) {
        this.user = null
      } finally {
        this.checked = true
      }
    },
    async logout() {
      try {
        await fetch('/user/logout', { method: 'POST', credentials: 'include' })
      } catch (e) {
        /* 后端未实现登出接口时忽略 */
      }
      this.user = null
    }
  }
})
