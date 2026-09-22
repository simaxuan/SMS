<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab">
      <!-- ===== 班级管理（支持挂接学校+级部）===== -->
      <el-tab-pane label="班级管理" name="class">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="班级需挂接到级部（可选）、归属学校（必选），构成 学校→级部→班级 组织链。普通老师默认归属其登录校，全局管理员可跨校指定。" />
        <div class="toolbar">
          <el-button size="small" type="success" @click="openClass">新增班级</el-button>
        </div>
        <el-table :data="classes" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="班级名称" />
          <el-table-column label="归属学校">
            <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
          </el-table-column>
          <el-table-column label="归属级部">
            <template #default="{ row }">{{ levelName(row.levelId) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="editClass(row)">编辑</el-button>
              <el-button link type="danger" @click="removeClass(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!classes.length" description="暂无班级" />
      </el-tab-pane>

      <!-- ===== 课程管理 ===== -->
      <el-tab-pane label="课程管理" name="course">
        <div class="toolbar">
          <el-button size="small" type="success" @click="openCourse">新增课程</el-button>
        </div>
        <el-table :data="courses" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="课程名称" />
          <el-table-column prop="fullScore" label="默认满分" width="120" />
          <el-table-column label="归属学校">
            <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="editCourse(row)">编辑</el-button>
              <el-button link type="danger" @click="removeCourse(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!courses.length" description="暂无课程" />
      </el-tab-pane>

      <!-- ===== 考试类型 ===== -->
      <el-tab-pane label="考试类型" name="examType">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="考试类型用于区分考试场次性质（如期中、期末、月考、模拟考等），新增考试时关联。已被考试引用的类型不可删除。" />
        <div class="toolbar">
          <el-button size="small" type="success" @click="openExamType">新增考试类型</el-button>
        </div>
        <el-table :data="examTypes" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="考试类型名称" />
          <el-table-column label="归属学校">
            <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="editExamType(row)">编辑</el-button>
              <el-button link type="danger" @click="removeExamType(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!examTypes.length" description="暂无考试类型" />
      </el-tab-pane>
    </el-tabs>

    <!-- 名称/满分/归属 通用对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="460px">
      <el-form label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item v-if="editing === 'course'" label="默认满分">
          <el-input-number v-model="fullScore" :min="1" :step="10" />
          <div class="tip">该课程默认满分，录入成绩时可针对某次考试单独调整。</div>
        </el-form-item>
        <el-form-item v-if="editing === 'class'" label="归属学校" required>
          <el-select v-model="selSchoolId" style="width: 100%" @change="loadSelLevels" placeholder="选择学校">
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing === 'class'" label="归属级部">
          <el-select v-model="selLevelId" clearable style="width: 100%" placeholder="选择级部（可选）">
            <el-option v-for="lv in selLevels" :key="lv.id" :label="lv.name" :value="lv.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing === 'course' || editing === 'examType'" label="归属学校">
          <el-select v-model="selSchoolId" clearable style="width: 100%" placeholder="留空则按登录上下文（普通老师）">
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
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
import { ref, onMounted } from 'vue'
import { dictApi, schoolApi, levelApi } from '../api'

const activeTab = ref('class')
const classes = ref([])
const courses = ref([])
const examTypes = ref([])
const schools = ref([])
/** 全校级部（用于名称映射） */
const allLevels = ref([])
/** 所选学校下的级部（用于班级挂接） */
const selLevels = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editing = ref('')
const name = ref('')
const fullScore = ref(100)
const selSchoolId = ref(null)
const selLevelId = ref(null)
let editingId = null

function schoolName(id) {
  return schools.value.find((s) => s.id === id)?.name || (id == null ? '(未归属)' : '-' + id)
}

function levelName(id) {
  return allLevels.value.find((lv) => lv.id === id)?.name || (id == null ? '(未挂接)' : '-' + id)
}

async function loadSelLevels() {
  if (!selSchoolId.value) { selLevels.value = []; return }
  selLevels.value = await levelApi.list({ schoolId: selSchoolId.value })
}

async function load() {
  const sc = await schoolApi.list()
  schools.value = sc || []
  const lv = await levelApi.list({})
  allLevels.value = lv || []
  const [cls, cs, et] = await Promise.all([
    dictApi.classes(),
    dictApi.courses(),
    dictApi.examTypes()
  ])
  classes.value = cls
  courses.value = cs
  examTypes.value = et
}

function openClass() {
  editing.value = 'class'; editingId = null; name.value = ''
  selSchoolId.value = schools.value[0]?.id ?? null; selLevelId.value = null
  dialogTitle.value = '新增班级'; dialogVisible.value = true
  loadSelLevels()
}
function openCourse() {
  editing.value = 'course'; editingId = null; name.value = ''; fullScore.value = 100
  selSchoolId.value = null
  dialogTitle.value = '新增课程'; dialogVisible.value = true
}
function openExamType() {
  editing.value = 'examType'; editingId = null; name.value = ''
  selSchoolId.value = null
  dialogTitle.value = '新增考试类型'; dialogVisible.value = true
}

function editClass(row) {
  editing.value = 'class'; editingId = row.id; name.value = row.name
  selSchoolId.value = row.schoolId ?? null; selLevelId.value = row.levelId ?? null
  dialogTitle.value = '编辑班级'; dialogVisible.value = true
  loadSelLevels()
}
function editCourse(row) {
  editing.value = 'course'; editingId = row.id; name.value = row.name; fullScore.value = row.fullScore || 100
  selSchoolId.value = row.schoolId ?? null
  dialogTitle.value = '编辑课程'; dialogVisible.value = true
}
function editExamType(row) {
  editing.value = 'examType'; editingId = row.id; name.value = row.name
  selSchoolId.value = row.schoolId ?? null
  dialogTitle.value = '编辑考试类型'; dialogVisible.value = true
}

async function submit() {
  if (!name.value) { ElMessage.warning('请输入名称'); return }
  if (editing.value === 'class') {
    if (selSchoolId.value == null) { ElMessage.warning('请选择归属学校'); return }
    const data = { name: name.value, schoolId: selSchoolId.value, levelId: selLevelId.value ?? null }
    if (editingId) await dictApi.updateClass(editingId, data)
    else await dictApi.createClass(data)
  } else if (editing.value === 'course') {
    const data = { name: name.value, fullScore: fullScore.value, schoolId: selSchoolId.value ?? null }
    if (editingId) await dictApi.updateCourse(editingId, data)
    else await dictApi.createCourse(data)
  } else {
    const data = { name: name.value, schoolId: selSchoolId.value ?? null }
    if (editingId) await dictApi.updateExamType(editingId, data)
    else await dictApi.createExamType(data)
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

async function removeExamType(row) {
  await ElMessageBox.confirm(`确认删除考试类型「${row.name}」？`, '提示', { type: 'warning' })
  await dictApi.removeExamType(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  width: 100%;
}
</style>
