<template>
  <el-card shadow="never">
    <template #header>成绩录入</template>
    <el-form :inline="true" :model="form" label-width="70px">
      <el-form-item label="考试">
        <el-select v-model="form.examId" clearable style="width: 180px" placeholder="全部考试" @change="load">
          <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程">
        <el-select v-model="form.courseId" clearable style="width: 160px" placeholder="全部课程" @change="onCourseChange">
          <el-option v-for="c in courses" :key="c.id" :label="c.name + '（满分' + (c.fullScore || 100) + '）'" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="学生">
        <!-- 学生选择改为远程搜索：避免一次性拉全量（原 size:1000 静默截断），首屏仅加载前 100 条 -->
        <el-select v-model="form.studentId" filterable clearable remote :remote-method="searchStudents" style="width: 200px" placeholder="输入学号/姓名搜索" @change="load">
          <el-option v-for="s in studentOptions" :key="s.id" :label="s.studentNo + ' - ' + s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="满分">
        <el-input-number v-model="form.fullScore" :min="1" :step="10" style="width: 130px" />
      </el-form-item>
      <el-form-item label="分数">
        <el-input-number v-model="form.score" :min="0" :max="form.fullScore" style="width: 140px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="create">录入成绩</el-button>
        <el-button type="success" @click="openBatch">批量录入</el-button>
      </el-form-item>
    </el-form>

    <el-divider />
    <div class="list-header">
      <span class="list-title">已录入成绩</span>
      <span class="list-tools">
        <el-button size="small" type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button size="small" @click="resetQuery">重置</el-button>
      </span>
    </div>

    <el-table :data="grades" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="学生" width="140">
        <template #default="{ row }">{{ studentLabel(row.studentId) }}</template>
      </el-table-column>
      <el-table-column label="考试" width="140">
        <template #default="{ row }">{{ examLabel(row.examId) }}</template>
      </el-table-column>
      <el-table-column label="课程" width="120">
        <template #default="{ row }">{{ courseLabel(row.courseId) }}</template>
      </el-table-column>
      <el-table-column prop="score" label="分数" width="90" />
      <el-table-column prop="fullScore" label="满分" width="90" />
      <el-table-column label="得分率" width="100">
        <template #default="{ row }">{{ percentText(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !grades.length" description="暂无已录入成绩，请先在上方选择考试/课程/学生后录入" />

    <el-dialog v-model="editVisible" title="修改成绩" width="480px">
      <el-form label-width="60px">
        <el-form-item label="学生">
          <el-select v-model="editForm.studentId" filterable remote :remote-method="searchStudents" style="width: 100%" placeholder="输入学号/姓名搜索">
            <el-option v-for="s in studentOptions" :key="s.id" :label="s.studentNo + ' - ' + s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试">
          <el-select v-model="editForm.examId" style="width: 100%">
            <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="editForm.courseId" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="满分">
          <el-input-number v-model="editForm.fullScore" :min="1" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分数">
          <el-input-number v-model="editForm.score" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="批量录入" width="640px">
      <div class="batch-header">
        <span>考试：{{ examLabel(form.examId) }} | 课程：{{ courseLabel(form.courseId) }}</span>
        <span>满分：<el-input-number v-model="batchFullScore" :min="1" :step="10" size="small" style="width: 120px" /></span>
        <el-checkbox v-model="selectAll" @change="toggleAll">全选</el-checkbox>
      </div>
      <el-table :data="batchStudents" border height="400" v-loading="batchLoading">
        <el-table-column width="50">
          <template #default="{ row }">
            <!-- #17 修复：独立 checkbox（非 el-checkbox-group）不能 v-model 数组；用 :model-value + @change 手动维护 -->
            <el-checkbox :model-value="selectedStudents.includes(row.id)" @change="(v) => toggleRow(row.id, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column label="分数">
          <template #default="{ row }">
            <el-input-number v-model="batchScores[row.id]" :min="0" style="width: 140px" />
          </template>
        </el-table-column>
      </el-table>
      <!-- 批量表格改为分页：不再一次性装载全量学生，避免 size:1000 静默截断 -->
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="batchTotal"
        :current-page="batchPage + 1"
        :page-size="batchSize"
        @current-change="(p) => { batchPage = p - 1; loadBatchStudents() }" />
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="batchSubmit">批量保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { gradeApi, examApi, dictApi, studentApi } from '../api'

const exams = ref([])
const courses = ref([])
const grades = ref([])
const loading = ref(false)

// 学生下拉改为远程搜索：studentOptions 仅作下拉底表，studentMap 缓存 id→{学号,姓名} 供反查与回显
const studentOptions = ref([])
const studentMap = ref(new Map())
// 批量录入表格改为分页加载，不再一次性装载全量学生
const batchStudents = ref([])
const batchPage = ref(0)
const batchSize = ref(20)
const batchTotal = ref(0)
const batchLoading = ref(false)

const form = reactive({ examId: null, courseId: null, studentId: null, score: 60, fullScore: 100 })

const batchVisible = ref(false)
const selectAll = ref(false)
const selectedStudents = ref([])
const batchScores = reactive({})
const batchFullScore = ref(100)

const editVisible = ref(false)
const editForm = reactive({ id: null, studentId: null, examId: null, courseId: null, score: 0, fullScore: 100 })
const submitting = ref(false) // P2-11 防重复提交：录入/修改/批量提交期间禁用按钮

// ===== 学生下拉远程搜索 + 反查缓存（替代原 size:1000 全量装载）=====
function mergeStudentMap(list) {
  ;(list || []).forEach((s) => studentMap.value.set(s.id, { studentNo: s.studentNo, name: s.name }))
  studentMap.value = new Map(studentMap.value)
}
// 反查兜底：表格中出现但本地未缓存的学生，按 id 单查后写入缓存（响应式更新即回显，杜绝空白）
function ensureStudent(id) {
  if (id == null || studentMap.value.has(id)) return
  studentApi.get(id).then((s) => {
    if (s) { studentMap.value.set(s.id, { studentNo: s.studentNo, name: s.name }); studentMap.value = new Map(studentMap.value) }
  }).catch(() => {})
}
// 远程结果替换下拉项时，保留顶部/编辑框中已选中的学生，保证选中后不空白
function buildOptions(list) {
  const arr = [...(list || [])]
  const selIds = [form.studentId, editForm.studentId].filter((id) => id != null)
  for (const sid of selIds) {
    const m = studentMap.value.get(sid)
    if (m && !arr.find((x) => x.id === sid)) arr.unshift({ id: sid, studentNo: m.studentNo, name: m.name })
  }
  return arr
}
function searchStudents(kw) {
  studentApi.list({ keyword: kw || '', page: 0, size: 50 }).then((res) => {
    const list = (res && res.content) || []
    mergeStudentMap(list)
    studentOptions.value = buildOptions(list)
  }).catch(() => {})
}
// 批量录入表格分页加载
async function loadBatchStudents() {
  batchLoading.value = true
  try {
    const res = await studentApi.list({ page: batchPage.value, size: batchSize.value })
    batchStudents.value = res.content || []
    batchTotal.value = res.totalElements ?? res.total ?? 0
  } finally {
    batchLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    grades.value = await gradeApi.list({
      examId: form.examId || undefined,
      courseId: form.courseId || undefined,
      studentId: form.studentId || undefined
    })
  } finally {
    loading.value = false
  }
}

function examLabel(id) { return exams.value.find((x) => x.id === id)?.name || '-' }
function courseLabel(id) { return courses.value.find((x) => x.id === id)?.name || '-' }
function studentLabel(id) { const m = studentMap.value.get(id); if (m) return m.name; ensureStudent(id); return '-' }

function onCourseChange() {
  const c = courses.value.find((x) => x.id === form.courseId)
  if (c && c.fullScore) form.fullScore = c.fullScore
  load()
}

function percentText(row) {
  const f = row.fullScore || 100
  if (!f) return '-'
  return (row.score * 100 / f).toFixed(1) + '%'
}

/** 重置成绩列表筛选条件并回到全量数据。 */
function resetQuery() {
  form.examId = null
  form.courseId = null
  form.studentId = null
  load()
}

async function create() {
  if (!form.examId || !form.courseId || !form.studentId) { ElMessage.warning('请选择考试/课程/学生'); return }
  if (submitting.value) return
  submitting.value = true
  try {
    await gradeApi.create(form)
    ElMessage.success('录入成功')
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row) {
  editForm.id = row.id
  editForm.studentId = row.studentId
  editForm.examId = row.examId
  editForm.courseId = row.courseId
  editForm.score = row.score
  editForm.fullScore = row.fullScore
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.studentId || !editForm.examId || !editForm.courseId) {
    ElMessage.warning('请选择学生/考试/科目')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await gradeApi.update(editForm.id, {
      studentId: editForm.studentId,
      examId: editForm.examId,
      courseId: editForm.courseId,
      score: editForm.score,
      fullScore: editForm.fullScore
    })
    ElMessage.success('修改成功')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该成绩？', '提示', { type: 'warning' })
  await gradeApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

function openBatch() {
  if (!form.examId || !form.courseId) { ElMessage.warning('请先选择考试与课程'); return }
  selectedStudents.value = []
  Object.keys(batchScores).forEach((k) => delete batchScores[k])
  const c = courses.value.find((x) => x.id === form.courseId)
  batchFullScore.value = (c && c.fullScore) || form.fullScore || 100
  batchPage.value = 0
  loadBatchStudents()
  batchVisible.value = true
}

function toggleAll() {
  selectedStudents.value = selectAll.value ? batchStudents.value.map((s) => s.id) : []
}

/** #17 单行勾选切换：手动维护 selectedStudents 数组。 */
function toggleRow(id, checked) {
  const set = new Set(selectedStudents.value)
  if (checked) set.add(id)
  else set.delete(id)
  selectedStudents.value = Array.from(set)
  selectAll.value = selectedStudents.value.length > 0 && selectedStudents.value.length === batchStudents.value.length
}

async function batchSubmit() {
  const reqs = []
  for (const id of selectedStudents.value) {
    if (batchScores[id] === undefined || batchScores[id] === null) {
      continue // 未填分数的学生跳过，避免误录
    }
    reqs.push({
      studentId: id,
      examId: form.examId,
      courseId: form.courseId,
      score: batchScores[id],
      fullScore: batchFullScore.value
    })
  }
  if (!reqs.length) { ElMessage.warning('请至少为一名已选学生填写分数'); return }
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await gradeApi.batch(reqs)
    ElMessage.success(`批量录入成功，新增 ${res.created} 条${reqs.length !== selectedStudents.value.length ? '（未填分数已跳过）' : ''}`)
    batchVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    const [e, c, s] = await Promise.all([examApi.list(), dictApi.courses(), studentApi.list({ page: 0, size: 100 })])
    // 学生下拉改为远程搜索（原 size:1000 全量会在超千时静默截断）；首屏仅取前 100 条作为底表，批量表格走分页
    mergeStudentMap(s.content || [])
    exams.value = e
    courses.value = c
    studentOptions.value = s.content || []
    loadBatchStudents()
    load()
  } catch (e) {
    // 错误已由拦截器统一提示
  }
})
</script>

<style scoped>
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.list-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.batch-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
}
</style>
