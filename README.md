# 江苏文化传承与创新平台

Vue 3 + Vite 前端与 Spring Boot 3.2 后端，包含文化地图、非遗浏览、文化资料库、AI 设计和个人中心。

## 项目结构

- `ai-culture-design/`：前端页面、静态图片与视频、AI 生图调用及回归测试。
- `backend/`：Java 后端、MySQL 数据库脚本和 API 文档配置。
- 根目录中文文档：项目介绍、技术实现及页面讲解。

## 本地启动

需要 Node.js 20.19+ 或 22.12+、JDK 17+、Maven 3.8+ 和 MySQL 8.0+。

1. 在根目录执行 `npm ci`，安装当前前端使用的 ECharts 依赖。
2. 进入 `ai-culture-design`，执行 `npm ci`。
3. 复制 `.env.example` 为 `.env.local`，配置火山引擎和高德地图密钥。
4. 执行 `npm run dev`，打开 <http://localhost:5175>。
5. 进入 `backend`，复制 `application-local.example.yml` 为 `application-local.yml`，填写本机 MySQL 账号密码。
6. 首次使用请检查并导入 `src/main/resources/db/init_v3_normalized.sql`，按需使用同目录的示例数据脚本。
7. 在 `backend` 目录执行 `mvn spring-boot:run`，API 文档位于 <http://localhost:8080/api/doc.html>。

后端也可以通过环境变量 `DB_PASSWORD` 设置数据库密码。请勿提交 `.env.local` 或 `application-local.yml`。

前端当前直接请求火山引擎，`VITE_` 配置会进入浏览器构建产物。对外部署时应将生图鉴权移至后端，高德地图 Web 密钥应设置域名限制。

## 验证

在 `ai-culture-design` 目录执行：

```sh
npm run build
node --test tests/volcengine.test.js
```

浏览器回归脚本为 `tests/ai-design.browser.cjs`，需要本地 Chrome、Playwright 和已启动的前端服务。默认从 `playwright` 包加载，也可设置 `PLAYWRIGHT_MODULE_PATH` 指向已有安装，然后运行 `node tests/ai-design.browser.cjs`。

AI 设计历史存储在当前浏览器，支持刷新后恢复、生成取消和失败重试。图片链接由生图服务提供，过期后可重新生成。

## 当前已知限制

部分后端实体与现有数据库脚本存在字段差异，例如 `heritage.tags`；相关列表接口需要进一步同步表结构后才能正常使用。
