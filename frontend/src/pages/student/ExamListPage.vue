<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as studentApi from '../../api/student'
import { pickErrorMessage } from '../../api/http'
import type { ExamResponse } from '../../types/api'
import { useRouter } from 'vue-router'
import { formatDateTime } from '../../utils/time'
import { Calendar, Timer, DocumentChecked, Trophy } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const exams = ref<ExamResponse[]>([])
const activeTab = ref<'pending' | 'completed'>('pending')

// Group exams based on myStatus
const pendingExams = computed(() => 
  exams.value.filter((e) => ['NOT_STARTED', 'IN_PROGRESS'].includes(e.myStatus || 'NOT_STARTED'))
)
const completedExams = computed(() => 
  exams.value.filter((e) => ['SUBMITTED', 'GRADED'].includes(e.myStatus || 'NOT_STARTED'))
)

function statusLabel(exam: ExamResponse) {
  if (exam.myStatus === 'GRADED') return '已出分'
  if (exam.myStatus === 'SUBMITTED') return '已提交'
  if (exam.myStatus === 'IN_PROGRESS') return '进行中'
  // For NOT_STARTED, check global status
  if (exam.status === 'ENDED') return '已错过'
  if (exam.status === 'IN_PROGRESS') return '进行中' // Global exam is open
  return '未开始'
}

function statusTagType(exam: ExamResponse) {
  if (exam.myStatus === 'GRADED') return 'success'
  if (exam.myStatus === 'SUBMITTED') return 'success'
  if (exam.myStatus === 'IN_PROGRESS') return 'warning'
  
  if (exam.status === 'ENDED') return 'info'
  if (exam.status === 'IN_PROGRESS') return 'primary'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    exams.value = await studentApi.listExams()
    // Auto switch tab if pending is empty but completed is not
    if (pendingExams.value.length === 0 && completedExams.value.length > 0) {
      activeTab.value = 'completed'
    }
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function goTake(row: ExamResponse) {
  await router.push(`/student/exams/${row.id}/take`)
}

async function goResult(row: ExamResponse) {
  await router.push(`/student/exams/${row.id}/result`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <div class="header">
        <div class="title">我的考试</div>
        <el-button type="primary" plain :icon="Calendar" :loading="loading" @click="load">刷新列表</el-button>
      </div>

      <el-tabs v-model="activeTab" class="exam-tabs">
        <el-tab-pane name="pending" label="待考 / 进行中">
          <div v-loading="loading">
            <el-empty v-if="pendingExams.length === 0" description="暂无待考考试" />
            <div v-else class="exam-grid">
              <el-card v-for="e in pendingExams" :key="e.id" class="exam-item" shadow="hover">
                <template #header>
                  <div class="exam-header">
                    <span class="exam-name">{{ e.name }}</span>
                    <el-tag :type="statusTagType(e)" size="small">{{ statusLabel(e) }}</el-tag>
                  </div>
                </template>
                <div class="exam-body">
                  <div class="info-row">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ formatDateTime(e.startAt) }} 开始</span>
                  </div>
                  <div class="info-row">
                    <el-icon><Timer /></el-icon>
                    <span>{{ formatDateTime(e.endAt) }} 结束</span>
                  </div>
                </div>
                <div class="exam-footer">
                  <el-button 
                    v-if="e.myStatus === 'IN_PROGRESS'" 
                    type="warning" 
                    style="width: 100%" 
                    @click="goTake(e)"
                  >
                    继续考试
                  </el-button>
                  <el-button 
                    v-else-if="e.status === 'IN_PROGRESS'" 
                    type="primary" 
                    style="width: 100%" 
                    @click="goTake(e)"
                  >
                    开始考试
                  </el-button>
                  <el-button 
                    v-else-if="e.status === 'NOT_STARTED'" 
                    disabled 
                    style="width: 100%"
                  >
                    尚未开始
                  </el-button>
                  <el-button 
                    v-else 
                    disabled 
                    type="info" 
                    plain 
                    style="width: 100%"
                  >
                    已结束
                  </el-button>
                </div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="completed" label="已完成">
          <div v-loading="loading">
            <el-empty v-if="completedExams.length === 0" description="暂无已完成考试" />
            <div v-else class="exam-grid">
              <el-card v-for="e in completedExams" :key="e.id" class="exam-item" shadow="hover">
                <template #header>
                  <div class="exam-header">
                    <span class="exam-name">{{ e.name }}</span>
                    <el-tag :type="statusTagType(e)" size="small">{{ statusLabel(e) }}</el-tag>
                  </div>
                </template>
                <div class="exam-body">
                  <div class="info-row">
                    <el-icon><Calendar /></el-icon>
                    <span>{{ formatDateTime(e.startAt) }}</span>
                  </div>
                  <div class="info-row" v-if="e.myStatus === 'GRADED'">
                    <el-icon><Trophy /></el-icon>
                    <span>已出成绩</span>
                  </div>
                  <div class="info-row" v-else>
                    <el-icon><DocumentChecked /></el-icon>
                    <span>等待阅卷</span>
                  </div>
                </div>
                <div class="exam-footer">
                  <el-button 
                    v-if="e.hasResult" 
                    type="success" 
                    plain 
                    style="width: 100%" 
                    @click="goResult(e)"
                  >
                    查看成绩
                  </el-button>
                  <el-button 
                    v-else 
                    type="info" 
                    plain 
                    style="width: 100%" 
                    disabled
                  >
                    查看详情
                  </el-button>
                </div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.header {
  display: flex; 
  align-items: center; 
  justify-content: space-between; 
  margin-bottom: 20px;
}
.title {
  font-size: 20px; 
  font-weight: 700;
}
.exam-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.exam-item {
  display: flex;
  flex-direction: column;
}
.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.exam-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.exam-body {
  padding: 10px 0;
  color: #606266;
  font-size: 14px;
}
.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.exam-footer {
  margin-top: 10px;
}
</style>
