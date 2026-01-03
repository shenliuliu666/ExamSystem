<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search, User, Timer } from '@element-plus/icons-vue'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import type { ExamResponse, Classroom } from '../../types/api'
import { formatDateTime } from '../../utils/time'

const router = useRouter()
const loading = ref(false)
const exams = ref<ExamResponse[]>([])
const classes = ref<Classroom[]>([])

const keyword = ref('')
const filterClassId = ref<number | ''>('')
const filterStatus = ref<'' | 'NOT_STARTED' | 'IN_PROGRESS' | 'ENDED'>('')

const filteredExams = computed(() => {
  return exams.value.filter((e) => {
    if (keyword.value && !e.name.includes(keyword.value)) return false
    if (filterClassId.value && e.classId !== filterClassId.value) return false
    if (filterStatus.value && e.status !== filterStatus.value) return false
    return true
  })
})

async function load() {
  loading.value = true
  try {
    const [examsRes, classesRes] = await Promise.all([
      teacherApi.listExams(),
      teacherApi.listClasses()
    ])
    exams.value = examsRes
    classes.value = classesRes
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  await router.push('/teacher/exams/create')
}

async function goToMonitorSelection() {
  await router.push('/teacher/monitor')
}

async function openAnalytics(row: ExamResponse) {
  await router.push(`/teacher/exams/${row.id}/analytics`)
}

async function openEdit(row: ExamResponse) {
  await router.push(`/teacher/exams/${row.id}/edit`)
}

async function remove(row: ExamResponse) {
  try {
    await ElMessageBox.confirm('确定要删除该考试吗？此操作不可恢复。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await teacherApi.deleteExam(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
       ElMessage.error(pickErrorMessage(e))
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- Top Actions -->
    <div class="top-bar">
      <div class="left-actions">
        <el-button type="primary" size="large" round @click="openCreate" class="create-btn">
          <el-icon class="el-icon--left"><Plus /></el-icon> 新建考试
        </el-button>
        <el-button size="large" round @click="goToMonitorSelection">监考</el-button>
      </div>
      <div class="search-actions">
         <el-input v-model="keyword" placeholder="试卷名" :prefix-icon="Search" clearable class="search-input" />
      </div>
    </div>

    <!-- Filters -->
    <div class="filter-bar">
       <el-select v-model="filterClassId" placeholder="全部班级" clearable class="class-select">
          <el-option label="全部班级" :value="''" />
          <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
       </el-select>
       
       <div class="status-filter">
          <span class="filter-label">状态</span>
          <el-radio-group v-model="filterStatus">
             <el-radio label="">全部</el-radio>
             <el-radio label="NOT_STARTED">未开始</el-radio>
             <el-radio label="IN_PROGRESS">进行中</el-radio>
             <el-radio label="ENDED">已结束</el-radio>
          </el-radio-group>
       </div>
    </div>

    <!-- Exam List -->
    <div class="exam-list" v-loading="loading">
       <el-card v-for="exam in filteredExams" :key="exam.id" class="exam-card" shadow="hover">
          <div class="exam-card-content">
             <!-- Left -->
             <div class="exam-info">
                <div class="exam-name">{{ exam.name }}</div>
                <div class="exam-meta">
                   <div class="meta-item">
                      <el-icon><User /></el-icon>
                      <span>{{ exam.className || '默认班级' }}</span>
                   </div>
                   <div class="meta-item">
                      <el-icon><Timer /></el-icon>
                      <span>考试时间: {{ formatDateTime(exam.startAt) }} 至 {{ formatDateTime(exam.endAt) }}</span>
                   </div>
                </div>
             </div>
             
             <!-- Right -->
             <div class="exam-actions-area">
                <div class="exam-stats">
                   <span class="stat-item"><strong>{{ exam.submittedCount || 0 }}</strong> 已交</span>
                   <span class="stat-item"><strong>{{ exam.unsubmittedCount || 0 }}</strong> 未交</span>
                </div>
                <div class="exam-buttons">
                   <el-button type="primary" link @click="openEdit(exam)">修改设置</el-button>
                   <el-button type="danger" link @click="remove(exam)">删除</el-button>
                   <el-button type="primary" link @click="openAnalytics(exam)">结果分析</el-button>
                </div>
             </div>
          </div>
       </el-card>
       <el-empty v-if="!loading && filteredExams.length === 0" description="暂无考试" />
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.left-actions {
  display: flex;
  gap: 16px;
}

.create-btn {
  padding-left: 24px;
  padding-right: 24px;
}

.search-actions {
  width: 300px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 32px;
  margin-bottom: 24px;
  background-color: #fff;
  padding: 16px;
  border-radius: 8px;
}

.class-select {
  width: 200px;
}

.status-filter {
  display: flex;
  align-items: center;
  gap: 16px;
}

.filter-label {
  color: #606266;
  font-weight: 500;
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exam-card {
  border-radius: 8px;
  border: none;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.exam-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.exam-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.exam-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #909399;
  font-size: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exam-actions-area {
  display: flex;
  align-items: center;
  gap: 32px;
}

.exam-stats {
  display: flex;
  gap: 16px;
  color: #606266;
  font-size: 14px;
}

.stat-item strong {
  font-size: 18px;
  color: #303133;
  margin-right: 4px;
}

.exam-buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.main-action-btn {
  padding-left: 24px;
  padding-right: 24px;
  margin-left: 12px;
}
</style>
