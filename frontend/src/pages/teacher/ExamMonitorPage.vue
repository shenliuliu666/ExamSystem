<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pickErrorMessage } from '../../api/http'
import * as teacherApi from '../../api/teacher'
import type { ExamMonitorResponse, ExamResponse } from '../../types/api'
import { formatDateTime } from '../../utils/time'

const route = useRoute()
const router = useRouter()

const examId = Number(route.params.id)
const loading = ref(false)
const exam = ref<ExamResponse | null>(null)
const monitor = ref<ExamMonitorResponse | null>(null)

const statusFilter = ref<'ALL' | 'IN_PROGRESS' | 'SUBMITTED'>('ALL')
const keyword = ref('')
const exceptionDialogVisible = ref(false)
const markedUsernames = ref<string[]>([])
const remindDialogVisible = ref(false)
const remindForm = ref<{ username: string; message: string }>({ username: '', message: '' })

const markStorageKey = `exam-monitor-marks-${examId}`

function formatEventType(type: string) {
  switch (type) {
    case 'VISIBILITY_VISIBLE':
      return '切回考试标签页'
    case 'VISIBILITY_HIDDEN':
      return '离开考试标签页（切到其他页面）'
    case 'WINDOW_FOCUS':
      return '切入考试窗口（窗口获得焦点）'
    case 'WINDOW_BLUR':
      return '切出考试窗口（窗口失去焦点）'
    default:
      return type
  }
}

function formatDuration(seconds: number) {
  if (!seconds || seconds <= 0) return '0 秒'
  const totalSeconds = Math.round(seconds)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const secs = totalSeconds % 60
  if (hours > 0) {
    if (minutes > 0) {
      return `${hours} 时 ${minutes} 分`
    }
    return `${hours} 时`
  }
  if (minutes > 0) {
    if (secs > 0) {
      return `${minutes} 分 ${secs} 秒`
    }
    return `${minutes} 分`
  }
  return `${secs} 秒`
}

const studentRows = computed(() => {
  const m = monitor.value
  if (!m) return []
  const usernames = new Set<string>()
  Object.keys(m.studentInfos ?? {}).forEach((u) => usernames.add(u))
  m.inProgressUsers.forEach((u) => usernames.add(u))
  m.submittedUsers.forEach((u) => usernames.add(u))
  m.latestHeartbeats.forEach((h) => usernames.add(h.username))
  m.events.forEach((e) => usernames.add(e.username))

  const list = Array.from(usernames).sort((a, b) => {
    const ia = m.studentInfos?.[a]
    const ib = m.studentInfos?.[b]
    const sa = (ia?.studentNo ?? '').trim()
    const sb = (ib?.studentNo ?? '').trim()
    if (!sa && sb) return 1
    if (sa && !sb) return -1
    if (sa && sb) {
      const cmp = sa.localeCompare(sb)
      if (cmp !== 0) return cmp
    }
    return a.localeCompare(b)
  })
  return list.map((u, index) => {
    const userEvents = m.events
      .filter((e) => e.username === u)
      .slice()
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())

    let tabSwitchCount = 0
    let awayStart: number | null = null
    let awayMs = 0

    for (const e of userEvents) {
      const ts = new Date(e.createdAt).getTime()
      if (e.type === 'WINDOW_BLUR' || e.type === 'VISIBILITY_HIDDEN') {
        if (awayStart == null) {
          awayStart = ts
        }
      } else if (e.type === 'WINDOW_FOCUS' || e.type === 'VISIBILITY_VISIBLE') {
        if (awayStart != null) {
          if (ts > awayStart) {
            awayMs += ts - awayStart
          }
          tabSwitchCount += 1
          awayStart = null
        }
      }
    }

    let status = '未开始'
    let statusTag: 'info' | 'warning' | 'success' = 'info'
    if (m.submittedUsers.includes(u)) {
      status = '已交卷'
      statusTag = 'success'
    } else if (m.inProgressUsers.includes(u)) {
      status = '作答中'
      statusTag = 'warning'
    }

    const heartbeat = m.latestHeartbeats.find((h) => h.username === u)
    const lastEvent = m.events.find((e) => e.username === u)

    const info = m.studentInfos?.[u]
    const fullName = info?.fullName || ''
    const studentNo = info?.studentNo || ''

    return {
      index: index + 1,
      username: u,
      fullName,
      studentNo,
      status,
      statusTag,
      lastHeartbeat: heartbeat ? formatDateTime(heartbeat.ts, 'YYYY-MM-DD HH:mm:ss') : '',
      lastEventType: lastEvent ? formatEventType(lastEvent.type) : '',
      lastEventTime: lastEvent ? formatDateTime(lastEvent.createdAt, 'YYYY-MM-DD HH:mm:ss') : '',
      tabSwitchCount,
      tabAwaySeconds: Math.round(awayMs / 1000),
    }
  })
})

const filteredRows = computed(() => {
  return studentRows.value.filter((row) => {
    if (statusFilter.value === 'IN_PROGRESS' && row.status !== '作答中') {
      return false
    }
    if (statusFilter.value === 'SUBMITTED' && row.status !== '已交卷') {
      return false
    }
    if (keyword.value) {
      const k = keyword.value.trim()
      if (k) {
        if (!row.username.includes(k) && !row.fullName.includes(k) && !row.studentNo.includes(k)) {
          return false
        }
      }
    }
    return true
  })
})

let timer: number | null = null

function isMarked(username: string) {
  return markedUsernames.value.includes(username)
}

function toggleMark(username: string) {
  const index = markedUsernames.value.indexOf(username)
  if (index >= 0) {
    markedUsernames.value.splice(index, 1)
  } else {
    markedUsernames.value.push(username)
  }
  try {
    window.localStorage.setItem(markStorageKey, JSON.stringify(markedUsernames.value))
  } catch {}
}

async function remind(row: { username: string; status: string }) {
  remindForm.value.username = row.username
  remindForm.value.message = ''
  remindDialogVisible.value = true
}

async function forceSubmit(row: { username: string; status: string }) {
  if (row.status !== '作答中') {
    ElMessage.warning('仅可对作答中的学生强制收卷')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要对考生「${row.username}」强制收卷吗？该操作不可撤销。`,
      '确认强制收卷',
      {
        type: 'warning',
        confirmButtonText: '强制收卷',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  try {
    await teacherApi.forceSubmitExamForStudent(examId, { username: row.username })
    ElMessage.success(`已对考生 ${row.username} 强制收卷`)
    await loadOnce()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  }
}

async function reopen(row: { username: string; status: string }) {
  if (row.status !== '已交卷') {
    ElMessage.warning('仅可对已交卷的学生重新开放')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要为考生「${row.username}」重新开放本次考试吗？`,
      '确认重新开放',
      {
        type: 'warning',
        confirmButtonText: '重新开放',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  try {
    await teacherApi.reopenExamForStudent(examId, { username: row.username })
    ElMessage.success(`已为考生 ${row.username} 重新开放考试`)
    await loadOnce()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  }
}

async function exportMonitor() {
  try {
    const blob = await teacherApi.exportMonitorData(examId, {
      markedUsernames: markedUsernames.value,
      statusFilter: statusFilter.value,
      keyword: keyword.value,
    })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `exam-${examId}-monitor.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  }
}

async function loadOnce() {
  loading.value = true
  try {
    const [e, m] = await Promise.all([teacherApi.getExam(examId), teacherApi.getExamMonitor(examId)])
    exam.value = e
    monitor.value = m
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function back() {
  await router.replace('/teacher/exams')
}

onMounted(async () => {
  try {
    const raw = window.localStorage.getItem(markStorageKey)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        markedUsernames.value = parsed.filter((x) => typeof x === 'string')
      }
    }
  } catch {}
  await loadOnce()
  timer = window.setInterval(async () => {
    try {
      monitor.value = await teacherApi.getExamMonitor(examId)
    } catch {}
  }, 5000)
})

onUnmounted(() => {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
})
</script>

<template>
  <div class="page">
    <el-card class="card" v-loading="loading">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px">
        <div style="display: flex; flex-direction: column; gap: 4px">
          <div style="font-size: 16px; font-weight: 700">考试监控</div>
          <el-text v-if="exam" type="info">#{{ exam.id }} {{ exam.name }}（{{ exam.status }}）</el-text>
        </div>
        <div style="display: flex; gap: 8px">
          <el-button type="primary" plain @click="exceptionDialogVisible = true">异常分析</el-button>
          <el-button plain @click="exportMonitor">导出Excel</el-button>
          <el-button plain @click="loadOnce">刷新</el-button>
          <el-button plain @click="back">返回</el-button>
        </div>
      </div>

      <div v-if="monitor" style="display: flex; flex-direction: column; gap: 16px">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="已开始">{{ monitor.startedCount }}</el-descriptions-item>
          <el-descriptions-item label="作答中">{{ monitor.inProgressCount }}</el-descriptions-item>
          <el-descriptions-item label="已交卷">{{ monitor.submittedCount }}</el-descriptions-item>
          <el-descriptions-item label="已出成绩">{{ monitor.resultsCount }}</el-descriptions-item>
        </el-descriptions>

        <div style="display: flex; justify-content: space-between; align-items: center">
          <div style="display: flex; gap: 12px">
            <el-radio-group v-model="statusFilter" size="small">
              <el-radio-button label="ALL">全部</el-radio-button>
              <el-radio-button label="IN_PROGRESS">作答中</el-radio-button>
              <el-radio-button label="SUBMITTED">已交卷</el-radio-button>
            </el-radio-group>
          </div>
          <el-input
            v-model="keyword"
            size="small"
            placeholder="按用户名搜索"
            clearable
            style="width: 220px"
          />
        </div>

        <el-table :data="filteredRows" border style="width: 100%">
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column prop="fullName" label="姓名" width="100" />
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column label="标记" width="80">
            <template #default="{ row }">
              <span>{{ isMarked(row.username) ? '是' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.statusTag" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="tabSwitchCount" label="切屏次数" width="100" />
          <el-table-column label="切屏时长" width="130">
            <template #default="{ row }">
              <span>{{ formatDuration(row.tabAwaySeconds) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="260">
            <template #default="{ row }">
              <el-button
                size="small"
                :type="isMarked(row.username) ? 'default' : 'primary'"
                plain
                @click="toggleMark(row.username)"
              >
                {{ isMarked(row.username) ? '取消标记' : '标记学生' }}
              </el-button>
              <el-button
                v-if="row.status === '作答中'"
                size="small"
                type="warning"
                plain
                @click="remind(row)"
              >
                提醒考生
              </el-button>
              <el-button
                v-if="row.status === '作答中'"
                size="small"
                type="danger"
                plain
                @click="forceSubmit(row)"
              >
                强制收卷
              </el-button>
              <el-button
                v-if="row.status === '已交卷'"
                size="small"
                type="success"
                plain
                @click="reopen(row)"
              >
                重新开放
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-dialog v-model="exceptionDialogVisible" title="异常分析" width="960px">
        <div v-if="monitor" style="display: grid; gap: 16px; grid-template-columns: 2fr 1fr">
          <el-card>
            <template #header>
              <div style="font-weight: 700">异常事件时间轴</div>
            </template>
            <div v-if="monitor.events.length === 0" style="color: #86909c">暂无上报事件</div>
            <el-timeline v-else>
              <el-timeline-item
                v-for="e in monitor.events"
                :key="e.id"
                :timestamp="formatDateTime(e.createdAt, 'YYYY-MM-DD HH:mm:ss')"
                type="warning"
              >
                <div>{{ e.username }} - {{ formatEventType(e.type) }}</div>
              </el-timeline-item>
            </el-timeline>
          </el-card>

          <el-card>
            <template #header>
              <div style="font-weight: 700">在线心跳记录</div>
            </template>
            <div v-if="monitor.latestHeartbeats.length === 0" style="color: #86909c">暂无心跳记录</div>
            <el-descriptions v-else :column="1" size="small" border>
              <el-descriptions-item
                v-for="h in monitor.latestHeartbeats"
                :key="`${h.attemptId}-${h.username}`"
                :label="h.username"
              >
                {{ formatDateTime(h.ts, 'YYYY-MM-DD HH:mm:ss') }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="exceptionDialogVisible = false">关闭</el-button>
          </span>
        </template>
      </el-dialog>

      <el-dialog v-model="remindDialogVisible" title="提醒考生" width="480px">
        <el-form label-width="80px">
          <el-form-item label="考生">
            <el-text>{{ remindForm.username }}</el-text>
          </el-form-item>
          <el-form-item label="提醒内容">
            <el-input
              v-model="remindForm.message"
              type="textarea"
              :rows="4"
              maxlength="200"
              show-word-limit
              placeholder="请输入要发送给考生的提醒内容"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="remindDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!remindForm.message.trim()"
            @click="async () => {
              try {
                await teacherApi.remindExamStudent(examId, {
                  username: remindForm.username,
                  message: remindForm.message.trim(),
                })
                ElMessage.success(`已向 ${remindForm.username} 发送提醒`)
                remindDialogVisible = false
              } catch (e) {
                ElMessage.error(pickErrorMessage(e))
              }
            }"
          >
            发送
          </el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>
