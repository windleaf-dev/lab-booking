<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import echarts from '../utils/echarts'
import { ElMessage } from 'element-plus'
import {
  fetchOverview,
  fetchBookingTrend,
  fetchStatusDistribution,
  fetchTopDevices,
  fetchDeviceByCategory,
} from '../api/stats'
import { fetchPending } from '../api/booking'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(true)
const refreshing = ref(false)
const overview = ref({})
const pendingList = ref([])

const trendRef = ref()
const statusRef = ref()
const topRef = ref()
const categoryRef = ref()
const charts = []

const statusMeta = {
  PENDING: { label: '待审批', color: '#fbbf24' },
  APPROVED: { label: '已通过', color: '#2f8cff' },
  REJECTED: { label: '已驳回', color: '#fb7185' },
  CANCELLED: { label: '已取消', color: '#64748b' },
  COMPLETED: { label: '已完成', color: '#34d399' },
  VIOLATED: { label: '违约', color: '#f43f5e' },
}

const stats = computed(() => [
  {
    label: '设备总数',
    value: overview.value.deviceCount ?? 0,
    suffix: '台',
    icon: 'Monitor',
    color: '#2f8cff',
    glow: 'rgba(47,140,255,.35)',
    hint: `可用 ${overview.value.availableCount ?? 0} · 维修 ${overview.value.maintenanceCount ?? 0}`,
    bars: [42, 58, 50, 72, 64, 88, 76],
  },
  {
    label: '今日预约',
    value: overview.value.todayCount ?? 0,
    suffix: '次',
    icon: 'Calendar',
    color: '#22d3ee',
    glow: 'rgba(34,211,238,.3)',
    hint: `累计 ${overview.value.bookingCount ?? 0} 次预约`,
    bars: [30, 46, 38, 62, 55, 70, 84],
  },
  {
    label: '待审批',
    value: overview.value.pendingCount ?? 0,
    suffix: '单',
    icon: 'Checked',
    color: '#fbbf24',
    glow: 'rgba(251,191,36,.28)',
    hint: auth.isStaff ? '等待你的处理' : '等待教师审批',
    bars: [20, 34, 28, 44, 36, 52, 47],
  },
  {
    label: '违约记录',
    value: overview.value.violatedCount ?? 0,
    suffix: '次',
    icon: 'Warning',
    color: '#fb7185',
    glow: 'rgba(251,113,133,.28)',
    hint: `已完成 ${overview.value.completedCount ?? 0} 次`,
    bars: [12, 8, 16, 10, 14, 9, 7],
  },
])

const availableRate = computed(() => overview.value.availableRate ?? 0)

const todayLabel = computed(() =>
  new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }),
)

function initChart(el, option) {
  if (!el) return null
  const instance = echarts.init(el)
  instance.setOption(option)
  charts.push(instance)
  return instance
}

const axisStyle = {
  axisLine: { lineStyle: { color: 'rgba(90, 130, 190, 0.25)' } },
  axisTick: { show: false },
  axisLabel: { color: '#6f86a8', fontSize: 11 },
}

function renderTrend(rows) {
  initChart(trendRef.value, {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8, 17, 32, 0.95)',
      borderColor: 'rgba(90, 160, 255, 0.3)',
      textStyle: { color: '#d7e5fb', fontSize: 12 },
    },
    grid: { left: 10, right: 18, top: 28, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: rows.map((row) => row.label),
      ...axisStyle,
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(90, 130, 190, 0.1)', type: 'dashed' } },
      axisLabel: { color: '#5f7799', fontSize: 10 },
    },
    series: [
      {
        name: '预约数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: rows.map((row) => row.value),
        lineStyle: {
          width: 2.5,
          color: '#22d3ee',
          shadowBlur: 18,
          shadowColor: 'rgba(34, 211, 238, 0.55)',
        },
        itemStyle: { color: '#22d3ee', borderColor: '#04101f', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(34, 211, 238, 0.32)' },
            { offset: 1, color: 'rgba(34, 211, 238, 0)' },
          ]),
        },
      },
    ],
  })
}

function renderStatus(rows) {
  const data = rows
    .map((row) => ({
      name: statusMeta[row.name]?.label || row.name,
      value: Number(row.value || 0),
      itemStyle: { color: statusMeta[row.name]?.color || '#64748b' },
    }))
    .filter((row) => row.value > 0)

  initChart(statusRef.value, {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 17, 32, 0.95)',
      borderColor: 'rgba(90, 160, 255, 0.3)',
      textStyle: { color: '#d7e5fb', fontSize: 12 },
      formatter: '{b}<br/>数量：{c}（{d}%）',
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 7,
      itemHeight: 7,
      textStyle: { color: '#7d91b3', fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['50%', '43%'],
        itemStyle: { borderColor: '#08111f', borderWidth: 3, borderRadius: 5 },
        label: { show: false },
        data,
      },
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '36%',
        style: {
          text: String(overview.value.bookingCount ?? 0),
          fill: '#dcebff',
          fontSize: 26,
          fontWeight: 700,
          textAlign: 'center',
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '46%',
        style: { text: 'TOTAL', fill: '#4f6485', fontSize: 9, textAlign: 'center' },
      },
    ],
  })
}

function renderTop(rows) {
  const data = [...rows].reverse()
  initChart(topRef.value, {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(8, 17, 32, 0.95)',
      borderColor: 'rgba(90, 160, 255, 0.3)',
      textStyle: { color: '#d7e5fb', fontSize: 12 },
    },
    grid: { left: 8, right: 24, top: 14, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(90, 130, 190, 0.1)', type: 'dashed' } },
      axisLabel: { color: '#5f7799', fontSize: 10 },
    },
    yAxis: {
      type: 'category',
      data: data.map((row) => row.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#9fb6d8', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        data: data.map((row) => row.value),
        barWidth: 12,
        itemStyle: {
          borderRadius: 6,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(47, 140, 255, 0.35)' },
            { offset: 1, color: '#22d3ee' },
          ]),
        },
      },
    ],
  })
}

function renderCategory(rows) {
  initChart(categoryRef.value, {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(8, 17, 32, 0.95)',
      borderColor: 'rgba(90, 160, 255, 0.3)',
      textStyle: { color: '#d7e5fb', fontSize: 12 },
      formatter: '{b}<br/>{c} 台（{d}%）',
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 7,
      itemHeight: 7,
      textStyle: { color: '#7d91b3', fontSize: 11 },
    },
    color: ['#2f8cff', '#22d3ee', '#a78bfa', '#34d399', '#fbbf24', '#fb7185'],
    series: [
      {
        type: 'pie',
        radius: ['40%', '66%'],
        center: ['50%', '43%'],
        itemStyle: { borderColor: '#08111f', borderWidth: 3, borderRadius: 5 },
        label: { show: false },
        data: rows.map((row) => ({ name: row.name, value: row.value })),
      },
    ],
  })
}

async function loadData() {
  try {
    const [overviewData, trend, status, top, category] = await Promise.all([
      fetchOverview(),
      fetchBookingTrend(7),
      fetchStatusDistribution(),
      fetchTopDevices(),
      fetchDeviceByCategory(),
    ])
    overview.value = overviewData || {}
    charts.forEach((chart) => chart.dispose())
    charts.length = 0
    await nextTick()
    renderTrend(trend || [])
    renderStatus(status || [])
    renderTop(top || [])
    renderCategory(category || [])

    if (auth.isStaff) {
      const pending = await fetchPending({ page: 1, size: 4 })
      pendingList.value = pending?.records || []
    }
  } finally {
    loading.value = false
  }
}

async function refresh() {
  refreshing.value = true
  try {
    await loadData()
    ElMessage.success('数据已刷新')
  } finally {
    refreshing.value = false
  }
}

function handleResize() {
  charts.forEach((chart) => chart.resize())
}

// WebSocket 推送的预约变化：400ms 防抖，避免短时间多条消息重复拉取
let realtimeTimer = null
function handleRealtime() {
  if (realtimeTimer) clearTimeout(realtimeTimer)
  realtimeTimer = setTimeout(() => {
    loadData()
  }, 400)
}

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', handleResize)
  window.addEventListener('lab:booking-changed', handleRealtime)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('lab:booking-changed', handleRealtime)
  if (realtimeTimer) clearTimeout(realtimeTimer)
  charts.forEach((chart) => chart.dispose())
  charts.length = 0
})
</script>

<template>
  <div v-loading="loading" class="page dashboard">
    <!-- 指挥中心横幅 -->
    <section class="command-hero">
      <div class="hero-copy">
        <div class="hero-tag">
          <span class="status-dot" />
          COMMAND CENTER · {{ todayLabel }}
        </div>
        <h2>
          你好，{{ auth.user?.realName || auth.user?.username }}
          <span class="hero-wave">/</span>
        </h2>
        <p>
          实验室资源运行正常。当前设备可用率
          <strong class="accent">{{ availableRate }}%</strong>，
          待处理审批 <strong class="accent">{{ overview.pendingCount ?? 0 }}</strong> 单。
        </p>
        <div class="hero-actions">
          <el-button type="primary" @click="router.push('/booking')">
            <el-icon><Calendar /></el-icon>
            <span>发起预约</span>
          </el-button>
          <el-button plain :loading="refreshing" @click="refresh">
            <el-icon><Refresh /></el-icon>
            <span>刷新数据</span>
          </el-button>
          <el-button v-if="auth.isStaff" text @click="router.push('/approvals')">
            进入审批中心
            <el-icon><Right /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="hero-radar">
        <div class="radar-ring ring-a" />
        <div class="radar-ring ring-b" />
        <div class="radar-ring ring-c" />
        <div class="radar-sweep" />
        <div class="radar-core">
          <span class="radar-value mono">{{ overview.todayCount ?? 0 }}</span>
          <span class="radar-label">TODAY</span>
        </div>
        <span class="radar-node node-1" />
        <span class="radar-node node-2" />
        <span class="radar-node node-3" />
      </div>
    </section>

    <!-- 指标卡片 -->
    <div class="stat-grid">
      <div
        v-for="item in stats"
        :key="item.label"
        class="stat-card"
        :style="{ '--card-color': item.color, '--card-glow': item.glow }"
      >
        <div class="stat-top">
          <div class="stat-icon">
            <el-icon :size="19"><component :is="item.icon" /></el-icon>
          </div>
          <span class="hud-label">{{ item.label }}</span>
        </div>
        <div class="stat-value mono">
          {{ item.value }}<small>{{ item.suffix }}</small>
        </div>
        <div class="stat-bottom">
          <span class="stat-hint">{{ item.hint }}</span>
          <div class="mini-bars">
            <i v-for="(bar, index) in item.bars" :key="index" :style="{ height: bar + '%' }" />
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-grid">
      <section class="panel trend-panel">
        <div class="panel-head">
          <div>
            <h3>预约趋势</h3>
            <p>最近 7 天有效预约数量（不含驳回与取消）</p>
          </div>
          <span class="panel-chip mono">7D</span>
        </div>
        <div ref="trendRef" class="chart trend-chart" />
      </section>

      <section class="panel status-panel">
        <div class="panel-head">
          <div>
            <h3>预约状态分布</h3>
            <p>全生命周期状态占比</p>
          </div>
        </div>
        <div ref="statusRef" class="chart status-chart" />
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>设备热度 TOP 5</h3>
            <p>按累计预约次数排序</p>
          </div>
        </div>
        <div ref="topRef" class="chart small-chart" />
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>设备分类构成</h3>
            <p>台账分类分布</p>
          </div>
        </div>
        <div ref="categoryRef" class="chart small-chart" />
      </section>

      <!-- 待审批 / 快捷入口 -->
      <section class="panel pending-panel">
        <div class="panel-head">
          <div>
            <h3>{{ auth.isStaff ? '待审批队列' : '快捷操作' }}</h3>
            <p>{{ auth.isStaff ? '按提交时间等待处理' : '常用功能入口' }}</p>
          </div>
          <el-tag v-if="auth.isStaff" type="warning" effect="light" round>
            {{ overview.pendingCount ?? 0 }} 待处理
          </el-tag>
        </div>

        <template v-if="auth.isStaff">
          <div v-if="pendingList.length" class="pending-list">
            <div v-for="item in pendingList" :key="item.id" class="pending-item">
              <div class="pending-main">
                <strong>{{ item.deviceName }}</strong>
                <span>
                  {{ item.realName }} · {{ item.bookingDate }} {{ item.slotName }}
                </span>
              </div>
              <span class="pending-purpose">{{ item.purpose || '未填写用途' }}</span>
            </div>
          </div>
          <el-empty v-else :image-size="54" description="暂无待审批预约" />
          <el-button class="pending-btn" type="primary" plain @click="router.push('/approvals')">
            查看全部审批
          </el-button>
        </template>

        <div v-else class="quick-grid">
          <button class="quick-item" @click="router.push('/booking')">
            <el-icon><Calendar /></el-icon>
            <strong>预约设备</strong>
            <span>选择日期与时段</span>
          </button>
          <button class="quick-item" @click="router.push('/my-bookings')">
            <el-icon><Tickets /></el-icon>
            <strong>我的预约</strong>
            <span>签到 / 取消</span>
          </button>
          <button class="quick-item" @click="router.push('/analytics')">
            <el-icon><TrendCharts /></el-icon>
            <strong>使用分析</strong>
            <span>趋势与热度</span>
          </button>
          <button class="quick-item" @click="router.push('/devices')">
            <el-icon><Monitor /></el-icon>
            <strong>设备台账</strong>
            <span>查看设备状态</span>
          </button>
        </div>
      </section>
    </div>

    <!-- 模块状态条 -->
    <section class="panel module-strip">
      <div class="module-item">
        <span class="hud-label">AUTH</span>
        <strong>JWT + RBAC</strong>
        <span class="module-state ok"><span class="status-dot" />运行中</span>
      </div>
      <div class="module-item">
        <span class="hud-label">BOOKING</span>
        <strong>时段冲突校验</strong>
        <span class="module-state ok"><span class="status-dot" />已启用</span>
      </div>
      <div class="module-item">
        <span class="hud-label">APPROVAL</span>
        <strong>审批 / 签到 / 违约</strong>
        <span class="module-state ok"><span class="status-dot" />已启用</span>
      </div>
      <div class="module-item">
        <span class="hud-label">SCHEDULER</span>
        <strong>状态自动流转</strong>
        <span class="module-state ok"><span class="status-dot" />60s / 次</span>
      </div>
      <div class="module-item">
        <span class="hud-label">NEXT</span>
        <strong>Redis · WebSocket</strong>
        <span class="module-state wait">规划中</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ---------------- 指挥中心 ---------------- */
.command-hero {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 30px;
  padding: 26px 30px;
  border-radius: 16px;
  border: 1px solid rgba(90, 160, 255, 0.24);
  background:
    linear-gradient(120deg, rgba(14, 30, 55, 0.9), rgba(7, 15, 29, 0.9)),
    radial-gradient(600px 260px at 85% 50%, rgba(34, 211, 238, 0.12), transparent 70%);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(150, 200, 255, 0.06);
  overflow: hidden;
}

.command-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(90, 150, 230, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(90, 150, 230, 0.06) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(90deg, transparent 40%, #000);
  pointer-events: none;
}

.hero-copy {
  position: relative;
  z-index: 2;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  letter-spacing: 2.4px;
  color: #5f7799;
}

.hero-tag .status-dot {
  width: 6px;
  height: 6px;
  background: #34d399;
  box-shadow: 0 0 8px #34d399;
  animation: blink 2s infinite;
}

@keyframes blink {
  50% {
    opacity: 0.35;
  }
}

.hero-copy h2 {
  margin: 14px 0 10px;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: 0.8px;
  color: #eaf4ff;
}

.hero-wave {
  color: #2f8cff;
  margin-left: 6px;
  text-shadow: 0 0 20px rgba(47, 140, 255, 0.7);
}

.hero-copy p {
  margin: 0;
  font-size: 13px;
  color: #8ba2c4;
  line-height: 1.8;
}

.accent {
  color: #7cc4ff;
  font-family: Consolas, monospace;
  text-shadow: 0 0 14px rgba(80, 170, 255, 0.4);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.hero-actions .el-button {
  margin-left: 0;
}

/* 雷达 */
.hero-radar {
  position: relative;
  width: 190px;
  height: 190px;
  flex-shrink: 0;
  margin-right: 24px;
}

.radar-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(90, 160, 255, 0.26);
}

.ring-a {
  inset: 0;
}

.ring-b {
  inset: 28px;
  border-color: rgba(90, 160, 255, 0.18);
}

.ring-c {
  inset: 56px;
  border-color: rgba(34, 211, 238, 0.28);
  box-shadow: 0 0 40px rgba(34, 211, 238, 0.12) inset;
}

.radar-sweep {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(from 0deg, rgba(34, 211, 238, 0.32), transparent 22%);
  animation: sweep 5.5s linear infinite;
  mask-image: radial-gradient(circle, transparent 34%, #000 35%);
}

@keyframes sweep {
  to {
    transform: rotate(360deg);
  }
}

.radar-core {
  position: absolute;
  inset: 74px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.16), rgba(4, 12, 24, 0.9));
  border: 1px solid rgba(34, 211, 238, 0.4);
  box-shadow: 0 0 30px rgba(34, 211, 238, 0.25);
}

.radar-value {
  font-size: 22px;
  color: #d9f7ff;
  text-shadow: 0 0 16px rgba(34, 211, 238, 0.7);
}

.radar-label {
  font-size: 8px;
  letter-spacing: 2px;
  color: #4f7aa8;
}

.radar-node {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22d3ee;
  box-shadow: 0 0 10px #22d3ee;
}

.node-1 {
  top: 24px;
  left: 46px;
}

.node-2 {
  bottom: 34px;
  right: 28px;
  background: #a78bfa;
  box-shadow: 0 0 10px #a78bfa;
}

.node-3 {
  top: 90px;
  right: 12px;
  background: #fbbf24;
  box-shadow: 0 0 10px #fbbf24;
}

/* ---------------- 指标卡 ---------------- */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  position: relative;
  padding: 16px 18px 14px;
  border-radius: 13px;
  background: linear-gradient(165deg, rgba(15, 29, 52, 0.92), rgba(7, 15, 29, 0.88));
  border: 1px solid var(--lab-border);
  box-shadow: 0 14px 40px rgba(0, 0, 0, 0.32);
  overflow: hidden;
  transition: transform 0.22s, border-color 0.22s, box-shadow 0.22s;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--card-color), transparent);
  opacity: 0.7;
}

.stat-card::after {
  content: '';
  position: absolute;
  width: 120px;
  height: 120px;
  right: -50px;
  bottom: -60px;
  border-radius: 50%;
  background: var(--card-glow);
  filter: blur(28px);
  opacity: 0.55;
}

.stat-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--card-color) 45%, transparent);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.42), 0 0 30px var(--card-glow);
}

.stat-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: var(--card-color);
  background: color-mix(in srgb, var(--card-color) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--card-color) 26%, transparent);
  box-shadow: 0 0 18px var(--card-glow);
}

.stat-value {
  margin: 12px 0 8px;
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
  color: #edf6ff;
  text-shadow: 0 0 22px var(--card-glow);
}

.stat-value small {
  font-size: 12px;
  font-weight: 400;
  color: #5f7799;
  margin-left: 5px;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.stat-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
}

.stat-hint {
  font-size: 10.5px;
  color: #5f7799;
}

.mini-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 22px;
  opacity: 0.65;
}

.mini-bars i {
  width: 3px;
  border-radius: 1px;
  background: linear-gradient(180deg, var(--card-color), transparent);
}

/* ---------------- 图表 ---------------- */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 16px;
}

.trend-panel {
  grid-column: span 8;
}

.status-panel {
  grid-column: span 4;
}

.chart-grid > .panel:nth-child(3),
.chart-grid > .panel:nth-child(4),
.pending-panel {
  grid-column: span 4;
}

.chart {
  width: 100%;
}

.trend-chart {
  height: 260px;
}

.status-chart,
.small-chart {
  height: 240px;
}

.panel-chip {
  font-size: 10px;
  letter-spacing: 1.5px;
  color: #5eead4;
  border: 1px solid rgba(52, 211, 153, 0.3);
  background: rgba(52, 211, 153, 0.07);
  padding: 3px 9px;
  border-radius: 5px;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.pending-item {
  padding: 10px 12px;
  border-radius: 9px;
  border: 1px solid rgba(74, 130, 210, 0.16);
  background: rgba(10, 22, 40, 0.55);
}

.pending-main {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.pending-main strong {
  font-size: 12.5px;
  color: #d7e5fb;
}

.pending-main span {
  font-size: 10.5px;
  color: #5f7799;
}

.pending-purpose {
  display: block;
  margin-top: 5px;
  font-size: 11px;
  color: #7d91b3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-btn {
  width: 100%;
  margin-top: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid rgba(74, 130, 210, 0.16);
  background: rgba(10, 22, 40, 0.55);
  color: inherit;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.quick-item:hover {
  border-color: rgba(90, 160, 255, 0.45);
  background: rgba(47, 140, 255, 0.08);
  transform: translateY(-2px);
}

.quick-item .el-icon {
  color: #7cc4ff;
}

.quick-item strong {
  font-size: 12.5px;
  color: #d7e5fb;
}

.quick-item span {
  font-size: 10.5px;
  color: #5f7799;
}

/* ---------------- 模块状态 ---------------- */
.module-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 18px;
  padding: 16px 20px;
}

.module-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 12px;
  border-left: 1px solid rgba(74, 130, 210, 0.18);
}

.module-item:first-child {
  border-left: none;
  padding-left: 0;
}

.module-item strong {
  font-size: 12px;
  color: #cfe0f7;
  font-weight: 500;
}

.module-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
}

.module-state.ok {
  color: #5eead4;
}

.module-state.ok .status-dot {
  width: 5px;
  height: 5px;
  background: #34d399;
  box-shadow: 0 0 6px #34d399;
}

.module-state.wait {
  color: #5f7799;
}

@media (max-width: 1280px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-panel,
  .status-panel,
  .chart-grid > .panel:nth-child(3),
  .chart-grid > .panel:nth-child(4),
  .pending-panel {
    grid-column: span 6;
  }

  .module-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .command-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-radar {
    display: none;
  }

  .trend-panel,
  .status-panel,
  .chart-grid > .panel:nth-child(3),
  .chart-grid > .panel:nth-child(4),
  .pending-panel {
    grid-column: span 12;
  }

  .module-strip {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
