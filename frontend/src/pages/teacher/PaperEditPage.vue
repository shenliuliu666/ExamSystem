<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import PaginationBar from '../../components/PaginationBar.vue'
import { formatDateTime } from '../../utils/time'
import type { CreateOrUpdateQuestionRequest, PaperResponse, QuestionResponse, QuestionType } from '../../types/api'

const route = useRoute()
const router = useRouter()

const PAPER_ONLY_TAG = '__paper_only__'

const paperId = computed(() => {
  const raw = route.params.id
  if (!raw) return null
  const v = Number(raw)
  return Number.isFinite(v) ? v : null
})

const isEdit = computed(() => paperId.value !== null)

const saving = ref(false)
const loadingPaper = ref(false)
const updatedAt = ref<string | null>(null)
const importing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const exporting = ref(false)

const form = reactive({
  name: '',
})

const selected = ref<QuestionResponse[]>([])
const activeQuestionIndex = ref<number | null>(null)

const totalScore = computed(() => {
  return selected.value.reduce((sum, q) => sum + (q.score ?? 0), 0)
})

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function exportFilename(base: string, ext: string) {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  const safe = String(base || '').trim().replace(/[\\/:*?"<>|\r\n\t]+/g, '_')
  const prefix = safe || '试卷'
  return `${prefix}_${ts}.${ext}`
}

async function exportPaper(format: 'xlsx' | 'docx') {
  if (!paperId.value) {
    ElMessage.error('请先保存试卷后再导出')
    return
  }
  exporting.value = true
  try {
    const blob = format === 'xlsx' ? await teacherApi.exportPaperExcel(paperId.value) : await teacherApi.exportPaperWord(paperId.value)
    downloadBlob(blob, exportFilename(form.name, format))
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    exporting.value = false
  }
}

function parseDifficulty(raw: unknown) {
  const v = String(raw ?? '').trim()
  if (!v) return 0.5
  const n = Number(v)
  if (!Number.isFinite(n)) return 0.5
  return n
}

const paperDifficulty = computed(() => {
  const total = totalScore.value
  if (!Number.isFinite(total) || total <= 0) return null
  const weighted = selected.value.reduce((sum, q) => {
    const score = Number(q.score ?? 0)
    if (!Number.isFinite(score) || score <= 0) return sum
    return sum + score * parseDifficulty(q.difficulty)
  }, 0)
  return weighted / total
})

function formatDifficulty(v: number | null | undefined) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return n.toFixed(2)
}

const questionLoading = ref(false)
const qPage = ref(1)
const qSize = ref(10)
const qTotal = ref(0)
const qItems = ref<QuestionResponse[]>([])
const qFilters = reactive({
  bankId: '',
  type: '' as '' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE',
  enabled: '' as '' | 'true' | 'false',
  keyword: '',
})
const selectDialogVisible = ref(false)
const bankSelection = ref<QuestionResponse[]>([])

const activeQuestion = computed(() => {
  if (activeQuestionIndex.value === null) return null
  return selected.value[activeQuestionIndex.value] ?? null
})

const activeSingleChoiceSelectedIndex = computed(() => {
  const q = activeQuestion.value
  if (!q || q.type !== 'SINGLE_CHOICE') return -1
  const upper = String(q.correctAnswer ?? '').trim().toUpperCase()
  if (!upper || !/^[A-Z]$/.test(upper)) return -1
  const idx = upper.charCodeAt(0) - 65
  if (!Array.isArray(q.options) || idx < 0 || idx >= q.options.length) return -1
  return idx
})

const activeMultipleChoiceSelectedLetters = computed<string[]>({
  get() {
    const q = activeQuestion.value
    if (!q || q.type !== 'MULTIPLE_CHOICE') return []
    const normalized = String(q.correctAnswer ?? '')
      .trim()
      .split(',')
      .map((x) => x.trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    return Array.from(new Set(normalized)).sort()
  },
  set(v) {
    const q = activeQuestion.value
    if (!q || q.type !== 'MULTIPLE_CHOICE') return
    const normalized = (Array.isArray(v) ? v : [])
      .map((x) => String(x).trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    q.correctAnswer = Array.from(new Set(normalized)).sort().join(',')
  },
})

const typeOrder: QuestionType[] = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE']

const typeLabelMap: Record<QuestionType, string> = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
}

const groupedQuestions = computed(() => {
  return typeOrder
    .map((t) => {
      const questions = selected.value
        .map((q, idx) => ({ q, idx }))
        .filter((x) => x.q.type === t)
      if (!questions.length) return null
      const totalScore = questions.reduce((sum, x) => sum + (x.q.score ?? 0), 0)
      return {
        type: t,
        label: typeLabelMap[t],
        totalScore,
        items: questions.map((x, i) => ({
          index: x.idx,
          displayNo: i + 1,
          score: x.q.score ?? 0,
        })),
      }
    })
    .filter((x): x is { type: QuestionType; label: string; totalScore: number; items: { index: number; displayNo: number; score: number }[] } => x !== null)
})

function toChineseOrder(n: number): string {
  const map = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
  if (n >= 1 && n <= map.length) {
    const v = map[n - 1]
    if (v !== undefined) return v
  }
  return String(n)
}

function normalizeQuestionParams() {
  const params: any = {
    page: qPage.value,
    size: qSize.value,
  }
  if (qFilters.bankId) params.bankId = Number(qFilters.bankId)
  if (qFilters.type) params.type = qFilters.type
  if (qFilters.keyword) params.keyword = qFilters.keyword
  if (qFilters.enabled) params.enabled = qFilters.enabled === 'true'
  return params
}

async function loadQuestions() {
  questionLoading.value = true
  try {
    const res = await teacherApi.listQuestions(normalizeQuestionParams())
    qItems.value = res.items
    qTotal.value = res.total
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    questionLoading.value = false
  }
}

function openSelectDialog(type?: '' | 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE') {
  if (typeof type !== 'undefined') {
    qFilters.type = type
  }
  selectDialogVisible.value = true
  qPage.value = 1
  loadQuestions()
}

function selectQuestion(idx: number) {
  activeQuestionIndex.value = idx
}

function onSmartImport() {
  importInput.value?.click()
}

async function onImportFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  importing.value = true
  try {
    const res = await teacherApi.importQuestionsExcel({ file, tags: PAPER_ONLY_TAG })
    const toAdd = (res.questions ?? []).map((q) => {
      const tags = Array.isArray(q.tags) ? q.tags : []
      return {
        ...q,
        tags: tags.includes(PAPER_ONLY_TAG) ? tags : [...tags, PAPER_ONLY_TAG],
      }
    })
    if (toAdd.length) {
      selected.value = [...selected.value, ...toAdd]
      activeQuestionIndex.value = selected.value.length - toAdd.length
    }
    if (res.failedCount) {
      const failures = (res.failures ?? []).slice(0, 50)
      const detail = failures.map((f) => `第${f.row}行：${f.reason}`).join('\n')
      await ElMessageBox.alert(detail || '存在导入失败的行', `导入完成：成功${res.successCount}，失败${res.failedCount}`, {
        confirmButtonText: '知道了',
      })
    } else {
      ElMessage.success(`导入成功：${res.successCount}`)
    }
  } catch (err) {
    ElMessage.error(pickErrorMessage(err))
  } finally {
    importing.value = false
  }
}

function createQuestionByType(type: QuestionType) {
  const now = Date.now()
  const q: QuestionResponse = {
    id: -now,
    bankId: undefined,
    type,
    stem: '',
    options: type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE' ? ['', '', '', ''] : [],
    tags: [],
    correctAnswer: type === 'TRUE_FALSE' ? 'true' : 'A',
    analysis: '',
    score: 5,
    difficulty: '0.5',
    knowledgePoint: '',
    enabled: true,
    createdAt: '',
    updatedAt: '',
  }
  selected.value = [...selected.value, q]
  activeQuestionIndex.value = selected.value.length - 1
}

function onBankSelectionChange(rows: QuestionResponse[]) {
  bankSelection.value = rows
}

function addSelectedFromBank() {
  if (!bankSelection.value.length) {
    ElMessage.error('请至少选择 1 道题目')
    return
  }
  const exists = new Set(selected.value.map((q) => q.id))
  const toAdd = bankSelection.value.filter((q) => !exists.has(q.id))
  if (!toAdd.length) {
    ElMessage.warning('所选题目已全部在试卷中')
    return
  }
  selected.value = [...selected.value, ...toAdd]
  activeQuestionIndex.value = selected.value.length - 1
  ElMessage.success(`已添加 ${toAdd.length} 道题目`)
  selectDialogVisible.value = false
}

function removeAt(idx: number) {
  selected.value = selected.value.filter((_, i) => i !== idx)
  if (!selected.value.length) {
    activeQuestionIndex.value = null
    return
  }
  if (activeQuestionIndex.value !== null) {
    if (idx < activeQuestionIndex.value) {
      activeQuestionIndex.value -= 1
    } else if (idx === activeQuestionIndex.value) {
      activeQuestionIndex.value = Math.min(idx, selected.value.length - 1)
    }
  }
}

function ensureActiveChoice() {
  const q = activeQuestion.value
  if (!q || (q.type !== 'SINGLE_CHOICE' && q.type !== 'MULTIPLE_CHOICE')) return null
  if (!Array.isArray(q.options)) q.options = []
  const minLen = 4
  while (q.options.length < minLen) {
    q.options.push('')
  }
  if (q.type === 'SINGLE_CHOICE') {
    const upper = String(q.correctAnswer ?? '').trim().toUpperCase()
    if (!upper || !/^[A-Z]$/.test(upper)) {
      q.correctAnswer = 'A'
    }
  } else {
    normalizeMultipleChoiceCorrectAnswer(q)
  }
  return q
}

function normalizeMultipleChoiceCorrectAnswer(q: QuestionResponse) {
  const maxIndex = Array.isArray(q.options) ? q.options.length - 1 : -1
  const normalized = String(q.correctAnswer ?? '')
    .trim()
    .split(',')
    .map((x) => x.trim().toUpperCase())
    .filter((x) => /^[A-Z]$/.test(x))
    .filter((x) => x.charCodeAt(0) - 65 >= 0 && x.charCodeAt(0) - 65 <= maxIndex)
  const unique = Array.from(new Set(normalized)).sort()
  q.correctAnswer = unique.length ? unique.join(',') : 'A'
}

function updateActiveStem(value: string) {
  const q = activeQuestion.value
  if (!q) return
  q.stem = value
}

function addActiveOption() {
  const q = ensureActiveChoice()
  if (!q) return
  q.options.push('')
}

function removeActiveOption(idx: number) {
  const q = ensureActiveChoice()
  if (!q) return
  if (q.options.length <= 2) return
  q.options.splice(idx, 1)
  if (q.type === 'SINGLE_CHOICE') {
    const upper = String(q.correctAnswer ?? '').trim().toUpperCase()
    if (!upper || !/^[A-Z]$/.test(upper)) return
    const ansIdx = upper.charCodeAt(0) - 65
    if (ansIdx >= q.options.length) {
      const nextIdx = Math.max(q.options.length - 1, 0)
      q.correctAnswer = String.fromCharCode(65 + nextIdx)
    }
    return
  }
  if (q.type === 'MULTIPLE_CHOICE') {
    normalizeMultipleChoiceCorrectAnswer(q)
  }
}

function updateActiveOption(idx: number, value: string) {
  const q = ensureActiveChoice()
  if (!q) return
  if (idx < 0 || idx >= q.options.length) return
  q.options[idx] = value
}

function selectActiveCorrectIndex(idx: number) {
  const q = ensureActiveChoice()
  if (!q) return
  if (q.type !== 'SINGLE_CHOICE') return
  if (idx < 0 || idx >= q.options.length) return
  q.correctAnswer = String.fromCharCode(65 + idx)
}

function updateActiveTrueFalseAnswer(value: string) {
  const q = activeQuestion.value
  if (!q || q.type !== 'TRUE_FALSE') return
  const v = String(value ?? '').trim().toLowerCase()
  q.correctAnswer = v === 'false' ? 'false' : 'true'
}

function updateActiveAnalysis(value: string) {
  const q = activeQuestion.value
  if (!q) return
  q.analysis = value
}

function updateActiveDifficulty(value: string) {
  const q = activeQuestion.value
  if (!q) return
  q.difficulty = String(value ?? '').trim()
}

function updateActiveScore(value: number) {
  const q = activeQuestion.value
  if (!q) return
  const n = Number(value)
  if (!Number.isFinite(n) || n <= 0) return
  q.score = n
}

function buildQuestionPayload(q: QuestionResponse): CreateOrUpdateQuestionRequest {
  const stem = String(q.stem ?? '').trim()
  const analysis = String(q.analysis ?? '')
  const difficulty = String(q.difficulty ?? '').trim()
  const knowledgePoint = String(q.knowledgePoint ?? '')
  const rawTags = Array.isArray(q.tags) ? q.tags.map((x) => String(x).trim()).filter((x) => x) : []
  const isNew = !q.id || q.id <= 0
  const hasPaperOnly = rawTags.includes(PAPER_ONLY_TAG)
  const tags = isNew || hasPaperOnly ? Array.from(new Set([...rawTags, PAPER_ONLY_TAG])) : rawTags

  if (q.type === 'SINGLE_CHOICE') {
    const options = Array.isArray(q.options) ? q.options.map((x) => String(x).trim()) : []
    const upper = String(q.correctAnswer ?? '').trim().toUpperCase()
    return {
      bankId: q.bankId,
      type: q.type,
      stem,
      options,
      correctAnswer: upper,
      analysis,
      score: q.score,
      difficulty,
      knowledgePoint,
      enabled: q.enabled,
      tags,
    }
  }

  if (q.type === 'MULTIPLE_CHOICE') {
    const options = Array.isArray(q.options) ? q.options.map((x) => String(x).trim()) : []
    const normalized = String(q.correctAnswer ?? '')
      .trim()
      .split(',')
      .map((x) => x.trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    const unique = Array.from(new Set(normalized)).sort()
    return {
      bankId: q.bankId,
      type: q.type,
      stem,
      options,
      correctAnswer: unique.join(','),
      analysis,
      score: q.score,
      difficulty,
      knowledgePoint,
      enabled: q.enabled,
      tags,
    }
  }

  const v = String(q.correctAnswer ?? '').trim().toLowerCase()
  return {
    bankId: q.bankId,
    type: q.type,
    stem,
    options: [],
    correctAnswer: v === 'false' ? 'false' : 'true',
    analysis,
    score: q.score,
    difficulty,
    knowledgePoint,
    enabled: q.enabled,
    tags,
  }
}

function validateQuestion(q: QuestionResponse): boolean {
  const stem = String(q.stem ?? '').trim()
  if (!stem) {
    ElMessage.error('请填写每道题目的题干')
    return false
  }
  if (q.type === 'SINGLE_CHOICE') {
    const options = Array.isArray(q.options) ? q.options : []
    const upper = String(q.correctAnswer ?? '').trim().toUpperCase()
    if (!upper) {
      ElMessage.error('请为每道单选题选择一个正确答案')
      return false
    }
    if (!/^[A-Z]$/.test(upper)) {
      ElMessage.error('单选题正确答案必须为 A-Z')
      return false
    }
    const idx = upper.charCodeAt(0) - 65
    if (idx < 0 || idx >= options.length) {
      ElMessage.error('单选题正确答案超出选项范围')
      return false
    }
    return true
  }
  if (q.type === 'MULTIPLE_CHOICE') {
    const options = Array.isArray(q.options) ? q.options : []
    const selected = String(q.correctAnswer ?? '')
      .trim()
      .split(',')
      .map((x) => x.trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    const unique = Array.from(new Set(selected)).sort()
    if (!unique.length) {
      ElMessage.error('请为每道多选题选择至少一个正确答案')
      return false
    }
    for (const upper of unique) {
      const idx = upper.charCodeAt(0) - 65
      if (idx < 0 || idx >= options.length) {
        ElMessage.error('多选题正确答案超出选项范围')
        return false
      }
    }
    q.correctAnswer = unique.join(',')
    return true
  }
  if (q.type === 'TRUE_FALSE') {
    const v = String(q.correctAnswer ?? '').trim().toLowerCase()
    if (v !== 'true' && v !== 'false') {
      ElMessage.error('判断题正确答案必须为 true/false')
      return false
    }
    return true
  }
  ElMessage.error('存在未知题型')
  return false
}

async function saveCurrentQuestion() {
  const q = activeQuestion.value
  if (!q) return
  if (!validateQuestion(q)) return
  saving.value = true
  try {
    const payload = buildQuestionPayload(q)
    let saved: QuestionResponse
    if (!q.id || q.id <= 0) {
      saved = await teacherApi.createQuestion(payload)
    } else {
      saved = await teacherApi.updateQuestion(q.id, payload)
    }
    if (activeQuestionIndex.value !== null) {
      const idx = activeQuestionIndex.value
      const list = [...selected.value]
      list[idx] = saved
      selected.value = list
      activeQuestionIndex.value = idx
    }
    ElMessage.success('已保存当前题目')
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    saving.value = false
  }
}

async function loadPaper() {
  if (!paperId.value) return
  loadingPaper.value = true
  try {
    const paper: PaperResponse = await teacherApi.getPaper(paperId.value)
    form.name = paper.name
    updatedAt.value = paper.updatedAt
    const questions = await Promise.all(paper.questionIds.map((id) => teacherApi.getQuestion(id)))
    selected.value = questions
    activeQuestionIndex.value = selected.value.length ? 0 : null
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loadingPaper.value = false
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.error('请填写试卷名称')
    return
  }
  if (selected.value.length === 0) {
    ElMessage.error('请至少选择 1 道题目')
    return
  }

  for (const q of selected.value) {
    if (!validateQuestion(q)) return
  }

  saving.value = true
  try {
    const persisted: QuestionResponse[] = []
    for (const q of selected.value) {
      const payload = buildQuestionPayload(q)
      let saved: QuestionResponse
      if (!q.id || q.id <= 0) {
        saved = await teacherApi.createQuestion(payload)
      } else {
        saved = await teacherApi.updateQuestion(q.id, payload)
      }
      persisted.push(saved)
    }
    selected.value = persisted

    const payloadPaper = {
      name: form.name.trim(),
      questionIds: persisted.map((q) => q.id),
    }
    let paper: PaperResponse
    if (paperId.value) {
      paper = await teacherApi.updatePaper(paperId.value, payloadPaper)
      ElMessage.success('已保存')
    } else {
      paper = await teacherApi.createPaper(payloadPaper)
      ElMessage.success('已创建')
    }
    updatedAt.value = paper.updatedAt
    await router.replace('/teacher/papers')
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    saving.value = false
  }
}

async function back() {
  await router.replace('/teacher/papers')
}

const draggingIndex = ref<number | null>(null)

function onOutlineDragStart(idx: number) {
  draggingIndex.value = idx
}

function onOutlineDragOver(_idx: number, event: DragEvent) {
  event.preventDefault()
}

function onOutlineDrop(idx: number) {
  if (draggingIndex.value === null || draggingIndex.value === idx) {
    draggingIndex.value = null
    return
  }
  const list = [...selected.value]
  const removed = list.splice(draggingIndex.value, 1)
  if (!removed.length) {
    draggingIndex.value = null
    return
  }
  const moved = removed[0] as QuestionResponse
  list.splice(idx, 0, moved)
  selected.value = list
  activeQuestionIndex.value = idx
  draggingIndex.value = null
}

onMounted(async () => {
  await Promise.all([loadQuestions(), loadPaper()])
})
</script>

<template>
  <input ref="importInput" type="file" accept=".xlsx,.xls" style="display: none" @change="onImportFileChange" />
  <div class="page">
    <el-card class="card" v-loading="loadingPaper">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <div style="font-size: 16px; font-weight: 700">{{ isEdit ? '编辑试卷' : '新建试卷' }}</div>
          <el-text v-if="isEdit && updatedAt" type="info">最后更新：{{ formatDateTime(updatedAt) }}</el-text>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button plain @click="back">返回</el-button>
          <el-dropdown v-if="isEdit" @command="(cmd: any) => exportPaper(cmd)">
            <el-button plain :loading="exporting">导出</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="xlsx">导出Excel</el-dropdown-item>
                <el-dropdown-item command="docx">导出Word</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>

      <div style="display: grid; gap: 12px">
        <el-card>
          <template #header>
            <div style="font-weight: 700">试卷基础信息</div>
          </template>
          <el-form label-width="90px" style="max-width: 520px">
            <el-form-item label="试卷名称">
              <el-input v-model="form.name" placeholder="例如：第一章测验" />
            </el-form-item>
            <el-form-item label="题目数量">
              <el-text type="info">{{ selected.length }} 题</el-text>
            </el-form-item>
            <el-form-item label="总分">
              <el-text type="info">{{ totalScore }} 分</el-text>
            </el-form-item>
            <el-form-item label="难度">
              <el-text type="info">{{ formatDifficulty(paperDifficulty) }}</el-text>
            </el-form-item>
          </el-form>
        </el-card>

        <div style="display: grid; gap: 12px; grid-template-columns: 260px 1fr">
          <el-card>
            <template #header>
              <div style="font-weight: 700">题目目录</div>
            </template>
            <div v-if="selected.length === 0" style="padding: 8px; color: #86909c">
              右侧通过“选题”添加试卷题目
            </div>
            <div v-else style="display: flex; flex-direction: column; gap: 10px">
              <div
                v-for="(group, gIndex) in groupedQuestions"
                :key="group.type"
                style="border-bottom: 1px solid #f2f3f5; padding-bottom: 6px"
              >
                <div style="font-weight: 700; font-size: 13px; margin-bottom: 4px">
                  {{ toChineseOrder(gIndex + 1) }}、{{ group.label }}（共{{ group.items.length }}题，{{
                    group.totalScore
                  }}
                  分）
                </div>
                <div style="display: flex; flex-direction: column; gap: 2px">
                  <div
                    v-for="item in group.items"
                    :key="item.index"
                    :draggable="true"
                    :style="{
                      padding: '4px 8px',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      border: activeQuestionIndex === item.index ? '1px solid #4c6fff' : '1px solid transparent',
                      backgroundColor: activeQuestionIndex === item.index ? '#f0f5ff' : '#ffffff',
                      fontSize: '13px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '8px',
                    }"
                    @click="selectQuestion(item.index)"
                    @dragstart="onOutlineDragStart(item.index)"
                    @dragover="onOutlineDragOver(item.index, $event)"
                    @drop="onOutlineDrop(item.index)"
                  >
                    <div>
                      <span style="margin-right: 4px">{{ item.displayNo }}</span>
                      <span style="color: #4c6fff">({{ item.score }}分)</span>
                    </div>
                    <el-button type="danger" text size="small" @click.stop="removeAt(item.index)">移除</el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-card>

          <div style="display: grid; gap: 12px">
            <el-card>
              <template #header>
                <div style="display: flex; align-items: center; justify-content: space-between">
                  <div style="font-weight: 700">添加题目</div>
                  <div style="font-size: 12px; color: #86909c">当前版本支持单选题、多选题与判断题</div>
                </div>
              </template>
              <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
                <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 8px">
                  <span style="font-size: 13px">添加题目：</span>
                  <el-button type="primary" plain @click="createQuestionByType('SINGLE_CHOICE')">
                    单选题
                  </el-button>
                  <el-button type="primary" plain @click="createQuestionByType('MULTIPLE_CHOICE')">
                    多选题
                  </el-button>
                  <el-button type="primary" plain @click="createQuestionByType('TRUE_FALSE')">
                    判断题
                  </el-button>
                </div>
                <div style="display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px">
                  <el-button plain :loading="importing" @click="onSmartImport">智能导入</el-button>
                  <el-button type="primary" plain @click="openSelectDialog()">选题</el-button>
                </div>
              </div>
            </el-card>

            <el-card>
              <template #header>
                <div style="display: flex; align-items: center; justify-content: space-between">
                  <div style="font-weight: 700">题目详情</div>
                  <div v-if="activeQuestion" style="font-size: 12px; color: #86909c">
                    ID：{{ activeQuestion.id }}，分值：{{ activeQuestion.score }}，难度：{{ activeQuestion.difficulty || '0.5' }}
                  </div>
                </div>
              </template>

              <div v-if="!activeQuestion" style="padding: 12px; color: #86909c">
                从左侧目录选择题目，或通过上方按钮选题添加
              </div>
              <div v-else style="display: grid; gap: 12px">
                <el-form label-width="80px" style="max-width: 720px">
                  <el-form-item label="题型">
                    <el-tag size="small" type="info">{{ typeLabelMap[activeQuestion.type] ?? activeQuestion.type }}</el-tag>
                  </el-form-item>

                  <el-form-item label="题干">
                    <el-input
                      :model-value="activeQuestion?.stem ?? ''"
                      type="textarea"
                      :rows="3"
                      @update:model-value="updateActiveStem"
                    />
                  </el-form-item>

                  <el-form-item
                    v-if="activeQuestion?.type === 'SINGLE_CHOICE' || activeQuestion?.type === 'MULTIPLE_CHOICE'"
                    label="选项"
                  >
                    <div style="display: grid; gap: 8px; width: 100%">
                      <template v-if="activeQuestion?.type === 'SINGLE_CHOICE'">
                        <div
                          v-for="(o, idx) in activeQuestion?.options"
                          :key="idx"
                          style="display: grid; grid-template-columns: 40px 1fr auto; align-items: center; gap: 8px"
                        >
                          <button
                            type="button"
                            :style="{
                              width: '32px',
                              height: '32px',
                              borderRadius: '16px',
                              border: idx === activeSingleChoiceSelectedIndex ? '0' : '1px solid #dcdfe6',
                              backgroundColor: idx === activeSingleChoiceSelectedIndex ? '#4c6fff' : '#ffffff',
                              color: idx === activeSingleChoiceSelectedIndex ? '#ffffff' : '#606266',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              cursor: 'pointer',
                              padding: 0,
                              fontWeight: 500,
                            }"
                            @click="selectActiveCorrectIndex(idx)"
                          >
                            {{ String.fromCharCode(65 + idx) }}
                          </button>
                          <el-input
                            :model-value="o"
                            :placeholder="`选项 ${String.fromCharCode(65 + idx)}`"
                            @update:model-value="updateActiveOption(idx, $event)"
                          />
                          <el-button
                            type="danger"
                            plain
                            :disabled="(activeQuestion?.options?.length ?? 0) <= 2"
                            @click="removeActiveOption(idx)"
                          >
                            删除
                          </el-button>
                        </div>
                      </template>
                      <el-checkbox-group v-else v-model="activeMultipleChoiceSelectedLetters" style="display: grid; gap: 8px">
                        <div
                          v-for="(o, idx) in activeQuestion?.options"
                          :key="idx"
                          style="display: grid; grid-template-columns: 50px 1fr auto; align-items: center; gap: 8px"
                        >
                          <el-checkbox :value="String.fromCharCode(65 + idx)">
                            {{ String.fromCharCode(65 + idx) }}
                          </el-checkbox>
                          <el-input
                            :model-value="o"
                            :placeholder="`选项 ${String.fromCharCode(65 + idx)}`"
                            @update:model-value="updateActiveOption(idx, $event)"
                          />
                          <el-button
                            type="danger"
                            plain
                            :disabled="(activeQuestion?.options?.length ?? 0) <= 2"
                            @click="removeActiveOption(idx)"
                          >
                            删除
                          </el-button>
                        </div>
                      </el-checkbox-group>
                      <el-button type="primary" text @click="addActiveOption">添加选项</el-button>
                    </div>
                  </el-form-item>

                  <el-form-item v-if="activeQuestion?.type === 'TRUE_FALSE'" label="正确答案">
                    <el-radio-group
                      :model-value="activeQuestion?.correctAnswer"
                      @update:model-value="updateActiveTrueFalseAnswer"
                    >
                      <el-radio label="true">正确</el-radio>
                      <el-radio label="false">错误</el-radio>
                    </el-radio-group>
                  </el-form-item>

                  <el-form-item label="解析">
                    <el-input
                      :model-value="activeQuestion?.analysis ?? ''"
                      type="textarea"
                      :rows="2"
                      @update:model-value="updateActiveAnalysis"
                    />
                  </el-form-item>

                  <el-form-item label="分值">
                    <el-input-number
                      :model-value="activeQuestion?.score ?? 0"
                      :min="1"
                      :max="200"
                      @update:model-value="updateActiveScore"
                    />
                  </el-form-item>

                  <el-form-item label="难度">
                    <el-select
                      :model-value="activeQuestion?.difficulty ?? '0.5'"
                      style="width: 240px"
                      placeholder="请选择难度"
                      clearable
                      @update:model-value="updateActiveDifficulty"
                    >
                      <el-option label="0.1 (难)" value="0.1" />
                      <el-option label="0.2 (难)" value="0.2" />
                      <el-option label="0.3 (中)" value="0.3" />
                      <el-option label="0.4 (中)" value="0.4" />
                      <el-option label="0.5 (中)" value="0.5" />
                      <el-option label="0.6 (中)" value="0.6" />
                      <el-option label="0.7 (中)" value="0.7" />
                      <el-option label="0.8 (易)" value="0.8" />
                      <el-option label="0.9 (易)" value="0.9" />
                      <el-option label="1.0 (易)" value="1.0" />
                    </el-select>
                  </el-form-item>
                </el-form>

                <div style="display: flex; justify-content: flex-end; gap: 8px">
                  <el-button type="primary" plain @click="saveCurrentQuestion">保存本题</el-button>
                  <el-button
                    type="danger"
                    plain
                    @click="activeQuestionIndex !== null && removeAt(activeQuestionIndex)"
                  >
                    删除本题
                  </el-button>
                </div>
              </div>
            </el-card>
          </div>
        </div>

        <el-dialog v-model="selectDialogVisible" title="从题库选择题目" width="820px">
          <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px">
            <el-select v-model="qFilters.type" placeholder="题型" style="width: 140px" clearable>
              <el-option label="单选" value="SINGLE_CHOICE" />
              <el-option label="多选" value="MULTIPLE_CHOICE" />
              <el-option label="判断" value="TRUE_FALSE" />
            </el-select>
            <el-select v-model="qFilters.enabled" placeholder="启用" style="width: 140px" clearable>
              <el-option label="启用" value="true" />
              <el-option label="停用" value="false" />
            </el-select>
            <el-input v-model="qFilters.keyword" placeholder="关键词" style="width: 200px" clearable />
            <el-button type="primary" plain @click="qPage = 1; loadQuestions()">查询</el-button>
          </div>

          <el-table
            :data="qItems"
            v-loading="questionLoading"
            style="width: 100%"
            @selection-change="onBankSelectionChange"
          >
            <el-table-column type="selection" width="50" />
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column label="题型" width="120">
              <template #default="{ row }">
                {{ typeLabelMap[row.type as QuestionType] ?? row.type }}
              </template>
            </el-table-column>
            <el-table-column prop="stem" label="题干" min-width="220" />
            <el-table-column prop="score" label="分值" width="80" />
          </el-table>

          <div style="display: flex; justify-content: flex-end; margin-top: 10px">
            <PaginationBar
              v-model:page="qPage"
              v-model:size="qSize"
              :total="qTotal"
              layout="total, prev, pager, next"
              :page-sizes="[10, 20, 50, 100]"
              @change="loadQuestions"
            />
          </div>

          <template #footer>
            <div style="display: flex; justify-content: space-between; width: 100%">
              <div style="font-size: 12px; color: #86909c">已选 {{ bankSelection.length }} 道题目</div>
              <div>
                <el-button @click="selectDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="addSelectedFromBank">添加到试卷</el-button>
              </div>
            </div>
          </template>
        </el-dialog>
      </div>
    </el-card>
  </div>
</template>
