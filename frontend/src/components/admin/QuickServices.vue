<template>
  <div class="quick-services-page">
    <div class="page-header">
      <div>
        <h2>快捷服务配置</h2>
        <p>配置客服首页快捷按钮，以及点击后填入输入框的提示内容。</p>
      </div>
      <el-button type="primary" @click="openDialog()">
        <el-icon><Plus /></el-icon>
        新增快捷服务
      </el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="services" v-loading="loading" border>
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="label" label="名称" width="140" />
        <el-table-column prop="serviceKey" label="标识" width="140" />
        <el-table-column prop="iconKey" label="图标" width="130">
          <template #default="{ row }">
            <el-icon><component :is="getIcon(row.iconKey)" /></el-icon>
            <span class="icon-name">{{ row.iconKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="promptText" label="提示内容" min-width="360" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" plain @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑快捷服务' : '新增快捷服务'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="服务标识">
          <el-input v-model="form.serviceKey" placeholder="如 policy" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.label" placeholder="如 政策咨询" />
        </el-form-item>
        <el-form-item label="图标">
          <el-select v-model="form.iconKey" style="width: 100%">
            <el-option v-for="icon in iconOptions" :key="icon.value" :label="icon.label" :value="icon.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示内容">
          <el-input v-model="form.promptText" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button label="ENABLED">启用</el-radio-button>
            <el-radio-button label="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, OfficeBuilding, Search, Tools, User, MessageBox, Plus } from '@element-plus/icons-vue'
import { createQuickService, getQuickServices, updateQuickService, updateQuickServiceStatus } from '@/api/chat.js'

const iconMap = { Document, OfficeBuilding, Search, Tools, User, MessageBox }
const iconOptions = [
  { label: '文档', value: 'Document' },
  { label: '楼宇', value: 'OfficeBuilding' },
  { label: '搜索', value: 'Search' },
  { label: '工具', value: 'Tools' },
  { label: '用户', value: 'User' },
  { label: '消息', value: 'MessageBox' }
]

const loading = ref(false)
const saving = ref(false)
const services = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({
  serviceKey: '',
  label: '',
  iconKey: 'Document',
  promptText: '',
  sortOrder: 10,
  status: 'ENABLED'
})

function getIcon(iconKey) {
  return iconMap[iconKey] || Document
}

async function fetchServices() {
  loading.value = true
  try {
    const res = await getQuickServices()
    services.value = res.data
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  editingId.value = row?.id || null
  form.serviceKey = row?.serviceKey || ''
  form.label = row?.label || ''
  form.iconKey = row?.iconKey || 'Document'
  form.promptText = row?.promptText || ''
  form.sortOrder = row?.sortOrder || 10
  form.status = row?.status || 'ENABLED'
  dialogVisible.value = true
}

async function submit() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateQuickService(editingId.value, form)
    } else {
      await createQuickService(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchServices()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  await updateQuickServiceStatus(row.id, row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED')
  ElMessage.success('状态已更新')
  fetchServices()
}

onMounted(fetchServices)
</script>

<style scoped>
.quick-services-page {
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

.icon-name {
  margin-left: 6px;
}
</style>
