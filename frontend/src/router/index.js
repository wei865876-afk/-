import { createRouter, createWebHashHistory } from 'vue-router'

// 使用 hash 路由，便于直接部署在 Spring Boot 静态目录下（无需服务端 rewrite 配置）
const routes = [
  { path: '/', redirect: '/assistant' },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/assistant', name: 'assistant', component: () => import('@/views/AssistantView.vue'), meta: { title: 'AI 助手' } },
  { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '智能看板' } },
  { path: '/students', name: 'students', component: () => import('@/views/StudentsView.vue'), meta: { title: '学生画像' } },
  { path: '/warning', name: 'warning', component: () => import('@/views/WarningView.vue'), meta: { title: '学业预警' } },
  { path: '/sql', name: 'sql', component: () => import('@/views/SqlView.vue'), meta: { title: '智能取数' } },
  { path: '/knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeView.vue'), meta: { title: '制度知识库' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
