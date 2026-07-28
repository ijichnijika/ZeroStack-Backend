# ZeroStack Backend (后端服务)

ZeroStack 是一个企业级的、基于大语言模型 (LLM) 驱动的全栈智能代码生成平台后端。
它采用了最新的 **Java 21** 与 **Spring Boot 3** 架构，深度整合了 **LangChain4j** 与 **Redisson**，不仅支持常规的对话式生成，更引入了高度可定制的 **Agent 工作流引擎** 和 **打字机级别的 SSE 原生流式响应**。

本后端项目致力于提供一个高性能、安全、可扩展的 AIGC 平台基座。

---

## 🌟 详细功能特性 (Detailed Features)

### 1. 核心应用与 AI 生成能力
- **AI 一键生成标题**：自动根据用户的需求提取应用的关键字，一键调用 AI 接口 (`AiTitleGeneratorService`) 生成高度匹配的应用标题和简介。
- **全栈代码智能生成**：通过大模型一键生成完整可运行的项目源码。
- **智能路由 (`RouterNode`)**：系统利用 AI 的意图理解能力，无需人工指定即可实现自动路由：
  - 若用户需求简单：自动生成单文件 `HTML`。
  - 若需多文件结构：自动生成 `MULTI_FILE`（分离 HTML、CSS、JS）。
  - 若需求复杂：自动拉取脚手架，全量生成 `VUE_PROJECT` (现代化多页面前端项目)。

### 2. 自动化构建与一键部署 (Build & Deploy Automation)
不仅仅是生成代码，ZeroStack 直接打通了 DevOps 流程：
- **项目自动化构建 (`ProjectBuilder`)**：针对 Vue 项目，在后台起子进程异步执行 `npm install` 与构建操作。
- **一键静态部署**：系统内置基于 Nginx 的静态页面自动挂载方案。构建完毕后，直接将产物暴露为静态 Web 服务进行跨域托管，真正实现“生成即上线”。
- **源码 ZIP 一键下载**：无论生成的是单 HTML 还是复杂的全栈架构，后端都支持将其自动打包为标准 ZIP 并供开发者一键下载到本地二次开发。
- **COS 自动截图对象存储**：系统能够自动为生成的应用捕获缩略截图，并对接上传至云对象存储 (COS)。

### 3. Agent 工作流与原生流式交互 (SSE & Deep Thinking)
- **SSE 流式通讯与进度推送**：放弃传统的请求-等待，利用 Server-Sent Events (SSE) 实现打字机级数据推送。在使用 Agent 模式时，将**工作流图的运行节点状态**（如：“正在分析需求”、“构建项目目录”）转化包装成 `WorkflowProgressMessage` 实时推送至前台，做到流程绝对透明。
- **原生深度思考 (Deep Thinking)**：深度集成适配带有 `<think>` 过程的大语言模型，流式分离正文与思考部分。
- **Tool Calling 控制**：规范了大模型的工具调用范式，并内置了 `ExitTool` 等逻辑拦截陷入循环调用的 Agent。

### 4. 多租户、多应用与记忆隔离 (Memory Isolation)
- **多级缓存对话隔离**：深度结合 **Redis** (分布式层) 与 **Caffeine** (本地 JVM 高速缓存)，针对不同 `AppId` 下的上下文提供了极其严格的隔离边界。防止项目间 AI 语境和聊天历史 (Chat Memory) 发生污染交叉。
- **聊天历史落库**：对话历史模块不仅存于缓存，更可持久化落入数据库，支持用户随时回溯。

### 5. 企业级防刷与安全护栏 (Rate Limiting & Guardrails)
- **Redisson 分布式限流**：自定义 `@RateLimit` 切面注解，支持通过 AOP 在任意 Controller 接口上实现基于 `IP`、`用户ID` 或是 `API 全局接口` 的频控，拦截爬虫和恶意调用。
- **AI 智能护栏 (Guardrails)**：
  - `PromptSafetyInputGuardrail`：前置拦截恶意提示词注入 (Prompt Injection) 及敏感词。
  - `RetryOutputGuardrail`：对于生成的非法 JSON 代码块自动触发重试纠正补偿机制。

---

## 🛠️ 技术选型 (Tech Stack)

- **核心架构**: Java 21, Spring Boot 3.x
- **数据库**: MySQL 8.x, MyBatis-Plus
- **大模型生态**: LangChain4j
- **高性能与中间件**: 
  - `Redis` (限流/持久缓存)
  - `Caffeine` (极速本地缓存)
  - `Redisson` (分布式锁/限流工具库)
- **高级特性**: SSE (流式网络推送)、AOP (切面编程)

---

## 🚀 快速启动指南 (Quick Start)

### 1. 环境准备
确保本机安装了以下环境：
- JDK 21+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+ (如果需要体验本地自动 npm 构建特性)

### 2. 初始化配置
项目在 `.gitignore` 中已屏蔽敏感配置。请在 `src/main/resources` 下复制 `application.yaml` 并改名为 `application-local.yaml`，填入您的密钥：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/zero_stack?serverTimezone=Asia/Shanghai
    username: root
    password: root_password
  data:
    redis:
      host: localhost
      port: 6379

ai:
  api-key: "YOUR_LANGCHAIN4J_SUPPORTED_API_KEY"
  base-url: "YOUR_LLM_BASE_URL"
```

### 3. 构建与启动
```bash
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```
*API 默认监听 8080 端口。*
