<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import echarts from '../utils/echarts'
import {
  fetchOverview,
  fetchBookingTrend,
  fetchStatusDistribution,
  fetchTopDevices,
  fetchDeviceByCategory,
} from '../api/stats'

const loading = ref(true)
const days = ref(7)
const overview = ref({})
const trend = ref([])
const statusRows = ref([])
const topRows = ref([])
const categoryRows = ref([])

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

const completionRate = computed(() => {
  const total = overview.value.bookingCount || 0
  if (!total) return 0
  return Math.round(((overview.value.completedCount || 0) / total) * 100)
})

const violationRate = computed(() => {
  const valid = (overview.value.completedCount || 0) + (overview.value.violatedCount || 0)
  if (!valid) return 0
  return Math.round(((overview.value.violatedCount || 0) / valid) * 100)
})

const peak = computed(() => {
  if (!trend.value.length) return { label: '—', value: 0 }
  return trend.value.reduce((a, b) => (Number(b.value) > Number(a.value) ? b : a), trend.value[0])
})

const topDevice = computed(() => topRows.value[0] || { name: '暂无数据', value: 0 })

function initChart(el, option) {
  if (!el) return
  const chart = echarts.init(el)
  chart.setOption(option)
  charts.push(chart)
}

const axisCommon = {
  axisLine: { lineStyle: { color: 'rgba(90, 130, 190, 0.25)' } },
  axisTick: { show: false },
  axisLabel: { color: '#6f86a8', fontSize: 11 },
}

const tooltipCommon = {
  backgroundColor: 'rgba(8, 17, 32, 0.96)',
  borderColor: 'rgba(90, 160, 255, 0.3)',
  textStyle: { color: '#d7e5fb', fontSize: 12 },
}

function renderTrend() {
  initChart(trendRef.value, {
    tooltip: { trigger: 'axis', ...tooltipCommon },
    grid: { left: 12, right: 20, top: 30, bottom: 10, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: trend.value.map((r) => r.label), ...axisCommon },
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
        data: trend.value.map((r) => r.value),
        lineStyle: { width: 2.6, color: '#22d3ee', shadowBlur: 20, shadowColor: 'rgba(34,211,238,.55)' },
        itemStyle: { color: '#22d3ee', borderColor: '#04101f', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(34, 211, 238, 0.34)' },
            { offset: 1, color: 'rgba(34, 211, 238, 0)' },
          ]),
        },
      },
    ],
  })
}

function renderStatus() {
  initChart(statusRef.value, {
    tooltip: { trigger: 'item', ...tooltipCommon, formatter: '{b}<br/>{c} 单（{d}%）' },
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
        radius: ['48%', '70%'],
        center: ['50%', '42%'],
        itemStyle: { borderColor: '#08111f', borderWidth: 3, borderRadius: 5 },
        label: { show: false },
        data: statusRows.value.map((row) => ({
          name: statusMeta[row.name]?.label || row.name,
          value: row.value,
          itemStyle: { color: statusMeta[row.name]?.color || '#64748b' },
        })),
      },
    ],
  })
}

function renderTop() {
  const data = [...topRows.value].reverse()
  initChart(topRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipCommon },
    grid: { left: 10, right: 26, top: 14, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(90, 130, 190, 0.1)', type: 'dashed' } },
      axisLabel: { color: '#5f7799', fontSize: 10 },
    },
    yAxis: {
      type: 'category',
      data: data.map((r) => r.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#9fb6d8', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 13,
        data: data.map((r) => r.value),
        itemStyle: {
          borderRadius: 6,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(47, 140, 255, 0.3)' },
            { offset: 1, color: '#22d3ee' },
          ]),
        },
      },
    ],
  })
}

function renderCategory() {
  initChart(categoryRef.value, {
    tooltip: { trigger: 'item', ...tooltipCommon, formatter: '{b}<br/>{c} 台（{d}%）' },
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
        radius: ['42%', '68%'],
        center: ['50%', '43%'],
        itemStyle: { borderColor: '#08111f', borderWidth: 3, borderRadius: 5 },
        label: { show: false },
        data: categoryRows.value,
      },
    ],
  })
}

async function loadAll(silent = false) {
  if (!silent) loading.value = true
  try {
    const [ov, tr, st, tp, ct] = await Promise.all([
      fetchOverview(),
      fetchBookingTrend(days.value),
      fetchStatusDistribution(),
      fetchTopDevices(),
      fetchDeviceByCategory(),
    ])
    overview.value = ov || {}
    trend.value = tr || []
    statusRows.value = st || []
    topRows.value = tp || []
    categoryRows.value = ct || []

    charts.forEach((chart) => chart.dispose())
    charts.length = 0
    await nextTick()
    renderTrend()
    renderStatus()
    renderTop()
    renderCategory()
  } finally {
    loading.value = false
  }
}

async function changeDays(value) {
  days.value = value
  loading.value = true
  try {
    trend.value = (await fetchBookingTrend(value)) || []
    charts.forEach((chart) => chart.dispose())
    charts.length = 0
    await nextTick()
    renderTrend()
    renderStatus()
    renderTop()
    renderCategory()
  } finally {
    loading.value = false
  }
}

function handleResize() {
  charts.forEach((chart) => chart.resize())
}

// 实时事件不弹全屏 loading，静默刷新图表
let realtimeTimer = null
function handleRealtime() {
  if (realtimeTimer) clearTimeout(realtimeTimer)
  realtimeTimer = setTimeout(() => {
    loadAll(true)
  }, 500)
}

onMounted(async () => {
  await loadAll()
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
  <div v-loading="loading" class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">统计分析</h2>
        <p class="page-subtitle">从预约趋势、状态构成和设备热度观察实验室资源使用情况。</p>
      </div>
      <el-button-group>
        <el-button :type="days === 7 ? 'primary' : ''" @click="changeDays(7)">7 天</el-button>
        <el-button :type="days === 14 ? 'primary' : ''" @click="changeDays(14)">14 天</el-button>
        <el-button :type="days === 30 ? 'primary' : ''" @click="changeDays(30)">30 天</el-button>
      </el-button-group>
    </div>

    <!-- 关键指标 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <span class="hud-label">TOTAL BOOKINGS</span>
        <strong class="mono">{{ overview.bookingCount ?? 0 }}</strong>
        <span class="kpi-sub">累计预约总数</span>
      </div>
      <div class="kpi-card">
        <span class="hud-label">COMPLETION RATE</span>
        <strong class="mono accent-green">{{ completionRate }}%</strong>
        <span class="kpi-sub">已完成 / 全部预约</span>
      </div>
      <div class="kpi-card">
        <span class="hud-label">VIOLATION RATE</span>
        <strong class="mono accent-red">{{ violationRate }}%</strong>
        <span class="kpi-sub">违约 / 已结束预约</span>
      </div>
      <div class="kpi-card">
        <span class="hud-label">PEAK DAY</span>
        <strong class="mono accent-cyan">{{ peak.label }}</strong>
        <span class="kpi-sub">峰值 {{ peak.value }} 次预约</span>
      </div>
    </div>

    <!-- 趋势 -->
    <section class="panel">
      <div class="panel-head">
        <div>
          <h3>预约趋势</h3>
          <p>最近 {{ days }} 天有效预约数量</p>
        </div>
        <span class="panel-chip mono">{{ days }}D</span>
      </div>
      <div ref="trendRef" class="chart trend-chart" />
    </section>

    <div class="chart-row">
      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>状态分布</h3>
            <p>预约生命周期构成</p>
          </div>
        </div>
        <div ref="statusRef" class="chart" />
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>设备热度</h3>
            <p>累计预约次数 TOP 5</p>
          </div>
        </div>
        <div ref="topRef" class="chart" />
      </section>

      <section class="panel">
        <div class="panel-head">
          <div>
            <h3>分类构成</h3>
            <p>设备台账分类分布</p>
          </div>
        </div>
        <div ref="categoryRef" class="chart" />
      </section>
    </div>

    <!-- 洞察 -->
    <section class="panel insight-panel">
      <div class="panel-head">
        <div>
          <h3>数据洞察</h3>
          <p>基于当前数据自动生成</p>
        </div>
        <el-tag type="info" effect="plain">AUTO</el-tag>
      </div>
      <div class="insight-grid">
        <div class="insight-item">
          <el-icon><TrendCharts /></el-icon>
          <div>
            <strong>预约高峰</strong>
            <span>{{ peak.label }} 出现 {{ peak.value }} 次预约，可提前调配设备与管理员。</span>
          </div>
        </div>
        <div class="insight-item">
          <el-icon><Star /></el-icon>
          <div>
            <strong>最热门设备</strong>
            <span>{{ topDevice.name }} 累计被预约 {{ topDevice.value }} 次，建议优先维护。</span>
          </div>
        </div>
        <div class="insight-item">
          <el-icon><CircleCheck /></el-icon>
          <div>
            <strong>履约情况</strong>
            <span>完成率 {{ completionRate }}%，违约率 {{ violationRate }}%，整体履约状态良好。</span>
          </div>
        </div>
        <div class="insight-item">
          <el-icon><Monitor /></el-icon>
          <div>
            <strong>设备可用率</strong>
            <span>{{ overview.availableRate ?? 0 }}%，维修中 {{ overview.maintenanceCount ?? 0 }} 台。</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--lab-border);
  background: linear-gradient(160deg, rgba(15, 29, 52, 0.92), rgba(7, 15, 29, 0.88));
  overflow: hidden;
}

.kpi-card::after {
  content: '';
  position: absolute;
  right: -30px;
  bottom: -40px;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: rgba(47, 140, 255, 0.12);
  filter: blur(24px);
}

.kpi-card strong {
  font-size: 26px;
  color: #eaf4ff;
}

.kpi-sub {
  font-size: 10.5px;
  color: #5f7799;
}

.accent-green {
  color: #5eead4 !important;
  text-shadow: 0 0 14px rgba(52, 211, 153, 0.35);
}

.accent-red {
  color: #fda4af !important;
  text-shadow: 0 0 14px rgba(251, 113, 133, 0.35);
}

.accent-cyan {
  color: #67e8f9 !important;
  text-shadow: 0 0 14px rgba(34, 211, 238, 0.35);
}

.chart {
  height: 270px;
}

.trend-chart {
  height: 300px;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.chart-row > .panel {
  min-width: 0;
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

.insight-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.insight-item {
  display: flex;
  gap: 10px;
  padding: 13px;
  border-radius: 10px;
  border: 1px solid rgba(74, 130, 210, 0.16);
  background: rgba(10, 22, 40, 0.5);
}

.insight-item .el-icon {
  color: #7cc4ff;
  margin-top: 2px;
  flex-shrink: 0;
}

.insight-item strong {
  display: block;
  font-size: 12.5px;
  color: #dcebff;
  margin-bottom: 3px;
}

.insight-item span {
  font-size: 11px;
  line-height: 1.6;
  color: #6f86a8;
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-row {
    grid-template-columns: 1fr;
  }

  .insight-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
