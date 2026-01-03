<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import { formatDateTime } from '../../utils/time'
import type { ExamAnalyticsResponse, ExamResponse, TeacherResultResponse } from '../../types/api'

const route = useRoute()
const router = useRouter()

const examId = Number(route.params.id)
const loading = ref(false)
const exam = ref<ExamResponse | null>(null)
const analytics = ref<ExamAnalyticsResponse | null>(null)
const results = ref<TeacherResultResponse[]>([])

const aiAnalyzing = ref(false)
const aiText = ref('')
const aiError = ref('')
const exporting = ref(false)

const studentKeyword = ref('')
const studentOnly = ref<'ALL' | 'PASS' | 'FAIL'>('ALL')
const studentSort = ref<'SCORE_DESC' | 'SCORE_ASC' | 'TIME_DESC' | 'TIME_ASC'>('SCORE_DESC')

function formatPercent(v: number) {
  return `${(v * 100).toFixed(2)}%`
}

function scoreRatio(totalScore: number, maxScore: number) {
  if (!maxScore || maxScore <= 0) return 0
  return totalScore / maxScore
}

function toNumber(v: unknown): number {
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : 0
}

function clamp01(v: number) {
  if (v < 0) return 0
  if (v > 1) return 1
  return v
}

function round2(v: number) {
  return Math.round(v * 100) / 100
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
  const prefix = safe || `exam-${examId}-results`
  return `${prefix}_${ts}.${ext}`
}

async function exportClassScoresExcel() {
  exporting.value = true
  try {
    const blob = await teacherApi.exportExamExcel(examId)
    downloadBlob(blob, exportFilename(exam.value?.name ? `班级成绩_${exam.value.name}` : `exam-${examId}-results`, 'xlsx'))
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    exporting.value = false
  }
}

const passLineRatio = computed(() => analytics.value?.passLineRatio ?? 0.6)
const passLineScore = computed(() => {
  const maxScore = analytics.value?.maxScore ?? 0
  return Math.ceil(maxScore * passLineRatio.value)
})

const resultStats = computed(() => {
  const items = results.value ?? []
  if (items.length === 0) {
    return {
      count: 0,
      passCount: 0,
      failCount: 0,
      median: 0,
      p25: 0,
      p75: 0,
      stddev: 0,
      avg: 0,
      ratioMedian: 0,
      ratioP25: 0,
      ratioP75: 0,
    }
  }

  const scores = items.map((r) => toNumber(r.totalScore)).sort((a, b) => a - b)
  const ratios = items.map((r) => clamp01(scoreRatio(r.totalScore, r.maxScore))).sort((a, b) => a - b)
  const n = scores.length

  const q = (arr: number[], p: number): number => {
    if (arr.length === 0) return 0
    const idx = (arr.length - 1) * p
    const lo = Math.floor(idx)
    const hi = Math.ceil(idx)
    const safeLo = Math.min(Math.max(lo, 0), arr.length - 1)
    const safeHi = Math.min(Math.max(hi, 0), arr.length - 1)
    const loVal = arr[safeLo] ?? 0
    const hiVal = arr[safeHi] ?? 0
    if (safeLo === safeHi) return loVal
    const w = idx - safeLo
    return loVal * (1 - w) + hiVal * w
  }

  const avg = scores.reduce((s, x) => s + x, 0) / n
  const variance = scores.reduce((s, x) => s + (x - avg) * (x - avg), 0) / n
  const stddev = Math.sqrt(variance)

  const passCount = items.filter((r) => clamp01(scoreRatio(r.totalScore, r.maxScore)) >= passLineRatio.value).length
  const failCount = n - passCount

  return {
    count: n,
    passCount,
    failCount,
    median: round2(q(scores, 0.5)),
    p25: round2(q(scores, 0.25)),
    p75: round2(q(scores, 0.75)),
    stddev: round2(stddev),
    avg: round2(avg),
    ratioMedian: round2(q(ratios, 0.5) * 100),
    ratioP25: round2(q(ratios, 0.25) * 100),
    ratioP75: round2(q(ratios, 0.75) * 100),
  }
})

type DistributionBin = {
  label: string
  from: number
  to: number
  count: number
  ratio: number
}

const distribution = computed<DistributionBin[]>(() => {
  const items = results.value ?? []
  if (items.length === 0) {
    return [
      { label: '<60%', from: 0, to: 0.6, count: 0, ratio: 0 },
      { label: '60-69%', from: 0.6, to: 0.7, count: 0, ratio: 0 },
      { label: '70-79%', from: 0.7, to: 0.8, count: 0, ratio: 0 },
      { label: '80-89%', from: 0.8, to: 0.9, count: 0, ratio: 0 },
      { label: '90-100%', from: 0.9, to: 1.0000001, count: 0, ratio: 0 },
    ]
  }

  const bins: DistributionBin[] = [
    { label: '<60%', from: 0, to: 0.6, count: 0, ratio: 0 },
    { label: '60-69%', from: 0.6, to: 0.7, count: 0, ratio: 0 },
    { label: '70-79%', from: 0.7, to: 0.8, count: 0, ratio: 0 },
    { label: '80-89%', from: 0.8, to: 0.9, count: 0, ratio: 0 },
    { label: '90-100%', from: 0.9, to: 1.0000001, count: 0, ratio: 0 },
  ]

  for (const r of items) {
    const ratio = clamp01(scoreRatio(r.totalScore, r.maxScore))
    const b = bins.find((x) => ratio >= x.from && ratio < x.to)
    if (b) b.count += 1
  }

  const total = items.length
  for (const b of bins) {
    b.ratio = total === 0 ? 0 : b.count / total
  }
  return bins
})

const distributionMaxCount = computed(() => Math.max(1, ...distribution.value.map((b) => b.count)))

type StudentRow = TeacherResultResponse & { ratio: number; pass: boolean }

const studentRows = computed<StudentRow[]>(() => {
  const items = results.value ?? []
  const k = studentKeyword.value.trim().toLowerCase()
  const rows = items
    .map((r) => {
      const ratio = clamp01(scoreRatio(r.totalScore, r.maxScore))
      return { ...r, ratio, pass: ratio >= passLineRatio.value }
    })
    .filter((r) => {
      if (studentOnly.value === 'PASS' && !r.pass) return false
      if (studentOnly.value === 'FAIL' && r.pass) return false
      if (!k) return true
      return (
        String(r.studentNo ?? '').toLowerCase().includes(k) ||
        String(r.fullName ?? '').toLowerCase().includes(k) ||
        String(r.studentUsername ?? '').toLowerCase().includes(k)
      )
    })

  const ts = (s: string) => {
    const n = Date.parse(s)
    return Number.isFinite(n) ? n : 0
  }
  rows.sort((a, b) => {
    if (studentSort.value === 'SCORE_ASC') return a.totalScore - b.totalScore
    if (studentSort.value === 'SCORE_DESC') return b.totalScore - a.totalScore
    if (studentSort.value === 'TIME_ASC') return ts(a.createdAt) - ts(b.createdAt)
    return ts(b.createdAt) - ts(a.createdAt)
  })
  return rows
})

async function load() {
  loading.value = true
  try {
    const [e, a, r] = await Promise.all([
      teacherApi.getExam(examId),
      teacherApi.getExamAnalytics(examId),
      teacherApi.getExamResults(examId),
    ])
    exam.value = e
    analytics.value = a
    results.value = r
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function back() {
  await router.replace('/teacher/exams')
}

async function goResults() {
  await router.push(`/teacher/exams/${examId}/results`)
}

async function onSmartAnalyze() {
  if (!resultStats.value.count) {
    ElMessage.info('当前暂无成绩数据，暂无法进行智能分析')
    return
  }
  aiAnalyzing.value = true
  aiText.value = ''
  aiError.value = ''
  try {
    const res = await teacherApi.getExamAiAnalysis(examId)
    aiText.value = res.content
  } catch (e) {
    aiError.value = pickErrorMessage(e)
  } finally {
    aiAnalyzing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card class="card" v-loading="loading">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <div style="font-size: 16px; font-weight: 700">成绩分析</div>
          <el-text v-if="exam" type="info">#{{ exam.id }} {{ exam.name }}</el-text>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button type="primary" plain :loading="exporting" @click="exportClassScoresExcel">导出班级成绩</el-button>
          <el-button plain @click="back">返回</el-button>
        </div>
      </div>

      <div v-if="analytics" style="display: grid; gap: 12px">
        <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="flex: 1"
            :title="`及格线：${passLineScore} 分（${formatPercent(passLineRatio)}）`"
          />
          <el-button type="primary" plain @click="goResults">查看全部成绩</el-button>
        </div>

        <el-row :gutter="12">
          <el-col :span="6">
            <el-card shadow="never">
              <el-statistic title="参与人数" :value="analytics.participants" />
              <div style="margin-top: 6px; color: #909399; font-size: 12px">已生成成绩人数</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <el-statistic title="平均分" :value="analytics.avgTotalScore.toFixed(2)" />
              <div style="margin-top: 6px; color: #909399; font-size: 12px">中位数 {{ resultStats.median }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <el-statistic title="最高 / 最低" :value="`${analytics.maxTotalScore} / ${analytics.minTotalScore}`" />
              <div style="margin-top: 6px; color: #909399; font-size: 12px">标准差 {{ resultStats.stddev }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <el-statistic title="及格率" :value="formatPercent(analytics.passRate)" />
              <div style="margin-top: 6px; color: #909399; font-size: 12px">
                及格 {{ resultStats.passCount }} / 不及格 {{ resultStats.failCount }}
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-card style="height: 340px">
              <template #header>
                <div style="display: flex; align-items: center; justify-content: space-between">
                  <div style="font-weight: 700">分数分布（按得分率）</div>
                  <div style="display: flex; flex-direction: column; gap: 4px; color: #909399; font-size: 12px">
                    <div>
                      <span>25%位 {{ resultStats.ratioP25 }}%</span>
                      <span style="margin: 0 8px">中位数 {{ resultStats.ratioMedian }}%</span>
                      <span>75%位 {{ resultStats.ratioP75 }}%</span>
                    </div>
                    <div>条形长度按人数归一，与人数成比例</div>
                  </div>
                </div>
              </template>
              <div style="display: grid; gap: 10px">
                <div
                  v-for="b in distribution"
                  :key="b.label"
                  style="display: grid; grid-template-columns: 70px 1fr 80px; gap: 10px; align-items: center"
                >
                  <div style="color: #606266">{{ b.label }}</div>
                  <div style="height: 10px; background: #f2f3f5; border-radius: 999px; overflow: hidden">
                    <div
                      :style="{
                        height: '100%',
                        width: `${(b.count / distributionMaxCount) * 100}%`,
                        maxWidth: '100%',
                        background: '#409eff',
                        borderRadius: '999px',
                      }"
                    />
                  </div>
                  <div style="text-align: right; color: #303133; font-size: 12px">
                    <span style="font-weight: 700">{{ b.count }}</span>
                    <span style="color: #909399; margin-left: 4px">{{ formatPercent(b.ratio) }}</span>
                  </div>
                </div>
              </div>
              <div style="margin-top: 6px; font-size: 12px; color: #909399">
                共 {{ resultStats.count }} 人，区间按得分率分段统计
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card style="height: 340px">
              <template #header>
                <div style="display: flex; align-items: center; justify-content: space-between">
                  <div style="font-weight: 700">考情分析</div>
                  <el-button type="primary" plain :loading="aiAnalyzing" @click="onSmartAnalyze">
                    {{ aiAnalyzing ? '分析中...' : '智能分析' }}
                  </el-button>
                </div>
              </template>
              <template v-if="aiText || aiError">
                <div style="height: 260px; overflow-y: auto; padding-right: 4px">
                  <el-alert
                    v-if="aiError"
                    type="error"
                    :closable="false"
                    show-icon
                    style="margin-bottom: 8px"
                    :title="aiError"
                  />
                  <el-scrollbar v-if="aiText" height="220px">
                    <div style="white-space: pre-wrap; line-height: 1.6; font-size: 13px">
                      {{ aiText }}
                    </div>
                  </el-scrollbar>
                  <div v-else-if="aiAnalyzing" style="height: 100%; display: flex; align-items: center; justify-content: center; color: #909399">
                    正在生成智能分析，请稍候...
                  </div>
                </div>
              </template>
              <template v-else>
                <div
                  style="
                    height: 260px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                  "
                >
                  <el-empty :image-size="80" description="暂无智能分析结果" />
                  <div style="font-size: 12px; color: #909399">
                    请点击上方“智能分析”按钮生成本次考试的考情分析
                  </div>
                </div>
              </template>
            </el-card>
          </el-col>
        </el-row>

        <el-card>
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between">
              <div style="font-weight: 700">班级成绩</div>
              <div style="display: flex; gap: 10px; align-items: center">
                <el-input v-model="studentKeyword" placeholder="搜索学号/姓名/用户名" clearable style="width: 220px" />
                <el-select v-model="studentOnly" style="width: 120px">
                  <el-option label="全部" value="ALL" />
                  <el-option label="仅及格" value="PASS" />
                  <el-option label="仅不及格" value="FAIL" />
                </el-select>
                <el-select v-model="studentSort" style="width: 160px">
                  <el-option label="得分高→低" value="SCORE_DESC" />
                  <el-option label="得分低→高" value="SCORE_ASC" />
                  <el-option label="交卷新→旧" value="TIME_DESC" />
                  <el-option label="交卷旧→新" value="TIME_ASC" />
                </el-select>
              </div>
            </div>
          </template>
          <el-table :data="studentRows" style="width: 100%" height="480">
            <el-table-column type="index" width="60" />
            <el-table-column prop="studentNo" label="学号" min-width="140">
              <template #default="{ row }">{{ row.studentNo || row.studentUsername }}</template>
            </el-table-column>
            <el-table-column prop="fullName" label="姓名" min-width="120">
              <template #default="{ row }">{{ row.fullName || '-' }}</template>
            </el-table-column>
            <el-table-column label="得分率" min-width="160">
              <template #default="{ row }">
                <el-progress :percentage="Math.round(row.ratio * 100)" :stroke-width="10" :status="row.pass ? 'success' : 'exception'" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.pass ? 'success' : 'danger'" effect="plain">{{ row.pass ? '及格' : '不及格' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="交卷时间" min-width="180">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

      </div>
    </el-card>
  </div>
</template>
