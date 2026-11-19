# Cultural 项目事实与经验

依据 2026-11-14 本地源码和本次开发记录整理。它是读取代码的入口，不是对未来版本的保证。以下路径均相对项目根目录；检索符号比固定行号更可靠。

## 当前结构与事实

| 对象 | 查证入口 | 当前事实及限制 |
|---|---|---|
| 前端启动 | `ai-culture-design/package.json` | Vue 3、Vite，开发端口 5175；根目录另有 ECharts 依赖 |
| 业务请求 | `src/utils/api/request.js`（前端内） | 使用 `fetch`；旧技术文档中的 Axios 描述不能当作现状 |
| AI 设计 | `src/views/AiDesignView.vue`、`src/AI-API/index.js` | 实际导出并使用 `volcengine.js`，浏览器直接调用生图服务 |
| 文字方案 | `volcengine.js` 的 `generateCultureDesign` | 本地模板描述，不是已接入大语言模型的证据 |
| 生图生命周期 | `generateReply`、`generateImageWithVolcEngine` | 先展示文字；120 秒默认超时；取消与请求身份检查；失败可重试 |
| 历史 | `readHistory`、`saveCurrentChat`、`loadHistory` | `cultural.design-history.v1`，同来源 localStorage，最多 50 条；不是账户级云同步 |
| 收藏封面 | `src/views/ProfileView.vue` 的 `collections` | 仍为三条示例数据；已替换为本地主题图并补充失败状态 |
| AI 收藏 | `AiDesignView.vue` 的 `saveToCollection` | 当前只显示成功提示，未完成收藏持久化 |
| 收藏详情 | `ProfileView.vue` 的 `viewCollection` | 当前只显示提示，没有真实详情跳转 |
| 后端 | `backend/pom.xml`、`src/main/resources/application.yml` | Spring Boot、MyBatis-Plus、MySQL；当前配置排除了 Redis 自动配置，不能按文档假定 Redis 已参与运行 |
| 数据一致性 | 后端实体、VO、`db/init_v3_normalized.sql` | 已遇到 `heritage.tags` 缺列；启动成功不能证明查询可用 |
| 配置与交付 | 根 `README.md`、`.gitignore`、前后端配置示例 | 实际密钥在本地文件；`VITE_` 值仍会进入浏览器产物，不能称为服务端保密 |

## 已发生的问题怎样变成规则

### 1. 空白回复不一定是服务失败

- 症状：发送设计描述后出现空白气泡。
- 原因：等待整次生图才展示内容；加载点依赖未定义的颜色；请求无超时。
- 修复：展示文字和可见进度，限制等待时间，保留失败信息和重试入口。
- 证据：真实生图曾返回 200 和图片；脚本覆盖成功、失败、取消及超时。
- 边界：真实调用成功只证明当次链路；不能推导服务永远可用。

### 2. 历史的“存在”必须由可恢复内容证明

- 症状：点击记录只弹“加载历史”提示。
- 原因：三条硬编码记录没有消息，点击处理也未恢复数据。
- 修复：真实会话快照、稳定 ID、保存与加载、取消旧请求及过期结果保护。
- 证据：同来源刷新恢复、会话切换、失败重试、旧结果不串会话。
- 边界：本地历史没有账户隔离；多设备需求要重新设计存储契约。

### 3. 图片“有 URL”不等于图片可见

- 症状：收藏卡片上半部空白。
- 原因：外部随机图地址不可用，且没有加载失败反馈。
- 修复：当前三条示例卡片使用项目已有主题图的本地副本，保留 AI 生成标记，提供主题名称与失败占位。
- 证据：屏蔽外网、移动视口刷新、阻断单张本地图的浏览器测试。
- 边界：不是接入真实收藏，也不能给任意用户上传图片随意替换成通用素材。

### 4. 页面切换是异步行为的一部分

- 症状：切换对话出现 `scrollHeight` 空引用，或旧响应污染新对话。
- 原因：延迟回调访问已移除的元素；用可变化的数组索引绑定异步结果。
- 修复：在 DOM 更新后重新判断元素是否存在；用请求身份与会话身份控制写回，终止已失效的等待。
- 边界：取消客户端等待不保证服务商已经停止计费或计算，界面文案应准确。

### 5. 代码和资料都需要版本边界

- 症状：旧文档描述“AI 经后端调用”，实际页面直接请求外部服务。
- 修复：记录实际调用链，不凭文件名、导入语句或规划文档判断能力。
- 交付经验：本项目曾用独立 `.publish/Cultural` 检出目录提交完整前后端，原前端另有 Git 记录。后续操作先核对当前工作目录、remote、分支及用户改动，不把临时发布目录写成通用开发规范。

## 现有验证入口

在 `ai-culture-design` 目录执行：

```sh
npm run build
node --test tests/volcengine.test.js
node tests/ai-design.browser.cjs
node tests/profile-collections.browser.cjs
```

浏览器脚本需要前端服务、Chrome 和 Playwright；可用 `PLAYWRIGHT_MODULE_PATH` 指向已有安装。不要把某台电脑的绝对依赖路径写成通用要求。

这些脚本曾在对应修复时通过。本次新增技能文档不等于重新运行了全部应用测试。权限隔离、后端字段迁移、真实收藏保存和完整视觉评测不在这些脚本的覆盖保证内。
