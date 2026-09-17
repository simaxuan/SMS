<template>
  <el-card shadow="never">
    <template #header>成绩录入</template>
    <el-form :inline="true" :model="form" label-width="70px">
      <el-form-item label="考试">
        <el-select v-model="form.examId" style="width: 180px" placeholder="请选择考试">
          <el-option v-for="e in exams" :key="e.id" :label="e.name" :value="e.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程">
        <el-select v-model="form.courseId" style="width: 160px" placeholder="请选择课程">
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="学生">
        <el-select v-model="form.studentId" filterable style="width: 200px" placeholder="请选择学生">
          <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' - ' + s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="分数">
        <el-input-number v-model="form.score" :min="0" :max="100" style="width: 140px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="create">录入成绩</el-button>
        <el-button type="success" @click="openBatch">批量录入</el-button>
      </el-form-item>
    </el-form>

    <el-divider>已录入成绩</el-divider>

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
      <el-table-column prop="score" label="分数" width="100" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="batchVisible" title="批量录入" width="620px">
      <div class="batch-header">
        <span>考试：{{ examLabel(form.examId) }} | 课程：{{ courseLabel(form.courseId) }}</span>
        <el-checkbox v-model="selectAll" @change="toggleAll">全选</el-checkbox>
      </div>
      <el-table :data="students" border height="400">
        <el-table-column width="50">
          <template #default="{ row }">
            <el-checkbox v-model="selectedStudents" :value="row.id">{{ '' }}</el-checkbox>
          </template>
        </el-table-column>
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column label="分数">
          <template #default="{ row }">
            <el-input-number v-model="batchScores[row.id]" :min="0" :max="100" style="width: 140px" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" @click="batchSubmit">批量保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { gradeApi, examApi, dictApi, studentApi } from '../api'

const exams = ref([])
const courses = ref([])
const students = ref([])
const grades = ref([])
const loading = ref(false)

const form = reactive({ examId: null, courseId: null, studentId: null, score: 60 })

const batchVisible = ref(false)
const selectAll = ref(false)
const selectedStudents = ref([])
const batchScores = reactive({})

async function load() {
  loading.value = true
  try {
    grades.value = await gradeApi.list({})
  } finally {
    loading.value = false
  }
}

function examLabel(id) { return exams.value.find((x) => x.id === id)?.name || '-' }
function courseLabel(id) { return courses.value.find((x) => x.id === id)?.name || '-' }
function studentLabel(id) { const s = students.value.find((x) => x.id === id); return s ? s.name : '-' }

async function create() {
  if (!form.examId || !form.courseId || !form.studentId) { ElMessage.warning('请选择考试/课程/学生'); return }
  await gradeApi.create(form)
  ElMessage.success('录入成功')
  load()
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
  batchVisible.value = true
}

function toggleAll() {
  selectedStudents.value = selectAll.value ? students.value.map((s) => s.id) : []
}

async function batchSubmit() {
  const reqs = selectedStudents.value.map((id) => ({
    studentId: id,
    examId: form.examId,
    courseId: form.courseId,
    score: batchScores[id] ?? 60
  }))
  if (!reqs.length) { ElMessage.warning('请选择学生'); return }
  const res = await gradeApi.batch(reqs)
  ElMessage.success(`批量录入成功，新增 ${res.created} 条`)
  batchVisible.value = false
  load()
}

onMounted(async () => {
  const [e, c, s] = await Promise.all([examApi.list(), dictApi.courses(), studentApi.list({ size: 100 })])
  exams.value = e
  courses.value = c
  students.value = s.content
  load()
})
</script>

<style scoped>
.batch-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  color: #606266;
}
</style>
