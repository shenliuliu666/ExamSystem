<template>
  <div class="class-detail-page">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <span class="divider">/</span>
        <h2 class="class-title">{{ classroom?.name || '加载中...' }}</h2>
        <el-tag v-if="classroom" size="small" type="info">ID: {{ classroom.id }}</el-tag>
      </div>
    </div>

    <div v-loading="loading" class="content-layout">
      <!-- Info Card -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-button type="primary" link @click="showEditDialog = true">编辑</el-button>
          </div>
        </template>
        <div class="info-grid">
          <div class="info-item">
            <label>邀请码</label>
            <div class="code-box">
              <span class="code">{{ classroom?.inviteCode }}</span>
              <el-button type="primary" link size="small" @click="copyCode(classroom?.inviteCode)">复制</el-button>
            </div>
          </div>
          <div class="info-item">
            <label>创建时间</label>
            <span>{{ classroom ? formatDate(classroom.createdAt) : '-' }}</span>
          </div>
          <div class="info-item">
            <label>成员数量</label>
            <span>{{ members.length }} 人</span>
          </div>
        </div>
      </el-card>

      <!-- Members Card -->
      <el-card class="members-card" shadow="never">
        <template #header>
          <div class="card-header">
            <div class="header-title">
              <span>成员管理</span>
              <el-input
                v-model="searchKeyword"
                placeholder="搜索学号或姓名"
                :prefix-icon="Search"
                clearable
                class="search-input"
              />
            </div>
            <div class="header-actions">
              <el-button type="primary" plain @click="showAddMemberDialog = true">添加成员</el-button>
              <el-upload
                class="upload-btn"
                :show-file-list="false"
                :before-upload="() => false"
                :on-change="handleImportChange"
                accept=".xlsx"
              >
                <el-button type="success" :loading="importing">
                  <el-icon><Upload /></el-icon> 导入 Excel
                </el-button>
              </el-upload>
            </div>
          </div>
        </template>

        <!-- Import Result Alert -->
        <el-alert
          v-if="importResult"
          :title="`导入完成：成功 ${importResult.successCount} 人，失败 ${importResult.failedCount} 条`"
          :type="importResult.failedCount > 0 ? 'warning' : 'success'"
          show-icon
          closable
          @close="resetImportState"
          class="import-alert"
        >
          <div v-if="importFailures.length > 0" class="failure-list">
            <p v-for="(f, i) in importFailures" :key="i">
              行 {{ f.row }}: {{ f.reason }}
            </p>
          </div>
        </el-alert>

        <el-table :data="filteredMembers" style="width: 100%" v-loading="membersLoading">
          <el-table-column label="学号" prop="studentNo" sortable>
            <template #default="{ row }">
              {{ row.studentNo || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="姓名" prop="fullName" sortable>
            <template #default="{ row }">
              {{ row.fullName || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="登录账号" prop="username" sortable />
          <el-table-column label="加入时间" prop="joinedAt" sortable>
            <template #default="{ row }">
              {{ formatDate(row.joinedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="right">
            <template #default="{ row }">
               <el-button type="primary" link @click="openUpdateMember(row)">修改</el-button>
               <el-button type="warning" link @click="handleResetPassword(row)">重置密码</el-button>
               <el-button type="danger" link @click="handleRemoveMember(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- Edit Dialog -->
    <el-dialog v-model="showEditDialog" title="编辑班级" width="400px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="班级名称">
          <el-input v-model="editForm.name" placeholder="请输入班级名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showEditDialog = false">取消</el-button>
          <el-button type="primary" @click="handleUpdate" :loading="updating">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Update Member Dialog -->
    <el-dialog v-model="showUpdateMemberDialog" title="修改成员信息" width="400px">
      <el-form :model="updateMemberForm" label-width="80px">
        <el-form-item label="学号">
          <el-input v-model="updateMemberForm.studentNo" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="updateMemberForm.fullName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="updateMemberForm.username" placeholder="请输入登录账号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showUpdateMemberDialog = false">取消</el-button>
          <el-button type="primary" @click="handleUpdateMember" :loading="updatingMember">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Add Member Dialog -->
    <el-dialog v-model="showAddMemberDialog" title="添加成员" width="400px">
      <el-form :model="addMemberForm" label-width="80px">
        <el-form-item label="学号">
          <el-input v-model="addMemberForm.studentNo" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="addMemberForm.fullName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="addMemberForm.username" placeholder="请输入学生登录账号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAddMemberDialog = false">取消</el-button>
          <el-button type="primary" @click="handleAddMember" :loading="addingMember">添加</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Search, Upload } from '@element-plus/icons-vue'
import * as api from '../../api/teacher'
import { pickErrorMessage } from '../../api/http'
import type { Classroom, ClassMember, StudentImportFailure, StudentImportResult } from '../../types/api'

const route = useRoute()
const router = useRouter()
const classId = Number(route.params.id)

const loading = ref(false)
const classroom = ref<Classroom | null>(null)
const members = ref<ClassMember[]>([])
const membersLoading = ref(false)

const showEditDialog = ref(false)
const updating = ref(false)
const editForm = ref({ name: '' })

const showUpdateMemberDialog = ref(false)
const updatingMember = ref(false)
const updateMemberForm = ref({ originalUsername: '', username: '', studentNo: '', fullName: '' })

const showAddMemberDialog = ref(false)
const addingMember = ref(false)
const addMemberForm = ref({ studentNo: '', fullName: '', username: '' })

const searchKeyword = ref('')
const importing = ref(false)
const importResult = ref<StudentImportResult | null>(null)
const importFailures = ref<StudentImportFailure[]>([])

const filteredMembers = computed(() => {
  if (!searchKeyword.value) return members.value
  const k = searchKeyword.value.toLowerCase()
  return members.value.filter(m => 
    (m.studentNo && m.studentNo.toLowerCase().includes(k)) ||
    (m.fullName && m.fullName.toLowerCase().includes(k)) ||
    m.username.toLowerCase().includes(k)
  )
})

async function loadData() {
  if (!classId) return
  loading.value = true
  try {
    const all = await api.listClasses()
    const found = all.find(c => c.id === classId)
    if (found) {
      classroom.value = found
      editForm.value.name = found.name
      await loadMembers()
    } else {
      ElMessage.error('班级不存在')
      router.push('/teacher/classes')
    }
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  membersLoading.value = true
  try {
    members.value = await api.listClassMembers(classId)
  } catch (e) {
    ElMessage.error('加载成员失败')
  } finally {
    membersLoading.value = false
  }
}

async function handleUpdate() {
  ElMessage.warning('编辑功能暂未开放')
  showEditDialog.value = false
}

function openUpdateMember(member: ClassMember) {
  updateMemberForm.value = {
    originalUsername: member.username,
    username: member.username,
    studentNo: member.studentNo || '',
    fullName: member.fullName || ''
  }
  showUpdateMemberDialog.value = true
}

async function handleUpdateMember() {
  updatingMember.value = true
  try {
    await api.updateClassMember(classId, updateMemberForm.value.originalUsername, {
      studentNo: updateMemberForm.value.studentNo,
      fullName: updateMemberForm.value.fullName,
      username: updateMemberForm.value.username
    })
    ElMessage.success('修改成功')
    showUpdateMemberDialog.value = false
    await loadMembers()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    updatingMember.value = false
  }
}

async function handleAddMember() {
  if (!addMemberForm.value.username.trim()) return
  if (!addMemberForm.value.studentNo.trim() || !addMemberForm.value.fullName.trim()) return
  addingMember.value = true
  try {
    await api.addClassMember(classId, {
      username: addMemberForm.value.username.trim(),
      studentNo: addMemberForm.value.studentNo.trim(),
      fullName: addMemberForm.value.fullName.trim(),
    })
    ElMessage.success('添加成功')
    showAddMemberDialog.value = false
    addMemberForm.value.username = ''
    addMemberForm.value.studentNo = ''
    addMemberForm.value.fullName = ''
    await loadMembers()
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    addingMember.value = false
  }
}

async function handleResetPassword(member: ClassMember) {
  try {
    await ElMessageBox.confirm(
      `确定要重置「${member.fullName || member.username}」的密码吗？`,
      '重置密码确认',
      {
        type: 'warning',
        confirmButtonText: '重置密码',
        cancelButtonText: '取消'
      }
    )
    await api.resetClassMemberPassword(classId, member.username)
    ElMessage.success('密码已重置')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(pickErrorMessage(e))
    }
  }
}

async function handleRemoveMember(member: ClassMember) {
  try {
    await ElMessageBox.confirm(`确定要将 ${member.fullName || member.username} 移出班级吗？`, '移除确认', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
    await api.removeClassMember(classId, member.username)
    ElMessage.success('已移除')
    await loadMembers()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(pickErrorMessage(e))
    }
  }
}

async function handleImportChange(file: any) {
  if (!file || !file.raw) return
  importing.value = true
  resetImportState()
  try {
    const res = await api.importStudentsToClass(classId, file.raw as File)
    importResult.value = res
    importFailures.value = res.failures || []
    if (res.successCount > 0) {
      ElMessage.success(`导入成功 ${res.successCount} 人`)
      await loadMembers()
    } else if (res.failedCount > 0) {
      ElMessage.warning('导入有失败记录，请查看详情')
    }
  } catch (e) {
    ElMessage.error(pickErrorMessage(e))
  } finally {
    importing.value = false
  }
}

function resetImportState() {
  importResult.value = null
  importFailures.value = []
}

function copyCode(code: string | undefined) {
  if (!code) return
  navigator.clipboard.writeText(code).then(() => {
    ElMessage.success('邀请码已复制')
  })
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString()
}

onMounted(loadData)
</script>

<style scoped>
.class-detail-page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}
.page-header {
  margin-bottom: 24px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.divider {
  color: #909399;
  font-size: 16px;
}
.class-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.content-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.info-card .card-header, .members-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 24px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.info-item label {
  font-size: 13px;
  color: #909399;
}
.info-item span {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
}
.code-box {
  display: flex;
  align-items: center;
  gap: 8px;
}
.code {
  font-family: monospace;
  font-size: 16px;
  background-color: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #409eff;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 16px;
}
.search-input {
  width: 240px;
}
.header-actions {
  display: flex;
  gap: 12px;
}
.import-alert {
  margin-bottom: 16px;
}
.failure-list {
  max-height: 100px;
  overflow-y: auto;
  margin-top: 8px;
  font-size: 12px;
}
</style>
