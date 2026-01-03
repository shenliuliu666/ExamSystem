<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import PaginationBar from '../../components/PaginationBar.vue'
import { formatDateTime } from '../../utils/time'
import type { PaperResponse, PaperUsage } from '../../types/api'

const router = useRouter()

const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const items = ref<PaperResponse[]>([])
const exportingId = ref<number | null>(null)

function formatDifficulty(v: number | null | undefined) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '-'
  return n.toFixed(2)
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

function exportFilename(base: string, ext: string) {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  const safe = String(base || '').trim().replace(/[\\/:*?"<>|\r\n\t]+/g, '_')
  const prefix = safe || '试卷'
  return `${prefix}_${ts}.${ext}`
}

async function exportPaper(row: PaperResponse, format: 'xlsx' | 'docx') {
  exportingId.value = row.id
  try {
    const blob = format === 'xlsx' ? await teacherApi.exportPaperExcel(row.id) : await teacherApi.exportPaperWord(row.id)
    downloadBlob(blob, exportFilename(row.name, format))
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    exportingId.value = null
  }
}

async function load() {
  loading.value = true
  try {
    const res = await teacherApi.listPapers({
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    items.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  await router.push('/teacher/papers/create')
}

async function openEdit(row: PaperResponse) {
  await router.push(`/teacher/papers/${row.id}/edit`)
}

async function remove(row: PaperResponse) {
  try {
    await ElMessageBox.confirm(`确认删除试卷「${row.name}」？`, '提示', { type: 'warning' })
    await teacherApi.deletePaper(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e === 'cancel' || e?.name === 'ElMessageBoxError') return
    const status = e?.response?.status
    if (status === 404) {
      ElMessage.success('试卷不存在或已被删除')
      await load()
      return
    }
    if (status === 409) {
      try {
        let usage = (e?.response?.data?.usage as PaperUsage | undefined) || undefined
        if (!usage) {
          usage = await teacherApi.getPaperUsage(row.id)
        }

        const examCount = usage?.examCount ?? 0
        const attemptCount = usage?.attemptCount ?? 0
        const totalExamCount = usage?.totalExamCount ?? examCount
        const totalAttemptCount = usage?.totalAttemptCount ?? attemptCount
        const blockedExamCount = usage?.blockedExamCount ?? Math.max(0, totalExamCount - examCount)

        if (examCount <= 0) {
          if (totalExamCount > 0) {
            ElMessage.error(`该试卷被 ${totalExamCount} 场考试使用，但当前账号无权限删除这些考试`)
            return
          }
          ElMessage.error(`该试卷已被作答使用（作答次数：${totalAttemptCount}），无法删除`)
          return
        }

        const examIds = (usage?.examIds || []).filter((x) => typeof x === 'number')
        if (!examIds.length) {
          ElMessage.error('该试卷已被考试使用，无法获取关联考试ID，无法一键删除')
          return
        }
        const summary = (usage?.exams || [])
          .slice(0, 10)
          .map((x) => `「${x.name}」(ID:${x.id})`)
          .join('、')

        await ElMessageBox.confirm(
          `该试卷被 ${totalExamCount} 场考试使用（可删除：${examCount}，不可删除：${blockedExamCount}）${summary ? `：${summary}${examCount > 10 ? '...' : ''}` : ''}。需要先删除可删除的考试才能继续尝试删除试卷，是否一键删除并继续？`,
          '无法删除试卷',
          {
            type: 'warning',
            confirmButtonText: '删除考试并继续',
            cancelButtonText: '取消',
          },
        )

        for (const examId of examIds) {
          try {
            await teacherApi.deleteExam(examId)
          } catch (deleteExamError: any) {
            const s = deleteExamError?.response?.status
            if (s === 404) continue
            throw deleteExamError
          }
        }

        try {
          await teacherApi.deletePaper(row.id)
        } catch (finalDeleteError: any) {
          const finalStatus = finalDeleteError?.response?.status
          if (finalStatus === 409 && blockedExamCount > 0) {
            ElMessage.error(`仍有 ${blockedExamCount} 场考试占用该试卷（当前账号无权限删除）`)
            await load()
            return
          }
          if (finalStatus !== 404) throw finalDeleteError
        }
        ElMessage.success('已删除')
        await load()
      } catch (err: any) {
        if (err === 'cancel' || err?.name === 'ElMessageBoxError') return
        ElMessage.error(pickErrorMessage(err))
      }
      return
    }
    ElMessage.error(pickErrorMessage(e))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card class="card">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="font-size: 16px; font-weight: 700">试卷管理</div>
        <div style="display: flex; gap: 8px">
          <el-button type="primary" @click="openCreate">新建试卷</el-button>
          <el-button plain :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px">
        <el-input v-model="keyword" placeholder="关键词" style="width: 240px" clearable />
        <el-button type="primary" plain @click="page = 1; load()">查询</el-button>
      </div>

      <el-table :data="items" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="name" label="名称" min-width="240" />
        <el-table-column label="难度" width="120">
          <template #default="{ row }">{{ formatDifficulty(row.difficulty) }}</template>
        </el-table-column>
        <el-table-column label="题目数" width="120">
          <template #default="{ row }">{{ row.questionIds.length }}</template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 10px">
              <el-button type="primary" size="small" plain @click="openEdit(row)">编辑</el-button>
              <el-dropdown @command="(cmd: any) => exportPaper(row, cmd)">
                <el-button size="small" plain :loading="exportingId === row.id">导出</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="xlsx">导出Excel</el-dropdown-item>
                    <el-dropdown-item command="docx">导出Word</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button type="danger" size="small" plain @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 12px">
        <PaginationBar v-model:page="page" v-model:size="size" :total="total" @change="load" />
      </div>
    </el-card>
  </div>
</template>
