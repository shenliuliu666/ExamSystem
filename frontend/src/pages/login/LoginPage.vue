<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import { pickErrorMessage } from '../../api/http'

const auth = useAuthStore()
const route = useRoute()

const activeTab = ref<'login' | 'register'>('login')

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)
const registering = ref(false)
const registerForm = reactive({
  username: '',
  password: '',
})

async function onSubmit() {
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
    if (redirect) {
      window.location.replace(redirect)
    }
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  registering.value = true
  try {
    await auth.register(registerForm.username.trim(), registerForm.password)
    registerForm.username = ''
    registerForm.password = ''
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    registering.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <div class="brand-panel">
        <div class="brand-badge">ES</div>
        <div class="brand-title">在线考试系统</div>
        <div class="brand-subtitle">登录后进入学生/教师工作台</div>
        <div class="brand-features">
          <div class="feature">统一身份登录，权限自动分流</div>
          <div class="feature">考试作答与成绩查看一体化</div>
          <div class="feature">题库、试卷、考试管理全流程</div>
        </div>
      </div>

      <el-card class="auth-card" shadow="never">
        <div class="card-head">
          <div class="head-title">欢迎使用</div>
          <div class="head-sub">请登录或注册账号</div>
        </div>

        <el-tabs v-model="activeTab" class="auth-tabs" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form :model="form" label-position="top" @submit.prevent="onSubmit">
              <el-form-item label="用户名">
                <el-input
                  v-model="form.username"
                  size="large"
                  autocomplete="username"
                  placeholder="请输入用户名"
                >
                  <template #prefix>
                    <el-icon><User /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="form.password"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="请输入密码"
                  @keyup.enter="onSubmit"
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-button class="primary-btn" type="primary" :loading="loading" size="large" @click="onSubmit">
                登录
              </el-button>
              <div class="switch-row">
                <span class="switch-text">没有账号？</span>
                <el-button link type="primary" @click="activeTab = 'register'">去注册</el-button>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form :model="registerForm" label-position="top">
              <el-form-item label="用户名">
                <el-input
                  v-model="registerForm.username"
                  size="large"
                  autocomplete="username"
                  placeholder="建议使用学号/工号作为用户名"
                >
                  <template #prefix>
                    <el-icon><User /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="密码">
                <el-input
                  v-model="registerForm.password"
                  size="large"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="请输入密码"
                  @keyup.enter="onRegister"
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>
              <el-button class="primary-btn" type="primary" :loading="registering" size="large" @click="onRegister">
                注册并登录
              </el-button>
              <div class="switch-row">
                <span class="switch-text">已有账号？</span>
                <el-button link type="primary" @click="activeTab = 'login'">去登录</el-button>
              </div>
            </el-form>
          </el-tab-pane>
        </el-tabs>

        <el-divider class="soft-divider" />
        <div class="tips">
          <div class="tip-title">演示账号</div>
          <div class="tip-row">
            <span class="tip-k">学生：</span>
            <span class="tip-v">student / student123</span>
          </div>
          <div class="tip-row">
            <span class="tip-k">教师：</span>
            <span class="tip-v">teacher / teacher123</span>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background:
    radial-gradient(900px 500px at 18% 12%, rgba(59, 130, 246, 0.22), transparent 70%),
    radial-gradient(700px 420px at 82% 18%, rgba(99, 102, 241, 0.16), transparent 65%),
    radial-gradient(900px 520px at 55% 90%, rgba(14, 165, 233, 0.12), transparent 70%),
    linear-gradient(180deg, #f6f9ff, #f5f7fa);
}

.auth-shell {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  gap: 18px;
  align-items: stretch;
}

.brand-panel {
  border-radius: 18px;
  padding: 28px 26px;
  background: linear-gradient(140deg, #0f172a, #1e293b);
  color: #e5e7eb;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18);
  border: 1px solid rgba(148, 163, 184, 0.22);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-badge {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: rgba(59, 130, 246, 0.95);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  letter-spacing: 0.6px;
  box-shadow:
    0 12px 24px rgba(37, 99, 235, 0.25),
    inset 0 0 0 1px rgba(255, 255, 255, 0.25);
}

.brand-title {
  margin-top: 14px;
  font-size: 22px;
  font-weight: 800;
  line-height: 1.2;
}

.brand-subtitle {
  margin-top: 8px;
  color: rgba(226, 232, 240, 0.82);
  font-size: 13px;
}

.brand-features {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feature {
  font-size: 13px;
  color: rgba(226, 232, 240, 0.88);
  line-height: 1.55;
}

.auth-card {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
  padding: 6px 6px 2px;
}

.card-head {
  padding: 10px 10px 2px;
}

.head-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.head-sub {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.auth-tabs {
  margin-top: 10px;
}

.primary-btn {
  width: 100%;
  border-radius: 12px;
  margin-top: 6px;
}

.switch-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.switch-text {
  color: #64748b;
  font-size: 13px;
}

.soft-divider {
  margin: 14px 0 10px;
}

.tips {
  padding: 2px 10px 14px;
}

.tip-title {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
}

.tip-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.tip-k {
  color: #475569;
}

.tip-v {
  color: #64748b;
}

@media (max-width: 860px) {
  .auth-shell {
    grid-template-columns: 1fr;
    gap: 14px;
  }
  .brand-panel {
    display: none;
  }
}

:deep(.el-tabs__item) {
  font-weight: 700;
}

:deep(.el-tabs__nav-wrap::after) {
  background-color: rgba(148, 163, 184, 0.35);
}
</style>
