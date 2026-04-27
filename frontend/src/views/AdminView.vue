<template>
  <div class="admin-view">
    <!-- 侧边栏 -->
    <aside class="admin-sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <el-icon size="32" color="#fff"><Service /></el-icon>
        </div>
        <div class="brand">
          <h2>国资客服系统</h2>
          <span>管理后台</span>
        </div>
      </div>

      <el-menu
        :default-active="$route.path"
        router
        class="admin-menu"
        background-color="#1a5fb4"
        text-color="#fff"
        active-text-color="#ffd04b"
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
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="admin-main">
      <header class="admin-header">
        <div class="header-title">
          <h2>{{ $route.meta.title }}</h2>
        </div>
        <div class="header-actions">
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="32" :icon="UserFilled" />
              <span>管理员</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人设置</el-dropdown-item>
                <el-dropdown-item>修改密码</el-dropdown-item>
                <el-dropdown-item divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <div class="admin-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  Service, DataLine, ChatDotRound, User, Document,
  TrendCharts, Back, UserFilled, ArrowDown
} from '@element-plus/icons-vue'
import { getHandoffConversations } from '@/api/chat.js'

const router = useRouter()
const route = useRoute()

const handoffCount = ref(0)

// 获取转人工数量
async function fetchHandoffCount() {
  try {
    const res = await getHandoffConversations()
    handoffCount.value = res.data.length
  } catch (error) {
    console.error('获取转人工数量失败:', error)
  }
}

// 返回客服页面
function goToChat() {
  router.push('/')
}

onMounted(() => {
  fetchHandoffCount()
  // 每30秒刷新一次
  setInterval(fetchHandoffCount, 30000)
})
</script>

<style scoped lang="scss">
.admin-view {
  display: flex;
  height: 100vh;
  background: #f5f7fa;
}

// 侧边栏
.admin-sidebar {
  width: 240px;
  background: #1a5fb4;
  display: flex;
  flex-direction: column;

  .sidebar-header {
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 12px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    .logo {
      width: 48px;
      height: 48px;
      background: rgba(255, 255, 255, 0.15);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .brand {
      h2 {
        font-size: 16px;
        font-weight: 600;
        color: #fff;
        margin: 0;
      }

      span {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.7);
      }
    }
  }

  .admin-menu {
    flex: 1;
    border-right: none;

    .el-menu-item {
      height: 50px;
      line-height: 50px;

      &:hover {
        background: rgba(255, 255, 255, 0.1) !important;
      }

      &.is-active {
        background: rgba(255, 255, 255, 0.15) !important;
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
    border-top: 1px solid rgba(255, 255, 255, 0.1);

    .el-button {
      width: 100%;
      color: rgba(255, 255, 255, 0.8);

      &:hover {
        color: #fff;
      }
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
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;

  .header-title {
    h2 {
      font-size: 18px;
      font-weight: 600;
      color: #1a1a1a;
      margin: 0;
    }
  }

  .header-actions {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 8px 12px;
      border-radius: 6px;
      transition: background 0.2s;

      &:hover {
        background: #f5f7fa;
      }

      span {
        font-size: 14px;
        color: #606266;
      }
    }
  }
}

// 内容区
.admin-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
