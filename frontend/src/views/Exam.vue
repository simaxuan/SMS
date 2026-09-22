<template>
  <el-card shadow="never">
    <template #header>考试管理</template>

    <div class="toolbar">
      <el-button type="primary" @click="openDialog()">新增考试</el-button>
    </div>

    <el-table :data="exams" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="考试名称" min-width="150" />
      <el-table-column v-if="isGlobalAdmin" label="归属学校" width="140">
        <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
      </el-table-column>
      <el-table-column prop="semester" label="学期" width="110" />
      <el-table-column label="考试类型" width="120">
        <template #default="{ row }">{{ typeLabel(row.examTypeId) }}</template>
      </el-table-column>
      <el-table-column prop="examDate" label="考试日期" width="130" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && exams.length === 0" description="暂无考试，请先新增考试" />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑考试' : '新增考试'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="考试名称" required>
          <el-input v-model="form.name" placeholder="如：第二次月考" />
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="form.examTypeId" style="width: 100%" clearable placeholder="选择类型">
            <el-option v-for="t in examTypes" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.semester" style="width: 100%" clearable filterable allow-create
                     default-first-option placeholder="选择或输入新学期的简短名称，如 2026春">
            <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isGlobalAdmin" label="归属学校">
          <el-select v-model="form.schoolId" style="width: 100%" placeholder="选择学校（不选则默认当前登录校）">
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试日期">
          <el-date-picker v-model="form.examDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { examApi, dictApi, statApi, schoolApi } from '../api'
import { useAuth } from '../composables/useAuth'

const { auth } = useAuth()
const isGlobalAdmin = computed(() => auth.value?.user?.scopeType === 'ALL')

const exams = ref([])
const examTypes = ref([])
const schools = ref([])
const semesterOptions = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({ id: null, name: '', examTypeId: null, semester: '', examDate: '', schoolId: null })

function schoolName(id) {
  return schools.value.find((s) => s.id === id)?.name || '-'
}

async function load() {
  loading.value = true
  try {
    exams.value = await examApi.list()
  } finally {
    loading.value = false
  }
}

function typeLabel(id) {
  return examTypes.value.find((x) => x.id === id)?.name || '-'
}

function openDialog(row) {
  if (row) {
    form.value = { id: row.id, name: row.name, examTypeId: row.examTypeId, semester: row.semester || '', examDate: row.examDate, schoolId: row.schoolId ?? null }
  } else {
    form.value = { id: null, name: '', examTypeId: null, semester: '', examDate: '', schoolId: isGlobalAdmin.value ? null : (auth.value?.user?.schoolId ?? null) }
  }
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.name) { ElMessage.warning('请填写考试名称'); return }
  const payload = {
    name: form.value.name,
    examTypeId: form.value.examTypeId || null,
    semester: form.value.semester || null,
    examDate: form.value.examDate || null,
    ...(isGlobalAdmin.value ? { schoolId: form.value.schoolId || null } : {})
  }
  if (form.value.id) {
    await examApi.update(form.value.id, payload)
    ElMessage.success('更新成功')
  } else {
    await examApi.create(payload)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除考试「${row.name}」？`, '提示', { type: 'warning' })
  await examApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(async () => {
  examTypes.value = await dictApi.examTypes()
  if (isGlobalAdmin.value) {
    try { schools.value = (await schoolApi.list()) || [] } catch (e) { schools.value = [] }
  }
  try {
    semesterOptions.value = await statApi.semesters()
  } catch (e) {
    semesterOptions.value = []
  }
  load()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>
