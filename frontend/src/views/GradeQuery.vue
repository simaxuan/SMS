<template>
  <el-card shadow="never">
    <template #header>成绩查询</template>
    <el-form :inline="true" label-width="70px">
      <el-form-item label="考试">
        <el-select v-model="query.examId" clearable style="width: 180px" placeholder="全部考试">
          <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程">
        <el-select v-model="query.courseId" clearable style="width: 160px" placeholder="全部课程">
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="学生">
        <!-- 学生选择改为远程搜索：避免一次性拉全量（原 size:1000 静默截断），首屏仅加载前 100 条；
             反查显示走 studentMap 缓存，命中不到时按 id 补查，杜绝选中/列表空白 -->
        <el-select v-model="query.studentId" filterable clearable remote :remote-method="searchStudents" style="width: 200px" placeholder="输入学号/姓名搜索" @change="load">
          <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' - ' + s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" :disabled="!grades.length" @click="exportCsv">导出CSV</el-button>
        <el-button :disabled="!grades.length" @click="printView">打印</el-button>
      </el-form-item>
    </el-form>

    <div id="print-area">
    <el-table :data="grades" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="学号" width="120">
        <template #default="{ row }">{{ studentNo(row.studentId) }}</template>
      </el-table-column>
      <el-table-column label="学生姓名" width="120">
        <template #default="{ row }">{{ studentName(row.studentId) }}</template>
      </el-table-column>
      <el-table-column label="考试" width="140">
        <template #default="{ row }">{{ examLabel(row.examId) }}</template>
      </el-table-column>
      <el-table-column label="课程" width="120">
        <template #default="{ row }">{{ courseLabel(row.courseId) }}</template>
      </el-table-column>
      <el-table-column prop="score" label="分数" sortable />
      <el-table-column prop="fullScore" label="满分" width="90" />
      <el-table-column label="得分率" width="100">
        <template #default="{ row }">{{ percentText(row) }}</template>
      </el-table-column>
    </el-table>
    </div>
    <el-empty v-if="!loading && !grades.length" description="暂无符合条件的成绩，请调整筛选条件" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { gradeApi, examApi, dictApi, studentApi } from '../api'
const exams = ref([])
const courses = ref([])
const students = ref([])
const grades = ref([])
const loading = ref(false)
const query = reactive({ examId: null, courseId: null, studentId: null })

// 学生下拉远程搜索：studentMap 缓存 id→{学号,姓名}，供表格反查显示，命中不到时按 id 补查
const studentMap = ref(new Map())
function mergeStudentMap(list) {
  ;(list || []).forEach((s) => studentMap.value.set(s.id, { studentNo: s.studentNo, name: s.name }))
  studentMap.value = new Map(studentMap.value)
}
function ensureStudent(id) {
  if (id == null || studentMap.value.has(id)) return
  // 反查兜底：列表里出现、但本地未缓存的学生，按 id 单查后写入缓存（响应式更新即回显）
  studentApi.get(id).then((s) => {
    if (s) { studentMap.value.set(s.id, { studentNo: s.studentNo, name: s.name }); studentMap.value = new Map(studentMap.value) }
  }).catch(() => {})
}
function searchStudents(kw) {
  studentApi.list({ keyword: kw || '', page: 0, size: 50 }).then((res) => {
    const list = (res && res.content) || []
    mergeStudentMap(list)
    const arr = [...list]
    const sid = query.studentId
    if (sid != null) {
      const m = studentMap.value.get(sid)
      if (m && !arr.find((x) => x.id === sid)) arr.unshift({ id: sid, studentNo: m.studentNo, name: m.name })
    }
    students.value = arr
  }).catch(() => {})
}

async function load() {
  loading.value = true
  try {
    grades.value = await gradeApi.list({
      examId: query.examId || undefined,
      courseId: query.courseId || undefined,
      studentId: query.studentId || undefined
    })
  } finally {
    loading.value = false
  }
}

function reset() {
  query.examId = null
  query.courseId = null
  query.studentId = null
  load()
}

function examLabel(id) { return exams.value.find((x) => x.id === id)?.name || '-' }
function courseLabel(id) { return courses.value.find((x) => x.id === id)?.name || '-' }
function studentNo(id) { const m = studentMap.value.get(id); if (m) return m.studentNo; ensureStudent(id); return '-' }
function studentName(id) { const m = studentMap.value.get(id); if (m) return m.name; ensureStudent(id); return '-' }

function percentText(row) {
  const f = row.fullScore || 100
  if (!f) return '-'
  return (row.score * 100 / f).toFixed(1) + '%'
}

function toCsvCell(v) {
  let s = String(v ?? '')
  // #22 CSV 公式注入防范：以 = + - @ 开头的文本前加单引号，避免被 Excel/Sheets 解析为公式
  if (/^[=+\-@]/.test(s)) s = "'" + s
  return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
}

function exportCsv() {
  if (!grades.value.length) return
  const header = ['学号', '姓名', '考试', '课程', '分数', '满分', '得分率']
  const rows = grades.value.map((r) => [
    studentNo(r.studentId), studentName(r.studentId), examLabel(r.examId), courseLabel(r.courseId),
    r.score, r.fullScore, percentText(r)
  ])
  // UTF-8 BOM 保证 Excel 打开中文不乱码
  const csv = '\uFEFF' + [header, ...rows].map((arr) => arr.map(toCsvCell).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '成绩导出_' + new Date().toISOString().slice(0, 10) + '.csv'
  a.click()
  URL.revokeObjectURL(url)
}

function printView() {
  window.print()
}

  onMounted(async () => {
  const [e, c, s] = await Promise.all([examApi.list(), dictApi.courses(), studentApi.list({ page: 0, size: 100 })])
  // 学生下拉改为远程搜索（原 size:1000 全量会在超千时静默截断）；首屏仅取前 100 条作为底表
  mergeStudentMap(s.content || [])
  exams.value = e
  courses.value = c
  students.value = s.content || []
  load()
})
</script>

<style>
/* 打印：仅保留成绩表格区域 */
@media print {
  body * { visibility: hidden; }
  #print-area, #print-area * { visibility: visible; }
  #print-area { position: absolute; left: 0; top: 0; width: 100%; }
}
</style>
