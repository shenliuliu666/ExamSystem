<script setup lang="ts">
import { computed } from 'vue'
import type { QuestionSnapshot } from '../types/api'

const props = defineProps<{
  question: QuestionSnapshot
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

function onUpdateModelValue(value: unknown) {
  emit('update:modelValue', String(value))
}

const multipleChoiceValue = computed(() => {
  const raw = String(props.modelValue ?? '').trim()
  if (!raw) return [] as string[]
  return raw
    .split(',')
    .map((x) => x.trim().toUpperCase())
    .filter((x) => /^[A-Z]$/.test(x))
})

function onUpdateMultipleChoiceValue(value: unknown) {
  const list = Array.isArray(value) ? value : []
  const normalized = list
    .map((x) => String(x).trim().toUpperCase())
    .filter((x) => /^[A-Z]$/.test(x))
  const unique = Array.from(new Set(normalized)).sort()
  emit('update:modelValue', unique.join(','))
}

const letterOptions = computed(() =>
  props.question.options.map((t, idx) => ({
    label: String.fromCharCode('A'.charCodeAt(0) + idx),
    text: t,
  })),
)
</script>

<template>
  <div style="display: grid; gap: 12px; font-size: 16px">
    <div style="font-weight: 600; font-size: 17px; line-height: 1.6">{{ question.stem }}</div>
    <div v-if="question.type === 'SINGLE_CHOICE'">
      <el-radio-group
        :model-value="modelValue"
        style="display: grid; gap: 10px; font-size: 16px"
        @update:model-value="onUpdateModelValue"
      >
        <el-radio v-for="o in letterOptions" :key="o.label" :value="o.label">
          {{ o.label }}. {{ o.text }}
        </el-radio>
      </el-radio-group>
    </div>
    <div v-else-if="question.type === 'MULTIPLE_CHOICE'">
      <el-checkbox-group
        :model-value="multipleChoiceValue"
        style="display: grid; gap: 10px; font-size: 16px"
        @update:model-value="onUpdateMultipleChoiceValue"
      >
        <el-checkbox v-for="o in letterOptions" :key="o.label" :value="o.label">
          {{ o.label }}. {{ o.text }}
        </el-checkbox>
      </el-checkbox-group>
    </div>
    <div v-else-if="question.type === 'TRUE_FALSE'">
      <el-radio-group
        :model-value="modelValue"
        @update:model-value="onUpdateModelValue"
      >
        <el-radio value="true">正确</el-radio>
        <el-radio value="false">错误</el-radio>
      </el-radio-group>
    </div>
  </div>
</template>
