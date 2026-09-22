import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 前端分包优化（第三轮优化3）：将体积大的第三方依赖拆成独立 chunk，
// 以便浏览器并行/按需加载并利用 HTTP 缓存，降低首屏单包体积。
function manualChunks(id) {
  if (!id.includes('node_modules')) return undefined
  if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element'
  if (id.includes('echarts')) return 'vendor-echarts'
  if (id.includes('vue') || id.includes('vue-router')) return 'vendor-vue'
  if (id.includes('axios')) return 'vendor-axios'
  return 'vendor-other'
}

export default defineConfig({
  plugins: [
    vue(),
    // 第五轮优化1：ElementPlus 按需引入
    // 自动按需注册组件（含样式），并在需要时自动引入 ElMessage/ElMessageBox 等 API 方法。
    AutoImport({
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      resolvers: [ElementPlusResolver()]
    })
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks
      }
    },
    // 阈值与 vendor-element 实际体积匹配：element-plus 全量按需后 minified ~819KB、
    // gzip ~258KB，对本机/局域网单机部署已合理；700 阈值会误报，故调高到 1000，
    // 仍保留对"单包超 1MB"的提醒（避免直接关掉告警）。
    chunkSizeWarningLimit: 1000
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
