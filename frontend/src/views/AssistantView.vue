<template>
  <div class="chat-layout">
    <div class="chat-main">
      <div class="chat-scroll" ref="scrollEl">
        <!-- 空态：能力引导 -->
        <div v-if="!chat.messages.length" class="welcome">
          <div class="welcome-hero">
            <div class="welcome-logo">智</div>
            <h1>你好，我是智学籍 · AI 教务助手</h1>
            <p>
              我能自主调用 9 类专业数据工具，帮你完成学生画像、成绩分析、学业预警、学籍异动核查与制度问答。
              {{ isLocal ? '当前为本地规则引擎模式，配置大模型密钥后将获得更自然的推理能力。' : '当前已接入大模型，支持复杂多步推理。' }}
            </p>
          </div>
          <div class="capability-grid">
            <div v-for="item in capabilities" :key="item.text" class="capability" @click="useHint(item.text)">
              <div class="capability-icon">{{ item.icon }}</div>
              <div>
                <div class="capability-title">{{ item.title }}</div>
                <div class="capability-text">{{ item.text }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 对话流 -->
        <div v-for="(message, index) in chat.messages" :key="index" class="msg" :class="message.role">
          <div class="msg-avatar">{{ message.role === 'user' ? '我' : '智' }}</div>
          <div class="msg-body">
            <div class="msg-name">
              {{ message.role === 'user' ? '我' : '智学籍' }}
              <span v-if="message.role === 'assistant' && message.mode" class="tag tag-muted" style="margin-left: 6px">
                {{ message.mode === 'local' ? '本地推理' : '大模型' }}
              </span>
              <span v-if="message.elapsedMs" class="tag tag-muted" style="margin-left: 6px">
                {{ (message.elapsedMs / 1000).toFixed(1) }}s
              </span>
            </div>

            <template v-if="message.role === 'user'">
              <div>{{ message.content }}</div>
            </template>
            <template v-else>
              <AgentSteps :steps="message.steps || []" />

              <div v-if="message.content" class="markdown-wrap">
                <MarkdownBlock :content="message.content" />
                <span v-if="message.streaming" class="cursor"></span>
              </div>

              <div v-else-if="message.streaming" class="thinking">
                <span class="loading-dots"><span></span><span></span><span></span></span>
                {{ message.steps?.length ? '正在分析工具返回的数据…' : '正在理解你的问题…' }}
              </div>

              <div v-if="message.error" class="msg-error">⚠ {{ message.error }}</div>

              <div v-if="message.citations?.length" class="citations">
                <div class="citations-title">📚 制度依据（{{ message.citations.length }}）</div>
                <div v-for="(item, i) in message.citations" :key="i" class="citation">
                  <b>《{{ item.title }}》</b>
                  <span class="score">相关度 {{ item.score }}</span>
                  <div>{{ item.snippet }}</div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="chat-input-card">
        <div class="chat-hints">
          <span v-for="hint in hints" :key="hint" class="chat-hint" @click="useHint(hint)">{{ hint }}</span>
        </div>
        <div class="chat-input-row">
          <textarea
            class="textarea"
            rows="2"
            v-model="input"
            placeholder="问点什么吧，例如：查一下张三的情况 / 各班平均分对比 / 转专业需要什么条件（Enter 发送，Shift+Enter 换行）"
            @keydown.enter.exact.prevent="submit"
          ></textarea>
          <button class="btn btn-primary" :disabled="chat.sending || !input.trim()" @click="submit">
            {{ chat.sending ? '思考中…' : '发送' }}
          </button>
        </div>
      </div>
    </div>

    <aside class="chat-aside">
      <div class="aside-head">
        <b>历史会话</b>
        <button class="btn btn-sm" @click="chat.reset()">新建</button>
      </div>
      <div v-if="!chat.conversations.length" class="aside-empty">暂无历史会话</div>
      <div
        v-for="item in chat.conversations"
        :key="item.id"
        class="conv-item"
        :class="{ active: item.id === chat.conversationId }"
        @click="chat.openConversation(item.id)"
      >
        <span class="conv-title">{{ item.title || '未命名会话' }}</span>
        <span class="conv-time">{{ (item.updated_at || '').slice(5, 16) }}</span>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { aiStatus } from '@/api/ai'
import AgentSteps from '@/components/AgentSteps.vue'
import MarkdownBlock from '@/components/MarkdownBlock.vue'

const chat = useChatStore()
const input = ref('')
const scrollEl = ref(null)
const status = ref(null)

const isLocal = computed(() => status.value?.mode === 'local')

const hints = [
  '查一下张三的情况',
  '各班平均分对比',
  '哪些学生需要重点关注',
  '学校有多少学生',
  '转专业需要什么条件',
  '还有多少待审核的学籍异动'
]

const capabilities = [
  { icon: '🧑‍🎓', title: '学生全景画像', text: '输入姓名或学号，自动汇总学籍、成绩、奖惩、异动' },
  { icon: '📊', title: '成绩多维分析', text: '班级 / 课程 / 院系 / 学生排名一键统计，自动出图' },
  { icon: '⚠️', title: '学业风险预警', text: '规则引擎识别风险等级，并生成分层帮扶建议' },
  { icon: '📜', title: '制度问答（RAG）', text: '基于校内制度知识库回答，逐条标注出处' },
  { icon: '🗄️', title: '自然语言取数', text: '自由描述统计需求，自动生成只读 SQL 并执行' },
  { icon: '🧩', title: '学籍业务核查', text: '奖惩记录、异动审核进度、数据质量异常一网打尽' }
]

const submit = async () => {
  const text = input.value.trim()
  if (!text || chat.sending) return
  input.value = ''
  await chat.send(text, 'assistant')
}

const useHint = (text) => {
  input.value = text
  submit()
}

const scrollToBottom = () => {
  nextTick(() => {
    if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight
  })
}

watch(
  () => chat.messages.map((m) => m.content.length + (m.steps?.length || 0)).join(','),
  scrollToBottom
)

onMounted(async () => {
  await chat.loadConversations()
  try {
    status.value = await aiStatus()
  } catch (e) {
    status.value = null
  }
})
</script>

<style scoped>
.welcome { max-width: 760px; margin: 12px auto 30px; }
.welcome-hero { text-align: center; margin-bottom: 26px; }
.welcome-logo {
  width: 56px; height: 56px; margin: 0 auto 14px; border-radius: 18px; background: var(--brand-grad);
  color: #fff; font-size: 24px; font-weight: 700; display: grid; place-items: center;
  box-shadow: 0 12px 30px rgba(79, 70, 229, 0.35);
}
.welcome-hero h1 { font-size: 21px; margin-bottom: 8px; }
.welcome-hero p { font-size: 13.5px; color: var(--ink-3); max-width: 620px; margin: 0 auto; }
.capability-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(238px, 1fr)); gap: 12px; }
.capability {
  display: flex; gap: 11px; padding: 14px; border: 1px solid var(--line); border-radius: 14px;
  background: #fff; cursor: pointer; transition: all 0.18s;
}
.capability:hover { border-color: #c7d2fe; box-shadow: var(--shadow-sm); transform: translateY(-1px); }
.capability-icon { font-size: 20px; }
.capability-title { font-size: 13.5px; font-weight: 650; }
.capability-text { font-size: 12.3px; color: var(--ink-3); }
.markdown-wrap { position: relative; }
.thinking { font-size: 13px; color: var(--ink-3); display: flex; align-items: center; gap: 6px; }
.msg-error { margin-top: 8px; font-size: 12.8px; color: var(--danger); background: #fef2f2; border-radius: 8px; padding: 8px 11px; }
.aside-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; font-size: 13.5px; }
.aside-empty { font-size: 12.5px; color: var(--ink-4); padding: 8px 2px; }
</style>
