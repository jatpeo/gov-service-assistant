import axios from 'axios'

export const AUTH_TOKEN_KEY = 'gov_assistant_auth_token'
export const AUTH_USER_KEY = 'gov_assistant_auth_user'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      clearAuth()
      if (window.location.pathname.startsWith('/admin')) {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
    }
    return Promise.reject(error)
  }
)

export function saveAuth(token, user) {
  localStorage.setItem(AUTH_TOKEN_KEY, token)
  localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(AUTH_USER_KEY)
}

export function getStoredUser() {
  const rawUser = localStorage.getItem(AUTH_USER_KEY)
  if (!rawUser) return null

  try {
    return JSON.parse(rawUser)
  } catch (error) {
    clearAuth()
    return null
  }
}

export function isAuthenticated() {
  return Boolean(localStorage.getItem(AUTH_TOKEN_KEY))
}

export function hasAnyRole(roles = []) {
  const user = getStoredUser()
  if (!user || !user.role) return false
  return roles.includes(user.role)
}

export function login(username, password) {
  return api.post('/auth/login', { username, password })
}

export function getCurrentUser() {
  return api.get('/auth/me')
}

/**
 * 创建新会话
 */
export function createConversation(userId, userName) {
  return api.post('/chat/conversation')
}

export function getMyConversations() {
  return api.get('/chat/conversations')
}

export function getEnabledQuickServices() {
  return api.get('/chat/quick-services')
}

export function deleteConversation(sessionId) {
  return api.delete(`/chat/conversations/${sessionId}`)
}

/**
 * 发送消息
 */
export function sendMessage(sessionId, message) {
  return api.post('/chat/message', null, {
    params: { sessionId, message }
  })
}

export async function streamMessage(sessionId, message, { onDelta, onDone, onError } = {}) {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  const params = new URLSearchParams({ sessionId, message })
  const response = await fetch(`/api/chat/message/stream?${params.toString()}`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  })

  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败：${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatchEvent = eventText => {
    const lines = eventText.split('\n')
    let eventName = 'message'
    const dataLines = []

    lines.forEach(line => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    })

    const data = dataLines.join('\n')
    if (eventName === 'delta') {
      onDelta?.(data)
      return
    }

    if (eventName === 'done') {
      try {
        onDone?.(JSON.parse(data))
      } catch (error) {
        onDone?.(data)
      }
      return
    }

    if (eventName === 'error') {
      try {
        onError?.(JSON.parse(data))
      } catch (error) {
        onError?.({ message: data })
      }
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    const events = buffer.split('\n\n')
    buffer = events.pop() || ''
    events.filter(Boolean).forEach(dispatchEvent)
  }

  const remaining = buffer.replace(/\r\n/g, '\n')
  if (remaining.trim()) {
    dispatchEvent(remaining)
  }
}

/**
 * 获取会话历史
 */
export function getConversationHistory(sessionId) {
  return api.get(`/chat/history/${sessionId}`)
}

/**
 * 关闭会话
 */
export function closeConversation(sessionId, satisfactionScore) {
  return api.post(`/chat/close/${sessionId}`, null, {
    params: { satisfactionScore }
  })
}

/**
 * 请求转人工
 */
export function requestHandoff(sessionId) {
  return api.post(`/chat/handoff/${sessionId}`)
}

/**
 * 连接会话实时 WebSocket。
 *
 * 调用链：
 * ChatView/Handoff 打开会话 -> createConversationSocket()
 * -> /ws/conversation?sessionId=...&token=...
 * -> 服务端推送 message / conversation_update 事件。
 */
export function createConversationSocket(sessionId, { onMessage, onOpen, onClose, onError } = {}) {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  // 统一走当前站点的相对路径，开发环境交给 Vite 代理，生产环境交给同源部署或反向代理。
  const url = new URL('/ws/conversation', window.location.origin)
  url.searchParams.set('sessionId', sessionId)
  if (token) {
    url.searchParams.set('token', token)
  }

  const socket = new WebSocket(url.toString())
  socket.onopen = event => onOpen?.(event)
  socket.onclose = event => onClose?.(event)
  socket.onerror = event => onError?.(event)
  socket.onmessage = event => {
    try {
      const data = JSON.parse(event.data)
      onMessage?.(data)
    } catch (error) {
      onMessage?.({ eventType: 'message', sessionId, payload: { raw: event.data } })
    }
  }
  return socket
}

// ==================== 管理后台 API ====================

/**
 * 获取会话列表
 */
export function getConversations(page = 0, size = 20) {
  return api.get('/admin/conversations', {
    params: { page, size }
  })
}

/**
 * 获取会话详情
 */
export function getConversationDetail(sessionId) {
  return api.get(`/admin/conversations/${sessionId}`)
}

/**
 * 获取会话消息
 */
export function getConversationMessages(sessionId) {
  return api.get(`/admin/conversations/${sessionId}/messages`)
}

/**
 * 获取转人工会话
 */
export function getHandoffConversations() {
  return api.get('/admin/handoff-conversations')
}

export function getHandoffSummary() {
  return api.get('/admin/handoff-summary')
}

export function getMySessions() {
  return api.get('/admin/my-sessions')
}

export function acceptConversation(sessionId) {
  return api.post(`/admin/conversations/${sessionId}/accept`)
}

export function closeAdminConversation(sessionId) {
  return api.post(`/admin/conversations/${sessionId}/close`)
}

export function completeConversation(sessionId) {
  return api.post(`/admin/conversations/${sessionId}/complete`)
}

export function replyConversation(sessionId, content) {
  return api.post(`/admin/conversations/${sessionId}/reply`, { content })
}

export function getUsers() {
  return api.get('/admin/users')
}

export function createUser(user) {
  return api.post('/admin/users', user)
}

export function updateUserStatus(id, status) {
  return api.patch(`/admin/users/${id}/status`, { status })
}

export function resetUserPassword(id, password) {
  return api.patch(`/admin/users/${id}/password`, { password })
}

export function getQuickServices() {
  return api.get('/admin/quick-services')
}

export function createQuickService(service) {
  return api.post('/admin/quick-services', service)
}

export function updateQuickService(id, service) {
  return api.put(`/admin/quick-services/${id}`, service)
}

export function updateQuickServiceStatus(id, status) {
  return api.patch(`/admin/quick-services/${id}/status`, { status })
}

/**
 * 获取知识库列表
 */
export function getKnowledgeItems(page = 0, size = 20) {
  return api.get('/admin/knowledge', {
    params: { page, size }
  })
}

/**
 * 获取知识库列表（支持搜索/状态筛选）
 */
export function queryKnowledgeItems({ page = 0, size = 20, keyword, status } = {}) {
  return api.get('/admin/knowledge', {
    params: { page, size, keyword, status }
  })
}

/**
 * 搜索知识库
 */
export function searchKnowledge(keyword) {
  return api.get('/admin/knowledge/search', {
    params: { keyword }
  })
}

/**
 * 添加知识条目
 */
export function addKnowledgeItem(item) {
  return api.post('/admin/knowledge', item)
}

/**
 * 更新知识条目
 */
export function updateKnowledgeItem(id, item) {
  return api.put(`/admin/knowledge/${id}`, item)
}

/**
 * 删除知识条目
 */
export function deleteKnowledgeItem(id) {
  return api.delete(`/admin/knowledge/${id}`)
}

/**
 * 更新知识条目状态
 */
export function updateKnowledgeStatus(id, status) {
  return api.patch(`/admin/knowledge/${id}/status`, { status })
}

/**
 * 批量更新知识条目状态
 */
export function batchUpdateKnowledgeStatus(ids, status) {
  return api.patch('/admin/knowledge/batch-status', { ids, status })
}

/**
 * 导出知识库
 */
export function exportKnowledgeItems() {
  return api.get('/admin/knowledge/export', {
    responseType: 'blob'
  })
}

/**
 * 导入知识库
 */
export function importKnowledgeItems(file) {
  const formData = new FormData()
  formData.append('file', file)
  return api.post('/admin/knowledge/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取高频问题
 */
export function getFrequentQuestions(limit = 10) {
  return api.get('/admin/knowledge/frequent', {
    params: { limit }
  })
}

/**
 * 获取文档知识列表
 */
export function getDocumentKnowledgeList(page = 0, size = 20) {
  return api.get('/admin/documents', {
    params: { page, size }
  })
}

/**
 * 获取文档详情
 */
export function getDocumentKnowledgeDetail(id) {
  return api.get(`/admin/documents/${id}`)
}

/**
 * 获取文档预览
 */
export function getDocumentKnowledgePreview(id) {
  return api.get(`/admin/documents/${id}/preview`)
}

/**
 * 获取文档片段
 */
export function getDocumentKnowledgeChunks(id) {
  return api.get(`/admin/documents/${id}/chunks`)
}

/**
 * 获取文档图片预览地址
 */
export function getDocumentKnowledgeChunkImageUrl(chunkId) {
  return `/api/admin/documents/chunks/${chunkId}/image`
}

/**
 * 上传文档知识
 */
export function uploadDocumentKnowledge(file, documentType, uploadedBy) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('documentType', documentType)
  if (uploadedBy) {
    formData.append('uploadedBy', uploadedBy)
  }
  return api.post('/admin/documents/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 重新解析文档
 */
export function retryDocumentKnowledge(id) {
  return api.post(`/admin/documents/${id}/retry`)
}

/**
 * 发布文档片段
 */
export function publishDocumentKnowledge(id) {
  return api.post(`/admin/documents/${id}/publish`)
}

/**
 * 拒绝文档
 */
export function rejectDocumentKnowledge(id, reason) {
  return api.post(`/admin/documents/${id}/reject`, null, {
    params: { reason }
  })
}

/**
 * 获取 RAG 索引状态
 */
export function getRagStatus() {
  return api.get('/admin/rag/status')
}

/**
 * 手动重建 RAG 索引
 */
export function rebuildRagIndex() {
  return api.post('/admin/rag/rebuild')
}

// ==================== 统计 API ====================

/**
 * 获取概览统计
 */
export function getOverviewStatistics() {
  return api.get('/admin/statistics/overview')
}

/**
 * 获取意图分类统计
 */
export function getIntentStatistics(startDate, endDate) {
  return api.get('/admin/statistics/intent', {
    params: { startDate, endDate }
  })
}

/**
 * 获取满意度统计
 */
export function getSatisfactionStatistics(startDate, endDate) {
  return api.get('/admin/statistics/satisfaction', {
    params: { startDate, endDate }
  })
}

/**
 * 获取每日统计
 */
export function getDailyStatistics(startDate, endDate) {
  return api.get('/admin/statistics/daily', {
    params: { startDate, endDate }
  })
}

/**
 * 获取知识库命中率
 */
export function getKnowledgeHitRate(startDate, endDate) {
  return api.get('/admin/statistics/knowledge-hit-rate', {
    params: { startDate, endDate }
  })
}
