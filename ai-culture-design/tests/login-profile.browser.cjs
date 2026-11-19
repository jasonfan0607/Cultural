const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.FRONTEND_URL || 'http://127.0.0.1:4173'

;(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/auth/login', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: { token: 'test-token', userInfo: {
        username: 'profile_test', nickname: '个人中心测试', avatar: '/logo1.png'
      } } })
    }))
    await page.route('**/api/auth/logout', route => route.fulfill({
      contentType: 'application/json', body: JSON.stringify({ code: 200 })
    }))
    await page.goto(origin + '/login')
    await page.getByPlaceholder('请输入用户名').fill('profile_test')
    await page.getByPlaceholder('请输入密码', { exact: true }).fill('test-password')
    await page.locator('.submit-btn').click()
    await page.waitForURL('**/profile')
    await page.locator('.profile-card').first().waitFor()
    assert.equal(await page.locator('.user-area').count(), 1, 'login must immediately show the profile entry without reloading')
    assert.equal(await page.locator('.login-area').count(), 0)
    await page.locator('.profile-page').waitFor({ state: 'visible' })
    const clearOfHeader = async selector => {
      const nav = await page.locator('.navbar').boundingBox()
      const content = await page.locator(selector).first().boundingBox()
      assert.ok(content.y >= nav.y + nav.height, `${selector} must be below the fixed navigation`)
    }
    await clearOfHeader('.profile-card')
    assert.ok(await page.getByRole('heading', { name: '我的收藏' }).isVisible())
    await page.locator('.user-dropdown').click()
    await page.getByRole('menuitem', { name: '个人主页' }).click()
    await page.screenshot({ path: 'tests/artifacts/profile-login-fixed.png', fullPage: true })
    await page.locator('.nav-menu').getByRole('link', { name: '我的作品', exact: true }).click()
    await page.waitForURL('**/works')
    await page.getByRole('heading', { name: '暂无作品' }).waitFor()
    assert.equal(await page.locator('.el-pagination').count(), 0, 'empty works should not show a misleading page 1')
    await clearOfHeader('.works-header')
    await page.getByPlaceholder('搜索作品标题...').fill('不存在的作品')
    await page.getByRole('heading', { name: '没有找到匹配的作品' }).waitFor()
    await page.screenshot({ path: 'tests/artifacts/works-empty-fixed.png', fullPage: true })
    // Corrupt saved works must show a usable empty state, not crash rendering.
    await page.evaluate(() => localStorage.setItem('userWorks_profile_test', '{broken'))
    await page.reload()
    await page.getByRole('heading', { name: '暂无作品' }).waitFor()
    await page.evaluate(() => localStorage.setItem('userWorks_profile_test', JSON.stringify([{ id: 1, title: '测试作品' }])))
    await page.reload()
    await page.getByRole('heading', { name: '测试作品' }).waitFor()
    await page.getByPlaceholder('搜索作品标题...').fill('不存在')
    await page.getByRole('heading', { name: '没有找到匹配的作品' }).waitFor()
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(origin + '/profile')
    await page.locator('.profile-card').first().waitFor()
    await clearOfHeader('.profile-card')
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await page.waitForURL('**/login')
    await page.goto(origin + '/works')
    assert.equal(await page.locator('.user-area').count(), 0, 'logout must clear the navigation user entry')
    assert.equal(await page.locator('.login-area').count(), 1)
    assert.deepEqual(errors, [])
    console.log('PASS: login/profile entry, fixed-header clearance, empty/malformed works, mobile layout and logout')
  } finally {
    await browser.close()
  }
})().catch(error => { console.error(error); process.exitCode = 1 })
