import { defineStore } from 'pinia'
import { streamChat, aiConversations, aiConversationDetail, aiDeleteConversation } from '@/api/ai'

/** 对话状态管理：消息、Agent 步骤、引用、会话历史 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    conversationId: null,
    messages: [],
    conversations: [],
    sending: false,
    meta: null,
    error: null
  }),
  actions: {
    reset() {
      this.messages = []
      this.conversationId = null
      this.error = null
    },

    async send(text, scene = 'assistant') {
      if (!text?.trim() || this.sending) return
      this.error = null
      this.sending = true
      this.messages.push({ role: 'user', content: text })

      const assistantMessage = {
        role: 'assistant',
        content: '',
        steps: [],
        citations: [],
        streaming: true,
        mode: null,
        model: null
      }
      this.messages.push(assistantMessage)
      const current = this.messages[this.messages.length - 1]

      try {
        await streamChat(
          {
            conversationId: this.conversationId,
            message: text,
            scene,
            newConversation: !this.conversationId
          },
          {
            onMeta: (data) => {
              if (data.conversationId) this.conversationId = data.conversationId
              if (data.mode) {
                current.mode = data.mode
                current.model = data.model
                this.meta = data
              }
            },
            onStep: (step) => {
              const index = current.steps.findIndex((item) => item.id === step.id)
              if (index >= 0) {
                current.steps[index] = { ...current.steps[index], ...step }
              } else {
                current.steps.push({ ...step })
              }
            },
            onDelta: (chunk) => {
              current.content += chunk
            },
            onCitations: (items) => {
              current.citations = items
            },
            onDone: (data) => {
              current.streaming = false
              current.mode = data.mode ?? current.mode
              current.elapsedMs = data.elapsedMs
              current.toolRounds = data.toolRounds
            },
            onError: (data) => {
              current.streaming = false
              current.error = data?.text || '对话失败'
            }
          }
        )
      } catch (e) {
        current.streaming = false
        current.error = e.message || String(e)
        this.error = current.error
      } finally {
        current.streaming = false
        this.sending = false
        this.loadConversations()
      }
    },

    async loadConversations() {
      try {
        const data = await aiConversations(30)
        this.conversations = data?.list ?? []
      } catch (e) {
        /* 忽略历史加载失败 */
      }
    },

    async openConversation(id) {
      if (this.sending) return
      try {
        const data = await aiConversationDetail(id)
        this.conversationId = id
        this.messages = (data?.messages ?? []).map((item) => {
          let steps = []
          if (item.tool_results) {
            try {
              steps = JSON.parse(item.tool_results)
            } catch (e) {
              steps = []
            }
          }
          let citations = []
          if (item.citations) {
            try {
              citations = JSON.parse(item.citations)
            } catch (e) {
              citations = []
            }
          }
          return {
            role: item.role,
            content: item.content || '',
            steps,
            citations,
            streaming: false,
            mode: item.mode
          }
        })
      } catch (e) {
        this.error = e.message
      }
    },

    async removeConversation(id) {
      await aiDeleteConversation(id)
      if (id === this.conversationId) this.reset()
      await this.loadConversations()
    }
  }
})
