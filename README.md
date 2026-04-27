# 国资客服智能助手

国有资本监管平台智能客服系统，面向国有企业、监管部门及相关人员提供专业、智能的客户服务。

## 项目架构

```
gov-service-assistant/
├── backend/                    # 后端 Spring Boot 项目
│   ├── src/main/java/
│   │   └── com/gov/assistant/
│   │       ├── config/        # 配置类
│   │       ├── controller/    # 控制器
│   │       ├── service/       # 业务逻辑
│   │       ├── entity/        # 实体类
│   │       └── repository/    # 数据访问
│   └── src/main/resources/
│       └── prompt/            # AI提示词
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
- ✅ 意图自动识别（6大类意图）
- ✅ 知识库检索与问答
- ✅ 智能转人工判断
- ✅ 会话满意度评价

### 管理后台功能
- ✅ 数据概览仪表盘
- ✅ 会话记录查询
- ✅ 知识库管理
- ✅ 统计分析报表
- ✅ 人工客服接入

### 意图分类
1. **政策咨询** - 国资监管政策法规
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
- LangChain4j 0.35
- DashScope AI
- H2/MySQL 数据库
- Redis 缓存

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
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务默认运行在 http://localhost:8080

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
langchain4j:
  dashscope:
    api-key: your-dashscope-api-key
    model-name: qwen-max
```

### 前端配置

前端通过 Vite 代理访问后端 API：
```javascript
// vite.config.js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
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
- 统计分析：查看意图分布、满意度统计等
- 人工接入：处理需要人工介入的会话

## 数据库设计

### 核心表
- `conversations` - 会话表
- `messages` - 消息表
- `knowledge_items` - 知识库表
- `statistics` - 统计表

## 开发计划

### 已完成
- [x] 项目基础架构搭建
- [x] 数据库实体设计
- [x] 基础API接口
- [x] 前端页面框架
- [x] 意图识别服务

### 待开发
- [ ] AI服务集成配置
- [ ] 知识库数据初始化
- [ ] 流式响应实现
- [ ] WebSocket人工客服
- [ ] 权限管理
- [ ] 数据导出功能

## 注意事项

1. 需要配置 DashScope API Key
2. 生产环境建议使用 MySQL 替代 H2
3. 需要配置 Redis 用于会话缓存
4. 建议配置 Nginx 进行反向代理

## License

MIT License
