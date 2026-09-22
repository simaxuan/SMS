import { describe, it, expect } from 'vitest'
import { resolveNav } from '../src/router/index.js'

// 构造一个最小 route 对象（含 meta / fullPath）
const route = (path, meta = {}, fullPath) => ({ path, meta, fullPath: fullPath || path })

describe('路由守卫判定 resolveNav（纯逻辑）', () => {
  const teacherAuth = { token: 't1', user: { role: 'TEACHER', nickname: '管理员' } }
  const parentAuth = { token: 't2', user: { role: 'PARENT', nickname: '家长' } }

  it('未登录访问家长首页（受保护）→ 跳登录并带 redirect', () => {
    const result = resolveNav(route('/parent/home', { roles: ['PARENT'] }, '/parent/home'), null)
    expect(result.path).toBe('/login')
    expect(result.query.redirect).toBe('/parent/home')
  })

  it('老师访问家长页（角色受限）→ 跳回老师首页 /dashboard', () => {
    const result = resolveNav(route('/parent/home', { roles: ['PARENT'] }), teacherAuth)
    expect(result).toBe('/dashboard')
  })

  it('家长访问老师页（角色受限）→ 跳回家长首页 /parent/home', () => {
    const result = resolveNav(route('/dashboard', { roles: ['TEACHER'] }), parentAuth)
    expect(result).toBe('/parent/home')
  })

  it('角色匹配 → 放行 true', () => {
    expect(resolveNav(route('/students', { roles: ['TEACHER'] }), teacherAuth)).toBe(true)
    expect(resolveNav(route('/parent/bind', { roles: ['PARENT'] }), parentAuth)).toBe(true)
  })

  it('公开路由（登录页）→ 放行 true', () => {
    expect(resolveNav(route('/login', { public: true }), null)).toBe(true)
  })

  it('已登录访问登录页 → 按角色回跳首页', () => {
    expect(resolveNav(route('/login', { public: true }), teacherAuth)).toBe('/dashboard')
    expect(resolveNav(route('/login', { public: true }), parentAuth)).toBe('/parent/home')
  })
})
