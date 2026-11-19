<template>
    <div class="works-view">
        <!-- 未登录时弹窗提示，已登录时展示作品列表 -->
        <el-dialog v-model="showDialog" title="温馨提示" width="300px" :close-on-click-modal="false" :show-close="false">
            <p>请先登录后再查看你的作品！</p>
            <div style="text-align: right; margin-top: 16px;">
                <el-button type="primary" @click="toLogin">去登录</el-button>
            </div>
        </el-dialog>

        <div v-if="isLogin">
            <header class="works-header">
                <div><h1>我的作品</h1><p>在这里查看你的文化创作</p></div>
                <router-link to="/profile" class="profile-link">返回个人中心</router-link>
            </header>
            <el-input v-model="search" aria-label="搜索作品" placeholder="搜索作品标题..." class="works-search" clearable />

            <div class="repair-tools" v-if="works.length">
                <el-button :loading="repairing" :disabled="repairing" @click="restoreImages">检查并修复失效图片（使用额度）</el-button>
                <p v-if="repairStatus" role="status" style="white-space:pre-wrap">{{ repairStatus }}</p>
            </div>
            <el-row v-if="filteredWorks.length" :gutter="16">
                <el-col v-for="work in pagedWorks" :key="work.id" :xs="24" :sm="12" :md="8">
                    <el-card class="work-card">
                        <el-image :src="work.image || defaultImg" :alt="work.title || '作品预览'" class="work-img" fit="contain" :preview-src-list="work.image ? [work.image] : []" preview-teleported>
                            <template #error><div class="image-unavailable">图片暂时无法加载</div></template>
                        </el-image>
                        <h3>{{ work.title }}</h3>
                        <p>{{ work.description }}</p>
                        <div class="work-meta"><span>{{ work.style }}</span><time>{{ work.date }}</time></div>
                        <router-link v-if="work.historyId" :to="{ path: '/design', query: { history: work.historyId } }">查看设计过程</router-link>
                    </el-card>
                </el-col>
            </el-row>
            <section v-else class="works-empty" aria-live="polite">
                <h2>{{ search.trim() ? '没有找到匹配的作品' : '暂无作品' }}</h2>
                <p>{{ search.trim() ? '换个关键词试试，或清空搜索查看全部作品。' : '你还没有保存作品，开始一次新的文化创作吧。' }}</p>
                <el-button v-if="search.trim()" @click="search = ''">清空搜索</el-button>
                <el-button v-else type="primary" @click="router.push('/design')">开始创作</el-button>
            </section>

            <div v-if="filteredWorks.length > pageSize" style="text-align:center;margin-top:30px;">
                <el-pagination background layout="prev, pager, next" :total="filteredWorks.length" :page-size="pageSize"
                    v-model:current-page="page" />
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { currentDesignUser, readUserWorks, subscribeUserWorks } from '@/utils/designStorage'

import { repairWorkImages } from '@/utils/repairWorkImages'
const repairing = ref(false)
const repairStatus = ref('')
let repairController
async function restoreImages() {
    if (repairing.value) return
    repairing.value = true
    repairController = new AbortController()
    try { await repairWorkImages(text => { repairStatus.value = text }, repairController.signal) }
    catch (error) { repairStatus.value = error.message }
    finally { repairing.value = false; loadWorks() }
}
onUnmounted(() => repairController?.abort())

const router = useRouter()
const works = ref([])
const search = ref('')
const page = ref(1)
const pageSize = ref(6)
const defaultImg = '/logo1.png'

const isLogin = ref(false)
const showDialog = ref(false)
let unsubscribeWorks

function loadWorks() {
    isLogin.value = localStorage.getItem('isLogin') === 'true'
    try {
        works.value = readUserWorks(currentDesignUser())
    } catch {
        ElMessage.error('读取作品失败，请检查浏览器存储空间后重试')
    }
}

onMounted(() => {
    // 检查登录状态
    isLogin.value = localStorage.getItem('isLogin') === 'true'
    if (!isLogin.value) {
        showDialog.value = true
    } else {
        loadWorks()
    }
    unsubscribeWorks = subscribeUserWorks(loadWorks)
})
onUnmounted(() => unsubscribeWorks?.())

// 登录按钮事件
function toLogin() {
    router.push('/login')
}

const filteredWorks = computed(() =>
    works.value.filter(
        w => String(w.title || '').includes(search.value.trim()) || String(w.description || '').includes(search.value.trim())
    )
)
const pagedWorks = computed(() =>
    filteredWorks.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
)
watch(search, () => { page.value = 1 })
</script>

<style scoped>
.works-view {
    box-sizing: border-box;
    max-width: 1000px;
    min-height: 75vh;
    margin: auto;
    padding: calc(var(--site-navbar-height, 96px) + 24px) 24px 40px;
}

.works-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
.works-header h1 { margin: 0 0 8px; font-size: 28px; color: #292f27; }
.works-header p, .works-empty p { color: #6d665d; }
.profile-link { color: #8b1538; white-space: nowrap; }
.works-search { width: 100%; max-width: 360px; margin-bottom: 24px; }
.works-empty { padding: 70px 20px; text-align: center; background: #faf8f4; border: 1px solid #e8e1d8; border-radius: 16px; }
.works-empty h2 { color: #292f27; font-size: 22px; }
.works-empty p { margin-bottom: 24px; }

.work-card {
    margin-bottom: 16px;
    min-height: 180px;
    position: relative;
    text-align: center;
}

.work-img {
    display: block;
    width: 100%;
    max-width: 100%;
    height: 220px;
    object-fit: cover;
    margin: 0 auto 10px auto;
}
.work-card p { color: #6d665d; overflow-wrap: anywhere; }
.work-meta { display: flex; justify-content: space-between; gap: 12px; color: #6d665d; margin: 12px 0; font-size: 12px; }
.image-unavailable { display: grid; place-items: center; height: 100%; background: #f7f4ef; color: #6d665d; }
</style>
