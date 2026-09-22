<template>
  <el-card shadow="never">
    <template #header>录入小测成绩（家长录入，仅自己可见）</template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
              title="在家小测/自测成绩由家长录入，使用「自定义小测」，与校内考试完全解耦。此类成绩不会进入班级统计，老师端也不查看；录入后可随时修改或删除。" />

    <el-form :inline="true" label-width="70px" @submit.prevent>
      <el-form-item label="孩子">
        <el-select v-model="form.studentId" placeholder="选择孩子" style="width: 210px" @change="loadMine">
          <el-option v-for="b in binds" :key="b.studentId"
                     :label="b.studentNo + ' - ' + b.name" :value="b.studentId" />
        </el-select>
      </el-form-item>
      <el-form-item label="小测名称">
        <el-input v-model="customQuizName" placeholder="如：数学第3单元自测" style="width: 200px" :disabled="!!editingId" />
      </el-form-item>
      <el-form-item label="科目">
        <el-select v-model="form.courseId" placeholder="选择科目" style="width: 140px" @change="onCourseChange">
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="满分">
        <el-input-number v-model="form.fullScore" :min="1" :max="9999" style="width: 120px" />
      </el-form-item>
      <el-form-item label="得分">
        <el-input-number v-model="form.score" :min="0" :max="9999" style="width: 120px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">
          {{ editingId ? '更新' : '录入' }}
        </el-button>
        <el-button v-if="editingId" @click="reset">取消编辑</el-button>
      </el-form-item>
    </el-form>

    <el-divider />
    <h4>我录入的小测成绩</h4>
    <el-table :data="mine" v-loading="loading" border stripe>
      <el-table-column prop="studentNo" label="学号" width="110" />
      <el-table-column prop="studentName" label="姓名" width="90" />
      <el-table-column prop="examName" label="小测名称" width="180" />
      <el-table-column prop="courseName" label="科目" width="110" />
      <el-table-column label="得分/满分" width="120">
        <template #default="{ row }">{{ row.score }} / {{ row.fullScore }}</template>
      </el-table-column>
      <el-table-column label="得分率" width="110">
        <template #default="{ row }">
          <span :class="row.percent >= 60 ? 'pass' : 'fail'">{{ row.percent }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" @click="edit(row)">修改</el-button>
          <el-popconfirm title="确认删除？" @confirm="remove(row.id)">
            <template #reference>
              <el-button size="small" type="danger" link>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && mine.length === 0" description="先选择孩子，即可查看/录入小测成绩" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { parentApi, examApi, dictApi, gradeApi } from '../../api'

const binds = ref([])
const courses = ref([])
const customExams = ref([])
const mine = ref([])
const loading = ref(false)
const saving = ref(false)
const editingId = ref(null)
const customQuizName = ref('')

const form = ref({ studentId: null, examId: null, courseId: null, fullScore: 100, score: null })

async function onCourseChange() {
  const c = courses.value.find((x) => x.id === form.value.courseId)
  if (c) form.value.fullScore = c.fullScore
}

async function loadMine() {
  if (!form.value.studentId) { mine.value = []; return }
  loading.value = true
  try {
    const grades = await gradeApi.list({ studentId: form.value.studentId })
    const parentGrades = (grades || []).filter((g) => g.source === 'parent')
    const examMap = {}
    customExams.value.forEach((e) => (examMap[e.id] = e.name))
    const studentMap = {}
    binds.value.forEach((b) => (studentMap[b.studentId] = b))
    const courseNameMap = {}
    courses.value.forEach((c) => (courseNameMap[c.id] = c.name))
    mine.value = parentGrades.map((g) => {
      const b = studentMap[g.studentId] || {}
      const full = g.fullScore || 100
      return {
        id: g.id,
        studentNo: b.studentNo || '-',
        studentName: b.name || '-',
        examName: examMap[g.examId] || ('小测#' + g.examId),
        courseName: courseNameMap[g.courseId] || ('科目#' + g.courseId),
        score: g.score,
        fullScore: full,
        percent: Math.round((g.score * 100.0 / full) * 10) / 10,
        studentId: g.studentId,
        examId: g.examId,
        courseId: g.courseId
      }
    })
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.value.studentId || !form.value.courseId) {
    ElMessage.warning('请选择孩子和科目')
    return
  }
  if (form.value.score === null) {
    ElMessage.warning('请填写得分')
    return
  }
  let examId = form.value.examId
  if (!editingId.value) {
    if (!customQuizName.value) { ElMessage.warning('请填写小测名称'); return }
    const exam = await examApi.customCreate({ name: customQuizName.value })
    examId = exam.id
    // 刷新自定义小测列表
    customExams.value = await examApi.customList()
  }
  saving.value = true
  try {
    const payload = {
      studentId: form.value.studentId,
      examId,
      courseId: form.value.courseId,
      fullScore: form.value.fullScore,
      score: form.value.score
    }
    if (editingId.value) {
      await gradeApi.update(editingId.value, { ...payload, examId: form.value.examId })
      ElMessage.success('已更新')
    } else {
      await gradeApi.create(payload)
      ElMessage.success('已录入')
    }
    reset()
    loadMine()
  } catch (e) {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

function edit(row) {
  editingId.value = row.id
  form.value.studentId = row.studentId
  form.value.examId = row.examId
  form.value.courseId = row.courseId
  form.value.fullScore = row.fullScore
  form.value.score = row.score
  customQuizName.value = row.examName && row.examName.startsWith('小测#') ? '' : row.examName
}

function reset() {
  editingId.value = null
  form.value.score = null
  form.value.examId = null
  customQuizName.value = ''
}

async function remove(id) {
  try {
    await gradeApi.remove(id)
    ElMessage.success('已删除')
    loadMine()
  } catch (e) {
    // 错误已提示
  }
}

onMounted(async () => {
  binds.value = await parentApi.binds()
  courses.value = await dictApi.courses()
  customExams.value = await examApi.customList()
})
</script>

<style scoped>
.pass { color: #67c23a; }
.fail { color: #f56c6c; }
</style>
