<template>
  <div class="ai-design-page">
    <div class="bg-decoration">
      <div class="bg-orb bg-orb-left"></div>
      <div class="bg-orb bg-orb-right"></div>
      <div class="bg-grid"></div>
    </div>

    <aside class="sidebar glass-panel">
      <div class="sidebar-header">
        <div class="logo">
          <img src="/logo1.png" alt="logo" class="logo-img" />
          <div>
            <span class="logo-kicker">Cultural Studio</span>
            <span class="logo-text">AI设计</span>
          </div>
        </div>
        <button class="new-chat-btn" @click="startNewChat">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          新建对话
        </button>
      </div>

      <div class="history-section">
        <h4 class="section-title">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
            <path d="M12 6V12L16 14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
          设计历史
        </h4>
        <div class="history-list">
          <div
            v-for="item in designHistory"
            :key="item.id"
            class="history-item"
            :class="{ active: currentHistoryId === item.id }"
            role="button"
            tabindex="0"
            @click="loadHistory(item.id)"
            @keydown.enter.prevent="loadHistory(item.id)"
            @keydown.space.prevent="loadHistory(item.id)"
          >
            <div class="history-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="2"/>
                <circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/>
                <path d="M21 15L16 10L5 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="history-info">
              <span class="history-title">{{ item.title }}</span>
              <span class="history-time">{{ formatHistoryTime(item.updatedAt) }}</span>
            </div>
          </div>
          <div v-if="designHistory.length === 0" class="empty-history">
            <p>暂无设计历史</p>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div class="tips-section">
          <h4 class="section-title">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
              <path d="M12 16V12M12 8H12.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
            使用提示
          </h4>
          <ul class="tips-list">
            <li>描述你想要的文化元素</li>
            <li>指定设计风格和用途</li>
            <li>可以参考历史设计</li>
          </ul>
        </div>
      </div>
    </aside>

    <main class="main-content">
      <section class="hero-strip glass-panel">
        <div>
          <p class="hero-eyebrow">江苏文化 · AI创意生成</p>
          <h1>让传统文化以更高级的方式被看见</h1>
          <p>围绕苏州园林、南京云锦、昆曲、苏绣等元素，快速生成适合海报、包装、文创和品牌视觉的设计方案。</p>
          <RouterLink to="/agent">先和文化智能体聊聊，查资料、构思方案 →</RouterLink>
          <p v-if="serviceStatus" class="generation-error" role="status">{{ serviceStatus }} <button class="status-action" @click="checkImageService().catch(() => {})">重新检查</button></p>
        </div>
        <div class="hero-metrics">
          <div>
            <strong>4K</strong>
            <span>视觉质感</span>
          </div>
          <div>
            <strong>AI</strong>
            <span>灵感生成</span>
          </div>
          <div>
            <strong>Jiangsu</strong>
            <span>文化主题</span>
          </div>
        </div>
      </section>

      <div class="conversation-area glass-panel" ref="conversationRef">
        <div v-if="messages.length === 0" class="welcome-section">
          <div class="welcome-card">
            <div class="welcome-badge">Curated by AI</div>
            <div class="welcome-icon">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <h2 class="welcome-title">AI文化设计助手</h2>
            <p class="welcome-subtitle">告诉我您想要创作的内容，我将为您生成独特的江苏文化设计作品</p>

            <div class="quick-actions">
              <h4>快速开始</h4>
              <div class="action-grid">
                <button class="action-card" @click="quickAction('苏州园林', '水墨风格', '海报')">
                  <div class="action-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M3 9L12 2L21 9V20C21 20.5304 20.7893 21.0391 20.4142 21.4142C20.0391 21.7893 19.5304 22 19 22H5C4.46957 22 3.96086 21.7893 3.58579 21.4142C3.21071 21.0391 3 20.5304 3 20V9Z" stroke="currentColor" stroke-width="2"/>
                      <path d="M9 22V12H15V22" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <span class="action-title">苏州园林</span>
                  <span class="action-desc">水墨风格海报</span>
                </button>
                <button class="action-card" @click="quickAction('南京云锦', '传统风格', '包装')">
                  <div class="action-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="2"/>
                      <path d="M3 9H21" stroke="currentColor" stroke-width="2"/>
                      <path d="M9 21V9" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <span class="action-title">南京云锦</span>
                  <span class="action-desc">传统风格包装</span>
                </button>
                <button class="action-card" @click="quickAction('昆曲', '现代简约', '文化衫')">
                  <div class="action-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M20.84 4.61C20.3292 4.099 19.7228 3.69364 19.0554 3.41708C18.3879 3.14052 17.6725 2.99817 16.95 2.99817C16.2275 2.99817 15.5121 3.14052 14.8446 3.41708C14.1772 3.69364 13.5708 4.099 13.06 4.61L12 5.67L10.94 4.61C9.9083 3.57831 8.50903 2.99871 7.05 2.99871C5.59096 2.99871 4.19169 3.57831 3.16 4.61C2.1283 5.64169 1.54871 7.04097 1.54871 8.5C1.54871 9.95903 2.1283 11.3583 3.16 12.39L4.22 13.45L12 21.23L19.78 13.45L20.84 12.39" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <span class="action-title">昆曲艺术</span>
                  <span class="action-desc">现代简约文化衫</span>
                </button>
                <button class="action-card" @click="quickAction('苏绣', '中国风', '插画')">
                  <div class="action-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M12 19L19 12L22 15L15 22L12 19Z" stroke="currentColor" stroke-width="2"/>
                      <path d="M18 13L16.5 5.5L2 2L5.5 16.5L13 18L18 13Z" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <span class="action-title">苏绣图案</span>
                  <span class="action-desc">中国风插画</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="chat-messages" ref="chatMessagesRef">
          <div
            v-for="(message, index) in messages"
            :key="index"
            :class="['message', message.role]"
          >
            <div v-if="message.role === 'user'" class="user-message">
              <div class="message-avatar user-avatar">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M20 21V19C20 17.9391 19.5786 16.9217 18.8284 16.1716C18.0783 15.4214 17.0609 15 16 15H8C6.93913 15 5.92172 15.4214 5.17157 16.1716C4.42143 16.9217 4 17.9391 4 19V21" stroke="currentColor" stroke-width="2"/>
                  <circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="2"/>
                </svg>
              </div>
              <div class="message-content">
                <div class="message-bubble user-bubble">{{ message.content }}</div>
              </div>
            </div>

            <div v-else class="ai-message">
              <div class="message-avatar ai-avatar">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" stroke-width="2"/>
                  <path d="M2 17L12 22L22 17" stroke="currentColor" stroke-width="2"/>
                  <path d="M2 12L12 17L22 12" stroke="currentColor" stroke-width="2"/>
                </svg>
              </div>
              <div class="message-content">
                <div class="message-bubble ai-bubble">
                  <MarkdownText v-if="message.content" class="reply-text" :content="message.content" />
                  <div v-if="message.isLoading" class="loading-indicator" role="status">
                    <span class="dot"></span>
                    <span class="dot"></span>
                    <span class="dot"></span>
                    <span>{{ message.stage || '正在准备生成…' }}</span>
                    <button class="status-action" @click="stopGeneration">取消</button>
                  </div>
                  <div v-if="message.error" class="generation-error" role="alert">
                    {{ message.error }}
                    <button v-if="message.params" class="status-action" :disabled="isGenerating" @click="retryReply(message)">重新生成（使用额度）</button>
                  </div>
                  <div v-if="message.notice" class="generation-notice">{{ message.notice }}</div>
                </div>
                <div v-if="message.imageUrl && !message.isLoading && !message.imageFailed" class="ai-image-result">
                  <img :src="message.imageUrl" alt="设计结果" class="result-image" @load="onImageLoad" @error="onImageError(message)" />
                  <div class="image-actions">
                    <button class="img-action-btn" @click="downloadImage(message.imageUrl)">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M21 15V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        <path d="M7 10L12 15L17 10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        <path d="M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                      </svg>
                      下载
                    </button>
                    <button class="img-action-btn" @click="router.push('/works')">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M19 21L12 16L5 21V5C5 4.46957 5.21071 3.96086 5.58579 3.58579C5.96086 3.21071 6.46957 3 7 3H17C17.5304 3 18.0391 3.21071 18.4142 3.58579C18.7893 3.96086 19 4.46957 19 5V21Z" stroke="currentColor" stroke-width="2"/>
                      </svg>
                      我的作品
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <div class="input-wrapper">
          <div class="input-container">
            <textarea
              v-model="userInput"
              class="message-input"
              placeholder="描述您想要设计的作品，例如：设计一幅苏州园林风格的水墨画海报..."
              @keydown.enter.exact="handleSendKey"
              maxlength="500"
              rows="1"
              ref="textAreaRef"
              @input="adjustTextAreaHeight"
            ></textarea>
            <button
              class="send-btn"
              :disabled="!userInput.trim() || isGenerating"
              @click="sendMessage"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M22 2L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
          <div class="input-footer">
            <span class="hint">Enter 发送 · Shift+Enter 换行</span>
            <span class="char-count">{{ userInput.length }}/500</span>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import { generateCultureDesign, generateJiangsuCultureImage } from '../AI-API'
import { agentHealth } from '../AI-API/agent.js'
import MarkdownText from '../components/MarkdownText.vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { currentDesignUser, readDesignHistory, writeDesignHistory, readUserWorks, saveGeneratedWork } from '@/utils/designStorage'

const route = useRoute()
const router = useRouter()
// Capture the account owning this workspace; async responses must not move to
// another account if the browser signs in elsewhere while generation runs.
const designOwner = currentDesignUser()

const userInput = ref('')
const messages = ref([])
const isGenerating = ref(false)
const serviceStatus = ref('')
async function checkImageService(signal) {
  try {
    const health = await agentHealth({ signal })
    serviceStatus.value = health.imageConfigured ? '' : '图片服务尚未配置，请联系管理员开通。文字方案可前往文化助手生成。'
    return health.imageConfigured
  } catch (error) {
    serviceStatus.value = '暂时无法连接图片服务，请稍后重试。'
    throw error
  }
}
const textAreaRef = ref(null)
const conversationRef = ref(null)
const chatMessagesRef = ref(null)
const currentHistoryId = ref(null)
const designHistory = ref(readHistory())
let activeRequest = null
let storageWarningShown = false

function readHistory() {
  try {
    const saved = readDesignHistory(designOwner)
    if (!Array.isArray(saved)) return []
    return saved.filter(item => item && typeof item.id === 'string' && Array.isArray(item.messages))
      .slice(0, 50).map(item => ({
        ...item,
        messages: item.messages.filter(message => message && ['user', 'ai'].includes(message.role)).map(message => ({
          ...message,
          isLoading: false,
          notice: message.isLoading ? '上次生成已中断，可点击重试。' : message.notice,
          error: message.isLoading ? '图片生成未完成。' : message.error
        }))
      }))
  } catch {
    return []
  }
}

function saveCurrentChat() {
  if (!messages.value.length || !currentHistoryId.value) return
  const entry = {
    id: currentHistoryId.value,
    title: (messages.value.find(message => message.role === 'user')?.content || '文化设计').slice(0, 24),
    updatedAt: Date.now(),
    messages: JSON.parse(JSON.stringify(messages.value))
  }
  designHistory.value = [entry, ...designHistory.value.filter(item => item.id !== entry.id)].slice(0, 50)
  try {
    writeDesignHistory(designOwner, designHistory.value)
  } catch {
    if (!storageWarningShown) {
      ElMessage.warning('浏览器存储不可用或已满，对话暂时只保留在当前页面。')
      storageWarningShown = true
    }
  }
}

function formatHistoryTime(timestamp) {
  return new Date(timestamp).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function stopGeneration() {
  if (!activeRequest) return
  const request = activeRequest
  activeRequest = null
  request.controller.abort()
  request.reply.isLoading = false
  request.reply.error = '图片生成已取消，可重试。'
  isGenerating.value = false
  saveCurrentChat()
}

function adjustTextAreaHeight() {
  if (textAreaRef.value) {
    textAreaRef.value.style.height = 'auto'
    textAreaRef.value.style.height = Math.min(textAreaRef.value.scrollHeight, 150) + 'px'
  }
}

function startNewChat() {
  stopGeneration()
  saveCurrentChat()
  messages.value = []
  userInput.value = ''
  currentHistoryId.value = null
  adjustTextAreaHeight()
}

function loadHistory(id) {
  if (id === currentHistoryId.value) return
  stopGeneration()
  const history = designHistory.value.find(item => item.id === id)
  if (!history) return
  messages.value = JSON.parse(JSON.stringify(history.messages))
  currentHistoryId.value = id
  userInput.value = ''
  adjustTextAreaHeight()
  scrollToBottom()
}

function quickAction(element, style, purpose) {
  if (isGenerating.value) return
  userInput.value = `设计一个${element}主题的${style}${purpose}`
  sendMessage()
}

function handleSendKey(event) {
  if (event.isComposing) return
  event.preventDefault()
  sendMessage()
}

async function sendMessage() {
  const input = userInput.value.trim()
  if (!input || isGenerating.value) return
  if (input.length > 500) {
    ElMessage.warning('输入内容不能超过500字')
    return
  }
  if (!currentHistoryId.value) currentHistoryId.value = crypto.randomUUID()
  messages.value.push({ role: 'user', content: input })
  userInput.value = ''
  adjustTextAreaHeight()
  const params = parseUserInput(input)
  params.culturalElement ||= extractCulturalElement(input) || '江苏文化'
  params.style ||= '中国风'
  params.purpose ||= '海报'
  const reply = reactive({ role: 'ai', content: '', isLoading: false, params })
  messages.value.push(reply)
  await generateReply(reply)
}

function retryReply(reply) {
  // An explicit new generation may incur a new charge; transport replays retain the same request ID.
  reply.imageRequestId = crypto.randomUUID()
  generateReply(reply)
}

async function generateReply(reply) {
  if (isGenerating.value) return
  const request = { controller: new AbortController(), reply }
  activeRequest = request
  isGenerating.value = true
  reply.isLoading = true
  reply.error = ''
  reply.notice = ''
  reply.imageUrl = null
  reply.imageFailed = false
  reply.stage = '正在检查图片服务…'
  saveCurrentChat()
  scrollToBottom()
  try {
    if (!await checkImageService(request.controller.signal))
      throw new Error('图片服务尚未配置，暂不能生成图片。请先配置服务端火山方舟密钥及模型权限。')
    const params = reply.params
    if (!reply.textComplete) {
      reply.content = ''
      reply.stage = '正在构思设计方案…'
      const text = await generateCultureDesign(params.culturalElement, params.style, params.purpose,
        { signal: request.controller.signal, description: params.description, onEvent(event, data) {
          if (activeRequest !== request) return
          if (event === 'text-start') reply.content = ''
          if (event === 'delta') { reply.content += data.text; reply.stage = '正在撰写设计方案…'; scrollToBottom() }
          if (event === 'tool' || event === 'status') reply.stage = data.message
        } })
      if (activeRequest !== request) return
      reply.content = text
      reply.textComplete = true
    }
    saveCurrentChat()
    scrollToBottom()
    reply.stage = '正在生成效果图，请稍候…'
    const imageUrl = await generateJiangsuCultureImage(
      params.culturalElement, params.style, '2048x2048',
      { signal: request.controller.signal, description: (params.description + '\n设计方案：' + reply.content).slice(0, 3500), purpose: params.purpose,
        requestId: reply.imageRequestId ||= crypto.randomUUID() }
    )
    if (activeRequest !== request) return
    reply.imageUrl = imageUrl
    reply.generatedAt = Date.now()
    try {
      saveGeneratedWork(designOwner, currentHistoryId.value, reply, messages.value.indexOf(reply))
    } catch {
      ElMessage.warning('图片已生成，但自动保存作品失败，请保留图片并检查浏览器存储空间。')
    }
  } catch (error) {
    if (activeRequest !== request) return
    reply.error = `生成未完成：${error.message || '请稍后重试。'}`
  } finally {
    if (activeRequest === request) {
      activeRequest = null
      reply.isLoading = false
      isGenerating.value = false
      saveCurrentChat()
      scrollToBottom()
    }
  }
}

function extractCulturalElement(input) {
  const keywords = ['园林', '云锦', '昆曲', '苏绣', '泥人', '漆器', '梳篦', '香醋', '蓝印花布', '剪纸', '紫砂',
                    '苏州', '南京', '扬州', '常州', '镇江', '南通', '徐州', '宜兴',
                    '文化', '艺术', '传统', '古典', '民俗', '非遗', '手工艺']

  for (const keyword of keywords) {
    if (input.includes(keyword)) {
      return input.substring(0, 50)
    }
  }
  return input.substring(0, 30)
}

function parseUserInput(input) {
  const params = {
    culturalElement: '',
    style: '',
    purpose: '',
    description: input
  }

  const culturalElements = ['苏州园林', '南京云锦', '昆曲', '苏绣', '惠山泥人', '扬州漆器', '常州梳篦', '镇江香醋', '南通蓝印花布', '徐州剪纸', '宜兴紫砂']
  const styles = ['中国风', '现代简约', '复古风格', '未来科技', '卡通可爱', '水墨风格', '油画风格', '扁平化', '传统风格']
  const purposes = ['海报', '包装', '文化衫', '文创产品', '网页设计', '书籍封面', '标志设计', '插画']

  for (const element of culturalElements) {
    if (input.includes(element)) {
      params.culturalElement = element
      break
    }
  }

  for (const style of styles) {
    if (input.includes(style)) {
      params.style = style
      break
    }
  }

  for (const purpose of purposes) {
    if (input.includes(purpose)) {
      params.purpose = purpose
      break
    }
  }

  return params
}

function scrollToBottom() {
  nextTick(() => {
    const container = chatMessagesRef.value
    if (container) container.scrollTop = container.scrollHeight
  })
}

function onImageError(message) {
  message.imageFailed = true
  message.error = '图片无法加载，链接可能已过期或网络不可用，可重试生成。'
  saveCurrentChat()
}

function onImageLoad() {
  scrollToBottom()
}

function downloadImage(url) {
  const link = document.createElement('a')
  link.href = url
  link.download = `design-${Date.now()}.png`
  link.click()
  ElMessage.success('图片下载中...')
}

function handleKeydown(event) {
  if (event.ctrlKey && event.key === 'n') {
    event.preventDefault()
    startNewChat()
  }
}

onMounted(() => {
  try {
    readUserWorks(designOwner)
  } catch {
    ElMessage.warning('历史作品同步失败，请检查浏览器存储空间。')
  }
  if (typeof route.query.history === 'string') loadHistory(route.query.history)
  checkImageService().catch(() => {})
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  stopGeneration()
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
:global(body) {
  margin: 0;
  background: linear-gradient(180deg, #fbf8f3 0%, #f7f4ee 100%);
  color: #1f2937;
}

.ai-design-page {
  --accent: #8b1538;
  --accent-color: #8b1538;
  --gold: #c9a962;
  --bg: #f7f4ee;
  --panel: rgba(255, 255, 255, 0.84);
  --text: #111827;
  --muted: #6b7280;
  --border: rgba(139, 21, 56, 0.08);
  --shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.ai-design-page {
  min-height: 100vh;
  padding-top: 90px;
  box-sizing: border-box;
  display: flex;
  position: relative;
  overflow: hidden;
  background: radial-gradient(circle at top, rgba(201, 169, 98, 0.14), transparent 28%), linear-gradient(180deg, #fbf8f3 0%, #f7f4ee 100%);
  font-family: 'Source Han Serif SC', 'Noto Serif SC', 'PingFang SC', 'Microsoft YaHei', serif;
}

.bg-decoration, .bg-grid, .bg-orb { position: fixed; inset: 0; pointer-events: none; z-index: 0; }
.bg-grid { background-image: linear-gradient(rgba(139, 21, 56, 0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(139, 21, 56, 0.03) 1px, transparent 1px); background-size: 52px 52px; mask-image: radial-gradient(circle at center, black 45%, transparent 100%); opacity: 0.35; }
.bg-orb-left { width: 380px; height: 380px; left: -120px; top: 120px; background: radial-gradient(circle, rgba(201, 169, 98, 0.24) 0%, rgba(201, 169, 98, 0.02) 70%, transparent 100%); }
.bg-orb-right { width: 460px; height: 460px; right: -160px; top: -80px; background: radial-gradient(circle, rgba(139, 21, 56, 0.12) 0%, rgba(139, 21, 56, 0.02) 70%, transparent 100%); }
.glass-panel { background: var(--panel); border: 1px solid var(--border); box-shadow: var(--shadow); backdrop-filter: blur(16px); }

.sidebar { width: 280px; display:flex; flex-direction:column; z-index:2; border-right: 1px solid var(--border); background: rgba(255,255,255,0.72); }
.sidebar-header, .sidebar-footer { padding: 20px; }
.sidebar-header { border-bottom: 1px solid var(--border); }
.sidebar-footer { border-top: 1px solid var(--border); }
.logo { display:flex; align-items:center; gap:12px; margin-bottom:16px; }
.logo-img { width:38px; height:38px; border-radius: 12px; object-fit:cover; }
.logo-kicker { display:block; font-size:11px; color: var(--muted); letter-spacing:1.5px; text-transform: uppercase; }
.logo-text { display:block; font-size:18px; font-weight:700; color: var(--text); }
.new-chat-btn { width:100%; display:flex; align-items:center; justify-content:center; gap:8px; padding:12px 14px; border:none; border-radius:999px; cursor:pointer; color:#fff; background: linear-gradient(135deg, #8b1538, #a91f45); box-shadow: 0 12px 28px rgba(139,21,56,0.18); }
.new-chat-btn:hover { transform: translateY(-2px); }
.history-section { flex:1; overflow:auto; padding:18px 20px; }
.section-title { display:flex; align-items:center; gap:8px; font-size:12px; color: var(--muted); letter-spacing:1px; margin:0 0 12px; }
.history-list { display:flex; flex-direction:column; gap:10px; }
.history-item { display:flex; gap:12px; align-items:center; padding:12px; border-radius:16px; background: var(--panel-strong, #fffdf8); border:1px solid var(--border); cursor:pointer; transition: all .24s ease; }
.history-item:hover { transform: translateX(4px); box-shadow: 0 10px 26px rgba(15,23,42,0.06); }
.history-item.active { background: linear-gradient(135deg, #fff8e9, #fffdf8); border-color: rgba(201,169,98,0.24); }
.history-icon { width:32px; height:32px; border-radius: 12px; display:flex; align-items:center; justify-content:center; background: linear-gradient(135deg, #fff7ea, #f7ebd5); color: var(--accent-color); flex-shrink:0; }
.history-info { min-width:0; flex:1; }
.history-title { display:block; font-size:13px; font-weight:600; color: var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.history-time { display:block; margin-top:3px; font-size:11px; color: var(--muted); }
.empty-history { text-align:center; padding:22px; color: var(--muted); font-size:13px; }
.tips-list { list-style:none; padding:0; margin:0; }
.tips-list li { position:relative; padding:6px 0 6px 16px; color: var(--muted); font-size:12px; line-height:1.5; }
.tips-list li::before { content:''; position:absolute; left:0; top:13px; width:6px; height:6px; border-radius:50%; background: var(--gold); }

.main-content { flex:1; min-width:0; z-index:1; display:flex; flex-direction:column; padding:20px; gap:16px; }
.hero-strip { border-radius:28px; padding:24px 28px; display:flex; justify-content:space-between; gap:24px; align-items:flex-end; background: rgba(255,255,255,0.82); border: 1px solid rgba(139,21,56,0.08); box-shadow: var(--shadow); }
.hero-eyebrow { margin:0 0 10px; color: var(--accent-color); font-size:12px; letter-spacing:2px; text-transform:uppercase; }
.hero-strip h1 { margin:0; font-size:clamp(26px, 4vw, 40px); line-height:1.1; }
.hero-strip p { margin:12px 0 0; max-width:720px; color: var(--muted); line-height:1.7; }
.hero-metrics { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:12px; min-width:360px; }
.hero-metrics > div { padding:14px 16px; border-radius:18px; border:1px solid var(--border); background: rgba(255,255,255,0.04); }
.hero-metrics strong { display:block; font-size:16px; }
.hero-metrics span { display:block; margin-top:4px; font-size:12px; color: var(--muted); }
.conversation-area { flex:1; display:flex; flex-direction:column; min-height:0; border-radius:28px; overflow:hidden; }
.welcome-section { flex:1; display:grid; place-items:center; padding:32px; }
.welcome-card { width:min(760px, 100%); padding:48px; text-align:center; position:relative; }
.welcome-badge { display:inline-flex; align-items:center; padding:8px 12px; margin-bottom:18px; border-radius:999px; border:1px solid rgba(214,174,103,0.25); background: rgba(214,174,103,0.1); color:#8b1538; font-size:12px; letter-spacing:0.08em; }
.welcome-icon { width:96px; height:96px; margin:0 auto 22px; border-radius:28px; background: linear-gradient(135deg, rgba(214,174,103,0.95), rgba(137,166,255,0.9)); display:grid; place-items:center; box-shadow: 0 18px 50px rgba(214,174,103,0.24); }
.welcome-title { margin:0; font-size:clamp(28px, 4vw, 42px); color: var(--text); }
.welcome-subtitle { margin:14px auto 0; max-width:620px; color: var(--muted); line-height:1.7; }
.quick-actions { margin-top:34px; }
.quick-actions h4 { margin:0 0 16px; color:#374151; font-size:14px; font-weight:600; }
.action-grid { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:14px; }
.action-card { padding:20px; border-radius:20px; border:1px solid var(--border); background: rgba(255,255,255,0.03); color:inherit; cursor:pointer; display:grid; justify-items:center; gap:8px; transition: transform 0.22s ease, border-color 0.22s ease, background 0.22s ease; }
.action-card:hover { transform: translateY(-3px); border-color: rgba(214,174,103,0.5); background: rgba(255,255,255,0.06); }
.action-icon { width:52px; height:52px; border-radius:16px; background: rgba(214,174,103,0.12); color: var(--accent); display:grid; place-items:center; }
.action-title { font-size:15px; font-weight:600; }
.action-desc { color: var(--muted); font-size:12px; }
.chat-messages { flex:1; overflow-y:auto; padding:24px 28px 8px; }
.message { margin-bottom:18px; animation: fadeIn 0.35s ease both; }
@keyframes fadeIn { from { opacity:0; transform:translateY(8px); } to { opacity:1; transform:translateY(0); } }
.user-message, .ai-message { display:flex; gap:12px; align-items:flex-start; max-width:82%; }
.user-message { margin-left:auto; flex-direction:row-reverse; }
.ai-message { margin-right:auto; }
.message-avatar { width:40px; height:40px; border-radius:14px; display:grid; place-items:center; flex-shrink:0; }
.user-avatar { background: linear-gradient(135deg, #8b1538, #a91f45); color:#fff; }
.ai-avatar { background: rgba(255,255,255,0.05); border:1px solid var(--border); color:#8b1538; }
.message-bubble { padding:16px 18px; border-radius:18px; line-height:1.7; font-size:14px; box-shadow: 0 10px 24px rgba(15,23,42,0.04); border:1px solid var(--border); }
.user-bubble { background: linear-gradient(135deg, #8b1538, #a91f45); color:#fff; border-bottom-right-radius:6px; }
.ai-bubble { background: rgba(255,255,255,0.92); color: var(--text); border-bottom-left-radius:6px; }
.loading-indicator { display:flex; align-items:center; flex-wrap:wrap; gap:8px; padding:12px 0 4px; color:var(--accent); font-size:13px; }
.reply-text { white-space:normal; overflow-wrap:anywhere; }
.generation-error { margin-top:12px; color:#9f1239; font-size:13px; }
.generation-notice { margin-top:8px; color:var(--muted); font-size:13px; }
.status-action { border:0; background:transparent; color:var(--accent); padding:4px 8px; text-decoration:underline; cursor:pointer; font:inherit; }
.status-action:disabled { opacity:.5; cursor:not-allowed; }
.loading-indicator .dot { width:8px; height:8px; border-radius:50%; background: var(--accent); animation: bounce 1.2s infinite ease-in-out both; }
.loading-indicator .dot:nth-child(2) { animation-delay:.16s; }
.loading-indicator .dot:nth-child(3) { animation-delay:.32s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0.6); opacity:.5; } 40% { transform: scale(1); opacity:1; } }
.ai-image-result { margin-top:14px; }
.result-image { width:100%; max-width:520px; display:block; border-radius:20px; border:1px solid var(--border); box-shadow: var(--shadow); }
.image-actions { display:flex; gap:10px; margin-top:12px; flex-wrap:wrap; }
.img-action-btn { height:38px; padding:0 14px; border-radius:12px; border:1px solid var(--border); background: rgba(255,255,255,0.92); color: var(--text); display:inline-flex; align-items:center; gap:8px; cursor:pointer; }
.img-action-btn:hover { border-color: rgba(214,174,103,0.45); color:#8b1538; }
.input-area { padding:0 28px 28px; }
.input-wrapper { max-width:920px; margin:0 auto; }
.input-container { position:relative; border-radius:26px; border:1px solid rgba(139,21,56,0.08); background: rgba(255,255,255,0.94); box-shadow: var(--shadow); overflow:hidden; }
.input-container:focus-within { border-color: rgba(139,21,56,0.24); box-shadow: 0 0 0 4px rgba(139,21,56,0.06), var(--shadow); }
.message-input { width:100%; min-height:68px; max-height:180px; padding:20px 74px 20px 22px; border:0; outline:0; background:transparent; resize:none; color: var(--text); font:inherit; font-size:15px; }
.message-input::placeholder { color:#9ca3af; }
.send-btn { position:absolute; right:14px; bottom:14px; width:44px; height:44px; border:0; border-radius:16px; background: linear-gradient(135deg, #8b1538, #c9a962); color:#fff; display:grid; place-items:center; cursor:pointer; }
.send-btn:hover:not(:disabled) { transform: translateY(-1px); }
.send-btn:disabled { opacity:0.45; cursor:not-allowed; }
.input-footer { display:flex; justify-content:space-between; gap:12px; padding:10px 4px 0; color: var(--muted); font-size:12px; }
@media (max-width: 1080px) { .sidebar { width:260px; } .hero-strip { flex-direction:column; align-items:start; } .hero-metrics { min-width:100%; width:100%; } }
@media (max-width: 768px) { .ai-design-page { flex-direction:column; } .sidebar { width:100%; } .history-section, .sidebar-footer { display:none; } .main-content { padding:14px; } .welcome-card { padding:34px 22px; } .action-grid { grid-template-columns:1fr; } .chat-messages { padding:18px 16px 0; } .input-area { padding:0 16px 16px; } .user-message, .ai-message { max-width:92%; } }
@media (max-width: 480px) { .hero-strip { padding:20px; } .hero-metrics { grid-template-columns:1fr; } .welcome-icon { width:84px; height:84px; } .message-bubble { padding:14px 16px; } .message-input { padding-right:64px; } }
</style>
