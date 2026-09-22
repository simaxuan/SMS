import { ref, computed } from 'vue'
import { authApi } from '../api'

/**
 * I-2 XSS 整改后：凭据（token）保存在后端 HttpOnly Cookie 中，本模块只缓存
 * 【非敏感】的用户资料（角色/昵称/用户名/学校/范围）用于 UI 状态与路由守卫，不再持有可被 XSS 窃取的令牌。
 */
function normalize(session) {
  if (!session?.user) return null
  const u = session.user
  // #24 白名单字段缓存：绝不把 phone / idCard 等敏感字段写入 localStorage
  return {
    user: {
      id: u?.id,
      username: u?.username,
      role: u?.role,
      nickname: u?.nickname,
      schoolId: u?.schoolId,
      scopeType: u?.scopeType,
      authType: u?.authType
    }
  }
}

/** 从 localStorage 恢复当前登录用户的资料（仅 user，无 token）。 */
function loadAuth() {
  const raw = localStorage.getItem('auth')
  if (!raw) return null
  try {
    return normalize(JSON.parse(raw))
  } catch (e) {
    return null
  }
}

const auth = ref(loadAuth())
const isTeacher = computed(() => auth.value?.user?.role === 'TEACHER')
const nickname = computed(() => auth.value?.user?.nickname || auth.value?.user?.username || '')

/** 退出：通知后端销毁会话（黑名单 + 清 Cookie）并清理本地资料缓存。 */
async function logout() {
  try {
    await authApi.logout()
  } catch (e) {
    // 忽略登出接口异常，本地必清
  }
  setSession(null)
}

/** 登录/登出成功后同步共享响应式会话，使 App 侧菜单/昵称/角色标签即时刷新。 */
function setSession(session) {
  auth.value = normalize(session)
  if (auth.value) {
    localStorage.setItem('auth', JSON.stringify(auth.value))
  } else {
    localStorage.removeItem('auth')
  }
}

export function useAuth() {
  return { auth, isTeacher, nickname, logout, setSession }
}
