<template>
  <el-card shadow="never">
    <template #header>成绩统计</template>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ===== 单科统计 ===== -->
      <el-tab-pane label="单科统计" name="course">
        <el-form :inline="true" label-width="70px">
          <el-form-item label="考试">
            <el-select v-model="query.examId" clearable style="width: 180px" placeholder="全部考试" @change="loadCourse">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程">
            <el-select v-model="query.courseId" clearable style="width: 160px" placeholder="全部课程" @change="loadCourse">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadCourse">查询</el-button>
            <el-button @click="resetCourse">重置</el-button>
          </el-form-item>
        </el-form>

        <el-alert v-if="stats && stats.note" type="info" :closable="false" show-icon :title="stats.note" style="margin-bottom: 16px" />
        <template v-if="stats">
          <el-row :gutter="16">
            <el-col :xs="12" :sm="8" :md="6" :lg="4" v-for="card in statCards" :key="card.label">
              <el-card shadow="hover" class="stat-card">
                <div class="value" :title="String(card.value)">{{ card.value }}</div>
                <div class="label">{{ card.label }}</div>
              </el-card>
            </el-col>
          </el-row>
          <el-row :gutter="20" style="margin-top: 20px">
            <el-col :xs="24" :lg="12">
              <el-card shadow="never" header="分数段分布"><div ref="distChartRef" style="height: 320px"></div></el-card>
            </el-col>
            <el-col :xs="24" :lg="12">
              <el-card shadow="never" header="及格/优秀率"><div ref="rateChartRef" style="height: 320px"></div></el-card>
            </el-col>
          </el-row>
        </template>
        <el-empty v-else description="请选择考试与课程进行统计" />
      </el-tab-pane>

      <!-- ===== 班级对比 ===== -->
      <el-tab-pane label="班级对比" name="compare">
        <el-form :inline="true" label-width="70px">
          <el-form-item label="范围">
            <el-radio-group v-model="compareQuery.mode" @change="onCompareModeChange">
              <el-radio value="exam">按考试</el-radio>
              <el-radio value="semester">按学期</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="compareQuery.mode === 'exam'" label="考试">
            <el-select v-model="compareQuery.examId" clearable style="width: 180px" placeholder="全部考试" @change="loadCompare">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="学期">
            <el-select v-model="compareQuery.semester" clearable style="width: 160px" @change="loadCompare" placeholder="选择学期">
              <el-option v-for="s in semesters" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程">
            <el-select v-model="compareQuery.courseId" clearable style="width: 160px" placeholder="全部课程" @change="loadCompare">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadCompare">查询</el-button>
            <el-button @click="resetCompare">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="compare && compare.note" type="info" :closable="false" show-icon :title="compare.note" style="margin-bottom: 16px" />
        <template v-if="compare && compare.classes && compare.classes.length">
          <div ref="compareChartRef" style="height: 340px"></div>
          <el-table :data="compare.classes" border stripe style="margin-top: 16px">
            <el-table-column prop="className" label="班级" width="140" />
            <el-table-column prop="total" label="参考人数" width="90" />
            <el-table-column prop="avg" label="平均分" width="90" />
            <el-table-column prop="percentAvg" label="得分率" width="90">
              <template #default="{ row }">{{ row.percentAvg }}%</template>
            </el-table-column>
            <el-table-column prop="passRate" label="及格率" width="90">
              <template #default="{ row }">{{ row.passRate }}%</template>
            </el-table-column>
            <el-table-column prop="excellentRate" label="优秀率" width="90">
              <template #default="{ row }">{{ row.excellentRate }}%</template>
            </el-table-column>
            <el-table-column label="分数段分布" min-width="220">
              <template #default="{ row }">
                <div class="dist-tags">
                  <el-tag v-for="(v, k) in row.distribution" :key="k" size="small" type="info" effect="plain" class="dist-tag">{{ k }}:{{ v }}</el-tag>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="请选择考试与课程进行班级对比" />
      </el-tab-pane>

      <!-- ===== 排名 ===== -->
      <el-tab-pane label="成绩排名" name="rank">
        <el-form :inline="true" label-width="70px">
          <el-form-item label="考试">
            <el-select v-model="rankQuery.examId" style="width: 180px" @change="loadRank">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程">
            <el-select v-model="rankQuery.courseId" style="width: 160px" @change="loadRank">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="排名范围">
            <el-select v-model="rankQuery.scope" style="width: 140px" @change="loadRank">
              <el-option label="班级" value="class" />
              <el-option label="年级" value="level" />
              <el-option label="全校" value="school" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadRank">查询</el-button>
            <el-button @click="resetRank">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="rankData && rankData.note" type="info" :closable="false" show-icon :title="rankData.note" style="margin-bottom: 16px" />
        <el-table v-if="rankData && rankData.rows && rankData.rows.length" :data="rankData.rows" border stripe>
          <el-table-column label="名次" width="80">
            <template #default="{ row }">{{ row.rank }}</template>
          </el-table-column>
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="classId" label="班级" width="100">
            <template #default="{ row }">{{ className(row.classId) }}</template>
          </el-table-column>
          <el-table-column prop="score" label="分数" sortable />
          <el-table-column prop="fullScore" label="满分" width="90" />
          <el-table-column label="得分率" width="100">
            <template #default="{ row }">{{ row.percent }}%</template>
          </el-table-column>
        </el-table>
        <el-empty v-else :description="rankQuery.examId && rankQuery.courseId ? '暂无排名数据' : '请选择考试与课程查看排名'" />
      </el-tab-pane>

      <!-- ===== 学生总分（多科口径，A3 联调点） ===== -->
      <el-tab-pane label="学生总分" name="student">
        <el-form :inline="true" label-width="70px">
          <el-form-item label="考试">
            <el-select v-model="studentQuery.examId" clearable style="width: 180px" placeholder="全部考试" @change="loadStudentStat">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="学生">
            <!-- 学生选择改为远程搜索：避免一次性拉全量（原 size:1000 静默截断），首屏仅加载前 100 条作为底表 -->
            <el-select v-model="studentQuery.studentId" style="width: 220px" filterable clearable remote :remote-method="searchStudents" placeholder="输入学号/姓名搜索" @change="loadStudentStat">
              <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' ' + s.name" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadStudentStat">查询</el-button>
            <el-button @click="resetStudent">重置</el-button>
          </el-form-item>
        </el-form>
        <template v-if="studentStat">
          <el-row :gutter="16">
            <el-col :xs="12" :sm="6" v-for="card in studentStatCards" :key="card.label">
              <el-card shadow="hover" class="stat-card">
                <div class="value" :title="String(card.value)">{{ card.value }}</div>
                <div class="label">{{ card.label }}</div>
              </el-card>
            </el-col>
          </el-row>
          <el-alert v-if="studentStat.note" type="info" :closable="false" show-icon :title="studentStat.note" style="margin-top: 16px" />
        </template>
        <el-empty v-else description="请选择考试与学生查看多科总分" />
      </el-tab-pane>

      <!-- ===== 进步/退步 ===== -->
      <el-tab-pane label="进步/退步" name="progress">
        <el-form :inline="true" label-width="70px">
          <el-form-item label="课程">
            <el-select v-model="progressQuery.courseId" style="width: 200px" @change="loadProgress">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="学期">
            <el-select v-model="progressQuery.semester" style="width: 140px" clearable placeholder="全部学期" @change="loadProgress">
              <el-option v-for="s in semesters" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-radio-group v-model="progressQuery.regress" @change="loadProgress">
              <el-radio :value="false">进步榜</el-radio>
              <el-radio :value="true">退步榜</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="!progressQuery.courseId" @click="loadProgress">查询</el-button>
            <el-button @click="resetProgress">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="progress && progress.note" type="info" :closable="false" show-icon :title="progress.note" style="margin-bottom: 16px" />
        <el-table v-if="progress && progress.items && progress.items.length" :data="progress.items" border stripe>
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column prop="prevPercent" label="上次得分率" width="110">
            <template #default="{ row }">{{ row.prevPercent }}%</template>
          </el-table-column>
          <el-table-column prop="lastPercent" label="本次得分率" width="110">
            <template #default="{ row }">{{ row.lastPercent }}%</template>
          </el-table-column>
          <el-table-column label="变化">
            <template #default="{ row }">
              <span :class="row.delta >= 0 ? 'up' : 'down'">{{ row.delta >= 0 ? '+' : '' }}{{ row.delta }}%</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="请选择课程查看进步/退步分析" />
      </el-tab-pane>

      <!-- ===== 成绩预警 ===== -->
      <el-tab-pane label="成绩预警" name="warning">
        <el-form :inline="true" label-width="70px">
          <el-form-item label="考试">
            <el-select v-model="warnQuery.examId" style="width: 180px" @change="loadWarn">
              <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程">
            <el-select v-model="warnQuery.courseId" style="width: 160px" @change="loadWarn">
              <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="warnOnlyFail">仅显示不及格</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadWarn">查询</el-button>
            <el-button @click="resetWarn">重置</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="warning" :closable="false" show-icon
                  title="预警规则：得分率 &lt; 60% 记为「不及格」（红色）；结合「进步/退步」Tab 可进一步观察连续下滑学生。" style="margin-bottom: 16px" />
        <el-table :data="warnRows" border stripe>
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="studentNo" label="学号" width="120" />
          <el-table-column label="班级" width="100">
            <template #default="{ row }">{{ className(row.classId) }}</template>
          </el-table-column>
          <el-table-column prop="score" label="分数" />
          <el-table-column prop="fullScore" label="满分" width="90" />
          <el-table-column label="得分率" width="110">
            <template #default="{ row }">
              <span :class="row.percent < 60 ? 'down' : ''">{{ row.percent }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="预警" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.percent < 60" type="danger">不及格</el-tag>
              <el-tag v-else type="success">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!warnRows.length" description="暂无符合条件的数据" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import echarts from '../utils/echarts'
import { statApi, examApi, dictApi, studentApi } from '../api'

const activeTab = ref('course')
const exams = ref([])
const courses = ref([])
const classes = ref([])
const students = ref([])

// 学生总分（A3 联调点）
const studentStat = ref(null)
const studentQuery = reactive({ examId: null, studentId: null })

// 单科
const stats = ref(null)
const distChartRef = ref()
const rateChartRef = ref()
let distChart = null
let rateChart = null
const query = reactive({ examId: null, courseId: null })

// 对比
const compare = ref(null)
const compareQuery = reactive({ mode: 'exam', examId: null, semester: null, courseId: null })
const compareChartRef = ref()
let compareChart = null

// 排名
const rankData = ref(null)
const rankQuery = reactive({ examId: null, courseId: null, scope: 'class' })

// 预警（独立查询对象，避免与排名 Tab 状态交叉污染）
const warnRows = ref([])
const warnOnlyFail = ref(true)
const warnQuery = reactive({ examId: null, courseId: null })

// 进退步
const progress = ref(null)
const progressQuery = reactive({ courseId: null, semester: null, regress: false })

const semesters = ref([])

const statCards = computed(() => {
  if (!stats.value) return []
  const s = stats.value
  return [
    { label: '参考人数', value: s.total },
    { label: '满分', value: s.fullScore },
    { label: '平均分', value: s.avg },
    { label: '得分率', value: s.percentAvg + '%' },
    { label: '总得分率', value: s.totalPercent != null ? s.totalPercent + '%' : '-' },
    { label: '标准分', value: s.stdScore != null ? s.stdScore : '-' },
    { label: '最高分', value: s.max },
    { label: '最低分', value: s.min },
    { label: '及格率', value: s.passRate + '%' },
    { label: '优秀率', value: s.excellentRate + '%' }
  ]
})

const studentStatCards = computed(() => {
  if (!studentStat.value) return []
  const s = studentStat.value
  return [
    { label: '总分', value: s.totalScore != null ? s.totalScore : '-' },
    { label: '满分', value: s.totalFull != null ? s.totalFull : '-' },
    { label: '得分率', value: s.totalPercent != null ? s.totalPercent + '%' : '-' },
    { label: '标准分', value: s.stdScore != null ? s.stdScore : '-' }
  ]
})

function className(id) {
  return classes.value.find((x) => x.id === id)?.name || '未分班'
}

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
    const sid = studentQuery.studentId
    if (sid != null) {
      const m = studentMap.value.get(sid)
      if (m && !arr.find((x) => x.id === sid)) arr.unshift({ id: sid, studentNo: m.studentNo, name: m.name })
    }
    students.value = arr
  }).catch(() => {})
}

async function loadCourse() {
  if (!query.examId || !query.courseId) { stats.value = null; return }
  stats.value = await statApi.course({ examId: query.examId, courseId: query.courseId })
  await nextTick(); renderCourseCharts()
}

/** 清空单科统计条件，回到"请选择考试与课程"空态。 */
function resetCourse() {
  query.examId = null
  query.courseId = null
  stats.value = null
}

async function loadCompare() {
  if (compareQuery.mode === 'exam') {
    if (!compareQuery.examId || !compareQuery.courseId) return
    compare.value = await statApi.comparison({ examId: compareQuery.examId, courseId: compareQuery.courseId })
  } else {
    if (!compareQuery.semester || !compareQuery.courseId) return
    compare.value = await statApi.comparison({ semester: compareQuery.semester, courseId: compareQuery.courseId })
  }
  await nextTick(); renderCompareChart()
}

function onCompareModeChange() {
  compare.value = null
  loadCompare()
}

/** 清空班级对比条件回到空态。 */
function resetCompare() {
  compareQuery.mode = 'exam'
  compareQuery.examId = null
  compareQuery.semester = null
  compareQuery.courseId = null
  compare.value = null
}

/** 清空排名条件回到空态。 */
function resetRank() {
  rankQuery.examId = null
  rankQuery.courseId = null
  rankQuery.scope = 'class'
  rankData.value = null
}

async function loadRank() {
  if (!rankQuery.examId || !rankQuery.courseId) { rankData.value = null; return }
  rankData.value = await statApi.rank({ examId: rankQuery.examId, courseId: rankQuery.courseId, scope: rankQuery.scope })
}

async function loadProgress() {
  if (!progressQuery.courseId) { progress.value = null; return }
  progress.value = await statApi.progress({
    courseId: progressQuery.courseId,
    semester: progressQuery.semester || undefined,
    limit: 20,
    regress: progressQuery.regress
  })
}

/** 清空进步退步条件回到空态。 */
function resetProgress() {
  progressQuery.courseId = null
  progressQuery.semester = null
  progressQuery.regress = false
  progress.value = null
}

async function loadWarn() {
  if (!warnQuery.examId || !warnQuery.courseId) { warnRows.value = []; return }
  const r = await statApi.rank({ examId: warnQuery.examId, courseId: warnQuery.courseId, scope: 'class' })
  warnRows.value = (r.rows || []).filter((x) => !warnOnlyFail.value || x.percent < 60)
}

/** 清空预警条件回到空态。 */
function resetWarn() {
  warnQuery.examId = null
  warnQuery.courseId = null
  warnRows.value = []
}

async function loadStudentStat() {
  if (!studentQuery.examId || !studentQuery.studentId) { studentStat.value = null; return }
  studentStat.value = await statApi.student({
    examId: studentQuery.examId,
    studentId: studentQuery.studentId
  })
}

/** 清空学生总分条件回到空态。 */
function resetStudent() {
  studentQuery.examId = null
  studentQuery.studentId = null
  studentStat.value = null
}

function renderCourseCharts() {
  if (!stats.value) return
  const dist = stats.value.distribution
  if (distChartRef.value) {
    distChart = distChart || echarts.init(distChartRef.value)
    distChart.setOption({
      tooltip: { trigger: 'axis' }, grid: { left: 40, right: 20, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: Object.keys(dist) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ type: 'bar', data: Object.values(dist), itemStyle: { color: '#409eff' }, barWidth: '50%' }]
    })
  }
  if (rateChartRef.value) {
    rateChart = rateChart || echarts.init(rateChartRef.value)
    rateChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c}%' }, legend: { bottom: 0 },
      series: [{ type: 'pie', radius: ['45%', '70%'],
        data: [{ name: '及格率', value: stats.value.passRate }, { name: '优秀率', value: stats.value.excellentRate }] }]
    })
  }
}

function renderCompareChart() {
  if (!compareChartRef.value || !compare.value || !compare.value.classes?.length) return
  compareChart = compareChart || echarts.init(compareChartRef.value)
  const rows = compare.value.classes
  compareChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均分'], bottom: 0 },
    grid: { left: 50, right: 20, top: 30, bottom: 50 },
    xAxis: { type: 'category', data: rows.map((r) => r.className) },
    yAxis: { type: 'value' },
    series: [{ name: '平均分', type: 'bar', data: rows.map((r) => r.avg), itemStyle: { color: '#409eff' }, barWidth: '45%' }]
  })
}

function handleResize() { distChart?.resize(); rateChart?.resize(); compareChart?.resize() }

// el-tab-pane 对非激活面板使用 v-show（display:none），隐藏态 init 的 ECharts 会得到 0×0 画布；
// 切换 Tab 后 nextTick 触发 resize，使图表按真实尺寸重绘。
function onTabChange() {
  // 切换到预警 Tab 时若尚未加载则主动加载（避免"初进入无数据、需手动再改一次"）
  if (activeTab.value === 'warning' && warnQuery.examId && warnQuery.courseId && !warnRows.value.length) {
    loadWarn()
  }
  nextTick(() => handleResize())
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  try {
    const [e, c, cl, st] = await Promise.all([examApi.list(), dictApi.courses(), dictApi.classes(), studentApi.list({ page: 0, size: 100 })])
    // 学生下拉改为远程搜索（原 size:1000 全量会在超千时静默截断）；首屏仅取前 100 条作为底表
    mergeStudentMap(st.content || [])
    exams.value = e; courses.value = c; classes.value = cl; students.value = (st && st.content) || []
    try { semesters.value = await statApi.semesters() } catch (err) { semesters.value = []
      console.warn(err) }
    if (e.length && c.length) {
      query.examId = e[0].id; query.courseId = c[0].id; loadCourse()
      compareQuery.examId = e[0].id; compareQuery.courseId = c[0].id; loadCompare()
      rankQuery.examId = e[0].id; rankQuery.courseId = c[0].id; loadRank()
    }
    if (e.length && students.value.length) {
      studentQuery.examId = e[0].id; studentQuery.studentId = students.value[0].id; loadStudentStat()
    }
  } catch (err) {
    // P2-10 主数据加载失败：保留空态，错误由拦截器提示，避免未处理拒绝
    console.warn('统计页初始化数据加载失败', err)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  distChart?.dispose(); rateChart?.dispose(); compareChart?.dispose()
})
</script>

<style scoped>
.stat-card { text-align: center; overflow: hidden; }
.stat-card :deep(.el-card__body) { padding: 16px 8px; }
.stat-card .value {
  font-size: 26px;
  font-weight: 700;
  color: var(--el-color-primary);
  text-align: center;
  line-height: 1.2;
  word-break: break-all;
  overflow-wrap: anywhere;
}
.stat-card .label {
  text-align: center;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dist-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.dist-tag { margin-right: 0; }
/* 颜色语义校正：进步/得分率上升(delta>=0)用绿色，退步用红色；
   预警页不及格行复用 .down 类，修正后不及格自然显示为红色（原为绿色，属语义错误）。 */
.up { color: var(--el-color-success); font-weight: 600; }
.down { color: var(--el-color-danger); font-weight: 600; }
</style>
