import { defineStore } from 'pinia'
import router from '../router'
import * as authApi from '../api/auth'
import type { Role } from '../types/api'

function normalizeRoles(roles: string[]): Role[] {
  return roles
    .map((r) => (r.startsWith('ROLE_') ? r.slice('ROLE_'.length) : r))
    .filter((r): r is Role => r === 'STUDENT' || r === 'TEACHER' || r === 'ADMIN')
}

function homePathForRoles(roles: Role[]): string {
  if (roles.includes('TEACHER')) return '/teacher/questions'
  if (roles.includes('STUDENT')) return '/student/exams'
  return '/login'
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: (localStorage.getItem('auth.token') as string | null) ?? null,
    username: (localStorage.getItem('auth.username') as string | null) ?? null,
    fullName: (localStorage.getItem('auth.fullName') as string | null) ?? null,
    studentNo: (localStorage.getItem('auth.studentNo') as string | null) ?? null,
    roles: normalizeRoles(JSON.parse(localStorage.getItem('auth.roles') ?? '[]')),
    ready: false,
  }),
  getters: {
    isAuthed: (s) => Boolean(s.token),
    hasRole: (s) => (role: Role) => s.roles.includes(role),
  },
  actions: {
    async hydrate() {
      if (!this.token) {
        this.ready = true
        return
      }
      try {
        const info = await authApi.me()
        this.username = info.username
        this.fullName = info.fullName ?? null
        this.studentNo = info.studentNo ?? null
        this.roles = normalizeRoles(info.roles)
        localStorage.setItem('auth.username', this.username ?? '')
        localStorage.setItem('auth.fullName', this.fullName ?? '')
        localStorage.setItem('auth.studentNo', this.studentNo ?? '')
        localStorage.setItem('auth.roles', JSON.stringify(this.roles))
      } catch {
        this.clear()
        await router.replace('/login')
      } finally {
        this.ready = true
      }
    },
    async login(username: string, password: string) {
      const res = await authApi.login({ username, password })
      this.token = res.token
      this.username = res.username
      this.roles = res.roles
      localStorage.setItem('auth.token', this.token)
      localStorage.setItem('auth.username', this.username)
      localStorage.setItem('auth.roles', JSON.stringify(this.roles))
      try {
        const info = await authApi.me()
        this.fullName = info.fullName ?? null
        this.studentNo = info.studentNo ?? null
        localStorage.setItem('auth.fullName', this.fullName ?? '')
        localStorage.setItem('auth.studentNo', this.studentNo ?? '')
      } catch {}
      await router.replace(homePathForRoles(this.roles))
    },
    async register(username: string, password: string) {
      const res = await authApi.register({ username, password })
      this.token = res.token
      this.username = res.username
      this.roles = res.roles
      localStorage.setItem('auth.token', this.token)
      localStorage.setItem('auth.username', this.username)
      localStorage.setItem('auth.roles', JSON.stringify(this.roles))
      try {
        const info = await authApi.me()
        this.fullName = info.fullName ?? null
        this.studentNo = info.studentNo ?? null
        localStorage.setItem('auth.fullName', this.fullName ?? '')
        localStorage.setItem('auth.studentNo', this.studentNo ?? '')
      } catch {}
      await router.replace(homePathForRoles(this.roles))
    },
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.clear()
        await router.replace('/login')
      }
    },
    clear() {
      this.token = null
      this.username = null
      this.fullName = null
      this.studentNo = null
      this.roles = []
      localStorage.removeItem('auth.token')
      localStorage.removeItem('auth.username')
      localStorage.removeItem('auth.fullName')
      localStorage.removeItem('auth.studentNo')
      localStorage.removeItem('auth.roles')
    },
  },
})
