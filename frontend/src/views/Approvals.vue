<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPending, fetchAllBookings, approveBooking } from '../api/booking'
import { fetchOverview } from '../api/stats'

const activeTab = ref('pending')
const loading = ref(false)
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingPage = ref(1)

const historyLoading = ref(false)
const historyList = ref([])
const historyTotal = ref(0)
const historyQuery = reactive({
  status: '',
  keyword: '',
  date: null,
  page: 1,
  size: 10,
})

const overview = ref({})

const decisionVisible = ref(false)
const decisionSubmitting = ref(false)
const decision = reactive({
  row: null,
  approved: true,
  remark: '',
})

const statusOptions = [
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'VIOLATED', label: '违约' },
]

const statusMeta = {
  PENDING: { label: '待审批', tag: 'warning' },
  APPROVED: { label: '已通过', tag: 'primary' },
  REJECTED: { label: '已驳回', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
  COMPLETED: { label: '已完成', tag: 'success' },
  VIOLATED: { label: '违约', tag: 'danger' },
}

const pendingCount = computed(() => overview.value.pendingCount ?? pendingTotal.value)

async function loadPending() {
  loading.value = true
  try {
    const data = await fetchPending({ page: pendingPage.value, size: 8 })
    pendingList.value = data?.records || []
    pendingTotal.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const data = await fetchAllBookings({
      status: historyQuery.status || undefined,
      keyword: historyQuery.keyword || undefined,
      date: historyQuery.date || undefined,
      page: historyQuery.page,
      size: historyQuery.size,
    })
    historyList.value = data?.records || []
    historyTotal.value = data?.total || 0
  } finally {
    historyLoading.value = false
  }
}

async function loadOverview() {
  try {
    overview.value = (await fetchOverview()) || {}
  } catch {
    overview.value = {}
  }
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'pending') loadPending()
  else loadHistory()
}

function searchHistory() {
  historyQuery.page = 1
  loadHistory()
}

function resetHistory() {
  historyQuery.status = ''
  historyQuery.keyword = ''
  historyQuery.date = null
  historyQuery.page = 1
  loadHistory()
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function openDecision(row, approved) {
  decision.row = row
  decision.approved = approved
  decision.remark = ''
  decisionVisible.value = true
}

async function submitDecision() {
  if (!decision.approved && !decision.remark.trim()) {
    ElMessage.warning('驳回时请填写原因，方便申请人修改')
    return
  }
  decisionSubmitting.value = true
  try {
    await approveBooking(decision.row.id, {
      approved: decision.approved,
      remark: decision.remark.trim() || undefined,
    })
    ElMessage.success(decision.approved ? '已通过该预约' : '已驳回该预约')
    decisionVisible.value = false
    window.dispatchEvent(new Event('lab:pending-changed'))
    await Promise.all([loadPending(), loadOverview()])
  } finally {
    decisionSubmitting.value = false
  }
}

async function quickApprove(row) {
  try {
    await ElMessageBox.confirm(
      `通过「${row.realName}」对 ${row.deviceName} 的预约（${row.bookingDate} ${row.slotName}）？`,
      '审批确认',
      { confirmButtonText: '通过', cancelButtonText: '取消', type: 'success' },
    )
  } catch {
    return
  }
  await approveBooking(row.id, { approved: true, remark: '审批通过' })
  ElMessage.success('已通过该预约')
  window.dispatchEvent(new Event('lab:pending-changed'))
  await Promise.all([loadPending(), loadOverview()])
}

onMounted(() => {
  loadPending()
  loadOverview()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">审批中心</h2>
        <p class="page-subtitle">处理待审批预约，查看历史审批记录。不能审批自己提交的预约。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="activeTab === 'pending' ? loading : historyLoading" @click="activeTab === 'pending' ? loadPending() : loadHistory()">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </div>
    </div>

    <section class="approval-banner">
      <div class="banner-item">
        <span class="hud-label">PENDING QUEUE</span>
        <strong class="mono accent-amber">{{ pendingCount }}</strong>
        <span>待处理审批</span>
      </div>
      <div class="banner-item">
        <span class="hud-label">APPROVED</span>
        <strong class="mono accent-blue">{{ overview.approvedCount ?? 0 }}</strong>
        <span>已通过</span>
      </div>
      <div class="banner-item">
        <span class="hud-label">COMPLETED</span>
        <strong class="mono accent-green">{{ overview.completedCount ?? 0 }}</strong>
        <span>已完成</span>
      </div>
      <div class="banner-item">
        <span class="hud-label">VIOLATED</span>
        <strong class="mono accent-red">{{ overview.violatedCount ?? 0 }}</strong>
        <span>违约记录</span>
      </div>
      <div class="banner-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>审批通过后，申请人需在时段开始前 30 分钟内签到；未签到将自动判定违约。</span>
      </div>
    </section>

    <section class="panel">
      <div class="tab-bar">
        <button class="tab-btn" :class="{ active: activeTab === 'pending' }" @click="switchTab('pending')">
          待审批
          <span v-if="pendingCount" class="tab-count">{{ pendingCount }}</span>
        </button>
        <button class="tab-btn" :class="{ active: activeTab === 'history' }" @click="switchTab('history')">
          全部记录
        </button>
      </div>

      <!-- 待审批 -->
      <template v-if="activeTab === 'pending'">
        <el-table v-loading="loading" :data="pendingList" stripe empty-text="没有待审批的预约">
          <el-table-column label="申请人" width="140">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.realName }}</strong>
                <span class="mono dim">@{{ row.userName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="设备" min-width="160">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.deviceName }}</strong>
                <span class="mono dim">{{ row.deviceCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="日期 / 时段" width="170">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="mono">{{ row.bookingDate }}</span>
                <span class="dim">{{ row.slotName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.purpose || '—' }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="160">
            <template #default="{ row }">
              <span class="dim mono">{{ formatTime(row.createdAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="quickApprove(row)">通过</el-button>
              <el-button link type="danger" @click="openDecision(row, false)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            :current-page="pendingPage"
            :page-size="8"
            :total="pendingTotal"
            layout="total, prev, pager, next"
            background
            @current-change="(page) => { pendingPage = page; loadPending() }"
          />
        </div>
      </template>

      <!-- 历史记录 -->
      <template v-else>
        <div class="filter-bar">
          <el-input
            v-model="historyQuery.keyword"
            placeholder="申请人 / 设备名称 / 编号"
            clearable
            style="width: 240px"
            :prefix-icon="'Search'"
            @keyup.enter="searchHistory"
            @clear="searchHistory"
          />
          <el-select v-model="historyQuery.status" placeholder="全部状态" clearable style="width: 140px" @change="searchHistory">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-date-picker
            v-model="historyQuery.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="预约日期"
            clearable
            style="width: 150px"
            @change="searchHistory"
          />
          <el-button type="primary" @click="searchHistory">
            <el-icon><Search /></el-icon>
            <span>查询</span>
          </el-button>
          <el-button @click="resetHistory">重置</el-button>
        </div>

        <el-table v-loading="historyLoading" :data="historyList" stripe style="margin-top: 16px" empty-text="暂无记录">
          <el-table-column label="申请人" width="130">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.realName }}</strong>
                <span class="mono dim">@{{ row.userName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="设备" min-width="150">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.deviceName }}</strong>
                <span class="mono dim">{{ row.deviceCode }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="日期 / 时段" width="160">
            <template #default="{ row }">
              <div class="user-cell">
                <span class="mono">{{ row.bookingDate }}</span>
                <span class="dim">{{ row.slotName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="statusMeta[row.status]?.tag || 'info'" effect="light">
                {{ statusMeta[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批信息" min-width="200">
            <template #default="{ row }">
              <div class="user-cell">
                <span v-if="row.approverName">审批人：{{ row.approverName }}</span>
                <span v-if="row.approveRemark" class="dim">{{ row.approveRemark }}</span>
                <span v-if="!row.approverName && !row.approveRemark" class="dim">—</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="签到时间" width="160">
            <template #default="{ row }">
              <span class="dim mono">{{ formatTime(row.checkinAt) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <el-pagination
            :current-page="historyQuery.page"
            :page-size="historyQuery.size"
            :total="historyTotal"
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            background
            @current-change="(page) => { historyQuery.page = page; loadHistory() }"
            @size-change="(size) => { historyQuery.size = size; historyQuery.page = 1; loadHistory() }"
          />
        </div>
      </template>
    </section>

    <!-- 驳回弹窗 -->
    <el-dialog
      v-model="decisionVisible"
      :title="decision.approved ? '通过预约' : '驳回预约'"
      width="480px"
      :close-on-click-modal="false"
    >
      <div class="decision-card">
        <div>
          <span class="hud-label">APPLICANT</span>
          <strong>{{ decision.row?.realName }}</strong>
          <span class="dim">@{{ decision.row?.userName }}</span>
        </div>
        <div>
          <span class="hud-label">RESOURCE</span>
          <strong>{{ decision.row?.deviceName }}</strong>
          <span class="dim">{{ decision.row?.bookingDate }} · {{ decision.row?.slotName }}</span>
        </div>
      </div>
      <el-input
        v-model="decision.remark"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        :placeholder="decision.approved ? '审批备注（选填）' : '请填写驳回原因（必填）'"
      />
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button
          :type="decision.approved ? 'success' : 'danger'"
          :loading="decisionSubmitting"
          @click="submitDecision"
        >
          {{ decision.approved ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions :deep(.el-button) {
  margin-left: 0;
}

.approval-banner {
  display: flex;
  align-items: center;
  gap: 30px;
  flex-wrap: wrap;
  padding: 16px 22px;
  border-radius: 12px;
  border: 1px solid var(--lab-border);
  background: linear-gradient(120deg, rgba(14, 28, 50, 0.88), rgba(7, 15, 29, 0.82));
}

.banner-item {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.banner-item strong {
  font-size: 22px;
}

.banner-item span:last-child {
  font-size: 10px;
  color: #5f7799;
}

.accent-amber {
  color: #fcd34d !important;
  text-shadow: 0 0 14px rgba(251, 191, 36, 0.4);
}

.accent-blue {
  color: #7cc4ff !important;
  text-shadow: 0 0 14px rgba(47, 140, 255, 0.4);
}

.accent-green {
  color: #5eead4 !important;
  text-shadow: 0 0 14px rgba(52, 211, 153, 0.4);
}

.accent-red {
  color: #fda4af !important;
  text-shadow: 0 0 14px rgba(251, 113, 133, 0.4);
}

.banner-tip {
  margin-left: auto;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 430px;
  font-size: 11px;
  line-height: 1.6;
  color: #6f86a8;
}

.banner-tip .el-icon {
  color: #7cc4ff;
  margin-top: 2px;
}

.tab-bar {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
  border-bottom: 1px solid rgba(74, 130, 210, 0.14);
  padding-bottom: 10px;
}

.tab-btn {
  position: relative;
  padding: 7px 16px;
  border-radius: 7px;
  border: 1px solid transparent;
  background: transparent;
  color: #7d91b3;
  font-size: 13px;
  cursor: pointer;
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  transition: all 0.18s;
}

.tab-btn:hover {
  color: #cfe0f7;
  background: rgba(47, 140, 255, 0.06);
}

.tab-btn.active {
  color: #dcebff;
  background: rgba(47, 140, 255, 0.12);
  border-color: rgba(47, 140, 255, 0.3);
}

.tab-count {
  min-width: 18px;
  height: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(251, 191, 36, 0.18);
  color: #fcd34d;
  font-size: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: Consolas, monospace;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-cell strong {
  font-size: 12.5px;
  color: #dcebff;
  font-weight: 500;
}

.user-cell span {
  font-size: 10.5px;
}

.decision-card {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
  border-radius: 10px;
  border: 1px solid rgba(74, 130, 210, 0.18);
  background: rgba(10, 22, 40, 0.55);
}

.decision-card > div {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.decision-card .hud-label {
  width: 84px;
}

.decision-card strong {
  font-size: 13px;
  color: #dcebff;
}
</style>
