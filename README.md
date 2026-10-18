# 智学籍 · AI 原生学籍管理平台

全国高校 AI Web 开发挑战赛参赛作品。
在既有 **Spring Boot + MyBatis + PostgreSQL** 学籍管理系统之上，新增 **AI Agent 工具调用、RAG 制度知识库、自然语言取数（NL2SQL）、学业预警与智能洞察**，并配套一个全新的 **Vue 3 + Vite** 前台（访问 `http://localhost:8080/app/`）。

> 原有学籍业务代码（controller / service / mapper / layui 页面）**零改动**，AI 能力全部收敛在 `com.school.student_manage.ai` 包内，可随时回退。

---

## 快速开始

```powershell
# 1. （可选）配置大模型密钥：不配置时自动使用本地规则引擎，功能完整可演示
$env:AI_API_KEY = "sk-xxxxxxxx"      # 支持 DeepSeek / 通义 / 智谱 / OpenAI / vLLM / Ollama

# 2. 启动（含已构建好的前端）
.\mvnw.cmd spring-boot:run

# 3. 打开
start http://localhost:8080/app/
```

演示账号：`admin / 123456`（管理员）、`jiaowu1 / 654321`（教务）、`2406010101 / 123456`（学生）

前端开发模式：

```powershell
cd frontend
npm install
npm run dev        # http://localhost:5173，接口代理到 8080
npm run build      # 产物输出到 ../src/main/resources/static/app
```

---

## 六大能力

| 页面 | 路由 | 说明 |
| --- | --- | --- |
| AI 助手 | `#/assistant` | Agent 自主调用 9 个数据工具，工具调用过程、参数、耗时全程可视化，回答流式输出 |
| 智能看板 | `#/dashboard` | KPI + 分布/成绩图表 + **AI 主动洞察**（含数据质量体检） |
| 学业预警 | `#/warning` | 规则引擎筛名单 + **AI 分层干预建议** |
| 学生画像 | `#/students` | 一人一档 360° 画像 + **AI 点评与帮扶建议** |
| 智能取数 | `#/sql` | 自然语言 → 只读 SQL（全文展示）→ 结果表格 / 图表，附 5 条安全护栏说明 |
| 制度知识库 | `#/knowledge` | RAG 语料管理 + 检索测试 + 回答可溯源 |

---

## 关键设计

- **双引擎**：远程大模型（OpenAI 兼容协议）与本地规则引擎实现同一个 `LlmClient` 接口，连续失败自动熔断降级 —— 无 Key / 断网也能完整演示。
- **工具信封协议**：`ToolResult{render, summary, data, chart, hints}` 一份数据三处复用：喂模型（压缩文本）、驱动前端渲染、组装本地兜底答案。
- **NL2SQL 安全**：仅 SELECT/WITH、关键字黑名单、12 张表白名单、外层 `LIMIT 200`、只读事务、8 秒超时；生成的 SQL 全文展示给用户。
- **RAG 可溯源**：中文二元分词 TF-IDF + 可选向量余弦融合；回答附「制度依据」卡片，知识库无条目时明确说明而非虚构。
- **数据质量自省**：主动指出成绩覆盖率仅 40%、存在超过 100 分的异常成绩、学籍状态取值不统一等真实数据问题，并说明对结论的影响。

---

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/README.md](./docs/README.md) | 项目总览、目录结构、功能页面 |
| [docs/01-作品说明与评分点对照.md](./docs/01-作品说明与评分点对照.md) | 赛题理解、创新点、评分点逐条对照 |
| [docs/02-系统架构与AI实现.md](./docs/02-系统架构与AI实现.md) | 架构图、Agent 循环、RAG、NL2SQL 安全、降级策略 |
| [docs/03-接口与配置手册.md](./docs/03-接口与配置手册.md) | 全部接口（REST + SSE 协议）与大模型配置 |
| [docs/04-演示脚本.md](./docs/04-演示脚本.md) | 答辩演示动线与兜底话术 |
