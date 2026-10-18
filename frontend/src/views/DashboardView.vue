<template>
  <div>
    <!-- KPI -->
    <div class="kpi-row">
      <div v-for="item in kpis" :key="item.label" class="card stat">
        <div class="stat-label"><span>{{ item.icon }}</span>{{ item.label }}</div>
        <div class="stat-value" :class="item.tone">
          {{ item.value }}<small>{{ item.unit }}</small>
        </div>
      </div>
    </div>

    <div class="grid grid-2 mt-16">
      <!-- AI 主动洞察 -->
      <div class="card card-pad insight-card">
        <div class="card-title">
          <h3>AI 主动洞察</h3>
          <div class="head-right">
            <span class="tag" :class="insight?.mode === 'remote' ? 'tag-ok' : 'tag-warn'">
              {{ insight?.mode === 'remote' ? '大模型生成' : '规则引擎生成' }}
            </span>
            <button class="btn btn-sm" :disabled="loading" @click="loadInsight">
              {{ loading ? '分析中…' : '重新分析' }}
            </button>
          </div>
        </div>

        <div v-if="loading && !insight" class="skeleton-stack">
          <div class="skeleton" style="width: 60%"></div>
          <div class="skeleton" style="width: 90%"></div>
          <div class="skeleton" style="width: 78%"></div>
        </div>

        <template v-else-if="insight">
          <div class="overall">{{ insight.overall }}</div>
          <div class="insight-item" v-for="(point, index) in insight.points" :key="index">
            <div class="insight-icon" :class="point.level">{{ levelIcon(point.level) }}</div>
            <div>
              <h4>{{ point.title }}</h4>
              <p>{{ point.detail }}</p>
              <div class="insight-evidence">依据：{{ point.evidence || '—' }}</div>
            </div>
          </div>
          <div class="foot-hint">
            分析时间 {{ insight.generatedAt }}｜模型 {{ insight.model }}
            <router-link to="/assistant">向助手追问 →</router-link>
          </div>
        </template>

        <div v-else class="empty">暂无洞察数据</div>
      </div>

      <!-- 分布图 -->
      <div class="card card-pad">
        <div class="card-title">
          <h3>院系学生分布</h3>
          <span class="hint">指标由 stats_overview 工具实时计算</span>
        </div>
        <EChart v-if="deptRows.length" :chart-meta="deptChart" :rows="deptRows" :height="196" />
        <div class="dist">
          <div v-for="item in deptRows" :key="item.name" class="dist-row">
            <span class="dist-name">{{ item.name }}</span>
            <div class="dist-bar"><i :style="{ width: barWidth(item.value, deptRows) }"></i></div>
            <span class="dist-value">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-2 mt-16">
      <!-- 班级平均分 -->
      <div class="card card-pad">
        <div class="card-title">
          <h3>班级平均分对比</h3>
          <span class="hint">analyze_scores · 维度=班级</span>
        </div>
        <EChart v-if="classRows.length" :chart-meta="classChart" :rows="classRows" :height="250" />
        <div v-else class="empty">暂无成绩数据</div>
      </div>

      <!-- 性别 / 风险 -->
      <div class="card card-pad">
        <div class="card-title">
          <h3>结构分布</h3>
          <span class="hint">性别口径已统一 COALESCE(gender, sex)</span>
        </div>
        <div class="half-grid">
          <div>
            <div class="sub-title">性别分布</div>
            <EChart v-if="genderRows.length" :chart-meta="genderChart" :rows="genderRows" :height="200" />
          </div>
          <div>
            <div class="sub-title">学业风险分布</div>
            <EChart v-if="riskRows.length" :chart-meta="riskChart" :rows="riskRows" :height="200" />
          </div>
        </div>
      </div>
    </div>

    <div class="card card-pad mt-16 data-quality">
      <div class="card-title">
        <h3>数据质量体检</h3>
        <span class="hint">AI 主动指出数据缺口，避免误判</span>
      </div>
      <div class="quality-row">
        <div class="quality-item">
          <span class="tag tag-info">成绩覆盖率</span>
          <b>{{ coverage }}%</b>
          <span>{{ withScore }}/{{ studentTotal }} 名学生有成绩记录</span>
        </div>
        <div class="quality-item">
          <span class="tag tag-warn">异常成绩</span>
          <b>{{ abnormalScore }}</b>
          <span>存在超过 100 分的记录，影响均值与排名</span>
        </div>
        <div class="quality-item">
          <span class="tag tag-warn">待审异动</span>
          <b>{{ pendingChange }}</b>
          <span>学籍异动申请待教务审核</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { aiInsight } from '@/api/ai'
import EChart from '@/components/EChart.vue'

const insight = ref(null)
const loading = ref(false)

const facts = computed(() => insight.value?.facts || {})
const metricOf = (label) => {
  const list = facts.value.metrics || []
  const hit = list.find((item) => item.label === label)
  return hit ? hit.value : 0
}
const riskMetricOf = (label) => {
  const list = facts.value.riskMetrics || []
  const hit = list.find((item) => item.label === label)
  return hit ? Number(hit.value) || 0 : 0
}

const studentTotal = computed(() => Number(facts.value.studentTotal) || 0)
const withScore = computed(() => Number(facts.value.studentsWithScore) || 0)
const pendingChange = computed(() => Number(facts.value.pendingChange) || 0)
const abnormalScore = computed(() => Number(facts.value.abnormalScore) || 0)
const coverage = computed(() =>
  studentTotal.value ? Math.round((withScore.value / studentTotal.value) * 1000) / 10 : 0
)

const kpis = computed(() => [
  { label: '在校学生', value: studentTotal.value, unit: '人', icon: '🎓' },
  { label: '班级', value: metricOf('班级数量'), unit: '个', icon: '🏫' },
  { label: '专业 / 院系', value: `${metricOf('专业数量')} / ${metricOf('院系数量')}`, unit: '', icon: '🧭' },
  { label: '成绩记录', value: metricOf('成绩记录'), unit: '条', icon: '📝' },
  { label: '高风险学生', value: riskMetricOf('高风险'), unit: '人', icon: '🚨', tone: 'tone-danger' },
  { label: '中风险学生', value: riskMetricOf('中风险'), unit: '人', icon: '⚠️', tone: 'tone-warn' },
  { label: '待审异动', value: pendingChange.value, unit: '条', icon: '🗂️', tone: 'tone-warn' },
  { label: '异常成绩', value: abnormalScore.value, unit: '条', icon: '🧯', tone: 'tone-danger' }
])

const deptRows = computed(() => facts.value.byDept || [])
const genderRows = computed(() => facts.value.byGender || [])
const classRows = computed(() =>
  (facts.value.classScores || []).filter((item) => item.avgScore !== null && item.avgScore !== undefined)
)
const riskRows = computed(() => {
  const list = facts.value.riskMetrics || []
  return list
    .filter((item) => ['高风险', '中风险', '低风险'].includes(item.label))
    .map((item) => ({ name: item.label, value: Number(item.value) || 0 }))
})

const deptChart = { type: 'bar', xKey: 'name', unit: '人', series: [{ key: 'value', name: '学生数' }] }
const genderChart = { type: 'pie', xKey: 'name', unit: '人', series: [{ key: 'value', name: '人数' }] }
const riskChart = { type: 'pie', xKey: 'name', unit: '人', series: [{ key: 'value', name: '人数' }] }
const classChart = {
  type: 'bar',
  xKey: 'name',
  unit: '分',
  series: [
    { key: 'avgScore', name: '平均分' },
    { key: 'passRate', name: '及格率(%)' }
  ]
}

const barWidth = (value, rows) => {
  const max = Math.max(...rows.map((item) => Number(item.value) || 0), 1)
  return `${Math.max(6, (Number(value) / max) * 100)}%`
}
const levelIcon = (level) => ({ info: 'ℹ️', warning: '⚠️', danger: '🚨' })[level] || 'ℹ️'

const loadInsight = async () => {
  loading.value = true
  try {
    insight.value = await aiInsight()
  } catch (e) {
    insight.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadInsight)
</script>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
@media (max-width: 1200px) { .kpi-row { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .kpi-row { grid-template-columns: 1fr; } }
.mt-16 { margin-top: 16px; }
.tone-warn { color: #b45309; }
.tone-danger { color: #dc2626; }
.head-right { display: flex; align-items: center; gap: 8px; }
.insight-card { display: flex; flex-direction: column; }
.overall {
  font-size: 13px; background: #f8f9ff; border: 1px solid #e6e9ff; color: var(--ink-2);
  border-radius: 12px; padding: 11px 13px; margin-bottom: 6px; line-height: 1.7;
}
.skeleton-stack { display: grid; gap: 10px; padding: 8px 0; }
.foot-hint { margin-top: 10px; font-size: 11.5px; color: var(--ink-4); display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.dist { margin-top: 12px; display: grid; gap: 6px; }
.dist-row { display: grid; grid-template-columns: 110px 1fr 40px; align-items: center; gap: 8px; font-size: 12.5px; }
.dist-name { color: var(--ink-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dist-bar { height: 7px; border-radius: 999px; background: #eef2ff; overflow: hidden; }
.dist-bar i { display: block; height: 100%; background: linear-gradient(90deg, #6366f1, #0ea5e9); border-radius: 999px; }
.dist-value { text-align: right; color: var(--ink-3); }
.half-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 900px) { .half-grid { grid-template-columns: 1fr; } }
.sub-title { font-size: 12.5px; color: var(--ink-3); font-weight: 600; margin-bottom: 4px; }
.quality-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 900px) { .quality-row { grid-template-columns: 1fr; } }
.quality-item {
  border: 1px solid var(--line); border-radius: 12px; padding: 12px 14px; background: #fcfdff;
  display: flex; flex-direction: column; gap: 5px;
}
.quality-item b { font-size: 21px; letter-spacing: -0.02em; }
.quality-item span:last-child { font-size: 12px; color: var(--ink-3); }
</style>
