import { describe, it, expect, beforeEach, vi } from 'vitest'

// mock 路径须与 useAuth.js 内 import 的 specifier 一致
vi.mock('../src/api', () => ({
  authApi: {
    logout: vi.fn(() => Promise.resolve())
  }
}))

import { authApi } from '../src/api'

const AUTH_TEACHER = {
  token: 'tk-1',
  user: { id: 1, username: 'admin', role: 'TEACHER', nickname: '管理员' }
}
const AUTH_PARENT = {
  token: 'tk-2',
  user: { id: 2, username: 'parent1', role: 'PARENT', nickname: '家长' }
}

// useAuth.js 的会话是模块级单例（顶层 ref(loadAuth()) 在模块加载时读取）。
// 因此每个用例需 resetModules + 动态 import，确保重新读取 localStorage。
async function useAuthFresh() {
  vi.resetModules()
  const mod = await import('../src/composables/useAuth.js')
  return mod.useAuth()
}

describe('useAuth', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('无会话时 isTeacher=false、昵称为空', async () => {
    const { isTeacher, nickname } = await useAuthFresh()
    expect(isTeacher.value).toBe(false)
    expect(nickname.value).toBe('')
  })

  it('老师会话：isTeacher=true、昵称取 nickname', async () => {
    localStorage.setItem('auth', JSON.stringify(AUTH_TEACHER))
    const { isTeacher, nickname } = await useAuthFresh()
    expect(isTeacher.value).toBe(true)
    expect(nickname.value).toBe('管理员')
  })

  it('家长会话：isTeacher=false', async () => {
    localStorage.setItem('auth', JSON.stringify(AUTH_PARENT))
    const { isTeacher } = await useAuthFresh()
    expect(isTeacher.value).toBe(false)
  })

  it('损坏的 localStorage 缓存按无会话处理', async () => {
    localStorage.setItem('auth', '{bad json')
    const { isTeacher } = await useAuthFresh()
    expect(isTeacher.value).toBe(false)
  })

  it('登出：调用后端并清理本地会话', async () => {
    localStorage.setItem('auth', JSON.stringify(AUTH_PARENT))
    const { auth, logout } = await useAuthFresh()
    await logout()
    expect(authApi.logout).toHaveBeenCalled()
    expect(auth.value).toBeNull()
    expect(localStorage.getItem('auth')).toBeNull()
  })

  it('登出时后端异常也被吞掉，本地必清', async () => {
    authApi.logout.mockRejectedValueOnce(new Error('net'))
    localStorage.setItem('auth', JSON.stringify(AUTH_PARENT))
    const { auth, logout } = await useAuthFresh()
    await logout()
    expect(auth.value).toBeNull()
    expect(localStorage.getItem('auth')).toBeNull()
  })
})
