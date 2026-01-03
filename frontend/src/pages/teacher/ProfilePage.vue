<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { ElMessage } from 'element-plus'
import * as api from '../../api/teacher'
import { pickErrorMessage } from '../../api/http'
import type { Classroom, ExamResponse, QuestionBank } from '../../types/api'
import { User, School, DataAnalysis, ArrowRight, Calendar, Timer, DocumentChecked } from '@element-plus/icons-vue'
import { formatDateTime } from '../../utils/time'

const router = useRouter()
const auth = useAuthStore()

const loadingClasses = ref(false)
const classes = ref<Classroom[]>([])
const loadingOverview = ref(false)
const banks = ref<QuestionBank[]>([])
const exams = ref<ExamResponse[]>([])

const topClasses = computed(() => classes.value.slice(0, 3))
const totalMembers = computed(() => classes.value.reduce((sum, c) => sum + (c.memberCount ?? 0), 0))

const examTotal = computed(() => exams.value.length)
const examUpcomingCount = computed(() => exams.value.filter((e) => e.status === 'NOT_STARTED').length)
const examInProgressCount = computed(() => exams.value.filter((e) => e.status === 'IN_PROGRESS').length)
const examEndedCount = computed(() => exams.value.filter((e) => e.status === 'ENDED').length)

const upcomingExams = computed(() => {
  const now = Date.now()
  return exams.value
    .filter((e) => e.status === 'NOT_STARTED')
    .filter((e) => new Date(e.startAt).getTime() >= now)
    .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime())
    .slice(0, 5)
})

const endedPercent = computed(() => {
  if (!examTotal.value) return 0
  return Math.round((examEndedCount.value / examTotal.value) * 100)
})

async function loadClasses() {
  loadingClasses.value = true
  try {
    classes.value = await api.listClasses()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loadingClasses.value = false
  }
}

async function loadOverview() {
  loadingOverview.value = true
  try {
    const [banksRes, examsRes] = await Promise.all([api.listQuestionBanks(), api.listExams()])
    banks.value = banksRes
    exams.value = examsRes
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loadingOverview.value = false
  }
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString()
}

function formatCountdown(iso: string) {
  const diff = new Date(iso).getTime() - Date.now()
  if (diff <= 0) return '已开始'
  const minutes = Math.ceil(diff / 60000)
  if (minutes < 60) return `${minutes} 分钟后`
  const hours = Math.ceil(diff / 3600000)
  if (hours < 24) return `${hours} 小时后`
  const days = Math.ceil(diff / 86400000)
  return `${days} 天后`
}

function goToClassList() {
  router.push('/teacher/classes')
}

function goToClassDetail(c: Classroom) {
  router.push(`/teacher/classes/${c.id}`)
}

function goToQuestions() {
  router.push('/teacher/questions')
}

function goToPapers() {
  router.push('/teacher/papers')
}

function goToExams() {
  router.push('/teacher/exams')
}

function goToMonitor() {
  router.push('/teacher/monitor')
}

onMounted(async () => {
  await Promise.all([loadClasses(), loadOverview()])
})
</script>

<template>
  <div class="page">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="never" class="profile-card">
          <div class="user-info">
            <el-avatar :size="64" :icon="User" />
            <div class="username">{{ auth.username }}</div>
            <el-tag size="small" type="success">教师</el-tag>
          </div>
          <el-divider />
          <div class="info-list">
            <div class="info-item">
              <el-icon><User /></el-icon>
              <span>账号：{{ auth.username }}</span>
            </div>
            <div class="info-item">
              <el-icon><School /></el-icon>
              <span>角色：教师</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon><DataAnalysis /></el-icon>
                <span>班级管理</span>
              </div>
              <el-button type="primary" link @click="goToClassList">
                管理班级 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <div class="class-section" v-loading="loadingClasses || loadingOverview">
            <el-empty v-if="classes.length === 0" description="暂无班级" />
            <div v-else class="class-row">
              <div
                v-for="c in topClasses"
                :key="c.id"
                class="mini-class-card"
                @click="goToClassDetail(c)"
              >
                <div class="mini-card-header">
                  <span class="class-name">{{ c.name }}</span>
                </div>
                <div class="mini-card-body">
                  <div class="info-line">
                    <el-icon><User /></el-icon>
                    <span>{{ c.memberCount || 0 }} 人</span>
                  </div>
                  <div class="info-line">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ formatDate(c.createdAt) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <el-divider content-position="left">概览</el-divider>
            <el-row :gutter="12" class="stat-row">
              <el-col :span="6">
                <div class="stat-tile">
                  <div class="stat-label">班级</div>
                  <div class="stat-value">{{ classes.length }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-tile">
                  <div class="stat-label">成员</div>
                  <div class="stat-value">{{ totalMembers }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-tile">
                  <div class="stat-label">题库</div>
                  <div class="stat-value">{{ banks.length }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-tile">
                  <div class="stat-label">未开始考试</div>
                  <div class="stat-value">{{ examUpcomingCount }}</div>
                </div>
              </el-col>
            </el-row>

            <el-row :gutter="12" class="module-row">
              <el-col :span="14">
                <div class="module-card">
                  <div class="module-title">
                    <div class="module-left">
                      <el-icon><Timer /></el-icon>
                      <span>即将进行的考试</span>
                    </div>
                    <el-button link type="primary" @click="goToExams">
                      查看全部 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
                    </el-button>
                  </div>

                  <el-empty v-if="upcomingExams.length === 0" description="暂无即将开始的考试" />
                  <div v-else class="upcoming-list">
                    <div v-for="e in upcomingExams" :key="e.id" class="upcoming-item">
                      <div class="upcoming-main">
                        <div class="upcoming-name">{{ e.name }}</div>
                        <div class="upcoming-meta">
                          <span class="meta-pill">{{ e.className || `班级#${e.classId}` }}</span>
                          <span class="meta-text">开始：{{ formatDateTime(e.startAt) }}</span>
                        </div>
                      </div>
                      <div class="upcoming-right">
                        <el-tag type="warning" effect="plain">{{ formatCountdown(e.startAt) }}</el-tag>
                      </div>
                    </div>
                  </div>
                </div>
              </el-col>

              <el-col :span="10">
                <div class="module-card">
                  <div class="module-title">
                    <div class="module-left">
                      <el-icon><DataAnalysis /></el-icon>
                      <span>数据概览</span>
                    </div>
                  </div>

                  <div class="exam-status">
                    <div class="exam-status-top">
                      <span class="exam-status-title">考试进度</span>
                      <span class="exam-status-sub">{{ examEndedCount }}/{{ examTotal }} 已结束</span>
                    </div>
                    <el-progress :percentage="endedPercent" :stroke-width="10" />
                    <div class="exam-status-tags">
                      <el-tag effect="plain">未开始 {{ examUpcomingCount }}</el-tag>
                      <el-tag type="success" effect="plain">进行中 {{ examInProgressCount }}</el-tag>
                      <el-tag type="info" effect="plain">已结束 {{ examEndedCount }}</el-tag>
                    </div>
                  </div>

                  <el-divider />

                  <div class="quick-actions">
                    <el-button class="quick-btn" plain @click="goToQuestions">
                      <el-icon class="el-icon--left"><DocumentChecked /></el-icon> 题库管理
                    </el-button>
                    <el-button class="quick-btn" plain @click="goToPapers">
                      <el-icon class="el-icon--left"><DocumentChecked /></el-icon> 试卷管理
                    </el-button>
                    <el-button class="quick-btn" plain @click="goToExams">
                      <el-icon class="el-icon--left"><Timer /></el-icon> 考试管理
                    </el-button>
                    <el-button class="quick-btn" plain @click="goToMonitor">
                      <el-icon class="el-icon--left"><DataAnalysis /></el-icon> 监考
                    </el-button>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.profile-card {
  text-align: center;
}
.user-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 0;
}
.username {
  font-size: 18px;
  font-weight: 700;
}
.info-list {
  text-align: left;
  padding: 0 20px;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #606266;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: bold;
}
.class-section {
  padding: 10px 0;
}
.class-row {
  display: flex;
  gap: 16px;
}
.stat-row {
  margin-top: 6px;
  margin-bottom: 10px;
}
.stat-tile {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px 14px;
  background: #ffffff;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.1;
}
.module-row {
  margin-top: 6px;
}
.module-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #ffffff;
  padding: 14px;
  min-height: 240px;
}
.module-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.module-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: #303133;
}
.upcoming-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.upcoming-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #f0f2f5;
  background: #fafcff;
}
.upcoming-main {
  min-width: 0;
}
.upcoming-name {
  font-weight: 700;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.upcoming-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.meta-pill {
  font-size: 12px;
  color: #409eff;
  background: rgba(64, 158, 255, 0.12);
  border-radius: 999px;
  padding: 2px 8px;
}
.meta-text {
  font-size: 12px;
  color: #909399;
}
.exam-status-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}
.exam-status-title {
  font-weight: 700;
  color: #303133;
}
.exam-status-sub {
  font-size: 12px;
  color: #909399;
}
.exam-status-tags {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.quick-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.quick-btn {
  width: 100%;
  justify-content: flex-start;
}
.mini-class-card {
  flex: 1;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
  background-color: #fff;
  min-width: 0; /* prevent flex overflow */
}
.mini-class-card:hover {
  border-color: #c6e2ff;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}
.mini-card-header {
  margin-bottom: 12px;
}
.class-name {
  font-weight: bold;
  font-size: 15px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}
.mini-card-body {
  font-size: 13px;
  color: #909399;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.info-line {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
