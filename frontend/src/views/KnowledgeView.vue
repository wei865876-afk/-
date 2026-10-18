<template>
  <div class="kb-layout">
    <div class="kb-left">
      <div class="card card-pad">
        <div class="card-title">
          <h3>检索测试</h3>
          <span class="tag tag-muted">混合检索</span>
        </div>
        <div class="search-row">
          <input class="input" v-model="query" placeholder="输入制度问题，如：转专业需要什么条件" @keydown.enter="doSearch" />
          <button class="btn btn-primary" :disabled="searching" @click="doSearch">
            {{ searching ? '检索中…' : '检索' }}
          </button>
        </div>
        <div class="chat-hints mt-10">
          <span v-for="item in samples" :key="item" class="chat-hint" @click="useSample(item)">{{ item }}</span>
        </div>

        <div v-if="hits.length" class="hit-list">
          <div class="hit-note">
            命中 {{ hits.length }} 个片段，按相关度排序（中文分词 + IDF 关键词检索，配置向量模型后自动融合语义相似度）
          </div>
          <div v-for="(hit, index) in hits" :key="index" class="hit">
            <div class="hit-head">
              <span class="citation-index">{{ index + 1 }}</span>
              <b>《{{ hit.title }}》</b>
              <span class="tag tag-muted">{{ hit.category }}</span>
              <span class="score">相关度 {{ hit.score }}</span>
            </div>
            <div class="hit-body">{{ hit.content }}</div>
            <div class="hit-foot">
              来源：{{ hit.source || '校内制度文件' }}
              <router-link :to="`/assistant?q=${encodeURIComponent(hit.title)}`" @click.prevent="ask(hit.title)">
                基于该制度追问 →
              </router-link>
            </div>
          </div>
        </div>
        <div v-else-if="searched" class="empty">未命中任何片段，可换个说法或补充知识库文档</div>
      </div>

      <div class="card card-pad mt-16">
        <div class="card-title">
          <h3>知识库文档</h3>
          <div class="head-right">
            <span class="hint">{{ count }} 篇 / {{ chunks }} 片段</span>
            <button class="btn btn-sm" @click="startCreate">新建</button>
          </div>
        </div>
        <div class="doc-list">
          <div
            v-for="doc in docs"
            :key="doc.id"
            class="doc-item"
            :class="{ active: doc.id === editing.id }"
            @click="openDoc(doc.id)"
          >
            <div class="doc-title">{{ doc.title }}</div>
            <div class="doc-sub">
              <span class="tag tag-muted">{{ doc.category || '未分类' }}</span>
              <span>{{ doc.chunk_count || 0 }} 片段</span>
              <span class="doc-del" @click.stop="removeDoc(doc)">删除</span>
            </div>
          </div>
          <div v-if="!docs.length" class="empty">知识库暂为空，可点击"新建"或"恢复示例语料"</div>
        </div>
      </div>
    </div>

    <div class="kb-right">
      <div class="card card-pad">
        <div class="card-title">
          <h3>{{ editing.id ? '编辑文档 #' + editing.id : '新增制度文档' }}</h3>
          <div class="head-right">
            <span v-if="embeddingEnabled" class="tag tag-ok">向量检索已开启</span>
            <span v-else class="tag tag-warn">关键词检索</span>
            <button class="btn btn-sm" @click="reindex">重建索引</button>
            <button class="btn btn-sm" @click="reset">恢复示例语料</button>
          </div>
        </div>
        <div class="form-grid">
          <div class="field">
            <label>标题</label>
            <input class="input" v-model="editing.title" placeholder="如：本科生转专业管理办法" />
          </div>
          <div class="field">
            <label>分类</label>
            <input class="input" v-model="editing.category" placeholder="如：学籍管理" />
          </div>
          <div class="field">
            <label>标签（逗号分隔）</label>
            <input class="input" v-model="editing.tags" placeholder="如：转专业,专业调整" />
          </div>
          <div class="field">
            <label>来源</label>
            <input class="input" v-model="editing.source" placeholder="如：校教〔2024〕12 号" />
          </div>
        </div>
        <div class="field">
          <label>正文（空行分段，分段越清晰检索越准）</label>
          <textarea class="textarea" rows="16" v-model="editing.content" placeholder="粘贴制度原文…"></textarea>
        </div>
        <div class="row-between">
          <span class="hint">保存后自动切分片段并重建该文档索引，立即参与问答检索</span>
          <div class="head-right">
            <button v-if="editing.id" class="btn btn-sm" @click="startCreate">取消编辑</button>
            <button class="btn btn-primary" :disabled="saving" @click="save">
              {{ saving ? '保存中…' : editing.id ? '保存修改' : '创建文档' }}
            </button>
          </div>
        </div>
      </div>

      <div class="card card-pad mt-16">
        <div class="card-title">
          <h3>RAG 工作原理</h3>
          <span class="hint">让制度问答可溯源、不编造</span>
        </div>
        <div class="flow">
          <div class="flow-step"><b>① 入库</b>制度原文按空行/句号切分为片段，写入 ai_knowledge_chunk；配置向量模型时同步生成 embedding</div>
          <div class="flow-step"><b>② 召回</b>问题 → 中文二元分词 → 关键词打分（TF-IDF 思路）；若有向量则与余弦相似度加权融合</div>
          <div class="flow-step"><b>③ 重排</b>按融合得分排序取 Top-K，作为"制度依据"注入提示词，并随回答一起返回出处</div>
          <div class="flow-step"><b>④ 生成</b>模型只依据召回片段作答；知识库无对应条目时明确说明"暂无该规定"，避免虚构条款</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  aiKnowledgeDelete,
  aiKnowledgeDetail,
  aiKnowledgeList,
  aiKnowledgeReindex,
  aiKnowledgeReset,
  aiKnowledgeSave,
  aiKnowledgeSearch
} from '@/api/ai'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const chat = useChatStore()

const docs = ref([])
const count = ref(0)
const chunks = ref(0)
const embeddingEnabled = ref(false)

const query = ref('')
const hits = ref([])
const searching = ref(false)
const searched = ref(false)
const saving = ref(false)

const editing = reactive({ id: null, title: '', category: '', tags: '', source: '', content: '' })

const samples = ['转专业需要什么条件', '学业预警怎么处理', '奖学金评定标准', '休学与复学流程', '考试违纪如何处分']

const load = async () => {
  try {
    const data = await aiKnowledgeList()
    docs.value = data?.list || []
    count.value = data?.count ?? docs.value.length
    chunks.value = data?.chunks ?? 0
    embeddingEnabled.value = !!data?.embeddingEnabled
  } catch (e) {
    docs.value = []
  }
}

const doSearch = async () => {
  const text = query.value.trim()
  if (!text) return
  searching.value = true
  try {
    const data = await aiKnowledgeSearch(text, 4)
    hits.value = data?.hits || []
  } catch (e) {
    hits.value = []
  } finally {
    searching.value = false
    searched.value = true
  }
}

const useSample = (text) => {
  query.value = text
  doSearch()
}

const openDoc = async (id) => {
  try {
    const detail = await aiKnowledgeDetail(id)
    Object.assign(editing, {
      id: detail?.id ?? id,
      title: detail?.title || '',
      category: detail?.category || '',
      tags: detail?.tags || '',
      source: detail?.source || '',
      content: detail?.content || ''
    })
  } catch (e) {
    /* 忽略 */
  }
}

const startCreate = () => {
  Object.assign(editing, { id: null, title: '', category: '', tags: '', source: '', content: '' })
}

const save = async () => {
  if (!editing.title.trim() || !editing.content.trim()) return
  saving.value = true
  try {
    const payload = { ...editing }
    if (!payload.id) delete payload.id
    const result = await aiKnowledgeSave(payload)
    if (result?.success) {
      await load()
      await openDoc(result.id)
      await doSearch()
    }
  } finally {
    saving.value = false
  }
}

const removeDoc = async (doc) => {
  await aiKnowledgeDelete(doc.id)
  if (editing.id === doc.id) startCreate()
  await load()
}

const reindex = async () => {
  await aiKnowledgeReindex()
  await load()
}

const reset = async () => {
  await aiKnowledgeReset()
  await load()
}

const ask = (title) => {
  chat.send(`根据《${title}》，请说明具体规定与办理流程`, 'knowledge')
  router.push('/assistant')
}

onMounted(async () => {
  await load()
  if (docs.value.length) {
    await openDoc(docs.value[0].id)
  }
  query.value = '转专业需要什么条件'
  await doSearch()
})
</script>

<style scoped>
.kb-layout { display: grid; grid-template-columns: 420px minmax(0, 1fr); gap: 16px; align-items: start; }
@media (max-width: 1100px) { .kb-layout { grid-template-columns: 1fr; } }
.mt-10 { margin-top: 10px; }
.mt-16 { margin-top: 16px; }
.hint { font-size: 12px; color: var(--ink-3); }
.head-right { display: flex; align-items: center; gap: 8px; }
.row-between { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.search-row { display: flex; gap: 8px; }
.hit-list { margin-top: 14px; }
.hit-note { font-size: 11.5px; color: var(--ink-4); margin-bottom: 8px; }
.hit { border: 1px solid #ede9fe; background: #faf9ff; border-radius: 12px; padding: 11px 13px; margin-bottom: 9px; }
.hit-head { display: flex; align-items: center; gap: 7px; font-size: 12.8px; color: #5b21b6; }
.citation-index {
  width: 18px; height: 18px; border-radius: 6px; background: #ede9fe; color: #6d28d9;
  display: grid; place-items: center; font-size: 11px;
}
.score { margin-left: auto; font-size: 11.5px; color: var(--ink-4); font-weight: 400; }
.hit-body { font-size: 12.5px; color: var(--ink-2); margin-top: 6px; line-height: 1.75; max-height: 96px; overflow: auto; }
.hit-foot { margin-top: 6px; font-size: 11.5px; color: var(--ink-4); display: flex; justify-content: space-between; gap: 8px; }
.doc-list { max-height: 320px; overflow: auto; }
.doc-item { padding: 9px 10px; border-radius: 11px; cursor: pointer; border: 1px solid transparent; }
.doc-item:hover { background: #f7f8ff; }
.doc-item.active { background: #eef2ff; border-color: #dbe3ff; }
.doc-title { font-size: 13.3px; font-weight: 600; }
.doc-sub { display: flex; align-items: center; gap: 8px; font-size: 11.5px; color: var(--ink-4); margin-top: 3px; }
.doc-del { margin-left: auto; color: var(--danger); cursor: pointer; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 820px) { .form-grid { grid-template-columns: 1fr; } }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12.3px; color: var(--ink-3); margin-bottom: 5px; }
.flow { display: grid; gap: 9px; }
.flow-step {
  font-size: 12.6px; color: var(--ink-2); background: #f8fafc; border: 1px solid var(--line);
  border-radius: 11px; padding: 10px 12px; line-height: 1.75;
}
.flow-step b { color: var(--brand-1); margin-right: 6px; }
</style>
