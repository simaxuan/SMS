<template>
  <el-card shadow="never">
    <template #header>孩子成绩趋势</template>

    <el-form :inline="true" label-width="70px">
      <el-form-item label="孩子">
        <el-select v-model="studentId" filterable clearable placeholder="请选择孩子" style="width: 260px" @change="load">
          <el-option v-for="b in binds" :key="b.studentId"
                     :label="b.studentNo + ' - ' + b.name + (b.className ? ' (' + b.className + ')' : '')"
                     :value="b.studentId" />
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
          <el-card shadow="never" header="总分趋势（校内考试，仅老师来源）">
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
          <el-table-column prop="examName" label="考试" width="150" />
          <el-table-column prop="examDate" label="日期" width="110" />
          <el-table-column prop="totalScore" label="校内总分" width="100" />
          <el-table-column prop="totalFull" label="满分" width="80" />
          <el-table-column label="总得分率" width="100">
            <template #default="{ row }">{{ row.totalPercent != null ? row.totalPercent + '%' : '-' }}</template>
          </el-table-column>
          <el-table-column label="标准分" width="90">
            <template #default="{ row }">{{ row.stdScore != null ? row.stdScore : '-' }}</template>
          </el-table-column>
          <el-table-column label="各科成绩" min-width="340">
            <template #default="{ row }">
              <span v-for="ci in row.courses" :key="ci.courseId" class="course-item">
                <b>{{ ci.courseName }}</b> {{ ci.score }}/{{ ci.fullScore }}
                <span :class="ci.percent >= 60 ? 'pass' : 'fail'">({{ ci.percent }}%)</span>
                <el-tag v-if="ci.source === 'parent'" size="small" type="warning">家长录入</el-tag>
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
    <el-empty v-else description="请选择孩子查看趋势" />

    <el-card v-if="studentId" shadow="never" header="最新考试排名" style="margin-top: 20px">
      <el-alert v-if="rankDisabled" type="warning" :closable="false" show-icon title="老师当前已关闭家长端排名展示" style="margin-bottom: 12px" />
      <template v-else>
        <el-form :inline="true" style="margin-bottom: 12px">
          <el-form-item label="排名范围">
            <el-select v-model="pref.classScope" style="width: 160px" @change="(v) => savePref('classScope', v)">
              <el-option label="只看孩子所在班级" :value="true" />
              <el-option label="全校排名" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item label="并列显示">
            <el-switch :model-value="pref.showTies" active-text="显示并列" inactive-text="唯一顺延"
                       @change="(v) => savePref('showTies', v)" />
          </el-form-item>
        </el-form>
        <el-table :data="ranks" border stripe>
          <el-table-column prop="courseName" label="科目" width="140" />
          <el-table-column prop="score" label="得分" width="110">
            <template #default="{ row }">{{ row.score }}/{{ row.fullScore }}</template>
          </el-table-column>
          <el-table-column label="名次" width="130">
            <template #default="{ row }">
              <b class="rank-num">{{ row.rank }}</b>&nbsp;/&nbsp;{{ row.total }}
              <el-tag v-if="pref.classScope" size="small" type="info">本班</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="percent" label="得分率">
            <template #default="{ row }">{{ row.percent }}%</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!ranks.length" description="暂无排名数据" />
      </template>
    </el-card>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import echarts from '../../utils/echarts'
import { statApi, parentApi, settingsApi } from '../../api'

const binds = ref([])
const studentId = ref(null)
const trend = ref([])
const note = ref('')
const ranks = ref([])
const rankDisabled = ref(false)
const pref = ref({ classScope: true, showTies: true })
const totalChartRef = ref()
const courseChartRef = ref()
let totalChart = null
let courseChart = null

async function load() {
  if (!studentId.value) return
  const res = await statApi.studentTrend({ studentId: studentId.value })
  trend.value = res.trend || []
  note.value = res.note || ''
  await nextTick()
  renderCharts()
  loadRanks()
}

/** 清空筛选回到空态。 */
function reset() {
  studentId.value = null
  trend.value = []
  note.value = ''
  ranks.value = []
}

/** 读取家长排名个性化偏好。 */
async function loadPref() {
  try {
    const p = await parentApi.preference()
    pref.value = { classScope: p.classScope !== false, showTies: p.showTies !== false }
  } catch (e) {
    pref.value = { classScope: true, showTies: true }
  }
}

/**
 * 保存偏好（#23 加固）：classScope 由 select 显式传目标值，showTies 由 switch 传值，
 * 不再依赖 v-model 先更新/@change 传值的时序（避免取到旧值）。
 */
async function savePref(key, val) {
  // classScope: 显式传入选中的布尔值；showTies: 由 switch 传入新值。二者都显式指定，消除时序脆弱。
  const next = { classScope: key === 'classScope' ? val : pref.value.classScope, showTies: key === 'showTies' ? val : pref.value.showTies }
  pref.value = next
  try {
    const saved = await parentApi.savePreference({ classScope: next.classScope, showTies: next.showTies })
    pref.value = { classScope: saved.classScope !== false, showTies: saved.showTies !== false }
    ElMessage.success('已保存排名偏好')
    loadRanks()
  } catch (e) {
    ElMessage.warning('保存偏好失败')
  }
}

/** 读取家长端排名开关并拉取孩子最新考试各科名次。 */
async function loadRanks() {
  ranks.value = []
  rankDisabled.value = false
  try {
    const s = await settingsApi.list()
    if (s.parent_rank_visible === false) { rankDisabled.value = true; return }
    const latest = trend.value[trend.value.length - 1]
    if (!latest) return
    for (const c of latest.courses || []) {
      const r = await statApi.rank({
        examId: latest.examId,
        courseId: c.courseId,
        classScope: pref.value.classScope,
        showTies: pref.value.showTies
      })
      if (!r.rows) continue
      const row = r.rows.find((x) => x.studentId === studentId.value)
      if (row) {
        ranks.value.push({
          courseName: c.courseName,
          score: row.score,
          fullScore: row.fullScore,
          rank: row.rank,
          total: r.total,
          percent: row.percent
        })
      }
    }
  } catch (e) {
    // 接口异常则静默隐藏排名
  }
}

function renderCharts() {
  const exams = trend.value
  const labels = exams.map((t) => t.examName)

  if (totalChartRef.value) {
    totalChart = totalChart || echarts.init(totalChartRef.value)
    totalChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: labels },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        name: '校内总分',
        type: 'line',
        smooth: true,
        data: exams.map((t) => t.totalScore),
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.15 }
      }]
    })
  }

  if (courseChartRef.value) {
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
  binds.value = await parentApi.binds()
  loadPref()
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
.rank-num { color: var(--el-color-primary); font-size: 16px; }
</style>
