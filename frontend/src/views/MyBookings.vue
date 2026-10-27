<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchMyBookings, cancelBooking, checkinBooking } from '../api/booking'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  status: '',
  page: 1,
  size: 10,
})

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'VIOLATED', label: '违约' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' },
]

const statusMeta = {
  PENDING: { label: '待审批', tag: 'warning' },
  APPROVED: { label: '已通过', tag: 'primary' },
  REJECTED: { label: '已驳回', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
  COMPLETED: { label: '已完成', tag: 'success' },
  VIOLATED: { label: '违约', tag: 'danger' },
}

const summary = computed(() => {
  const pending = list.value.filter((item) => item.status === 'PENDING').length
  const approved = list.value.filter((item) => item.status === 'APPROVED').length
  return { pending, approved, shown: list.value.length }
})

function dateTimeOf(row, time) {
  return new Date(`${row.bookingDate}T${String(time).slice(0, 8)}`)
}

function canCancel(row) {
  if (!['PENDING', 'APPROVED'].includes(row.status)) return false
  return dateTimeOf(row, row.startTime).getTime() > Date.now()
}

function checkinState(row) {
  if (row.status !== 'APPROVED') return { ok: false, reason: '' }
  if (row.checkinAt) return { ok: false, reason: '已签到' }
  const start = dateTimeOf(row, row.startTime)
  const end = dateTimeOf(row, row.endTime)
  const now = Date.now()
  if (now < start.getTime() - 30 * 60 * 1000) {
    return { ok: false, reason: '开始前 30 分钟开放签到' }
  }
  if (now > end.getTime()) return { ok: false, reason: '时段已结束' }
  return { ok: true, reason: '' }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    const data = await fetchMyBookings({
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    })
    list.value = data?.records || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function changeStatus(status) {
  query.status = status
  query.page = 1
  load()
}

function handlePage(page) {
  query.page = page
  load()
}

// 审批 / 自动流转结果通过 WebSocket 推来后，列表实时更新
let realtimeTimer = null
function handleRealtime() {
  if (realtimeTimer) clearTimeout(realtimeTimer)
  realtimeTimer = setTimeout(() => {
    load()
  }, 400)
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消「${row.deviceName}」${row.bookingDate} ${row.slotName} 的预约吗？`,
      '取消预约',
      { confirmButtonText: '取消预约', cancelButtonText: '保留', type: 'warning' },
    )
  } catch {
    return
  }
  await cancelBooking(row.id)
  ElMessage.success('预约已取消')
  if (row.status === 'PENDING') window.dispatchEvent(new Event('lab:pending-changed'))
  load()
}

async function handleCheckin(row) {
  try {
    await ElMessageBox.confirm(
      `确认已到达「${row.deviceName}」并完成签到？`,
      '签到确认',
      { confirmButtonText: '确认签到', cancelButtonText: '再等等', type: 'info' },
    )
  } catch {
    return
  }
  await checkinBooking(row.id)
  ElMessage.success('签到成功，祝你实验顺利')
  load()
}

onMounted(() => {
  load()
  window.addEventListener('lab:booking-changed', handleRealtime)
})

onBeforeUnmount(() => {
  window.removeEventListener('lab:booking-changed', handleRealtime)
  if (realtimeTimer) clearTimeout(realtimeTimer)
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">我的预约</h2>
        <p class="page-subtitle">查看预约状态、签到与取消。逾期未签到将由系统自动判定违约。</p>
      </div>
      <el-button type="primary" @click="router.push('/booking')">
        <el-icon><Plus /></el-icon>
        <span>新建预约</span>
      </el-button>
    </div>

    <section class="panel">
      <div class="status-tabs">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          type="button"
          class="status-tab"
          :class="{ active: query.status === option.value }"
          @click="changeStatus(option.value)"
        >
          {{ option.label }}
        </button>
        <span class="tab-summary">
          本页 {{ summary.shown }} 条 · 待审批 {{ summary.pending }} · 已通过 {{ summary.approved }}
        </span>
      </div>

      <el-table v-loading="loading" :data="list" stripe empty-text="暂无预约记录">
        <el-table-column label="设备" min-width="180">
          <template #default="{ row }">
            <div class="device-cell">
              <strong>{{ row.deviceName }}</strong>
              <span class="mono dim">{{ row.deviceCode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="日期 / 时段" width="190">
          <template #default="{ row }">
            <div class="time-cell">
              <span class="mono">{{ row.bookingDate }}</span>
              <span class="dim">{{ row.slotName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.purpose || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta[row.status]?.tag || 'info'" effect="light">
              {{ statusMeta[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批 / 签到" min-width="200">
          <template #default="{ row }">
            <div class="audit-cell">
              <span v-if="row.approverName">审批人：{{ row.approverName }}</span>
              <span v-if="row.approveRemark" class="dim">备注：{{ row.approveRemark }}</span>
              <span v-if="row.checkinAt" class="ok-text">签到：{{ formatTime(row.checkinAt) }}</span>
              <span v-if="!row.approverName && !row.checkinAt" class="dim">—</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'APPROVED'"
              link
              type="success"
              :disabled="!checkinState(row).ok"
              :title="checkinState(row).reason"
              @click="handleCheckin(row)"
            >
              签到
            </el-button>
            <el-button v-if="canCancel(row)" link type="danger" @click="handleCancel(row)">
              取消
            </el-button>
            <span v-if="!canCancel(row) && row.status !== 'APPROVED'" class="dim">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="handlePage"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.status-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.status-tab {
  padding: 6px 14px;
  border-radius: 7px;
  border: 1px solid rgba(74, 130, 210, 0.18);
  background: rgba(10, 22, 40, 0.55);
  color: #8ba2c4;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.18s;
}

.status-tab:hover {
  border-color: rgba(90, 160, 255, 0.4);
  color: #cfe0f7;
}

.status-tab.active {
  color: #dcebff;
  border-color: rgba(47, 140, 255, 0.5);
  background: rgba(47, 140, 255, 0.14);
  box-shadow: 0 0 16px rgba(47, 140, 255, 0.15);
}

.tab-summary {
  margin-left: auto;
  font-size: 11px;
  color: #5f7799;
}

.device-cell,
.time-cell,
.audit-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.device-cell strong {
  font-size: 13px;
  color: #dcebff;
  font-weight: 500;
}

.time-cell .mono {
  color: #cfe0f7;
  font-size: 12px;
}

.audit-cell {
  font-size: 11px;
  color: #8ba2c4;
  line-height: 1.5;
}

.ok-text {
  color: #5eead4;
}
</style>
