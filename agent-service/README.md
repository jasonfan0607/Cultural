# 江苏文化智能体

独立 Spring Boot 4.0.5 / Spring AI 2.0.2 服务，复用原有 Spring Boot 3.2 业务后端。Java 17+。

## 已实现

- DeepSeek 原生 Spring AI 适配器与显式 ReAct 工具循环；每轮最多 6 次模型请求、10 次工具执行，单次输出最多 4096 tokens。
- `searchCulture`、`getCultureDetail` 查询 MySQL 中已公开、未删除的非遗与传统艺术；返回条目 ID、标题、内容供核对。检索是关键词查询，不是向量 RAG。
- `generateDesignImage` 调用火山方舟 Seedream。用户本轮勾选允许生图后才注册工具；最多尝试一次。原图片设计页也已改用服务端接口。
- `searchWeb` 实时请求公开搜索网站，返回不同网页的标题、链接、摘要、搜索平台与检索时间。默认 DuckDuckGo，失败或无结果时尝试搜狗，再降级到维基百科公开检索 API；百科来源明确标注，不冒充百度／Google 搜索结果。可配置 SerpAPI 接入 Google／百度。不会把搜索摘要说成已阅读的网页全文。
- 搜索恢复：百科连接中断最多重试一次；验证码和限流不重试。临时连接错误冷却 10 秒，网站验证/限流冷却 5 分钟。补充检索失败返回 `partial_results` 并保留本轮已有网页来源；同词缓存最多保留 24 小时，超过 10 分钟的降级缓存标记 `stale` 并保留原检索时间。公开搜索入口仍受网站验证和网络限制，稳定接入 Google/百度建议配置正式 SerpAPI Key。
- 搜索验证、限流与网络故障分别诊断；受阻入口冷却 5 分钟，不随模型换词反复请求。成功结果缓存 10 分钟（最多 200 个查询），保留原检索时间并标记缓存；缓存到期重新联网。百科检索会移除海报、包装等设计修饰词，保留实际检索词并按标题相关性筛选。全部来源失败时仍如实报告，不返回虚构资料。
- 复杂搜索词会逐步缩短：例如“南京云锦 传统纹样 种类”无匹配时，再联网检索“南京云锦”。每次最多 3 个候选词；扩展到主题概述的资料标记 `retrievalScope: topic-overview`，不能据此声称已核实具体纹样细节。成功但无匹配返回 `no_results`，不冒充网络故障。
- 百科候选词的结果跨问法缓存 10 分钟，空结果缓存 1 分钟；限流期间也可复用未过期的主题结果。实际百科请求串行发送且间隔至少 1 秒，每轮最多两次模型发起的联网检索，避免反复追加请求消耗额度或触发限流。后续工具调用会返回已收集来源并提示停止搜索。
- 平台资料按规范化标题、描述和城市去重，重复记录共用第一条引用 ID；联网资料按 URL 去重。不修改数据库原始资料，不编造不同内容凑数量。
- Spring AI `model.stream` 接收 DeepSeek 增量正文，通过 SSE `delta` 即时推送；保留思考内容和工具调用参数供后续轮次使用，思考内容不发送到浏览器。
- 两个 AI 页面安全渲染 Markdown 标题、加粗、列表、表格、代码和链接；关闭原始 HTML 与模型插入的图片，链接仅允许 HTTP(S)。
- 文化助手左侧提供“历史对话”，手机端可展开查看。新对话自动按账号保存到本机浏览器，记录正文、来源、图片链接、时间及中断状态；刷新后可回看，支持确认后单条删除。新建对话不再删除上一段记录；在线上下文有效时可接着聊，过期或服务重启后仍可查看已保存内容并开启新对话。
- 图片设计先检查图片配置，再流式生成文字方案并提交生图；设计方案会进入图片提示词。缺少生图配置时提前提示，不再先生成长篇文字后才发现无法生图。
- 从业务后端验证 JWT；会话按真实用户 ID 隔离，模型无法指定用户身份。
- 保留完整 DeepSeek assistant 消息和工具结果，包括 `reasoning_content`。内部推理不会发送到前端。
- 限流、单用户单任务、全局最多 8 个活动请求、超时、中断、服务端固定上游地址和输入校验。
- 图片以用户 ID + 请求 ID 去重，成功及失败结果保留 30 分钟；不自动重试付费 API。

## 你需要准备

| 配置 | 是否必需 | 申请/来源 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 文本对话必需 | [DeepSeek 开放平台](https://platform.deepseek.com/)，创建 API Key，并确认 API 账户余额及模型可用性。网页聊天账号不等于 API 额度。 |
| `DEEPSEEK_MODEL` | 默认已设置 | 当前默认 `deepseek-flash`，可按账号实际可用模型更改。 |
| `VOLCENGINE_API_KEY` | 生图必需 | [火山方舟控制台](https://console.volcengine.com/ark)，创建 API Key，开通 Seedream 模型并确认额度。 |
| `VOLCENGINE_MODEL` | 默认已设置 | 默认 `doubao-seedream-5-0-260128`；如账号要求推理接入点 ID，可改为获授权的接入点。 |
| `SERPAPI_API_KEY` | 指定 Google／百度搜索时需要 | [SerpAPI](https://serpapi.com/)，创建搜索 API Key；配置 `WEB_SEARCH_ENGINE=google` 或 `baidu`。不填则使用公开搜索入口，受其网络与验证限制。 |
| MySQL 与平台登录账号 | 查询和身份验证必需 | 使用项目原有数据库及注册/登录功能；智能体通过后端查询，无需额外数据库账号。 |

无需申请 MCP、向量数据库或额外 OpenAI API Key。不要把 DeepSeek / 火山密钥填入任何 `VITE_` 变量，也无需发送到聊天。

## 本地启动

1. 按根目录说明准备 MySQL，导入 `backend/src/main/resources/db/init_v3_normalized.sql` 和所需文化资料。确认后端可登录。现有数据库如果字段不同，先备份、核对，**不要直接重建已有库**。新增智能体接口使用显式字段投影，不依赖不存在的 `tags` 列，也不执行建表或修改资料。
2. 在 `backend` 目录运行 `mvn spring-boot:run`，端口 8080。
3. 任选一种方式配置智能体：
   - 在启动智能体的 PowerShell 中设置环境变量：

     ```powershell
     $env:DEEPSEEK_API_KEY = '你的 DeepSeek API Key'
     $env:VOLCENGINE_API_KEY = '你的火山方舟 API Key' # 不需要图片时可省略
     mvn spring-boot:run
     ```

   - 或在 `agent-service` 目录复制 `application-local.example.yml` 为 `application-local.yml`，填写 `agent.deepseek-key`、`agent.image-key`，然后运行 `mvn spring-boot:run`。该文件已被 Git 忽略。**本地文件中的同名配置优先于 application.yml 内的环境变量占位配置；不要同时使用两种方式。**
4. 智能体默认监听 `127.0.0.1:8081`。访问 `http://127.0.0.1:8081/api/agent/health`，检查 `modelConfigured` / `imageConfigured`。它们仅代表密钥已填写，不代表平台鉴权或余额验证成功。
5. 在 `ai-culture-design` 运行 `npm run dev`，启动前会自动检查智能体；若未运行则构建并在后台启动，已运行则直接复用。也可在项目根目录执行 `npm run start:agent`。登录后访问 `/agent`。导航：AI 设计 → 文化智能体。Vite 将 `/agent-api` 代理至 8081；`AGENT_PORT` 环境变量可同时改变自动启动端口和代理目标。

若 Windows 拒绝绑定默认 5175 端口，可运行 `npm run dev -- --host 127.0.0.1 --port 6175`，打开终端实际显示的地址。业务后端在单独终端运行；智能体可以使用上述后台启动方式，也可继续在单独终端运行 Maven。后台服务日志位于 `agent-service/logs/`，启动命令会显示 PID。退出前端不会自动停止后台智能体；修改密钥后需停止原智能体进程并重新启动。

如果页面提示“智能体服务暂时无法连接”，先在项目根目录执行 `npm run start:agent`，再检查 `/agent-api/health`。开发代理会返回明确的 JSON 503 错误，避免只显示空 HTTP 500。直接运行 `vite` 而非 `npm run dev` 不会触发自动启动。

开启思考模式：设置 `DEEPSEEK_THINKING=true`，或本地配置 `agent.thinking: true`，重启服务。建议先验证默认非思考模式，再按质量和延迟需求开启。

## 接口与权限

所有聊天、图片、删除会话接口均要求 `Authorization: Bearer <平台登录token>`，仅 `/health` 公开。

| 接口 | 用途 |
| --- | --- |
| `POST /api/agent/chat` | `{sessionId?, requestId, message, allowImage}`；返回 SSE |
| `POST /api/agent/image` | `{requestId, prompt, size}`；返回 `{url}` |
| `DELETE /api/agent/sessions/{id}` | 删除自己的空闲会话 |
| `GET /api/agent/health` | 进程健康和配置是否存在 |

`requestId` 使用 UUID；新消息使用新 ID；网络中断后重放原请求保持 ID。聊天重放仅缓存该会话最近一次成功请求。设计页的“重新生成（使用额度）”会明确创建新图片任务。

SSE 事件为 `session`、`status`、`tool`、`source`、`text-start`、`delta`、`image`、`warning`、`done`、`error` 和每 10 秒一次 `ping`。`text-start` 表示一个新模型回合，清空上一回合临时文字；`delta` 返回 `{text}`，直接追加；`done` 返回 `{sessionId, content, sources, images}`，与最终内容校准。失败保留当前部分正文，重试清空临时文字。`error` 返回 `{code, message}`。HTTP 200 后仍须处理流中的错误事件。

业务后端新增的 `/api/agent/identity`、`/api/agent/culture`、`/api/agent/culture/{type}/{id}` 均受原有 Spring Security 认证保护。文化工具仅允许 `heritage` / `art`，不接受 SQL、任意 URL、数据库凭证或用户 ID。

## 部署及边界

- 当前面向单实例：会话、限流、图片去重数据在内存，重启后丢失。会话闲置 30 分钟过期，最多 500 个；历史最多保留 6 个完整回合并受字符预算限制。多实例/长期历史应接入共享存储与持久化任务账本后再部署；当前不能保证跨重启或超过 30 分钟后的图片去重。
- 浏览器历史与上述模型上下文独立，当前不跨浏览器或设备同步；清除浏览器数据会清除本机记录。历史功能上线前未保存的旧对话无法补回。图片保存的是提供商 URL，URL 过期后原图可能无法显示。存储不可用或已满时页面会提示保存失败，不会悄悄丢弃旧记录。
- 停止/断线会尽力中断当前请求并停止后续执行。已发送给外部图片平台的任务无法保证撤销，也无法保证退款。出现超时应先核对平台记录再明确发起新生成。
- 单次模型 HTTP 超时 45 秒、图片 HTTP 超时 115 秒、整轮默认 150 秒；未配置密钥时清晰报错，不返回模拟答案。需要更长思考时可调整受限配置，但应同步调整前端和反向代理超时。
- 默认不保存或发布作品，也不允许执行系统命令。资料摘要以普通文本展示，答复使用禁用 HTML 的 Markdown 渲染器。
- 原有通用搜索/列表仍有项目既存的实体与规范化表差异；智能体走新建的专用查询投影。数据库测试覆盖了智能体所需字段，不能代替在你的实际 MySQL 数据上的验收。
- 对外部署由 HTTPS 反向代理将 `/agent-api/` 映射到 `http://127.0.0.1:8081/api/agent/`，关闭缓冲，设置至少 180 秒读取超时；生产构建不会自带 Vite 代理。代理也应限制请求体大小和连接数。

  ```nginx
  location /agent-api/ {
      proxy_pass http://127.0.0.1:8081/api/agent/;
      proxy_http_version 1.1;
      proxy_buffering off;
      proxy_cache off;
      proxy_read_timeout 180s;
      client_max_body_size 64k;
      proxy_set_header Authorization $http_authorization;
  }
  ```

- `CULTURAL_BACKEND_URL`、`DEEPSEEK_BASE_URL`、`VOLCENGINE_BASE_URL` 是服务端部署配置，应仅由管理员设置。容器环境如需监听所有网卡，设置 `AGENT_BIND_ADDRESS=0.0.0.0` 并用网络策略限制访问。不要将开发用数据库、JWT 默认密钥用于公网。

## 验证

```powershell
# agent-service：本地模拟 DeepSeek/业务后端/生图平台，不产生 API 费用
mvn test
# backend：H2 MySQL 模式验证只读文化查询
mvn test
# ai-culture-design
node --test tests/agent.test.js tests/volcengine.test.js
npm run build
node tests/agent.browser.cjs
node tests/ai-design.browser.cjs
```

浏览器脚本需要 Chrome 和 Playwright；可通过 `PLAYWRIGHT_MODULE_PATH` 指向已有 Playwright，`FRONTEND_URL` 指向已启动的前端（默认 5175）。脚本拦截外部请求，不使用真实 Key。页面截图输出在 `tests/artifacts/`。

首次填入真实密钥后请验收：登录 → 查询南京云锦 → 查看资料 → 追问修改方案 → 勾选允许生图并明确要求图片 → 检查一张图片、后台调用记录与费用。再测试新会话、退出登录、无资料结果和错误密钥。协议测试已覆盖真实 Spring AI 序列化，但真实平台模型权限、余额、网络与图片效果仍须使用你的账号验证。

搜索缓存默认写入 `agent-service/data/search-cache.json`（已忽略版本管理），服务重启后恢复，最多保留 24 小时；`SEARCH_CACHE_FILE` 可覆盖位置。缓存保存公开网页摘要、查询词与原检索时间，不保存 API Key。实时失败时显示 `stale` 标记，页面展示原始时间，不能把历史缓存当作刚刚联网获取。连接错误后重建 HTTP 客户端，避免网络切换后复用失效连接。
