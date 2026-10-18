<template>
  <div class="student-layout">
    <div class="list-pane">
      <div class="card card-pad">
        <div class="card-title">
          <h3>学生名录</h3>
          <span class="hint">共 {{ rows.length }} 人</span>
        </div>
        <div class="search-row">
          <input
            class="input"
            v-model="form.keyword"
            placeholder="姓名 / 学号关键字"
            @keydown.enter="search"
          />
          <button class="btn btn-primary" :disabled="loading" @click="search">
            {{ loading ? '查询中…' : '查询' }}
          </button>
        </div>
        <div class="filter-row">
          <select class="select" v-model="form.className">
            <option value="">全部班级</option>
            <option v-for="item in options.classNames" :key="item" :value="item">{{ item }}</option>
          </select>
          <select class="select" v-model="form.major">
            <option value="">全部专业</option>
            <option v-for="item in options.majors" :key="item" :value="item">{{ item }}</option>
          </select>
          <select class="select" v-model="form.status">
            <option value="">全部状态</option>
            <option v-for="item in options.statuses" :key="item" :value="item">{{ item }}</option>
          </select>
          <select class="select" v-model="form.gender">
            <option value="">全部性别</option>
            <option value="男">男</option>
            <option value="女">女</option>
          </select>
        </div>

        <div class="student-list">
          <div
            v-for="row in rows"
            :key="row.stuId"
            class="student-item"
            :class="{ active: row.stuId === activeId }"
            @click="openProfile(row.stuId)"
          >
            <div class="stu-avatar">{{ (row.stuName || '?').slice(0, 1) }}</div>
            <div class="stu-main">
              <div class="stu-name">
                {{ row.stuName }}
                <span class="stu-id">{{ row.stuId }}</span>
              </div>
              <div class="stu-sub">{{ row.className }} · {{ row.major }}</div>
            </div>
            <span class="tag" :class="row.status === '在读' || row.status === '在校' ? 'tag-ok' : 'tag-muted'">
              {{ row.status || '未知' }}
            </span>
          </div>
          <div v-if="!rows.length && !loading" class="empty">未查询到符合条件的学生</div>
        </div>
      </div>
    </div>

    <div class="detail-pane">
      <div v-if="!activeId" class="card card-pad placeholder">
        <div class="placeholder-icon">🧑‍🎓</div>
        <h3>选择一名学生，查看 AI 全景画像</h3>
        <p>
          画像数据来自 get_student_profile 工具（学籍 + 成绩 + 奖惩 + 异动 + 档案），
          由 AI 生成学业状态判断与帮扶建议，数据缺失时会被明确指出。
        </p>
      </div>

      <template v-else>
        <div class="card card-pad">
          <div class="card-title">
            <h3>AI 画像点评</h3>
            <div class="head-right">
              <span v-if="portrait?.riskLevel" class="tag" :class="levelClass(portrait.riskLevel)">
                {{ levelText(portrait.riskLevel) }}
              </span>
              <span class="tag" :class="portrait?.mode === 'remote' ? 'tag-ok' : 'tag-warn'">
                {{ portrait?.mode === 'remote' ? '大模型生成' : '规则引擎生成' }}
              </span>
              <button class="btn btn-sm" :disabled="loadingProfile" @click="loadProfile(activeId)">
                {{ loadingProfile ? '生成中…' : '重新生成' }}
              </button>
            </div>
          </div>
          <div v-if="loadingProfile && !portrait" class="skeleton-stack">
            <div class="skeleton" style="width: 70%"></div>
            <div class="skeleton" style="width: 92%"></div>
          </div>
          <template v-else-if="portrait">
            <div class="comment">{{ portrait.comment }}</div>
            <ul v-if="portrait.suggestions?.length" class="suggestions">
              <li v-for="(item, index) in portrait.suggestions" :key="index">{{ item }}</li>
            </ul>
            <div class="foot-hint">
              生成时间 {{ portrait.generatedAt }}｜模型 {{ portrait.model }}
              <button class="btn btn-sm btn-ghost" @click="ask(`请针对${studentName}（${activeId}）的画像给出本学期帮扶计划`)">
                在助手中继续追问 →
              </button>
            </div>
          </template>
        </div>

        <div class="card card-pad mt-16">
          <div class="card-title">
            <h3>全景档案</h3>
            <span class="hint">get_student_profile 工具原始数据</span>
          </div>
          <ToolResultView v-if="portrait?.profile" :step="portrait.profile" />
          <div v-else class="empty">暂无画像数据</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiStudents, aiStudentProfile } from '@/api/ai'
import { useChatStore } from '@/stores/chat'
import ToolResultView from '@/components/ToolResultView.vue'

const route = useRoute()
const router = useRouter()
const chat = useChatStore()

const form = reactive({ keyword: '', className: '', major: '', status: '', gender: '' })
const rows = ref([])
const loading = ref(false)
const loadingProfile = ref(false)
const portrait = ref(null)
const activeId = ref('')
const studentName = ref('')
const options = reactive({ classNames: [], majors: [], statuses: [] })

const levelText = (value) => ({ high: '高风险', medium: '中风险', low: '低风险' })[value] || value
const levelClass = (value) => ({ high: 'tag-danger', medium: 'tag-warn', low: 'tag-ok' })[value] || 'tag-muted'

/** 从全量名录中提取筛选下拉选项（并发一次全量查询，避免多次请求） */
const loadOptions = async () => {
  try {
    const result = await aiStudents({ limit: 200 })
    const list = result?.data?.rows || []
    options.classNames = [...new Set(list.map((item) => item.className).filter((v) => v && v !== '-'))]
    options.majors = [...new Set(list.map((item) => item.major).filter((v) => v && v !== '-'))]
    options.statuses = [...new Set(list.map((item) => item.status).filter(Boolean))]
  } catch (e) {
    /* 忽略筛选项加载失败 */
  }
}

const search = async () => {
  loading.value = true
  try {
    const payload = { limit: 200 }
    Object.entries(form).forEach(([key, value]) => {
      if (value) payload[key] = value
    })
    const result = await aiStudents(payload)
    rows.value = result?.data?.rows || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

const loadProfile = async (stuId) => {
  loadingProfile.value = true
  try {
    portrait.value = await aiStudentProfile({ stuId })
  } catch (e) {
    portrait.value = null
  } finally {
    loadingProfile.value = false
  }
}

const openProfile = async (stuId) => {
  activeId.value = stuId
  const hit = rows.value.find((item) => item.stuId === stuId)
  studentName.value = hit?.stuName || stuId
  portrait.value = null
  await loadProfile(stuId)
}

const ask = (text) => {
  chat.send(text, 'assistant')
  router.push('/assistant')
}

onMounted(async () => {
  await Promise.all([loadOptions(), search()])
  const target = route.query.stuId
  if (target) {
    await openProfile(String(target))
  } else if (rows.value.length) {
    await openProfile(rows.value[0].stuId)
  }
})
</script>

<style scoped>
.student-layout { display: grid; grid-template-columns: 352px minmax(0, 1fr); gap: 16px; align-items: start; }
@media (max-width: 1100px) { .student-layout { grid-template-columns: 1fr; } }
.mt-16 { margin-top: 16px; }
.search-row { display: flex; gap: 8px; }
.filter-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 9px; }
.filter-row .select { font-size: 12.5px; padding: 7px 9px; }
.student-list { margin-top: 12px; max-height: 62vh; overflow: auto; }
.student-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 12px;
  cursor: pointer; border: 1px solid transparent; transition: all 0.15s;
}
.student-item:hover { background: #f7f8ff; }
.student-item.active { background: #eef2ff; border-color: #dbe3ff; }
.stu-avatar {
  width: 34px; height: 34px; flex: 0 0 34px; border-radius: 11px; display: grid; place-items: center;
  background: var(--brand-grad); color: #fff; font-weight: 700; font-size: 14px;
}
.stu-main { flex: 1; min-width: 0; }
.stu-name { font-size: 13.5px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.stu-id { font-size: 11.5px; color: var(--ink-4); font-weight: 400; }
.stu-sub { font-size: 12px; color: var(--ink-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.placeholder { text-align: center; padding: 60px 30px; color: var(--ink-3); }
.placeholder-icon { font-size: 38px; margin-bottom: 10px; }
.placeholder h3 { font-size: 15px; margin-bottom: 8px; }
.placeholder p { font-size: 12.8px; max-width: 460px; margin: 0 auto; }
.head-right { display: flex; align-items: center; gap: 8px; }
.comment { font-size: 13.5px; line-height: 1.85; color: var(--ink-1); }
.suggestions { margin: 12px 0 0; padding-left: 20px; font-size: 12.8px; color: var(--ink-2); }
.suggestions li { margin-bottom: 5px; }
.skeleton-stack { display: grid; gap: 10px; }
.foot-hint { margin-top: 12px; font-size: 11.5px; color: var(--ink-4); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
