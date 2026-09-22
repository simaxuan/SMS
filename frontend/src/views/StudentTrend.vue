<template>
  <el-card shadow="never">
    <template #header>个人成绩趋势</template>
    <el-form :inline="true" label-width="70px">
      <el-form-item label="学生">
        <!-- 学生选择改为远程搜索：避免一次性拉全量（原 size:1000 静默截断），首屏仅加载前 100 条 -->
        <el-select v-model="studentId" filterable clearable remote :remote-method="searchStudents" style="width: 240px" placeholder="输入学号/姓名搜索" @change="load">
          <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' - ' + s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!studentId" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert v-if="note" type="info" :closable="false" show-icon :title="note" style="margin-bottom: 16px" />

    <template v-if="trend.length">
      <el-row :gutter="16">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" header="总分趋势">
            <div ref="totalChartRef" style="height: 320px"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" header="各科得分率(%)">
            <div ref="courseChartRef" style="height: 320px"></div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" header="各科成绩明细" style="margin-top: 20px">
        <el-table :data="trend" border stripe>
          <el-table-column prop="examName" label="考试" width="160" />
          <el-table-column prop="examDate" label="日期" width="120" />
          <el-table-column prop="totalScore" label="总分" width="90" />
          <el-table-column label="各科" min-width="360">
            <template #default="{ row }">
              <span v-for="ci in row.courses" :key="ci.courseId" class="course-item">
                <b>{{ ci.courseName }}</b> {{ ci.score }}/{{ ci.fullScore }}
                <span :class="ci.percent >= 60 ? 'pass' : 'fail'">({{ ci.percent }}%)</span>
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
    <el-empty v-else description="请选择学生查看趋势" />
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import echarts from '../utils/echarts'
import { statApi, studentApi } from '../api'

const students = ref([])
const studentId = ref(null)
const trend = ref([])
const note = ref('')
const totalChartRef = ref()
const courseChartRef = ref()
let totalChart = null
let courseChart = null

// 学生下拉远程搜索：studentMap 缓存 id→{学号,姓名}，远程结果替换下拉项时仍保留已选中学生以正确回显
const studentMap = ref(new Map())
function mergeStudentMap(list) {
  ;(list || []).forEach((s) => studentMap.value.set(s.id, { studentNo: s.studentNo, name: s.name }))
  studentMap.value = new Map(studentMap.value)
}
function searchStudents(kw) {
  studentApi.list({ keyword: kw || '', page: 0, size: 50 }).then((res) => {
    const list = (res && res.content) || []
    mergeStudentMap(list)
    const arr = [...list]
    const sid = studentId.value
    if (sid != null) {
      const m = studentMap.value.get(sid)
      if (m && !arr.find((x) => x.id === sid)) arr.unshift({ id: sid, studentNo: m.studentNo, name: m.name })
    }
    students.value = arr
  }).catch(() => {})
}

async function load() {
  if (!studentId.value) return
  const res = await statApi.studentTrend({ studentId: studentId.value })
  trend.value = res.trend || []
  note.value = res.note || ''
  await nextTick()
  renderCharts()
}

/** 清空筛选回到空态。 */
function reset() {
  studentId.value = null
  trend.value = []
  note.value = ''
}

function renderCharts() {
  const exams = trend.value
  const labels = exams.map((t) => t.examName)

  // 总分趋势
  if (totalChartRef.value) {
    totalChart = totalChart || echarts.init(totalChartRef.value)
    totalChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: labels },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        name: '总分',
        type: 'line',
        smooth: true,
        data: exams.map((t) => t.totalScore),
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.15 }
      }]
    })
  }

  // 各科得分率趋势
  if (courseChartRef.value) {
    // 收集所有课程
    const courseMap = new Map()
    exams.forEach((t) => (t.courses || []).forEach((c) => {
      if (!courseMap.has(c.courseId)) courseMap.set(c.courseId, c.courseName)
    }))
    const series = [...courseMap.entries()].map(([cid, cname]) => ({
      name: cname,
      type: 'line',
      smooth: true,
      data: exams.map((t) => {
        const c = (t.courses || []).find((x) => x.courseId === cid)
        return c ? c.percent : null
      })
    }))
    courseChart = courseChart || echarts.init(courseChartRef.value)
    courseChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { type: 'scroll', bottom: 0 },
      grid: { left: 50, right: 20, top: 30, bottom: 60 },
      xAxis: { type: 'category', data: labels },
      yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
      series
    })
  }
}

function handleResize() {
  totalChart?.resize()
  courseChart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  // 学生下拉改为远程搜索（原 size:1000 全量会在超千时静默截断）；首屏仅取前 100 条作为底表
  const s = await studentApi.list({ page: 0, size: 100 })
  mergeStudentMap(s.content || [])
  students.value = s.content || []
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  totalChart?.dispose()
  courseChart?.dispose()
})
</script>

<style scoped>
.course-item {
  display: inline-block;
  margin-right: 16px;
  color: var(--el-text-color-regular);
}
.pass { color: var(--el-color-success); }
.fail { color: var(--el-color-danger); }
</style>
