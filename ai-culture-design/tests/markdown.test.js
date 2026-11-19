import { test } from 'node:test'
import assert from 'node:assert/strict'
import { renderMarkdown, uniqueSources, safeWebUrl } from '../src/utils/markdown.js'

test('renders headings, emphasis, lists and tables; keeps model HTML inert', () => {
  const html = renderMarkdown('# 方案\n\n**云锦**\n\n- 纹样\n\n| 配色 | 用途 |\n| --- | --- |\n| 金 | 点缀 |')
  for (const tag of ['<h1>', '<strong>', '<ul>', '<table>']) assert.ok(html.includes(tag))
  const unsafe = renderMarkdown('<script>alert(1)</script>\n[点我](javascript:alert(1))\n![图片](https://evil.example/track)')
  assert.ok(!unsafe.includes('<script>'))
  assert.ok(!unsafe.includes('href="javascript:'))
  assert.ok(!unsafe.includes('<img'))
  assert.match(renderMarkdown('[来源](https://example.org)'), /rel="noopener noreferrer"/)
  assert.equal(safeWebUrl('data:text/html,bad'), '')
})
test('deduplicates content even across IDs, keeps distinct web URLs', () => {
  const a = { id: 2, type: 'heritage', title: '南京云锦', description: '丝织工艺', city: '南京' }
  const sources = uniqueSources([a, { ...a, id: 12 }, { id: 'a', type: 'web', url: 'https://a.example' }, { id: 'b', type: 'web', url: 'https://b.example' }])
  assert.equal(sources.length, 3)
  assert.equal(sources[0].id, 2)
})
