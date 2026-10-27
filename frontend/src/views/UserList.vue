<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchUsers, createUser, updateUser, resetUserPassword } from '../api/user'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const query = reactive({
  keyword: '',
  role: '',
  page: 1,
  size: 10,
})

const roleOptions = [
  { value: 'ADMIN', label: '管理员', tag: 'danger' },
  { value: 'TEACHER', label: '教师', tag: 'warning' },
  { value: 'STUDENT', label: '学生', tag: 'info' },
]

const roleMeta = Object.fromEntries(roleOptions.map((item) => [item.value, item]))

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const editingId = ref(null)

const form = reactive({
  username: '',
  password: '123456',
  realName: '',
  role: 'STUDENT',
  status: 1,
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    const data = await fetchUsers({
      keyword: query.keyword || undefined,
      role: query.role || undefined,
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

function resetQuery() {
  query.keyword = ''
  query.role = ''
  query.page = 1
  load()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    username: '',
    password: '123456',
    realName: '',
    role: 'STUDENT',
    status: 1,
  })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    password: '',
    realName: row.realName || '',
    role: row.role,
    status: row.status,
  })
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        realName: form.realName,
        role: form.role,
        status: form.status,
      })
      ElMessage.success('用户信息已更新')
    } else {
      await createUser({ ...form })
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  try {
    await updateUser(row.id, {
      realName: row.realName,
      role: row.role,
      status: row.status,
    })
    ElMessage.success(row.status === 1 ? '账号已启用' : '账号已禁用')
  } catch {
    load()
  }
}

async function resetPassword(row) {
  let value
  try {
    const result = await ElMessageBox.prompt(
      `为「${row.realName || row.username}」设置新密码，留空则重置为 123456`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputValue: '123456',
        inputPattern: /^.{0,}$/,
        inputErrorMessage: '密码格式不正确',
      },
    )
    value = result.value || '123456'
  } catch {
    return
  }
  if (value.length > 0 && value.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  await resetUserPassword(row.id, value)
  ElMessage.success('密码已重置')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-subtitle">维护系统账号与角色。管理员不能禁用自己或修改自己的角色。</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新增用户</span>
      </el-button>
    </div>

    <section class="panel">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="用户名 / 姓名"
          clearable
          style="width: 220px"
          :prefix-icon="'Search'"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 140px" @change="search">
          <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="search">
          <el-icon><Search /></el-icon>
          <span>查询</span>
        </el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px" empty-text="暂无用户">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" class="avatar">
                {{ (row.realName || row.username || '?').slice(0, 1) }}
              </el-avatar>
              <div>
                <strong>{{ row.realName || '未填写姓名' }}</strong>
                <span class="mono dim">@{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleMeta[row.role]?.tag || 'info'" effect="light">
              {{ roleMeta[row.role]?.label || row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="row.id === auth.user?.id"
              active-text="启用"
              inactive-text="禁用"
              inline-prompt
              @change="toggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="mono dim">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="resetPassword(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="(page) => { query.page = page; load() }"
          @size-change="(size) => { query.size = size; query.page = 1; load() }"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑用户' : '新增用户'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="Boolean(editingId)" placeholder="登录名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="真实姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col v-if="!editingId" :span="12">
            <el-form-item label="初始密码" prop="password">
              <el-input v-model="form.password" show-password placeholder="至少 6 位" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色" prop="role">
              <el-select v-model="form.role" style="width: 100%">
                <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
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

<style scoped>
.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-cell > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-cell strong {
  font-size: 13px;
  color: #dcebff;
  font-weight: 500;
}

.user-cell span {
  font-size: 10.5px;
}

.avatar {
  background: linear-gradient(135deg, #2f8cff, #8b5cf6);
  font-size: 12px;
  flex-shrink: 0;
}
</style>
