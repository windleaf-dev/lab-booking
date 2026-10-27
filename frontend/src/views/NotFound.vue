<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="code">404</div>
    <h1>页面不存在</h1>
    <p class="muted">你访问的地址可能已被移除，或者输入有误。</p>
    <el-button type="primary" @click="router.replace('/dashboard')">返回仪表盘</el-button>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--lab-bg);
}

.code {
  font-size: 88px;
  font-weight: 800;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #2f6fed, #7c3aed);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 0 0 16px;
  font-size: 13px;
}
</style>
