/** 统一 HTTP 封装：携带 Session Cookie，自动解包 {code,data,msg} 结构 */

export async function request(url, options = {}) {
  const config = {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options
  }
  if (config.body && typeof config.body !== 'string') {
    config.body = JSON.stringify(config.body)
  }
  const response = await fetch(url, config)
  if (!response.ok) {
    throw new Error(`请求失败（HTTP ${response.status}）`)
  }
  const text = await response.text()
  if (!text) return null
  let json
  try {
    json = JSON.parse(text)
  } catch (e) {
    return text
  }
  // 兼容 layui 风格 {code:0,...} 与直接返回对象两种格式
  if (json && typeof json === 'object' && 'code' in json) {
    if (json.code !== 0) {
      throw new Error(json.msg || '请求失败')
    }
    return json.data === undefined ? json : json.data
  }
  return json
}

export const get = (url, params) => {
  if (params) {
    const query = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
    ).toString()
    if (query) url += (url.includes('?') ? '&' : '?') + query
  }
  return request(url)
}

export const post = (url, body) => request(url, { method: 'POST', body: body ?? {} })
export const put = (url, body) => request(url, { method: 'PUT', body: body ?? {} })
export const del = (url) => request(url, { method: 'DELETE' })

/** 表单方式提交（后端部分接口使用 @RequestParam 绑定对象） */
export async function postForm(url, data) {
  const form = new URLSearchParams()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null) form.append(key, value)
  })
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
    body: form
  })
  const json = await response.json()
  if (json.code !== 0) throw new Error(json.msg || '请求失败')
  return json.data
}
