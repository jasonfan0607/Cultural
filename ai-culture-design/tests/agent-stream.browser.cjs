const assert = require('node:assert/strict')
const fs = require('node:fs')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1360, height: 1000 } })
    await page.addInitScript(() => {
      localStorage.setItem('isLogin', 'true'); localStorage.setItem('auth_token', 'test-token')
      const original = window.fetch.bind(window)
      window.fetch = async (url, options) => {
        if (!String(url).endsWith('/agent-api/chat')) return original(url, options)
        const source = { id: 2, type: 'heritage', title: '南京云锦', description: '丝织工艺' }
        const web = { id: 'a', type: 'web', title: '云锦纹样研究', description: '公开搜索摘要', url: 'https://example.org/yunjin', domain: 'example.org', engine: 'DuckDuckGo' }
        const stream = new ReadableStream({ start(controller) {
          const emit = (name, data) => controller.enqueue(new TextEncoder().encode(`event:${name}\ndata:${JSON.stringify(data)}\n\n`))
          emit('session', { sessionId: 'test-session' }); emit('text-start', { round: 0 })
          emit('source', source); emit('source', { ...source, id: 12 }); emit('source', web)
          emit('delta', { text: '# 云锦方案\n\n**金色纹样**\n\n- 礼盒\n' })
          window.finishAgentStream = () => {
            const content = '# 云锦方案\n\n**金色纹样**\n\n- 礼盒\n\n[来源](https://example.org/yunjin)'
            emit('delta', { text: '\n[来源](https://example.org/yunjin)' })
            emit('done', { content, sources: [source, { ...source, id: 12 }, web], images: [] }); controller.close()
          }
        } })
        return new Response(stream, { headers: { 'content-type': 'text/event-stream' } })
      }
    })
    await page.goto((process.env.FRONTEND_URL || 'http://127.0.0.1:5175') + '/agent')
    await page.locator('#agent-input').fill('查找云锦资料')
    await page.getByRole('button', { name: '开始探索' }).click()
    await page.locator('.assistant h1').waitFor()
    assert.equal(await page.locator('.assistant strong').innerText(), '金色纹样')
    assert.equal(await page.locator('.assistant li').innerText(), '礼盒')
    assert.equal(await page.locator('.source-list details').count(), 2)
    assert.equal(await page.getByRole('button', { name: '停止生成' }).count(), 1, 'text visible before stream finishes')
    await page.evaluate(() => window.finishAgentStream())
    await page.getByRole('button', { name: '开始探索' }).waitFor()
    assert.equal(await page.locator('.source-list details').count(), 2)
    await page.locator('.source-list details').last().locator('summary').click()
    assert.equal(await page.getByRole('link', { name: /打开来源/ }).getAttribute('href'), 'https://example.org/yunjin')
    fs.mkdirSync('tests/artifacts', { recursive: true })
    await page.screenshot({ path: 'tests/artifacts/agent-stream-markdown.png', fullPage: true })
    console.log('PASS: visible streaming before done, Markdown formatting, deduplication and real source links')
  } finally { await browser.close() }
})().catch(e => { console.error(e); process.exitCode = 1 })
