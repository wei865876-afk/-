<template>
  <div>
    <div class="card card-pad">
      <div class="card-title">
        <h3>学业预警名单</h3>
        <div class="head-right">
          <span class="hint">规则引擎：不及格门数 · 平均分 · 待审异动 · 成绩缺失</span>
          <button class="btn btn-sm" :disabled="loading" @click="load">
            {{ loading ? '扫描中…' : '重新扫描' }}
          </button>
        </div>
      </div>

      <div class="pill-row">
        <div
          v-for="item in levelOptions"
          :key="item.value"
          class="pill"
          :class="{ active: level === item.value }"
          @click="changeLevel(item.value)"
        >
          {{ item.label }}
        </div>
      </div>

      <div class="metric-grid mt-14">
        <div v-for="item in metrics" :key="item.label" class="metric" :class="meterClass(item.label)">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}<small>{{ item.unit }}</small></div>
        </div>
      </div>

      <EChart v-if="riskChartRows.length" class="mt-14" :chart-meta="riskChart" :rows="riskChartRows" :height="190" />
    </div>

    <div class="grid grid-2 mt-16">
      <div class="card card-pad">
        <div class="card-title">
          <h3>预警明细</h3>
          <span class="hint">共 {{ rows.length }} 人</span>
        </div>
        <div class="table-wrap" style="max-height: 520px">
          <table class="data">
            <thead>
              <tr>
                <th>学号</th><th>姓名</th><th>班级</th><th>平均分</th><th>不及格</th>
                <th>风险</th><th>触发原因</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.stuId">
                <td>{{ row.stuId }}</td>
                <td class="text-strong">{{ row.stuName }}</td>
                <td>{{ row.className }}</td>
                <td>{{ row.avgScore }}</td>
                <td>{{ row.failCount }}</td>
                <td><span class="tag" :class="levelClass(row.riskLevel)">{{ levelText(row.riskLevel) }}</span></td>
                <td class="reason-cell" :title="row.reasons">{{ row.reasons }}</td>
                <td class="op-cell">
                  <button class="btn btn-sm btn-ghost" @click="viewProfile(row)">画像</button>
                  <button class="btn btn-sm btn-ghost" @click="ask(`请针对${row.stuName}（${row.stuId}）的预警原因制定帮扶方案`)">
                    追问
                  </button>
                </td>
              </tr>
              <tr v-if="!rows.length && !loading">
                <td colspan="8"><div class="empty">当前筛选条件下暂无预警学生</div></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card card-pad">
        <div class="card-title">
          <h3>AI 分层干预建议</h3>
          <span class="tag" :class="advice?.mode === 'remote' ? 'tag-ok' : 'tag-warn'">
            {{ advice?.mode === 'remote' ? '大模型生成' : '规则模板' }}
          </span>
        </div>
        <div v-if="advice?.overall" class="overall">{{ advice.overall }}</div>
        <div v-for="item in advice?.items || []" :key="item.stuId" class="advice-item">
          <div class="advice-head">
            <span class="tag" :class="levelClass(item.level)">{{ levelText(item.level) }}</span>
            <b>{{ item.stuName || item.stuId }}</b>
            <span class="muted">{{ item.stuId }}</span>
          </div>
          <div class="advice-body">{{ item.advice }}</div>
          <div v-if="item.reasons" class="advice-reason">触发原因：{{ item.reasons }}</div>
        </div>
        <div v-if="!advice" class="empty">正在生成干预建议…</div>
        <div class="foot-hint">
          建议由 AI 依据真实预警数据生成，最终处置以学校正式文件与人工审核为准
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { aiToolInvoke, aiWarningAdvice } from '@/api/ai'
import { useChatStore } from '@/stores/chat'
import EChart from '@/components/EChart.vue'

const router = useRouter()
const chat = useChatStore()

const level = ref('all')
const loading = ref(false)
const result = ref(null)
const advice = ref(null)

const levelOptions = [
  { label: '全部等级', value: 'all' },
  { label: '高风险', value: 'high' },
  { label: '中风险', value: 'medium' },
  { label: '低风险', value: 'low' }
]

const metrics = computed(() => result.value?.data?.metrics || [])
const rows = computed(() => result.value?.data?.rows || [])
const riskChartRows = computed(() =>
  metrics.value
    .filter((item) => ['高风险', '中风险', '低风险'].includes(item.label))
    .map((item) => ({ name: item.label, value: Number(item.value) || 0 }))
)
const riskChart = { type: 'pie', xKey: 'name', unit: '人', series: [{ key: 'value', name: '人数' }] }

const levelText = (value) => ({ high: '高风险', medium: '中风险', low: '低风险' })[value] || value || '-'
const levelClass = (value) => ({ high: 'tag-danger', medium: 'tag-warn', low: 'tag-ok' })[value] || 'tag-muted'
const meterClass = (label) => (label === '高风险' ? 'meter-danger' : label === '中风险' ? 'meter-warn' : '')

const load = async () => {
  loading.value = true
  try {
    const [warn, tips] = await Promise.all([
      aiToolInvoke('risk_warning', { level: level.value, limit: 100 }),
      aiWarningAdvice(level.value, 50)
    ])
    result.value = warn
    advice.value = tips
  } catch (e) {
    result.value = null
    advice.value = null
  } finally {
    loading.value = false
  }
}

const changeLevel = async (value) => {
  level.value = value
  await load()
}

const viewProfile = (row) => {
  router.push({ path: '/students', query: { stuId: row.stuId } })
}

const ask = (text) => {
  chat.send(text, 'warning')
  router.push('/assistant')
}

onMounted(load)
</script>

<style scoped>
.head-right { display: flex; align-items: center; gap: 10px; }
.mt-14 { margin-top: 14px; }
.mt-16 { margin-top: 16px; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 10px; }
.metric { background: #f8fafc; border: 1px solid var(--line); border-radius: 11px; padding: 9px 12px; }
.metric.meter-danger { background: #fef2f2; border-color: #fee2e2; }
.metric.meter-warn { background: #fffbeb; border-color: #fef3c7; }
.metric-label { font-size: 11.5px; color: var(--ink-3); }
.metric-value { font-size: 19px; font-weight: 700; letter-spacing: -0.02em; }
.metric-value small { font-size: 11px; color: var(--ink-3); margin-left: 3px; font-weight: 500; }
.text-strong { font-weight: 600; color: var(--ink-1); }
.reason-cell { max-width: 190px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12.5px; }
.op-cell { white-space: nowrap; }
.overall {
  font-size: 13px; background: #f8f9ff; border: 1px solid #e6e9ff; color: var(--ink-2);
  border-radius: 12px; padding: 11px 13px; margin-bottom: 10px; line-height: 1.7;
}
.advice-item { border-top: 1px dashed var(--line); padding: 11px 0; }
.advice-item:first-of-type { border-top: none; }
.advice-head { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.advice-head .muted { color: var(--ink-4); font-size: 11.5px; margin-left: auto; }
.advice-body { font-size: 12.8px; color: var(--ink-2); margin-top: 5px; line-height: 1.7; }
.advice-reason { font-size: 11.5px; color: var(--ink-4); margin-top: 3px; }
.foot-hint { margin-top: 12px; font-size: 11.5px; color: var(--ink-4); }
</style>
