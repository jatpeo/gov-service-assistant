<template>
  <div class="statistics-page">
    <div class="page-header">
      <h2>统计分析</h2>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        @change="fetchStatistics"
      />
    </div>

    <!-- 概览卡片 -->
    <el-row :gutter="20" class="overview-cards">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ overview.todayConversations || 0 }}</div>
          <div class="stat-label">今日会话</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ overview.totalConversations || 0 }}</div>
          <div class="stat-label">总会话数</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ overview.activeConversations || 0 }}</div>
          <div class="stat-label">活跃会话</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ overview.handoffConversations || 0 }}</div>
          <div class="stat-label">转人工数</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <span>意图分类统计</span>
          </template>
          <div ref="intentChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <span>满意度分布</span>
          </template>
          <div ref="satisfactionChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :span="24">
        <el-card class="chart-card">
          <template #header>
            <span>每日会话趋势</span>
          </template>
          <div ref="dailyChartRef" class="chart-container" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

const dateRange = ref([])
const overview = ref({})
const intentChartRef = ref(null)
const satisfactionChartRef = ref(null)
const dailyChartRef = ref(null)

let intentChart = null
let satisfactionChart = null
let dailyChart = null

const fetchStatistics = async () => {
  try {
    // TODO: 调用 API 获取统计数据
    // const res = await fetch('/api/admin/statistics/overview')
    // overview.value = await res.json()

    // Mock 数据
    overview.value = {
      todayConversations: 128,
      totalConversations: 5680,
      activeConversations: 15,
      handoffConversations: 23
    }

    initCharts()
  } catch (error) {
    ElMessage.error('获取统计数据失败')
  }
}

const initCharts = () => {
  // 意图分类饼图
  if (intentChartRef.value) {
    intentChart = echarts.init(intentChartRef.value)
    intentChart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c} ({d}%)' },
        data: [
          { value: 335, name: '政策咨询' },
          { value: 310, name: '业务办理' },
          { value: 234, name: '进度查询' },
          { value: 135, name: '技术支持' },
          { value: 148, name: '账号权限' },
          { value: 98, name: '投诉建议' }
        ]
      }]
    })
  }

  // 满意度柱状图
  if (satisfactionChartRef.value) {
    satisfactionChart = echarts.init(satisfactionChartRef.value)
    satisfactionChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['非常满意', '满意', '一般', '不满意', '非常不满意'] },
      yAxis: { type: 'value' },
      series: [{
        data: [320, 450, 220, 80, 30],
        type: 'bar',
        itemStyle: {
          color: (params) => {
            const colors = ['#67C23A', '#95D475', '#E6A23C', '#F56C6C', '#F78989']
            return colors[params.dataIndex]
          }
        }
      }]
    })
  }

  // 每日趋势折线图
  if (dailyChartRef.value) {
    dailyChart = echarts.init(dailyChartRef.value)
    dailyChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
      },
      yAxis: { type: 'value' },
      series: [
        {
          name: '会话数',
          type: 'line',
          smooth: true,
          data: [120, 132, 101, 134, 90, 230, 210],
          areaStyle: { opacity: 0.3 }
        },
        {
          name: '转人工',
          type: 'line',
          smooth: true,
          data: [20, 18, 15, 22, 12, 35, 30]
        }
      ]
    })
  }
}

const handleResize = () => {
  intentChart?.resize()
  satisfactionChart?.resize()
  dailyChart?.resize()
}

onMounted(() => {
  fetchStatistics()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  intentChart?.dispose()
  satisfactionChart?.dispose()
  dailyChart?.dispose()
})
</script>

<style scoped>
.statistics-page {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.overview-cards {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  margin-bottom: 20px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.charts-row {
  margin-bottom: 20px;
}

.chart-card {
  margin-bottom: 20px;
}

.chart-container {
  height: 280px;
}
</style>
