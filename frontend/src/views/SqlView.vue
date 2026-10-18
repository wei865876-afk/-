<template>
  <div>
    <div class="grid grid-2">
      <div class="card card-pad">
        <div class="card-title">
          <h3>用一句话描述你的数据需求</h3>
          <span class="tag" :class="status?.mode === 'remote' ? 'tag-ok' : 'tag-warn'">
            {{ status?.mode === 'remote' ? '大模型理解' : '本地模板匹配' }}
          </span>
        </div>
        <textarea
          class="textarea"
          rows="3"
          v-model="question"
          placeholder="例如：统计各院系的学生人数 / 各班平均分和及格率 / 有哪些奖励记录 / 还有多少待审核的学籍异动"
          @keydown.enter.exact.prevent="run"
        ></textarea>
        <div class="chat-hints mt-10">
          <span v-for="item in examples" :key="item" class="chat-hint" @click="useExample(item)">{{ item }}</span>
        </div>
        <div class="row-between mt-12">
          <span class="hint">
            {{ status?.mode === 'remote' ? '由大模型生成 SQL 并通过安全校验后执行' : '未配置密钥时由本地模板引擎兜底，仍可完成常用取数' }}
          </span>
          <button class="btn btn-primary" :disabled="loading || !question.trim()" @click="run">
            {{ loading ? '生成中…' : '生成并执行' }}
          </button>
        </div>
      </div>

      <div class="card card-pad guard-card">
        <div class="card-title">
          <h3>安全护栏</h3>
          <span class="hint">NL2SQL 的执行边界</span>
        </div>
        <div class="guard-item"><span class="guard-idx">1</span>只允许单条 <code>SELECT / WITH</code> 查询，写操作、DDL、系统表一律拒绝</div>
        <div class="guard-item"><span class="guard-idx">2</span>表名白名单：12 张业务表 / 视图（student、score、class、reward_punish、change_apply …）</div>
        <div class="guard-item"><span class="guard-idx">3</span>自动包裹 <code>LIMIT 200</code> 限制返回行数，并设置 8 秒查询超时</div>
        <div class="guard-item"><span class="guard-idx">4</span>使用只读数据库连接执行，无法修改任何数据</div>
        <div class="guard-item"><span class="guard-idx">5</span>生成的 SQL 全文展示给用户，统计口径可复核</div>
      </div>
    </div>

    <div v-if="error" class="card card-pad mt-16 error-card">⚠ {{ error }}</div>

    <div v-if="answer?.success" class="card card-pad mt-16">
      <div class="card-title">
        <div>
          <h3>执行结果</h3>
          <div class="hint">
            {{ answer.question }}｜共 {{ answer.total }} 行｜耗时 {{ answer.elapsedMs }} ms
            <span v-if="answer.truncated" class="tag tag-warn" style="margin-left: 6px">结果已截断</span>
          </div>
        </div>
        <div class="head-right">
          <span v-if="answer.explanation" class="tag tag-muted">{{ answer.explanation }}</span>
          <button class="btn btn-sm" @click="askInAssistant">在助手中继续分析</button>
        </div>
      </div>
      <ToolResultView :step="step" />
    </div>

    <div v-else-if="answer && answer.sql" class="card card-pad mt-16">
      <div class="card-title"><h3>生成的 SQL 未通过校验</h3></div>
      <pre class="sql-code">{{ answer.sql }}</pre>
      <div class="error-text">{{ answer.message }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { aiSql, aiStatus } from '@/api/ai'
import { useChatStore } from '@/stores/chat'
import ToolResultView from '@/components/ToolResultView.vue'

const router = useRouter()
const chat = useChatStore()

const question = ref('统计各院系的学生人数')
const loading = ref(false)
const answer = ref(null)
const error = ref('')
const status = ref(null)

const examples = [
  '统计各院系的学生人数',
  '各班平均分和及格率',
  '按课程统计平均分',
  '男女比例是多少',
  '有哪些奖励记录',
  '还有多少待审核的学籍异动'
]

/** 自动推断图表：两列且第二列为数值时画柱状图 */
const inferredChart = computed(() => {
  const rows = answer.value?.rows || []
  const columns = answer.value?.columns || []
  if (rows.length < 2 || columns.length < 2 || columns.length > 3) return null
  const valueKey = columns[columns.length - 1]
  const numeric = rows.every((row) => row[valueKey] === null || !Number.isNaN(Number(row[valueKey])))
  if (!numeric) return null
  return {
    type: 'bar',
    xKey: columns[0],
    unit: '',
    series: [{ key: valueKey, name: valueKey }]
  }
})

const step = computed(() => ({
  render: 'sql',
  data: {
    sql: answer.value?.sql,
    purpose: answer.value?.explanation,
    columns: answer.value?.columns || [],
    rows: answer.value?.rows || []
  },
  chart: inferredChart.value
}))

const useExample = (text) => {
  question.value = text
  run()
}

const run = async () => {
  const text = question.value.trim()
  if (!text || loading.value) return
  loading.value = true
  error.value = ''
  answer.value = null
  try {
    const result = await aiSql(text)
    if (result?.success) {
      answer.value = result
    } else {
      answer.value = result
      error.value = result?.message || '未能生成可执行的查询'
    }
  } catch (e) {
    error.value = e.message || String(e)
  } finally {
    loading.value = false
  }
}

const askInAssistant = () => {
  chat.send(`${question.value}（请给出统计口径说明与结论）`, 'sql')
  router.push('/assistant')
}

onMounted(async () => {
  try {
    status.value = await aiStatus()
  } catch (e) {
    status.value = null
  }
})
</script>

<style scoped>
.mt-10 { margin-top: 10px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.hint { font-size: 12px; color: var(--ink-3); }
.row-between { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.head-right { display: flex; align-items: center; gap: 8px; }
.guard-card .guard-item { display: flex; gap: 9px; font-size: 12.6px; color: var(--ink-2); padding: 6px 0; line-height: 1.7; }
.guard-idx {
  width: 18px; height: 18px; flex: 0 0 18px; margin-top: 3px; border-radius: 6px; background: #eef2ff;
  color: var(--brand-1); font-size: 11px; display: grid; place-items: center; font-weight: 600;
}
code { background: #f1f5f9; padding: 1px 5px; border-radius: 5px; font-size: 12px; color: #7c3aed; }
.error-card { color: var(--danger); font-size: 13px; }
.error-text { color: var(--danger); font-size: 12.8px; margin-top: 8px; }
.sql-code {
  background: #0f172a; color: #a5f3fc; padding: 12px 14px; border-radius: 10px; margin: 0;
  font-size: 12.3px; line-height: 1.6; overflow: auto; font-family: Consolas, Monaco, monospace;
}
</style>
