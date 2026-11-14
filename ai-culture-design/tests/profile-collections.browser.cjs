const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')

;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => {
      localStorage.setItem('isLogin', 'true')
      localStorage.setItem('username', '收藏封面测试')
      localStorage.setItem('avatar', '/logo1.png')
    })
    // The collection covers must work without any external image service.
    await page.route('**/*', route => {
      if (new URL(route.request().url()).hostname === '127.0.0.1') return route.continue()
      return route.abort()
    })
    await page.goto('http://127.0.0.1:5175/profile')
    const images = page.locator('.tab-content .card-image img')
    await images.first().waitFor()
    assert.equal(await images.count(), 3)
    for (const image of await images.all()) {
      await image.scrollIntoViewIfNeeded()
      await image.evaluate(image => image.decode())
      assert.ok(await image.evaluate(image => image.complete && image.naturalWidth > 0))
      assert.match(await image.getAttribute('src'), /^\/images\/collections\//)
    }
    assert.deepEqual(await images.evaluateAll(images => images.map(image => image.alt)), ['南京云锦', '苏州刺绣', '宜兴紫砂'])
    if (process.env.COLLECTIONS_SCREENSHOT) {
      await page.locator('.tabs-container').screenshot({ path: process.env.COLLECTIONS_SCREENSHOT })
    }
    console.log('PASS: all three themed covers load with external requests blocked')

    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await images.first().waitFor()
    for (const image of await images.all()) {
      await image.scrollIntoViewIfNeeded()
      await image.evaluate(image => image.decode())
    }
    console.log('PASS: covers load after refresh on a mobile viewport')

    let failedRequests = 0
    await page.route('**/images/collections/nanjing-yunjin.jpg', route => {
      failedRequests++
      return route.abort()
    })
    await page.reload()
    await page.locator('.card-image').first().scrollIntoViewIfNeeded()
    const fallback = page.getByRole('img', { name: '南京云锦：封面暂不可用' })
    await fallback.waitFor()
    assert.equal(await fallback.locator('small').innerText(), '封面暂不可用')
    assert.equal(await images.count(), 2)
    assert.equal(failedRequests, 1, 'a missing cover must not cause a retry loop')
    assert.deepEqual(errors, [])
    console.log('PASS: missing images show a labeled fallback without errors or retry loops')
  } finally {
    await browser.close()
  }
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
