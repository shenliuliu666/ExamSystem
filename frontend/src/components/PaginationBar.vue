<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    page: number
    size: number
    total: number
    pageSizes?: number[]
    layout?: string
  }>(),
  {
    pageSizes: () => [10, 20, 50, 100],
    layout: 'total, prev, pager, next, sizes',
  },
)

const emit = defineEmits<{
  (e: 'update:page', value: number): void
  (e: 'update:size', value: number): void
  (e: 'change'): void
}>()

function onCurrentChange(next: number) {
  emit('update:page', next)
  emit('change')
}

function onSizeChange(next: number) {
  emit('update:size', next)
  emit('update:page', 1)
  emit('change')
}
</script>

<template>
  <el-pagination
    :current-page="props.page"
    :page-size="props.size"
    :total="props.total"
    :page-sizes="props.pageSizes"
    :layout="props.layout"
    @current-change="onCurrentChange"
    @size-change="onSizeChange"
  />
</template>

