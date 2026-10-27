<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSlots, fetchDayBookings, createBooking } from '../api/booking'
import { fetchDevices } from '../api/device'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const loading = ref(true)
const bookingsLoading = ref(false)
const slots = ref([])
const devices = ref([])
const bookings = ref([])

const today = formatDate(new Date())
const maxDate = formatDate(new Date(Date.now() + 14 * 86400000))
const date = ref(today)

const canPrev = computed(() => date.value > today)
const canNext = computed(() => date.value < maxDate)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const currentCell = reactive({
  device: null,
  slot: null,
  purpose: '',
})

const formRules = {
  purpose: [{ required: true, message: '请填写设备用途', trigger: 'blur' }],
}

function formatDate(value) {
  const d = new Date(value)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function shiftDate(days) {
  const d = new Date(date.value + 'T00:00:00')
  d.setDate(d.getDate() + days)
  const next = formatDate(d)
  if (next < today || next > maxDate) return
  date.value = next
}

function disabledDate(time) {
  const start = new Date(today + 'T00:00:00')
  const max = new Date(start)
  max.setDate(max.getDate() + 14)
  return time < start || time > max
}

/* ---------------- 数据 ---------------- */
async function loadBase() {
  loading.value = true
  try {
    const [slotData, deviceData] = await Promise.all([
      fetchSlots(),
      fetchDevices({ page: 1, size: 100 }),
    ])
    slots.value = slotData || []
    devices.value = deviceData?.records || []
    await loadBookings()
  } finally {
    loading.value = false
  }
}

async function loadBookings() {
  bookingsLoading.value = true
  try {
    bookings.value = (await fetchDayBookings(date.value)) || []
  } finally {
    bookingsLoading.value = false
  }
}

watch(date, loadBookings)

/* ---------------- 矩阵计算 ---------------- */
const bookingMap = computed(() => {
  const map = new Map()
  for (const item of bookings.value) {
    map.set(`${item.deviceId}_${item.slotId}`, item)
  }
  return map
})

function bookingOf(device, slot) {
  return bookingMap.value.get(`${device.id}_${slot.id}`) || null
}

function timeString(value) {
  return value ? String(value).slice(0, 8) : '00:00:00'
}

function nowTimeString() {
  const now = new Date()
  return [now.getHours(), now.getMinutes(), now.getSeconds()]
    .map((n) => String(n).padStart(2, '0'))
    .join(':')
}

function isPast(device, slot) {
  if (date.value < today) return true
  if (date.value > today) return false
  return timeString(slot.startTime) <= nowTimeString()
}

function cellState(device, slot) {
  const booking = bookingOf(device, slot)
  if (device.status === 'MAINTENANCE') {
    return { kind: 'maintenance', text: '维修中', booking: null }
  }
  if (device.status === 'RETIRED') {
    return { kind: 'retired', text: '已停用', booking: null }
  }
  if (booking) {
    const labels = {
      PENDING: '待审批',
      APPROVED: '已预约',
      COMPLETED: '已完成',
      VIOLATED: '违约',
    }
    return {
      kind: booking.status.toLowerCase(),
      text: labels[booking.status] || booking.status,
      booking,
    }
  }
  if (isPast(device, slot)) {
    return { kind: 'past', text: '已过期', booking: null }
  }
  return { kind: 'free', text: '可预约', booking: null }
}

const summary = computed(() => {
  let free = 0
  let pending = 0
  let booked = 0
  for (const device of devices.value) {
    for (const slot of slots.value) {
      const { kind } = cellState(device, slot)
      if (kind === 'free') free += 1
      else if (kind === 'pending') pending += 1
      else if (kind === 'approved') booked += 1
    }
  }
  return { free, pending, booked }
})

/* ---------------- 预约弹窗 ---------------- */
function openBook(device, slot) {
  const state = cellState(device, slot)
  if (state.kind !== 'free') return
  currentCell.device = device
  currentCell.slot = slot
  currentCell.purpose = ''
  dialogVisible.value = true
}

async function submitBooking() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createBooking({
      deviceId: currentCell.device.id,
      slotId: currentCell.slot.id,
      date: date.value,
      purpose: currentCell.purpose,
    })
    ElMessage.success('预约已提交，等待教师审批')
    dialogVisible.value = false
    window.dispatchEvent(new Event('lab:pending-changed'))
    await loadBookings()
  } finally {
    submitting.value = false
  }
}

function isMine(booking) {
  return booking?.userId === auth.user?.id
}

onMounted(loadBase)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">预约看板</h2>
        <p class="page-subtitle">按日期查看全部设备的时段占用，点击空闲格直接发起预约。</p>
      </div>
      <div class="header-actions">
        <el-button circle :disabled="!canPrev" @click="shiftDate(-1)"><el-icon><ArrowLeft /></el-icon></el-button>
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="disabledDate"
          style="width: 150px"
        />
        <el-button circle :disabled="!canNext" @click="shiftDate(1)"><el-icon><ArrowRight /></el-icon></el-button>
        <el-button @click="date = today">今天</el-button>
        <el-button :loading="bookingsLoading" circle @click="loadBookings">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 概览条 -->
    <section class="overview-strip">
      <div class="ov-item">
        <span class="hud-label">DATE</span>
        <strong class="mono">{{ date }}</strong>
        <span class="ov-sub">{{ date === today ? '今天' : '已选择日期' }}</span>
      </div>
      <div class="ov-item">
        <span class="hud-label">FREE SLOTS</span>
        <strong class="mono accent-cyan">{{ summary.free }}</strong>
        <span class="ov-sub">可预约时段</span>
      </div>
      <div class="ov-item">
        <span class="hud-label">PENDING</span>
        <strong class="mono accent-amber">{{ summary.pending }}</strong>
        <span class="ov-sub">等待审批</span>
      </div>
      <div class="ov-item">
        <span class="hud-label">RESERVED</span>
        <strong class="mono accent-blue">{{ summary.booked }}</strong>
        <span class="ov-sub">已通过预约</span>
      </div>
      <div class="legend">
        <span><i class="lg free" />空闲</span>
        <span><i class="lg pending" />待审批</span>
        <span><i class="lg approved" />已通过</span>
        <span><i class="lg completed" />已完成</span>
        <span><i class="lg violated" />违约</span>
        <span><i class="lg locked" />不可用</span>
      </div>
    </section>

    <!-- 资源矩阵 -->
    <section v-loading="loading" class="panel matrix-panel">
      <div class="matrix-scroll">
        <div
          class="matrix"
          :style="{ gridTemplateColumns: `196px repeat(${slots.length || 1}, minmax(132px, 1fr))` }"
        >
          <div class="matrix-corner">
            <span class="hud-label">RESOURCE</span>
            <span class="corner-sub">设备 / 时段</span>
          </div>
          <div v-for="slot in slots" :key="slot.id" class="slot-head">
            <strong class="mono">{{ timeString(slot.startTime).slice(0, 5) }}</strong>
            <span>{{ timeString(slot.endTime).slice(0, 5) }}</span>
          </div>

          <template v-for="device in devices" :key="device.id">
            <div class="device-cell">
              <div class="device-name">
                <span class="device-code mono">{{ device.code }}</span>
                <strong>{{ device.name }}</strong>
              </div>
              <div class="device-meta">
                <span>{{ device.categoryName }}</span>
                <span class="device-location">{{ device.location || '—' }}</span>
              </div>
            </div>

            <button
              v-for="slot in slots"
              :key="slot.id"
              class="cell"
              :class="cellState(device, slot).kind"
              :disabled="cellState(device, slot).kind !== 'free'"
              :title="
                cellState(device, slot).booking
                  ? `${cellState(device, slot).booking.realName || ''} · ${cellState(device, slot).booking.purpose || '未填写用途'}`
                  : cellState(device, slot).text
              "
              @click="openBook(device, slot)"
            >
              <template v-if="cellState(device, slot).booking">
                <div class="cell-top">
                  <span class="cell-state">{{ cellState(device, slot).text }}</span>
                  <span v-if="isMine(cellState(device, slot).booking)" class="mine-dot">我</span>
                </div>
                <span class="cell-user">
                  {{ cellState(device, slot).booking.realName || cellState(device, slot).booking.userName }}
                </span>
              </template>
              <template v-else>
                <el-icon v-if="cellState(device, slot).kind === 'free'" class="cell-plus"><Plus /></el-icon>
                <el-icon v-else class="cell-plus"><Lock /></el-icon>
                <span class="cell-state">{{ cellState(device, slot).text }}</span>
              </template>
            </button>
          </template>
        </div>
      </div>
    </section>

    <!-- 预约弹窗 -->
    <el-dialog v-model="dialogVisible" title="发起设备预约" width="520px" :close-on-click-modal="false">
      <div class="book-summary">
        <div class="sum-row">
          <span class="hud-label">DEVICE</span>
          <strong>{{ currentCell.device?.name }}</strong>
          <span class="mono dim">{{ currentCell.device?.code }} · {{ currentCell.device?.location }}</span>
        </div>
        <div class="sum-row">
          <span class="hud-label">DATE / SLOT</span>
          <strong class="mono">{{ date }}</strong>
          <span class="mono dim">
            {{ timeString(currentCell.slot?.startTime).slice(0, 5) }} -
            {{ timeString(currentCell.slot?.endTime).slice(0, 5) }}
          </span>
        </div>
      </div>

      <el-form ref="formRef" :model="currentCell" :rules="formRules" label-position="top">
        <el-form-item label="使用用途" prop="purpose">
          <el-input
            v-model="currentCell.purpose"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="简要说明实验内容、课程或项目，方便审批"
          />
        </el-form-item>
      </el-form>

      <div class="book-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>提交后状态为「待审批」，教师通过后请在开始前 30 分钟内签到，否则记为违约。</span>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBooking">提交预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.header-actions .el-button {
  margin-left: 0;
}

.overview-strip {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
  padding: 14px 20px;
  border-radius: 12px;
  border: 1px solid var(--lab-border);
  background: linear-gradient(120deg, rgba(14, 28, 50, 0.85), rgba(7, 15, 29, 0.8));
}

.ov-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 88px;
}

.ov-item strong {
  font-size: 18px;
  color: #dcebff;
}

.ov-sub {
  font-size: 10px;
  color: #5f7799;
}

.accent-cyan {
  color: #5eead4 !important;
  text-shadow: 0 0 12px rgba(52, 211, 153, 0.4);
}

.accent-amber {
  color: #fcd34d !important;
  text-shadow: 0 0 12px rgba(251, 191, 36, 0.4);
}

.accent-blue {
  color: #7cc4ff !important;
  text-shadow: 0 0 12px rgba(47, 140, 255, 0.4);
}

.legend {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 10.5px;
  color: #7d91b3;
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.lg {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  display: inline-block;
}

.lg.free {
  background: rgba(52, 211, 153, 0.75);
}

.lg.pending {
  background: rgba(251, 191, 36, 0.8);
}

.lg.approved {
  background: rgba(47, 140, 255, 0.85);
}

.lg.completed {
  background: rgba(125, 145, 179, 0.7);
}

.lg.violated {
  background: rgba(251, 113, 133, 0.85);
}

.lg.locked {
  background: rgba(70, 85, 110, 0.6);
}

.matrix-panel {
  padding: 16px;
}

.matrix-scroll {
  overflow-x: auto;
}

.matrix {
  display: grid;
  gap: 8px;
  min-width: 760px;
}

.matrix-corner {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 4px;
  padding: 10px 12px;
}

.corner-sub {
  font-size: 11px;
  color: #5f7799;
}

.slot-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  padding: 9px 6px;
  border-radius: 8px;
  background: rgba(20, 38, 64, 0.55);
  border: 1px solid rgba(74, 130, 210, 0.16);
}

.slot-head strong {
  font-size: 13px;
  color: #cfe0f7;
}

.slot-head span {
  font-size: 9.5px;
  color: #5f7799;
}

.device-cell {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 10px 12px;
  border-radius: 9px;
  background: rgba(12, 24, 43, 0.7);
  border: 1px solid rgba(74, 130, 210, 0.14);
}

.device-name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.device-code {
  font-size: 9.5px;
  color: #5eead4;
  padding: 1px 5px;
  border-radius: 3px;
  border: 1px solid rgba(52, 211, 153, 0.25);
  background: rgba(52, 211, 153, 0.06);
  flex-shrink: 0;
}

.device-name strong {
  font-size: 12.5px;
  color: #dcebff;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-meta {
  display: flex;
  justify-content: space-between;
  font-size: 9.5px;
  color: #5f7799;
}

.device-location {
  font-family: Consolas, monospace;
}

.cell {
  min-height: 64px;
  border-radius: 9px;
  border: 1px solid rgba(74, 130, 210, 0.18);
  background: rgba(10, 22, 40, 0.6);
  color: #7d91b3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.18s;
  padding: 6px;
}

.cell-top {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.cell-state {
  font-size: 11px;
  letter-spacing: 0.3px;
}

.cell-user {
  font-size: 9.5px;
  color: rgba(255, 255, 255, 0.55);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-plus {
  font-size: 14px;
  opacity: 0.8;
}

.cell.free {
  color: #5eead4;
  border-color: rgba(52, 211, 153, 0.25);
  background: rgba(52, 211, 153, 0.055);
}

.cell.free:hover {
  border-color: rgba(52, 211, 153, 0.6);
  background: rgba(52, 211, 153, 0.12);
  box-shadow: 0 0 20px rgba(52, 211, 153, 0.18);
  transform: translateY(-1px);
}

.cell.pending {
  color: #fcd34d;
  border-color: rgba(251, 191, 36, 0.28);
  background: rgba(251, 191, 36, 0.07);
  cursor: not-allowed;
}

.cell.approved {
  color: #7cc4ff;
  border-color: rgba(47, 140, 255, 0.3);
  background: rgba(47, 140, 255, 0.08);
  cursor: not-allowed;
}

.cell.completed {
  color: #a9bbd6;
  border-color: rgba(125, 145, 179, 0.2);
  background: rgba(125, 145, 179, 0.06);
  cursor: not-allowed;
}

.cell.violated {
  color: #fda4af;
  border-color: rgba(251, 113, 133, 0.3);
  background: rgba(251, 113, 133, 0.08);
  cursor: not-allowed;
}

.cell.maintenance,
.cell.retired,
.cell.past {
  color: #465a78;
  border-color: rgba(70, 85, 110, 0.18);
  background: rgba(20, 32, 50, 0.4);
  cursor: not-allowed;
}

.cell:disabled {
  opacity: 1;
}

.mine-dot {
  font-size: 8px;
  padding: 0 3px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.book-summary {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid rgba(74, 130, 210, 0.18);
  background: rgba(10, 22, 40, 0.55);
  margin-bottom: 18px;
}

.sum-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sum-row strong {
  font-size: 13px;
  color: #dcebff;
}

.sum-row .hud-label {
  width: 92px;
  flex-shrink: 0;
}

.book-tip {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 11.5px;
  line-height: 1.6;
  color: #8ba2c4;
  background: rgba(47, 140, 255, 0.06);
  border: 1px solid rgba(74, 130, 210, 0.16);
}

.book-tip .el-icon {
  color: #7cc4ff;
  margin-top: 2px;
}

@media (max-width: 900px) {
  .legend {
    margin-left: 0;
  }
}
</style>
