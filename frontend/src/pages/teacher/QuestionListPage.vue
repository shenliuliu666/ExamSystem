<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as teacherApi from '../../api/teacher'
import { pickErrorMessage } from '../../api/http'
import PaginationBar from '../../components/PaginationBar.vue'
import type { PagedResult, QuestionResponse, QuestionBank, QuestionBankMember } from '../../types/api'

const router = useRouter()

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const items = ref<QuestionResponse[]>([])
const banks = ref<QuestionBank[]>([])
const importing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const exporting = ref(false)

const filters = reactive({
  bankId: '',
  type: '',
  keyword: '',
  enabled: '',
})

const bankDialogVisible = ref(false)
const bankSaving = ref(false)
const bankForm = reactive({
  name: '',
  visibility: 'PRIVATE',
})

const memberDialogVisible = ref(false)
const memberLoading = ref(false)
const memberSaving = ref(false)
const members = ref<QuestionBankMember[]>([])
const memberForm = reactive({
  username: '',
  role: 'EDITOR',
})

async function openCreate() {
  await router.push('/teacher/questions/create')
}

async function openEdit(row: QuestionResponse) {
  await router.push(`/teacher/questions/${row.id}/edit`)
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
    const bankId = filters.bankId ? Number(filters.bankId) : undefined
    const res = await teacherApi.importQuestionsExcel({ file, bankId })
    page.value = 1
    await Promise.all([loadBanks(), load()])
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

function exportFilename(prefix: string, ext: string) {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  return `${prefix}_${ts}.${ext}`
}

async function exportExcel() {
  exporting.value = true
  try {
    const params: any = {}
    if (filters.bankId) params.bankId = Number(filters.bankId)
    if (filters.type) params.type = filters.type
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.enabled) params.enabled = filters.enabled === 'true'
    const blob = await teacherApi.exportQuestionsExcel(params)
    downloadBlob(blob, exportFilename('题库导出', 'xlsx'))
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    exporting.value = false
  }
}

function typeLabel(type: string) {
  if (type === 'SINGLE_CHOICE') return '单选'
  if (type === 'MULTIPLE_CHOICE') return '多选'
  if (type === 'TRUE_FALSE') return '判断'
  return type
}

function difficultyLabel(raw: unknown) {
  const v = String(raw ?? '').trim()
  if (!v) return '-'
  const n = Number(v)
  if (!Number.isFinite(n)) return v
  const level = n >= 0.8 ? '易' : n >= 0.3 ? '中' : '难'
  return `${v} (${level})`
}

async function loadBanks() {
  try {
    banks.value = await teacherApi.listQuestionBanks()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  }
}

async function load() {
  loading.value = true
  try {
    const params: any = {
      page: page.value,
      size: size.value,
    }
    if (filters.bankId) params.bankId = Number(filters.bankId)
    if (filters.type) params.type = filters.type
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.enabled) params.enabled = filters.enabled === 'true'

    const res: PagedResult<QuestionResponse> = await teacherApi.listQuestions(params)
    items.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function remove(row: QuestionResponse) {
  try {
    await ElMessageBox.confirm(`确认删除题目 #${row.id}？`, '提示', { type: 'warning' })
    await teacherApi.deleteQuestion(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e === 'cancel' || e?.name === 'ElMessageBoxError') return
    ElMessage.error(pickErrorMessage(e))
  }
}

async function saveBank() {
  if (!bankForm.name.trim()) {
    ElMessage.error('请填写题库名称')
    return
  }
  bankSaving.value = true
  try {
    await teacherApi.createQuestionBank({
      name: bankForm.name.trim(),
      visibility: bankForm.visibility,
    })
    ElMessage.success('已创建题库')
    bankDialogVisible.value = false
    await loadBanks()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    bankSaving.value = false
  }
}

async function loadMembers() {
  if (!filters.bankId) return
  memberLoading.value = true
  try {
    members.value = await teacherApi.listQuestionBankMembers(Number(filters.bankId))
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    memberLoading.value = false
  }
}

async function addMember() {
  if (!filters.bankId) return
  if (!memberForm.username.trim()) {
    ElMessage.error('请填写成员用户名')
    return
  }
  memberSaving.value = true
  try {
    await teacherApi.addQuestionBankMember(Number(filters.bankId), {
      username: memberForm.username.trim(),
      role: memberForm.role,
    })
    ElMessage.success('已添加成员')
    memberForm.username = ''
    memberForm.role = 'EDITOR'
    await loadMembers()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    memberSaving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadBanks(), load()])
})
</script>

<template>
  <input ref="importInput" type="file" accept=".xlsx,.xls" style="display: none" @change="onImportFileChange" />
  <div class="page">
    <el-card class="card">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="font-size: 16px; font-weight: 700">题库管理</div>
        <div style="display: flex; gap: 8px">
          <el-button type="primary" @click="openCreate">新增题目</el-button>
          <el-button plain :loading="importing" @click="onSmartImport">智能导入</el-button>
          <el-button plain :loading="exporting" @click="exportExcel">导出Excel</el-button>
          <el-button plain :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px">
        <el-select v-model="filters.bankId" placeholder="题库" style="width: 200px" clearable>
          <el-option v-for="b in banks" :key="b.id" :label="b.name" :value="String(b.id)" />
        </el-select>
        <el-select v-model="filters.type" placeholder="题型" style="width: 160px" clearable>
          <el-option label="单选" value="SINGLE_CHOICE" />
          <el-option label="多选" value="MULTIPLE_CHOICE" />
          <el-option label="判断" value="TRUE_FALSE" />
        </el-select>
        <el-select v-model="filters.enabled" placeholder="启用" style="width: 160px" clearable>
          <el-option label="启用" value="true" />
          <el-option label="停用" value="false" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="关键词" style="width: 240px" clearable />
        <el-button type="primary" plain @click="page = 1; load()">查询</el-button>
      </div>

      <el-table :data="items" v-loading="loading" style="width: 100%">
        <el-table-column
          type="index"
          label="序号"
          width="80"
          :index="(i: number) => (page - 1) * size + i + 1"
        />
        <el-table-column label="题型" width="140">
          <template #default="{ row }">
            {{ typeLabel(row.type) }}
          </template>
        </el-table-column>
        <el-table-column prop="stem" label="题干" min-width="260" />
        <el-table-column label="难度" width="140">
          <template #default="{ row }">
            {{ difficultyLabel(row.difficulty) }}
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="100" />
        <el-table-column prop="enabled" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button type="primary" size="small" plain @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 12px">
        <PaginationBar v-model:page="page" v-model:size="size" :total="total" @change="load" />
      </div>

      <el-dialog v-model="bankDialogVisible" title="新建题库" width="420px">
        <el-form label-width="80px">
          <el-form-item label="名称">
            <el-input v-model="bankForm.name" placeholder="例如：章节一题库" />
          </el-form-item>
          <el-form-item label="可见性">
            <el-select v-model="bankForm.visibility" style="width: 200px">
              <el-option label="私有" value="PRIVATE" />
              <el-option label="共享" value="SHARED" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="bankDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="bankSaving" @click="saveBank">确定</el-button>
          </span>
        </template>
      </el-dialog>

      <el-dialog v-model="memberDialogVisible" title="题库成员" width="520px">
        <div style="margin-bottom: 12px">
          <el-form inline>
            <el-form-item label="用户名">
              <el-input v-model="memberForm.username" placeholder="成员用户名" />
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="memberForm.role" style="width: 160px">
                <el-option label="编辑" value="EDITOR" />
                <el-option label="查看" value="VIEWER" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="memberSaving" @click="addMember">添加成员</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-table :data="members" v-loading="memberLoading" style="width: 100%">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="role" label="角色" width="120" />
          <el-table-column prop="joinedAt" label="加入时间" />
        </el-table>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="memberDialogVisible = false">关闭</el-button>
          </span>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
