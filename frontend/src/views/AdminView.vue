<template>
  <div class="admin-view">
    <header class="top-header">
      <div class="top-brand">
        <div class="logo">
          <el-icon size="32" color="#fff"><Service /></el-icon>
        </div>
        <div class="brand">
          <h2>智能客服系统</h2>
          <span>管理后台</span>
        </div>
      </div>
      <div class="top-actions">
        <el-dropdown @command="handleUserCommand">
          <span class="user-info">
            <el-avatar :size="32" :icon="UserFilled" />
            <span>{{ currentUser?.displayName || '管理员' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ currentUser?.username || '未登录' }}</el-dropdown-item>
              <el-dropdown-item disabled>{{ getRoleLabel(currentUser?.role) }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="admin-body">
      <!-- 侧边栏 -->
      <aside class="admin-sidebar">
      <el-menu
        :default-active="$route.path"
        router
        class="admin-menu"
        background-color="#fff"
        text-color="#3f4652"
        active-text-color="#b72a33"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>数据概览</span>
        </el-menu-item>

        <el-menu-item index="/admin/conversations">
          <el-icon><ChatDotRound /></el-icon>
          <span>会话管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/handoff">
          <el-icon><User /></el-icon>
          <span>人工接入</span>
          <el-badge v-if="handoffCount > 0" :value="handoffCount" class="menu-badge" />
        </el-menu-item>

        <el-menu-item index="/admin/knowledge">
          <el-icon><Document /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>

        <el-menu-item v-if="currentUser?.role === 'ADMIN'" index="/admin/rag">
          <el-icon><RefreshRight /></el-icon>
          <span>RAG 管理</span>
        </el-menu-item>

        <el-menu-item v-if="currentUser?.role === 'ADMIN'" index="/admin/users">
          <el-icon><UserFilled /></el-icon>
          <span>人员管理</span>
        </el-menu-item>
        <el-menu-item v-if="currentUser?.role === 'ADMIN'" index="/admin/quick-services">
          <el-icon><Menu /></el-icon>
          <span>快捷服务</span>
        </el-menu-item>

        <el-menu-item index="/admin/statistics">
          <el-icon><TrendCharts /></el-icon>
          <span>统计分析</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <el-button type="info" text @click="goToChat">
          <el-icon><Back /></el-icon>
          返回客服页面
        </el-button>
        <div class="support-info">
          <div>技术服务1：123456789</div>
          <div>系统版本号：v1.0.0</div>
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="admin-main">
      <header class="admin-header">
        <div class="header-title">
          <h2>{{ $route.meta.title }}</h2>
        </div>
      </header>

      <div class="admin-content">
        <router-view />
      </div>
    </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  Service, DataLine, ChatDotRound, User, Document,
  TrendCharts, Back, UserFilled, ArrowDown, Menu, RefreshRight
} from '@element-plus/icons-vue'
import { clearAuth, getCurrentUser, getHandoffConversations, getStoredUser } from '@/api/chat.js'

const router = useRouter()
const route = useRoute()

const handoffCount = ref(0)
const currentUser = ref(getStoredUser())
let handoffTimer = null

// 获取转人工数量
async function fetchHandoffCount() {
  try {
    const res = await getHandoffConversations()
    handoffCount.value = res.data.length
  } catch (error) {
    console.error('获取转人工数量失败:', error)
  }
}

async function fetchCurrentUser() {
  try {
    const res = await getCurrentUser()
    currentUser.value = res.data
  } catch (error) {
    console.error('获取当前用户失败:', error)
  }
}

// 返回客服页面
function goToChat() {
  router.push('/')
}

function getRoleLabel(role) {
  const labels = {
    ADMIN: '系统管理员',
    AGENT: '人工客服',
    VIEWER: '只读账号'
  }
  return labels[role] || '管理账号'
}

function handleUserCommand(command) {
  if (command === 'logout') {
    clearAuth()
    router.replace('/login')
  }
}

onMounted(() => {
  fetchCurrentUser()
  fetchHandoffCount()
  // 每30秒刷新一次
  handoffTimer = setInterval(fetchHandoffCount, 30000)
})

onUnmounted(() => {
  if (handoffTimer) {
    clearInterval(handoffTimer)
  }
})
</script>

<style scoped lang="scss">
.admin-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #eef1f5;
}

.top-header {
  height: 58px;
  background: #b72a33;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(123, 30, 38, 0.22);
  z-index: 2;

  .top-brand {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .logo {
    width: 38px;
    height: 38px;
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.16);
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .brand {
    h2 {
      font-size: 17px;
      font-weight: 600;
      color: #fff;
      margin: 0;
    }

    span {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.78);
    }
  }

  .top-actions {
    .user-info {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      color: #fff;
      cursor: pointer;
      padding: 6px 8px;
      border-radius: 4px;

      &:hover {
        background: rgba(255, 255, 255, 0.12);
      }
    }
  }
}

.admin-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

// 侧边栏
.admin-sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #d7dde6;
  display: flex;
  flex-direction: column;
  padding-top: 14px;

  .admin-menu {
    flex: 1;
    border-right: none;

    .el-menu-item {
      height: 52px;
      line-height: 52px;
      margin: 3px 0;
      border-left: 4px solid transparent;

      &:hover {
        background: #fbf0f1 !important;
        color: #b72a33 !important;
      }

      &.is-active {
        background: #b72a33 !important;
        color: #fff !important;
        border-left-color: #8e1d25;
      }
    }

    .menu-badge {
      margin-left: 8px;

      :deep(.el-badge__content) {
        background: #f56c6c;
        border: none;
      }
    }
  }

  .sidebar-footer {
    padding: 16px;
    border-top: 1px solid #edf0f5;

    .el-button {
      width: 100%;
      color: #b72a33;

      &:hover {
        color: #941f28;
        background: #fbf0f1;
      }
    }

    .support-info {
      margin-top: 12px;
      color: #606875;
      font-size: 12px;
      line-height: 1.7;
    }
  }
}

// 主内容区
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// 头部
.admin-header {
  min-height: 52px;
  background: #fff;
  border: 1px solid #dfe3eb;
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  margin: 16px 18px 0;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.06);

  .header-title {
    h2 {
      font-size: 18px;
      font-weight: 600;
      color: #2b2f36;
      margin: 0;
    }
  }
}

// 内容区
.admin-content {
  flex: 1;
  margin: 0 18px 18px;
  padding: 18px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #dfe3eb;
  border-top: none;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.06);
}
</style>
