const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.FRONTEND_URL || 'http://127.0.0.1:4173'

;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    // Keep tests deterministic and independent of remote image providers.
    await context.route('**/*', route => {
      if (new URL(route.request().url()).origin === origin) return route.continue()
      if (route.request().resourceType() === 'image') return route.fulfill({ path: 'public/logo1.png', contentType: 'image/png' })
      return route.abort()
    })
    const page = await context.newPage()
    const errors = []
    context.on('page', p => p.on('pageerror', e => errors.push(e.message)))
    page.on('pageerror', e => errors.push(e.message))
    await page.goto(origin + '/login')
    await page.evaluate(() => {
      localStorage.setItem('isLogin', 'true')
      localStorage.setItem('username', 'collection_test_a')
      localStorage.setItem('avatar', '/logo1.png')
    })
    await page.goto(origin + '/profile')
    await page.getByRole('heading', { name: '暂无收藏' }).waitFor()
    assert.equal(await page.locator('.content-count').innerText(), '0 项')
    const gallery = await context.newPage()
    await gallery.goto(origin + '/gallery')
    const first = gallery.locator('.work-card').filter({ hasText: '苏州拙政园春景' })
    await first.hover()
    await first.getByRole('button', { name: '收藏', exact: true }).click()
    await first.getByRole('button', { name: '取消收藏', exact: true }).waitFor()
    await page.getByRole('heading', { name: '苏州拙政园春景' }).waitFor()
    assert.equal(await page.locator('.content-count').innerText(), '1 项', 'another open profile tab updates immediately')
    await gallery.reload()
    assert.equal(await first.getByRole('button', { name: '取消收藏', exact: true }).getAttribute('aria-pressed'), 'true')
    const second = gallery.locator('.work-card').filter({ hasText: '南京中山陵秋色' })
    await second.click()
    await gallery.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).click()
    await gallery.locator('.detail-dialog').getByRole('button', { name: '已收藏', exact: true }).waitFor()
    await page.getByRole('heading', { name: '南京中山陵秋色' }).waitFor()
    await page.reload()
    assert.equal(await page.locator('.content-card').count(), 2)
    assert.equal(await page.locator('.stat-value').first().innerText(), '2')
    assert.equal(await page.locator('.content-card').filter({ hasText: '苏州拙政园春景' }).locator('img').getAttribute('src'), await first.locator('.work-image img').getAttribute('src'))
    // Opening a saved card must return to that gallery item's detail.
    await page.getByRole('heading', { name: '苏州拙政园春景' }).click()
    await page.waitForURL('**/gallery?work=1')
    await page.locator('.detail-dialog').getByRole('button', { name: '已收藏', exact: true }).waitFor()
    await page.locator('.detail-dialog').getByRole('button', { name: '已收藏', exact: true }).click()
    await page.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).waitFor()
    await page.goto(origin + '/profile')
    assert.equal(await page.locator('.content-card').count(), 1)
    await page.getByRole('button', { name: '取消收藏', exact: true }).click()
    await page.getByRole('heading', { name: '暂无收藏' }).waitFor()
    await gallery.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).waitFor()
    await gallery.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).click()
    await page.getByRole('heading', { name: '南京中山陵秋色' }).waitFor()
    // Account B must not inherit account A's saved items.
    await page.evaluate(() => localStorage.setItem('username', 'collection_test_b'))
    await page.reload()
    await page.getByRole('heading', { name: '暂无收藏' }).waitFor()
    await gallery.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).waitFor()
    // Failed persistence must not show a successful/selected collection.
    await gallery.evaluate(() => {
      window.originalSetItem = Storage.prototype.setItem
      Storage.prototype.setItem = function (key, value) {
        if (key.startsWith('galleryCollections_v1_')) throw new DOMException('Full', 'QuotaExceededError')
        return window.originalSetItem.call(this, key, value)
      }
    })
    await gallery.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).click()
    await gallery.getByText('收藏保存失败，请检查浏览器存储空间后重试', { exact: true }).waitFor()
    assert.equal(await gallery.locator('.detail-dialog').getByRole('button', { name: '收藏', exact: true }).getAttribute('aria-pressed'), 'false')
    await gallery.evaluate(() => { Storage.prototype.setItem = window.originalSetItem })
    await page.evaluate(() => localStorage.setItem('username', 'collection_test_a'))
    await page.reload()
    await page.getByRole('heading', { name: '南京中山陵秋色' }).waitFor()
    assert.equal(await page.locator('.content-card').count(), 1)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await page.getByRole('heading', { name: '南京中山陵秋色' }).waitFor()
    assert.deepEqual(errors, [])
    console.log('PASS: gallery/profile collections, refresh, both entry points, cross-tab sync, removal, account isolation, quota errors and mobile')
  } finally {
    await browser.close()
  }
})().catch(error => { console.error(error); process.exitCode = 1 })
