import test from 'node:test'
import assert from 'node:assert/strict'
import { consumeEvents, chatAgent, agentImage } from '../src/AI-API/agent.js'

function stream(text, width = 1) {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream({ start(controller) {
    for (let i = 0; i < bytes.length; i += width) controller.enqueue(bytes.slice(i, i + width))
    controller.close()
  } })
}
test('SSE supports split UTF-8 characters, CRLF and multiple events', async () => {
  const events = []
  const answer = await consumeEvents(stream('event:status\r\ndata:{"message":"正在检索云锦"}\r\n\r\nevent:done\r\ndata:{"content":"云锦方案"}\r\n\r\n'), (name, data) => events.push([name, data]))
  assert.equal(events[0][1].message, '正在检索云锦')
  assert.equal(answer.content, '云锦方案')
})
test('SSE rejects a dropped connection and preserves explicit backend errors', async () => {
  await assert.rejects(consumeEvents(stream('event:status\ndata:{}\n\n'), () => {}), /连接中断/)
  await assert.rejects(consumeEvents(stream('event:error\ndata:{"code":404,"message":"会话已过期"}\n\n'), () => {}), e => e.status === 404 && /会话/.test(e.message))
})
test('requests send the login token and stable image request ID, no provider key', async t => {
  const original = globalThis.fetch, oldStorage = globalThis.localStorage
  t.after(() => { globalThis.fetch = original; globalThis.localStorage = oldStorage })
  globalThis.localStorage = { getItem: key => key === 'auth_token' ? 'login-token' : null }
  globalThis.fetch = async (url, options) => {
    assert.equal(options.headers.Authorization, 'Bearer login-token')
    assert.ok(url.startsWith('/agent-api/'))
    assert.equal(JSON.parse(options.body).requestId, 'stable-image-request-001')
    return Response.json({ url: 'https://example.com/image.png' })
  }
  assert.equal(await agentImage('云锦', '2048x2048', { requestId: 'stable-image-request-001' }), 'https://example.com/image.png')
  globalThis.fetch = async () => Response.json({ message: '登录已失效' }, { status: 401 })
  await assert.rejects(chatAgent({ message: 'test' }), e => e.status === 401)
})
