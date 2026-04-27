<template>
  <div class="conversations">
    <!-- 搜索筛选 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索会话ID或用户"
        style="width: 240px"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="statusFilter" placeholder="会话状态" clearable style="width: 140px">
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已关闭" value="CLOSED" />
        <el-option label="已转人工" value="HANDOFF" />
      </el-select>

      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px"
      />

      <el-button type="primary" @click="fetchConversations">
        <el-icon><Search /></el-icon>
        查询
      </el-button>
    </div>

    <!-- 会话列表 -->
    <el-table
      :data="conversations"
      v-loading="loading"
      stripe
      style="width: 100%"
    >
      <el-table-column prop="sessionId" label="会话ID" width="180">
        <template #default="{ row }">
          <el-link type="primary" @click="viewDetail(row)">
            {{ row.sessionId.substring(0, 16) }}...
          </el-link>
        </template>
      </el-table-column>

      <el-table-column prop="userName" label="用户" width="120">
        <template #default="{ row }">
          {{ row.userName || '匿名用户' }}
        </template>
      </el-table-column>

      <el-table-column prop="primaryIntent" label="主要意图" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.primaryIntent" size="small" type="info">
            {{ getIntentLabel(row.primaryIntent) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="humanHandoff" label="转人工" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.humanHandoff" type="warning" size="small">是</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column prop="satisfactionScore" label="满意度" width="100">
        <template #default="{ row }">
          <el-rate
            v-if="row.satisfactionScore"
            v-model="row.satisfactionScore"
            disabled
            show-score
            text-color="#ff9900"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="viewDetail(row)">
            查看
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 会话详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="会话详情"
      width="700px"
    >
      <div v-if="currentConversation" class="conversation-detail">
        <div class="detail-header">
          <div class="detail-item">
            <span class="label">会话ID：</span>
            <span class="value">{{ currentConversation.sessionId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">用户：</span>
            <span class="value">{{ currentConversation.userName || '匿名用户' }}</span>
          </div>
          <div class="detail-item">
            <span class="label">状态：</span>
            <el-tag :type="getStatusType(currentConversation.status)" size="small">
              {{ getStatusLabel(currentConversation.status) }}
            </el-tag>
          </div>
          <div class="detail-item">
            <span class="label">满意度：</span>
            <el-rate
              v-if="currentConversation.satisfactionScore"
              v-model="currentConversation.satisfactionScore"
              disabled
              show-score
            />
            <span v-else>未评价</span>
          </div>
        </div>

        <div class="message-list">
          <div
            v-for="(msg, index) in currentMessages"
            :key="index"
            class="message-item"
            :class="msg.senderType.toLowerCase()"
          >
            <div class="message-header">
              <span class="sender">{{ getSenderLabel(msg.senderType) }}</span>
              <span class="time">{{ formatDateTime(msg.createdAt) }}</span>
            </div>
            <div class="message-content">{{ msg.content }}</div>
            <div v-if="msg.intentType" class="message-intent">
              <el-tag size="small" type="info">{{ getIntentLabel(msg.intentType) }}</el-tag>
              <span v-if="msg.intentConfidence" class="confidence">
                置信度: {{ (msg.intentConfidence * 100).toFixed(1) }}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getConversations, getConversationMessages } from '@/api/chat.js'

const loading = ref(false)
const conversations = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const searchQuery = ref('')
const statusFilter = ref('')
const dateRange = ref(null)

const detailDialogVisible = ref(false)
const currentConversation = ref(null)
const currentMessages = ref([])

// 获取状态标签
function getStatusLabel(status) {
  const labels = {
    'ACTIVE': '进行中',
    'CLOSED': '已关闭',
    'HANDOFF': '已转人工',
    'TIMEOUT': '已超时'
  }
  return labels[status] || status
}

// 获取状态类型
function getStatusType(status) {
  const types = {
    'ACTIVE': 'success',
    'CLOSED': 'info',
    'HANDOFF': 'warning',
    'TIMEOUT': 'danger'
  }
  return types[status] || 'info'
}

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

// 获取发送者标签
function getSenderLabel(senderType) {
  const labels = {
    'USER': '用户',
    'AI': 'AI助手',
    'HUMAN': '人工客服',
    'SYSTEM': '系统'
  }
  return labels[senderType] || senderType
}

// 格式化日期时间
function formatDateTime(dateTime) {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN')
}

// 获取会话列表
async function fetchConversations() {
  loading.value = true
  try {
    const res = await getConversations(currentPage.value - 1, pageSize.value)
    conversations.value = res.data.content
    total.value = res.data.totalElements
  } catch (error) {
    console.error('获取会话列表失败:', error)
    ElMessage.error('获取会话列表失败')
  } finally {
    loading.value = false
  }
}

// 查看详情
async function viewDetail(row) {
  currentConversation.value = row
  detailDialogVisible.value = true

  try {
    const res = await getConversationMessages(row.sessionId)
    currentMessages.value = res.data
  } catch (error) {
    console.error('获取会话消息失败:', error)
    ElMessage.error('获取会话消息失败')
  }
}

// 分页处理
function handleSizeChange(val) {
  pageSize.value = val
  fetchConversations()
}

function handleCurrentChange(val) {
  currentPage.value = val
  fetchConversations()
}

onMounted(() => {
  fetchConversations()
})
</script>

<style scoped lang="scss">
.conversations {
  .search-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    padding: 20px;
    background: #fff;
    border-radius: 8px;
  }

  .pagination {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
    padding: 20px;
    background: #fff;
    border-radius: 8px;
  }
}

.conversation-detail {
  .detail-header {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    padding: 16px;
    background: #f5f7fa;
    border-radius: 8px;
    margin-bottom: 20px;

    .detail-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .label {
        color: #606266;
        font-size: 14px;
      }

      .value {
        color: #1a1a1a;
        font-weight: 500;
      }
    }
  }

  .message-list {
    max-height: 400px;
    overflow-y: auto;

    .message-item {
      padding: 12px;
      margin-bottom: 12px;
      border-radius: 8px;
      background: #f5f7fa;

      &.user {
        background: #e6f7ff;
      }

      &.ai {
        background: #f6ffed;
      }

      &.system {
        background: #fff7e6;
      }

      .message-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 8px;
        font-size: 13px;

        .sender {
          font-weight: 500;
          color: #1a1a1a;
        }

        .time {
          color: #909399;
        }
      }

      .message-content {
        color: #1a1a1a;
        line-height: 1.6;
        white-space: pre-wrap;
      }

      .message-intent {
        margin-top: 8px;
        display: flex;
        align-items: center;
        gap: 8px;

        .confidence {
          font-size: 12px;
          color: #909399;
        }
      }
    }
  }
}
</style>
