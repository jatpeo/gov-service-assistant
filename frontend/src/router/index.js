import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '智能客服' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { title: '管理后台' },
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
  next()
})

export default router
