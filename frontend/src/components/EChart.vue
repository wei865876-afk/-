<template>
  <div class="chart-box" :style="{ height: height + 'px' }" ref="el"></div>
</template>

<script setup>
import { computed, toRef } from 'vue'
import { useChart, buildChartOption } from '@/composables/useChart'

const props = defineProps({
  chartMeta: { type: Object, default: null },
  rows: { type: Array, default: () => [] },
  fallbackRows: { type: Array, default: () => [] },
  height: { type: Number, default: 260 }
})

const source = computed(() => (props.rows?.length ? props.rows : props.fallbackRows))
const option = computed(() => buildChartOption(props.chartMeta, source.value))
// 必须把 ref 解构出来绑定到模板，否则图表容器拿不到 DOM 节点
const { el } = useChart(toRef(option))
</script>

<style scoped>
.chart-box { width: 100%; }
</style>
