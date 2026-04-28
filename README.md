# 智能客服助手

智能客服系统，面向企业、部门及相关人员提供专业、智能的客户服务。

## 项目架构

后端当前以 `backend-springaialibaba` 为准，核心模块按业务域划分：

```text
controller  接口层，按 auth/chat/admin 分组
service     业务层，按 auth/chat/document/rag 分组
entity      实体层，按 auth/chat/document/admin 分组
repository  数据访问层，按 auth/chat/document/admin 分组
config      安全、AI、初始化、记忆配置
```

```
gov-service-assistant/
├── backend-springaialibaba/    # 后端 Spring Boot + Spring AI Alibaba 项目
│   ├── src/main/java/
│   │   └── com/gov/assistant/
│   │       ├── config/        # 安全、AI、会话记忆、初始化配置
│   │       ├── controller/    # API 接口
│   │       │   ├── admin/
│   │       │   ├── auth/
│   │       │   └── chat/
│   │       ├── entity/        # JPA 实体
│   │       │   ├── admin/
│   │       │   ├── auth/
│   │       │   ├── chat/
│   │       │   └── document/
│   │       ├── repository/    # JPA 数据访问
│   │       │   ├── admin/
│   │       │   ├── auth/
│   │       │   ├── chat/
│   │       │   └── document/
│   │       ├── service/       # 业务服务
│   │       │   ├── auth/
│   │       │   ├── chat/
│   │       │   ├── document/
│   │       │   └── rag/
│   │       └── GovService_Alibaba_AssistantApplication.java
│   └── src/main/resources/
│       ├── application.yml    # 后端配置
│       └── prompt/            # AI 提示词
│
├── frontend/                   # 前端 Vue3 项目
│   ├── src/
│   │   ├── components/        # 组件
│   │   │   ├── chat/          # 客服组件
│   │   │   └── admin/         # 管理后台组件
│   │   ├── views/             # 页面视图
│   │   ├── api/               # API封装
│   │   └── utils/             # 工具类
│   └── package.json
│
└── docs/                       # 项目文档
```

## 功能特性

### 智能客服功能
- ✅ 多轮对话支持
- ✅ 登录后查看个人会话历史
- ✅ 意图自动识别（6大类意图）
- ✅ Spring AI Alibaba RAG 语义检索
- ✅ 文档知识库检索与问答
- ✅ 智能转人工判断
- ✅ 会话满意度评价
- ✅ 快捷服务自动发送

### 管理后台功能
- ✅ 数据概览仪表盘
- ✅ 会话记录查询
- ✅ 知识库管理
- ✅ 文档知识库管理
- ✅ 快捷服务配置
- ✅ 人员管理与权限控制
- ✅ 统计分析报表
- ✅ 人工客服接入

### 意图分类
1. **政策咨询** - 监管政策法规
2. **业务办理** - 各类业务流程指导
3. **进度查询** - 业务办理进度查询
4. **技术支持** - 系统使用问题
5. **账号权限** - 账号权限申请
6. **投诉建议** - 投诉与建议反馈

## 技术栈

### 后端
- Java 21
- Spring Boot 3.2
- Spring Data JPA
- Spring AI Alibaba
- DashScope AI / 通义千问
- Spring AI Chat Memory
- Spring AI VectorStore / RAG
- H2/MySQL 数据库

### 前端
- Vue 3.4
- Element Plus
- Vue Router
- Pinia
- ECharts
- Axios

## 快速开始

### 后端启动

```bash
cd backend-springaialibaba
mvn clean install
mvn spring-boot:run
```

后端服务默认运行在 http://localhost:8099

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端服务默认运行在 http://localhost:5173

## 配置说明

### 后端配置 (application.yml)

```yaml
spring:
  ai:
    dashscope:
      api-key: your-dashscope-api-key
      chat:
        options:
          model: qwen-plus
      embedding:
        options:
          model: text-embedding-v4
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1

app:
  rag:
    top-k: 5
    similarity-threshold: 0.35
  security:
    token-secret: your-token-secret
    default-admin:
      username: admin
      password: admin123
```

也可以通过环境变量配置：

```bash
export AI_DASHSCOPE_API_KEY=your-dashscope-api-key
export APP_SECURITY_TOKEN_SECRET=your-token-secret
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD=your-strong-password
```

管理后台默认需要登录。首次启动会自动创建默认管理员账号，生产环境请务必修改默认密码。

前台智能客服也需要登录后使用，系统会按登录账号保存和展示个人会话历史。开发环境默认账号：

- 管理员：`admin / admin123`
- 普通用户：`user / user123`

### 前端配置

前端通过 Vite 代理访问后端 API：
```javascript
// vite.config.js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8099',
      changeOrigin: true
    }
  }
}
```

## 使用说明

### 智能客服页面
访问 http://localhost:5173 进入客服对话界面

### 管理后台
访问 http://localhost:5173/admin 进入管理后台

管理后台功能：
- 数据概览：查看今日会话、总会话、转人工数量等
- 会话管理：查看所有会话记录和详情
- 知识库管理：维护FAQ知识库
- 文档知识库：上传和管理文档知识片段
- 快捷服务：配置前台快捷服务按钮与自动发送内容
- 人员管理：维护系统用户、角色和状态
- 统计分析：查看意图分布、满意度统计等
- 人工接入：处理需要人工介入的会话

## 数据库设计

### 核心表
- `conversations` - 会话表
- `messages` - 消息表
- `user_accounts` - 用户账号表
- `knowledge_items` - 知识库表
- `knowledge_documents` - 文档知识库文件表
- `knowledge_chunks` - 文档知识片段表
- `quick_services` - 快捷服务配置表
- `statistics` - 统计表

## 开发计划

### 已完成
- [x] 项目基础架构搭建
- [x] 数据库实体设计
- [x] 基础API接口
- [x] 前端页面框架
- [x] 意图识别服务
- [x] 登录认证与角色权限
- [x] 多轮对话与会话记忆
- [x] Spring AI Alibaba RAG 检索
- [x] 文档知识库与 OCR 解析
- [x] 快捷服务后台配置
- [x] 人工客服接入

### 待开发
- [ ] 流式响应实现
- [ ] WebSocket人工客服
- [ ] 数据导出功能
- [ ] 外部向量库接入

## 注意事项

1. 需要配置 DashScope API Key
2. 生产环境建议使用 MySQL 替代 H2
3. 建议配置 Nginx 进行反向代理

## License

MIT License
