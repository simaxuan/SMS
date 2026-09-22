import { describe, it, expect, vi, beforeEach } from 'vitest'

// 构造 fake axios 实例，其拦截器 use 会把回调存入共享 handlers，供测试直接调用；隔离真实网络
const state = vi.hoisted(() => {
  const inst = {
    interceptors: {
      request: { use: (ok) => { inst.handlers.request = ok } },
      response: { use: (ok, err) => { inst.handlers.responseOK = ok; inst.handlers.responseErr = err } }
    },
    handlers: { request: null, responseOK: null, responseErr: null }
  }
  return { inst }
})

vi.mock('axios', () => ({ default: { create: () => state.inst } }))

// http.js 中 ElMessage 为全局引用（vitest.config 未挂 auto-import 插件），在此安放 stub
globalThis.ElMessage = { error: vi.fn() }

import http from '../src/api/http'

function enterLoginPage() {
  // 将 pathname 置于 /login，使 toLogin 不触发 location.href 导航（jsdom 未实现导航）
  window.history.replaceState(null, '', '/login')
}

describe('http 响应拦截器（统一 code 契约 / 鉴权 401 / 错误提示）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    enterLoginPage()
    localStorage.clear()
    // 注意：不可清空 state.inst.handlers —— 拦截器 use 仅在模块加载时注册一次
  })

  it('会注册 request 与 response 拦截器', () => {
    expect(state.inst.handlers.request).toBeTypeOf('function')
    expect(state.inst.handlers.responseOK).toBeTypeOf('function')
    expect(state.inst.handlers.responseErr).toBeTypeOf('function')
  })

  it('code=200 → 返回 res.data（剥离统一响应壳）', async () => {
    const result = await state.inst.handlers.responseOK({ data: { code: 200, data: { id: 1 }, message: 'ok' } })
    expect(result).toEqual({ id: 1 })
  })

  it('code≠200（如 400）→ ElMessage 提示并 reject', async () => {
    await expect(state.inst.handlers.responseOK({ data: { code: 400, data: null, message: '分数不能超过满分' } }))
      .rejects.toThrow('分数不能超过满分')
    expect(ElMessage.error).toHaveBeenCalledWith('分数不能超过满分')
  })

  it('code=401 → 清理本地会话（登出态）并 reject', async () => {
    localStorage.setItem('auth', JSON.stringify({ token: 't', user: { role: 'PARENT' } }))
    await expect(state.inst.handlers.responseOK({ data: { code: 401, data: null, message: '未登录' } }))
      .rejects.toThrow('未登录')
    expect(localStorage.getItem('auth')).toBeNull()
  })

  it('HTTP 错误（业务 422）→ 取后端 message 提示并 reject', async () => {
    await expect(
      state.inst.handlers.responseErr({ response: { status: 422, data: { message: '该学生该考试成绩已存在' } } })
    ).rejects.toMatchObject({})
    expect(ElMessage.error).toHaveBeenCalledWith('该学生该考试成绩已存在')
  })

  it('HTTP 错误（401）→ 清理会话并 reject（后端降级场景）', async () => {
    localStorage.setItem('auth', JSON.stringify({ token: 't', user: { role: 'TEACHER' } }))
    await expect(state.inst.handlers.responseErr({ response: { status: 401, data: { message: 'token 失效' } } }))
      .rejects.toMatchObject({})
    expect(localStorage.getItem('auth')).toBeNull()
  })

  it('HTTP 网络错误（无 response）→ 兜底提示并 reject', async () => {
    await expect(state.inst.handlers.responseErr({ message: 'Network Error' }))
      .rejects.toMatchObject({ message: 'Network Error' })
    expect(ElMessage.error).toHaveBeenCalledWith('Network Error')
  })

  it('错误分支：无后端 message 时以 error.message 兜底', async () => {
    await expect(
      state.inst.handlers.responseErr({ response: { status: 500, data: {} }, message: 'Request failed' })
    ).rejects.toMatchObject({})
    expect(ElMessage.error).toHaveBeenCalledWith('Request failed')
  })

  it('请求拦截器：透传 config（凭据走 HttpOnly Cookie，不再注入 X-Token）', () => {
    const config = { url: '/grades', method: 'get' }
    expect(state.inst.handlers.request(config)).toBe(config)
  })
})
