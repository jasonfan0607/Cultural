import MarkdownIt from 'markdown-it'

export function safeWebUrl(value) {
  try {
    const url = new URL(value)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''
  } catch { return '' }
}
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: false })
markdown.validateLink = url => Boolean(safeWebUrl(url))
markdown.renderer.rules.link_open = (tokens, index, options, env, self) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return self.renderToken(tokens, index, options)
}
// Images are delivered by the image service, not arbitrary model-generated Markdown.
markdown.disable('image')
export const renderMarkdown = text => markdown.render(String(text || ''))

export function mergeSource(sources, source) {
  const normalize = value => String(value || '').normalize('NFKC').replace(/\s+/g, '').toLowerCase()
  const same = a => (a.type === source.type && String(a.id) === String(source.id)) ||
    (source.type === 'web' ? a.type === 'web' && a.url === source.url :
      a.type !== 'web' && normalize(a.title) === normalize(source.title) &&
      normalize(a.description) === normalize(source.description) && normalize(a.city) === normalize(source.city))
  const index = sources.findIndex(same)
  if (index < 0) sources.push(source)
  else sources[index] = { ...sources[index], ...source, id: sources[index].id }
}
export function uniqueSources(sources = []) {
  const result = []
  for (const source of sources) mergeSource(result, source)
  return result
}
