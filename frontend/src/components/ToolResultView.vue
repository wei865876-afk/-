<template>
  <div class="tool-result">
    <!-- 表格 / SQL 结果 -->
    <template v-if="render === 'table' || render === 'sql'">
      <div v-if="render === 'sql' && data?.sql" class="sql-block">
        <div class="sql-head">
          <span class="tag tag-info">AI 生成的只读 SQL</span>
          <span v-if="data.purpose" class="sql-purpose">{{ data.purpose }}</span>
          <button class="btn btn-sm btn-ghost" @click="copySql">复制</button>
        </div>
        <pre class="sql-code">{{ data.sql }}</pre>
      </div>
      <div v-if="columns.length" class="table-wrap" :style="{ maxHeight: '320px' }">
        <table class="data">
          <thead>
            <tr><th v-for="col in visibleColumns" :key="col">{{ colLabel(col) }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in visibleRows" :key="index">
              <td v-for="col in visibleColumns" :key="col">{{ formatCell(row[col]) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="rows.length > visibleRows.length" class="more-hint">
        共 {{ rows.length }} 行，已展示前 {{ visibleRows.length }} 行
        <button class="btn btn-sm" @click="showAll = true">展开全部</button>
      </div>
      <EChart
        v-if="chartMeta"
        class="mt-12"
        :chart-meta="chartMeta"
        :rows="rows"
        :fallback-rows="chartFallback"
        :height="240"
      />
    </template>

    <!-- 指标卡 -->
    <template v-else-if="render === 'metrics'">
      <div class="metric-grid">
        <div v-for="(item, index) in metrics" :key="index" class="metric">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></div>
        </div>
      </div>
      <EChart
        v-if="chartMeta"
        class="mt-12"
        :chart-meta="chartMeta"
        :rows="distributionRows"
        :height="230"
      />
      <div v-if="distributionRows.length" class="dist">
        <div v-for="(item, index) in distributionRows.slice(0, 6)" :key="index" class="dist-row">
          <span class="dist-name">{{ item.name }}</span>
          <div class="dist-bar"><i :style="{ width: barWidth(item.value) }"></i></div>
          <span class="dist-value">{{ item.value }}</span>
        </div>
      </div>
    </template>

    <!-- 学生画像 -->
    <template v-else-if="render === 'profile'">
      <div class="profile-head">
        <div class="avatar">{{ (data?.basic?.stuName || '学').slice(0, 1) }}</div>
        <div>
          <div class="profile-name">
            {{ data?.basic?.stuName }}
            <span class="tag tag-muted">{{ data?.basic?.stuId }}</span>
            <span class="tag" :class="data?.basic?.status === '在读' || data?.basic?.status === '在校' ? 'tag-ok' : 'tag-warn'">
              {{ data?.basic?.status || '未知' }}
            </span>
          </div>
          <div class="profile-sub">
            {{ data?.basic?.dept || '-' }} · {{ data?.basic?.major || '-' }} · {{ data?.basic?.className || '未分班' }}
            <span v-if="data?.basic?.advisor">｜辅导员 {{ data.basic.advisor }}</span>
          </div>
        </div>
      </div>
      <div class="metric-grid compact">
        <div v-for="(item, index) in scoreSummary" :key="index" class="metric">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></div>
        </div>
      </div>
      <div v-if="data?.scores?.length" class="sub-block">
        <div class="sub-title">成绩明细</div>
        <div class="table-wrap" style="max-height: 220px">
          <table class="data">
            <thead><tr><th>课程</th><th>成绩</th><th>学年</th></tr></thead>
            <tbody>
              <tr v-for="(score, index) in data.scores" :key="index">
                <td>{{ score.course_name }}</td>
                <td>
                  <span :class="Number(score.score) < 60 ? 'text-danger' : 'text-strong'">{{ score.score }}</span>
                </td>
                <td>{{ score.school_year || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-if="data?.rewards?.length" class="sub-block">
        <div class="sub-title">奖惩记录</div>
        <div v-for="(item, index) in data.rewards" :key="index" class="line-item">
          <span class="tag" :class="item.rp_type === '奖励' ? 'tag-ok' : 'tag-danger'">{{ item.rp_type }}</span>
          <span>{{ item.level_name || item.reason || '-' }}</span>
          <span class="muted">{{ item.rp_date }}</span>
        </div>
      </div>
      <div v-if="data?.changes?.length" class="sub-block">
        <div class="sub-title">学籍异动</div>
        <div v-for="(item, index) in data.changes" :key="index" class="line-item">
          <span class="tag tag-info">{{ item.change_type }}</span>
          <span>{{ item.remark || '-' }}</span>
          <span class="muted">{{ item.status }}｜{{ item.submit_time }}</span>
        </div>
      </div>
    </template>

    <!-- 学业预警 -->
    <template v-else-if="render === 'warning'">
      <div class="metric-grid compact">
        <div v-for="(item, index) in data?.metrics || []" :key="index" class="metric">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}<small v-if="item.unit">{{ item.unit }}</small></div>
        </div>
      </div>
      <div class="table-wrap mt-12" style="max-height: 300px">
        <table class="data">
          <thead>
            <tr><th>学号</th><th>姓名</th><th>班级</th><th>平均分</th><th>不及格</th><th>风险</th><th>原因</th><th>建议</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in (data?.rows || []).slice(0, 20)" :key="item.stuId">
              <td>{{ item.stuId }}</td>
              <td class="text-strong">{{ item.stuName }}</td>
              <td>{{ item.className }}</td>
              <td>{{ item.avgScore }}</td>
              <td>{{ item.failCount }}</td>
              <td><span class="tag" :class="levelClass(item.riskLevel)">{{ levelText(item.riskLevel) }}</span></td>
              <td :title="item.reasons">{{ item.reasons }}</td>
              <td :title="item.suggestion">{{ item.suggestion }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- 知识库引用 -->
    <template v-else-if="render === 'citations'">
      <div class="citation-list">
        <div v-for="(item, index) in data?.citations || []" :key="index" class="citation-card">
          <div class="citation-title">
            <span class="citation-index">{{ index + 1 }}</span>
            《{{ item.title }}》
            <span class="tag tag-muted">{{ item.category }}</span>
            <span class="score">相关度 {{ item.score }}</span>
          </div>
          <div class="citation-body">{{ item.snippet }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import EChart from '@/components/EChart.vue'

const props = defineProps({
  step: { type: Object, required: true }
})

const showAll = ref(false)

const render = computed(() => props.step.render || 'text')
const data = computed(() => props.step.data || {})
const chartMeta = computed(() => props.step.chart || null)

const columns = computed(() => {
  const fromData = data.value?.columns
  if (Array.isArray(fromData) && fromData.length) return fromData
  const first = rows.value[0]
  return first ? Object.keys(first) : []
})

const rows = computed(() => (Array.isArray(data.value?.rows) ? data.value.rows : []))

const visibleRows = computed(() => (showAll.value ? rows.value : rows.value.slice(0, 12)))
const visibleColumns = computed(() => columns.value.slice(0, 10))

const metrics = computed(() => {
  if (render.value === 'metrics') {
    if (Array.isArray(data.value?.metrics)) return data.value.metrics
  }
  return []
})

const scoreSummary = computed(() => data.value?.scoreSummary || [])

const distributionRows = computed(() => {
  const candidates = [data.value?.byDept, data.value?.byClass, data.value?.byMajor, data.value?.byGender]
  for (const candidate of candidates) {
    if (Array.isArray(candidate) && candidate.length) return candidate
  }
  return []
})

const chartFallback = computed(() => chartMeta.value?.staticData || [])

const columnLabels = {
  stuId: '学号',
  stuName: '姓名',
  gender: '性别',
  className: '班级',
  major: '专业',
  dept: '院系',
  status: '状态',
  entryDate: '入学日期',
  type: '类型',
  levelName: '等级',
  amount: '金额',
  reason: '事由',
  date: '日期',
  name: '名称',
  avgScore: '平均分',
  passRate: '及格率(%)',
  excellentRate: '优秀率(%)',
  maxScore: '最高分',
  minScore: '最低分',
  records: '条数',
  applyId: '申请号',
  changeType: '异动类型',
  remark: '备注',
  submitTime: '提交时间'
}

const colLabel = (col) => columnLabels[col] || col

const formatCell = (value) => {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'number') return Number.isInteger(value) ? value : Number(value.toFixed(2))
  const text = String(value)
  return text.length > 40 ? text.slice(0, 40) + '…' : text
}

const barWidth = (value) => {
  const max = Math.max(...distributionRows.value.map((item) => Number(item.value) || 0), 1)
  return `${Math.max(6, (Number(value) / max) * 100)}%`
}

const levelText = (level) => ({ high: '高风险', medium: '中风险', low: '低风险' })[level] || level || '-'
const levelClass = (level) => ({ high: 'tag-danger', medium: 'tag-warn', low: 'tag-ok' })[level] || 'tag-muted'

const copySql = async () => {
  try {
    await navigator.clipboard.writeText(data.value?.sql || '')
  } catch (e) {
    /* 剪贴板不可用 */
  }
}
</script>

<style scoped>
.mt-12 { margin-top: 12px; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(118px, 1fr)); gap: 10px; }
.metric-grid.compact { grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); }
.metric { background: #f8fafc; border: 1px solid var(--line); border-radius: 11px; padding: 9px 12px; }
.metric-label { font-size: 11.5px; color: var(--ink-3); }
.metric-value { font-size: 18px; font-weight: 700; letter-spacing: -0.02em; }
.metric-value small { font-size: 11px; color: var(--ink-3); margin-left: 3px; font-weight: 500; }
.dist { margin-top: 12px; display: grid; gap: 6px; }
.dist-row { display: grid; grid-template-columns: 92px 1fr 40px; align-items: center; gap: 8px; font-size: 12.5px; }
.dist-name { color: var(--ink-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dist-bar { height: 7px; border-radius: 999px; background: #eef2ff; overflow: hidden; }
.dist-bar i { display: block; height: 100%; background: linear-gradient(90deg, #6366f1, #0ea5e9); border-radius: 999px; }
.dist-value { text-align: right; color: var(--ink-3); }
.more-hint { font-size: 12px; color: var(--ink-3); margin-top: 8px; display: flex; align-items: center; gap: 8px; }
.sql-block { margin-bottom: 10px; }
.sql-head { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.sql-purpose { font-size: 12px; color: var(--ink-3); flex: 1; }
.sql-code {
  background: #0f172a; color: #a5f3fc; padding: 12px 14px; border-radius: 10px; margin: 0;
  font-size: 12.3px; line-height: 1.6; overflow: auto; font-family: Consolas, Monaco, monospace;
}
.profile-head { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.avatar {
  width: 44px; height: 44px; border-radius: 13px; background: var(--brand-grad); color: #fff;
  display: grid; place-items: center; font-size: 18px; font-weight: 700;
}
.profile-name { font-size: 15px; font-weight: 650; display: flex; align-items: center; gap: 6px; }
.profile-sub { font-size: 12.5px; color: var(--ink-3); }
.sub-block { margin-top: 14px; }
.sub-title { font-size: 12.5px; color: var(--ink-3); margin-bottom: 6px; font-weight: 600; }
.line-item { display: flex; align-items: center; gap: 8px; font-size: 12.5px; padding: 4px 0; }
.muted { color: var(--ink-4); font-size: 12px; margin-left: auto; }
.text-danger { color: var(--danger); font-weight: 600; }
.text-strong { font-weight: 600; color: var(--ink-1); }
.citation-list { display: grid; gap: 10px; }
.citation-card { border: 1px solid #ede9fe; background: #faf9ff; border-radius: 12px; padding: 11px 13px; }
.citation-title { display: flex; align-items: center; gap: 7px; font-size: 12.8px; font-weight: 600; color: #5b21b6; }
.citation-index {
  width: 18px; height: 18px; border-radius: 6px; background: #ede9fe; color: #6d28d9;
  display: grid; place-items: center; font-size: 11px;
}
.citation-body { font-size: 12.5px; color: var(--ink-2); margin-top: 6px; line-height: 1.7; }
.score { margin-left: auto; font-size: 11.5px; color: var(--ink-4); font-weight: 400; }
</style>
