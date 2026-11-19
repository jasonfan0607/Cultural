const PREFIX = 'cultural.agent-history.v1:'
export const SESSION_TTL = 30 * 60 * 1000

// This scopes local display records only. API authentication still happens on the server.
export function historyOwner(storage = globalThis.localStorage) {
  const token = storage.getItem('auth_token')
  if (!token) return ''
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (payload.user_id != null) return `user:${payload.user_id}`
  } catch { /* Older/local login flows also store the account name. */ }
  const username = storage.getItem('username')
  return username ? `account:${username}` : ''
}
const scope = owner => `${PREFIX}${encodeURIComponent(owner)}:`
const key = (owner, id) => `${scope(owner)}${encodeURIComponent(id)}`
const clone = value => JSON.parse(JSON.stringify(value))

export function historySnapshot({ id, sessionId, createdAt, updatedAt, messages, sessionTouchedAt }) {
  return { version: 1, id, sessionId, createdAt, updatedAt, sessionTouchedAt,
    title: messages.find(m => m.role === 'user')?.content?.slice(0, 60) || '新的探索',
    messages: clone(messages).map(message => ({ ...message, status: '',
      error: message.pending ? '上次生成已中断，已保留收到的内容。' : (message.error || '') })) }
}
export function saveHistory(owner, record, storage = globalThis.localStorage) {
  if (!owner || !record.messages.length) return
  // One key per conversation: saving one record cannot overwrite another tab's history.
  storage.setItem(key(owner, record.id), JSON.stringify(record))
}
export function loadHistory(owner, storage = globalThis.localStorage) {
  if (!owner) return []
  const records = []
  for (let i = 0; i < storage.length; i++) {
    const name = storage.key(i)
    if (!name?.startsWith(scope(owner))) continue
    try {
      const record = JSON.parse(storage.getItem(name))
      if (record?.version !== 1 || typeof record.id !== 'string' || !Array.isArray(record.messages) ||
          !Number.isFinite(record.updatedAt) || !Number.isFinite(record.createdAt) || !Number.isFinite(new Date(record.updatedAt).getTime())) continue
      if (!record.messages.every(m => m && ['user', 'assistant'].includes(m.role) && typeof m.content === 'string')) continue
      record.title = typeof record.title === 'string' ? record.title : record.messages.find(m => m.role === 'user')?.content.slice(0, 60) || '历史对话'
      record.messages = record.messages.map(m => ({ ...m,
        sources: Array.isArray(m.sources) ? m.sources.filter(source => source && typeof source === 'object') : [],
        images: Array.isArray(m.images) ? m.images.filter(url => typeof url === 'string') : [] }))
      records.push(record)
    } catch { /* A corrupt record must not prevent access to the other conversations. */ }
  }
  return records.sort((a, b) => b.updatedAt - a.updatedAt)
}
export function removeHistory(owner, id, storage = globalThis.localStorage) {
  if (owner) storage.removeItem(key(owner, id))
}
export function isHistoryStorageKey(name) { return name?.startsWith(PREFIX) }
