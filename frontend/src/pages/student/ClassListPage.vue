<template>
  <div class="class-list-page">
    <div class="header">
      <h2>我的班级</h2>
      <el-button type="primary" @click="goProfile">前往个人中心</el-button>
    </div>
    <el-empty description="班级相关信息已迁移至个人中心，请在“个人中心”加入或查看班级" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

async function goProfile() {
  await router.push('/student/profile')
}
</script>

<style scoped>
.class-list-page {
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>
