<template>
  <div class="handoff-page">
    <!-- 页面标题和统计 -->
    <div class="page-header">
      <h2>人工接入</h2>
      <div class="header-actions">
        <el-tag :type="getSocketStatusType()" effect="plain" class="socket-status-tag">
          实时连接 · {{ getSocketStatusLabel() }}
        </el-tag>
        <el-tag type="success" effect="dark" size="large">
          <el-icon><User /></el-icon>
          在线客服: {{ onlineAgents }}人
        </el-tag>
        <el-tag type="warning" effect="dark" size="large">
          <el-icon><Bell /></el-icon>
          待接入: {{ pendingCount }}人
        </el-tag>
      </div>
    </div>

    <!-- 待接入列表 -->
    <el-card class="pending-section" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon><Message /></el-icon>
            待人工接入会话
          </span>
          <el-button type="primary" size="small" @click="refreshPending" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>
      <el-table class="handoff-table" :data="pendingList" v-loading="loading" stripe border fit>
        <el-table-column prop="sessionId" label="会话ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户" min-width="120" />
        <el-table-column prop="intentType" label="意图类型" min-width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="getIntentTagType(row.intentType)">
              {{ getIntentLabel(row.intentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handoffTime" label="转人工时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.handoffTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="waitTime" label="等待时长" min-width="110">
          <template #default="{ row }">
            <el-tag :type="getWaitTimeTagType(row.waitTime)" size="small">
              {{ formatDuration(row.waitTime) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="最后消息" min-width="320" show-overflow-tooltip />
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleAccept(row)">
              <el-icon><Check /></el-icon>
              接入
            </el-button>
            <el-button type="danger" size="small" plain @click="handleClose(row)">
              <el-icon><Close /></el-icon>
              关闭
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="pendingList.length === 0 && !loading" description="暂无待接入会话" />
    </el-card>

    <!-- 我的会话 -->
    <el-card class="my-section" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon><ChatDotRound /></el-icon>
            我的接入会话
          </span>
          <el-button type="primary" size="small" @click="refreshMySessions" :loading="loadingMy">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>
      <el-table class="handoff-table" :data="mySessionList" v-loading="loadingMy" stripe border fit>
        <el-table-column prop="sessionId" label="会话ID" min-width="220" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户" min-width="150" />
        <el-table-column prop="acceptTime" label="接入时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.acceptTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="会话时长" min-width="130">
          <template #default="{ row }">
            {{ formatDuration(row.duration) }}
          </template>
        </el-table-column>
        <el-table-column prop="unreadCount" label="未读消息" min-width="120" align="center">
          <template #default="{ row }">
            <el-badge :value="row.unreadCount" :max="99" v-if="row.unreadCount > 0" />
            <span v-else class="text-muted">0</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleReply(row)">
              <el-icon><ChatLineRound /></el-icon>
              回复
            </el-button>
            <el-button type="success" size="small" @click="handleComplete(row)">
              <el-icon><CircleCheck /></el-icon>
              完成
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="mySessionList.length === 0 && !loadingMy" description="暂无接入会话" />
    </el-card>

    <!-- 回复对话框 -->
    <el-dialog
      v-model="showReplyDialog"
      title="人工客服对话"
      width="700px"
      :close-on-click-modal="false"
      class="chat-dialog"
      @closed="disconnectChatSocket"
    >
      <div class="chat-container">
        <div class="chat-header-info">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="用户">{{ currentSession?.userName }}</el-descriptions-item>
            <el-descriptions-item label="会话ID">{{ currentSession?.sessionId }}</el-descriptions-item>
            <el-descriptions-item label="接入时间">{{ formatTime(currentSession?.acceptTime) }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="chat-history" ref="chatHistoryRef">
          <div
            v-for="msg in currentChatHistory"
            :key="msg.id"
            :class="['message-wrapper', isRightSideMessage(msg.senderType) ? 'right-side' : 'left-side']"
          >
            <div class="message-avatar">
              <el-avatar
                :size="36"
                :icon="msg.senderType === 'USER' ? UserFilled : (msg.senderType === 'HUMAN' ? Service : ChatDotRound)"
                :style="{ background: getAvatarBg(msg.senderType) }"
              />
            </div>
            <div class="message-content-wrapper">
              <div class="message-sender">{{ getSenderName(msg.senderType) }}</div>
              <div class="message-bubble" :class="msg.senderType.toLowerCase()">
                {{ msg.content }}
              </div>
              <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
            </div>
          </div>
        </div>
        <div class="chat-input-area">
          <el-input
            v-model="replyMessage"
            type="textarea"
            :rows="3"
            placeholder="请输入回复内容... (Ctrl+Enter 发送)"
            resize="none"
            @keyup.enter.ctrl="sendReply"
          />
          <div class="input-actions">
            <el-button type="primary" @click="sendReply" :loading="sending" size="large">
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Bell, Message, Refresh, Check, Close,
  ChatDotRound, ChatLineRound, CircleCheck,
  UserFilled, Service, Promotion
} from '@element-plus/icons-vue'
import {
  acceptConversation,
  closeAdminConversation,
  completeConversation,
  getConversationMessages,
  getHandoffConversations,
  getHandoffSummary,
  getMySessions,
  replyConversation,
  createConversationSocket
} from '@/api/chat.js'

const loading = ref(false)
const loadingMy = ref(false)
const pendingList = ref([])
const mySessionList = ref([])
const onlineAgents = ref(5)
const pendingCount = ref(0)
const showReplyDialog = ref(false)
const replyMessage = ref('')
const sending = ref(false)
const currentChatHistory = ref([])
const currentSession = ref(null)
const chatHistoryRef = ref(null)
const socketStatus = ref('disconnected')

let refreshTimer = null
let chatSocket = null
let chatReconnectTimer = null
let chatReconnectAttempts = 0
let shouldReconnectChat = false

const intentTypeMap = {
  'POLICY_CONSULTATION': { label: '政策咨询', type: '' },
  'BUSINESS_PROCESSING': { label: '业务办理', type: 'success' },
  'PROGRESS_QUERY': { label: '进度查询', type: 'info' },
  'TECHNICAL_SUPPORT': { label: '技术支持', type: 'warning' },
  'ACCOUNT_PERMISSION': { label: '账号权限', type: 'danger' },
  'COMPLAINT_SUGGESTION': { label: '投诉建议', type: 'danger' },
  'OTHER': { label: '其他', type: 'info' }
}

const getIntentLabel = (type) => intentTypeMap[type]?.label || type
const getIntentTagType = (type) => intentTypeMap[type]?.type || 'info'

const getWaitTimeTagType = (seconds) => {
  if (seconds > 300) return 'danger'
  if (seconds > 120) return 'warning'
  return 'info'
}

const getAvatarBg = (senderType) => {
  const colors = {
    'USER': '#909399',
    'AI': '#b72a33',
    'HUMAN': '#67c23a',
    'SYSTEM': '#e6a23c'
  }
  return colors[senderType] || '#909399'
}

const getSenderName = (senderType) => {
  const names = {
    'USER': '用户',
    'AI': '智能助手',
    'HUMAN': '人工客服',
    'SYSTEM': '系统'
  }
  return names[senderType] || senderType
}

const isRightSideMessage = (senderType) => {
  return senderType === 'USER' || senderType === 'SYSTEM' || senderType === 'AI'
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatDuration = (seconds) => {
  if (!seconds && seconds !== 0) return '-'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (mins > 0) {
    return `${mins}分${secs}秒`
  }
  return `${secs}秒`
}

const getSocketStatusLabel = () => {
  const labels = {
    connecting: '连接中',
    connected: '已连接',
    reconnecting: '重连中',
    disconnected: '已断开',
    error: '连接异常'
  }
  return labels[socketStatus.value] || '已断开'
}

const getSocketStatusType = () => {
  const types = {
    connecting: 'warning',
    connected: 'success',
    reconnecting: 'warning',
    disconnected: 'info',
    error: 'danger'
  }
  return types[socketStatus.value] || 'info'
}

// API 调用
const fetchPendingList = async () => {
  loading.value = true
  try {
    const [listRes, summaryRes] = await Promise.all([
      getHandoffConversations(),
      getHandoffSummary()
    ])
    pendingList.value = listRes.data.map(item => ({
      ...item,
      waitTime: item.waitTime ?? Math.floor((Date.now() - new Date(item.handoffTime).getTime()) / 1000)
    }))
    pendingCount.value = summaryRes.data.pendingCount
    onlineAgents.value = summaryRes.data.onlineAgents
  } catch (error) {
    console.error('获取待接入列表失败:', error)
    pendingList.value = []
    pendingCount.value = 0
    onlineAgents.value = 0
    ElMessage.error('获取待接入列表失败')
  } finally {
    loading.value = false
  }
}

const fetchMySessionList = async () => {
  loadingMy.value = true
  try {
    const res = await getMySessions()
    mySessionList.value = res.data.map(item => ({
      ...item,
      duration: item.duration ?? Math.floor((Date.now() - new Date(item.acceptTime).getTime()) / 1000)
    }))
  } catch (error) {
    console.error('获取我的会话失败:', error)
    mySessionList.value = []
    ElMessage.error('获取我的接入会话失败')
  } finally {
    loadingMy.value = false
  }
}

const refreshPending = () => {
  fetchPendingList()
}

const refreshMySessions = () => {
  fetchMySessionList()
}

const handleAccept = async (row) => {
  try {
    await acceptConversation(row.sessionId)
    ElMessage.success('已接入会话')
    fetchPendingList()
    fetchMySessionList()
  } catch (error) {
    console.error('接入失败:', error)
    ElMessage.error('接入失败')
  }
}

const handleClose = (row) => {
  ElMessageBox.confirm('确定要关闭该会话吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await closeAdminConversation(row.sessionId)
      ElMessage.success('会话已关闭')
      fetchPendingList()
    } catch (error) {
      console.error('关闭失败:', error)
      ElMessage.error('关闭失败')
    }
  })
}

// 加载聊天记录
const loadChatHistory = async () => {
  if (!currentSession.value) return
  try {
    const res = await getConversationMessages(currentSession.value.sessionId)
    const messages = res.data
    // 只有当消息数量变化时才更新，避免闪烁
    if (messages.length !== currentChatHistory.value.length) {
      currentChatHistory.value = messages
      nextTick(() => {
        scrollToBottom()
      })
    }
  } catch (error) {
    console.error('获取聊天记录失败:', error)
  }
}

const clearChatReconnectTimer = () => {
  if (chatReconnectTimer) {
    clearTimeout(chatReconnectTimer)
    chatReconnectTimer = null
  }
}

const disconnectChatSocket = () => {
  shouldReconnectChat = false
  clearChatReconnectTimer()
  if (chatSocket) {
    chatSocket.close()
    chatSocket = null
  }
  socketStatus.value = 'disconnected'
}

const upsertChatMessage = (message) => {
  if (!message?.id) return
  const index = currentChatHistory.value.findIndex(item => String(item.id) === String(message.id))
  if (index >= 0) {
    currentChatHistory.value.splice(index, 1, { ...currentChatHistory.value[index], ...message })
  } else {
    currentChatHistory.value.push(message)
  }
  nextTick(() => {
    scrollToBottom()
  })
}

const handleRealtimeEvent = (event) => {
  if (!event || event.sessionId !== currentSession.value?.sessionId) return

  if (event.eventType === 'message') {
    const message = event.payload || {}
    if (message.senderType === 'USER' || message.senderType === 'HUMAN' || message.senderType === 'SYSTEM') {
      upsertChatMessage(message)
      fetchMySessionList()
      if (message.senderType === 'USER') {
        fetchPendingList()
      }
    }
    return
  }

  if (event.eventType === 'conversation_update') {
    fetchPendingList()
    fetchMySessionList()
  }
}

const scheduleChatReconnect = (sessionId) => {
  if (!shouldReconnectChat || !sessionId) return

  clearChatReconnectTimer()
  socketStatus.value = 'reconnecting'
  const delay = Math.min(10000, 1000 * (2 ** Math.min(chatReconnectAttempts, 3)))
  chatReconnectAttempts += 1
  chatReconnectTimer = window.setTimeout(() => {
    connectChatSocket(sessionId)
  }, delay)
}

const connectChatSocket = (sessionId) => {
  shouldReconnectChat = true
  clearChatReconnectTimer()
  if (!sessionId) return
  socketStatus.value = 'connecting'

  chatSocket = createConversationSocket(sessionId, {
    onOpen: () => {
      socketStatus.value = 'connected'
      chatReconnectAttempts = 0
    },
    onMessage: handleRealtimeEvent,
    onClose: () => {
      chatSocket = null
      if (shouldReconnectChat && currentSession.value?.sessionId === sessionId) {
        scheduleChatReconnect(sessionId)
        return
      }
      socketStatus.value = 'disconnected'
    },
    onError: error => {
      socketStatus.value = 'error'
      console.warn('人工会话实时连接异常:', error)
    }
  })
}

const handleReply = async (row) => {
  currentSession.value = row
  await loadChatHistory()
  showReplyDialog.value = true
  connectChatSocket(row.sessionId)
}

const sendReply = async () => {
  if (!replyMessage.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  sending.value = true
  try {
    const res = await replyConversation(currentSession.value.sessionId, replyMessage.value)
    upsertChatMessage(res.data)
    replyMessage.value = ''
    ElMessage.success('发送成功')
    nextTick(() => {
      scrollToBottom()
    })
  } catch (error) {
    console.error('发送失败:', error)
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}

const scrollToBottom = () => {
  if (chatHistoryRef.value) {
    chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
  }
}

const handleComplete = (row) => {
  ElMessageBox.confirm('确定要完成该会话吗？完成后用户将无法继续发送消息。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await completeConversation(row.sessionId)
      ElMessage.success('会话已完成')
      fetchMySessionList()
    } catch (error) {
      console.error('操作失败:', error)
      ElMessage.error('操作失败')
    }
  })
}

onMounted(() => {
  fetchPendingList()
  fetchMySessionList()
  // 定时刷新
  refreshTimer = setInterval(() => {
    fetchPendingList()
    fetchMySessionList()
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  disconnectChatSocket()
})
</script>

<style scoped>
.handoff-page {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 0 4px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.socket-status-tag {
  border-color: rgba(183, 42, 51, 0.28);
  color: #b72a33;
}

.header-actions .el-tag {
  font-size: 14px;
  padding: 8px 16px;
}

.header-actions .el-icon {
  margin-right: 4px;
}

.pending-section {
  margin-bottom: 24px;
}

.my-section {
  margin-bottom: 24px;
}

.handoff-table {
  width: 100%;
}

.handoff-table :deep(.el-table__header),
.handoff-table :deep(.el-table__body),
.handoff-table :deep(.el-table__empty-block) {
  width: 100% !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title .el-icon {
  color: #b72a33;
  font-size: 18px;
}

.text-muted {
  color: #909399;
}

.text-danger {
  color: #F56C6C;
  font-weight: bold;
}

/* 对话框样式 */
.chat-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 600px;
}

.chat-header-info {
  padding: 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

.message-wrapper {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.message-wrapper.right-side {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 70%;
}

.message-wrapper.right-side .message-content-wrapper {
  align-items: flex-end;
}

.message-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  word-break: break-word;
  line-height: 1.5;
}

.message-bubble.user {
  background: #b72a33;
  color: white;
  border-bottom-right-radius: 4px;
}

.message-bubble.ai {
  background: white;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}

.message-bubble.human {
  background: #67c23a;
  color: white;
  border-bottom-left-radius: 4px;
}

.message-bubble.system {
  background: #e6a23c;
  color: white;
  text-align: center;
  font-size: 13px;
}

.message-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.chat-input-area {
  padding: 16px;
  background: white;
  border-top: 1px solid #e4e7ed;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

:deep(.el-table .cell) {
  white-space: nowrap;
}
</style>
