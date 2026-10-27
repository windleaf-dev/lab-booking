<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchDevices, createDevice, updateDevice, removeDevice } from '../api/device'
import { fetchCategories } from '../api/category'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canEdit = computed(() => auth.isStaff)

const statusOptions = [
  { value: 'AVAILABLE', label: '可用', tag: 'success' },
  { value: 'MAINTENANCE', label: '维修中', tag: 'warning' },
  { value: 'RETIRED', label: '已停用', tag: 'info' },
]

const statusMeta = Object.fromEntries(statusOptions.map((item) => [item.value, item]))

const loading = ref(false)
const list = ref([])
const total = ref(0)
const categories = ref([])

const query = reactive({
  keyword: '',
  categoryId: null,
  status: '',
  page: 1,
  size: 10,
})

/* ---------------- 列表 ---------------- */
async function load() {
  loading.value = true
  try {
    const data = await fetchDevices({
      keyword: query.keyword || undefined,
      categoryId: query.categoryId || undefined,
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

function search() {
  query.page = 1
  load()
}

function reset() {
  query.keyword = ''
  query.categoryId = null
  query.status = ''
  query.page = 1
  load()
}

function handlePageChange(page) {
  query.page = page
  load()
}

function handleSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const editingId = ref(null)

const form = reactive({
  code: '',
  name: '',
  categoryId: null,
  model: '',
  location: '',
  status: 'AVAILABLE',
  description: '',
})

const rules = {
  code: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择设备分类', trigger: 'change' }],
  status: [{ required: true, message: '请选择设备状态', trigger: 'change' }],
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    code: '',
    name: '',
    categoryId: null,
    model: '',
    location: '',
    status: 'AVAILABLE',
    description: '',
  })
  formRef.value?.clearValidate()
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    name: row.name,
    categoryId: row.categoryId,
    model: row.model || '',
    location: row.location || '',
    status: row.status,
    description: row.description || '',
  })
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      await updateDevice(editingId.value, payload)
      ElMessage.success('设备信息已更新')
    } else {
      await createDevice(payload)
      ElMessage.success('设备已新增')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除设备「${row.name}」吗？已有预约记录的设备不能删除，可改为停用。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await removeDevice(row.id)
  ElMessage.success('已删除')
  // 删掉当前页最后一条时回退一页
  if (list.value.length === 1 && query.page > 1) query.page -= 1
  load()
}

onMounted(async () => {
  try {
    categories.value = (await fetchCategories()) || []
  } catch {
    categories.value = []
  }
  load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">设备台账</h2>
        <p class="page-subtitle">
          维护实验室设备的基础信息与状态。管理员、教师可编辑，学生仅可查看。
        </p>
      </div>
      <el-button v-if="canEdit" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新增设备</span>
      </el-button>
    </div>

    <section class="panel">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索名称 / 编号 / 型号 / 位置"
          clearable
          style="width: 260px"
          :prefix-icon="'Search'"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="query.categoryId"
          placeholder="全部分类"
          clearable
          style="width: 160px"
          @change="search"
        >
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        stripe
        style="margin-top: 16px"
        empty-text="暂无设备数据"
      >
        <el-table-column prop="code" label="编号" width="110">
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="model" label="型号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.model || '—' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="110">
          <template #default="{ row }">{{ row.location || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta[row.status]?.tag || 'info'" effect="light">
              {{ statusMeta[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            <span class="muted">{{ (row.createdAt || '').replace('T', ' ').slice(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑设备' : '新增设备'"
      width="560px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备编号" prop="code">
              <el-input v-model="form.code" placeholder="如 D009" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="name">
              <el-input v-model="form.name" placeholder="如 3D 打印机" maxlength="80" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                <el-option
                  v-for="category in categories"
                  :key="category.id"
                  :label="category.name"
                  :value="category.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option
                  v-for="item in statusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="form.model" placeholder="如 Prusa MK4" maxlength="80" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放位置">
              <el-input v-model="form.location" placeholder="如 C101" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注说明">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                maxlength="300"
                show-word-limit
                placeholder="设备用途、注意事项等"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
