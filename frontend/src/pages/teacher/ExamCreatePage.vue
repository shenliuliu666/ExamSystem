<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import type { Classroom, ExamSettings, PaperResponse } from '../../types/api'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const saving = ref(false)
const papers = ref<PaperResponse[]>([])
const classes = ref<Classroom[]>([])

const isEdit = ref(false)
const examId = ref(0)

const form = reactive<{
  name: string
  classId: number | null
  paperId: number | null
  startAt: Date | null
  endAt: Date | null
  settings: ExamSettings
  startTimeType: 'IMMEDIATE' | 'SCHEDULED'
  endTimeType: 'SCHEDULED'
}>({
  name: '',
  classId: null,
  paperId: null,
  startAt: null,
  endAt: null,
  startTimeType: 'SCHEDULED',
  endTimeType: 'SCHEDULED',
  settings: {
    durationMinutes: null,
    shuffleQuestions: false,
    enableHeartbeat: true,
    recordTabSwitch: true,
    autoSubmitOnTimeout: true,
    allowReviewPaper: true,
    showAnswersStrategy: 'AFTER_SUBMISSION',
    showScore: true,
  },
})

async function loadClasses() {
  try {
    classes.value = await teacherApi.listClasses()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  }
}

async function loadPapers() {
  loading.value = true
  try {
    const res = await teacherApi.listPapers({ page: 1, size: 200 })
    papers.value = res.items
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadExam(id: number) {
  loading.value = true
  try {
    const exam = await teacherApi.getExam(id)
    form.name = exam.name
    form.classId = exam.classId || null
    form.paperId = exam.paperId
    form.startAt = new Date(exam.startAt)
    form.endAt = new Date(exam.endAt)
    if (exam.settings) {
      form.settings = { ...form.settings, ...exam.settings }
    }
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.error('请填写考试名称')
    return
  }
  if (!form.paperId) {
    ElMessage.error('请选择试卷')
    return
  }
  if (!form.classId) {
    ElMessage.error('请选择班级')
    return
  }

  // Handle start/end time based on radio selection
  if (form.startTimeType === 'IMMEDIATE') {
    form.startAt = new Date()
    // Add a small buffer if needed, but 'now' is fine
  }
  
  // endTimeType is currently always SCHEDULED based on UI request
  
  if (!form.startAt || !form.endAt) {
    ElMessage.error('请选择开始与结束时间')
    return
  }
  if (form.endAt.getTime() <= form.startAt.getTime()) {
    ElMessage.error('结束时间必须晚于开始时间')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      paperId: form.paperId,
      classId: form.classId,
      startAt: form.startAt.toISOString(),
      endAt: form.endAt.toISOString(),
      settings: form.settings,
    }
    
    if (isEdit.value) {
      await teacherApi.updateExam(examId.value, payload)
      ElMessage.success('已更新')
    } else {
      await teacherApi.createExam(payload)
      ElMessage.success('已创建')
    }
    await router.replace('/teacher/exams')
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    saving.value = false
  }
}

async function back() {
  await router.replace('/teacher/exams')
}

onMounted(async () => {
  await loadClasses()
  await loadPapers()
  
  if (route.params.id) {
    isEdit.value = true
    examId.value = Number(route.params.id)
    await loadExam(examId.value)
  }
})
</script>

<template>
  <div class="page">
    <el-card class="card" v-loading="loading">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="font-size: 16px; font-weight: 700">{{ isEdit ? '编辑考试' : '创建考试' }}</div>
        <div style="display: flex; gap: 8px">
          <el-button plain @click="back">返回</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>

      <el-form label-width="100px" style="max-width: 560px">
        <el-form-item label="考试名称">
          <el-input v-model="form.name" placeholder="例如：期中考试" />
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="form.classId" placeholder="选择班级" filterable style="width: 100%">
            <el-option v-for="c in classes" :key="c.id" :label="`#${c.id} ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷">
          <el-select v-model="form.paperId" placeholder="选择试卷" filterable style="width: 100%">
            <el-option v-for="p in papers" :key="p.id" :label="`#${p.id} ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <div style="display: flex; flex-direction: column; gap: 8px; width: 100%">
            <el-radio-group v-model="form.startTimeType">
              <el-radio label="IMMEDIATE">立即开始</el-radio>
              <el-radio label="SCHEDULED">定时开始</el-radio>
            </el-radio-group>
            <el-date-picker
              v-if="form.startTimeType === 'SCHEDULED'"
              v-model="form.startAt"
              type="datetime"
              placeholder="选择开始时间"
              style="width: 100%"
            />
          </div>
        </el-form-item>
        <el-form-item label="截止时间">
           <div style="display: flex; flex-direction: column; gap: 8px; width: 100%">
            <el-radio-group v-model="form.endTimeType">
              <!-- "Immediate Stop" is confusing for creation, user request image shows "Scheduled Stop" checked. 
                   We keep it as "Scheduled" primarily, but maybe add "Immediate" if user really wants to close it? 
                   Wait, "Immediate Stop" in creation makes no sense. The image has "Immediate Stop" radio but unchecked. 
                   Let's assume "Immediate Stop" is just a UI option that might mean "Close Now" (only useful for edit).
                   For creation, we default to Scheduled. 
                   Actually, let's just show the UI as requested. -->
              <el-radio label="IMMEDIATE" disabled>立即截止</el-radio>
              <el-radio label="SCHEDULED">定时截止</el-radio>
            </el-radio-group>
            <el-date-picker
              v-model="form.endAt"
              type="datetime"
              placeholder="选择截止时间"
              style="width: 100%"
            />
          </div>
        </el-form-item>
        <el-form-item label="考试限时">
          <div style="display: flex; align-items: center">
            <el-input-number
              v-model="form.settings.durationMinutes"
              :min="0"
              :max="600"
              :step="10"
              controls-position="right"
              style="width: 120px"
            />
            <span style="margin-left: 8px; color: #606266">分钟</span>
          </div>
        </el-form-item>

        <el-divider content-position="left">高级设置</el-divider>

        <el-form-item label="交卷设置">
            <el-checkbox v-model="form.settings.autoSubmitOnTimeout" label="考试到达截止时间后自动提交" />
        </el-form-item>

        <el-form-item label="试卷设置">
            <div style="display: flex; flex-direction: column; gap: 4px">
                <el-checkbox v-model="form.settings.allowReviewPaper" label="允许学生考后查看试卷" />
                
                <div style="display: flex; align-items: center; gap: 8px; margin-left: 24px" v-if="form.settings.allowReviewPaper">
                    <span style="color: #606266; font-size: 14px">允许查看答案</span>
                    <el-select v-model="form.settings.showAnswersStrategy" style="width: 180px">
                        <el-option label="学生提交后" value="AFTER_SUBMISSION" />
                        <el-option label="考试截止后" value="AFTER_DEADLINE" />
                        <el-option label="从不" value="NONE" />
                    </el-select>
                </div>

                <el-checkbox v-model="form.settings.showScore" label="允许学生查看分数" />
            </div>
        </el-form-item>

        <el-form-item label="防作弊">
            <div style="display: flex; flex-direction: column; gap: 4px">
                <el-checkbox v-model="form.settings.shuffleQuestions" label="随机题目顺序" />
                <el-checkbox v-model="form.settings.enableHeartbeat" label="启用心跳监控" />
                <el-checkbox v-model="form.settings.recordTabSwitch" label="记录切屏事件" />
            </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}
.card {
  min-height: 600px;
}
</style>
