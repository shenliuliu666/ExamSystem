<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import type { ExamResponse } from '../../types/api'
import { formatDateTime } from '../../utils/time'

const router = useRouter()
const loading = ref(false)
const exams = ref<ExamResponse[]>([])

const activeExams = computed(() => {
  // Filter for exams that are IN_PROGRESS or maybe about to start
  // Or just list all and let user pick.
  // Ideally, monitor is only for IN_PROGRESS.
  return exams.value.filter(e => e.status === 'IN_PROGRESS')
})

const otherExams = computed(() => {
  return exams.value.filter(e => e.status !== 'IN_PROGRESS')
})

async function load() {
  loading.value = true
  try {
    // We reuse listExams.
    // Ideally backend should have a dedicated endpoint or we filter client side.
    exams.value = await teacherApi.listExams()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

function goMonitor(exam: ExamResponse) {
  router.push(`/teacher/exams/${exam.id}/monitor`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <div class="title">选择监考场次</div>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <div class="section-title">正在进行 ({{ activeExams.length }})</div>
    <div class="list" v-loading="loading">
       <el-empty v-if="!loading && activeExams.length === 0" description="当前没有正在进行的考试" />
       <el-card 
         v-for="exam in activeExams" 
         :key="exam.id" 
         class="exam-card active" 
         shadow="hover" 
         @click="goMonitor(exam)"
       >
          <div class="card-content">
             <div class="info">
                <h3>{{ exam.name }}</h3>
                <div class="meta">
                  <span>班级: {{ exam.className }}</span>
                  <span>{{ formatDateTime(exam.startAt) }} - {{ formatDateTime(exam.endAt) }}</span>
                </div>
             </div>
             <el-button type="primary" size="large" round>进入监考</el-button>
          </div>
       </el-card>
    </div>

    <div class="section-title" style="margin-top: 32px">其他考试</div>
    <div class="list" v-loading="loading">
       <el-card 
         v-for="exam in otherExams" 
         :key="exam.id" 
         class="exam-card" 
         shadow="hover" 
         @click="goMonitor(exam)"
       >
          <div class="card-content">
             <div class="info">
                <h3>{{ exam.name }}</h3>
                <div class="meta">
                   <el-tag size="small" :type="exam.status === 'ENDED' ? 'info' : 'warning'">
                     {{ exam.status === 'ENDED' ? '已结束' : '未开始' }}
                   </el-tag>
                   <span>班级: {{ exam.className }}</span>
                </div>
             </div>
             <el-button plain round>查看监考记录</el-button>
          </div>
       </el-card>
    </div>
  </div>
</template>

<style scoped>
.page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.title {
  font-size: 20px;
  font-weight: 600;
}

.section-title {
  font-size: 16px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 16px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exam-card {
  cursor: pointer;
  transition: all 0.2s;
}

.exam-card:hover {
  transform: translateY(-2px);
}

.exam-card.active {
  border-left: 4px solid var(--el-color-primary);
}

.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
}

.meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 14px;
  align-items: center;
}
</style>
