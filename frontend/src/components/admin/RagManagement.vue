<template>
  <div class="rag-page">
    <div class="page-header">
      <div>
        <h2>RAG 管理</h2>
        <p>查看当前索引是否可用、最近一次重建结果，并在需要时手动刷新索引。</p>
      </div>
      <div class="header-actions">
        <el-switch
          v-model="autoRefreshEnabled"
          inline-prompt
          active-text="自动刷新"
          inactive-text="手动刷新"
          @change="handleAutoRefreshChange"
        />
        <el-button @click="fetchRagStatus">
          <el-icon><Refresh /></el-icon>
          刷新状态
        </el-button>
        <el-button type="primary" :loading="rebuilding" @click="handleRebuild">
          <el-icon><RefreshRight /></el-icon>
          手动重建
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="summary-grid">
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">索引状态</div>
          <div class="summary-value">
            <el-tag :type="ragStatus.ready ? 'success' : 'warning'" size="large">
              {{ ragStatus.ready ? '已就绪' : '未就绪' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">索引条目</div>
          <div class="summary-value number-value">{{ ragStatus.documentCount }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="summary-card">
          <div class="summary-label">最后重建</div>
          <div class="summary-value time-value">
            {{ formatTime(ragStatus.lastRebuiltAt) || '-' }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="detail-card">
      <template #header>
        <div class="section-header">
          <div>
            <h3>索引详情</h3>
            <p>这里展示最近一次重建时间、结果信息和当前自动刷新状态。</p>
          </div>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="是否就绪">
          <el-tag :type="ragStatus.ready ? 'success' : 'warning'">
            {{ ragStatus.ready ? '已就绪' : '未就绪' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="条目数量">
          {{ ragStatus.documentCount }}
        </el-descriptions-item>
        <el-descriptions-item label="最后重建时间">
          {{ formatTime(ragStatus.lastRebuiltAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="最后重建结果">
          <el-tag :type="ragStatus.lastRebuildSuccess ? 'success' : 'danger'">
            {{ ragStatus.lastRebuildSuccess ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="结果说明" :span="2">
          {{ ragStatus.lastRebuildMessage || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="自动刷新" :span="2">
          {{ autoRefreshEnabled ? '开启' : '关闭' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="!ragStatus.lastRebuildSuccess && ragStatus.lastRebuildMessage"
        class="rag-alert"
        type="warning"
        :title="ragStatus.lastRebuildMessage"
        show-icon
        :closable="false"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight } from '@element-plus/icons-vue'
import { getRagStatus, rebuildRagIndex } from '@/api/chat.js'

const ragStatus = reactive({
  ready: false,
  documentCount: 0,
  lastRebuiltAt: null,
  lastRebuildSuccess: false,
  lastRebuildMessage: ''
})

const rebuilding = ref(false)
const autoRefreshEnabled = ref(true)
let autoRefreshTimer = null

function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function applyRagStatus(data = {}) {
  ragStatus.ready = Boolean(data.ready)
  ragStatus.documentCount = Number(data.documentCount || 0)
  ragStatus.lastRebuiltAt = data.lastRebuiltAt || null
  ragStatus.lastRebuildSuccess = Boolean(data.lastRebuildSuccess)
  ragStatus.lastRebuildMessage = data.lastRebuildMessage || data.message || ''
}

async function fetchRagStatus() {
  try {
    const res = await getRagStatus()
    applyRagStatus(res.data || {})
  } catch (error) {
    console.error('获取 RAG 状态失败:', error)
    ElMessage.error('获取 RAG 状态失败')
  }
}

async function handleRebuild() {
  rebuilding.value = true
  try {
    const res = await rebuildRagIndex()
    const data = res.data || {}
    applyRagStatus(data)
    if (data.success === false) {
      ElMessage.warning(data.message || 'RAG 索引重建未完成')
    } else {
      ElMessage.success('RAG 索引已重建')
    }
  } catch (error) {
    console.error('重建 RAG 失败:', error)
    ElMessage.error('重建 RAG 失败')
  } finally {
    rebuilding.value = false
    await fetchRagStatus()
  }
}

function startAutoRefresh() {
  stopAutoRefresh()
  if (!autoRefreshEnabled.value) return
  autoRefreshTimer = window.setInterval(fetchRagStatus, 30000)
}

function stopAutoRefresh() {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

function handleAutoRefreshChange() {
  if (autoRefreshEnabled.value) {
    startAutoRefresh()
    fetchRagStatus()
  } else {
    stopAutoRefresh()
  }
}

onMounted(() => {
  fetchRagStatus()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.rag-page {
  display: grid;
  gap: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #1f2937;
}

.page-header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.summary-grid {
  margin: 0;
}

.summary-card {
  height: 100%;
}

.summary-label {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 12px;
}

.summary-value {
  color: #1f2937;
}

.number-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.time-value {
  font-size: 16px;
  font-weight: 600;
}

.detail-card {
  min-height: 320px;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
}

.section-header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.rag-alert {
  margin-top: 16px;
}
</style>
