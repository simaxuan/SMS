import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// 第五轮优化1：ElementPlus 改为 unplugin-vue-components 按需引入
// （组件、样式与方法均按使用自动引入），不再 app.use(ElementPlus) 全量注册，
// 由 App.vue 外层 el-config-provider 提供中文 locale。
// 注：此前误引入 createPinia（pinia 无任何 store 文件，属死依赖），已移除。
const app = createApp(App)

app.use(router)

app.mount('#app')
