import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '首页' } },
  { path: '/students', name: 'Students', component: () => import('../views/StudentList.vue'), meta: { title: '学生信息管理' } },
  { path: '/grade-entry', name: 'GradeEntry', component: () => import('../views/GradeEntry.vue'), meta: { title: '成绩录入' } },
  { path: '/grade-query', name: 'GradeQuery', component: () => import('../views/GradeQuery.vue'), meta: { title: '成绩查询' } },
  { path: '/statistics', name: 'Statistics', component: () => import('../views/Statistics.vue'), meta: { title: '成绩统计' } },
  { path: '/dictionary', name: 'Dictionary', component: () => import('../views/Dictionary.vue'), meta: { title: '基础数据' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.afterEach((to) => {
  document.title = (to.meta?.title ? to.meta.title + ' - ' : '') + '考试成绩管理系统'
})

export default router
