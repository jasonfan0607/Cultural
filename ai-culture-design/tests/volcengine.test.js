import test from 'node:test'
import assert from 'node:assert/strict'
import { generateImageWithVolcEngine, generateJiangsuCultureImage } from '../src/AI-API/volcengine.js'

test('image service: success, errors, timeout and cancellation', async (t) => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  await t.test('preserves the full request and purpose', async () => {
    globalThis.fetch = async (_, options) => {
      const body = JSON.parse(options.body)
      assert.match(body.prompt, /白色文化衫/)
      assert.match(body.prompt, /用途：文化衫/)
      assert.equal(body.model, undefined, 'provider model is selected on the server')
      assert.ok(body.requestId)
      return Response.json({ url: 'https://example.com/design.png' })
    }
    assert.equal(await generateJiangsuCultureImage('昆曲', '现代简约', '2K', {
      description: '设计白色文化衫', purpose: '文化衫'
    }), 'https://example.com/design.png')
  })

  await t.test('reports authentication, malformed and empty responses', async () => {
    globalThis.fetch = async () => Response.json({ message: '请先登录' }, { status: 401 })
    await assert.rejects(generateImageWithVolcEngine('测试'), /请先登录/)
    globalThis.fetch = async () => new Response('invalid json')
    await assert.rejects(generateImageWithVolcEngine('测试'), /无法读取/)
    globalThis.fetch = async () => Response.json({ data: [] })
    await assert.rejects(generateImageWithVolcEngine('测试'), /有效的图片地址/)
  })

  const pendingFetch = async (_, { signal }) => new Promise((resolve, reject) => {
    const abort = () => reject(new DOMException('Aborted', 'AbortError'))
    if (signal.aborted) abort()
    else signal.addEventListener('abort', abort, { once: true })
  })

  await t.test('stops a stalled request', async () => {
    globalThis.fetch = pendingFetch
    await assert.rejects(generateImageWithVolcEngine('测试', undefined, undefined, true, { timeoutMs: 10 }), /请求超时/)
  })

  await t.test('stops when the user switches conversations', async () => {
    globalThis.fetch = pendingFetch
    const controller = new AbortController()
    const pending = generateImageWithVolcEngine('测试', undefined, undefined, true, { signal: controller.signal })
    controller.abort()
    await assert.rejects(pending, { name: 'AbortError' })
  })

  await t.test('reports network failures', async () => {
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await assert.rejects(generateImageWithVolcEngine('测试'), /无法连接智能体服务/)
  })
})
