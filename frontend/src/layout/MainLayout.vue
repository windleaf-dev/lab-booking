<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { fetchOverview } from '../api/stats'
import { changePassword } from '../api/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const pageTitle = computed(() => route.meta.title || '工作台')
const pendingCount = ref(0)
const now = ref(new Date())
let timer = null
let poller = null

const roleTextMap = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
}

const roleTagMap = {
  ADMIN: 'danger',
  TEACHER: 'warning',
  STUDENT: 'info',
}

const menus = computed(() => {
  const all = [
    { section: 'OVERVIEW', path: '/dashboard', title: '仪表盘', icon: 'DataBoard' },
    { section: 'RESERVATION', path: '/booking', title: '预约看板', icon: 'Calendar' },
    { section: 'RESERVATION', path: '/my-bookings', title: '我的预约', icon: 'Tickets' },
    {
      section: 'RESERVATION',
      path: '/approvals',
      title: '审批中心',
      icon: 'Checked',
      roles: ['ADMIN', 'TEACHER'],
    },
    { section: 'RESERVATION', path: '/analytics', title: '统计分析', icon: 'TrendCharts' },
    { section: 'MANAGEMENT', path: '/devices', title: '设备管理', icon: 'Monitor' },
    { section: 'MANAGEMENT', path: '/categories', title: '分类管理', icon: 'Collection' },
    { section: 'MANAGEMENT', path: '/users', title: '用户管理', icon: 'User', roles: ['ADMIN'] },
  ]
  return all.filter((item) => !item.roles || item.roles.includes(auth.role))
})

const groupedMenus = computed(() => {
  const groups = []
  for (const item of menus.value) {
    let group = groups.find((g) => g.name === item.section)
    if (!group) {
      group = { name: item.section, items: [] }
      groups.push(group)
    }
    group.items.push(item)
  }
  return groups
})

const dateText = computed(() =>
  now.value.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'short' }),
)

const timeText = computed(() =>
  now.value.toLocaleTimeString('zh-CN', { hour12: false }),
)

async function loadBadge() {
  if (!auth.isStaff) return
  try {
    const data = await fetchOverview()
    pendingCount.value = data?.pendingCount || 0
  } catch {
    // 看板角标失败不影响主流程
  }
}

async function handleCommand(command) {
  if (command === 'password') {
    openPassword()
    return
  }
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.logout()
  router.replace('/login')
}

/* ---------------- 修改密码 ---------------- */
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function openPassword() {
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  pwdVisible.value = true
  await nextTick()
  pwdFormRef.value?.clearValidate()
}

async function submitPassword() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return

  pwdSubmitting.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    pwdVisible.value = false
    ElMessage.success('密码已修改，请使用新密码重新登录')
    await auth.logout()
    router.replace('/login')
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
  loadBadge()
  poller = setInterval(loadBadge, 60_000)
  // 审批 / 预约后立即刷新角标，无需等待轮询
  window.addEventListener('lab:pending-changed', loadBadge)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (poller) clearInterval(poller)
  window.removeEventListener('lab:pending-changed', loadBadge)
})
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">
          <el-icon :size="21"><Monitor /></el-icon>
          <span class="brand-pulse" />
        </div>
        <div class="brand-text">
          <strong>实验室预约</strong>
          <span>LAB · BOOKING</span>
        </div>
      </div>

      <nav class="nav">
        <div v-for="group in groupedMenus" :key="group.name" class="nav-group">
          <div class="nav-caption">{{ group.name }}</div>
          <el-menu
            :default-active="route.path"
            router
            class="side-menu"
            background-color="transparent"
            text-color="#93a9cc"
            active-text-color="#ffffff"
          >
            <el-menu-item
              v-for="item in group.items"
              :key="item.path"
              :index="item.path"
              :title="item.title"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
              <span v-if="item.path === '/approvals' && pendingCount" class="menu-badge">
                {{ pendingCount > 99 ? '99+' : pendingCount }}
              </span>
            </el-menu-item>
          </el-menu>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="sys-line">
          <span class="status-dot" />
          <span>SYS ONLINE</span>
          <span class="sys-port">:8091</span>
        </div>
        <div class="version">V1.0.0 · PHASE 02</div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <div class="crumb">
            <span class="crumb-root">LAB</span>
            <span class="crumb-sep">/</span>
            <span>{{ pageTitle }}</span>
          </div>
          <h1>{{ pageTitle }}</h1>
        </div>

        <div class="topbar-right">
          <div class="clock">
            <span class="clock-time mono">{{ timeText }}</span>
            <span class="clock-date">{{ dateText }}</span>
          </div>
          <div class="online-pill">
            <span class="status-dot" />
            ONLINE
          </div>
          <el-tag :type="roleTagMap[auth.role] || 'info'" effect="light" round class="role-tag">
            {{ roleTextMap[auth.role] || auth.role }}
          </el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-chip">
              <el-avatar :size="34" class="avatar">
                {{ (auth.user?.realName || auth.user?.username || '?').slice(0, 1) }}
              </el-avatar>
              <div class="user-meta">
                <strong>{{ auth.user?.realName || auth.user?.username }}</strong>
                <span>@{{ auth.user?.username }}</span>
              </div>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  {{ auth.user?.realName }} · {{ roleTextMap[auth.role] }}
                </el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>

    <el-dialog
      v-model="pwdVisible"
      title="修改登录密码"
      width="440px"
      :close-on-click-modal="false"
      @closed="pwdFormRef?.resetFields()"
    >
      <div class="pwd-tip">
        <el-icon><Lock /></el-icon>
        <span>修改成功后当前登录状态会失效，需要使用新密码重新登录。</span>
      </div>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
            @keyup.enter="submitPassword"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

/* ---------------- 侧边栏 ---------------- */
.sidebar {
  width: 236px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, rgba(8, 18, 34, 0.96), rgba(4, 10, 20, 0.98));
  border-right: 1px solid rgba(74, 130, 210, 0.16);
  color: #fff;
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 20;
  backdrop-filter: blur(12px);
}

.sidebar::after {
  content: '';
  position: absolute;
  top: 0;
  right: -1px;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, transparent, rgba(47, 140, 255, 0.5), transparent);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px 20px;
}

.brand-logo {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  color: #d9ecff;
  background: linear-gradient(135deg, rgba(47, 140, 255, 0.9), rgba(34, 211, 238, 0.55));
  box-shadow: 0 0 24px rgba(47, 140, 255, 0.45), inset 0 0 12px rgba(255, 255, 255, 0.2);
}

.brand-pulse {
  position: absolute;
  right: -3px;
  top: -3px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 10px #34d399;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.45;
    transform: scale(0.8);
  }
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-text strong {
  font-size: 15px;
  letter-spacing: 1px;
}

.brand-text span {
  font-size: 9px;
  color: #5f7799;
  letter-spacing: 3px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 4px 12px 12px;
}

.nav-group {
  margin-bottom: 14px;
}

.nav-caption {
  padding: 8px 12px 6px;
  font-size: 9px;
  letter-spacing: 2.4px;
  color: #3f5675;
}

.side-menu {
  border-right: none;
}

.side-menu :deep(.el-menu-item) {
  height: 42px;
  margin-bottom: 3px;
  border-radius: 9px;
  font-size: 13.5px;
  position: relative;
  transition: all 0.22s;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(47, 140, 255, 0.1) !important;
  color: #cfe8ff;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(47, 140, 255, 0.28), rgba(47, 140, 255, 0.06)) !important;
  color: #fff !important;
  box-shadow: inset 0 0 0 1px rgba(90, 160, 255, 0.22), 0 8px 22px rgba(47, 140, 255, 0.12);
}

.side-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 11px;
  bottom: 11px;
  width: 3px;
  border-radius: 2px;
  background: linear-gradient(180deg, #22d3ee, #2f8cff);
  box-shadow: 0 0 12px #2f8cff;
}

.side-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #7cc4ff;
  filter: drop-shadow(0 0 6px rgba(80, 170, 255, 0.7));
}

.menu-badge {
  margin-left: auto;
  min-width: 20px;
  height: 18px;
  padding: 0 6px;
  border-radius: 9px;
  background: rgba(251, 113, 133, 0.2);
  border: 1px solid rgba(251, 113, 133, 0.4);
  color: #fda4af;
  font-size: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: Consolas, monospace;
}

.sidebar-footer {
  padding: 16px 20px 22px;
  border-top: 1px solid rgba(74, 130, 210, 0.14);
}

.sys-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  letter-spacing: 1.6px;
  color: #7d91b3;
}

.sys-port {
  color: #4f6485;
  letter-spacing: 0.5px;
}

.version {
  margin-top: 8px;
  font-size: 9px;
  letter-spacing: 2px;
  color: #344a68;
  font-family: Consolas, monospace;
}

/* ---------------- 主区域 ---------------- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 72px;
  padding: 0 26px;
  background: rgba(6, 13, 25, 0.72);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(74, 130, 210, 0.16);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 15;
}

.crumb {
  font-size: 10px;
  letter-spacing: 1.8px;
  color: #4f6485;
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 7px;
}

.crumb-root {
  color: #2f8cff;
}

.crumb-sep {
  color: #2a3d58;
}

.topbar h1 {
  margin: 4px 0 0;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.6px;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.clock-time {
  font-size: 16px;
  color: #cfe8ff;
  text-shadow: 0 0 14px rgba(80, 170, 255, 0.4);
}

.clock-date {
  font-size: 10px;
  color: #5f7799;
  letter-spacing: 0.5px;
}

.online-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: 20px;
  border: 1px solid rgba(52, 211, 153, 0.3);
  background: rgba(52, 211, 153, 0.08);
  color: #5eead4;
  font-size: 9px;
  letter-spacing: 1.6px;
}

.online-pill .status-dot {
  width: 6px;
  height: 6px;
  margin-right: 0;
  background: #34d399;
  box-shadow: 0 0 8px #34d399;
  animation: pulse 2s infinite;
}

.role-tag {
  font-size: 11px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 9px;
  border-radius: 11px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.user-chip:hover {
  background: rgba(47, 140, 255, 0.08);
  border-color: rgba(74, 130, 210, 0.24);
}

.avatar {
  background: linear-gradient(135deg, #2f8cff, #8b5cf6);
  font-size: 14px;
  box-shadow: 0 0 16px rgba(47, 140, 255, 0.4);
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-meta strong {
  font-size: 12.5px;
  color: #dcebff;
}

.user-meta span {
  font-size: 10px;
  color: #5f7799;
}

.caret {
  color: #5f7799;
  font-size: 12px;
}

.content {
  padding: 22px 26px 36px;
  flex: 1;
}

@media (max-width: 1100px) {
  .clock,
  .online-pill {
    display: none;
  }
}

@media (max-width: 860px) {
  .sidebar {
    width: 72px;
  }

  .brand-text,
  .nav-caption,
  .sidebar-footer,
  .side-menu :deep(.el-menu-item span:not(.menu-badge)) {
    display: none;
  }

  .side-menu :deep(.el-menu-item) {
    position: relative;
  }

  .menu-badge {
    position: absolute;
    top: 6px;
    right: 6px;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    margin-left: 0;
    font-size: 9px;
  }

  .brand {
    justify-content: center;
    padding: 20px 0;
  }

  .side-menu :deep(.el-menu-item) {
    justify-content: center;
    padding: 0 !important;
  }

  .content {
    padding: 16px;
  }

  .topbar {
    padding: 0 16px;
  }

  .user-meta,
  .role-tag {
    display: none;
  }
}

.pwd-tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  margin-bottom: 16px;
  border-radius: 8px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #8ba2c4;
  background: rgba(47, 140, 255, 0.06);
  border: 1px solid rgba(74, 130, 210, 0.16);
}

.pwd-tip .el-icon {
  color: #7cc4ff;
  margin-top: 2px;
}
</style>
