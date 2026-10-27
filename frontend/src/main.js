import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Calendar,
  Checked,
  CircleCheck,
  Collection,
  DataBoard,
  InfoFilled,
  Lock,
  Monitor,
  Plus,
  Refresh,
  Right,
  Search,
  Star,
  Tickets,
  TrendCharts,
  User,
  Warning,
} from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'

// 全站深色科技主题
document.documentElement.classList.add('dark')

const app = createApp(App)

// 只注册项目实际用到的图标，避免把整套图标库打进产物
const icons = {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Calendar,
  Checked,
  CircleCheck,
  Collection,
  DataBoard,
  InfoFilled,
  Lock,
  Monitor,
  Plus,
  Refresh,
  Right,
  Search,
  Star,
  Tickets,
  TrendCharts,
  User,
  Warning,
}

for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
