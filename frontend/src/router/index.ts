import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { setupGuards } from './guards'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('../pages/login/LoginPage.vue'),
    meta: { public: true },
  },
  {
    path: '/student/exams/:id/take',
    component: () => import('../pages/student/ExamTakingPage.vue'),
    meta: { roles: ['STUDENT'] },
  },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/student/exams' },
      {
        path: 'student/exams',
        component: () => import('../pages/student/ExamListPage.vue'),
        meta: { roles: ['STUDENT'] },
      },
      {
        path: 'student/exams/:id/result',
        component: () => import('../pages/student/ExamResultPage.vue'),
        meta: { roles: ['STUDENT'] },
      },
      {
        path: 'student/classes',
        component: () => import('../pages/student/ClassListPage.vue'),
        meta: { roles: ['STUDENT'] },
      },
      {
        path: 'student/profile',
        component: () => import('../pages/student/ProfilePage.vue'),
        meta: { roles: ['STUDENT'] },
      },
      {
        path: 'teacher/questions',
        component: () => import('../pages/teacher/QuestionListPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/questions/create',
        component: () => import('../pages/teacher/QuestionEditPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/questions/:id/edit',
        component: () => import('../pages/teacher/QuestionEditPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/papers',
        component: () => import('../pages/teacher/PaperListPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/papers/create',
        component: () => import('../pages/teacher/PaperEditPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/papers/:id/edit',
        component: () => import('../pages/teacher/PaperEditPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/classes',
        component: () => import('../pages/teacher/ClassListPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/classes/:id',
        component: () => import('../pages/teacher/ClassDetailPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/profile',
        component: () => import('../pages/teacher/ProfilePage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams',
        component: () => import('../pages/teacher/ExamListPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams/create',
        component: () => import('../pages/teacher/ExamCreatePage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams/:id/edit',
        component: () => import('../pages/teacher/ExamCreatePage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams/:id/results',
        component: () => import('../pages/teacher/ExamResultsPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams/:id/analytics',
        component: () => import('../pages/teacher/ExamAnalyticsPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams/:id/monitor',
        component: () => import('../pages/teacher/ExamMonitorPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/exams/:id/edit',
        component: () => import('../pages/teacher/ExamCreatePage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'teacher/monitor',
        component: () => import('../pages/teacher/MonitorSelectionPage.vue'),
        meta: { roles: ['TEACHER'] },
      },
      {
        path: 'admin',
        component: () => import('../pages/admin/AdminPage.vue'),
        meta: { roles: ['ADMIN'] },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

setupGuards(router)

export default router
