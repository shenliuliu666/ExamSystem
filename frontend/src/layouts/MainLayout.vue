<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, Collection, Reading, User as UserIcon } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

type NavItem = {
  index: string
  label: string
  icon: any
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const studentMenu = computed<NavItem[]>(() => {
  if (!auth.hasRole('STUDENT')) return []
  return [
    { index: '/student/exams', label: '我的考试', icon: Document },
    { index: '/student/profile', label: '个人中心', icon: UserIcon },
  ]
})

const teacherMenu = computed<NavItem[]>(() => {
  if (!auth.hasRole('TEACHER')) return []
  return [
    { index: '/teacher/questions', label: '题库管理', icon: Document },
    { index: '/teacher/papers', label: '试卷管理', icon: Collection },
    { index: '/teacher/exams', label: '考试管理', icon: Reading },
    { index: '/teacher/profile', label: '个人中心', icon: UserIcon },
  ]
})

const active = computed(() => {
  const p = route.path
  if (p.startsWith('/student/exams')) return '/student/exams'
  if (p.startsWith('/student/profile')) return '/student/profile'
  if (p.startsWith('/teacher/questions')) return '/teacher/questions'
  if (p.startsWith('/teacher/papers')) return '/teacher/papers'
  if (p.startsWith('/teacher/classes')) return '/teacher/classes'
  if (p.startsWith('/teacher/exams')) return '/teacher/exams'
  if (p.startsWith('/teacher/profile')) return '/teacher/profile'
  return p
})

async function onSelect(index: string) {
  await router.push(index)
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="sider">
      <div class="brand">
        <div class="brand-logo">ES</div>
        <div class="brand-text">
          <div class="brand-title">在线考试系统</div>
          <div class="brand-subtitle">Examination System</div>
        </div>
      </div>
      <el-menu
        :default-active="active"
        class="sider-menu"
        background-color="transparent"
        text-color="#cbd5f5"
        active-text-color="#ffffff"
        @select="onSelect"
      >
        <template v-if="studentMenu.length">
          <el-menu-item-group title="学生空间">
            <el-menu-item v-for="i in studentMenu" :key="i.index" :index="i.index">
              <el-icon class="menu-icon">
                <component :is="i.icon" />
              </el-icon>
              <span>{{ i.label }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </template>
        <template v-if="teacherMenu.length">
          <el-menu-item-group title="教师空间">
            <el-menu-item v-for="i in teacherMenu" :key="i.index" :index="i.index">
              <el-icon class="menu-icon">
                <component :is="i.icon" />
              </el-icon>
              <span>{{ i.label }}</span>
            </el-menu-item>
          </el-menu-item-group>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">在线考试系统</div>
        <div class="header-right">
          <el-text type="info">{{ auth.username }}</el-text>
          <el-button type="primary" plain @click="auth.logout()">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.sider {
  background: linear-gradient(180deg, #0f172a, #1e293b);
  color: #e5e7eb;
  border-right: none;
}
.brand {
  display: flex;
  align-items: center;
  padding: 16px 20px 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.35);
}
.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-weight: 700;
  font-size: 14px;
  margin-right: 10px;
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.4);
}
.brand-text {
  display: flex;
  flex-direction: column;
}
.brand-title {
  font-size: 14px;
  font-weight: 600;
}
.brand-subtitle {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}
.sider-menu {
  border-right: none;
  margin-top: 4px;
}
:deep(.el-menu-item-group__title) {
  padding-left: 20px;
  font-size: 12px;
  color: #9ca3af;
}
.menu-icon {
  margin-right: 8px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  border-bottom: 1px solid #ebeef5;
  background: #ffffff;
}
.header-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.main {
  padding: 16px;
  background: #f5f7fa;
}
</style>
