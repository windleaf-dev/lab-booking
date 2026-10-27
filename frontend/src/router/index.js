import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import MainLayout from '../layout/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'booking',
        name: 'booking',
        component: () => import('../views/BookingBoard.vue'),
        meta: { title: '预约看板' },
      },
      {
        path: 'my-bookings',
        name: 'my-bookings',
        component: () => import('../views/MyBookings.vue'),
        meta: { title: '我的预约' },
      },
      {
        path: 'approvals',
        name: 'approvals',
        component: () => import('../views/Approvals.vue'),
        meta: { title: '审批中心', roles: ['ADMIN', 'TEACHER'] },
      },
      {
        path: 'analytics',
        name: 'analytics',
        component: () => import('../views/Analytics.vue'),
        meta: { title: '统计分析' },
      },
      {
        path: 'devices',
        name: 'devices',
        component: () => import('../views/DeviceList.vue'),
        meta: { title: '设备管理' },
      },
      {
        path: 'categories',
        name: 'categories',
        component: () => import('../views/CategoryList.vue'),
        meta: { title: '分类管理' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UserList.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFound.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (to.name === 'login' && auth.isLogin) return '/dashboard'
    return true
  }

  if (!auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    ElMessage.warning('没有权限访问该页面')
    return '/dashboard'
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 实验室设备预约平台` : '实验室设备预约平台'
})

export default router
