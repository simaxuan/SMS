<template>
  <!-- 第五轮优化1：外层 el-config-provider 提供中文 locale（按需引入后无全局 use(ElementPlus)） -->
  <el-config-provider :locale="zhCn">
    <!-- 登录页：全屏无布局 -->
    <router-view v-if="$route.path === '/login'" />

  <el-container v-else class="layout">
    <!-- 侧边栏宽度随折叠态变化；窄屏（≤768px）由 onMounted 自动折叠 -->
    <el-aside :width="asideWidth" :class="{ aside: true, collapsed }">
      <div class="logo">📊 考试成绩管理系统</div>
      <div class="role-tag">{{ isTeacher ? '校园版·老师' : '个人版·家长' }}</div>
      <!-- :collapse 由 Element Plus 自动切换为图标模式，无需手动隐藏文字 -->
      <el-menu :collapse="collapsed" :default-active="$route.path" router background-color="#001529" text-color="#c0c4cc" active-text-color="#409eff">
        <template v-if="isTeacher">
          <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>首页</span></el-menu-item>
          <el-menu-item index="/students"><el-icon><User /></el-icon><span>学生信息管理</span></el-menu-item>
          <el-menu-item index="/grade-entry"><el-icon><EditPen /></el-icon><span>成绩录入</span></el-menu-item>
          <el-menu-item index="/grade-query"><el-icon><Search /></el-icon><span>成绩查询</span></el-menu-item>
          <el-menu-item index="/statistics"><el-icon><TrendCharts /></el-icon><span>成绩统计</span></el-menu-item>
          <el-menu-item index="/student-trend"><el-icon><DataLine /></el-icon><span>个人成绩趋势</span></el-menu-item>
          <el-menu-item index="/exams"><el-icon><Notebook /></el-icon><span>考试管理</span></el-menu-item>
          <el-menu-item index="/settings"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
          <el-menu-item index="/dictionary"><el-icon><Collection /></el-icon><span>基础数据</span></el-menu-item>
          <el-menu-item index="/organization"><el-icon><OfficeBuilding /></el-icon><span>组织管理</span></el-menu-item>
        </template>
        <template v-else>
          <el-menu-item index="/parent/home"><el-icon><Odometer /></el-icon><span>家长首页</span></el-menu-item>
          <el-menu-item index="/parent/bind"><el-icon><Link /></el-icon><span>孩子绑定</span></el-menu-item>
          <el-menu-item index="/parent/trend"><el-icon><DataLine /></el-icon><span>孩子成绩趋势</span></el-menu-item>
          <el-menu-item index="/parent/entry"><el-icon><EditPen /></el-icon><span>录入小测成绩</span></el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button text class="collapse-btn" @click="toggleSidebar" :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'">
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <span class="title">{{ $route.meta?.title || '首页' }}</span>
        </div>
        <div class="user-box">
          <!-- 三遗留处理：全局超管（SCOPE_ALL）登录校切换器；null=全部学校总览 -->
          <el-select
            v-if="isGlobalAdmin"
            v-model="activeSchoolId"
            size="small"
            placeholder="全部学校（总览）"
            clearable
            style="width: 168px"
            @change="onSwitchSchool">
            <el-option :value="null" label="全部学校（总览）" />
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
          <span class="nickname">{{ nickname }}</span>
          <el-button size="small" @click="openPwdDialog">修改密码</el-button>
          <el-button size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 修改密码对话框（家长/老师通用） -->
  <el-dialog v-model="pwdDialogVisible" title="修改密码" width="420px" @closed="resetPwdForm">
    <el-form label-width="100px" @submit.prevent>
      <el-form-item label="新密码">
        <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-64 位"
                  autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认密码">
        <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码"
                  autocomplete="new-password" @keyup.enter="submitPwd" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSubmitting" @click="submitPwd">确认修改</el-button>
    </template>
  </el-dialog>
  </el-config-provider>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ref, onMounted, computed } from 'vue'
import { useAuth } from './composables/useAuth'
import { schoolApi, authApi } from './api'
// 中文 locale
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 侧边菜单图标：按需显式引入（不再全局注册全部图标）
import { Odometer, User, EditPen, Search, TrendCharts, DataLine, Notebook, Setting, Collection, Link, OfficeBuilding, Fold, Expand } from '@element-plus/icons-vue'

const router = useRouter()
const { isTeacher, nickname, logout, auth, setSession } = useAuth()

// 修改密码
const pwdDialogVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = ref({ newPassword: '', confirm: '' })
function openPwdDialog() {
  pwdForm.value = { newPassword: '', confirm: '' }
  pwdDialogVisible.value = true
}
function resetPwdForm() {
  pwdForm.value = { newPassword: '', confirm: '' }
}
async function submitPwd() {
  const np = pwdForm.value.newPassword
  if (!np || np.length < 6) { ElMessage.warning('新密码至少 6 位'); return }
  if (np !== pwdForm.value.confirm) { ElMessage.warning('两次输入的密码不一致'); return }
  pwdSubmitting.value = true
  try {
    await authApi.changePassword(np)
    ElMessage.success('密码已修改，请用新密码重新登录')
    pwdDialogVisible.value = false
    // 改密后后端已使旧令牌失效：跳回登录页
    logout()
    router.push('/login')
  } catch (e) {
    // 错误已提示
  } finally {
    pwdSubmitting.value = false
  }
}

// 侧边栏折叠：桌面默认展开，窄屏（≤768px）自动折叠，避免挤压主内容区
const collapsed = ref(false)
const asideWidth = computed(() => (collapsed.value ? '64px' : '220px'))
function toggleSidebar() { collapsed.value = !collapsed.value }

// 三遗留处理：全局超管（SCOPE_ALL）登录校切换器
const isGlobalAdmin = computed(() => auth.value?.user?.scopeType === 'ALL')
const schools = ref([])
const activeSchoolId = ref(null)

onMounted(async () => {
  if (isGlobalAdmin.value) {
    try {
      const list = await schoolApi.list()
      schools.value = Array.isArray(list) ? list : []
    } catch (e) {
      schools.value = []
    }
    activeSchoolId.value = auth.value?.user?.schoolId ?? null
  }
})

// 切换登录校：null 表示"全部学校总览"；成功后用后端返回的新 user 刷新本地会话（含 schoolId）
async function onSwitchSchool(sid) {
  const target = sid ?? null
  try {
    const payload = await authApi.switchSchool(target)
    const user = payload?.user
    if (user) {
      setSession({ user })
      activeSchoolId.value = user.schoolId ?? null
    }
  } catch (e) {
    // 切换失败回滚到当前会话学校，避免 UI 与后端不一致
    activeSchoolId.value = auth.value?.user?.schoolId ?? null
  }
}

// 注销：登出后端会话并跳回登录页
function handleLogout() {
  logout()
  router.push('/login')
}

// 窄屏自动折叠：进入即判断，窗口缩放到 ≤768px 时收起侧栏，留更多空间给内容
onMounted(() => {
  if (window.innerWidth <= 768) collapsed.value = true
  window.addEventListener('resize', () => {
    if (window.innerWidth <= 768) collapsed.value = true
  })
})
</script>

<style>
html, body, #app {
  margin: 0;
  height: 100%;
}
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
}
.aside .logo {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  padding: 18px 0 6px;
  letter-spacing: 1px;
}
.aside .role-tag {
  color: #7f93aa;
  font-size: 12px;
  text-align: center;
  margin-bottom: 12px;
}
.aside .el-menu {
  border-right: none;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.collapse-btn {
  font-size: 18px;
}
.header .title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.user-box {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-box .nickname {
  color: var(--el-text-color-regular);
  font-size: 14px;
}
/* 折叠态：隐藏侧栏品牌文字与角色标识，仅保留图标导航 */
.aside.collapsed .logo,
.aside.collapsed .role-tag {
  display: none;
}
.main {
  background: #f0f2f5;
  padding: 20px;
}
/* 内容最大宽度约束：超宽屏下避免卡片/表格被过度拉伸（作用于 el-main 直接子级） */
.main > * {
  max-width: 1440px;
}
/* 筛选表单窄屏换行兜底：inline 表单换行后项间留出垂直间距 */
.main :deep(.el-form--inline .el-form-item) {
  margin-right: 12px;
  margin-bottom: 14px;
}
/* 对话框最大宽度限制：窄屏（手机/小窗）下不超出视口，避免横向溢出被裁切 */
.el-dialog {
  max-width: 92vw;
}
/* 窄屏适配：缩小主区域留白，把更多宽度留给表格/表单；
   并防止 flex 子级溢出导致整体出现横向滚动条 */
@media (max-width: 992px) {
  .main { padding: 14px; }
}
@media (max-width: 768px) {
  .main { padding: 10px; }
  .layout { min-width: 0; }
}
</style>
