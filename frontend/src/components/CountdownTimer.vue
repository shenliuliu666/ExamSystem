<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Clock } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const props = defineProps<{
  endAt: string
}>()

const emit = defineEmits<{
  (e: 'done'): void
}>()

const now = ref(Date.now())
let timer: number | null = null

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})

const remainMs = computed(() => {
  const end = dayjs(props.endAt).valueOf()
  return Math.max(0, end - now.value)
})

const text = computed(() => {
  const totalSeconds = Math.floor(remainMs.value / 1000)
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = totalSeconds % 60
  
  const mm = h * 60 + m
  return `${mm}' ${String(s).padStart(2, '0')}"`
})

const doneEmitted = ref(false)
onMounted(() => {
  const check = window.setInterval(() => {
    if (!doneEmitted.value && remainMs.value <= 0) {
      doneEmitted.value = true
      emit('done')
    }
  }, 500)
  onBeforeUnmount(() => window.clearInterval(check))
})
</script>

<template>
  <div class="countdown-timer">
    <el-icon class="icon"><Clock /></el-icon>
    <span class="text">{{ text }}</span>
  </div>
</template>

<style scoped>
.countdown-timer {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #ff9f43;
  font-weight: 600;
  font-size: 18px;
}
.icon {
  font-size: 22px;
}
.text {
  font-feature-settings: "tnum";
}
</style>
