<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-head">
        <div class="head-top">
          <div class="brand-logo">智</div>
          <div>
            <h1>智学籍 · AI 原生学籍管理平台</h1>
            <p>自然语言取数 · 智能预警 · 制度问答 · 学生画像</p>
          </div>
        </div>
      </div>
      <div class="login-body">
        <div class="role-switch">
          <div
            v-for="item in roles"
            :key="item.value"
            class="role-btn"
            :class="{ active: form.role === item.value }"
            @click="form.role = item.value"
          >
            {{ item.label }}
          </div>
        </div>

        <div class="field">
          <label>账号</label>
          <input class="input" v-model="form.username" placeholder="请输入账号" @keydown.enter="submit" />
        </div>
        <div class="field">
          <label>密码</label>
          <input class="input" type="password" v-model="form.password" placeholder="请输入密码" @keydown.enter="submit" />
        </div>

        <div v-if="auth.error" class="login-error">⚠ {{ auth.error }}</div>

        <button class="btn btn-primary login-btn" :disabled="auth.loading || !canSubmit" @click="submit">
          {{ auth.loading ? '登录中…' : '登录' }}
        </button>

        <div class="login-tip">
          <div class="tip-title">演示账号（点击自动填充）</div>
          <div class="tip-accounts">
            <span
              v-for="item in demoAccounts"
              :key="item.username"
              class="chat-hint"
              @click="fill(item)"
            >
              {{ item.label }}
            </span>
          </div>
          <div class="tip-note">
            登录状态基于 Spring Session 维持；未登录也可体验 AI 助手，登录后可获得个性化上下文与"我的课程/成绩"视角问答。
          </div>
          <router-link to="/assistant" class="skip">不登录，直接体验 →</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const roles = [
  { label: '管理员', value: 'admin' },
  { label: '教务老师', value: '教务' },
  { label: '学生', value: 'student' }
]

const demoAccounts = [
  { label: 'admin / 123456（管理员）', username: 'admin', password: '123456', role: 'admin' },
  { label: 'jiaowu1 / 654321（张教务）', username: 'jiaowu1', password: '654321', role: '教务' },
  { label: '2406010101 / 123456（学生）', username: '2406010101', password: '123456', role: 'student' }
]

const form = reactive({ username: '', password: '', role: 'admin' })
const canSubmit = computed(() => form.username.trim() && form.password.trim())

const fill = (item) => {
  form.username = item.username
  form.password = item.password
  form.role = item.role
}

const submit = async () => {
  if (!canSubmit.value || auth.loading) return
  const ok = await auth.login(form.username.trim(), form.password, form.role)
  if (ok) {
    router.push('/assistant')
  }
}
</script>

<style scoped>
.head-top { display: flex; align-items: center; gap: 12px; }
.head-top h1 { font-size: 17px; margin-bottom: 4px; }
.brand-logo {
  width: 40px; height: 40px; flex: 0 0 40px; border-radius: 12px; background: rgba(255, 255, 255, 0.22);
  display: grid; place-items: center; font-weight: 700; font-size: 18px; color: #fff;
}
.login-btn { width: 100%; padding: 11px 0; font-size: 14px; }
.tip-title { font-size: 12px; color: var(--ink-3); margin-bottom: 7px; font-weight: 600; }
.tip-accounts { display: flex; flex-wrap: wrap; gap: 7px; }
.tip-note { margin-top: 9px; font-size: 11.5px; color: var(--ink-4); line-height: 1.7; }
.skip { display: inline-block; margin-top: 8px; font-size: 12.5px; }
</style>
