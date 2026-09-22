import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录', public: true } },
  // ===== 老师端（校园版）=====
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '首页', roles: ['TEACHER'] } },
  { path: '/students', name: 'Students', component: () => import('../views/StudentList.vue'), meta: { title: '学生信息管理', roles: ['TEACHER'] } },
  { path: '/grade-entry', name: 'GradeEntry', component: () => import('../views/GradeEntry.vue'), meta: { title: '成绩录入', roles: ['TEACHER'] } },
  { path: '/grade-query', name: 'GradeQuery', component: () => import('../views/GradeQuery.vue'), meta: { title: '成绩查询', roles: ['TEACHER'] } },
  { path: '/statistics', name: 'Statistics', component: () => import('../views/Statistics.vue'), meta: { title: '成绩统计', roles: ['TEACHER'] } },
  { path: '/student-trend', name: 'StudentTrend', component: () => import('../views/StudentTrend.vue'), meta: { title: '个人成绩趋势', roles: ['TEACHER'] } },
  { path: '/exams', name: 'Exam', component: () => import('../views/Exam.vue'), meta: { title: '考试管理', roles: ['TEACHER'] } },
  { path: '/settings', name: 'Settings', component: () => import('../views/Settings.vue'), meta: { title: '系统设置', roles: ['TEACHER'] } },
  { path: '/dictionary', name: 'Dictionary', component: () => import('../views/Dictionary.vue'), meta: { title: '基础数据', roles: ['TEACHER'] } },
  { path: '/organization', name: 'Organization', component: () => import('../views/Organization.vue'), meta: { title: '组织管理', roles: ['TEACHER'] } },
  // ===== 家长端（个人版）=====
  { path: '/parent/home', name: 'ParentHome', component: () => import('../views/parent/ParentHome.vue'), meta: { title: '家长首页', roles: ['PARENT'] } },
  { path: '/parent/bind', name: 'ParentBind', component: () => import('../views/parent/ParentBind.vue'), meta: { title: '孩子绑定', roles: ['PARENT'] } },
  { path: '/parent/trend', name: 'ParentTrend', component: () => import('../views/parent/ParentTrend.vue'), meta: { title: '孩子成绩趋势', roles: ['PARENT'] } },
  { path: '/parent/entry', name: 'ParentEntry', component: () => import('../views/parent/ParentEntry.vue'), meta: { title: '录入小测成绩', roles: ['PARENT'] } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function getAuth() {
  const raw = localStorage.getItem('auth')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

/**
 * 路由守卫核心判定（纯逻辑，可单测）。
 * 返回：
 *  - true           放行
 *  - 字符串路径      重定向到该路径
 *  - {path, query}  跳转对象（登录页带回跳 redirect）
 */
export function resolveNav(to, auth) {
  if (to.meta.public) {
    // 已登录访问登录页 → 跳回角色首页
    if (auth?.user && to.path === '/login') {
      return auth.user.role === 'TEACHER' ? '/dashboard' : '/parent/home'
    }
    return true
  }
  if (!auth?.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta.roles
  if (roles && !roles.includes(auth.user.role)) {
    // 无权限 → 跳回角色首页
    return auth.user.role === 'TEACHER' ? '/dashboard' : '/parent/home'
  }
  return true
}

router.beforeEach((to) => {
  return resolveNav(to, getAuth())
})

router.afterEach((to) => {
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + '考试成绩管理系统'
})

export default router
