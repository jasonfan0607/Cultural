// Gallery entries are currently local frontend data, not backend UserWork IDs.
// Keep an account-scoped snapshot so gallery and profile share the same records.
const PREFIX = 'galleryCollections_v1_'
const CHANGE_EVENT = 'gallery-collections-changed'

function storageKey() {
  const username = localStorage.getItem('username')
  return localStorage.getItem('isLogin') === 'true' && username
    ? PREFIX + encodeURIComponent(username)
    : null
}

export function readGalleryCollections() {
  const key = storageKey()
  if (!key) return []
  try {
    const saved = JSON.parse(localStorage.getItem(key) || '[]')
    if (!Array.isArray(saved)) return []
    const seen = new Set()
    return saved.filter(item => {
      if (!item || !['string', 'number'].includes(typeof item.id) ||
          typeof item.title !== 'string' || typeof item.imageUrl !== 'string') return false
      const id = String(item.id)
      if (seen.has(id)) return false
      seen.add(id)
      return true
    })
  } catch {
    return []
  }
}

function writeCollections(records) {
  const key = storageKey()
  if (!key) throw new Error('请先登录后再收藏')
  // Notify and update the UI only after storage succeeds (e.g. quota may be full).
  localStorage.setItem(key, JSON.stringify(records))
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function toggleGalleryCollection(work, categoryLabel) {
  const records = readGalleryCollections()
  const exists = records.some(item => String(item.id) === String(work.id))
  if (exists) {
    writeCollections(records.filter(item => String(item.id) !== String(work.id)))
  } else {
    const snapshot = {
      id: work.id,
      title: work.title,
      description: work.description || '',
      imageUrl: work.imageUrl,
      author: work.author || '',
      authorAvatar: work.authorAvatar || '',
      category: work.category || '',
      categoryLabel,
      tags: Array.isArray(work.tags) ? [...work.tags] : [],
      likes: Number(work.likes) || 0,
      views: Number(work.views) || 0,
      collects: Math.max(0, (Number(work.collects) || 0) - Number(Boolean(work.isCollected))),
      allowDownload: Boolean(work.allowDownload),
      createTime: work.createTime || '',
      collectedAt: new Date().toISOString()
    }
    writeCollections([snapshot, ...records])
  }
  return !exists
}

export function removeGalleryCollection(id) {
  writeCollections(readGalleryCollections().filter(item => String(item.id) !== String(id)))
}

export function subscribeGalleryCollections(listener) {
  const onStorage = event => {
    if (event.key === null || event.key.startsWith(PREFIX) || ['isLogin', 'username'].includes(event.key)) listener()
  }
  window.addEventListener(CHANGE_EVENT, listener)
  window.addEventListener('storage', onStorage)
  return () => {
    window.removeEventListener(CHANGE_EVENT, listener)
    window.removeEventListener('storage', onStorage)
  }
}
