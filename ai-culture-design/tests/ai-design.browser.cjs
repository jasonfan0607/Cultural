const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright');
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const pending = [];
    await page.route('**/api/v3/images/generations', route => { pending.push(route); });
    await page.route('**/qa-design.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><rect width="512" height="512" fill="#8b1538"/><text x="120" y="256" fill="white" font-size="32">Design preview</text></svg>' }));
    const nextRequest = async () => { for(let i=0;i<100 && !pending.length;i++) await new Promise(r=>setTimeout(r,50)); assert.ok(pending.length,'expected image request'); return pending.shift(); };
    const success = route => route.fulfill({contentType:'application/json',body:JSON.stringify({data:[{url:'http://127.0.0.1:5175/qa-design.svg'}]})});
    const send = async text => { await page.locator('textarea').fill(text); await page.locator('.send-btn').click(); return nextRequest(); };
    const settled = () => page.waitForFunction(() => !document.querySelector('.loading-indicator'));
    await page.goto('http://127.0.0.1:5175/design');
    assert.equal(await page.locator('.history-item').count(),0,'no fabricated history');
    let request = await send('设计一个昆曲主题的现代简约文化衫');
    assert.ok(await page.locator('.reply-text').innerText());
    assert.match(await page.locator('.loading-indicator').innerText(),/正在生成图片/);
    assert.notEqual(await page.locator('.dot').first().evaluate(el=>getComputedStyle(el).backgroundColor),'rgba(0, 0, 0, 0)');
    assert.match(request.request().postDataJSON().prompt,/文化衫/);
    await success(request); await settled();
    await page.locator('.result-image').waitFor();
    await page.getByRole('button',{name:'新建对话'}).click();
    await page.locator('.history-item').first().click();
    assert.equal(await page.locator('.result-image').count(),1);
    assert.match(await page.locator('.user-bubble').innerText(),/昆曲/);
    await page.reload(); await page.locator('.history-item').first().click();
    assert.equal(await page.locator('.result-image').count(),1,'image restored after reload');
    console.log('PASS: visible progress, full prompt, history switching and reload');
    request = await send('把文化衫改成白色');
    await request.fulfill({status:401,contentType:'application/json',body:JSON.stringify({error:{message:'invalid'}})}); await settled();
    assert.match(await page.locator('.generation-error').innerText(),/密钥无效/);
    await page.getByRole('button',{name:'重试生成图片'}).click(); request=await nextRequest(); await success(request); await settled();
    assert.equal(await page.locator('.generation-error').count(),0);
    assert.equal(await page.locator('.history-item').count(),1,'continuation updates same conversation');
    console.log('PASS: failure message and retry');
    const stale = await send('生成一个尚未完成的设计');
    await page.getByRole('button',{name:'新建对话'}).click();
    assert.equal(await page.locator('.loading-indicator').count(),0);
    request = await send('南京云锦主题包装'); await success(request); await settled();
    await success(stale).catch(()=>{});
    assert.equal(await page.locator('.user-bubble').count(),1,'stale result cannot enter new conversation');
    assert.match(await page.locator('.user-bubble').innerText(),/南京云锦/);
    await page.locator('.history-item').nth(1).click();
    assert.match(await page.locator('.generation-error').last().innerText(),/已取消/);
    assert.equal(await page.locator('.history-item').count(),2);
    console.log('PASS: cancellation and stale response isolation');
    assert.deepEqual(errors,[]);

    console.log('PASS: no browser runtime errors');
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1});

