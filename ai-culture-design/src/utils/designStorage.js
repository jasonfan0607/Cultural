export const LEGACY_HISTORY_KEY = 'cultural.design-history.v1'
const HISTORY_PREFIX = 'cultural.design-history.v2.'
const LEGACY_OWNER_KEY = LEGACY_HISTORY_KEY + '.owner'
const WORKS_EVENT = 'design-works-changed'

export function currentDesignUser() {
  return localStorage.getItem('isLogin') === 'true' ? localStorage.getItem('username') || '' : ''
}

function readArray(key) {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

export function designHistoryKey(username) {
  return HISTORY_PREFIX + encodeURIComponent(username)
}

export function readDesignHistory(username = currentDesignUser()) {
  if (!username) return []
  const key = designHistoryKey(username)
  if (localStorage.getItem(key) !== null) return readArray(key)
  // Old history has no owner. Claim it once for the current account, leaving
  // the original intact and preventing later accounts from inheriting it.
  const previousOwner = localStorage.getItem(LEGACY_OWNER_KEY)
  if (previousOwner && previousOwner !== username) return []
  const history = readArray(LEGACY_HISTORY_KEY)
  if (!history.length) return []
  localStorage.setItem(LEGACY_OWNER_KEY, username)
  localStorage.setItem(key, JSON.stringify(history))
  return history
}

export function writeDesignHistory(username, history) {
  if (!username) return
  localStorage.setItem(designHistoryKey(username), JSON.stringify(history))
}

function generatedWork(history, message, index) {
  if (message?.role !== 'ai' || message.isLoading || typeof message.imageUrl !== 'string' || !message.imageUrl) return null
  const params = message.params || {}
  const prompt = params.description || history.title || 'AI文化设计'
  const createdAt = Number(message.generatedAt) || Number(history.updatedAt) || 0
  return {
    id: message.workId || `design:${message.imageRequestId || `${history.id}:${index}:${message.imageUrl}`}`,
    title: prompt.slice(0, 50),
    description: prompt,
    image: message.imageUrl,
    style: params.style || 'AI设计',
    date: createdAt ? new Date(createdAt).toLocaleDateString('zh-CN') : '',
    createdAt,
    historyId: history.id,
    source: 'volcengine'
  }
}

function mergeWorks(saved, generated) {
  const byId = new Map()
  for (const item of [...saved, ...generated]) {
    if (item && typeof item === 'object' && item.id != null) byId.set(String(item.id), item)
  }
  return [...byId.values()].sort((a, b) => (Number(b.createdAt) || 0) - (Number(a.createdAt) || 0))
}

function persistWorks(username, works) {
  const key = 'userWorks_' + username
  const serialized = JSON.stringify(works)
  if (localStorage.getItem(key) === serialized) return
  localStorage.setItem(key, serialized)
  window.dispatchEvent(new Event(WORKS_EVENT))
}

export function readUserWorks(username = currentDesignUser()) {
  if (!username) return []
  const saved = readArray('userWorks_' + username)
  const generated = readDesignHistory(username).flatMap(history =>
    history && Array.isArray(history.messages)
      ? history.messages.map((message, index) => generatedWork(history, message, index)).filter(Boolean)
      : [])
  const works = mergeWorks(saved, generated)
  // Backfill history so later history trimming never removes archived works.
  persistWorks(username, works)
  return works
}

export function saveGeneratedWork(username, historyId, reply, index) {
  if (!username) throw new Error('请登录后保存作品')
  const work = generatedWork({ id: historyId, updatedAt: Date.now() }, { ...reply, isLoading: false }, index)
  if (!work) return
  persistWorks(username, mergeWorks(readArray('userWorks_' + username), [work]))
}

export function subscribeUserWorks(listener) {
  const onStorage = event => {
    if (event.key === null || event.key.startsWith('userWorks_') || event.key.startsWith(HISTORY_PREFIX) || ['username', 'isLogin'].includes(event.key)) listener()
  }
  window.addEventListener(WORKS_EVENT, listener)
  window.addEventListener('storage', onStorage)
  return () => {
    window.removeEventListener(WORKS_EVENT, listener)
    window.removeEventListener('storage', onStorage)
  }
}

// Keep the work identity/date and the linked conversation together when restoring an image.
export function workImageContext(username, work) {
  const history = readDesignHistory(username).find(item => item.id === work.historyId)
  const reply = history?.messages?.find((message, index) => generatedWork(history, message, index)?.id === work.id)
  return { params: reply?.params || { description: work.description || work.title, style: work.style }, content: reply?.content || '' }
}

export function replaceWorkImage(username, workId, previousUrl, imageUrl) {
  const worksKey = 'userWorks_' + username
  const historyKey = designHistoryKey(username)
  const saved = readUserWorks(username)
  const target = saved.find(work => String(work.id) === String(workId))
  if (!target || target.image !== previousUrl) throw new Error('作品已发生变化，请刷新后查看')
  const history = readDesignHistory(username)
  const backupKey = 'cultural.image-repair-backup.' + encodeURIComponent(username)
  if (!localStorage.getItem(backupKey)) localStorage.setItem(backupKey, JSON.stringify({ works: saved, history }))
  for (const entry of history) {
    for (const [index, message] of (entry.messages || []).entries()) {
      if (generatedWork(entry, message, index)?.id !== workId) continue
      message.workId = workId
      message.previousImageUrl ||= message.imageUrl
      message.imageUrl = imageUrl
      message.imageFailed = false
      message.restoredAt = Date.now()
    }
  }
  const oldHistory = localStorage.getItem(historyKey)
  const updated = saved.map(work => work.id === workId
    ? { ...work, image: imageUrl, previousImageUrl: work.previousImageUrl || previousUrl, restoredAt: Date.now() } : work)
  try {
    writeDesignHistory(username, history)
    localStorage.setItem(worksKey, JSON.stringify(updated))
  } catch (error) {
    if (oldHistory === null) localStorage.removeItem(historyKey)
    else localStorage.setItem(historyKey, oldHistory)
    throw error
  }
  window.dispatchEvent(new Event(WORKS_EVENT))
}
