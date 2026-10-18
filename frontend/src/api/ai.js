/** AI 接口封装，含 SSE 流式对话解析 */

/**
 * 流式对话。
 * Spring 的 SseEmitter 以 `event:<name>\ndata:<json>\n\n` 形式输出，
 * 这里用 fetch + ReadableStream 手工解析（POST + 自定义头部无法用 EventSource）。
 *
 * @param {object} payload {conversationId, message, scene, newConversation}
 * @param {object} handlers {onMeta, onStep, onDelta, onCitations, onDone, onError}
 * @returns {Promise<void>}
 */
export async function streamChat(payload, handlers = {}) {
  const response = await fetch('/ai/chat/stream', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json; charset=utf-8', Accept: 'text/event-stream' },
    body: JSON.stringify(payload)
  })
  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => '')
    throw new Error(`对话请求失败（HTTP ${response.status}）${text ? '：' + text.slice(0, 200) : ''}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (block) => {
    let event = 'message'
    const dataLines = []
    for (const rawLine of block.split('\n')) {
      const line = rawLine.replace(/\r$/, '')
      if (!line || line.startsWith(':')) continue
      if (line.startsWith('event:')) {
        event = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
    if (!dataLines.length) return
    const raw = dataLines.join('\n')
    let data = raw
    try {
      data = JSON.parse(raw)
    } catch (e) {
      /* 保留原始字符串 */
    }
    switch (event) {
      case 'meta':
        handlers.onMeta?.(data)
        break
      case 'step':
        handlers.onStep?.(data)
        break
      case 'delta':
        handlers.onDelta?.(data?.text ?? '')
        break
      case 'citations':
        handlers.onCitations?.(data?.items ?? [])
        break
      case 'done':
        handlers.onDone?.(data)
        break
      case 'error':
        handlers.onError?.(data)
        break
      default:
        break
    }
  }

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const block = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      dispatch(block)
      index = buffer.indexOf('\n\n')
    }
  }
  if (buffer.trim()) dispatch(buffer)
}

export const aiStatus = () => fetch('/ai/status', { credentials: 'include' }).then((r) => r.json())

export const aiInsight = () =>
  fetch('/ai/insight', { method: 'POST', credentials: 'include' }).then((r) => r.json())

export const aiWarningAdvice = (level = 'all', limit = 20) =>
  fetch('/ai/warning-advice', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ level, limit })
  }).then((r) => r.json())

export const aiSql = (question) =>
  fetch('/ai/sql', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question })
  }).then((r) => r.json())

export const aiKnowledgeList = () => fetch('/ai/knowledge', { credentials: 'include' }).then((r) => r.json())

export const aiKnowledgeSearch = (query, topK = 5) =>
  fetch('/ai/knowledge/search', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, topK })
  }).then((r) => r.json())

export const aiKnowledgeDetail = (id) =>
  fetch(`/ai/knowledge/${id}`, { credentials: 'include' }).then((r) => r.json())

export const aiKnowledgeSave = (body) =>
  fetch('/ai/knowledge', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then((r) => r.json())

export const aiKnowledgeDelete = (id) =>
  fetch(`/ai/knowledge/${id}`, { method: 'DELETE', credentials: 'include' }).then((r) => r.json())

export const aiKnowledgeReindex = () =>
  fetch('/ai/knowledge/reindex', { method: 'POST', credentials: 'include' }).then((r) => r.json())

export const aiKnowledgeReset = () =>
  fetch('/ai/knowledge/reset', { method: 'POST', credentials: 'include' }).then((r) => r.json())

/** 工具直连：复用 Agent 工具层（query_students / risk_warning 等） */
export const aiToolInvoke = (name, args = {}) =>
  fetch(`/ai/tool/${name}`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(args)
  }).then((r) => r.json())

export const aiStudents = (params = {}) =>
  fetch('/ai/students', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params)
  }).then((r) => r.json())

export const aiStudentProfile = (payload = {}) =>
  fetch('/ai/students/profile', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }).then((r) => r.json())

export const aiConversations = (limit = 30) =>
  fetch(`/ai/conversations?limit=${limit}`, { method: 'POST', credentials: 'include' }).then((r) => r.json())

export const aiConversationDetail = (id) =>
  fetch(`/ai/conversations/${id}`, { credentials: 'include' }).then((r) => r.json())

export const aiDeleteConversation = (id) =>
  fetch(`/ai/conversations/${id}`, { method: 'DELETE', credentials: 'include' }).then((r) => r.json())
