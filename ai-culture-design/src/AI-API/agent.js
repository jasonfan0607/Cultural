const BASE = import.meta.env?.VITE_AGENT_BASE_URL || '/agent-api'

function authHeaders() {
  const token = globalThis.localStorage?.getItem('auth_token')
  return { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }
}

export function requestId() { return globalThis.crypto.randomUUID() }

export async function agentHealth(options = {}) {
  const response = await fetch(`${BASE}/health`, { signal: options.signal || AbortSignal.timeout(5000) })
  if (!response.ok) await readError(response)
  return response.json()
}

async function readError(response) {
  const body = await response.json().catch(() => ({}))
  const fallback = [500, 502, 503, 504].includes(response.status)
    ? '智能体服务暂时无法连接，请确认服务已启动后重试。'
    : `请求失败（HTTP ${response.status}）`
  const error = new Error(body.message || fallback)
  error.status = response.status
  throw error
}

// UTF-8 safe SSE decoder. Accepts chunk boundaries inside characters, event names, or JSON.
export async function consumeEvents(stream, onEvent) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = '', complete = false, answer
  function parse(block) {
    let event = 'message'
    const data = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
    }
    if (!data.length) return
    let payload
    try { payload = JSON.parse(data.join('\n')) }
    catch { throw new Error('智能体返回的事件无法读取，请重试') }
    if (event === 'error') {
      const error = new Error(payload.message || '请求失败')
      error.status = payload.code
      throw error
    }
    onEvent(event, payload)
    if (event === 'done') { complete = true; answer = payload }
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      // Normalize complete CRLF pairs only, preserving a trailing CR across chunks.
      buffer = buffer.replace(/\r\n/g, '\n')
      let end
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        parse(buffer.slice(0, end)); buffer = buffer.slice(end + 2)
      }
      if (done) break
    }
    if (buffer.trim()) parse(buffer)
    if (!complete) throw new Error('连接中断，答复尚未完成。可以重试或新建对话。')
    return answer
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
}

async function withDeadline(options, operation, timeoutMs = 160000) {
  const controller = new AbortController()
  let timedOut = false
  const cancel = () => controller.abort()
  if (options.signal?.aborted) cancel()
  options.signal?.addEventListener('abort', cancel, { once: true })
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, options.timeoutMs ?? timeoutMs)
  try { return await operation(controller.signal) }
  catch (error) {
    if (timedOut) throw new Error('请求超时，请稍后重试；已提交的生图任务可能仍在完成。')
    if (options.signal?.aborted) throw new DOMException('请求已取消', 'AbortError')
    if (error instanceof TypeError) throw new Error('无法连接智能体服务，请检查服务是否启动')
    throw error
  } finally { clearTimeout(timer); options.signal?.removeEventListener('abort', cancel) }
}

export async function chatAgent(payload, options = {}) {
  return withDeadline(options, async signal => {
    const response = await fetch(`${BASE}/chat`, {
      method: 'POST', headers: authHeaders(), body: JSON.stringify(payload), signal
    })
    if (!response.ok) await readError(response)
    if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream'))
      throw new Error('智能体服务返回了无法读取的响应')
    return consumeEvents(response.body, options.onEvent || (() => {}))
  })
}

export async function agentImage(prompt, size, options = {}) {
  return withDeadline(options, async signal => {
    const response = await fetch(`${BASE}/image`, {
      method: 'POST', headers: authHeaders(), signal,
      body: JSON.stringify({ requestId: options.requestId || requestId(), prompt, size })
    })
    if (!response.ok) await readError(response)
    const body = await response.json().catch(() => { throw new Error('图片服务返回了无法读取的响应') })
    if (typeof body.url !== 'string' || !(body.url.startsWith('https://') || /^\/agent-api\/images\/[a-f0-9-]{36}\.(png|jpg)$/.test(body.url))) throw new Error('图片服务未返回有效的图片地址')
    return body.url.startsWith('/agent-api/images/') ? BASE + body.url.slice('/agent-api'.length) : body.url
  }, 125000)
}

export async function deleteSession(id) {
  const response = await fetch(`${BASE}/sessions/${encodeURIComponent(id)}`, { method: 'DELETE', headers: authHeaders() })
  if (!response.ok) await readError(response)
}

export async function agentText(message, options = {}) {
  let sessionId
  try {
    const answer = await chatAgent({ message, requestId: options.requestId || requestId(), allowImage: false }, {
      ...options, onEvent: (event, data) => {
        if (event === 'session') sessionId = data.sessionId
        options.onEvent?.(event, data)
      }
    })
    return answer.content
  } finally { if (sessionId) deleteSession(sessionId).catch(() => {}) }
}
