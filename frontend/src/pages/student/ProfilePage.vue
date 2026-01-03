<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import * as studentApi from '../../api/student'
import type { Classroom, ExamResponse } from '../../types/api'
import { User, School, Trophy, DataAnalysis } from '@element-plus/icons-vue'

const auth = useAuthStore()
const loading = ref(false)
const exams = ref<ExamResponse[]>([])
const classes = ref<Classroom[]>([])

const completedExams = computed(() =>
  exams.value.filter((e) => e.myStatus === 'GRADED' || e.myStatus === 'SUBMITTED'),
)

const stats = computed(() => {
  const total = completedExams.value.length
  const graded = completedExams.value.filter((e) => e.myStatus === 'GRADED').length
  return {
    total,
    graded,
  }
})

const primaryClassName = computed(() => {
  const first = classes.value[0]
  if (!first) return '暂未加入班级'
  if (classes.value.length === 1) return first.name
  return `${first.name} 等 ${classes.value.length} 个班级`
})

const showJoinDialog = ref(false)
const joining = ref(false)
const joinForm = ref({ inviteCode: '', studentNo: '', fullName: '' })

async function load() {
  loading.value = true
  try {
    const [examList, classList] = await Promise.all([
      studentApi.listExams(),
      studentApi.listClasses(),
    ])
    exams.value = examList
    classes.value = classList
  } finally {
    loading.value = false
  }
}

async function handleJoin() {
  if (!joinForm.value.inviteCode) return
  if (!joinForm.value.studentNo.trim() || !joinForm.value.fullName.trim()) return
  joining.value = true
  try {
    await studentApi.joinClass({
      inviteCode: joinForm.value.inviteCode,
      studentNo: joinForm.value.studentNo.trim(),
      fullName: joinForm.value.fullName.trim(),
    })
    ElMessage.success('加入成功')
    showJoinDialog.value = false
    joinForm.value.inviteCode = ''
    joinForm.value.studentNo = ''
    joinForm.value.fullName = ''
    classes.value = await studentApi.listClasses()
  } catch (e: any) {
    if (e.response && e.response.status === 409) {
      ElMessage.warning('你已经加入过该班级')
    } else if (e.response && e.response.status === 404) {
      ElMessage.error('邀请码无效')
    } else {
      ElMessage.error('加入失败')
    }
  } finally {
    joining.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="never" class="profile-card">
          <div class="user-info">
            <el-avatar :size="64" :icon="User" />
            <div class="username">{{ auth.fullName || auth.username }}</div>
            <el-tag size="small">学生</el-tag>
          </div>
          <el-divider />
          <div class="info-list">
            <div class="info-item">
              <el-icon><School /></el-icon>
              <span>班级：{{ primaryClassName }}</span>
            </div>
            <div class="info-item">
              <el-icon><User /></el-icon>
              <span>学号：{{ auth.studentNo || auth.username }}</span>
            </div>
            <div class="info-item">
              <el-button type="primary" text @click="showJoinDialog = true">加入班级</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="16">
        <el-card shadow="never" title="学习概况">
          <template #header>
            <div class="card-header">
              <span>学习概况</span>
              <el-icon><DataAnalysis /></el-icon>
            </div>
          </template>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <div class="stat-box">
                <div class="stat-value">{{ stats.total }}</div>
                <div class="stat-label">已参加考试</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="stat-box">
                <div class="stat-value">{{ stats.graded }}</div>
                <div class="stat-label">已出分考试</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <el-card shadow="never" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>近期完成</span>
              <el-icon><Trophy /></el-icon>
            </div>
          </template>
          <el-empty v-if="completedExams.length === 0" description="暂无考试记录" />
          <el-table v-else :data="completedExams.slice(0, 5)" stripe style="width: 100%">
            <el-table-column prop="name" label="考试名称" />
            <el-table-column prop="endAt" label="结束时间" width="180">
              <template #default="{ row }">
                {{ new Date(row.endAt).toLocaleDateString() }}
              </template>
            </el-table-column>
            <el-table-column prop="myStatus" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.myStatus === 'GRADED' ? 'success' : 'info'">
                  {{ row.myStatus === 'GRADED' ? '已出分' : '已提交' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="showJoinDialog" title="加入班级" width="400px">
      <el-form :model="joinForm" label-width="80px">
        <el-form-item label="邀请码">
          <el-input v-model="joinForm.inviteCode" placeholder="请输入8位邀请码" />
        </el-form-item>
        <el-form-item label="学号">
          <el-input v-model="joinForm.studentNo" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="joinForm.fullName" placeholder="请输入姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showJoinDialog = false">取消</el-button>
          <el-button type="primary" :loading="joining" @click="handleJoin">加入</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}
.profile-card {
  text-align: center;
}
.user-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 0;
}
.username {
  font-size: 18px;
  font-weight: 700;
}
.info-list {
  text-align: left;
  padding: 0 20px;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #606266;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-box {
  background-color: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 8px;
}
.stat-label {
  color: #909399;
  font-size: 14px;
}
</style>
