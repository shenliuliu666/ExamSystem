import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

export const http = axios.create({
  baseURL,
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth.token')
  if (token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('auth.token')
      localStorage.removeItem('auth.username')
      localStorage.removeItem('auth.roles')
      if (router.currentRoute.value.path !== '/login') {
        await router.replace({ path: '/login' })
      }
    }
    if (status === 403) {
      ElMessage.error('无权限')
      const roles = (() => {
        try {
          return JSON.parse(localStorage.getItem('auth.roles') ?? '[]') as string[]
        } catch {
          return []
        }
      })()
      const has = (r: string) => roles.includes(r)
      const target = has('TEACHER') ? '/teacher/questions' : has('STUDENT') ? '/student/exams' : '/login'
      if (router.currentRoute.value.path !== target) {
        await router.replace(target)
      }
    }
    return Promise.reject(error)
  },
)

export function pickErrorMessage(error: unknown): string {
  const e = error as AxiosError<any>
  const data = e.response?.data
  if (data && typeof data === 'object') {
    if (typeof (data as any).message === 'string') {
      const m = String((data as any).message ?? '').trim()
      if (m && m !== 'No message available') {
        return m
      }
    }
    if (typeof (data as any).error === 'string') {
      const err = String((data as any).error ?? '').trim()
      if (err) {
        return err
      }
    }
  }
  if (e.message) {
    return e.message
  }
  return '请求失败，请稍后重试'
}
