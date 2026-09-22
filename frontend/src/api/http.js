import axios from 'axios'
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  // 凭据走 HttpOnly Cookie（I-2 XSS 整改）：浏览器自动携带 exam_token，不再经 X-Token 注入本地存储凭证
  withCredentials: true
})

function toLogin() {
  localStorage.removeItem('auth')
  if (!window.location.pathname.startsWith('/login')) {
    // P1-6：携带原路径作为 redirect，登录页据此回跳（与 API 契约一致）
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = '/login?redirect=' + redirect
  }
}

// 凭据走 HttpOnly Cookie（I-2 XSS 整改）：浏览器自动携带 exam_token，无需请求拦截器注入令牌，
// 故移除原空实现请求拦截器（(config) => config 无实际作用，属死代码）。
http.interceptors.response.use(
  (response) => {
    const res = response.data
    // 401 统一处理：跳转登录页并直接 reject，避免再进入下方分支重复弹错误提示
    if (res.code === 401) {
      toLogin()
      return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
    }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      toLogin()
    }
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default http
