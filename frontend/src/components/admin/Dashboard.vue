<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: #e6f7ff; color: #1890ff;">
          <el-icon size="32"><ChatDotRound /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.todayConversations || 0 }}</div>
          <div class="stat-label">今日会话</div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: #f6ffed; color: #52c41a;">
          <el-icon size="32"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalConversations || 0 }}</div>
          <div class="stat-label">总会话</div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: #fff7e6; color: #fa8c16;">
          <el-icon size="32"><User /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.handoffConversations || 0 }}</div>
          <div class="stat-label">转人工</div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="hover">
        <div class="stat-icon" style="background: #fff1f0; color: #f5222d;">
          <el-icon size="32"><Loading /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.activeConversations || 0 }}</div>
          <div class="stat-label">进行中</div>
        </div>
      </el-card>
    </div>

    <!-- 图表区域 -->
    <div class="chart-section">
      <el-row :gutter="24">
        <el-col :span="16">
          <el-card class="chart-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>会话趋势</span>
                <el-radio-group v-model="trendPeriod" size="small">
                  <el-radio-button label="week">近7天</el-radio-button>
                  <el-radio-button label="month">近30天</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div ref="trendChartRef" class="chart-container"></div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card class="chart-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>意图分类</span>
              </div>
            </template>
            <div ref="intentChartRef" class="chart-container"></div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 底部区域 -->
    <div class="bottom-section">
      <el-row :gutter="24">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>满意度分布</span>
              </div>
            </template>
            <div ref="satisfactionChartRef" class="chart-container-small"></div>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span>高频问题 TOP5</span>
              </div>
            </template>
            <div class="top-questions">
              <div
                v-for="(item, index) in topQuestions"
                :key="index"
                class="question-item"
              >
                <div class="question-rank" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
                <div class="question-info">
                  <div class="question-title">{{ item.question }}</div>
                  <div class="question-count">浏览 {{ item.viewCount }} 次</div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import * as echarts from 'echarts'
import { ChatDotRound, CircleCheck, User, Loading } from '@element-plus/icons-vue'
import { getOverviewStatistics, getDailyStatistics, getFrequentQuestions } from '@/api/chat.js'

// 统计数据
const stats = ref({})
const trendPeriod = ref('week')
const topQuestions = ref([])

// 图表引用
const trendChartRef = ref(null)
const intentChartRef = ref(null)
const satisfactionChartRef = ref(null)

let trendChart = null
let intentChart = null
let satisfactionChart = null

// 获取概览统计
async function fetchOverviewStats() {
  try {
    const res = await getOverviewStatistics()
    stats.value = res.data
  } catch (error) {
    console.error('获取概览统计失败:', error)
  }
}

// 获取趋势数据
async function fetchTrendData() {
  try {
    const endDate = new Date()
    const startDate = new Date()
    if (trendPeriod.value === 'week') {
      startDate.setDate(endDate.getDate() - 7)
    } else {
      startDate.setDate(endDate.getDate() - 30)
    }

    const res = await getDailyStatistics(
      startDate.toISOString().split('T')[0],
      endDate.toISOString().split('T')[0]
    )

    const data = res.data
    const dates = data.map(item => item.statDate)
    const conversations = data.map(item => item.totalConversations)

    updateTrendChart(dates, conversations)
  } catch (error) {
    console.error('获取趋势数据失败:', error)
  }
}

// 获取高频问题
async function fetchTopQuestions() {
  try {
    const res = await getFrequentQuestions(5)
    topQuestions.value = res.data
  } catch (error) {
    console.error('获取高频问题失败:', error)
  }
}

// 更新趋势图表
function updateTrendChart(dates, data) {
  if (!trendChart) return

  trendChart.setOption({
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#606266' }
    },
    series: [{
      data: data,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { color: '#b72a33', width: 3 },
      itemStyle: { color: '#b72a33' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(26, 95, 180, 0.3)' },
            { offset: 1, color: 'rgba(26, 95, 180, 0.05)' }
          ]
        }
      }
    }],
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    }
  })
}

// 初始化意图分类图表
function initIntentChart() {
  intentChart = echarts.init(intentChartRef.value)

  intentChart.setOption({
    tooltip: {
      trigger: 'item'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: { color: '#606266' }
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 8,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: {
        show: false
      },
      emphasis: {
        label: {
          show: true,
          fontSize: 14,
          fontWeight: 'bold'
        }
      },
      data: [
        { value: 335, name: '政策咨询', itemStyle: { color: '#b72a33' } },
        { value: 310, name: '业务办理', itemStyle: { color: '#52c41a' } },
        { value: 234, name: '进度查询', itemStyle: { color: '#fa8c16' } },
        { value: 135, name: '技术支持', itemStyle: { color: '#722ed1' } },
        { value: 148, name: '其他', itemStyle: { color: '#8c8c8c' } }
      ]
    }]
  })
}

// 初始化满意度图表
function initSatisfactionChart() {
  satisfactionChart = echarts.init(satisfactionChartRef.value)

  satisfactionChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    xAxis: {
      type: 'category',
      data: ['5星', '4星', '3星', '2星', '1星'],
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#606266' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#606266' }
    },
    series: [{
      type: 'bar',
      data: [
        { value: 320, itemStyle: { color: '#52c41a' } },
        { value: 180, itemStyle: { color: '#95de64' } },
        { value: 60, itemStyle: { color: '#faad14' } },
        { value: 20, itemStyle: { color: '#ff7a45' } },
        { value: 10, itemStyle: { color: '#ff4d4f' } }
      ],
      barWidth: '50%',
      borderRadius: [4, 4, 0, 0]
    }],
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    }
  })
}

// 监听周期变化
watch(trendPeriod, fetchTrendData)

onMounted(() => {
  fetchOverviewStats()
  fetchTrendData()
  fetchTopQuestions()

  // 初始化图表
  trendChart = echarts.init(trendChartRef.value)
  initIntentChart()
  initSatisfactionChart()

  // 窗口大小变化时重绘
  window.addEventListener('resize', () => {
    trendChart?.resize()
    intentChart?.resize()
    satisfactionChart?.resize()
  })
})
</script>

<style scoped lang="scss">
.dashboard {
  .stat-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 24px;
    margin-bottom: 24px;

    .stat-card {
      :deep(.el-card__body) {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 20px;
      }

      .stat-icon {
        width: 64px;
        height: 64px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .stat-info {
        .stat-value {
          font-size: 28px;
          font-weight: 600;
          color: #1a1a1a;
          line-height: 1;
          margin-bottom: 8px;
        }

        .stat-label {
          font-size: 14px;
          color: #606266;
        }
      }
    }
  }

  .chart-section {
    margin-bottom: 24px;

    .chart-card {
      .card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .chart-container {
        height: 320px;
      }
    }
  }

  .bottom-section {
    .chart-container-small {
      height: 240px;
    }

    .top-questions {
      .question-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 0;
        border-bottom: 1px solid #e4e7ed;

        &:last-child {
          border-bottom: none;
        }

        .question-rank {
          width: 28px;
          height: 28px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 14px;
          font-weight: 600;
          flex-shrink: 0;

          &.rank-1 {
            background: #fff1f0;
            color: #f5222d;
          }

          &.rank-2 {
            background: #fff7e6;
            color: #fa8c16;
          }

          &.rank-3 {
            background: #ffffe6;
            color: #d4b106;
          }

          &.rank-4,
          &.rank-5 {
            background: #f5f5f5;
            color: #8c8c8c;
          }
        }

        .question-info {
          flex: 1;
          min-width: 0;

          .question-title {
            font-size: 14px;
            color: #1a1a1a;
            margin-bottom: 4px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .question-count {
            font-size: 12px;
            color: #8c8c8c;
          }
        }
      }
    }
  }
}
</style>
