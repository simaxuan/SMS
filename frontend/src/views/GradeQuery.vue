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
        <el-select v-model="query.studentId" filterable clearable style="width: 200px" placeholder="全部学生">
          <el-option v-for="s in students" :key="s.id" :label="s.studentNo + ' - ' + s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

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
    </el-table>
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
function studentNo(id) { return students.value.find((x) => x.id === id)?.studentNo || '-' }
function studentName(id) { return students.value.find((x) => x.id === id)?.name || '-' }

onMounted(async () => {
  const [e, c, s] = await Promise.all([examApi.list(), dictApi.courses(), studentApi.list({ size: 100 })])
  exams.value = e
  courses.value = c
  students.value = s.content
  load()
})
</script>
