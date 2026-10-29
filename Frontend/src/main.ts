import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/styles/main.css'
import './assets/styles/operations.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// Pinia 必须先于 Router 安装，路由守卫会读取登录与角色状态。
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
