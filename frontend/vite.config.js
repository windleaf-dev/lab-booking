import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5174,
    open: false,
    proxy: {
      // 前端 /api 请求统一转发到后端，避免开发期跨域
      '/api': {
        target: 'http://127.0.0.1:8091',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // 把体积较大的依赖拆成独立 chunk，首屏只下载必要代码
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia', 'axios'],
          'element-plus': ['element-plus'],
          'element-icons': ['@element-plus/icons-vue'],
          echarts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
        },
      },
    },
  },
})
