<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import * as teacherApi from '../../api/teacher'
import { pickErrorMessage } from '../../api/http'
import type { CreateOrUpdateQuestionRequest, QuestionResponse, QuestionType } from '../../types/api'

const route = useRoute()
const router = useRouter()

const questionId = computed(() => {
  const raw = route.params.id
  if (!raw) return null
  const v = Number(raw)
  return Number.isFinite(v) ? v : null
})

const isEdit = computed(() => questionId.value !== null)

const loading = ref(false)
const saving = ref(false)
const loadedQuestion = ref<QuestionResponse | null>(null)

const formRef = ref<FormInstance>()
const form = reactive<CreateOrUpdateQuestionRequest>({
  bankId: undefined,
  type: 'SINGLE_CHOICE',
  stem: '',
  options: ['', '', '', ''],
  correctAnswer: 'A',
  analysis: '',
  score: 5,
  difficulty: '0.5',
  knowledgePoint: '',
  enabled: true,
  tags: [],
})

function normalizeOptions(list: unknown): string[] {
  if (!Array.isArray(list)) return []
  return list.map((x) => String(x)).map((x) => x.trim())
}

function normalizeForType(type: QuestionType) {
  if (type === 'SINGLE_CHOICE') {
    const opts = Array.isArray(form.options) ? [...form.options] : []
    const minLen = 4
    while (opts.length < minLen) {
      opts.push('')
    }
    form.options = opts
    const upper = String(form.correctAnswer ?? '').trim().toUpperCase()
    form.correctAnswer = upper && /^[A-Z]$/.test(upper) ? upper : 'A'
    return
  }
  if (type === 'MULTIPLE_CHOICE') {
    const opts = Array.isArray(form.options) ? [...form.options] : []
    const minLen = 4
    while (opts.length < minLen) {
      opts.push('')
    }
    form.options = opts
    const normalized = String(form.correctAnswer ?? '')
      .trim()
      .split(',')
      .map((x) => x.trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    const unique = Array.from(new Set(normalized)).sort()
    form.correctAnswer = unique.length ? unique.join(',') : 'A'
    return
  }
  if (type === 'TRUE_FALSE') {
    form.options = []
    const v = String(form.correctAnswer ?? '').trim().toLowerCase()
    form.correctAnswer = v === 'false' ? 'false' : 'true'
  }
}

function resetFormForCreate() {
  form.bankId = undefined
  form.type = 'SINGLE_CHOICE'
  form.stem = ''
  form.options = ['', '', '', '']
  form.correctAnswer = 'A'
  form.analysis = ''
  form.score = 5
  form.difficulty = '0.5'
  form.knowledgePoint = ''
  form.enabled = true
  form.tags = []
  loadedQuestion.value = null
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

function normalizeDifficulty(raw: string | null | undefined): string {
  if (!raw) return ''
  const v = String(raw).trim()
  if (/^0\.[1-9]$/.test(v) || v === '1.0') return v
  if (v === 'easy') return '0.8'
  if (v === 'medium') return '0.5'
  if (v === 'hard') return '0.2'
  return v
}

const singleChoiceSelectedIndex = computed({
  get() {
    if (form.type !== 'SINGLE_CHOICE') return -1
    const upper = String(form.correctAnswer ?? '').trim().toUpperCase()
    if (!upper || !/^[A-Z]$/.test(upper)) return -1
    const idx = upper.charCodeAt(0) - 65
    if (!Array.isArray(form.options) || idx < 0 || idx >= form.options.length) return -1
    return idx
  },
  set(idx: number) {
    if (form.type !== 'SINGLE_CHOICE') return
    if (!Array.isArray(form.options) || idx < 0 || idx >= form.options.length) return
    form.correctAnswer = String.fromCharCode(65 + idx)
  },
})

const multipleChoiceSelectedLetters = computed<string[]>({
  get() {
    if (form.type !== 'MULTIPLE_CHOICE') return []
    const normalized = String(form.correctAnswer ?? '')
      .trim()
      .split(',')
      .map((x) => x.trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    return Array.from(new Set(normalized)).sort()
  },
  set(v: string[]) {
    if (form.type !== 'MULTIPLE_CHOICE') return
    const normalized = (Array.isArray(v) ? v : [])
      .map((x) => String(x).trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    form.correctAnswer = Array.from(new Set(normalized)).sort().join(',')
  },
})

watch(
  () => form.type,
  (t) => {
    normalizeForType(t)
  },
)

const rules: FormRules<CreateOrUpdateQuestionRequest> = {
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  stem: [{ required: true, message: '请填写题干', trigger: 'blur' }],
  score: [
    { required: true, message: '请填写分值', trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        const n = Number(value)
        if (!Number.isFinite(n) || n <= 0) return callback(new Error('分值必须 > 0'))
        callback()
      },
      trigger: 'change',
    },
  ],
}

function validateCorrectAnswer(): boolean {
  const type = form.type
  if (type === 'SINGLE_CHOICE') {
    const upper = String(form.correctAnswer ?? '').trim().toUpperCase()
    const opts = normalizeOptions(form.options)
    if (!upper) {
      ElMessage.error('请为单选题选择一个正确答案')
      return false
    }
    if (!/^[A-Z]$/.test(upper)) {
      ElMessage.error('单选题正确答案必须为 A-Z')
      return false
    }
    const idx = upper.charCodeAt(0) - 65
    if (idx < 0 || idx >= opts.length) {
      ElMessage.error('正确答案超出选项范围')
      return false
    }
    return true
  }
  if (type === 'MULTIPLE_CHOICE') {
    const opts = normalizeOptions(form.options)
    const selected = String(form.correctAnswer ?? '')
      .trim()
      .split(',')
      .map((x) => x.trim().toUpperCase())
      .filter((x) => /^[A-Z]$/.test(x))
    const unique = Array.from(new Set(selected)).sort()
    if (!unique.length) {
      ElMessage.error('请为多选题选择至少一个正确答案')
      return false
    }
    for (const upper of unique) {
      const idx = upper.charCodeAt(0) - 65
      if (idx < 0 || idx >= opts.length) {
        ElMessage.error('正确答案超出选项范围')
        return false
      }
    }
    form.correctAnswer = unique.join(',')
    return true
  }
  if (type === 'TRUE_FALSE') {
    const v = String(form.correctAnswer ?? '').trim().toLowerCase()
    if (v !== 'true' && v !== 'false') {
      ElMessage.error('判断题正确答案必须为 true/false')
      return false
    }
    return true
  }
  ElMessage.error('未知题型')
  return false
}

async function load() {
  if (!questionId.value) return
  loading.value = true
  try {
    const q = await teacherApi.getQuestion(questionId.value)
    loadedQuestion.value = q
    form.bankId = q.bankId
    form.type = q.type
    form.stem = q.stem
    form.options = q.type === 'SINGLE_CHOICE' || q.type === 'MULTIPLE_CHOICE' ? normalizeOptions(q.options) : []
    form.correctAnswer = q.correctAnswer
    form.analysis = q.analysis ?? ''
    form.score = q.score
    form.difficulty = normalizeDifficulty(q.difficulty) || '0.5'
    form.knowledgePoint = q.knowledgePoint ?? ''
    form.enabled = q.enabled
    form.tags = Array.isArray(q.tags) ? [...q.tags] : []
    normalizeForType(q.type)
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

function addOption() {
  if (form.type !== 'SINGLE_CHOICE' && form.type !== 'MULTIPLE_CHOICE') return
  const next = Array.isArray(form.options) ? [...form.options] : []
  next.push('')
  form.options = next
}

function removeOption(idx: number) {
  if (form.type !== 'SINGLE_CHOICE' && form.type !== 'MULTIPLE_CHOICE') return
  const next = (Array.isArray(form.options) ? [...form.options] : []).filter((_, i) => i !== idx)
  form.options = next
  normalizeForType(form.type)
}

function updateOption(idx: number, value: unknown) {
  if (form.type !== 'SINGLE_CHOICE' && form.type !== 'MULTIPLE_CHOICE') return
  const next = Array.isArray(form.options) ? [...form.options] : []
  next[idx] = String(value)
  form.options = next
}

async function save(mode: 'stay' | 'continue' = 'stay') {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!validateCorrectAnswer()) return

  saving.value = true
  try {
    const payload: CreateOrUpdateQuestionRequest = {
      ...form,
      bankId: form.bankId ? Number(form.bankId) : undefined,
      tags: Array.isArray(form.tags) ? form.tags.map((x) => String(x).trim()).filter((x) => x) : [],
      stem: form.stem.trim(),
      options: form.type === 'SINGLE_CHOICE' || form.type === 'MULTIPLE_CHOICE' ? normalizeOptions(form.options) : [],
      correctAnswer:
        form.type === 'TRUE_FALSE'
          ? String(form.correctAnswer).trim().toLowerCase()
          : String(form.correctAnswer)
              .trim()
              .split(',')
              .map((x) => x.trim().toUpperCase())
              .filter((x) => /^[A-Z]$/.test(x))
              .filter((x, i, arr) => arr.indexOf(x) === i)
              .sort()
              .join(','),
    }

    if (questionId.value) {
      await teacherApi.updateQuestion(questionId.value, payload)
      ElMessage.success('已保存')
      await load()
    } else {
      await teacherApi.createQuestion(payload)
      if (mode === 'continue') {
        ElMessage.success('已保存，继续出题')
        resetFormForCreate()
        return
      }
      ElMessage.success('已创建')
    }
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    saving.value = false
  }
}

async function back() {
  await router.replace('/teacher/questions')
}

onMounted(async () => {
  if (questionId.value) {
    await load()
    return
  }
  const t = route.query.type
  if (t === 'SINGLE_CHOICE' || t === 'MULTIPLE_CHOICE' || t === 'TRUE_FALSE') {
    form.type = t
    normalizeForType(t)
  }
})
</script>

<template>
  <div class="page">
    <el-card class="card" v-loading="loading">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <div style="font-size: 16px; font-weight: 700">{{ isEdit ? '编辑题目' : '新增题目' }}</div>
          <el-text v-if="loadedQuestion" type="info">#{{ loadedQuestion.id }}（最后更新：{{ loadedQuestion.updatedAt }}）</el-text>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button plain @click="back">返回</el-button>
          <el-button
            v-if="!isEdit"
            type="primary"
            plain
            :loading="saving"
            @click="save('continue')"
          >
            继续出题
          </el-button>
          <el-button type="primary" :loading="saving" @click="save()">保存</el-button>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" style="max-width: 720px">
        <el-form-item label="题型" prop="type">
          <el-select v-model="form.type" style="width: 200px">
            <el-option label="单选" value="SINGLE_CHOICE" />
            <el-option label="多选" value="MULTIPLE_CHOICE" />
            <el-option label="判断" value="TRUE_FALSE" />
          </el-select>
        </el-form-item>

        <el-form-item label="题干" prop="stem">
          <el-input v-model="form.stem" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item v-if="form.type === 'SINGLE_CHOICE' || form.type === 'MULTIPLE_CHOICE'" label="选项">
          <div style="display: grid; gap: 8px; width: 100%">
            <template v-if="form.type === 'SINGLE_CHOICE'">
              <div
                v-for="(o, idx) in form.options"
                :key="idx"
                style="display: grid; grid-template-columns: 40px 1fr auto; align-items: center; gap: 8px"
              >
                <button
                  type="button"
                  :style="{
                    width: '32px',
                    height: '32px',
                    borderRadius: '16px',
                    border: idx === singleChoiceSelectedIndex ? '0' : '1px solid #dcdfe6',
                    backgroundColor: idx === singleChoiceSelectedIndex ? '#4c6fff' : '#ffffff',
                    color: idx === singleChoiceSelectedIndex ? '#ffffff' : '#606266',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    padding: 0,
                    fontWeight: 500,
                  }"
                  @click="singleChoiceSelectedIndex = idx"
                >
                  {{ String.fromCharCode(65 + idx) }}
                </button>
                <el-input
                  :model-value="o"
                  :placeholder="`选项 ${String.fromCharCode(65 + idx)}`"
                  @update:model-value="updateOption(idx, $event)"
                />
                <el-button type="danger" plain :disabled="(form.options?.length ?? 0) <= 2" @click="removeOption(idx)">
                  删除
                </el-button>
              </div>
            </template>
            <el-checkbox-group v-else v-model="multipleChoiceSelectedLetters" style="display: grid; gap: 8px">
              <div
                v-for="(o, idx) in form.options"
                :key="idx"
                style="display: grid; grid-template-columns: 50px 1fr auto; align-items: center; gap: 8px"
              >
                <el-checkbox :value="String.fromCharCode(65 + idx)">
                  {{ String.fromCharCode(65 + idx) }}
                </el-checkbox>
                <el-input
                  :model-value="o"
                  :placeholder="`选项 ${String.fromCharCode(65 + idx)}`"
                  @update:model-value="updateOption(idx, $event)"
                />
                <el-button type="danger" plain :disabled="(form.options?.length ?? 0) <= 2" @click="removeOption(idx)">
                  删除
                </el-button>
              </div>
            </el-checkbox-group>
            <el-button type="primary" text @click="addOption">添加选项</el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="form.type === 'TRUE_FALSE'" label="正确答案">
          <el-radio-group v-model="form.correctAnswer">
            <el-radio value="true">正确</el-radio>
            <el-radio value="false">错误</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="分值" prop="score">
          <el-input-number v-model="form.score" :min="1" :max="200" />
        </el-form-item>

        <el-form-item label="难度">
          <el-select v-model="form.difficulty" style="width: 240px" placeholder="请选择难度" clearable>
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

        <el-form-item label="知识点">
          <el-input v-model="form.knowledgePoint" />
        </el-form-item>

        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入后回车添加标签"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
