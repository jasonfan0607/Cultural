import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { agentProxyOptions } from '../agent-proxy.js'
import { chatAgent } from '../src/AI-API/agent.js'

test('offline agent proxy returns actionable JSON 503 instead of an empty 500', () => {
  const proxy = new EventEmitter(), writes = []
  const options = agentProxyOptions()
  options.configure(proxy)
  assert.equal(options.rewrite('/agent-api/chat'), '/api/agent/chat')
  proxy.emit('error', new Error('ECONNREFUSED'), {}, {
    writeHead: (...args) => writes.push(args), end: body => writes.push(JSON.parse(body))
  })
  assert.equal(writes[0][0], 503)
  assert.match(writes[1].message, /npm run start:agent/)
  proxy.emit('error', new Error('disconnect'), {}, { headersSent: true, writeHead: () => assert.fail('must not write twice') })
})
test('old or production proxies returning empty HTTP 500 get a clear frontend error', async t => {
  const original = globalThis.fetch
  t.after(() => { globalThis.fetch = original })
  globalThis.fetch = async () => new Response('', { status: 500 })
  await assert.rejects(chatAgent({ message: '测试' }), e => e.status === 500 && /服务已启动/.test(e.message))
})
