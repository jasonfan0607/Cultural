<template>
  <main class="login-page">
    <div class="login-container">
      <section class="brand-section" aria-labelledby="brand-title">
        <div class="brand-masthead">
          <router-link to="/" aria-label="江苏文化，返回首页">江苏文化 · 创意新生</router-link>
          <span>JIANGSU STUDIO</span>
        </div>
        <div class="brand-heading">
          <h1 id="brand-title">把江苏，<br><em>写进新意。</em></h1>
          <div class="poetry-seal" aria-label="一方水土，万般灵感"><span>一方水土</span><span>万般灵感</span></div>
        </div>
        <p class="brand-description">拾起千年的文化灵感，写下属于你的新篇。</p>
        <figure class="brand-artwork">
          <img src="/images/login/jiangnan-tea.png" alt="江南水乡窗景下的茶饮、传统糕点与荷花水彩画" width="925" height="520" fetchpriority="high">
          <figcaption><span>以文化为底色 · 以创意续新章</span><span class="artist-seal" aria-label="江苏新作">江苏<br>新作</span></figcaption>
        </figure>
        <div class="brand-features">
          <div><h3>海量文化素材</h3><p>汇聚江苏非遗文化精髓</p></div>
          <div><h3>AI智能设计</h3><p>一键生成创意文化作品</p></div>
          <div><h3>社区分享</h3><p>与创意爱好者共同成长</p></div>
        </div>
      </section>
      <section class="form-section" aria-label="账号登录与注册">
        <div class="form-card" >
          <div class="form-eyebrow"><span>灵感在此相逢</span><span class="edition-label">江苏 · 雅集</span></div>
          <div class="form-header">
            <h2 class="form-title">{{ activeTab === 'login' ? '欢迎回来' : '创建账号' }}</h2>
            <p class="form-subtitle">{{ activeTab === 'login' ? '登录您的账号继续探索' : '注册新账号开启创意之旅' }}</p>
          </div>

          <div class="tab-switcher">
            <button
              type="button" class="tab-btn" :disabled="isLoading || isRegistering"
              :class="{ active: activeTab === 'login' }" :aria-pressed="activeTab === 'login'"
              @click="switchTab('login')"
            >
              登录
            </button>
            <button
              type="button" class="tab-btn" :disabled="isLoading || isRegistering"
              :class="{ active: activeTab === 'register' }" :aria-pressed="activeTab === 'register'"
              @click="switchTab('register')"
            >
              注册
            </button>
            <div class="tab-indicator" :class="{ 'move-right': activeTab === 'register' }"></div>
          </div>

          <transition name="fade-slide" mode="out-in">
            <el-form
              v-if="activeTab === 'login'"
              key="login"
              ref="loginRef"
              :model="loginForm"
              :rules="loginRules"
              class="auth-form"
              @submit.prevent="login"
            >
              <el-form-item prop="username">
                <div class="input-wrapper">
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M20 21V19C20 17.9391 19.5786 16.9217 18.8284 16.1716C18.0783 15.4214 17.0609 15 16 15H8C6.93913 15 5.92172 15.4214 5.17157 16.1716C4.42143 16.9217 4 17.9391 4 19V21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      <circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <el-input
                    v-model="loginForm.username"
                    placeholder="请输入用户名"
                    clearable
                    autocomplete="username" aria-label="用户名"
                  />
                </div>
              </el-form-item>

              <el-form-item prop="password">
                <div class="input-wrapper">
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <rect x="3" y="11" width="18" height="11" rx="2" stroke="currentColor" stroke-width="2"/>
                      <path d="M7 11V7C7 5.67392 7.52678 4.40215 8.46447 3.46447C9.40215 2.52678 10.6739 2 12 2C13.3261 2 14.5979 2.52678 15.5355 3.46447C16.4732 4.40215 17 5.67392 17 7V11" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <el-input
                    v-model="loginForm.password" autocomplete="current-password"
                    placeholder="请输入密码" aria-label="密码"
                    type="password"
                    show-password
                  />
                </div>
              </el-form-item>

              <div class="form-options">
                <label class="remember-checkbox">
                  <input type="checkbox" v-model="loginForm.rememberMe">
                  <span class="checkmark"></span>
                  <span class="label-text">记住我</span>
                </label>
                <button type="button" class="forgot-link" @click="forgotPassword">忘记密码？</button>
              </div>

              <el-form-item class="form-actions">
                <button
                  type="submit"
                  class="submit-btn"
                  :class="{ loading: isLoading }"
                  :disabled="isLoading"


                >
                  <span class="btn-text">{{ isLoading ? '登录中...' : '登录' }}</span>
                  <span class="btn-loader" v-if="isLoading"></span><span v-else class="btn-arrow" aria-hidden="true">⟶</span>
                </button>
              </el-form-item>
            </el-form>

            <el-form
              v-else
              key="register"
              ref="registerRef"
              :model="registerForm"
              :rules="registerRules"
              class="auth-form"
              @submit.prevent="register"
            >
              <el-form-item prop="username">
                <div class="input-wrapper">
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M20 21V19C20 17.9391 19.5786 16.9217 18.8284 16.1716C18.0783 15.4214 17.0609 15 16 15H8C6.93913 15 5.92172 15.4214 5.17157 16.1716C4.42143 16.9217 4 17.9391 4 19V21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      <circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <el-input
                    v-model="registerForm.username"
                    placeholder="请输入用户名"
                    clearable
                    autocomplete="username" aria-label="用户名"
                  />
                </div>
              </el-form-item>

              <el-form-item prop="password">
                <div class="input-wrapper">
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <rect x="3" y="11" width="18" height="11" rx="2" stroke="currentColor" stroke-width="2"/>
                      <path d="M7 11V7C7 5.67392 7.52678 4.40215 8.46447 3.46447C9.40215 2.52678 10.6739 2 12 2C13.3261 2 14.5979 2.52678 15.5355 3.46447C16.4732 4.40215 17 5.67392 17 7V11" stroke="currentColor" stroke-width="2"/>
                    </svg>
                  </div>
                  <el-input
                    v-model="registerForm.password" autocomplete="new-password"
                    placeholder="请输入密码" aria-label="密码"
                    type="password"
                    show-password
                  />
                </div>
              </el-form-item>

              <el-form-item prop="confirmPassword">
                <div class="input-wrapper">
                  <div class="input-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <path d="M12 22C12 22 20 18 20 12V5L12 2L4 5V12C4 18 12 22 12 22Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      <path d="M9 12L11 14L15 10" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </div>
                  <el-input
                    v-model="registerForm.confirmPassword" autocomplete="new-password"
                    placeholder="请确认密码" aria-label="确认密码"
                    type="password"
                    show-password
                  />
                </div>
              </el-form-item>

              <el-form-item class="form-actions">
                <button
                  type="submit"
                  class="submit-btn register-btn"
                  :class="{ loading: isRegistering }"
                  :disabled="isRegistering"


                >
                  <span class="btn-text">{{ isRegistering ? '注册中...' : '注册' }}</span>
                  <span class="btn-loader" v-if="isRegistering"></span><span v-else class="btn-arrow" aria-hidden="true">⟶</span>
                </button>
              </el-form-item>
            </el-form>
          </transition>

          <div class="form-footer">
            <div class="divider">
              <span>其他登录方式</span>
            </div>
            <div class="social-login">
              <button class="social-btn wechat" title="微信登录"  type="button" @click="socialLogin('微信')">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm5.34 2.867c-1.797-.052-3.746.512-5.28 1.786-1.72 1.428-2.687 3.72-1.78 6.22.942 2.453 3.666 4.229 6.884 4.229.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.047c.134 0 .24-.111.24-.247 0-.06-.023-.12-.038-.177l-.327-1.233a.582.582 0 0 1-.023-.156.49.49 0 0 1 .201-.398C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.837-6.656-6.088V8.89c-.135-.01-.269-.03-.406-.03zm-2.53 3.274c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.969-.982z"/>
                </svg>
              <span>微信</span></button>
              <button class="social-btn qq" title="QQ登录"  type="button" @click="socialLogin('QQ')">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12.003 2c-2.265 0-6.29 1.364-6.29 7.325v1.195S3.55 14.96 3.55 17.474c0 .665.17 1.025.281 1.025.114 0 .902-.484 1.748-2.072 0 0-.18 2.197 1.904 3.967 0 0-1.77.495-1.77 1.182 0 .686 4.078.43 6.29.43 2.212 0 6.29.256 6.29-.43 0-.687-1.77-1.182-1.77-1.182 2.085-1.77 1.905-3.967 1.905-3.967.845 1.588 1.634 2.072 1.746 2.072.111 0 .283-.36.283-1.025 0-2.514-2.166-6.954-2.166-6.954V9.325C18.29 3.364 14.268 2 12.003 2z"/>
                </svg>
              <span>QQ</span></button>
              <button class="social-btn weibo" title="微博登录"  type="button" @click="socialLogin('微博')">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M10.098 20.323c-3.977.391-7.414-1.406-7.672-4.02-.259-2.609 2.759-5.047 6.74-5.441 3.979-.394 7.413 1.404 7.671 4.018.259 2.6-2.759 5.049-6.739 5.443zM9.05 17.219c-.384.616-1.208.884-1.829.602-.612-.279-.793-.991-.406-1.593.379-.595 1.176-.861 1.793-.601.622.263.82.972.442 1.592zm1.27-1.627c-.141.237-.449.353-.689.253-.236-.09-.313-.361-.177-.586.138-.227.436-.346.672-.24.239.09.315.36.194.573zm.176-2.719c-1.893-.493-4.033.45-4.857 2.118-.836 1.704-.026 3.591 1.886 4.21 1.983.64 4.318-.341 5.132-2.179.8-1.793-.201-3.642-2.161-4.149zm7.563-1.224c-.346-.105-.579-.18-.405-.649.388-1.032.428-1.922.006-2.556-.786-1.18-2.936-1.115-5.381-.032 0 0-.77.337-.573-.274.377-1.217.32-2.237-.267-2.826-1.333-1.337-4.869.045-7.894 3.088C1.098 10.931 0 13.287 0 15.312c0 3.878 4.973 6.236 9.839 6.236 6.376 0 10.617-3.705 10.617-6.646 0-1.777-1.497-2.785-2.397-3.253zm1.937-5.722c-.849-.97-2.1-1.384-3.328-1.205l.003-.001c-.34.05-.568.36-.518.694.05.335.354.566.69.516.772-.113 1.55.143 2.082.754.531.61.714 1.418.498 2.17-.087.324.1.66.42.75.322.09.658-.098.748-.42.322-1.128.05-2.336-.595-3.058zm2.247-2.57c-1.416-1.614-3.498-2.303-5.545-1.995-.35.052-.59.376-.538.723.052.348.378.59.727.538 1.616-.243 3.259.299 4.377 1.576 1.118 1.278 1.503 2.98 1.062 4.554-.092.328.1.669.427.762.328.092.668-.1.76-.428.55-1.96.07-4.077-1.27-5.53z"/>
                </svg>
              <span>微博</span></button>
            </div>
          </div>
        </div>

        <div class="form-footer-links">
          <span>登录即表示您同意我们的</span>
          <a href="#">服务条款</a>
          <span>和</span>
          <a href="#">隐私政策</a>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, setToken } from '@/utils/api'

const router = useRouter()
const activeTab = ref('login')
const isLoading = ref(false)

const loginForm = ref({
    username: '',
    password: '',
    rememberMe: false
})
const loginRef = ref()
const loginRules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, message: '用户名至少3位', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码至少6位', trigger: 'blur' }
    ]
}

onMounted(() => {
    const savedUser = localStorage.getItem('savedUser')
    if (savedUser) {
        try {
            const user = JSON.parse(savedUser)
            loginForm.value.username = user.username
            loginForm.value.password = user.password
            loginForm.value.rememberMe = true
        } catch (error) {
            console.error('解析保存的用户信息失败:', error)
        }
    }
})

async function login() {
    if (isLoading.value) return
    const valid = await loginRef.value.validate().catch(() => false)
    if (!valid) return
    try {

        isLoading.value = true

        const response = await authApi.login({
            username: loginForm.value.username,
            password: loginForm.value.password,
            rememberMe: loginForm.value.rememberMe
        })


        if (response.code === 200 && response.data) {
            setToken(response.data.token)

            const userInfo = response.data.userInfo || {}
            localStorage.setItem('username', userInfo.username || loginForm.value.username)
            localStorage.setItem('nickname', userInfo.nickname || loginForm.value.username)
            localStorage.setItem('avatar', userInfo.avatar || 'https://i.pravatar.cc/150?u=' + loginForm.value.username)

            if (loginForm.value.rememberMe) {
                localStorage.setItem('savedUser', JSON.stringify({
                    username: loginForm.value.username,
                    password: loginForm.value.password
                }))
            } else {
                localStorage.removeItem('savedUser')
            }

            ElMessage.success(response.message || '登录成功')
            router.push('/profile')
        } else {
            ElMessage.error(response.message || '用户名或密码错误')
        }
    } catch (error) {
        console.error('登录失败:', error)
        ElMessage.error('登录过程中发生错误，请重试')
    } finally {
        isLoading.value = false
    }
}

const isRegistering = ref(false)
const registerForm = ref({
    username: '',
    password: '',
    confirmPassword: ''
})
const registerRef = ref()
const registerRules = {
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, message: '用户名至少3位', trigger: 'blur' },
        { max: 20, message: '用户名最多20位', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码至少6位', trigger: 'blur' },
        { max: 20, message: '密码最多20位', trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请确认密码', trigger: 'blur' },
        {
            validator: (rule, value, callback) => {
                if (value !== registerForm.value.password) {
                    callback(new Error('两次输入的密码不一致'))
                } else {
                    callback()
                }
            },
            trigger: 'blur'
        }
    ]
}

async function register() {
    if (isRegistering.value) return
    const valid = await registerRef.value.validate().catch(() => false)
    if (!valid) return
    try {

        isRegistering.value = true

        const response = await authApi.register({
            username: registerForm.value.username,
            password: registerForm.value.password,
            confirmPassword: registerForm.value.confirmPassword
        })

        if (response.code === 200) {
            ElMessage.success(response.message || '注册成功，请登录')

            registerForm.value.username = ''
            registerForm.value.password = ''
            registerForm.value.confirmPassword = ''

            activeTab.value = 'login'
        } else {
            ElMessage.error(response.message || '注册失败')
        }
    } catch (error) {
        console.error('注册失败:', error)
        if (error.message) {
            ElMessage.error(error.message)
        } else {
            ElMessage.error('注册过程中发生错误，请重试')
        }
    } finally {
        isRegistering.value = false
    }
}

function switchTab(tab) {
    if (isLoading.value || isRegistering.value) return
    activeTab.value = tab
}

function forgotPassword() {
    ElMessage.info('请联系管理员重置密码。')
}
function socialLogin(provider) {
    ElMessage.info(`${provider}登录暂未开放，请使用账号密码登录。`)
}
</script>

<style scoped>
.login-page {
  --ink: #292f27;
  --muted: #73776a;
  --red: #af2345;
  --line: #d5d8cb;
  --serif: "Noto Serif SC", "Songti SC", "SimSun", serif;
  --brush: "STKaiti", "KaiTi", "Kaiti SC", serif;
  min-height: 100svh;
  padding: 8px 3.3vw;
  background: #f7f8f3;
  color: var(--ink);
  font-family: var(--serif);
  box-sizing: border-box;
}
.login-page *, .login-page *::before, .login-page *::after { box-sizing: border-box; }
.login-page button, .login-page a { -webkit-tap-highlight-color: transparent; }
.login-page button { font-family: inherit; cursor: pointer; }
.login-page button:focus-visible, .login-page a:focus-visible { outline: 2px solid var(--red); outline-offset: 5px; }
.login-container { display: grid; grid-template-columns: 53.5% 46.5%; max-width: 1948px; min-height: calc(100svh - 16px); margin: auto; border: 1px solid var(--line); }
.brand-section { padding: clamp(24px, 2.5vw, 50px) clamp(26px, 2.75vw, 56px) 16px; background: #dfe8d9; border-right: 1px solid #becab7; }
.brand-masthead { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-bottom: clamp(16px, 1vw, 22px); border-bottom: 1px solid #a3b29b; color: #36544f; }
.brand-masthead a { color: inherit; text-decoration: none; font-size: clamp(13px, 1vw, 20px); letter-spacing: .1em; }
.brand-masthead > span { font-family: Georgia, serif; font-size: clamp(9px, .74vw, 15px); letter-spacing: .22em; }
.brand-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: clamp(24px, 2.1vw, 44px); }
.brand-heading h1 { margin: 0; font-family: var(--brush); font-size: clamp(48px, 5.3vw, 108px); font-weight: 700; line-height: 1.12; letter-spacing: .035em; }
.brand-heading em { font-style: normal; color: var(--red); }
.poetry-seal { display: flex; flex-direction: row-reverse; gap: .5em; flex-shrink: 0; margin-right: 12px; padding: 15px 13px; border: 4px double #8e9e85; color: #45665b; font-size: clamp(16px, 1.3vw, 27px); font-family: var(--brush); }
.poetry-seal span { writing-mode: vertical-rl; letter-spacing: .2em; }
.brand-description { margin: clamp(18px, 1.6vw, 32px) 0; font-size: clamp(14px, 1.06vw, 22px); color: #3f5c53; letter-spacing: .035em; line-height: 1.7; }
.brand-artwork { margin: 0; border: 1px solid #b6ac8d; background: #f3e7bc; }
.brand-artwork img { display: block; width: 100%; height: auto; aspect-ratio: 925 / 520; object-fit: cover; }
.brand-artwork figcaption { min-height: 64px; padding: 12px 22px; border-top: 1px solid #b6ac8d; display: flex; justify-content: space-between; align-items: center; gap: 12px; color: #85672f; font-size: clamp(13px, 1vw, 21px); letter-spacing: .12em; }
.artist-seal { display: inline-block; padding: 3px 5px; border: 1px solid var(--red); color: var(--red); transform: rotate(-5deg); font-family: var(--brush); line-height: 1.1; font-size: clamp(15px, 1vw, 21px); flex-shrink: 0; }
.brand-features { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: clamp(22px, 1.6vw, 34px); padding: 23px 0; border-block: 1px solid #a3b29b; color: #36544f; }
.brand-features > div { padding-inline: 20px; border-left: 1px solid #adbaa3; }
.brand-features > div:first-child { border: 0; padding-left: 0; }
.brand-features > div:last-child { padding-right: 0; }
.brand-features h3 { margin: 0 0 12px; font-size: clamp(15px, 1.15vw, 24px); font-weight: 600; }
.brand-features p { margin: 0; font-size: clamp(11px, .87vw, 18px); line-height: 1.6; }
.form-section { display: flex; flex-direction: column; align-items: center; min-width: 0; background: #fff; padding: clamp(48px, 7.3vw, 150px) clamp(30px, 7.1vw, 145px) 18px; }
.form-card { width: 100%; max-width: 610px; }
.form-eyebrow { display: flex; justify-content: space-between; align-items: center; gap: 15px; margin-bottom: clamp(25px, 2.1vw, 44px); color: var(--red); font-size: clamp(13px, 1vw, 20px); letter-spacing: .13em; }
.edition-label { padding: 7px 10px; border: 1px solid #d6c59b; color: #8c8065; font-size: clamp(11px, .8vw, 16px); letter-spacing: .05em; white-space: nowrap; }
.form-title { margin: 0 0 10px; font-family: var(--brush); font-size: clamp(42px, 3.5vw, 72px); line-height: 1.15; font-weight: 700; letter-spacing: .08em; }
.form-subtitle { margin: 0; color: var(--muted); font-size: clamp(14px, 1.05vw, 21px); line-height: 1.7; }
.tab-switcher { display: flex; position: relative; margin: clamp(30px, 2.7vw, 56px) 0 clamp(26px, 2vw, 42px); border-bottom: 1px solid var(--line); }
.tab-btn { flex: 1; padding: 0 0 16px; border: 0; background: none; color: var(--muted); font-size: clamp(17px, 1.25vw, 25px); }
.tab-btn.active { color: var(--red); font-weight: 700; }
.tab-indicator { position: absolute; bottom: -1px; height: 3px; width: 50%; background: var(--red); transition: transform .25s ease; }
.tab-indicator.move-right { transform: translateX(100%); }
.auth-form { --el-color-primary: var(--red); --el-color-danger: var(--red); }
.auth-form :deep(.el-form-item) { margin-bottom: clamp(24px, 1.8vw, 36px); }
.input-wrapper { display: flex; align-items: center; width: 100%; height: clamp(56px, 3.9vw, 80px); padding: 0 clamp(14px, 1.2vw, 24px); border: 1px solid var(--line); border-radius: 6px; background: #f8f9f4; transition: border-color .2s, box-shadow .2s; }
.input-wrapper:focus-within { border-color: #a3ae96; box-shadow: 0 0 0 3px #dfe8d966; }
.input-icon { display: flex; align-items: center; color: #7e8675; margin-right: 14px; }
.input-icon svg { width: clamp(20px, 1.4vw, 28px); height: clamp(20px, 1.4vw, 28px); }
.input-wrapper :deep(.el-input__wrapper) { padding: 0; background: transparent; box-shadow: none !important; }
.input-wrapper :deep(.el-input__inner) { font-family: var(--serif); font-size: clamp(16px, 1.2vw, 24px); color: var(--ink); height: 48px; }
.input-wrapper :deep(.el-input__inner::placeholder) { color: #777b71; }
.auth-form :deep(.is-error .input-wrapper) { border-color: var(--red); }
.auth-form :deep(.el-form-item__error) { padding-top: 5px; font-size: 12px; }
.form-options { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 0 clamp(28px, 2.3vw, 46px); font-size: clamp(14px, 1.05vw, 21px); }
.remember-checkbox { display: inline-flex; align-items: center; gap: 9px; color: var(--muted); cursor: pointer; }
.remember-checkbox input { width: 19px; height: 19px; margin: 0; accent-color: var(--red); cursor: pointer; }
.checkmark { display: none; }
.forgot-link { padding: 0; border: 0; background: transparent; color: var(--red); font-size: inherit; }
.forgot-link:hover { text-decoration: underline; text-underline-offset: 4px; }
.auth-form :deep(.form-actions) { margin-bottom: 0; }
.submit-btn { display: flex; position: relative; align-items: center; justify-content: center; width: 100%; height: clamp(58px, 4.1vw, 84px); border: 1px solid #a31c3d; border-radius: 5px; background: var(--red); color: #fff8e7; transition: background .2s; }
.submit-btn::before { content: ''; position: absolute; inset: 5px; border: 1px solid #e2b589a8; pointer-events: none; }
.submit-btn:hover { background: #98203d; }
.submit-btn:disabled { cursor: wait; opacity: .7; }
.btn-text { font-family: var(--brush); font-size: clamp(27px, 2.1vw, 42px); font-weight: 700; letter-spacing: .15em; }
.btn-arrow { position: absolute; right: 22px; padding-left: 14px; border-left: 1px solid #ecd9ba; color: #ecd9ba; font: 28px/1 Georgia, serif; }
.btn-loader { position: absolute; right: 25px; width: 20px; height: 20px; border: 2px solid #ffffff50; border-top-color: white; border-radius: 50%; animation: spin .8s linear infinite; }
.divider { display: flex; align-items: center; gap: 20px; margin: clamp(26px, 2vw, 42px) 0 24px; color: var(--muted); white-space: nowrap; font-size: clamp(12px, .9vw, 18px); }
.divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--line); }
.social-login { display: grid; grid-template-columns: repeat(3, 1fr); gap: clamp(8px, .8vw, 16px); }
.social-btn { display: flex; justify-content: center; align-items: center; gap: 10px; height: clamp(46px, 3.25vw, 66px); padding: 0 8px; border: 1px solid var(--line); border-radius: 5px; color: #646e5a; background: #fff; font-size: clamp(14px, 1vw, 20px); transition: background .2s, border-color .2s; }
.social-btn svg { width: 22px; height: 22px; }
.social-btn:hover { border-color: #9ba78f; background: #f6f8f1; }
.form-footer-links { margin-top: auto; padding-top: clamp(44px, 6.8vw, 138px); color: var(--muted); font-size: clamp(11px, .85vw, 17px); line-height: 1.8; text-align: center; }
.form-footer-links a { color: var(--red); text-decoration: none; }
.form-footer-links a:hover { text-decoration: underline; }
.fade-slide-enter-active, .fade-slide-leave-active { transition: opacity .16s, transform .16s; }
.fade-slide-enter-from, .fade-slide-leave-to { opacity: 0; transform: translateY(6px); }
@keyframes spin { to { transform: rotate(360deg); } }
@media (min-width: 1600px) {
  .brand-artwork figcaption { min-height: 85px; }
}
@media (max-width: 1100px) {
  .login-page { padding: 12px; }
  .login-container { grid-template-columns: 51% 49%; min-height: calc(100svh - 24px); }
  .brand-section { padding-inline: 25px; }
  .form-section { padding: 62px 32px 20px; }
  .brand-heading h1 { font-size: clamp(46px, 5.6vw, 64px); }
  .poetry-seal { margin-right: 0; padding: 10px 7px; font-size: 16px; }
  .brand-features > div { padding-inline: 10px; }
  .brand-features h3 { font-size: 14px; }
  .brand-features p { font-size: 11px; }
}
@media (max-width: 760px) {
  .login-page { padding: 0; }
  .login-container { display: flex; flex-direction: column; border: 0; min-height: 100svh; }
  .brand-section { padding: 20px 24px 24px; border-right: 0; border-bottom: 1px solid #becab7; }
  .brand-masthead { padding-bottom: 14px; }
  .brand-heading { margin-top: 20px; }
  .brand-heading h1 { font-size: clamp(40px, 8vw, 60px); }
  .poetry-seal { font-size: 16px; }
  .brand-description { font-size: 13px; margin: 16px 0 0; }
  .brand-artwork, .brand-features { display: none; }
  .form-section { flex: 1; padding: 30px 24px 22px; }
  .form-card { max-width: 480px; }
  .form-eyebrow { margin-bottom: 22px; }
  .form-title { font-size: 42px; }
  .form-footer-links { padding-top: 36px; }
}
@media (prefers-reduced-motion: reduce) {
  .login-page *, .login-page *::before, .login-page *::after { animation: none !important; transition: none !important; }
}
</style>
