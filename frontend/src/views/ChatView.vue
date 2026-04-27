<template>
  <div class="chat-view">
    <!-- 头部 -->
    <header class="chat-header">
      <div class="header-left">
        <div class="logo">
          <el-icon size="28" color="#1a5fb4"><Service /></el-icon>
        </div>
        <div class="brand">
          <h1>国资客服智能助手</h1>
          <span class="subtitle">国有资本监管平台</span>
        </div>
      </div>
      <div class="header-right">
        <el-button type="primary" text @click="goToAdmin">
          <el-icon><Setting /></el-icon>
          管理后台
        </el-button>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="chat-main">
      <!-- 左侧快捷入口 -->
      <aside class="quick-actions">
        <div class="section-title">快捷服务</div>
        <div class="action-buttons">
          <el-button
            v-for="action in quickActions"
            :key="action.key"
            class="action-btn"
            @click="quickSend(action.text)"
          >
            <el-icon :size="18"><component :is="action.icon" /></el-icon>
            <span>{{ action.label }}</span>
          </el-button>
        </div>

        <div class="section-title" style="margin-top: 24px;">常见问题</div>
        <div class="faq-list">
          <div
            v-for="faq in frequentQuestions"
            :key="faq.id"
            class="faq-item"
            @click="quickSend(faq.question)"
          >
            <el-icon><QuestionFilled /></el-icon>
            <span>{{ faq.question }}</span>
          </div>
        </div>
      </aside>

      <!-- 聊天区域 -->
      <section class="chat-area">
        <!-- 欢迎界面 -->
        <div v-if="messages.length === 0" class="welcome-panel">
          <div class="welcome-content">
            <div class="welcome-icon">
              <el-icon size="64" color="#1a5fb4"><ChatDotRound /></el-icon>
            </div>
            <h2 class="welcome-title">您好，我是国资客服智能助手</h2>
            <p class="welcome-desc">
              我可以为您解答关于国有资本监管的政策法规、业务办理、系统使用等问题
            </p>
            <div class="intent-tags">
              <el-tag
                v-for="intent in intentTypes"
                :key="intent.key"
                class="intent-tag"
                effect="plain"
                @click="quickSend(intent.example)"
              >
                {{ intent.label }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-else class="message-list" ref="messageListRef">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="{
              'user-message': msg.senderType === 'USER',
              'ai-message': msg.senderType === 'AI',
              'system-message': msg.senderType === 'SYSTEM',
              'human-message': msg.senderType === 'HUMAN'
            }"
          >
            <div class="message-avatar">
              <el-avatar
                :size="40"
                :icon="getMessageAvatar(msg.senderType)"
                :style="{ background: getMessageAvatarBg(msg.senderType) }"
              />
            </div>
            <div class="message-content">
              <div class="message-header">
                <span class="sender-name">{{ getSenderName(msg.senderType) }}</span>
                <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
              </div>
              <div class="message-body" :class="{ 'system-body': msg.senderType === 'SYSTEM' }" v-html="renderMarkdown(msg.content)"></div>
              <div v-if="getMessageImageChunks(msg).length" class="source-images">
                <div class="source-images-title">来源图片</div>
                <div class="source-images-grid">
                  <img
                    v-for="chunkId in getMessageImageChunks(msg)"
                    :key="chunkId"
                    class="source-image"
                    :src="getChunkImageUrl(chunkId)"
                    :alt="`来源图片 ${chunkId}`"
                  />
                </div>
              </div>
              <div v-if="getSystemActions(msg).length" class="system-actions">
                <el-button
                  v-for="action in getSystemActions(msg)"
                  :key="action.key"
                  size="small"
                  :type="action.type"
                  plain
                  @click="handleSystemAction(action)"
                >
                  {{ action.label }}
                </el-button>
              </div>

              <!-- 意图标签 -->
              <div v-if="msg.intentType && msg.senderType === 'USER'" class="intent-label">
                <el-tag size="small" type="info">{{ getIntentLabel(msg.intentType) }}</el-tag>
              </div>
            </div>
          </div>

          <!-- 加载状态 -->
          <div v-if="isLoading" class="message-item ai-message">
            <div class="message-avatar">
              <el-avatar :size="40" :icon="Service" style="background: #1a5fb4" />
            </div>
            <div class="message-content">
              <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <div class="input-wrapper">
            <el-input
              ref="chatInputRef"
              v-model="inputMessage"
              type="textarea"
              :rows="2"
              placeholder="请输入您的问题..."
              resize="none"
              @keydown.enter.prevent="sendMessage"
              :disabled="isLoading"
            />
            <div class="input-actions">
              <el-button
                type="primary"
                :disabled="!inputMessage.trim() || isLoading"
                @click="sendMessage"
              >
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
              <el-button
                v-if="messages.length > 0"
                @click="showRatingDialog"
              >
                <el-icon><CircleCheck /></el-icon>
                结束会话
              </el-button>
            </div>
          </div>
          <div class="input-tips">
            <el-icon><InfoFilled /></el-icon>
            <span>AI助手可能产生不准确信息，重要问题请核实或转人工服务</span>
          </div>
        </div>
      </section>
    </main>

    <!-- 满意度评价对话框 -->
    <el-dialog
      v-model="ratingDialogVisible"
      title="会话评价"
      width="400px"
    >
      <div class="rating-content">
        <p>请对本次服务进行评价：</p>
        <el-rate v-model="rating" :max="5" show-score />
        <el-input
          v-model="ratingComment"
          type="textarea"
          :rows="3"
          placeholder="请输入您的建议或意见（选填）"
          style="margin-top: 16px"
        />
      </div>
      <template #footer>
        <el-button @click="ratingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRating">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import {
  Service, Setting, UserFilled, ChatDotRound,
  Promotion, CircleCheck, InfoFilled, QuestionFilled,
  Document, OfficeBuilding, Search, Tools, User, MessageBox
} from '@element-plus/icons-vue'
import {
  createConversation, sendMessage as sendMessageApi, getConversationHistory,
  closeConversation, getFrequentQuestions
} from '@/api/chat.js'

const router = useRouter()

// 配置 marked
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true
})

// 状态
const sessionId = ref('')
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const messageListRef = ref(null)
const chatInputRef = ref(null)
const frequentQuestions = ref([])
const ratingDialogVisible = ref(false)
const rating = ref(5)
const ratingComment = ref('')

// 轮询定时器
let messagePollTimer = null
let lastMessageCount = 0

// 快捷操作
const quickActions = [
  { key: 'policy', label: '政策咨询', icon: Document, text: '我想了解最新的国资监管政策' },
  { key: 'business', label: '业务办理', icon: OfficeBuilding, text: '如何办理国有资本产权登记？' },
  { key: 'query', label: '进度查询', icon: Search, text: '查询我的业务办理进度' },
  { key: 'support', label: '技术支持', icon: Tools, text: '系统登录遇到问题怎么办？' },
  { key: 'account', label: '账号权限', icon: User, text: '申请账号权限开通' },
  { key: 'feedback', label: '投诉建议', icon: MessageBox, text: '我有投诉建议要反馈' }
]

// 意图类型
const intentTypes = [
  { key: 'policy', label: '政策咨询', example: '最新的国有资产评估管理办法是什么？' },
  { key: 'business', label: '业务办理', example: '产权登记需要准备哪些材料？' },
  { key: 'query', label: '进度查询', example: '查询我的备案申请进度' },
  { key: 'support', label: '技术支持', example: '忘记密码如何重置？' }
]

// 获取意图标签
function getIntentLabel(intentType) {
  const labels = {
    'POLICY_CONSULTATION': '政策咨询',
    'BUSINESS_PROCESSING': '业务办理',
    'PROGRESS_QUERY': '进度查询',
    'TECHNICAL_SUPPORT': '技术支持',
    'ACCOUNT_PERMISSION': '账号权限',
    'COMPLAINT_SUGGESTION': '投诉建议',
    'OTHER': '其他'
  }
  return labels[intentType] || intentType
}

// 格式化时间
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 渲染 Markdown
function renderMarkdown(content) {
  return marked(content)
}

function parseChunkIds(value) {
  if (!value) return []
  if (Array.isArray(value)) return value.map(item => String(item)).filter(Boolean)
  return String(value)
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

function getMessageImageChunks(msg) {
  if (!msg || msg.senderType !== 'AI') {
    return []
  }
  return parseChunkIds(msg.knowledgeSourceChunkIds)
}

function getChunkImageUrl(chunkId) {
  return `/api/chat/document-chunks/${chunkId}/image`
}

function getSystemActions(msg) {
  if (msg.senderType !== 'SYSTEM' || !msg.content) {
    return []
  }

  if (msg.content.includes('您可以选择') || msg.content.includes('转人工客服')) {
    return [
      { key: 'change-topic', label: '换个话题', type: 'primary', action: 'change-topic' },
      { key: 'handoff', label: '转人工客服', type: 'warning', action: 'handoff' }
    ]
  }

  return []
}

function handleSystemAction(action) {
  if (!action) return

  if (action.action === 'handoff') {
    quickSend('转人工')
    return
  }

  inputMessage.value = ''
  nextTick(() => {
    chatInputRef.value?.focus?.()
  })
}

// 获取消息发送者名称
function getSenderName(senderType) {
  const names = {
    'USER': '您',
    'AI': '智能助手',
    'HUMAN': '人工客服',
    'SYSTEM': '系统'
  }
  return names[senderType] || '智能助手'
}

// 获取消息头像图标
function getMessageAvatar(senderType) {
  const icons = {
    'USER': UserFilled,
    'AI': Service,
    'HUMAN': Service,
    'SYSTEM': Service
  }
  return icons[senderType] || Service
}

// 获取消息头像背景色
function getMessageAvatarBg(senderType) {
  const colors = {
    'USER': '#909399',
    'AI': '#1a5fb4',
    'HUMAN': '#67c23a',
    'SYSTEM': '#e6a23c'
  }
  return colors[senderType] || '#1a5fb4'
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// 快捷发送
function quickSend(text) {
  inputMessage.value = text
  sendMessage()
}

// 发送消息
async function sendMessage() {
  const message = inputMessage.value.trim()
  if (!message || isLoading.value) return

  // 暂停轮询，避免冲突
  stopMessagePolling()

  inputMessage.value = ''
  isLoading.value = true

  // 添加用户消息（使用临时ID）
  const tempUserMsg = {
    id: 'temp-' + Date.now(),
    senderType: 'USER',
    content: message,
    createdAt: new Date().toISOString()
  }
  messages.value.push(tempUserMsg)
  scrollToBottom()

  try {
    const res = await sendMessageApi(sessionId.value, message)
    const data = res.data

    // 发送成功后，立即获取完整消息列表替换
    const historyRes = await getConversationHistory(sessionId.value)
    if (historyRes.data) {
      messages.value = historyRes.data.map(msg => ({
        senderType: msg.senderType,
        content: msg.content,
        createdAt: msg.createdAt,
        knowledgeSourceChunkIds: msg.knowledgeSourceChunkIds
      }))
    }

    // 如果是AI回复且后端没返回（正常情况已经通过history获取），兜底处理
    if (!data.needHandoff && data.content && messages.value.length === 1) {
      messages.value.push({
        senderType: 'AI',
        content: data.content,
        createdAt: new Date().toISOString()
      })
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    // 移除临时消息，显示错误
    messages.value = messages.value.filter(m => m.id !== tempUserMsg.id)
    messages.value.push({
      senderType: 'SYSTEM',
      content: '抱歉，消息发送失败，请稍后重试或联系人工客服。',
      createdAt: new Date().toISOString()
    })
  } finally {
    isLoading.value = false
    scrollToBottom()
    // 恢复轮询
    startMessagePolling()
  }
}

// 显示评价对话框
function showRatingDialog() {
  ratingDialogVisible.value = true
}

// 提交评价
async function submitRating() {
  try {
    await closeConversation(sessionId.value, rating.value)
    ratingDialogVisible.value = false

    // 重置会话
    messages.value = []
    rating.value = 5
    ratingComment.value = ''

    // 创建新会话
    const res = await createConversation()
    sessionId.value = res.data.sessionId

    // 显示提示
    ElMessage.success('感谢您的评价，已为您开启新会话')
  } catch (error) {
    console.error('提交评价失败:', error)
    ElMessage.error('提交失败，请重试')
  }
}

// 跳转到管理后台
function goToAdmin() {
  router.push('/admin')
}

// 轮询获取新消息（用于接收人工客服回复）
async function pollMessages() {
  if (!sessionId.value) return
  try {
    const res = await getConversationHistory(sessionId.value)
    const history = res.data
    if (!history || history.length === 0) return

    // 将后端消息格式化为前端格式
    const formattedMessages = history.map(msg => ({
      senderType: msg.senderType,
      content: msg.content,
      createdAt: msg.createdAt,
      knowledgeSourceChunkIds: msg.knowledgeSourceChunkIds
    }))

    // 如果本地消息为空，直接赋值
    if (messages.value.length === 0) {
      messages.value = formattedMessages
      return
    }

    // 如果远程消息更多，追加新消息
    if (formattedMessages.length > messages.value.length) {
      const newMessages = formattedMessages.slice(messages.value.length)
      messages.value.push(...newMessages)
    }
  } catch (error) {
    console.error('获取消息失败:', error)
  }
}

// 启动轮询
function startMessagePolling() {
  if (messagePollTimer) return
  messagePollTimer = setInterval(pollMessages, 3000) // 每3秒轮询一次
}

// 停止轮询
function stopMessagePolling() {
  if (messagePollTimer) {
    clearInterval(messagePollTimer)
    messagePollTimer = null
  }
}

// 初始化
onMounted(async () => {
  try {
    // 创建会话
    const res = await createConversation()
    sessionId.value = res.data.sessionId

    // 加载常见问题
    const faqRes = await getFrequentQuestions(5)
    frequentQuestions.value = faqRes.data

    // 启动消息轮询
    startMessagePolling()
  } catch (error) {
    console.error('初始化失败:', error)
  }
})

// 组件卸载时停止轮询
import { onUnmounted } from 'vue'
onUnmounted(() => {
  stopMessagePolling()
})

// 监听消息变化，自动滚动
watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped lang="scss">
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

// 头部
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      background: #f0f5ff;
      border-radius: 8px;
    }

    .brand {
      h1 {
        font-size: 18px;
        font-weight: 600;
        color: #1a1a1a;
        margin: 0;
      }

      .subtitle {
        font-size: 12px;
        color: #909399;
      }
    }
  }
}

// 主体
.chat-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

// 左侧快捷入口
.quick-actions {
  width: 280px;
  padding: 20px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 12px;
    padding-left: 8px;
    border-left: 3px solid #1a5fb4;
  }

  .action-buttons {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .action-btn {
      justify-content: flex-start;
      height: 44px;
      padding: 0 16px;
      border-radius: 8px;

      .el-icon {
        margin-right: 8px;
      }
    }
  }

  .faq-list {
    .faq-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      margin-bottom: 4px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 13px;
      color: #606266;
      transition: all 0.2s;

      &:hover {
        background: #f5f7fa;
        color: #1a5fb4;
      }

      .el-icon {
        flex-shrink: 0;
        color: #909399;
      }

      span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}

// 聊天区域
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

// 欢迎界面
.welcome-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;

  .welcome-content {
    text-align: center;
    max-width: 600px;

    .welcome-icon {
      margin-bottom: 24px;
    }

    .welcome-title {
      font-size: 28px;
      font-weight: 600;
      color: #1a1a1a;
      margin-bottom: 12px;
    }

    .welcome-desc {
      font-size: 15px;
      color: #606266;
      margin-bottom: 32px;
      line-height: 1.6;
    }

    .intent-tags {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 12px;

      .intent-tag {
        cursor: pointer;
        padding: 8px 16px;
        font-size: 14px;

        &:hover {
          color: #1a5fb4;
          border-color: #1a5fb4;
        }
      }
    }
  }
}

// 消息列表
.message-list {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: #f5f7fa;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;

  &.user-message {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;

      .message-body {
        background: #1a5fb4;
        color: #fff;
        border-radius: 12px 12px 4px 12px;
      }
    }
  }

  &.ai-message {
    .message-content {
      align-items: flex-start;

      .message-body {
        background: #fff;
        border: 1px solid #e4e7ed;
        border-radius: 12px 12px 12px 4px;
      }
    }
  }

  &.system-message {
    .message-content {
      align-items: flex-start;

      .message-body {
        background: #fdf6ec;
        border: 1px solid #f5dab1;
        border-radius: 12px;
        color: #e6a23c;
        font-size: 13px;
      }
    }
  }

  &.human-message {
    .message-content {
      align-items: flex-start;

      .message-body {
        background: #f0f9eb;
        border: 1px solid #b3e19d;
        border-radius: 12px 12px 12px 4px;
        color: #67c23a;
      }
    }
  }

  .message-avatar {
    flex-shrink: 0;
  }

  .message-content {
    display: flex;
    flex-direction: column;
    max-width: 70%;

    .message-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;
      font-size: 13px;

      .sender-name {
        font-weight: 500;
        color: #1a1a1a;
      }

      .message-time {
        color: #909399;
      }
    }

    .message-body {
      padding: 12px 16px;
      font-size: 14px;
      line-height: 1.6;
      color: #1a1a1a;

      :deep(pre) {
        background: #f5f7fa;
        padding: 12px;
        border-radius: 6px;
        overflow-x: auto;
        margin: 8px 0;
      }

      :deep(code) {
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 13px;
      }

      :deep(p) {
        margin: 8px 0;
      }
    }

    .source-images {
      margin-top: 10px;

      .source-images-title {
        font-size: 12px;
        color: #909399;
        margin-bottom: 8px;
      }

      .source-images-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
        gap: 8px;
      }

      .source-image {
        width: 100%;
        max-height: 160px;
        object-fit: cover;
        border-radius: 8px;
        border: 1px solid #e4e7ed;
        background: #fff;
        cursor: pointer;
      }
    }

    .system-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      margin-top: 10px;
    }

    .intent-label {
      margin-top: 6px;
    }
  }
}

// 打字动画
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e4e7ed;

  span {
    width: 8px;
    height: 8px;
    background: #1a5fb4;
    border-radius: 50%;
    animation: typing 1.4s infinite;
    opacity: 0.4;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.4;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

// 输入区域
.input-area {
  padding: 20px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;

  .input-wrapper {
    display: flex;
    gap: 12px;

    .el-textarea {
      flex: 1;
    }

    .input-actions {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .el-button {
        height: 40px;
      }
    }
  }

  .input-tips {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 12px;
    font-size: 12px;
    color: #909399;

    .el-icon {
      font-size: 14px;
    }
  }
}

// 评价对话框
.rating-content {
  text-align: center;
  padding: 20px 0;

  p {
    margin-bottom: 16px;
    color: #606266;
  }

  .el-rate {
    margin-bottom: 16px;
  }
}
</style>
