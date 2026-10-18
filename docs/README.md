# 智学籍 · AI 原生学籍管理平台

> 全国高校 AI Web 开发挑战赛参赛作品
> 在既有 Spring Boot + MyBatis + PostgreSQL 学籍管理系统之上，做 **AI Native 化升级**：把"表单+报表"升级为"对话+Agent 工具调用+主动洞察"。

---

## 一、作品定位

| 维度 | 传统学籍系统 | 本作品（AI 原生） |
| --- | --- | --- |
| 交互方式 | 菜单 → 表单 → 报表 | **自然语言提问**，AI 自主规划并调用工具取数 |
| 数据获取 | 预置报表，需求变更就要改代码 | **NL2SQL**：一句话生成只读 SQL 并执行，附统计口径 |
| 制度查询 | 翻 PDF / 找文件 | **RAG 制度知识库**：回答逐条标注出处，可溯源不编造 |
| 管理动作 | 被动等老师查 | **AI 主动洞察**：自动跑数据发现风险、数据缺口与待办积压 |
| 学生视角 | 查分数 | **一人一档 360° 画像 + AI 点评与帮扶建议** |

**核心主张**：AI 不是"加一个聊天框"，而是深度嵌入业务流程——所有结论都来自真实数据库，工具调用过程、生成的 SQL、引用的制度原文全部对用户可见。

---

## 二、技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Spring Boot 2.7 · MyBatis · PostgreSQL · SSE 流式输出 |
| AI 层 | OpenAI 兼容协议（DeepSeek / 通义 / 智谱 / OpenAI / vLLM / Ollama） · Function Calling · 自研 Agent 循环 · 混合检索 RAG · NL2SQL 安全执行器 |
| 前端 | Vue 3 · Vite 6 · Pinia · Vue Router（hash 模式）· ECharts 5 · marked + DOMPurify |
| 部署 | 前端构建产物打进 Spring Boot 静态目录，**单 jar 启动**，`http://localhost:8080/app/` |

---

## 三、目录结构

```
student_manage
├─ src/main/java/com/school/student_manage
│  ├─ controller / service / mapper / entity ...   # 原有学籍业务（未改动，保持兼容）
│  ├─ config/SpaForwardConfig.java                 # /app/ 前端入口转发
│  └─ ai/                                          # ★ 本次新增的 AI 能力模块
│     ├─ llm/       OpenAI 兼容客户端 · 本地规则引擎 · 路由器（熔断降级）
│     ├─ tool/      9 个数据工具 + 注册中心 + 统一结果信封
│     ├─ agent/     Agent 循环（规划 → 调工具 → 观测 → 作答）
│     ├─ rag/       知识库服务 · 中文混合检索 · 文本切分
│     ├─ data/      只读 SQL 执行器（安全护栏）· 库表结构注入
│     ├─ service/   对话编排(SSE) · 主动洞察 · NL2SQL
│     ├─ store/     会话存储 · 幂等建表
│     ├─ prompt/    提示词中心
│     └─ web/       AI REST 接口
├─ src/main/resources
│  ├─ ai/knowledge-seed.json                       # 内置制度示例语料（9 篇）
│  └─ static/app/                                  # ★ Vue3 构建产物（访问 /app/）
└─ frontend/                                       # ★ Vue3 独立工程
   └─ src/{views,components,stores,api,composables,styles}
```

---

## 四、快速启动

### 1. 启动后端（含 Web 前端）

```powershell
# 可选：配置大模型密钥（不配置则自动使用本地规则引擎，功能完整可演示）
$env:AI_API_KEY = "sk-xxxxxxxx"
.\mvnw.cmd spring-boot:run
```

打开 <http://localhost:8080/app/> 即可使用。
原有的 layui 页面（`/login.html`、`/admin.html`、`/student.html`）保持可用，互不影响。

### 2. 前端开发模式（可选，改前端时用）

```powershell
cd frontend
npm install
npm run dev      # http://localhost:5173，接口自动代理到 8080
```

### 3. 重新构建前端产物

```powershell
cd frontend
npm run build    # 自动输出到 ../src/main/resources/static/app
```

### 4. 演示账号

| 账号 | 密码 | 身份 | 说明 |
| --- | --- | --- | --- |
| admin | 123456 | admin | 系统管理员 |
| jiaowu1 | 654321 | 教务 | 张教务 |
| 2406010101 | 123456 | student | 学生 |
| 2023001 | 111111 | 学生 | 张三 |

> 未登录也能体验 AI 助手（会话记录归属"访客"），登录后可获得身份化上下文。

---

## 五、六大功能页面

| 页面 | 路由 | 亮点 |
| --- | --- | --- |
| **AI 助手** | `#/assistant` | Agent 多轮对话，左侧可视化展示每一步工具调用与结果，回答流式输出，Markdown + 图表渲染，历史会话可回溯 |
| **智能看板** | `#/dashboard` | KPI + 分布/成绩图表 + **AI 主动洞察**（自动发现风险、数据缺口）+ 数据质量体检 |
| **学业预警** | `#/warning` | 规则引擎（不及格/低均分/待审异动/成绩缺失/异常分）筛名单 + **AI 分层干预建议** |
| **学生画像** | `#/students` | 一人一档：学籍+成绩+奖惩+异动+档案，**AI 画像点评与帮扶建议**，可跳转助手继续追问 |
| **智能取数** | `#/sql` | 自然语言 → 只读 SQL（全文展示）→ 执行 → 表格/图表；右侧明示 5 条安全护栏 |
| **制度知识库** | `#/knowledge` | RAG 语料管理 + 检索测试（命中片段与相关度）+ 原理说明 |

---

## 六、文档索引

| 文档 | 内容 |
| --- | --- |
| [01-作品说明与评分点对照.md](./01-作品说明与评分点对照.md) | 赛题理解、创新点、评分点逐条对照与实现证据 |
| [02-系统架构与AI实现.md](./02-系统架构与AI实现.md) | 分层架构、Agent 循环、RAG 检索、NL2SQL 安全、降级策略 |
| [03-接口与配置手册.md](./03-接口与配置手册.md) | 全部 AI 接口（REST + SSE 协议）、大模型配置与切换方法 |
| [04-演示脚本.md](./04-演示脚本.md) | 答辩演示动线、每步说什么、预期屏幕效果、异常兜底话术 |
