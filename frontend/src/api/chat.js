import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/**
 * 创建新会话
 */
export function createConversation(userId, userName) {
  return api.post('/chat/conversation', null, {
    params: { userId, userName }
  })
}

/**
 * 发送消息
 */
export function sendMessage(sessionId, message) {
  return api.post('/chat/message', null, {
    params: { sessionId, message }
  })
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
