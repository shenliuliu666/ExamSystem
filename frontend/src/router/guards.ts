import type { Router } from 'vue-router'
import { useAuthStore } from '../stores/auth'

export function setupGuards(router: Router) {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.ready) {
      await auth.hydrate()
    }

    if (to.meta.public) {
      if (auth.isAuthed && to.path === '/login') {
        if (auth.hasRole('TEACHER')) return '/teacher/questions'
        if (auth.hasRole('STUDENT')) return '/student/exams'
      }
      return true
    }

    if (!auth.isAuthed) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    const roles = (to.meta.roles as string[] | undefined) ?? []
    if (roles.length > 0 && !roles.some((r) => auth.roles.includes(r as any))) {
      if (auth.hasRole('TEACHER')) return '/teacher/questions'
      if (auth.hasRole('STUDENT')) return '/student/exams'
      return '/login'
    }

    return true
  })
}

