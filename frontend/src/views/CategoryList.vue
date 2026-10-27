<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchCategories, createCategory, updateCategory, removeCategory } from '../api/category'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const canEdit = computed(() => auth.isStaff)

const loading = ref(false)
const list = ref([])

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const editingId = ref(null)

const form = reactive({
  code: '',
  name: '',
  remark: '',
})

const rules = {
  code: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    list.value = (await fetchCategories()) || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, { code: '', name: '', remark: '' })
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
    remark: row.remark || '',
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
      await updateCategory(editingId.value, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已新增')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  if (row.deviceCount > 0) {
    ElMessage.warning(`该分类下有 ${row.deviceCount} 台设备，请先转移或删除设备`)
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await removeCategory(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">设备分类</h2>
        <p class="page-subtitle">分类用于设备归档与统计，删除前需先清空该分类下的设备。</p>
      </div>
      <el-button v-if="canEdit" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新增分类</span>
      </el-button>
    </div>

    <section class="panel">
      <el-table v-loading="loading" :data="list" stripe empty-text="暂无分类数据">
        <el-table-column prop="code" label="编码" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="分类名称" min-width="160" />
        <el-table-column prop="deviceCount" label="设备数量" width="120">
          <template #default="{ row }">
            <el-tag :type="row.deviceCount > 0 ? 'primary' : 'info'" effect="plain">
              {{ row.deviceCount }} 台
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
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
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑分类' : '新增分类'"
      width="480px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="form.code" placeholder="如 PC / EI / IM" maxlength="30" />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如 计算机设备" maxlength="50" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="该分类包含哪些设备"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
