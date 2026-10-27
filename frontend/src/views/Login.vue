<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '123456',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const demoAccounts = [
  { username: 'admin', label: '管理员', desc: '全量权限 · 用户管理', code: 'ADM' },
  { username: 'teacher', label: '教师', desc: '设备维护 · 审批预约', code: 'TCH' },
  { username: 'student', label: '学生', desc: '查看设备 · 发起预约', code: 'STU' },
]

function fill(account) {
  form.username = account.username
  form.password = '123456'
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const user = await auth.login({ ...form })
    ElMessage.success(`欢迎回来，${user.realName || user.username}`)
    router.replace(route.query.redirect || '/dashboard')
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="scanline" />
    <div class="orb orb-a" />
    <div class="orb orb-b" />

    <header class="login-top">
      <div class="top-brand">
        <div class="top-logo">
          <el-icon :size="18"><Monitor /></el-icon>
        </div>
        <span>LAB · BOOKING</span>
      </div>
      <div class="top-status">
        <span class="status-dot" />
        SYSTEM ONLINE
      </div>
    </header>

    <main class="login-stage">
      <section class="hero">
        <div class="hero-eyebrow">
          <span class="eyebrow-line" />
          LABORATORY RESOURCE ORCHESTRATION
        </div>
        <h1>
          实验室设备<br />
          <span class="gradient-text">预约与审批中枢</span>
        </h1>
        <p class="hero-desc">
          统一管理设备台账、时段资源与审批流程。实时状态、冲突校验、签到违约，
          让每一台设备的使用都可追踪、可度量。
        </p>

        <div class="hero-metrics">
          <div class="metric">
            <span class="metric-value mono">08</span>
            <span class="metric-label">DEVICES</span>
          </div>
          <div class="metric">
            <span class="metric-value mono">04</span>
            <span class="metric-label">SLOTS / DAY</span>
          </div>
          <div class="metric">
            <span class="metric-value mono">03</span>
            <span class="metric-label">ROLES</span>
          </div>
        </div>

        <ul class="feature-list">
          <li>
            <el-icon><Calendar /></el-icon>
            <div>
              <strong>时段冲突校验</strong>
              <span>数据库唯一索引兜底，并发下也不会重复占位</span>
            </div>
          </li>
          <li>
            <el-icon><Checked /></el-icon>
            <div>
              <strong>审批与违约闭环</strong>
              <span>教师审批、扫码签到、超时未签到自动记录违约</span>
            </div>
          </li>
          <li>
            <el-icon><TrendCharts /></el-icon>
            <div>
              <strong>资源使用分析</strong>
              <span>预约趋势、设备热度、状态分布实时可视化</span>
            </div>
          </li>
        </ul>

        <div class="hero-rings">
          <div class="ring ring-1" />
          <div class="ring ring-2" />
          <div class="ring ring-3" />
        </div>
      </section>

      <section class="login-card">
        <div class="card-corner tl" />
        <div class="card-corner tr" />
        <div class="card-corner bl" />
        <div class="card-corner br" />

        <div class="card-head">
          <div>
            <span class="hud-label">SECURE ACCESS</span>
            <h2>账号登录</h2>
          </div>
          <div class="card-badge mono">JWT · HS256</div>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="submit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="'User'" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              :prefix-icon="'Lock'"
              show-password
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">
            <span>进入控制台</span>
            <el-icon><Right /></el-icon>
          </el-button>
        </el-form>

        <div class="demo-box">
          <div class="demo-title">
            <span>演示身份</span>
            <span class="mono dim">PASSWORD · 123456</span>
          </div>
          <div class="demo-list">
            <button
              v-for="account in demoAccounts"
              :key="account.username"
              type="button"
              class="demo-item"
              :class="{ active: form.username === account.username }"
              @click="fill(account)"
            >
              <span class="demo-code mono">{{ account.code }}</span>
              <div class="demo-main">
                <strong>{{ account.label }}</strong>
                <span>{{ account.desc }}</span>
              </div>
              <span class="demo-user mono">{{ account.username }}</span>
            </button>
          </div>
        </div>

        <div class="card-foot">
          <span>Vue 3</span>
          <span class="sep">/</span>
          <span>Spring Boot 3</span>
          <span class="sep">/</span>
          <span>MySQL 8</span>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(900px 600px at 18% 12%, rgba(47, 140, 255, 0.16), transparent 60%),
    radial-gradient(700px 500px at 88% 88%, rgba(139, 92, 246, 0.12), transparent 60%),
    #040a14;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(90, 150, 230, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(90, 150, 230, 0.055) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 50% 40%, #000 0%, transparent 80%);
}

.scanline {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.55), transparent);
  filter: blur(1px);
  animation: scan 7s linear infinite;
  opacity: 0.6;
}

@keyframes scan {
  0% {
    top: -2%;
  }
  100% {
    top: 102%;
  }
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  animation: float 12s ease-in-out infinite;
}

.orb-a {
  width: 420px;
  height: 420px;
  background: rgba(47, 140, 255, 0.28);
  top: -160px;
  left: -120px;
}

.orb-b {
  width: 360px;
  height: 360px;
  background: rgba(34, 211, 238, 0.18);
  bottom: -140px;
  right: 8%;
  animation-delay: -4s;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-22px);
  }
}

.login-top {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26px 42px;
}

.top-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 12px;
  letter-spacing: 3px;
  color: #93a9cc;
}

.top-logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #e6f6ff;
  background: linear-gradient(135deg, #2f8cff, #22d3ee);
  box-shadow: 0 0 22px rgba(47, 140, 255, 0.5);
}

.top-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  letter-spacing: 2px;
  color: #5eead4;
}

.login-stage {
  position: relative;
  z-index: 2;
  flex: 1;
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 24px 42px 54px;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  align-items: center;
  gap: 60px;
}

/* ---------------- 左侧 Hero ---------------- */
.hero {
  position: relative;
  padding: 10px 0;
}

.hero-eyebrow {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 10px;
  letter-spacing: 3.2px;
  color: #4f7aa8;
  margin-bottom: 22px;
}

.eyebrow-line {
  width: 36px;
  height: 1px;
  background: linear-gradient(90deg, #2f8cff, transparent);
}

.hero h1 {
  margin: 0;
  font-size: clamp(34px, 4.4vw, 54px);
  line-height: 1.22;
  letter-spacing: 1px;
  font-weight: 650;
  color: #eaf4ff;
}

.gradient-text {
  background: linear-gradient(100deg, #7cc4ff 0%, #22d3ee 45%, #a78bfa 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 26px rgba(34, 211, 238, 0.22));
}

.hero-desc {
  margin: 22px 0 0;
  max-width: 480px;
  font-size: 14px;
  line-height: 1.9;
  color: #8ba2c4;
}

.hero-metrics {
  display: flex;
  gap: 42px;
  margin: 30px 0 26px;
  padding: 18px 0;
  border-top: 1px solid rgba(74, 130, 210, 0.16);
  border-bottom: 1px solid rgba(74, 130, 210, 0.16);
  max-width: 480px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-value {
  font-size: 26px;
  color: #cfe8ff;
  text-shadow: 0 0 18px rgba(80, 170, 255, 0.55);
}

.metric-label {
  font-size: 9px;
  letter-spacing: 2px;
  color: #4f6485;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 15px;
  max-width: 500px;
}

.feature-list li {
  display: flex;
  gap: 13px;
  align-items: flex-start;
}

.feature-list .el-icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: #7cc4ff;
  background: rgba(47, 140, 255, 0.1);
  border: 1px solid rgba(74, 130, 210, 0.24);
}

.feature-list strong {
  display: block;
  font-size: 13px;
  color: #cfe0f7;
  font-weight: 600;
}

.feature-list span {
  font-size: 11.5px;
  color: #6f86a8;
  line-height: 1.6;
}

.hero-rings {
  position: absolute;
  right: -80px;
  top: 50%;
  transform: translateY(-50%);
  width: 320px;
  height: 320px;
  pointer-events: none;
  opacity: 0.35;
}

.ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(90, 160, 255, 0.3);
}

.ring-1 {
  inset: 0;
  animation: spin 26s linear infinite;
  border-style: dashed;
}

.ring-2 {
  inset: 44px;
  animation: spin 18s linear infinite reverse;
}

.ring-3 {
  inset: 92px;
  border-color: rgba(34, 211, 238, 0.4);
  box-shadow: 0 0 30px rgba(34, 211, 238, 0.15) inset;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------------- 登录卡片 ---------------- */
.login-card {
  position: relative;
  padding: 34px 32px 26px;
  border-radius: 18px;
  background: linear-gradient(165deg, rgba(14, 28, 50, 0.92), rgba(6, 13, 25, 0.94));
  border: 1px solid rgba(90, 160, 255, 0.26);
  box-shadow:
    0 40px 100px rgba(0, 0, 0, 0.55),
    0 0 60px rgba(47, 140, 255, 0.1),
    inset 0 1px 0 rgba(150, 200, 255, 0.08);
  backdrop-filter: blur(16px);
}

.login-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 20%;
  right: 20%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(34, 211, 238, 0.7), transparent);
}

.card-corner {
  position: absolute;
  width: 16px;
  height: 16px;
  border-color: rgba(34, 211, 238, 0.55);
  border-style: solid;
  pointer-events: none;
}

.card-corner.tl {
  top: 10px;
  left: 10px;
  border-width: 1px 0 0 1px;
}

.card-corner.tr {
  top: 10px;
  right: 10px;
  border-width: 1px 1px 0 0;
}

.card-corner.bl {
  bottom: 10px;
  left: 10px;
  border-width: 0 0 1px 1px;
}

.card-corner.br {
  bottom: 10px;
  right: 10px;
  border-width: 0 1px 1px 0;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 26px;
}

.card-head h2 {
  margin: 6px 0 0;
  font-size: 24px;
  letter-spacing: 1px;
}

.card-badge {
  font-size: 9px;
  letter-spacing: 1px;
  color: #5eead4;
  padding: 4px 9px;
  border-radius: 5px;
  border: 1px solid rgba(52, 211, 153, 0.28);
  background: rgba(52, 211, 153, 0.07);
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  font-size: 14px;
  letter-spacing: 2px;
  border-radius: 9px;
  display: flex;
  justify-content: center;
  gap: 8px;
}

.demo-box {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px dashed rgba(74, 130, 210, 0.2);
}

.demo-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #8ba2c4;
  margin-bottom: 12px;
  letter-spacing: 1px;
}

.demo-list {
  display: grid;
  gap: 8px;
}

.demo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  text-align: left;
  padding: 9px 12px;
  border-radius: 9px;
  border: 1px solid rgba(74, 130, 210, 0.16);
  background: rgba(10, 22, 40, 0.6);
  color: inherit;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.demo-item:hover,
.demo-item.active {
  border-color: rgba(90, 160, 255, 0.48);
  background: rgba(47, 140, 255, 0.09);
  box-shadow: 0 0 20px rgba(47, 140, 255, 0.1);
}

.demo-code {
  width: 34px;
  height: 26px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  font-size: 9px;
  letter-spacing: 1px;
  border-radius: 5px;
  color: #7cc4ff;
  border: 1px solid rgba(74, 130, 210, 0.3);
  background: rgba(47, 140, 255, 0.07);
}

.demo-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.demo-main strong {
  font-size: 12.5px;
  color: #d7e5fb;
}

.demo-main span {
  font-size: 10.5px;
  color: #5f7799;
}

.demo-user {
  font-size: 10.5px;
  color: #4f7aa8;
}

.card-foot {
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid rgba(74, 130, 210, 0.12);
  display: flex;
  gap: 8px;
  justify-content: center;
  font-size: 9px;
  letter-spacing: 1.4px;
  color: #3f5675;
}

.sep {
  color: #263a56;
}

@media (max-width: 980px) {
  .login-stage {
    grid-template-columns: 1fr;
    gap: 32px;
    max-width: 520px;
  }

  .hero-rings {
    display: none;
  }

  .hero h1 {
    font-size: 36px;
  }

  .hero-desc,
  .hero-metrics,
  .feature-list {
    max-width: none;
  }
}

@media (max-width: 620px) {
  .login-top {
    padding: 18px 20px;
  }

  .login-stage {
    padding: 10px 20px 30px;
  }

  .login-card {
    padding: 26px 20px 20px;
  }

  .feature-list {
    display: none;
  }

  .hero-metrics {
    gap: 26px;
  }
}
</style>
