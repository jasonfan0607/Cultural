import test from 'node:test'
import assert from 'node:assert/strict'
import { historyOwner, historySnapshot, saveHistory, loadHistory, removeHistory } from '../src/utils/agent-history.js'
const storage = () => {
  const data = new Map()
  return { get length() { return data.size }, key: i => [...data.keys()][i], getItem: k => data.get(k) ?? null,
    setItem: (k, v) => data.set(k, v), removeItem: k => data.delete(k) }
}
const record = (id, updatedAt) => historySnapshot({ id, sessionId: `session-${id}`, createdAt: 1, updatedAt,
  sessionTouchedAt: updatedAt, messages: [{ role: 'user', content: `问题${id}` },
    { role: 'assistant', content: '**部分答复**', pending: true, status: '生成中', sources: [{ type: 'web', id: 'a', url: 'https://example.org' }], images: ['https://example.org/image.png'] }] })
test('history persists full messages, sorts by update time and deletes exactly one record', () => {
  const s = storage()
  saveHistory('alice', record('a', 10), s); saveHistory('alice', record('b', 20), s)
  saveHistory('bob', record('c', 30), s)
  const items = loadHistory('alice', s)
  assert.deepEqual(items.map(i => i.id), ['b', 'a'])
  assert.match(items[0].messages[1].error, /中断/)
  assert.equal(items[0].messages[1].status, '')
  assert.equal(items[0].messages[1].sources.length, 1)
  assert.equal(items[0].messages[1].images.length, 1)
  removeHistory('alice', 'a', s)
  assert.deepEqual(loadHistory('alice', s).map(i => i.id), ['b'])
  assert.equal(loadHistory('bob', s).length, 1)
  assert.deepEqual(loadHistory('', s), [])
})
test('owner follows account ID across renewed tokens; logout cannot see another account', () => {
  const s = storage()
  s.setItem('auth_token', `header.${btoa(JSON.stringify({ user_id: 42, exp: 1 }))}.signature`)
  assert.equal(historyOwner(s), 'user:42')
  s.setItem('auth_token', `header.${btoa(JSON.stringify({ user_id: 42, exp: 2 }))}.new-signature`)
  assert.equal(historyOwner(s), 'user:42')
  s.removeItem('auth_token'); s.setItem('username', 'old-name')
  assert.equal(historyOwner(s), '')
})
test('bad records do not break the list, storage failures are not silently swallowed', () => {
  const s = storage()
  saveHistory('alice', record('ok', 10), s)
  s.setItem('cultural.agent-history.v1:alice:bad', '{')
  assert.equal(loadHistory('alice', s).length, 1)
  s.setItem = () => { throw new Error('quota exceeded') }
  assert.throws(() => saveHistory('alice', record('failed', 20), s), /quota/)
  assert.equal(loadHistory('alice', s).length, 1)
})
