import { currentDesignUser, readUserWorks, workImageContext, replaceWorkImage } from './designStorage.js'
import { generateJiangsuCultureImage } from '../AI-API/volcengine.js'

export function imageState(url) {
  return new Promise(resolve => {
    const image = new Image()
    const timer = setTimeout(() => finish('timeout'), 20000)
    function finish(state) { clearTimeout(timer); image.onload = image.onerror = null; resolve(state) }
    image.onload = () => finish(image.naturalWidth > 0 ? 'loaded' : 'failed')
    image.onerror = () => finish('failed')
    image.src = url
  })
}

export async function repairWorkImages(report, signal) {
  const owner = currentDesignUser()
  if (!owner) throw new Error('请先登录')
  const items = readUserWorks(owner).filter(work => work.image && work.source === 'volcengine')
  report('正在检查全部作品的图片…')
  const checks = await Promise.all(items.map(async work => ({ work, state: await imageState(work.image) })))
  const broken = checks.filter(item => item.state === 'failed').map(item => item.work)
  const uncertain = checks.filter(item => item.state === 'timeout').length
  let repaired = 0
  const failures = []
  for (const [index, work] of broken.entries()) {
    if (signal.aborted || currentDesignUser() !== owner) throw new Error('修复已停止，已完成的图片已保存')
    report(`正在重新生成 ${index + 1}/${broken.length}：${work.title}；已恢复 ${repaired} 张`)
    const jobKey = 'cultural.image-repair.' + encodeURIComponent(owner) + '.' + work.id
    let job
    try { job = JSON.parse(localStorage.getItem(jobKey) || 'null') } catch { /* malformed journal */ }
    try {
      // A completed paid response survives a storage failure and can be applied without paying again.
      if (!job?.url || job.previousUrl !== work.image) {
        const { params, content } = workImageContext(owner, work)
        job = { requestId: crypto.randomUUID(), previousUrl: work.image, status: 'pending' }
        localStorage.setItem(jobKey, JSON.stringify(job))
        job.url = await generateJiangsuCultureImage(params.culturalElement || work.description || work.title,
          params.style || work.style || '中国风', '2048x2048', {
            requestId: job.requestId, signal, purpose: params.purpose || '海报',
            description: ((params.description || work.description || work.title) + '\n原设计方案：' + content).slice(0, 3500)
          })
        job.status = 'generated'
        localStorage.setItem(jobKey, JSON.stringify(job))
      }
      if (await imageState(job.url) !== 'loaded') throw new Error('新图片已生成，但暂时无法读取；保留结果以便再次保存')
      replaceWorkImage(owner, work.id, work.image, job.url)
      repaired++
    } catch (error) {
      failures.push(`${work.title}：${error.message}`)
      if (error.status === 401 || error.status === 429 || signal.aborted) break
    }
  }
  const result = `已恢复 ${repaired} 张图片；${failures.length ? `${failures.length} 张未完成` : '本轮无生成失败'}${uncertain ? `；${uncertain} 张加载超时，未重复生成` : ''}。`
  report(result + (failures.length ? '\n' + failures.join('\n') : ''))
  return { repaired, failures, uncertain }
}
