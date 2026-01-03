<template>
  <div class="class-list-page">
    <div class="header">
      <h2>班级管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">创建班级</el-button>
    </div>

    <div v-loading="loading" class="class-grid">
      <el-empty v-if="classes.length === 0" description="暂无班级，请创建" />
      <el-card v-for="c in classes" :key="c.id" class="class-card" shadow="hover" @click="goToDetail(c)">
        <template #header>
          <div class="card-header">
            <span class="class-name">{{ c.name }}</span>
            <el-tag size="small">ID: {{ c.id }}</el-tag>
          </div>
        </template>
        <div class="card-body">
          <div class="info-item">
            <el-icon><User /></el-icon>
            <span>成员：{{ c.memberCount || 0 }} 人</span>
          </div>
          <div class="info-item">
            <el-icon><Calendar /></el-icon>
            <span>创建于：{{ formatDate(c.createdAt) }}</span>
          </div>
          <div class="actions">
            <el-button type="primary" link @click.stop="goToDetail(c)">进入管理</el-button>
            <el-button type="danger" link @click.stop="handleDelete(c)">删除</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreateDialog" title="创建班级" width="400px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="班级名称">
          <el-input v-model="createForm.name" placeholder="请输入班级名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" @click="handleCreate" :loading="creating">创建</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Calendar } from '@element-plus/icons-vue'
import * as api from '../../api/teacher'
import { pickErrorMessage } from '../../api/http'
import type { Classroom } from '../../types/api'

const router = useRouter()
const loading = ref(false)
const classes = ref<Classroom[]>([])
const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = ref({ name: '' })

async function loadClasses() {
  loading.value = true
  try {
    classes.value = await api.listClasses()
  } catch (e) {
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!createForm.value.name) return
  creating.value = true
  try {
    await api.createClass({ name: createForm.value.name })
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    createForm.value.name = ''
    loadClasses()
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

async function handleDelete(c: Classroom) {
  try {
    // Check if there are exclusive members first
    const exclusiveMembers = await api.getClassExclusiveMembers(c.id)
    
    let deleteMembers = false
    let confirmMsg = '确定要删除该班级吗？此操作不可恢复。'
    
    if (exclusiveMembers.length > 0) {
      confirmMsg = `该班级有 ${exclusiveMembers.length} 名学生仅属于此班级（${exclusiveMembers.slice(0, 3).join(', ')}${exclusiveMembers.length > 3 ? '等' : ''}）。\n删除班级将同时删除这些学生账号。是否继续？`
      deleteMembers = true
    } else {
      confirmMsg = '确定要删除该班级吗？班级成员关系将被解除，学生账号保留。'
    }

    await ElMessageBox.confirm(confirmMsg, '删除确认', {
      type: 'warning',
      confirmButtonText: deleteMembers ? '删除班级和账号' : '删除班级',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true
    })

    await api.deleteClass(c.id, deleteMembers)
    ElMessage.success('删除成功')
    await loadClasses()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(pickErrorMessage(e))
    }
  }
}

function goToDetail(c: Classroom) {
  router.push(`/teacher/classes/${c.id}`)
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString()
}

onMounted(loadClasses)
</script>

<style scoped>
.class-list-page {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.class-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
}
.class-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.class-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.class-name {
  font-weight: bold;
  font-size: 16px;
}
.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: #606266;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
</style>
