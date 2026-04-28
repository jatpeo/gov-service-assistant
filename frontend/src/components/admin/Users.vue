<template>
  <div class="users-page">
    <div class="page-header">
      <div>
        <h2>人员管理</h2>
        <p>管理后台账号、人工客服和普通用户权限</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新增人员
      </el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="users" v-loading="loading" border>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="姓名" min-width="140" />
        <el-table-column prop="role" label="角色" width="130">
          <template #default="{ row }">
            <el-tag :type="getRoleTag(row.role)">{{ getRoleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="170">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openResetDialog(row)">重置密码</el-button>
            <el-button
              size="small"
              :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
              plain
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新增人员" width="460px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="createForm.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="系统管理员" value="ADMIN" />
            <el-option label="人工客服" value="AGENT" />
            <el-option label="只读账号" value="VIEWER" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <el-form label-width="90px">
        <el-form-item label="账号">
          <el-input :model-value="currentUser?.username" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReset">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createUser, getUsers, resetUserPassword, updateUserStatus } from '@/api/chat.js'

const loading = ref(false)
const saving = ref(false)
const users = ref([])
const createVisible = ref(false)
const resetVisible = ref(false)
const currentUser = ref(null)
const newPassword = ref('')

const createForm = reactive({
  username: '',
  displayName: '',
  role: 'USER',
  password: ''
})

function getRoleLabel(role) {
  const labels = {
    ADMIN: '系统管理员',
    AGENT: '人工客服',
    VIEWER: '只读账号',
    USER: '普通用户'
  }
  return labels[role] || role
}

function getRoleTag(role) {
  const tags = {
    ADMIN: 'danger',
    AGENT: 'success',
    VIEWER: 'info',
    USER: ''
  }
  return tags[role] || ''
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await getUsers()
    users.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  createForm.username = ''
  createForm.displayName = ''
  createForm.role = 'USER'
  createForm.password = ''
  createVisible.value = true
}

async function submitCreate() {
  saving.value = true
  try {
    await createUser(createForm)
    ElMessage.success('人员已新增')
    createVisible.value = false
    fetchUsers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '新增失败')
  } finally {
    saving.value = false
  }
}

function openResetDialog(row) {
  currentUser.value = row
  newPassword.value = ''
  resetVisible.value = true
}

async function submitReset() {
  if (!newPassword.value) {
    ElMessage.warning('请输入新密码')
    return
  }
  saving.value = true
  try {
    await resetUserPassword(currentUser.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '重置失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(`确定${nextStatus === 'ACTIVE' ? '启用' : '停用'}该账号吗？`, '状态变更')
  await updateUserStatus(row.id, nextStatus)
  ElMessage.success('状态已更新')
  fetchUsers()
}

onMounted(fetchUsers)
</script>

<style scoped>
.users-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 24px;
}

.page-header p {
  margin: 0;
  color: #667085;
}
</style>
