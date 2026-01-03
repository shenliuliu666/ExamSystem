<script setup lang="ts">
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
</script>

<template>
  <div class="page">
    <el-card class="card">
      <div style="font-size: 16px; font-weight: 700; margin-bottom: 8px">管理员</div>
      <el-text type="info">当前仅用于权限边界验证（{{ auth.username }}）</el-text>
    </el-card>
  </div>
</template>

