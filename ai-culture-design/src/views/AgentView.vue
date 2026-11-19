<script setup>
import { ref, computed, nextTick, onBeforeUnmount } from 'vue'
import { chatAgent, deleteSession, requestId } from '../AI-API/agent.js'
import MarkdownText from '../components/MarkdownText.vue'
import { historyOwner, historySnapshot, saveHistory, loadHistory, removeHistory, isHistoryStorageKey, SESSION_TTL } from '../utils/agent-history.js'
import { mergeSource, uniqueSources, safeWebUrl } from '../utils/markdown.js'

const input = ref(''), messages = ref([]), sessionId = ref(''), busy = ref(false), allowImage = ref(false)
const transcript = ref(null)
const historyOpen = ref(!window.matchMedia('(max-width: 900px)').matches), historyItems = ref([]), currentId = ref(''), deleteId = ref('')
const historyError = ref(''), historyNotice = ref(''), readOnly = ref(false)
let owner = '', active, saveTimer, createdAt = 0, updatedAt = 0, sessionTouchedAt = 0
try { owner = historyOwner(); historyItems.value = loadHistory(owner) }
catch { historyError.value = '无法读取本机历史记录，请检查浏览器存储权限。' }
const currentTitle = computed(() => historyItems.value.find(item => item.id === currentId.value)?.title)
const suggestions = ['南京云锦有哪些适合包装设计的纹样？', '结合苏绣，为茶叶礼盒设计一套现代简约方案。', '比较昆曲与蓝印花布，推荐一个校园文创主题。']
const scroll = () => nextTick(() => transcript.value?.scrollTo({ top: transcript.value.scrollHeight, behavior: 'smooth' }))
const historyDate = value => new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(value)
function persist() {
  clearTimeout(saveTimer); saveTimer = null
  if (!currentId.value || !messages.value.length || !owner) return
  try {
    const record = historySnapshot({ id: currentId.value, sessionId: sessionId.value, createdAt, updatedAt,
      sessionTouchedAt, messages: messages.value })
    saveHistory(owner, record)
    historyItems.value = loadHistory(owner)
    historyError.value = ''
  } catch { historyError.value = '历史记录未能保存，浏览器存储可能已满或被禁用。请先保留当前内容，或删除不需要的记录。' }
}
function scheduleSave() {
  updatedAt = Date.now()
  if (!saveTimer) saveTimer = setTimeout(() => { saveTimer = null; persist() }, 600)
}
function stop() { active?.controller.abort() }
function leaveCurrent() {
  clearTimeout(saveTimer); saveTimer = null
  if (active) {
    active.reply.pending = false; active.reply.status = ''
    active.reply.error = '已停止接收，已保留收到的内容。'
    active.controller.abort()
    active = null; busy.value = false
  }
  persist()
}
function clearCurrent() {
  currentId.value = ''; sessionId.value = ''; messages.value = []; input.value = ''
  readOnly.value = false; deleteId.value = ''; historyNotice.value = ''
  createdAt = 0; updatedAt = 0; sessionTouchedAt = 0
}
function newChat() {
  leaveCurrent(); clearCurrent()
  nextTick(() => transcript.value?.scrollTo({ top: 0, behavior: 'instant' }))
}
function openHistory(id) {
  if (id === currentId.value) return
  leaveCurrent()
  try { historyItems.value = loadHistory(owner) }
  catch { historyError.value = '无法读取历史记录，请重试。'; return }
  const record = historyItems.value.find(item => item.id === id)
  if (!record) return
  currentId.value = record.id; sessionId.value = record.sessionId || ''
  messages.value = JSON.parse(JSON.stringify(record.messages)).map(m => ({ ...m, pending: false, status: '' }))
  createdAt = record.createdAt; updatedAt = record.updatedAt; sessionTouchedAt = record.sessionTouchedAt || 0
  readOnly.value = Boolean(sessionId.value && Date.now() - sessionTouchedAt >= SESSION_TTL)
  input.value = ''; deleteId.value = ''; historyNotice.value = ''
  scroll()
}
async function deleteConversation(id) {
  const record = historyItems.value.find(item => item.id === id)
  if (!record) return
  if (id === currentId.value) leaveCurrent()
  try { removeHistory(owner, id); historyItems.value = loadHistory(owner) }
  catch { historyError.value = '删除失败，请检查浏览器存储权限后重试。'; return }
  if (currentId.value === id) clearCurrent()
  deleteId.value = ''; historyNotice.value = '已删除这条历史对话。'
  if (record.sessionId) deleteSession(record.sessionId).catch(() => {})
}
function onStorage(event) {
  if (event.key === null) {
    clearTimeout(saveTimer); saveTimer = null; stop(); active = null; busy.value = false; clearCurrent()
    owner = ''; historyItems.value = []
  } else if (['auth_token', 'username'].includes(event.key)) {
    leaveCurrent(); clearCurrent()
    try { owner = historyOwner(); historyItems.value = loadHistory(owner) } catch { historyItems.value = [] }
  } else if (isHistoryStorageKey(event.key)) {
    try {
      const records = loadHistory(owner)
      if (currentId.value && historyItems.value.some(r => r.id === currentId.value) && !records.some(r => r.id === currentId.value)) {
        // Another tab deleted this record. Do not resurrect it from a late SSE callback.
        clearTimeout(saveTimer); saveTimer = null; stop(); active = null; busy.value = false; clearCurrent()
        historyNotice.value = '这条对话已在其他页面删除。'
      }
      historyItems.value = records
    } catch { historyError.value = '历史记录同步失败，请刷新后重试。' }
  }
}
window.addEventListener('storage', onStorage)
window.addEventListener('pagehide', leaveCurrent)
async function send(text = input.value) {
  if (busy.value || readOnly.value || !text.trim()) return
  if (sessionId.value && sessionTouchedAt && Date.now() - sessionTouchedAt >= SESSION_TTL) { readOnly.value = true; return }
  const message = text.trim()
  if (message.length > 4000) return
  if (!currentId.value) { currentId.value = requestId(); createdAt = Date.now() }
  updatedAt = Date.now(); historyNotice.value = ''
  messages.value.push({ role: 'user', content: message })
  const reply = { role: 'assistant', content: '', sources: [], images: [], status: '', error: '', message,
    requestId: requestId(), allowImage: allowImage.value }
  messages.value.push(reply)
  input.value = ''
  persist()
  await run(messages.value.at(-1))
}
async function run(reply) {
  if (busy.value || readOnly.value) return
  busy.value = true; reply.error = ''; reply.content = ''; reply.sources = []; reply.status = '正在连接文化助手'
  reply.pending = true
  const request = { controller: new AbortController(), reply }; active = request
  updatedAt = Date.now(); persist()
  scroll()
  try {
    await chatAgent({ sessionId: sessionId.value || null, requestId: reply.requestId,
      message: reply.message, allowImage: reply.allowImage }, {
      signal: request.controller.signal,
      onEvent(event, data) {
        if (active !== request) return
        if (event === 'session') { sessionId.value = data.sessionId; sessionTouchedAt = Date.now() }
        if (event === 'text-start') reply.content = ''
        if (event === 'delta') { reply.content += data.text; reply.status = '正在生成答复…' }
        if (event === 'status' || event === 'tool' || event === 'warning') reply.status = data.message
        if (event === 'source') {
          mergeSource(reply.sources, data)
        }
        if (event === 'image' && !reply.images.includes(data.url)) reply.images.push(data.url)
        if (event === 'done') { reply.content = data.content; reply.sources = uniqueSources(data.sources); reply.images = data.images; reply.pending = false; sessionTouchedAt = Date.now() }
        scheduleSave()
        scroll()
      }
    })
  } catch (error) {
    if (active === request && error.status === 404 && sessionId.value) { readOnly.value = true; sessionTouchedAt = 0 }
    if (active === request) reply.error = error.name === 'AbortError'
      ? '已停止接收。图片任务可能仍在完成；服务运行期间，30 分钟内同一请求重试不会重复生图。'
      : error.message
  } finally {
    if (active === request) { active = null; busy.value = false; reply.pending = false; reply.status = ''; updatedAt = Date.now(); persist(); scroll() }
  }
}
onBeforeUnmount(() => { leaveCurrent(); window.removeEventListener('storage', onStorage); window.removeEventListener('pagehide', leaveCurrent) })
</script>

<template>
  <main class="culture-agent">
    <aside class="agent-intro">
      <span class="agent-seal" aria-hidden="true">文</span>
      <p class="eyebrow">JIANGSU CULTURE · CREATIVE COMPANION</p>
      <h1>循文脉，<br>生新意。</h1>
      <p class="intro-copy">从一段文化故事，到一份设计灵感。一起寻找江苏传统里的当代表达。</p>
      <button class="new-session" @click="newChat">＋ 开启新的探索</button>
      <section class="history-panel" aria-label="历史对话">
        <button class="history-toggle" :aria-expanded="historyOpen" aria-controls="agent-history-list" @click="historyOpen = !historyOpen">
          <span>历史对话 <small>{{ historyItems.length.toString().padStart(2, '0') }}</small></span><span aria-hidden="true">{{ historyOpen ? '−' : '＋' }}</span>
        </button>
        <div v-show="historyOpen" id="agent-history-list">
          <p class="history-caption">拾起上一次的灵感</p>
          <p v-if="!historyItems.length" class="history-empty">还没有历史对话。开始探索后，对话会自动保存在这里。</p>
          <ul v-else class="history-list">
            <li v-for="record in historyItems" :key="record.id" class="history-item" :class="{ selected: currentId === record.id }">
              <div class="history-row">
                <button class="history-select" :aria-current="currentId === record.id ? 'true' : undefined" :title="record.title" @click="openHistory(record.id)">
                  <span class="history-title">{{ record.title }}</span><small>{{ historyDate(record.updatedAt) }} · {{ record.messages.filter(m => m.role === 'user').length }} 轮探索</small>
                </button>
                <button class="history-delete" :aria-label="`删除对话：${record.title}`" title="删除这条对话" @click="deleteId = record.id">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 10v7M14 10v7"/></svg>
                </button>
              </div>
              <div v-if="deleteId === record.id" class="history-confirm" role="alert"><p>删除这条对话？删除后无法恢复。</p><button @click="deleteId = ''">取消</button><button class="confirm-delete" @click="deleteConversation(record.id)">确认删除</button></div>
            </li>
          </ul>
          <p class="history-storage">记录保存在本机浏览器，按账号区分。</p>
        </div>
        <p v-if="historyError" class="history-error" role="alert">{{ historyError }}</p>
      </section>
      <div v-if="!historyItems.length" class="agent-notes"><span>01 / 寻文化依据</span><span>02 / 理设计思路</span><span>03 / 绘创意效果</span></div>
      <p class="session-note">历史记录可随时回看。在线上下文闲置 30 分钟后过期，已保存的记录仍会保留。</p>
      <RouterLink to="/design" class="design-link">前往图片设计工作台 ↗</RouterLink>
    </aside>

    <section class="agent-workspace" aria-label="文化助手对话">
      <header class="agent-heading"><div class="conversation-heading"><span class="status-dot"></span> 江苏文化助手 <small v-if="currentTitle" :title="currentTitle">{{ currentTitle }}</small></div><RouterLink to="/login">登录账号 ↗</RouterLink></header>
      <p v-if="historyNotice" class="history-notice" role="status">{{ historyNotice }}</p>
      <div ref="transcript" class="transcript" aria-live="polite" aria-relevant="additions text" role="log">
        <div v-if="!messages.length" class="agent-welcome">
          <p class="eyebrow">从一个好奇开始</p><h2>你想让哪一种文化，<br>走进今天的生活？</h2>
          <p>可以聊非遗、查资料，也可以一起构思包装、海报与文创。</p>
          <button v-for="(question, i) in suggestions" :key="question" class="suggestion" @click="send(question)"><span>0{{ i + 1 }}</span>{{ question }}<b aria-hidden="true">↗</b></button>
        </div>
        <article v-for="(item, index) in messages" :key="index" class="agent-message" :class="item.role">
          <span class="speaker">{{ item.role === 'user' ? '你' : '文化助手' }}</span>
          <MarkdownText v-if="item.role === 'assistant' && item.content" class="message-content" :content="item.content" />
          <p v-else-if="item.content" class="message-content">{{ item.content }}</p>
          <p v-if="item.status" class="tool-status"><span class="status-dot"></span>{{ item.status }}</p>
          <div v-if="item.sources?.length" class="source-list"><p class="source-label">参考资料 · {{ item.sources.length }}</p>
            <details v-for="source in item.sources" :key="`${source.type}:${source.id}`"><summary>{{ source.title }} <small>{{ source.type === 'web' ? '联网资料' : `[${source.type}:${source.id}]` }}</small></summary><p>{{ source.content || source.description || '暂无摘要' }}</p><template v-if="source.type === 'web'"><a v-if="safeWebUrl(source.url)" :href="safeWebUrl(source.url)" target="_blank" rel="noopener noreferrer">打开来源 · {{ source.domain }} ↗</a><p><small>{{ source.engine }} · 搜索摘要（非全文） · {{ source.retrievedAt ? new Date(source.retrievedAt).toLocaleString('zh-CN') : '时间未知' }}{{ source.stale ? " · 历史缓存，本次未实时核实" : source.cached ? " · 检索缓存" : "" }}</small></p><p v-if="source.searchNotice"><small>{{ source.searchNotice }}</small></p></template><small v-else>{{ source.city }} · 平台公开文化资料</small></details>
          </div>
          <a v-for="url in (Array.isArray(item.images) ? item.images : []).filter(safeWebUrl)" :key="url" :href="url" target="_blank" rel="noopener noreferrer" class="result-image"><img :src="url" alt="根据文化设计方案生成的效果图" loading="lazy"><span>查看原图 ↗</span></a>
          <div v-if="item.error" class="agent-error" role="alert"><p>{{ item.error }}</p><button v-if="index === messages.length - 1 && !readOnly" :disabled="busy" @click="run(item)">重试此请求</button><button :disabled="busy" @click="newChat">新建对话</button></div>
        </article>
      </div>
      <div v-if="readOnly" class="history-readonly"><p>正在查看历史记录</p><span>在线上下文已过期，文字、来源和图片记录仍可查看。</span><button class="send-button" @click="newChat">开启新对话 ↗</button></div>
      <form v-else class="agent-composer" @submit.prevent="send()">
        <label for="agent-input" class="composer-label">说说你的想法</label>
        <textarea id="agent-input" v-model="input" maxlength="4000" rows="3" placeholder="例如：结合南京云锦，为茶叶礼盒设计一套包装……" :disabled="busy" @keydown.ctrl.enter.prevent="send()"></textarea>
        <div class="composer-actions"><label class="image-consent"><input v-model="allowImage" type="checkbox" :disabled="busy"> 本轮允许生成一张图片 <small>会使用生图额度</small></label><button v-if="busy" type="button" class="send-button" @click="stop">停止生成</button><button v-else class="send-button" :disabled="!input.trim()" type="submit">开始探索 ↗</button></div>
        <p class="composer-footnote">Ctrl + Enter 发送 · 创意建议由 AI 生成，文化事实请结合资料核对。</p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.culture-agent{--ink:#24463d;--muted:#66776b;--paper:#faf8f2;display:grid;grid-template-columns:300px minmax(0,1fr);max-width:1320px;margin:40px auto 60px;padding:0 24px;color:var(--ink);gap:40px;min-height:760px}
.agent-intro{padding:30px 0}.agent-seal{display:grid;place-items:center;width:44px;height:48px;background:#9d4d37;color:#fff6e4;font:28px 'STSong','SimSun',serif;border-radius:2px;transform:rotate(-4deg)}
.eyebrow{font-size:10px;letter-spacing:2px;margin:28px 0 22px;color:var(--muted)}h1{font:56px/1.45 'STSong','SimSun',serif;letter-spacing:5px;margin:0 0 20px}.intro-copy{line-height:1.9;font-size:14px;max-width:250px;color:var(--muted)}
button,a,textarea,input{outline-offset:4px}button{font:inherit;cursor:pointer}button:disabled{opacity:.5;cursor:not-allowed}.new-session{width:100%;padding:15px;border:1px solid #a9b7a7;background:transparent;color:var(--ink);margin:24px 0 38px;text-align:left;border-radius:4px}.new-session:hover{background:#ecf0e8}
.agent-notes{display:grid;gap:16px;font-size:12px;letter-spacing:1px;border-top:1px solid #d7ddd1;padding-top:26px}.session-note{font-size:12px;line-height:1.9;color:var(--muted);margin:30px 0}.design-link{color:var(--ink);font-size:13px}
.agent-workspace{background:var(--paper);border:1px solid #e2e5d9;border-radius:14px;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 16px 50px #24463d08;height:min(850px,calc(100dvh - 125px));min-height:660px}.agent-heading{display:flex;justify-content:space-between;align-items:center;padding:22px 28px;border-bottom:1px solid #e2e5d9;font-size:13px}.agent-heading a{font-size:12px;color:var(--muted)}.status-dot{display:inline-block;width:6px;height:6px;background:#6e8a66;border-radius:50%;margin-right:10px;vertical-align:middle}
.transcript{flex:1;overflow-y:auto;overscroll-behavior:contain;padding:28px 36px}.agent-welcome{max-width:570px;margin:12px auto 35px}.agent-welcome h2{font:32px/1.6 'STSong','SimSun',serif;letter-spacing:2px;margin:0 0 18px}.agent-welcome>p:not(.eyebrow){font-size:13px;color:var(--muted);line-height:1.9;margin-bottom:30px}.suggestion{display:flex;gap:15px;align-items:center;width:100%;text-align:left;border:0;border-top:1px solid #dfe3d7;background:transparent;padding:18px 0;color:var(--ink);font-size:13px;line-height:1.7}.suggestion:hover{background:#f0f2e9}.suggestion span{color:#9d4d37;font-family:Georgia,serif}.suggestion b{margin-left:auto}
.agent-message{margin:0 0 28px}.speaker{font-size:11px;letter-spacing:2px;color:var(--muted)}.message-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;font-size:14px;margin:10px 0}.user{background:#edf0e7;padding:16px 20px;border-radius:6px;margin-left:40px}.tool-status{font-size:13px;color:var(--muted)}.source-list{margin:18px 0;font-size:12px}.source-label{color:var(--muted)}details{padding:12px;border:1px solid #dce1d4;border-radius:4px;margin:8px 0}summary{cursor:pointer;line-height:1.7}summary small{color:var(--muted)}details p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;max-height:260px;overflow:auto}.result-image{display:block;max-width:420px;color:var(--ink);font-size:12px;margin:15px 0}.result-image img{width:100%;border-radius:6px}.agent-error{padding:12px 16px;background:#f8e9df;color:#914e37;font-size:13px;line-height:1.8}.agent-error button{background:transparent;border:1px solid #c3a38d;color:inherit;margin:5px 10px 0 0;padding:5px 10px;border-radius:3px}
.agent-composer{padding:18px 28px 15px;border-top:1px solid #e2e5d9;background:#fffdf8}.composer-label{font-size:11px;color:var(--muted)}textarea{display:block;resize:vertical;max-height:180px;width:100%;box-sizing:border-box;border:0;background:transparent;font:14px/1.8 inherit;color:var(--ink);padding:10px 0;min-height:80px}.composer-actions{display:flex;gap:14px;justify-content:space-between;align-items:center}.image-consent{font-size:12px;display:flex;align-items:center;gap:5px;flex-wrap:wrap}.image-consent input{accent-color:var(--ink)}.image-consent small{color:var(--muted);font-size:10px}.send-button{background:var(--ink);border:0;color:#fffdf5;padding:12px 20px;border-radius:4px;white-space:nowrap;font-size:13px}.composer-footnote{font-size:10px;color:var(--muted);margin:12px 0 0}
@media(max-width:900px){.culture-agent{grid-template-columns:1fr;gap:16px;margin-top:15px;padding:0 15px}.agent-intro{padding:12px 0;display:flex;align-items:center;gap:15px}.agent-intro h1{font-size:24px;line-height:1.3;margin:0;letter-spacing:2px}.agent-intro h1 br{display:none}.agent-intro .eyebrow,.intro-copy,.agent-notes,.session-note,.design-link{display:none}.new-session{width:auto;margin:0 0 0 auto;font-size:12px;padding:12px}.agent-workspace{min-height:640px;height:calc(100dvh - 180px)}.transcript{padding:20px}.agent-welcome h2{font-size:26px}.agent-composer{padding:15px}.agent-heading{padding:18px}.composer-actions{align-items:flex-end}.image-consent small{display:none}.user{margin-left:20px}}
.culture-agent{margin-top:110px}
.assistant .message-content{white-space:normal}
@media(max-width:900px){.culture-agent{margin-top:150px}}
.new-session{margin-bottom:22px}
.history-panel{border-top:1px solid #d7ddd1;margin-bottom:24px;min-width:0}
.history-toggle{display:flex;align-items:center;justify-content:space-between;width:100%;border:0;background:transparent;color:var(--ink);padding:18px 0 10px;font-size:14px;text-align:left;letter-spacing:1px}
.history-toggle small{font:12px Georgia,serif;color:#9d4d37;margin-left:8px}.history-caption{font-size:11px;color:var(--muted);margin:0 0 14px;letter-spacing:1px}
.history-list{list-style:none;padding:0;margin:0;max-height:340px;overflow:auto;scrollbar-width:thin}.history-item{margin-bottom:7px;border:1px solid transparent;border-radius:5px}.history-item.selected{background:#edf0e7;border-color:#d3ddcc}.history-row{display:flex;align-items:center;gap:4px}.history-select{flex:1;min-width:0;text-align:left;border:0;background:transparent;color:var(--ink);padding:12px 10px}.history-title{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:13px;line-height:1.7}.history-select small{display:block;color:var(--muted);font-size:10px;margin-top:5px}.history-item:hover{background:#f0f2e9}.history-delete{display:grid;place-items:center;flex-shrink:0;width:34px;height:36px;margin-right:4px;border:0;background:transparent;color:#788676;border-radius:4px}.history-delete:hover{background:#f5e5dd;color:#9d4d37}.history-empty,.history-storage{font-size:12px;line-height:1.8;color:var(--muted)}.history-storage{font-size:10px;margin:15px 0 0}.history-confirm{padding:0 10px 12px;color:#914e37;font-size:12px;line-height:1.7}.history-confirm button{border:1px solid #c3a38d;color:inherit;background:transparent;border-radius:3px;padding:5px 12px;margin-right:8px}.history-confirm .confirm-delete{background:#9d4d37;color:white;border-color:#9d4d37}.history-error{font-size:12px;color:#914e37;line-height:1.7}.history-notice{padding:10px 28px;margin:0;background:#edf0e7;font-size:12px;line-height:1.7}
.conversation-heading{min-width:0;flex:1}.conversation-heading small{display:block;max-width:400px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:11px;color:var(--muted);margin-top:6px}.agent-heading>a{flex-shrink:0;margin-left:15px}
.history-readonly{border-top:1px solid #e2e5d9;padding:18px 28px;background:#fffdf8;display:grid;grid-template-columns:1fr auto;gap:5px 12px}.history-readonly p{margin:0;font-size:13px}.history-readonly span{font-size:12px;line-height:1.8;color:var(--muted)}.history-readonly button{grid-column:2;grid-row:1/3;align-self:center}
@media(max-width:900px){.agent-intro{flex-wrap:wrap}.new-session{margin-bottom:0}.history-panel{flex-basis:100%;margin:0;border-bottom:1px solid #d7ddd1}.history-toggle{padding:12px 0}.history-list{max-height:220px}.history-caption{display:none}.history-storage{margin:10px 0}.conversation-heading small{max-width:220px}.history-readonly{padding:16px;grid-template-columns:1fr}.history-readonly button{grid-row:auto;grid-column:1/-1;justify-self:start;margin-top:10px}.history-notice{padding:10px 18px}}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}
</style>
