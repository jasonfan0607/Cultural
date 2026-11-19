# 江苏文化传承与创新平台

Vue 3 + Vite 前端与 Spring Boot 3.2 后端，包含文化地图、非遗浏览、文化资料库、AI 设计和个人中心。

## 项目结构

- `ai-culture-design/`：前端页面、静态图片与视频、AI 生图调用及回归测试。
- `backend/`：Java 后端、MySQL 数据库脚本和 API 文档配置。
- `agent-service/`：Spring AI + DeepSeek 文化智能体、ReAct 工具循环与服务端生图；见[接入与密钥配置](agent-service/README.md)。
- 根目录中文文档：项目介绍、技术实现及页面讲解。

## 本地启动

需要 Node.js 20.19+ 或 22.12+、JDK 17+、Maven 3.8+ 和 MySQL 8.0+。

1. 在根目录执行 `npm ci`，安装当前前端使用的 ECharts 依赖。
2. 进入 `ai-culture-design`，执行 `npm ci`。
3. 复制 `.env.example` 为 `.env.local`，按需配置高德地图 Web 密钥。DeepSeek 与火山引擎密钥仅在 `agent-service` 配置。
4. 执行 `npm run dev`，打开 <http://localhost:5175>。
5. 进入 `backend`，复制 `application-local.example.yml` 为 `application-local.yml`，填写本机 MySQL 账号密码。
6. 首次使用请检查并导入 `src/main/resources/db/init_v3_normalized.sql`，按需使用同目录的示例数据脚本。
7. 在 `backend` 目录执行 `mvn spring-boot:run`，API 文档位于 <http://localhost:8080/api/doc.html>。

后端也可以通过环境变量 `DB_PASSWORD` 设置数据库密码。请勿提交 `.env.local` 或 `application-local.yml`。

运行前端 `npm run dev` 时会自动检查并在后台启动 `agent-service`（默认 8081），首次启动或服务源码变更时会先用 Maven 构建。也可在项目根目录执行 `npm run start:agent` 单独启动；重复执行会复用健康的服务。日志位于 `agent-service/logs/`。业务后端仍需启动，AI 功能需要登录平台。前端经 `/agent-api` 访问服务端，DeepSeek 和火山引擎密钥不进入浏览器构建产物。请删除旧 `.env.local` 中的 `VITE_VOLCENGINE_API_KEY`；若旧密钥曾被部署到浏览器，应在平台轮换。高德地图 Web 密钥应设置域名限制。

## 验证

在 `ai-culture-design` 目录执行：

```sh
npm run build
node --test tests/volcengine.test.js
node --test tests/agent.test.js
```

浏览器回归脚本为 `tests/ai-design.browser.cjs`，需要本地 Chrome、Playwright 和已启动的前端服务。默认从 `playwright` 包加载，也可设置 `PLAYWRIGHT_MODULE_PATH` 指向已有安装，然后运行 `node tests/ai-design.browser.cjs`。

AI 设计历史存储在当前浏览器，支持刷新后恢复、生成取消和失败重试。新生成图片由 AI 服务保存到 `agent-service/data/generated-images/`，通过本地图片接口访问；备份项目数据时请保留该目录。历史临时链接失效时，可在“我的作品”中检查并重新生成（使用生图额度），同步更新作品和设计历史。

## 当前已知限制

部分后端实体与现有数据库脚本存在字段差异，例如 `heritage.tags`；相关列表接口需要进一步同步表结构后才能正常使用。

## 项目开发经验技能

两份项目内技能将本项目真实修复过程整理为可复用的方法：

- [ui-ux-pro-max-skill](ui-ux-pro-max-skill/SKILL.md)：需求分析、意图表达、需求分解、规格约束及验收评测。包含[规格模板](ui-ux-pro-max-skill/assets/development-spec.md)、[实际案例](ui-ux-pro-max-skill/references/worked-spec.md)和[技能评测集](ui-ux-pro-max-skill/references/skill-evals.md)。
- [frontend-design 项目版](frontend-design/SKILL.md)：文化主题界面、异步状态、历史恢复、图片可靠性及视觉验收。包含[界面评测集](frontend-design/references/frontend-evals.md)。

使用时可明确指定：“读取 `ui-ux-pro-max-skill/SKILL.md`，将本次需求整理为可验收规格，再依据 `frontend-design/SKILL.md` 实现相关界面。”只要求分析时，交付规格即可。

这些是项目内的 Markdown 技能源文件；没有自动安装或覆盖个人全局技能。规则区分已实现能力、未实现事项和建议指标，避免把项目规划当作已完成事实。
