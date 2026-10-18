<template>
  <div v-if="steps.length" class="steps">
    <div v-for="step in steps" :key="step.id" class="step" :class="step.status">
      <span class="step-dot"></span>
      <div class="step-head">
        <span class="step-tool">{{ step.title || step.tool }}</span>
        <span class="tag" :class="statusClass(step.status)">{{ statusText(step.status) }}</span>
        <span v-if="step.durationMs" class="step-time">{{ step.durationMs }} ms</span>
        <span v-if="step.summary" class="step-summary">{{ step.summary }}</span>
      </div>
      <div v-if="hasArgs(step)" class="step-args">{{ formatArgs(step.args) }}</div>
      <div v-if="step.error" class="step-error">{{ step.error }}</div>
      <div v-if="step.status !== 'running' && step.render && step.render !== 'text'" class="step-result">
        <ToolResultView :step="step" />
      </div>
    </div>
  </div>
</template>

<script setup>
import ToolResultView from '@/components/ToolResultView.vue'

const props = defineProps({
  steps: { type: Array, default: () => [] }
})

const hasArgs = (step) => step.args && Object.keys(step.args).length > 0

const formatArgs = (args) => {
  const entries = Object.entries(args || {})
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => {
      const text = typeof value === 'string' ? value : JSON.stringify(value)
      return `${key}: ${text.length > 60 ? text.slice(0, 60) + '…' : text}`
    })
  return entries.join('\n')
}

const statusText = (status) => ({ running: '执行中', success: '已完成', failed: '失败' })[status] || status
const statusClass = (status) =>
  ({ running: 'tag-info', success: 'tag-ok', failed: 'tag-danger' })[status] || 'tag-muted'
</script>

<style scoped>
.step-time { font-size: 11.5px; color: var(--ink-4); }
.step-summary { font-size: 12px; color: var(--ink-3); }
.step-error { font-size: 12px; color: var(--danger); margin-top: 6px; }
</style>
