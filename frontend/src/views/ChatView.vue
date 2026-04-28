<template>
  <div class="chat-view" :class="{ 'compact-mode': isWidgetMode }" :style="widgetStyle">
    <!-- 头部 -->
    <header class="chat-header">
      <div
        class="header-left"
        :class="{ 'widget-drag-handle': isWidgetMode }"
        :title="isWidgetMode ? '拖动移动窗口' : ''"
        @pointerdown="startWidgetDrag"
      >
        <div class="logo">
          <el-icon size="28" color="#fff"><Service /></el-icon>
        </div>
        <div class="brand">
          <h1>智能客服助手</h1>
          <span class="subtitle">xxxxxx平台</span>
        </div>
      </div>
      <div class="header-right">
        <el-button v-if="canAccessAdmin && !isWidgetMode" type="primary" text @click="goToAdmin">
          <el-icon><Setting /></el-icon>
          管理后台
        </el-button>
        <el-tag v-if="!isWidgetMode" size="small" :type="getSocketStatusTagType()" effect="plain" class="socket-status-tag">
          实时连接 · {{ getSocketStatusLabel() }}
        </el-tag>
        <el-button class="mode-toggle-btn" text @click="toggleWidgetMode">
          <el-icon>
            <component :is="isWidgetMode ? Expand : Fold" />
          </el-icon>
          {{ isWidgetMode ? '展开页面' : '收起为窗口' }}
        </el-button>
        <el-dropdown @command="handleUserCommand">
          <span class="chat-user">
            <el-avatar :size="30" :icon="UserFilled" />
            <span>{{ currentUser?.displayName || '用户' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ currentUser?.username || '未登录' }}</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <button
        v-if="isWidgetMode"
        class="widget-resize-handle"
        type="button"
        aria-label="调整窗口大小"
        title="拖动可调整窗口大小"
        @pointerdown="startWidgetResize"
      >
        <span></span>
      </button>
    </header>

    <!-- 主体内容 -->
    <main class="chat-main">
      <!-- 左侧会话工作台 -->
      <aside v-if="!isWidgetMode" class="chat-sidebar">
        <div class="sidebar-user">
          <el-avatar :size="42" :icon="UserFilled" />
          <div>
            <strong>{{ currentUser?.displayName || '用户' }}</strong>
            <span>{{ getRoleLabel(currentUser?.role) }}</span>
          </div>
        </div>

        <el-button class="new-chat-btn" type="primary" @click="startNewConversation">
          <el-icon><Plus /></el-icon>
          新建会话
        </el-button>

        <div class="section-title">我的会话</div>
        <div class="conversation-list">
          <div
            v-for="conversation in conversationHistory"
            :key="conversation.sessionId"
            class="conversation-item"
            :class="{ active: conversation.sessionId === sessionId }"
            role="button"
            tabindex="0"
            @click="selectConversation(conversation.sessionId)"
            @keydown.enter="selectConversation(conversation.sessionId)"
          >
            <span class="conversation-title">{{ conversation.title }}</span>
            <span class="conversation-meta">
              {{ getConversationStatusLabel(conversation.status) }} · {{ formatDate(conversation.updatedAt || conversation.createdAt) }}
            </span>
            <button
              v-if="conversation.sessionId === sessionId"
              class="clear-conversation"
              type="button"
              aria-label="删除当前会话"
              @click.stop="deleteSelectedConversation(conversation.sessionId)"
            >
              <el-icon><Close /></el-icon>
            </button>
          </div>
          <div v-if="!conversationHistory.length" class="empty-history">
            暂无历史会话
          </div>
        </div>

      </aside>

      <!-- 聊天区域 -->
      <section class="chat-area">
        <div class="quick-service-bar">
          <div class="quick-service-title">快捷服务</div>
          <div class="quick-service-actions">
            <button
              v-for="action in quickActions"
              :key="action.id || action.serviceKey"
              class="quick-service-item"
              @click="applyQuickService(action)"
            >
              <el-icon><component :is="getQuickServiceIcon(action.iconKey)" /></el-icon>
              <span>{{ action.label }}</span>
            </button>
          </div>
        </div>

        <!-- 欢迎界面 -->
        <div v-if="messages.length === 0" class="welcome-panel">
          <div class="welcome-content">
            <div class="welcome-icon">
              <el-icon size="64" color="#b72a33"><ChatDotRound /></el-icon>
            </div>
            <h2 class="welcome-title">您好，我是智能客服助手</h2>
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
              <div
                class="message-body"
                :class="{ 'scope-guide-body': isScopeGuideMessage(msg) }"
                v-html="renderMarkdown(msg.content)"
              ></div>
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
                  <el-icon v-if="action.icon"><component :is="action.icon" /></el-icon>
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
          <div v-if="isLoading && !hasStreamingMessage()" class="message-item ai-message">
            <div class="message-avatar">
              <el-avatar :size="40" :icon="Service" style="background: #b72a33" />
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
              :rows="3"
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
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import {
  Service, Setting, UserFilled, ChatDotRound,
  Promotion, CircleCheck, InfoFilled,
  Document, OfficeBuilding, Search, Tools, User, MessageBox,
  ArrowDown, Plus, Close, Fold, Expand
} from '@element-plus/icons-vue'
import {
  createConversation, deleteConversation, streamMessage as streamMessageApi, getConversationHistory,
  closeConversation, clearAuth, getEnabledQuickServices, getMyConversations, getStoredUser, hasAnyRole,
  createConversationSocket
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
const conversationHistory = ref([])
const currentUser = ref(getStoredUser())
const ratingDialogVisible = ref(false)
const rating = ref(5)
const ratingComment = ref('')
const socketStatus = ref('disconnected')
const isWidgetMode = ref(localStorage.getItem('gov_assistant_chat_widget_mode') === 'compact')
const widgetRect = ref(loadWidgetRect())

let conversationSocket = null
let conversationReconnectTimer = null
let conversationReconnectAttempts = 0
let shouldReconnectConversation = false
let widgetDragState = null
let widgetResizeState = null

const WIDGET_LAYOUT_KEY = 'gov_assistant_chat_widget_layout'
const WIDGET_MIN_WIDTH = 320
const WIDGET_MIN_HEIGHT = 420
const WIDGET_MAX_WIDTH = 640
const WIDGET_MAX_HEIGHT = 860

const quickActions = ref([])
const quickServiceIconMap = { Document, OfficeBuilding, Search, Tools, User, MessageBox }

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

function formatDate(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function getRoleLabel(role) {
  const labels = {
    ADMIN: '系统管理员',
    AGENT: '人工客服',
    VIEWER: '只读账号',
    USER: '平台用户'
  }
  return labels[role] || '平台用户'
}

function getConversationStatusLabel(status) {
  const labels = {
    ACTIVE: '进行中',
    HANDOFF: '人工接入',
    CLOSED: '已结束',
    TIMEOUT: '已超时'
  }
  return labels[status] || '会话'
}

const canAccessAdmin = hasAnyRole(['ADMIN', 'AGENT', 'VIEWER'])

const widgetStyle = computed(() => {
  if (!isWidgetMode.value) {
    return {}
  }

  return {
    '--widget-left': `${widgetRect.value.left}px`,
    '--widget-top': `${widgetRect.value.top}px`,
    '--widget-width': `${widgetRect.value.width}px`,
    '--widget-height': `${widgetRect.value.height}px`
  }
})

function loadWidgetRect() {
  const fallback = getDefaultWidgetRect()
  try {
    const raw = localStorage.getItem(WIDGET_LAYOUT_KEY)
    if (!raw) return fallback
    const parsed = JSON.parse(raw)
    return clampWidgetRect({
      left: Number(parsed.left),
      top: Number(parsed.top),
      width: Number(parsed.width),
      height: Number(parsed.height)
    })
  } catch (error) {
    return fallback
  }
}

function getDefaultWidgetRect() {
  const width = 420
  const height = 680
  const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1440
  const viewportHeight = typeof window !== 'undefined' ? window.innerHeight : 900
  return clampWidgetRect({
    left: Math.max(16, viewportWidth - width - 16),
    top: Math.max(16, viewportHeight - height - 16),
    width,
    height
  })
}

function clampWidgetRect(rect) {
  const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1440
  const viewportHeight = typeof window !== 'undefined' ? window.innerHeight : 900
  const width = Math.min(Math.max(Number(rect.width) || 420, WIDGET_MIN_WIDTH), Math.min(WIDGET_MAX_WIDTH, viewportWidth - 16))
  const height = Math.min(Math.max(Number(rect.height) || 680, WIDGET_MIN_HEIGHT), Math.min(WIDGET_MAX_HEIGHT, viewportHeight - 16))
  const left = Math.min(Math.max(Number(rect.left) || viewportWidth - width - 16, 16), Math.max(16, viewportWidth - width - 16))
  const top = Math.min(Math.max(Number(rect.top) || viewportHeight - height - 16, 16), Math.max(16, viewportHeight - height - 16))
  return { left, top, width, height }
}

function persistWidgetRect() {
  localStorage.setItem(WIDGET_LAYOUT_KEY, JSON.stringify(widgetRect.value))
}

function toggleWidgetMode() {
  isWidgetMode.value = !isWidgetMode.value
  localStorage.setItem('gov_assistant_chat_widget_mode', isWidgetMode.value ? 'compact' : 'full')
  if (isWidgetMode.value) {
    widgetRect.value = clampWidgetRect(widgetRect.value)
    persistWidgetRect()
  }
  nextTick(() => {
    scrollToBottom()
    chatInputRef.value?.focus?.()
  })
}

function startWidgetDrag(event) {
  if (!isWidgetMode.value || event.button !== 0) return
  event.preventDefault()
  widgetDragState = {
    startX: event.clientX,
    startY: event.clientY,
    startLeft: widgetRect.value.left,
    startTop: widgetRect.value.top
  }
}

function startWidgetResize(event) {
  if (!isWidgetMode.value || event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()
  widgetResizeState = {
    startX: event.clientX,
    startY: event.clientY,
    startWidth: widgetRect.value.width,
    startHeight: widgetRect.value.height
  }
}

function handleGlobalPointerMove(event) {
  if (!isWidgetMode.value) return

  if (widgetDragState) {
    widgetRect.value = clampWidgetRect({
      ...widgetRect.value,
      left: widgetDragState.startLeft + (event.clientX - widgetDragState.startX),
      top: widgetDragState.startTop + (event.clientY - widgetDragState.startY)
    })
  }

  if (widgetResizeState) {
    widgetRect.value = clampWidgetRect({
      ...widgetRect.value,
      width: widgetResizeState.startWidth + (event.clientX - widgetResizeState.startX),
      height: widgetResizeState.startHeight + (event.clientY - widgetResizeState.startY)
    })
  }
}

function handleGlobalPointerUp() {
  if (widgetDragState || widgetResizeState) {
    widgetDragState = null
    widgetResizeState = null
    persistWidgetRect()
  }
}

function handleWindowResize() {
  if (!isWidgetMode.value) return
  widgetRect.value = clampWidgetRect(widgetRect.value)
  persistWidgetRect()
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

function hasStreamingMessage() {
  return messages.value.some(msg => String(msg.id || '').startsWith('stream-'))
}

function getChunkImageUrl(chunkId) {
  return `/api/chat/document-chunks/${chunkId}/image`
}

function getSystemActions(msg) {
  if (msg.senderType !== 'SYSTEM' || !msg.content) {
    return []
  }

  if (isScopeGuideMessage(msg)) {
    return [
      { key: 'policy', label: '咨询政策', type: 'primary', action: 'send', text: '我想了解相关监管政策', icon: Document },
      { key: 'business', label: '办理业务', type: 'success', action: 'send', text: '如何办理国有资本产权登记？', icon: OfficeBuilding },
      { key: 'handoff', label: '转人工', type: 'warning', action: 'handoff', icon: Service }
    ]
  }

  if (msg.content.includes('转人工客服')) {
    return [
      { key: 'handoff', label: '转人工', type: 'warning', action: 'handoff', icon: Service }
    ]
  }

  return []
}

function isScopeGuideMessage(msg) {
  return msg?.senderType === 'SYSTEM'
    && typeof msg.content === 'string'
    && msg.content.includes('我主要协助处理平台相关事项')
}

function handleSystemAction(action) {
  if (!action) return

  if (action.action === 'handoff') {
    quickSend('转人工')
    return
  }

  if (action.action === 'send' && action.text) {
    quickSend(action.text)
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
    'SYSTEM': '智能助手'
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
    'AI': '#b72a33',
    'HUMAN': '#67c23a',
    'SYSTEM': '#b72a33'
  }
  return colors[senderType] || '#b72a33'
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function getSocketStatusLabel() {
  const labels = {
    connecting: '连接中',
    connected: '已连接',
    reconnecting: '重连中',
    disconnected: '已断开',
    error: '连接异常'
  }
  return labels[socketStatus.value] || '已断开'
}

function getSocketStatusTagType() {
  const types = {
    connecting: 'warning',
    connected: 'success',
    reconnecting: 'warning',
    disconnected: 'info',
    error: 'danger'
  }
  return types[socketStatus.value] || 'info'
}

function clearConversationReconnectTimer() {
  if (conversationReconnectTimer) {
    clearTimeout(conversationReconnectTimer)
    conversationReconnectTimer = null
  }
}

function disconnectConversationSocket() {
  shouldReconnectConversation = false
  clearConversationReconnectTimer()
  if (conversationSocket) {
    conversationSocket.close()
    conversationSocket = null
  }
  socketStatus.value = 'disconnected'
}

function upsertRealtimeMessage(message) {
  if (!message?.id) return
  const index = messages.value.findIndex(item => String(item.id) === String(message.id))
  if (index >= 0) {
    messages.value.splice(index, 1, { ...messages.value[index], ...message })
  } else {
    messages.value.push(message)
  }
  scrollToBottom()
}

async function refreshConversationListIfNeeded() {
  try {
    await loadConversationHistoryList()
  } catch (error) {
    console.error('刷新会话列表失败:', error)
  }
}

function scheduleConversationReconnect(targetSessionId) {
  if (!shouldReconnectConversation || !targetSessionId) return

  clearConversationReconnectTimer()
  socketStatus.value = 'reconnecting'
  const delay = Math.min(10000, 1000 * (2 ** Math.min(conversationReconnectAttempts, 3)))
  conversationReconnectAttempts += 1
  conversationReconnectTimer = window.setTimeout(() => {
    connectConversationSocket(targetSessionId)
  }, delay)
}

function handleRealtimeEvent(event) {
  if (!event || event.sessionId !== sessionId.value) return

  if (event.eventType === 'message') {
    const message = event.payload || {}
    // 当前页面已经通过 SSE 处理用户输入和 AI 回复，只同步人工客服和系统消息，避免重复。
    if (message.senderType === 'HUMAN' || message.senderType === 'SYSTEM') {
      upsertRealtimeMessage(message)
      refreshConversationListIfNeeded()
    }
    return
  }

  if (event.eventType === 'conversation_update') {
    refreshConversationListIfNeeded()
  }
}

function connectConversationSocket(targetSessionId) {
  shouldReconnectConversation = true
  clearConversationReconnectTimer()
  if (!targetSessionId) return
  socketStatus.value = 'connecting'

  conversationSocket = createConversationSocket(targetSessionId, {
    onOpen: () => {
      socketStatus.value = 'connected'
      conversationReconnectAttempts = 0
    },
    onMessage: handleRealtimeEvent,
    onClose: () => {
      conversationSocket = null
      if (shouldReconnectConversation && sessionId.value === targetSessionId) {
        scheduleConversationReconnect(targetSessionId)
        return
      }
      socketStatus.value = 'disconnected'
    },
    onError: error => {
      socketStatus.value = 'error'
      console.warn('会话实时连接异常:', error)
    }
  })
}

// 快捷发送
function quickSend(text) {
  inputMessage.value = text
  sendMessage()
}

function getQuickServiceIcon(iconKey) {
  return quickServiceIconMap[iconKey] || Document
}

async function loadQuickServices() {
  const res = await getEnabledQuickServices()
  quickActions.value = res.data || []
}

function applyQuickService(action) {
  const promptText = action.promptText || action.label || ''
  if (!promptText.trim()) return
  quickSend(promptText)
}

async function loadConversationHistoryList() {
  const res = await getMyConversations()
  conversationHistory.value = res.data || []
}

async function startNewConversation() {
  disconnectConversationSocket()
  const res = await createConversation()
  sessionId.value = res.data.sessionId
  messages.value = []
  await loadConversationHistoryList()
  connectConversationSocket(sessionId.value)
  nextTick(() => {
    chatInputRef.value?.focus?.()
  })
}

async function selectConversation(nextSessionId) {
  if (!nextSessionId || nextSessionId === sessionId.value) return

  disconnectConversationSocket()
  sessionId.value = nextSessionId
  const historyRes = await getConversationHistory(sessionId.value)
  messages.value = formatBackendMessages(historyRes.data || [])
  scrollToBottom()
  connectConversationSocket(sessionId.value)
}

async function deleteSelectedConversation(targetSessionId) {
  if (!targetSessionId) return

  try {
    await ElMessageBox.confirm('删除后将无法恢复该会话记录，确定删除吗？', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }

  disconnectConversationSocket()
  try {
    await deleteConversation(targetSessionId)
    await loadConversationHistoryList()
    if (targetSessionId === sessionId.value) {
      sessionId.value = ''
      messages.value = []
      inputMessage.value = ''
    }
    ElMessage.success('会话已删除')
  } catch (error) {
    console.error('删除会话失败:', error)
    ElMessage.error(error.response?.data?.message || '删除失败，请稍后重试')
  } finally {
    if (sessionId.value) {
      connectConversationSocket(sessionId.value)
    }
    nextTick(() => {
      chatInputRef.value?.focus?.()
    })
  }
}

function formatBackendMessages(history) {
  return history.map(msg => ({
    senderType: msg.senderType,
    content: msg.content,
    createdAt: msg.createdAt,
    knowledgeSourceChunkIds: msg.knowledgeSourceChunkIds
  }))
}

// 发送消息
async function sendMessage() {
  const message = inputMessage.value.trim()
  if (!message || isLoading.value) return

  if (!sessionId.value) {
    const conversationRes = await createConversation()
    sessionId.value = conversationRes.data.sessionId
    await loadConversationHistoryList()
    connectConversationSocket(sessionId.value)
  }

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
    const streamingAiMsg = {
      id: 'stream-' + Date.now(),
      senderType: 'AI',
      content: '',
      createdAt: new Date().toISOString()
    }
    messages.value.push(streamingAiMsg)
    const streamingIndex = messages.value.length - 1
    scrollToBottom()

    let doneData = null
    await streamMessageApi(sessionId.value, message, {
      onDelta: delta => {
        const current = messages.value[streamingIndex]
        if (current && current.id === streamingAiMsg.id) {
          current.content = `${current.content || ''}${delta}`
        }
        scrollToBottom()
      },
      onDone: data => {
        doneData = data
      },
      onError: error => {
        throw new Error(error?.message || '流式响应失败')
      }
    })

    // 流式输出结束后，拉取完整消息列表，校准意图、知识来源、图片片段等后端字段
    const historyRes = await getConversationHistory(sessionId.value)
    if (historyRes.data) {
      messages.value = formatBackendMessages(historyRes.data)
    }
    await loadConversationHistoryList()

    // 后端极端情况下未及时返回历史时，用 done 数据兜底展示。
    if (doneData?.content && !messages.value.some(msg => msg.senderType === 'AI' && msg.content === doneData.content)) {
      const current = messages.value[streamingIndex]
      if (current && current.id === streamingAiMsg.id) {
        current.content = doneData.content
      }
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
    await startNewConversation()

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

function handleUserCommand(command) {
  if (command === 'logout') {
    clearAuth()
    router.replace('/login')
  }
}

// 初始化
onMounted(async () => {
  window.addEventListener('pointermove', handleGlobalPointerMove)
  window.addEventListener('pointerup', handleGlobalPointerUp)
  window.addEventListener('pointercancel', handleGlobalPointerUp)
  window.addEventListener('resize', handleWindowResize)
  try {
    currentUser.value = getStoredUser()
    await loadQuickServices()
    await loadConversationHistoryList()
    if (conversationHistory.value.length > 0) {
      await selectConversation(conversationHistory.value[0].sessionId)
    } else {
      await startNewConversation()
    }
  } catch (error) {
    console.error('初始化失败:', error)
  }
})

onUnmounted(() => {
  window.removeEventListener('pointermove', handleGlobalPointerMove)
  window.removeEventListener('pointerup', handleGlobalPointerUp)
  window.removeEventListener('pointercancel', handleGlobalPointerUp)
  window.removeEventListener('resize', handleWindowResize)
  disconnectConversationSocket()
})

// 监听消息变化，自动滚动
watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped lang="scss">
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #eef1f5;

  &.compact-mode {
    position: fixed;
    left: var(--widget-left, auto);
    top: var(--widget-top, auto);
    width: var(--widget-width, 420px);
    height: var(--widget-height, 680px);
    right: auto;
    bottom: auto;
    background: #fff;
    border-radius: 16px;
    overflow: hidden;
    z-index: 1200;
    box-shadow: 0 16px 40px rgba(22, 34, 66, 0.18);

    .chat-header {
      position: relative;
      height: 52px;
      padding: 0 14px 0 16px;

      .header-left {
        gap: 10px;
        cursor: move;

        .logo {
          width: 32px;
          height: 32px;
        }

        .brand {
          h1 {
            font-size: 15px;
          }

          .subtitle {
            display: none;
          }
        }
      }

      .header-right {
        gap: 8px;

        .mode-toggle-btn {
          color: #fff;
          font-weight: 500;
        }

        .chat-user {
          padding: 4px 6px;
          font-size: 13px;
        }
      }

      .widget-resize-handle {
        position: absolute;
        right: 3px;
        bottom: 3px;
        width: 18px;
        height: 18px;
        padding: 0;
        border: none;
        background: transparent;
        cursor: nwse-resize;
        opacity: 0.8;
        touch-action: none;

        span {
          display: block;
          width: 100%;
          height: 100%;
          background:
            linear-gradient(135deg, transparent 0 48%, rgba(255, 255, 255, 0.95) 48% 50%, transparent 50% 100%),
            linear-gradient(135deg, transparent 0 66%, rgba(255, 255, 255, 0.95) 66% 68%, transparent 68% 100%),
            linear-gradient(135deg, transparent 0 84%, rgba(255, 255, 255, 0.95) 84% 86%, transparent 86% 100%);
        }
      }
    }

    .chat-main {
      min-height: 0;
    }

    .chat-area {
      min-width: 0;
    }

    .quick-service-bar {
      min-height: 50px;
      padding: 8px 14px;
      gap: 12px;

      .quick-service-title {
        font-size: 13px;
      }

      .quick-service-item {
        height: 32px;
        padding: 0 11px;
      }
    }

    .welcome-panel {
      padding: 24px 18px;

      .welcome-content {
        max-width: 100%;

        .welcome-title {
          font-size: 22px;
        }

        .welcome-desc {
          font-size: 13px;
          margin-bottom: 20px;
        }
      }
    }

    .message-list {
      padding: 16px 14px;
    }

    .message-item {
      margin-bottom: 18px;
    }

    .input-area {
      padding: 14px;

      .input-wrapper {
        grid-template-columns: minmax(0, 1fr) 108px;
        gap: 10px;

        .el-textarea {
          min-height: 72px;

          :deep(.el-textarea__inner) {
            min-height: 72px !important;
          }
        }

        .input-actions .el-button {
          height: 36px;
        }
      }
    }
  }
}

.widget-drag-handle {
  user-select: none;
  touch-action: none;
}

// 头部
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 58px;
  background: #b72a33;
  border-bottom: 1px solid #941f28;
  box-shadow: 0 2px 8px rgba(123, 30, 38, 0.22);

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      background: rgba(255, 255, 255, 0.16);
      border-radius: 6px;
    }

    .brand {
      h1 {
        font-size: 18px;
        font-weight: 600;
        color: #fff;
        margin: 0;
      }

      .subtitle {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.78);
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 14px;

    .socket-status-tag {
      border-color: rgba(255, 255, 255, 0.38);
      background: rgba(255, 255, 255, 0.12);
      color: #fff;
    }

    .chat-user {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      color: #fff;
      font-size: 14px;
      padding: 6px 8px;
      border-radius: 4px;

      &:hover {
        background: rgba(255, 255, 255, 0.12);
      }
    }

    .mode-toggle-btn {
      color: #fff;
      padding: 6px 10px;

      &:hover {
        background: rgba(255, 255, 255, 0.12);
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

// 左侧会话工作台
.chat-sidebar {
  width: 280px;
  padding: 18px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .sidebar-user {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    background: #f8fafc;
    margin-bottom: 14px;

    strong {
      display: block;
      color: #1f2d3d;
      font-size: 14px;
      margin-bottom: 2px;
    }

    span {
      color: #7a8798;
      font-size: 12px;
    }
  }

  .new-chat-btn {
    width: 100%;
    height: 40px;
    border-radius: 6px;
    margin-bottom: 18px;
  }

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 12px;
    padding-left: 8px;
    border-left: 3px solid #b72a33;
  }

  .conversation-list {
    display: flex;
    flex-direction: column;
    flex: 1;
    gap: 8px;
    min-height: 0;
    overflow-y: auto;
    margin-bottom: 0;
    padding-right: 2px;

    .conversation-item {
      position: relative;
      width: 100%;
      text-align: left;
      min-height: 62px;
      border: 1px solid #dce5f0;
      background: #fff;
      padding: 11px 42px 11px 14px;
      border-radius: 8px;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
      transition: all 0.18s ease;

      &:hover {
        background: #fff7f8;
        border-color: #b8d7ff;
      }

      &.active {
        background: #fbf0f1;
        border-color: #e6a6ab;
      }

      .conversation-title {
        display: block;
        color: #1f2d3d;
        font-size: 14px;
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .conversation-meta {
        display: block;
        margin-top: 6px;
        color: #8a94a6;
        font-size: 12px;
      }

      .clear-conversation {
        position: absolute;
        top: 9px;
        right: 9px;
        width: 24px;
        height: 24px;
        border: none;
        border-radius: 50%;
        background: rgba(26, 95, 180, 0.1);
        color: #b72a33;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;

        &:hover {
          background: rgba(26, 95, 180, 0.18);
        }
      }
    }

    .empty-history {
      color: #98a2b3;
      font-size: 13px;
      padding: 12px;
      background: #f8fafc;
      border-radius: 8px;
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

.quick-service-bar {
  min-height: 58px;
  padding: 10px 24px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 18px;

  .quick-service-title {
    flex-shrink: 0;
    color: #606266;
    font-size: 14px;
    font-weight: 600;
    padding-left: 8px;
    border-left: 3px solid #b72a33;
  }

  .quick-service-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    overflow-x: auto;
  }

  .quick-service-item {
    height: 36px;
    padding: 0 13px;
    border: 1px solid #d7e3f1;
    border-radius: 6px;
    background: #f8fbff;
    color: #46566a;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
    transition: all 0.18s ease;

    .el-icon {
      color: #b72a33;
      font-size: 16px;
    }

    &:hover {
      border-color: #e6a6ab;
      background: #fbf0f1;
      color: #b72a33;
    }
  }
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
          color: #b72a33;
          border-color: #b72a33;
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
        background: #b72a33;
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
        background: #fffdfd;
        border: 1px solid #f0d8dc;
        border-left: 4px solid #b72a33;
        border-radius: 10px;
        color: #324055;
        font-size: 14px;
        box-shadow: 0 1px 2px rgba(183, 42, 51, 0.04);

        &.scope-guide-body {
          max-width: 760px;
          padding: 14px 18px 16px;

          &::before {
            content: '系统提示';
            display: inline-flex;
            align-items: center;
            margin-bottom: 10px;
            padding: 2px 8px;
            border-radius: 999px;
            background: #fff2f3;
            color: #b72a33;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0;
          }

          :deep(p) {
            margin: 6px 0;
          }
        }
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
      display: inline-flex;
      flex-direction: column;
      align-items: flex-start;
      width: fit-content;
      max-width: 100%;
      padding: 12px 16px;
      font-size: 14px;
      line-height: 1.6;
      color: #1a1a1a;
      word-break: break-word;

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

      .el-button {
        border-radius: 6px;
      }

      .el-icon {
        margin-right: 4px;
      }
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
    background: #b72a33;
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
    display: grid;
    grid-template-columns: minmax(0, 1fr) 132px;
    gap: 14px;
    align-items: stretch;

    .el-textarea {
      min-height: 88px;

      :deep(.el-textarea__inner) {
        min-height: 88px !important;
        padding: 12px 14px;
        line-height: 1.6;
      }
    }

    .input-actions {
      display: flex;
      flex-direction: column;
      gap: 10px;

      .el-button {
        width: 100%;
        height: 39px;
        margin-left: 0;
        border-radius: 4px;
        justify-content: center;
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
