<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { User } from '@element-plus/icons-vue'
import * as studentApi from '../../api/student'
import { pickErrorMessage } from '../../api/http'
import type { AttemptStartResponse, ExamResponse, QuestionSnapshot } from '../../types/api'
import QuestionRenderer from '../../components/QuestionRenderer.vue'
import CountdownTimer from '../../components/CountdownTimer.vue'
import { useAuthStore } from '../../stores/auth'
import { formatDateTime } from '../../utils/time'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const examId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitting = ref(false)
const attempt = ref<AttemptStartResponse | null>(null)
const examInfo = ref<ExamResponse | null>(null)

const answers = reactive<Record<number, string>>({})
let heartbeatTimer: number | null = null
let proctorEventCount = 0
let handleBlur: (() => void) | null = null
let handleFocus: (() => void) | null = null
let handleVisibilityChange: (() => void) | null = null
let proctorMessageTimer: number | null = null
let lastProctorMessageId = 0
const forceSubmitted = ref(false)

const currentIndex = ref(0)
const previewVisible = ref(false)

function cacheKey() {
  if (!attempt.value) return null
  return `exam:${attempt.value.examId}:attempt:${attempt.value.attemptId}`
}

function proctorMessageKey() {
  if (!attempt.value) return null
  return `exam:${attempt.value.examId}:attempt:${attempt.value.attemptId}:lastProctorMessageId`
}

function loadLastProctorMessageId() {
  const key = proctorMessageKey()
  if (!key) return
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return
    const n = Number(raw)
    if (Number.isFinite(n) && n > 0) {
      lastProctorMessageId = n
    }
  } catch {}
}

function persistLastProctorMessageId() {
  const key = proctorMessageKey()
  if (!key) return
  try {
    localStorage.setItem(key, String(lastProctorMessageId))
  } catch {}
}

function loadCachedAnswers() {
  const key = cacheKey()
  if (!key) return
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return
    const parsed = JSON.parse(raw) as Record<string, string>
    Object.entries(parsed).forEach(([k, v]) => {
      const id = Number(k)
      if (Number.isFinite(id)) {
        answers[id] = v
      }
    })
  } catch {}
}

function persistAnswers() {
  const key = cacheKey()
  if (!key) return
  localStorage.setItem(key, JSON.stringify(answers))
}

async function start() {
  loading.value = true
  try {
    const [attemptRes, examRes] = await Promise.all([
      studentApi.startExam(examId.value),
      studentApi.getExam(examId.value).catch(() => null),
    ])
    attempt.value = attemptRes
    if (examRes) {
      examInfo.value = examRes
    }
    loadLastProctorMessageId()
    loadCachedAnswers()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
    await router.replace('/student/exams')
  } finally {
    loading.value = false
  }
}

async function sendHeartbeat() {
  if (!attempt.value) return
  try {
    await studentApi.sendHeartbeat(examId.value, { attemptId: attempt.value.attemptId })
  } catch {}
}

async function sendProctorEvent(type: string) {
  if (!attempt.value) return
  if (proctorEventCount >= 50) return
  proctorEventCount += 1
  try {
    await studentApi.sendProctorEvent(examId.value, {
      attemptId: attempt.value.attemptId,
      type,
    })
  } catch {}
}

async function pollProctorMessages() {
  if (!attempt.value) return
  try {
    const messages = await studentApi.pollProctorMessages(examId.value, {
      attemptId: attempt.value.attemptId,
      afterEventId: lastProctorMessageId || undefined,
    })
    if (!messages || messages.length === 0) return
    for (const m of messages) {
      lastProctorMessageId = Math.max(lastProctorMessageId, m.id)
      persistLastProctorMessageId()
      if (m.type === 'TEACHER_REMIND') {
        await ElMessageBox.alert(m.message || '监考老师发送了提醒信息。', '监考提醒', {
          confirmButtonText: '我知道了',
        }).catch(() => {})
      } else if (m.type === 'TEACHER_FORCE_SUBMIT') {
        if (forceSubmitted.value) {
          continue
        }
        forceSubmitted.value = true
        if (heartbeatTimer !== null) {
          window.clearInterval(heartbeatTimer)
          heartbeatTimer = null
        }
        if (proctorMessageTimer !== null) {
          window.clearInterval(proctorMessageTimer)
          proctorMessageTimer = null
        }
        const key = cacheKey()
        if (key) localStorage.removeItem(key)
        await ElMessageBox.alert('监考老师已为你强制交卷，本次考试已结束。', '考试结束', {
          confirmButtonText: '查看结果',
          showClose: false,
          closeOnClickModal: false,
          closeOnPressEscape: false,
        }).catch(() => {})
        await router.replace(`/student/exams/${examId.value}/result`)
        break
      }
    }
  } catch {}
}

function questions(): QuestionSnapshot[] {
  return attempt.value?.questions ?? []
}

const flatQuestions = computed(() => questions())

const totalQuestions = computed(() => flatQuestions.value.length)

const totalScore = computed(() =>
  flatQuestions.value.reduce((sum, q) => sum + (q.score || 0), 0),
)

type GroupedQuestion = {
  type: QuestionSnapshot['type']
  label: string
  questions: { index: number; question: QuestionSnapshot }[]
  totalScore: number
}

function questionTypeLabel(type: QuestionSnapshot['type']) {
  if (type === 'SINGLE_CHOICE') return '单选题'
  if (type === 'MULTIPLE_CHOICE') return '多选题'
  if (type === 'TRUE_FALSE') return '判断题'
  return type
}

const groupedQuestions = computed<GroupedQuestion[]>(() => {
  const groups: GroupedQuestion[] = []
  const typeIndex = new Map<QuestionSnapshot['type'], number>()
  flatQuestions.value.forEach((q, index) => {
    let gi = typeIndex.get(q.type)
    if (gi === undefined) {
      gi = groups.length
      typeIndex.set(q.type, gi)
      groups.push({
        type: q.type,
        label: questionTypeLabel(q.type),
        questions: [],
        totalScore: 0,
      })
    }
    const group = groups[gi]
    group!.questions.push({ index, question: q })
    group!.totalScore += q.score || 0
  })
  return groups
})

const currentQuestion = computed(() => flatQuestions.value[currentIndex.value] || null)

const currentGroupMeta = computed(() => {
  const q = currentQuestion.value
  if (!q) return null
  const groups = groupedQuestions.value
  for (let gi = 0; gi < groups.length; gi += 1) {
    const group = groups[gi]!
    const idxInGroup = group.questions.findIndex((item) => item.question.id === q.id)
    if (idxInGroup !== -1) {
      return {
        groupIndex: gi,
        groupLabel: group.label,
        questionIndexInGroup: idxInGroup,
        totalQuestionsInGroup: group.questions.length,
        totalScoreInGroup: group.totalScore,
      }
    }
  }
  return null
})

const chineseNumbers = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']

function sectionPrefix(index: number) {
  const ch = chineseNumbers[index] || String(index + 1)
  return `${ch}、`
}

function goQuestion(index: number) {
  if (index < 0 || index >= flatQuestions.value.length) return
  currentIndex.value = index
}

const isFirst = computed(() => currentIndex.value === 0)

const isLast = computed(() => currentIndex.value >= flatQuestions.value.length - 1)

function isAnswered(questionId: number) {
  const v = answers[questionId]
  return v !== undefined && v !== ''
}

function goPrev() {
  goQuestion(currentIndex.value - 1)
}

function goNext() {
  goQuestion(currentIndex.value + 1)
}

async function onSubmit() {
  if (!attempt.value) return
  if (forceSubmitted.value) return
  const confirmed = await ElMessageBox.confirm('确认交卷？交卷后无法继续作答。', '交卷确认', {
    type: 'warning',
    confirmButtonText: '交卷',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (!confirmed) return

  submitting.value = true
  try {
    persistAnswers()
    const payload = {
      attemptId: attempt.value.attemptId,
      answers: questions()
        .map((q) => ({ questionId: q.id, answer: answers[q.id] ?? '' }))
        .filter((a) => a.answer !== ''),
    }
    await studentApi.submitExam(examId.value, payload)
    const key = cacheKey()
    if (key) localStorage.removeItem(key)
    ElMessage.success('交卷成功')
    await router.replace(`/student/exams/${examId.value}/result`)
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    submitting.value = false
  }
}

async function onTimeout() {
  if (submitting.value) return
  if (!attempt.value) return
  if (forceSubmitted.value) return
  submitting.value = true
  try {
    const payload = {
      attemptId: attempt.value.attemptId,
      answers: questions()
        .map((q) => ({ questionId: q.id, answer: answers[q.id] ?? '' }))
        .filter((a) => a.answer !== ''),
    }
    await studentApi.submitExam(examId.value, payload)
  } catch {
  } finally {
    const key = cacheKey()
    if (key) localStorage.removeItem(key)
    await router.replace(`/student/exams/${examId.value}/result`)
  }
}

onMounted(() => {
  start()
  heartbeatTimer = window.setInterval(() => {
    sendHeartbeat()
  }, 10000)
  proctorMessageTimer = window.setInterval(() => {
    pollProctorMessages()
  }, 7000)
  handleBlur = () => {
    sendProctorEvent('WINDOW_BLUR')
  }
  handleFocus = () => {
    sendProctorEvent('WINDOW_FOCUS')
  }
  handleVisibilityChange = () => {
    if (document.hidden) {
      sendProctorEvent('VISIBILITY_HIDDEN')
    } else {
      sendProctorEvent('VISIBILITY_VISIBLE')
    }
  }
  window.addEventListener('blur', handleBlur)
  window.addEventListener('focus', handleFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  if (heartbeatTimer !== null) {
    window.clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  if (proctorMessageTimer !== null) {
    window.clearInterval(proctorMessageTimer)
    proctorMessageTimer = null
  }
  if (handleBlur) {
    window.removeEventListener('blur', handleBlur)
    handleBlur = null
  }
  if (handleFocus) {
    window.removeEventListener('focus', handleFocus)
    handleFocus = null
  }
  if (handleVisibilityChange) {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    handleVisibilityChange = null
  }
})
</script>

<template>
  <div class="exam-page" v-loading="loading">
    <div class="exam-header">
      <div class="exam-header-left">
        <span class="exam-header-title">{{ examInfo?.name || '考试' }}</span>
      </div>
      <div class="exam-header-right">
        <el-button type="primary" plain @click="previewVisible = true">整卷预览</el-button>
      </div>
    </div>

    <div v-if="attempt" class="exam-main">
      <div class="exam-sidebar">
        <div class="exam-info-card">
          <div class="exam-timer">
            <CountdownTimer :end-at="attempt.endAt" @done="onTimeout" />
          </div>
          <el-divider />
          <div class="exam-info-row">
            <span class="label">考试编号</span>
            <span class="value">{{ attempt.examId }}</span>
          </div>
          <div class="exam-info-row">
            <span class="label">考试名称</span>
            <span class="value">{{ examInfo?.name || '-' }}</span>
          </div>
          <div class="exam-info-row">
            <span class="label">姓名/学号</span>
            <span class="value">
              <el-icon class="inline-icon"><User /></el-icon>
              <span>{{ attempt.studentName || '未知' }} / {{ attempt.studentNo || auth.studentNo || auth.username }}</span>
            </span>
          </div>
          <div class="exam-info-row">
            <span class="label">所属班级</span>
            <span class="value">{{ attempt.className || examInfo?.className || '未加入班级' }}</span>
          </div>
          <div class="exam-info-row">
            <span class="label">题目数量</span>
            <span class="value">{{ totalQuestions }} 题</span>
          </div>
          <div class="exam-info-row">
            <span class="label">试卷总分</span>
            <span class="value">{{ totalScore }} 分</span>
          </div>
          <div class="exam-info-row">
            <span class="label">考试时间</span>
            <span class="value">
              {{ formatDateTime(examInfo?.startAt) }} 至 {{ formatDateTime(examInfo?.endAt) }}
            </span>
          </div>
        </div>

        <el-button class="submit-btn" type="primary" :loading="submitting" @click="onSubmit">
          交卷
        </el-button>
      </div>

        <div v-if="currentQuestion" class="exam-content">
        <div v-if="currentGroupMeta" class="question-header">
          <div class="question-title">
            <span class="section-prefix">{{ sectionPrefix(currentGroupMeta.groupIndex) }}</span>
            <span class="section-name">{{ currentGroupMeta.groupLabel }}</span>
            <span class="section-extra">
              （共 {{ currentGroupMeta.totalQuestionsInGroup }} 题，{{ currentGroupMeta.totalScoreInGroup }} 分）
            </span>
          </div>
        </div>

        <div class="question-body">
          <div class="question-index">
            <span class="q-no">{{ currentIndex + 1 }}.</span>
            <span class="q-meta">（{{ questionTypeLabel(currentQuestion.type) }}，{{ currentQuestion.score }}分）</span>
          </div>

          <QuestionRenderer
            :question="currentQuestion"
            :model-value="answers[currentQuestion!.id] ?? ''"
            @update:model-value="
              (v) => {
                answers[currentQuestion!.id] = v
                persistAnswers()
              }
            "
          />

          <div class="question-footer">
            <el-button plain :disabled="isFirst" @click="goPrev">上一题</el-button>
            <div class="spacer" />
            <el-button v-if="!isLast" type="primary" plain @click="goNext">下一题</el-button>
            <el-button v-else type="success" :loading="submitting" @click="onSubmit">
              提交试卷
            </el-button>
          </div>
        </div>
      </div>

      <div class="exam-nav">
        <div class="nav-header">
          <div class="nav-title">题目导航</div>
          <div class="nav-legend">
            <el-tag size="small" type="primary">当前题目</el-tag>
            <el-tag size="small" type="success">已作答</el-tag>
            <el-tag size="small" type="info">未作答</el-tag>
          </div>
        </div>
        <div class="nav-groups">
          <div v-for="(group, gIndex) in groupedQuestions" :key="group.type" class="nav-group">
          <div class="nav-group-title">
            {{ sectionPrefix(gIndex) }}{{ group.label }}
            <span class="nav-group-extra">
              （{{ group.questions.length }} 题，{{ group.totalScore }} 分）
            </span>
          </div>
            <div class="nav-question-list">
              <el-button
                v-for="item in group.questions"
                :key="item.question.id"
                class="nav-question"
                :type="
                  currentIndex === item.index
                    ? 'primary'
                    : isAnswered(item.question.id)
                      ? 'success'
                      : 'info'
                "
                size="small"
                @click="goQuestion(item.index)"
              >
                {{ item.index + 1 }}
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="加载考试数据失败" />
  </div>

  <el-dialog
    v-model="previewVisible"
    title="整卷预览"
    width="80%"
    class="exam-preview-dialog"
    destroy-on-close
  >
    <div v-if="attempt" class="preview-scroll">
      <div v-for="(q, idx) in questions()" :key="q.id" class="preview-question">
        <div class="preview-question-header">
          <div class="preview-question-title">第 {{ idx + 1 }} 题</div>
          <el-tag type="primary" effect="plain">{{ q.score }} 分</el-tag>
        </div>
        <QuestionRenderer
          :question="q"
          :model-value="answers[q.id] ?? ''"
          @update:model-value="
            (v) => {
              answers[q.id] = v
              persistAnswers()
            }
          "
        />
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.exam-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.exam-header {
  height: 60px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg, #1f2937, #111827);
  color: #fff;
}

.exam-header-title {
  font-size: 20px;
  font-weight: 700;
}

.exam-main {
  flex: 1;
  display: flex;
  padding: 16px 24px 24px;
  box-sizing: border-box;
  gap: 16px;
}

.exam-sidebar {
  width: 300px;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #ebeef5;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.exam-info-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exam-timer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: #fffbf0;
  border-radius: 12px;
  border: 1px solid #ff9f43;
}

.exam-info-row {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 10px;
  font-size: 15px;
  color: #606266;
  padding: 4px 0;
}

.exam-info-row .label {
  color: #909399;
  font-size: 15px;
  min-width: 76px;
  line-height: 22px;
}

.exam-info-row .value {
  font-weight: 600;
  color: #303133;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
  line-height: 22px;
  flex-wrap: wrap;
}

.inline-icon {
  color: #909399;
}

.submit-btn {
  margin-top: 12px;
  width: 100%;
}

.exam-content {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  border: 1px solid #ebeef5;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
}

.question-header {
  margin-bottom: 24px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 16px;
}

.question-title {
  font-size: 19px;
  font-weight: 700;
  color: #303133;
}

.section-prefix {
  margin-right: 4px;
}

.section-extra {
  margin-left: 12px;
  font-size: 14px;
  color: #909399;
  font-weight: 400;
}

.question-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.question-index {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 18px;
  font-size: 18px;
}

.q-no {
  font-weight: 700;
  color: #303133;
}

.q-meta {
  font-size: 14px;
  font-weight: 500;
  color: #909399;
}

.question-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
}

.question-footer .spacer {
  flex: 1;
}

.exam-nav {
  width: 280px;
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #ebeef5;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
}

.nav-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.nav-title {
  font-size: 17px;
  font-weight: 700;
  color: #303133;
}

.nav-legend {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.nav-groups {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.nav-group + .nav-group {
  margin-top: 12px;
}

.nav-group-title {
  font-size: 15px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 12px;
}

.nav-group-extra {
  margin-left: 6px;
  font-size: 13px;
  color: #909399;
  font-weight: 400;
}

.nav-question-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.nav-question {
  min-width: 36px;
  height: 36px;
  padding: 0;
  font-size: 14px;
  font-weight: 500;
}

.question-footer :deep(.el-button) {
  border-radius: 10px;
  border: 1px solid #ebeef5;
}

.nav-question-list :deep(.el-button) {
  border-radius: 10px;
  border: 1px solid #ebeef5;
  background: #fff;
  color: #303133;
}

.nav-question-list :deep(.el-button--primary) {
  background: #409eff;
  border-color: #409eff;
  color: #ffffff;
}

.nav-question-list :deep(.el-button--success) {
  background: #ecf5ff;
  border-color: #b3d8ff;
  color: #303133;
}

.nav-question-list :deep(.el-button--info) {
  background: #ffffff;
  border-color: #dcdfe6;
  color: #909399;
}

.nav-legend :deep(.el-tag) {
  border-radius: 999px;
  border-width: 1px;
}

.nav-legend :deep(.el-tag--primary) {
  background: #409eff;
  border-color: #409eff;
  color: #ffffff;
}

.nav-legend :deep(.el-tag--success) {
  background: #ecf5ff;
  border-color: #b3d8ff;
  color: #303133;
}

.nav-legend :deep(.el-tag--info) {
  background: #f4f4f5;
  border-color: #e9e9eb;
  color: #909399;
}

.exam-preview-dialog :deep(.el-dialog__body) {
  padding-top: 0;
}

.preview-scroll {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 8px;
}

.preview-question {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.preview-question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
