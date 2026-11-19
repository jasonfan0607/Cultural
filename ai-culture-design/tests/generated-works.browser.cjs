const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.FRONTEND_URL || 'http://127.0.0.1:4173'
;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const errors = []
    context.on('page', page => page.on('pageerror', error => errors.push(error.message)))
    await context.route('**/agent-api/health', route => route.fulfill({ json: { imageConfigured: true, modelConfigured: true } }))
    await context.route('**/agent-api/chat', route => route.fulfill({ contentType: 'text/event-stream', body: 'event:done\ndata:{"content":"文化设计方案"}\n\n' }))
    let calls = 0, fail = false
    await context.route('**/agent-api/image', route => {
      calls++
      return route.fulfill(fail ? { status: 500, json: { message: '测试生成失败' } } : { json: { url: 'https://example.com/generated-' + calls + '.svg' } })
    })
    await context.route('**/generated-*.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="#af2345"/></svg>' }))
    const worksPage = await context.newPage()
    await worksPage.goto(origin + '/login')
    await worksPage.evaluate(origin => {
      localStorage.setItem('isLogin', 'true')
      localStorage.setItem('username', 'works_test_a')
      localStorage.setItem('avatar', '/logo1.png')
      localStorage.setItem('cultural.design-history.v1', JSON.stringify([
        { id: 'legacy-conversation', title: '旧设计对话', updatedAt: 1000, messages: [
          { role: 'user', content: '历史作品一' },
          { role: 'ai', imageUrl: origin + '/generated-old-one.svg', imageRequestId: 'old-one', params: { description: '历史作品一', style: '中国风' } },
          { role: 'ai', imageUrl: origin + '/generated-old-two.svg', params: { description: '历史作品二' } },
          { role: 'ai', error: '未完成', params: { description: '失败记录' } }
        ] }
      ]))
      localStorage.setItem('userWorks_works_test_a', JSON.stringify([{ id: 'manual-1', title: '原有作品', image: '/logo1.png' }]))
    }, origin)
    await worksPage.goto(origin + '/works')
    await worksPage.getByRole('heading', { name: '历史作品一' }).waitFor()
    assert.equal(await worksPage.locator('.work-card').count(), 3, 'backfill every successful image and retain pre-existing works')
    await worksPage.reload()
    assert.equal(await worksPage.locator('.work-card').count(), 3, 'backfill must be idempotent')
    const design = await context.newPage()
    await design.goto(origin + '/design')
    const send = async prompt => {
      await design.locator('textarea').fill(prompt)
      await design.locator('.send-btn').click()
      await design.waitForFunction(() => !document.querySelector('.loading-indicator'))
    }
    await send('徐州云龙山宣传图')
    await design.locator('.result-image').waitFor()
    await worksPage.getByRole('heading', { name: '徐州云龙山宣传图' }).waitFor()
    assert.equal(await worksPage.locator('.work-card').count(), 4, 'new image appears in an already open works page')
    assert.equal(await worksPage.locator('.work-card').first().locator('h3').innerText(), '徐州云龙山宣传图')
    fail = true
    await send('生成失败的测试图')
    await design.locator('.generation-error').waitFor()
    assert.equal(await worksPage.locator('.work-card').count(), 4, 'failed generations must not create a work')
    fail = false
    await design.getByRole('button', { name: '重新生成（使用额度）' }).click()
    await worksPage.getByRole('heading', { name: '生成失败的测试图' }).waitFor()
    assert.equal(await worksPage.locator('.work-card').count(), 5)
    await worksPage.reload()
    assert.equal(await worksPage.locator('.work-card').count(), 5)
    await worksPage.getByPlaceholder('搜索作品标题...').fill('云龙山')
    assert.equal(await worksPage.locator('.work-card').count(), 1)
    await worksPage.locator('.work-img').click()
    await worksPage.locator('.el-image-viewer__wrapper').waitFor()
    await worksPage.keyboard.press('Escape')
    await worksPage.getByRole('link', { name: '查看设计过程' }).click()
    await worksPage.waitForURL('**/design?history=*')
    await worksPage.locator('.result-image').first().waitFor()
    assert.match(await worksPage.locator('.user-bubble').first().innerText(), /云龙山/)
    await worksPage.goto(origin + '/profile')
    await worksPage.getByRole('button', { name: '我的作品', exact: true }).click()
    await worksPage.getByRole('heading', { name: '历史作品一' }).waitFor()
    assert.equal(await worksPage.locator('.content-card').count(), 5)
    assert.equal(await worksPage.locator('.stat-value').nth(2).innerText(), '5')
    await design.close()
    await worksPage.evaluate(() => localStorage.setItem('username', 'works_test_b'))
    await worksPage.goto(origin + '/works')
    await worksPage.getByRole('heading', { name: '暂无作品' }).waitFor()
    await worksPage.goto(origin + '/design')
    assert.equal(await worksPage.locator('.history-item').count(), 0, 'another account cannot inherit the old shared history')
    await worksPage.evaluate(() => localStorage.setItem('username', 'works_test_a'))
    await worksPage.goto(origin + '/works')
    assert.equal(await worksPage.locator('.work-card').count(), 5)
    await worksPage.setViewportSize({ width: 390, height: 844 })
    await worksPage.reload()
    assert.equal(await worksPage.locator('.work-card').count(), 5)
    assert.deepEqual(errors, [])
    console.log('PASS: historical backfill, automatic save, cross-tab updates, errors/retry, search/preview, profile counts, refresh, account isolation and mobile')
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
