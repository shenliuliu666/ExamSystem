<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import { formatDateTime } from '../../utils/time'
import type { ExamResponse, TeacherResultResponse } from '../../types/api'

const route = useRoute()
const router = useRouter()

const examId = Number(route.params.id)
const loading = ref(false)
const exam = ref<ExamResponse | null>(null)
const items = ref<TeacherResultResponse[]>([])

async function load() {
  loading.value = true
  try {
    const [e, res] = await Promise.all([teacherApi.getExam(examId), teacherApi.getExamResults(examId)])
    exam.value = e
    items.value = res
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function back() {
  await router.replace('/teacher/exams')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card class="card" v-loading="loading">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <div style="font-size: 16px; font-weight: 700">考试成绩</div>
          <el-text v-if="exam" type="info">
            #{{ exam.id }} {{ exam.name }}（{{ formatDateTime(exam.startAt) }} - {{ formatDateTime(exam.endAt) }}）
          </el-text>
        </div>
        <el-button plain @click="back">返回</el-button>
      </div>

      <el-table :data="items" style="width: 100%">
        <el-table-column prop="studentUsername" label="学生" min-width="160" />
        <el-table-column prop="totalScore" label="得分" width="120" />
        <el-table-column prop="maxScore" label="满分" width="120" />
        <el-table-column label="交卷时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
