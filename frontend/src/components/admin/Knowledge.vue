<template>
  <div class="knowledge-page">
    <div class="page-header">
      <div>
        <h2>知识库管理</h2>
        <p>以上传文档为主，支持 PDF 操作手册、Word 常见问题解析，保留少量手工补录入口。</p>
      </div>
      <div class="header-actions">
        <el-button @click="openDocumentUpload">
          <el-icon><Upload /></el-icon>
          上传文档
        </el-button>
        <el-button type="primary" @click="openManualDialog">
          <el-icon><Plus /></el-icon>
          手工补录
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <div>
            <h3>文档中心</h3>
            <p>上传原始文件后自动抽取文本、表格与图片 OCR 结果，审核后发布到聊天检索。</p>
          </div>
          <div class="section-actions">
            <el-button @click="fetchDocuments">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="documentList"
        v-loading="documentLoading"
        stripe
        height="360"
      >
        <el-table-column prop="documentCode" label="编码" width="180" />
        <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-tag effect="plain">{{ getDocumentTypeLabel(row.documentType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getDocumentStatusTag(row.status)">
              {{ getDocumentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalChunks" label="片段数" width="100" />
        <el-table-column prop="publishedChunks" label="已发布" width="100" />
        <el-table-column prop="uploadedBy" label="上传人" width="120" />
        <el-table-column prop="parseMessage" label="解析说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt || row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDocumentPreview(row)">预览</el-button>
            <el-button text type="success" @click="handlePublishDocument(row)">发布</el-button>
            <el-button text type="warning" @click="handleRetryDocument(row)">重试</el-button>
            <el-button text type="danger" @click="handleRejectDocument(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="documentPage"
          v-model:page-size="documentPageSize"
          :total="documentTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchDocuments"
          @current-change="fetchDocuments"
        />
      </div>
    </el-card>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="section-header">
          <div>
            <h3>手工补录</h3>
            <p>用于紧急纠错或补充少量标准问答，保持为辅助入口。</p>
          </div>
          <div class="section-actions">
            <el-input
              v-model="manualFilters.keyword"
              class="keyword-input"
              placeholder="搜索问题、答案或关键词"
              clearable
              @keyup.enter="handleManualSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="manualFilters.status" class="status-select" placeholder="状态筛选" clearable>
              <el-option label="全部状态" value="ALL" />
              <el-option label="启用" value="ENABLED" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
            <el-button type="primary" @click="handleManualSearch">查询</el-button>
            <el-button @click="handleManualReset">重置</el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="manualList"
        v-loading="manualLoading"
        stripe
        height="360"
      >
        <el-table-column prop="itemCode" label="编码" width="120" />
        <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="320" show-overflow-tooltip />
        <el-table-column label="分类" width="150">
          <template #default="{ row }">
            <el-tag effect="plain">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" min-width="180" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中次数" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'DISABLED' ? 'info' : 'success'">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="handleManualEdit(row)">编辑</el-button>
            <el-button text type="warning" @click="handleManualToggleStatus(row)">
              {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
            </el-button>
            <el-button text type="danger" @click="handleManualDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="manualPage"
          v-model:page-size="manualPageSize"
          :total="manualTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchManualList"
          @current-change="fetchManualList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="documentUploadVisible"
      title="上传文档"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="100px">
        <el-form-item label="文档类型">
          <el-select v-model="documentForm.documentType" style="width: 100%">
            <el-option label="操作手册 PDF" value="PDF_OPERATION_MANUAL" />
            <el-option label="常见问题 Word" value="WORD_FAQ" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传人">
          <el-input v-model="documentForm.uploadedBy" placeholder="可选" />
        </el-form-item>
        <el-form-item label="文件">
          <div class="upload-row">
            <el-button @click="chooseDocumentFile">选择文件</el-button>
            <span class="upload-filename">{{ selectedDocumentFileName || '未选择文件' }}</span>
          </div>
          <input
            ref="documentFileInputRef"
            type="file"
            class="hidden-file-input"
            accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            @change="handleDocumentFileChange"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="documentUploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="documentSaving" @click="handleUploadDocument">
          上传并解析
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="documentPreviewVisible"
      title="文档预览"
      size="60%"
      destroy-on-close
    >
      <div class="preview-meta" v-if="previewDocument">
        <div><strong>文件名：</strong>{{ previewDocument.fileName }}</div>
        <div><strong>状态：</strong>{{ getDocumentStatusLabel(previewDocument.status) }}</div>
        <div><strong>解析说明：</strong>{{ previewDocument.parseMessage || '-' }}</div>
      </div>

      <el-divider>解析片段</el-divider>

      <el-timeline>
        <el-timeline-item
          v-for="chunk in previewChunks"
          :key="chunk.id"
          :type="chunk.status === 'PUBLISHED' ? 'success' : chunk.status === 'FAILED' ? 'danger' : 'primary'"
        >
          <div class="chunk-item">
            <div class="chunk-title">
              {{ chunk.chunkTitle || '知识片段' }}
            </div>
            <div class="chunk-meta">
              <span v-if="chunk.pageNo">第 {{ chunk.pageNo }} 页</span>
              <span v-if="chunk.imageIndex !== null && chunk.imageIndex !== undefined">图片 {{ chunk.imageIndex + 1 }}</span>
              <span>{{ chunk.sectionPath || '-' }}</span>
              <span>{{ getChunkStatusLabel(chunk.status) }}</span>
            </div>
            <div class="chunk-content">{{ chunk.content || '-' }}</div>
            <div v-if="chunk.sourceImagePath" class="chunk-image">
              <img
                :src="getDocumentKnowledgeChunkImageUrl(chunk.id)"
                :alt="chunk.chunkTitle || '文档图片'"
              />
            </div>
            <div v-if="chunk.ocrText" class="chunk-ocr">OCR: {{ chunk.ocrText }}</div>
            <div v-if="chunk.failureReason" class="chunk-error">失败原因: {{ chunk.failureReason }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>

    <el-dialog
      v-model="manualDialogVisible"
      :title="manualIsEdit ? '编辑手工条目' : '添加手工条目'"
      width="760px"
      destroy-on-close
    >
      <el-form
        ref="manualFormRef"
        :model="manualForm"
        :rules="manualRules"
        label-width="100px"
      >
        <el-form-item v-if="manualIsEdit" label="条目编码">
          <el-input v-model="manualForm.itemCode" disabled />
        </el-form-item>
        <el-form-item label="问题" prop="question">
          <el-input v-model="manualForm.question" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="manualForm.answer" type="textarea" :rows="5" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="manualForm.category" style="width: 100%">
                <el-option
                  v-for="option in categoryOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="manualForm.status" style="width: 100%">
                <el-option label="启用" value="ENABLED" />
                <el-option label="禁用" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关键词">
          <el-input v-model="manualForm.keywords" placeholder="多个关键词用逗号分隔" />
        </el-form-item>
        <el-form-item label="相关政策">
          <el-input v-model="manualForm.relatedPolicy" />
        </el-form-item>
        <el-form-item label="适用对象">
          <el-input v-model="manualForm.applicableTarget" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSaving" @click="handleManualSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  addKnowledgeItem,
  deleteKnowledgeItem,
  getDocumentKnowledgeChunkImageUrl,
  getDocumentKnowledgeChunks,
  getDocumentKnowledgeList,
  getDocumentKnowledgePreview,
  publishDocumentKnowledge,
  queryKnowledgeItems,
  rejectDocumentKnowledge,
  retryDocumentKnowledge,
  updateKnowledgeItem,
  updateKnowledgeStatus,
  uploadDocumentKnowledge
} from '@/api/chat.js'

const categoryOptions = [
  { label: '政策法规', value: 'POLICY_REGULATION' },
  { label: '业务指南', value: 'BUSINESS_GUIDE' },
  { label: '操作流程', value: 'OPERATION_PROCESS' },
  { label: '常见问题', value: 'FAQ' },
  { label: '系统功能', value: 'SYSTEM_FUNCTION' },
  { label: '数据报表', value: 'DATA_REPORT' },
  { label: '安全合规', value: 'SECURITY_COMPLIANCE' }
]

const categoryMap = new Map(categoryOptions.map(option => [option.value, option.label]))
const selectedDocumentFileName = ref('')
const maxDocumentUploadSize = 50 * 1024 * 1024

const documentLoading = ref(false)
const documentSaving = ref(false)
const documentList = ref([])
const documentTotal = ref(0)
const documentPage = ref(1)
const documentPageSize = ref(10)
const documentUploadVisible = ref(false)
const documentPreviewVisible = ref(false)
const previewDocument = ref(null)
const previewChunks = ref([])
const documentFileInputRef = ref(null)
const selectedDocumentFile = ref(null)

const documentForm = reactive({
  documentType: 'PDF_OPERATION_MANUAL',
  uploadedBy: ''
})

const manualLoading = ref(false)
const manualSaving = ref(false)
const manualList = ref([])
const manualTotal = ref(0)
const manualPage = ref(1)
const manualPageSize = ref(10)
const manualDialogVisible = ref(false)
const manualIsEdit = ref(false)
const manualFormRef = ref()

const manualFilters = reactive({
  keyword: '',
  status: 'ALL'
})

const emptyManualForm = () => ({
  id: null,
  itemCode: '',
  question: '',
  answer: '',
  category: 'FAQ',
  keywords: '',
  relatedPolicy: '',
  applicableTarget: '',
  status: 'ENABLED'
})

const manualForm = reactive(emptyManualForm())

const manualRules = {
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function getCategoryLabel(category) {
  return categoryMap.get(category) || category || '-'
}

function getStatusLabel(status) {
  return status === 'DISABLED' ? '禁用' : '启用'
}

function getDocumentTypeLabel(type) {
  if (type === 'PDF_OPERATION_MANUAL') return '操作手册 PDF'
  if (type === 'WORD_FAQ') return '常见问题 Word'
  return type || '-'
}

function getDocumentStatusLabel(status) {
  const map = {
    UPLOADED: '已上传',
    PARSING: '解析中',
    PENDING_REVIEW: '待审核',
    PUBLISHED: '已发布',
    FAILED: '失败'
  }
  return map[status] || status || '-'
}

function getDocumentStatusTag(status) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARSING') return 'warning'
  return 'info'
}

function getChunkStatusLabel(status) {
  const map = {
    DRAFT: '草稿',
    REVIEWED: '已审核',
    PUBLISHED: '已发布',
    SKIPPED: '已跳过',
    REJECTED: '已拒绝',
    FAILED: '失败'
  }
  return map[status] || status || '-'
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function resetManualForm() {
  Object.assign(manualForm, emptyManualForm())
}

function openDocumentUpload() {
  selectedDocumentFile.value = null
  selectedDocumentFileName.value = ''
  documentForm.documentType = 'PDF_OPERATION_MANUAL'
  documentForm.uploadedBy = ''
  if (documentFileInputRef.value) {
    documentFileInputRef.value.value = ''
  }
  documentUploadVisible.value = true
}

function chooseDocumentFile() {
  documentFileInputRef.value?.click()
}

function handleDocumentFileChange(event) {
  const file = event.target.files?.[0] || null
  if (file && file.size > maxDocumentUploadSize) {
    ElMessage.warning('文件超过 50MB，请压缩后再上传')
    event.target.value = ''
    selectedDocumentFile.value = null
    selectedDocumentFileName.value = ''
    return
  }
  selectedDocumentFile.value = file
  selectedDocumentFileName.value = file?.name || ''
}

async function fetchDocuments() {
  documentLoading.value = true
  try {
    const res = await getDocumentKnowledgeList(documentPage.value - 1, documentPageSize.value)
    const data = res.data || {}
    documentList.value = data.content || []
    documentTotal.value = data.totalElements || 0
  } catch (error) {
    console.error('获取文档列表失败:', error)
    ElMessage.error('获取文档列表失败')
  } finally {
    documentLoading.value = false
  }
}

async function handleUploadDocument() {
  if (!selectedDocumentFile.value) {
    ElMessage.warning('请先选择文档文件')
    return
  }

  documentSaving.value = true
  try {
    if (selectedDocumentFile.value.size > maxDocumentUploadSize) {
      ElMessage.warning('文件超过 50MB，请压缩后再上传')
      return
    }
    await uploadDocumentKnowledge(
      selectedDocumentFile.value,
      documentForm.documentType,
      documentForm.uploadedBy.trim() || undefined
    )
    ElMessage.success('上传并解析成功')
    documentUploadVisible.value = false
    selectedDocumentFile.value = null
    selectedDocumentFileName.value = ''
    if (documentFileInputRef.value) {
      documentFileInputRef.value.value = ''
    }
    fetchDocuments()
  } catch (error) {
    console.error('上传文档失败:', error)
    ElMessage.error('上传文档失败')
  } finally {
    documentSaving.value = false
  }
}

async function openDocumentPreview(row) {
  documentPreviewVisible.value = true
  previewDocument.value = row
  previewChunks.value = []
  try {
    const res = await getDocumentKnowledgePreview(row.id)
    const data = res.data || {}
    previewDocument.value = data.document || row
    previewChunks.value = data.chunks || []
  } catch (error) {
    console.error('获取文档预览失败:', error)
    ElMessage.error('获取文档预览失败')
  }
}

async function handlePublishDocument(row) {
  try {
    await ElMessageBox.confirm(`确定发布文档「${row.fileName}」的解析结果吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await publishDocumentKnowledge(row.id)
    ElMessage.success('发布成功')
    fetchDocuments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('发布失败:', error)
      ElMessage.error('发布失败')
    }
  }
}

async function handleRetryDocument(row) {
  try {
    await ElMessageBox.confirm(`确定重新解析文档「${row.fileName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await retryDocumentKnowledge(row.id)
    ElMessage.success('重试成功')
    fetchDocuments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('重试失败:', error)
      ElMessage.error('重试失败')
    }
  }
}

async function handleRejectDocument(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝文档', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：扫描质量过低、格式不支持'
    })
    await rejectDocumentKnowledge(row.id, value)
    ElMessage.success('已拒绝')
    fetchDocuments()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('拒绝失败:', error)
      ElMessage.error('拒绝失败')
    }
  }
}

async function fetchManualList() {
  manualLoading.value = true
  try {
    const res = await queryKnowledgeItems({
      page: manualPage.value - 1,
      size: manualPageSize.value,
      keyword: manualFilters.keyword.trim() || undefined,
      status: manualFilters.status === 'ALL' ? undefined : manualFilters.status
    })
    const data = res.data || {}
    manualList.value = data.content || []
    manualTotal.value = data.totalElements || 0
  } catch (error) {
    console.error('获取手工知识列表失败:', error)
    ElMessage.error('获取手工知识列表失败')
  } finally {
    manualLoading.value = false
  }
}

function openManualDialog() {
  manualIsEdit.value = false
  resetManualForm()
  manualDialogVisible.value = true
}

function handleManualEdit(row) {
  manualIsEdit.value = true
  Object.assign(manualForm, {
    id: row.id,
    itemCode: row.itemCode,
    question: row.question,
    answer: row.answer,
    category: row.category,
    keywords: row.keywords || '',
    relatedPolicy: row.relatedPolicy || '',
    applicableTarget: row.applicableTarget || '',
    status: row.status || 'ENABLED'
  })
  manualDialogVisible.value = true
}

async function handleManualToggleStatus(row) {
  const nextStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const actionText = nextStatus === 'ENABLED' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要${actionText}该知识条目吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await updateKnowledgeStatus(row.id, nextStatus)
    ElMessage.success(`${actionText}成功`)
    fetchManualList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(`${actionText}失败:`, error)
      ElMessage.error(`${actionText}失败`)
    }
  }
}

async function handleManualDelete(row) {
  try {
    await ElMessageBox.confirm('确定要永久删除该知识条目吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteKnowledgeItem(row.id)
    ElMessage.success('删除成功')
    fetchManualList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

function handleManualSearch() {
  manualPage.value = 1
  fetchManualList()
}

function handleManualReset() {
  manualFilters.keyword = ''
  manualFilters.status = 'ALL'
  manualPage.value = 1
  fetchManualList()
}

async function handleManualSave() {
  if (!manualFormRef.value) return

  try {
    const valid = await manualFormRef.value.validate().catch(() => false)
    if (!valid) return

    manualSaving.value = true
    const payload = {
      question: manualForm.question.trim(),
      answer: manualForm.answer.trim(),
      category: manualForm.category,
      keywords: manualForm.keywords.trim() || null,
      relatedPolicy: manualForm.relatedPolicy.trim() || null,
      applicableTarget: manualForm.applicableTarget.trim() || null,
      status: manualForm.status
    }

    if (manualIsEdit.value) {
      await updateKnowledgeItem(manualForm.id, payload)
      ElMessage.success('更新成功')
    } else {
      await addKnowledgeItem(payload)
      ElMessage.success('添加成功')
    }

    manualDialogVisible.value = false
    fetchManualList()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error(manualIsEdit.value ? '更新失败' : '添加失败')
  } finally {
    manualSaving.value = false
  }
}

onMounted(() => {
  fetchDocuments()
  fetchManualList()
})
</script>

<style scoped>
.knowledge-page {
  padding: 20px 24px 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #1f2937;
}

.page-header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.header-actions,
.section-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.section-card {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
}

.section-header p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.keyword-input {
  width: 320px;
}

.status-select {
  width: 140px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.hidden-file-input {
  display: none;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.upload-filename {
  color: #6b7280;
  font-size: 13px;
}

.preview-meta {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
  color: #374151;
}

.chunk-item {
  display: grid;
  gap: 8px;
}

.chunk-title {
  font-weight: 600;
  color: #1f2937;
}

.chunk-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: #6b7280;
  font-size: 12px;
}

.chunk-content,
.chunk-ocr,
.chunk-error {
  white-space: pre-wrap;
  line-height: 1.7;
}

.chunk-image {
  margin-top: 8px;
}

.chunk-image img {
  max-width: 100%;
  display: block;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.chunk-error {
  color: #dc2626;
}

@media (max-width: 768px) {
  .knowledge-page {
    padding: 16px;
  }

  .page-header,
  .section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .keyword-input,
  .status-select {
    width: 100%;
  }
}
</style>
