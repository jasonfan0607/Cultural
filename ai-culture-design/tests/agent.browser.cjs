const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.FRONTEND_URL || 'http://127.0.0.1:5175'
const event = (name, data) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`
;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } })
    const errors = []; page.on('pageerror', e => errors.push(e.message))
    await page.addInitScript(() => { localStorage.setItem('isLogin', 'true'); localStorage.setItem('auth_token', 'browser-test-token') })
    let mode = 'success', calls = []
    await page.route('**/agent-api/sessions/**', r => r.fulfill({ contentType: 'application/json', body: '{"deleted":true}' }))
    await page.route('**/agent-api/chat', async route => {
      const body = route.request().postDataJSON(); calls.push(body)
      assert.equal(route.request().headers().authorization, 'Bearer browser-test-token')
      if (mode === 'error') return route.fulfill({ status: 503, contentType: 'application/json', body: '{"message":"尚未配置服务端 DEEPSEEK_API_KEY"}' })
      const source = { id: 7, type: 'heritage', title: '南京云锦', description: '传统丝织工艺', content: '文化依据：传统丝织工艺。', city: '南京' }
      await route.fulfill({ contentType: 'text/event-stream', body:
        event('session', { sessionId: '11111111-1111-1111-1111-111111111111' }) +
        event('tool', { name: 'searchCulture', message: '正在检索文化资料' }) +
        event('source', source) +
        event('done', { content: '以南京云锦 [heritage:7] 为文化依据，采用留白与织纹设计茶叶礼盒。', sources: [source], images: [] }) })
    })
    await page.goto(origin + '/agent')
    await page.locator('.agent-intro h1').waitFor()
    assert.match(await page.locator('.agent-intro h1').textContent(), /循文脉/)
    const output = path.resolve('tests/artifacts'); fs.mkdirSync(output, { recursive: true })
    await page.screenshot({ path: path.join(output, 'agent-desktop.png'), fullPage: true })
    await page.locator('#agent-input').fill('南京云锦包装')
    await page.getByRole('button', { name: '开始探索' }).click()
    await page.getByText('以南京云锦 [heritage:7] 为文化依据，采用留白与织纹设计茶叶礼盒。', { exact: true }).waitFor()
    assert.equal(calls[0].allowImage, false)
    await page.locator('summary').click()
    await page.getByText('文化依据：传统丝织工艺。', { exact: true }).waitFor()
    await page.locator('#agent-input').fill('改成海报')
    await page.getByRole('button', { name: '开始探索' }).click()
    await page.waitForFunction(() => document.querySelectorAll('.assistant .message-content').length === 2)
    assert.equal(calls[1].sessionId, '11111111-1111-1111-1111-111111111111')
    mode = 'error'
    await page.locator('#agent-input').fill('测试缺少配置')
    await page.getByRole('button', { name: '开始探索' }).click()
    await page.getByText('尚未配置服务端 DEEPSEEK_API_KEY', { exact: true }).waitFor()
    const retryId = calls.at(-1).requestId; mode = 'success'
    await page.getByRole('button', { name: '重试此请求' }).click()
    await page.waitForFunction(() => document.querySelectorAll('.assistant .message-content').length === 3)
    assert.equal(calls.at(-1).requestId, retryId)
    await page.getByRole('button', { name: '开启新的探索' }).click()
    assert.equal(await page.locator('.agent-message').count(), 0)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.waitForFunction(() => window.scrollY === 0)
    await page.screenshot({ path: path.join(output, 'agent-mobile.png'), fullPage: true })
    const intro = await page.locator('.agent-intro').boundingBox()
    const nav = await page.locator('.navbar').boundingBox()
    assert.ok(intro.y >= nav.y + nav.height, 'navigation must not cover mobile introduction')
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'no mobile horizontal overflow')
    assert.deepEqual(errors, [])
    console.log('PASS: agent desktop/mobile, auth header, multi-turn, sources, errors, retry IDs and new conversation')
  } finally { await browser.close() }
})().catch(e => { console.error(e); process.exitCode = 1 })
