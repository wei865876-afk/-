<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-logo">智</div>
        <div class="brand-text">
          <b>智学籍</b>
          <span>AI 原生学籍管理平台</span>
        </div>
      </div>
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menu"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: $route.path === item.path }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="sidebar-foot">
        <div class="mode-chip">
          <span class="mode-dot" :class="{ local: isLocal }"></span>
          {{ isLocal ? '本地规则引擎' : '大模型在线' }}
        </div>
        <div>{{ modelName }}</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div>
          <h2>{{ currentTitle }}</h2>
          <div class="topbar-sub">{{ subtitle }}</div>
        </div>
        <div class="topbar-right">
          <span class="tag tag-muted">工具 {{ status?.tools?.length ?? 0 }} 个</span>
          <span class="tag tag-muted">知识库 {{ status?.knowledgeDocs ?? 0 }} 篇</span>
          <template v-if="auth.isLogin">
            <span>{{ auth.displayName }}</span>
            <span class="tag tag-info">{{ auth.roleLabel }}</span>
            <button class="btn btn-sm" @click="logout">退出</button>
          </template>
          <router-link v-else to="/login" class="btn btn-sm btn-primary">登录</router-link>
        </div>
      </header>
      <div class="page">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiStatus } from '@/api/ai'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const status = ref(null)

const menu = [
  { path: '/assistant', label: 'AI 助手', icon: '✦' },
  { path: '/dashboard', label: '智能看板', icon: '◎' },
  { path: '/warning', label: '学业预警', icon: '⚑' },
  { path: '/students', label: '学生画像', icon: '☰' },
  { path: '/sql', label: '智能取数', icon: '⌘' },
  { path: '/knowledge', label: '制度知识库', icon: '❖' }
]

const subtitles = {
  assistant: 'Agent 自主规划 · 工具调用 · 多轮对话',
  dashboard: 'AI 主动洞察 · 学籍与成绩态势',
  warning: '规则引擎识别 + 大模型生成干预建议',
  students: '一人一档 · AI 全景画像',
  sql: '自然语言 → 只读 SQL → 可视化结果',
  knowledge: 'RAG 检索增强 · 制度问答可溯源',
  login: '统一身份认证'
}

const currentTitle = computed(() => route.meta?.title || '智学籍')
const subtitle = computed(() => subtitles[route.name] || '')
const isLocal = computed(() => status.value?.mode === 'local' || status.value?.localFallback)
const modelName = computed(() => status.value?.model || '正在检测模型状态…')

const logout = async () => {
  await auth.logout()
  router.push('/login')
}

onMounted(async () => {
  await auth.check()
  try {
    status.value = await aiStatus()
  } catch (e) {
    status.value = null
  }
})
</script>
