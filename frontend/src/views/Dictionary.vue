<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>班级管理</span>
              <el-button size="small" type="success" @click="openClass">新增班级</el-button>
            </div>
          </template>
          <el-table :data="classes" border stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="班级名称" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="editClass(row)">编辑</el-button>
                <el-button link type="danger" @click="removeClass(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>课程管理</span>
              <el-button size="small" type="success" @click="openCourse">新增课程</el-button>
            </div>
          </template>
          <el-table :data="courses" border stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="课程名称" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="editCourse(row)">编辑</el-button>
                <el-button link type="danger" @click="removeCourse(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="dialogType + '名称'" width="420px">
      <el-input v-model="name" placeholder="请输入名称" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dictApi } from '../api'

const classes = ref([])
const courses = ref([])
const dialogVisible = ref(false)
const dialogType = ref('')
const name = ref('')
let editingId = null
let editing = ''

async function load() {
  classes.value = await dictApi.classes()
  courses.value = await dictApi.courses()
}

function openClass() { editing = 'class'; editingId = null; name.value = ''; dialogType.value = '新增班级'; dialogVisible.value = true }
function openCourse() { editing = 'course'; editingId = null; name.value = ''; dialogType.value = '新增课程'; dialogVisible.value = true }
function editClass(row) { editing = 'class'; editingId = row.id; name.value = row.name; dialogType.value = '编辑班级'; dialogVisible.value = true }
function editCourse(row) { editing = 'course'; editingId = row.id; name.value = row.name; dialogType.value = '编辑课程'; dialogVisible.value = true }

async function submit() {
  if (!name.value) { ElMessage.warning('请输入名称'); return }
  if (editing === 'class') {
    if (editingId) await dictApi.updateClass(editingId, { name: name.value })
    else await dictApi.createClass({ name: name.value })
  } else {
    if (editingId) await dictApi.updateCourse(editingId, { name: name.value })
    else await dictApi.createCourse({ name: name.value })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function removeClass(row) {
  await ElMessageBox.confirm(`确认删除班级「${row.name}」？`, '提示', { type: 'warning' })
  await dictApi.removeClass(row.id)
  ElMessage.success('删除成功')
  load()
}

async function removeCourse(row) {
  await ElMessageBox.confirm(`确认删除课程「${row.name}」？`, '提示', { type: 'warning' })
  await dictApi.removeCourse(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
