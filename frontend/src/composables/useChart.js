import { onMounted, onBeforeUnmount, ref, watch, shallowRef } from 'vue'
import * as echarts from 'echarts'

/** ECharts 轻量封装：自动 resize、主题化配色 */
export function useChart(optionRef) {
  const el = ref(null)
  const chart = shallowRef(null)

  const render = () => {
    if (!el.value) return
    if (!chart.value) {
      chart.value = echarts.init(el.value)
    }
    if (optionRef.value) {
      chart.value.setOption(optionRef.value, true)
    }
  }

  const resize = () => chart.value?.resize()

  onMounted(() => {
    render()
    window.addEventListener('resize', resize)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    chart.value?.dispose()
    chart.value = null
  })
  watch(optionRef, render, { deep: true })

  return { el, chart, render }
}

export const CHART_COLORS = ['#4f46e5', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6']

/** 由工具返回的 chart 元数据 + 数据行生成 ECharts option */
export function buildChartOption(chartMeta, rows) {
  if (!chartMeta || !Array.isArray(rows) || !rows.length) return null
  const data = chartMeta.staticData ?? rows
  const xKey = chartMeta.xKey || 'name'
  const series = chartMeta.series || []
  const categories = data.map((row) => row[xKey] ?? row.name ?? '-')

  if (chartMeta.type === 'pie') {
    return {
      color: CHART_COLORS,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle', textStyle: { fontSize: 11, color: '#64748b' } },
      series: [
        {
          type: 'pie',
          radius: ['46%', '72%'],
          center: ['50%', '44%'],
          itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
          label: { fontSize: 11, color: '#475569' },
          data: data.map((row) => ({ name: row[xKey] ?? row.name, value: Number(row.value ?? 0) }))
        }
      ]
    }
  }

  return {
    color: CHART_COLORS,
    grid: { left: 8, right: 16, top: 34, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: series.length > 1 ? { top: 0, icon: 'circle', textStyle: { fontSize: 11, color: '#64748b' } } : undefined,
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { fontSize: 11, color: '#64748b', interval: 0, rotate: categories.length > 6 ? 30 : 0 },
      axisLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: {
      type: 'value',
      name: chartMeta.unit || '',
      nameTextStyle: { fontSize: 11, color: '#94a3b8' },
      axisLabel: { fontSize: 11, color: '#64748b' },
      splitLine: { lineStyle: { color: '#f1f5f9' } }
    },
    series: series.map((item, index) => ({
      name: item.name || item.key,
      type: chartMeta.type === 'line' ? 'line' : 'bar',
      smooth: true,
      barMaxWidth: 34,
      itemStyle: { borderRadius: chartMeta.type === 'line' ? 0 : [6, 6, 0, 0] },
      data: data.map((row) => Number(row[item.key] ?? 0))
    }))
  }
}
