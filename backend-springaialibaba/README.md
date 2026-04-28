# backend-springaialibaba

智能客服助手后端，基于 Spring Boot、Spring AI Alibaba、DashScope 和 Spring Data JPA 实现。

## 目录结构

核心模块按业务域划分：

```text
controller  接口层，按 auth/chat/admin 分组
service     业务层，按 auth/chat/document/rag 分组
entity      实体层，按 auth/chat/document/admin 分组
repository  数据访问层，按 auth/chat/document/admin 分组
config      安全、AI、初始化、记忆配置
```

```text
backend-springaialibaba
├── pom.xml
└── src/main
    ├── java/com/gov/assistant
    │   ├── GovService_Alibaba_AssistantApplication.java
    │   ├── config                 # 全局配置
    │   ├── controller             # API 接口
    │   │   ├── admin
    │   │   ├── auth
    │   │   └── chat
    │   ├── entity                 # JPA 实体
    │   │   ├── admin
    │   │   ├── auth
    │   │   ├── chat
    │   │   └── document
    │   ├── repository             # JPA Repository
    │   │   ├── admin
    │   │   ├── auth
    │   │   ├── chat
    │   │   └── document
    │   └── service                # 业务服务
    │       ├── auth
    │       ├── chat
    │       ├── document
    │       └── rag
    └── resources
        ├── application.yml
        └── prompt
            └── system-prompt.txt
```

## 模块说明

| 模块 | 说明 |
| --- | --- |
| `controller/auth` | 登录认证接口 |
| `controller/chat` | 前台智能客服接口 |
| `controller/admin` | 管理后台接口 |
| `service/auth` | 登录校验、Token 签发、当前用户获取 |
| `service/chat` | 聊天主链路、会话管理、多轮记忆、意图识别 |
| `service/document` | 手工知识库、文档上传、解析、OCR、关键词检索 |
| `service/rag` | Spring AI Alibaba Embedding + VectorStore 语义检索 |
| `entity/*` | 按业务域拆分的 JPA 实体 |
| `repository/*` | 按业务域拆分的 JPA Repository |
| `config` | Spring Security、AI 参数、Chat Memory、初始化数据、异常处理 |

## 核心链路

用户消息进入 `ChatController` 后，由 `ChatService` 编排处理：

1. 校验会话状态。
2. 识别用户意图。
3. 判断是否需要转人工。
4. 优先匹配标准答案。
5. 使用 RAG 语义召回知识库。
6. RAG 未命中时回退到文档关键词检索。
7. 再回退到手工知识库关键词检索。
8. 最后生成兜底回复或调用大模型回答。

## RAG 说明

当前 RAG 使用 Spring AI Alibaba 的 Embedding 能力和 `SimpleVectorStore`：

- 手工知识库来源：`KnowledgeItem`
- 文档知识库来源：`KnowledgeChunk`
- 默认模型：`text-embedding-v4`
- 默认召回数量：`5`
- 默认相似度阈值：`0.35`

管理接口：

```text
GET  /api/admin/rag/status
POST /api/admin/rag/rebuild
```

当前是内存向量库方案，适合开发和小规模知识库。生产环境知识量较大时，建议替换为 Redis、Milvus、DashVector 或支持向量检索的数据库。

## 运行

```bash
cd backend-springaialibaba
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8099
```

## 默认账号

开发环境首次启动会自动初始化账号：

```text
管理员：admin / admin123
普通用户：user / user123
```

生产环境请通过环境变量修改默认密码和 Token 密钥。
