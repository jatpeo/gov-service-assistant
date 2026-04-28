import { createRouter, createWebHistory } from 'vue-router'
import { getStoredUser, hasAnyRole, isAuthenticated } from '@/api/chat.js'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '智能客服', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { title: '管理后台', requiresAuth: true, roles: ['ADMIN', 'AGENT', 'VIEWER'] },
    children: [
      {
        path: '',
        redirect: '/admin/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/components/admin/Dashboard.vue'),
        meta: { title: '数据概览' }
      },
      {
        path: 'conversations',
        name: 'Conversations',
        component: () => import('@/components/admin/Conversations.vue'),
        meta: { title: '会话管理' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/components/admin/Knowledge.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'rag',
        name: 'RagManagement',
        component: () => import('@/components/admin/RagManagement.vue'),
        meta: { title: 'RAG 管理', roles: ['ADMIN'] }
      },
      {
        path: 'quick-services',
        name: 'QuickServices',
        component: () => import('@/components/admin/QuickServices.vue'),
        meta: { title: '快捷服务配置', roles: ['ADMIN'] }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/components/admin/Users.vue'),
        meta: { title: '人员管理', roles: ['ADMIN'] }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/components/admin/Statistics.vue'),
        meta: { title: '统计分析' }
      },
      {
        path: 'handoff',
        name: 'Handoff',
        component: () => import('@/components/admin/Handoff.vue'),
        meta: { title: '人工接入' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title + ' - 国资客服智能助手'
  }

  if (to.matched.some(record => record.meta.requiresAuth) && !isAuthenticated()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  const requiredRoles = [...to.matched]
    .reverse()
    .map(record => record.meta.roles)
    .find(Boolean)
  if (requiredRoles && !hasAnyRole(requiredRoles)) {
    next('/')
    return
  }

  if (to.path === '/login' && isAuthenticated()) {
    const user = getStoredUser()
    next(user?.role === 'USER' ? '/' : '/admin')
    return
  }

  next()
})

export default router
