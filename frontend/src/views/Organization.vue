<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ===== 学校管理 ===== -->
      <el-tab-pane label="学校管理" name="school">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="多租户根：每所学校独立命名空间，级部/班级/学生按学校隔离。学校编码全局唯一。" />
        <div class="toolbar">
          <el-button size="small" type="success" @click="openSchool">新增学校</el-button>
        </div>
        <el-table :data="schools" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="学校名称" />
          <el-table-column prop="code" label="学校编码" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="editSchool(row)">编辑</el-button>
              <el-button link type="danger" @click="removeSchool(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 级部管理 ===== -->
      <el-tab-pane label="级部管理" name="level">
        <div class="toolbar">
          <el-form :inline="true">
            <el-form-item label="学校">
              <el-select v-model="levelSchoolId" style="width: 200px" @change="loadLevels">
                <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button size="small" type="success" :disabled="!levelSchoolId" @click="openLevel">新增级部</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-table :data="levels" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="级部名称" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="editLevel(row)">编辑</el-button>
              <el-button link type="danger" @click="removeLevel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!levels.length && levelSchoolId" description="该学校暂无级部" />
      </el-tab-pane>

      <!-- ===== 老师-班级绑定 ===== -->
      <el-tab-pane label="老师班级归属" name="tc">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="老师与班级多对多归属：一个老师可带多个班级，一个班级可有多位老师，天然支持跨年级。用于老师数据范围（班/级/校）隔离。" />
        <div class="toolbar">
          <el-form :inline="true">
            <el-form-item label="老师">
              <el-select v-model="tcTeacherId" style="width: 200px" @change="loadTc">
                <el-option v-for="t in teachers" :key="t.id" :label="(t.nickname || t.username) + ' (' + t.username + ')'" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
        <el-form :inline="true" v-if="tcTeacherId" class="assign-form">
          <el-form-item label="归属班级">
            <el-select v-model="assignClassIds" multiple filterable style="width: 400px" placeholder="选择班级（可多选）">
              <el-option v-for="cl in schoolClasses(selectedTeacherSchool)" :key="cl.id" :label="cl.name" :value="cl.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doAssign">保存归属</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="tcRows" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="classId" label="班级ID" />
          <el-table-column label="班级名称">
            <template #default="{ row }">{{ className(row.classId) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeTc(row)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!tcRows.length" description="请选择老师查看其班级归属" />
      </el-tab-pane>

      <!-- ===== 教师管理（教师账号落库） ===== -->
      <el-tab-pane label="教师管理" name="tm">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="创建教师账号并强制归属学校（多租户前提）。CLASS 范围须绑定本校班级；SCHOOL 可见本校全部班级；ALL 为全校超管。仅校级/全校管理员可维护。" />
        <div class="toolbar">
          <el-button size="small" type="success" @click="openTeacher">新增教师</el-button>
        </div>
        <el-table :data="teacherRows" border stripe>
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="schoolId" label="学校ID" width="90" />
          <el-table-column label="数据范围" width="110">
            <template #default="{ row }">
              <el-tag :type="row.scopeType === 'ALL' ? 'danger' : (row.scopeType === 'SCHOOL' ? 'warning' : 'info')">
                {{ scopeLabel(row.scopeType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeTeacher(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!teacherRows.length" description="暂无教师账号" />
      </el-tab-pane>

      <!-- ===== 家长管理（重置家长密码） ===== -->
      <el-tab-pane label="家长管理" name="pm">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
          title="列出本校家长账号（多租户按登录校隔离）。遗失/锁定后可「重置为默认密码」= 该生身份证后 8 位（后端统一计算，操作审计留痕）。仅校级/全校管理员可执行重置。" />
        <el-table :data="parentRows" border stripe v-loading="parentsLoading">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" width="160" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="phone" label="手机号" width="140" />
          <el-table-column prop="schoolId" label="学校ID" width="90" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button :disabled="!isManager" link type="warning" @click="resetPwd(row)">重置默认密码</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!parentsLoading && !parentRows.length" description="暂无家长账号" />
      </el-tab-pane>
    </el-tabs>

    <!-- 学校/级部通用对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="420px">
      <el-form label-width="90px">
        <el-form-item v-if="editing === 'school'" label="学校编码">
          <el-input v-model="schoolCode" placeholder="请输入学校编码" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="name" placeholder="请输入名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 教师创建对话框 -->
    <el-dialog v-model="teacherDialogVisible" title="新增教师" width="480px">
      <el-form label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="teacherForm.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="teacherForm.password" type="password" show-password placeholder="6-64 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="teacherForm.nickname" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="teacherForm.phone" placeholder="可选" />
        </el-form-item>
        <el-form-item label="归属学校" required>
          <el-select v-model="teacherForm.schoolId" style="width: 100%">
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围" required>
          <el-select v-model="teacherForm.scopeType" style="width: 100%">
            <el-option label="班级（仅绑定班级）" value="CLASS" />
            <el-option label="校级（本校全部班）" value="SCHOOL" />
            <el-option label="全校（超管）" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="teacherForm.scopeType === 'CLASS'" label="绑定班级" required>
          <el-select v-model="teacherForm.classIds" multiple filterable style="width: 100%" placeholder="选择班级（可多选）">
            <el-option v-for="cl in schoolClasses(teacherForm.schoolId)" :key="cl.id" :label="cl.name" :value="cl.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="teacherDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTeacher">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { schoolApi, levelApi, teacherClassApi, authApi, dictApi } from '../api'

const activeTab = ref('school')
const schools = ref([])
const levels = ref([])
const teachers = ref([])
const classes = ref([])
const tcRows = ref([])
const levelSchoolId = ref(null)
const tcTeacherId = ref(null)
const assignClassIds = ref([])

// 教师管理
const teacherRows = ref([])
const teacherDialogVisible = ref(false)
const teacherForm = reactive({
  username: '', password: '', nickname: '', phone: '', schoolId: null, scopeType: 'CLASS', classIds: []
})

// 家长管理（重置家长密码）
const parentRows = ref([])
const parentsLoading = ref(false)
const isManager = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const editing = ref('')
const name = ref('')
const schoolCode = ref('')
let editingId = null

async function loadSchools() {
  schools.value = await schoolApi.list()
  if (schools.value.length && levelSchoolId.value == null) {
    levelSchoolId.value = schools.value[0].id
  }
}

async function loadLevels() {
  if (!levelSchoolId.value) { levels.value = []; return }
  levels.value = await levelApi.list({ schoolId: levelSchoolId.value })
}

async function loadTc() {
  if (!tcTeacherId.value) { tcRows.value = []; return }
  tcRows.value = await teacherClassApi.list({ teacherAccountId: tcTeacherId.value })
  assignClassIds.value = tcRows.value.map((r) => r.classId)
}

function onTabChange() {
  if (activeTab.value === 'level') loadLevels()
  if (activeTab.value === 'tc') loadTc()
  if (activeTab.value === 'tm') loadTeachers()
  if (activeTab.value === 'pm') loadParents()
}

function scopeLabel(t) {
  return t === 'ALL' ? '全校' : (t === 'SCHOOL' ? '校级' : '班级')
}

/** 当前选中老师的归属校（用于老师-班级绑定下拉按校过滤） */
const selectedTeacherSchool = computed(() => {
  if (!tcTeacherId.value) return null
  const t = teachers.value.find((x) => x.id === tcTeacherId.value)
  return t ? t.schoolId : null
})

/** 按学校过滤班级下拉（老师所属校/教师创建所选校），避免跨校误绑。 */
function schoolClasses(schoolId) {
  if (schoolId == null) return classes.value
  return classes.value.filter((c) => c.schoolId === schoolId)
}

async function loadTeachers() {
  teacherRows.value = await authApi.teachers()
}

async function loadParents() {
  parentsLoading.value = true
  try {
    parentRows.value = (await authApi.parents()) || []
  } finally {
    parentsLoading.value = false
  }
}

async function resetPwd(row) {
  await ElMessageBox.confirm(`确认将家长「${row.username}」的密码重置为默认密码（该生身份证后 8 位）？此操作会使其所有登录立即失效。`, '重置密码', { type: 'warning' })
  await authApi.resetParentPassword(row.id)
  ElMessage.success('已重置为默认密码')
}

function openTeacher() {
  teacherForm.username = ''; teacherForm.password = ''; teacherForm.nickname = ''
  teacherForm.phone = ''; teacherForm.schoolId = schools.value[0]?.id ?? null
  teacherForm.scopeType = 'CLASS'; teacherForm.classIds = []
  teacherDialogVisible.value = true
}

async function submitTeacher() {
  if (!teacherForm.username || !teacherForm.password) { ElMessage.warning('请填写用户名与密码'); return }
  if (teacherForm.password.length < 6) { ElMessage.warning('密码至少 6 位'); return }
  if (!teacherForm.schoolId) { ElMessage.warning('请选择归属学校'); return }
  if (teacherForm.scopeType === 'CLASS' && !teacherForm.classIds.length) { ElMessage.warning('CLASS 范围请绑定至少一个班级'); return }
  const data = {
    username: teacherForm.username, password: teacherForm.password,
    nickname: teacherForm.nickname || null, phone: teacherForm.phone || null,
    schoolId: teacherForm.schoolId, scopeType: teacherForm.scopeType,
    classIds: teacherForm.scopeType === 'CLASS' ? teacherForm.classIds : []
  }
  await authApi.createTeacher(data)
  ElMessage.success('教师创建成功')
  teacherDialogVisible.value = false
  loadTeachers()
}

async function removeTeacher(row) {
  await ElMessageBox.confirm(`确认删除教师「${row.username}」？此操作会清理其班级归属。`, '提示', { type: 'warning' })
  await authApi.removeTeacher(row.id)
  ElMessage.success('已删除')
  loadTeachers()
}

function className(id) {
  return classes.value.find((x) => x.id === id)?.name || '-' + id
}

function openSchool() { editing.value = 'school'; editingId = null; name.value = ''; schoolCode.value = ''; dialogTitle.value = '新增学校'; dialogVisible.value = true }
function openLevel() { editing.value = 'level'; editingId = null; name.value = ''; dialogTitle.value = '新增级部'; dialogVisible.value = true }
function editSchool(row) { editing.value = 'school'; editingId = row.id; name.value = row.name; schoolCode.value = row.code; dialogTitle.value = '编辑学校'; dialogVisible.value = true }
function editLevel(row) { editing.value = 'level'; editingId = row.id; name.value = row.name; dialogTitle.value = '编辑级部'; dialogVisible.value = true }

async function submit() {
  if (!name.value) { ElMessage.warning('请输入名称'); return }
  if (editing.value === 'school') {
    if (!schoolCode.value) { ElMessage.warning('请输入学校编码'); return }
    const data = { name: name.value, code: schoolCode.value }
    if (editingId) await schoolApi.update(editingId, data)
    else {
      const sc = await schoolApi.create(data)
      // 新建学校后自动切换到新校，级部管理下拉直接定位到该校，便于挂接级部
      if (sc && sc.id) levelSchoolId.value = sc.id
    }
    ElMessage.success('保存成功')
  } else if (editing.value === 'level') {
    const data = { schoolId: levelSchoolId.value, name: name.value }
    if (editingId) await levelApi.update(editingId, data)
    else await levelApi.create(data)
    ElMessage.success('保存成功')
    loadLevels()
  }
  dialogVisible.value = false
  loadSchools()
}

async function removeSchool(row) {
  await ElMessageBox.confirm(`确认删除学校「${row.name}」？`, '提示', { type: 'warning' })
  await schoolApi.remove(row.id)
  ElMessage.success('删除成功')
  loadSchools()
}

async function removeLevel(row) {
  await ElMessageBox.confirm(`确认删除级部「${row.name}」？`, '提示', { type: 'warning' })
  await levelApi.remove(row.id)
  ElMessage.success('删除成功')
  loadLevels()
}

async function doAssign() {
  if (!tcTeacherId.value || !assignClassIds.value.length) {
    ElMessage.warning('请选择老师与至少一个班级'); return
  }
  await teacherClassApi.assign({ teacherAccountId: tcTeacherId.value, classIds: assignClassIds.value })
  ElMessage.success('归属已保存')
  loadTc()
}

async function removeTc(row) {
  await ElMessageBox.confirm('确认解绑该班级归属？', '提示', { type: 'warning' })
  await teacherClassApi.remove(row.id)
  ElMessage.success('已解绑')
  loadTc()
}

onMounted(async () => {
  const [sc, cl, tc] = await Promise.all([schoolApi.list(), dictApi.classes(), authApi.teachers()])
  schools.value = sc; classes.value = cl; teachers.value = tc || []
  teacherRows.value = tc || []
  if (schools.value.length) levelSchoolId.value = schools.value[0].id
  if (teachers.value.length) tcTeacherId.value = teachers.value[0].id
  // 家长重置权限：仅 SCOPE_SCHOOL/ALL 管理员可执行（与教师管理口径一致）
  const me = JSON.parse(localStorage.getItem('auth') || 'null')
  isManager.value = !!me?.user?.scopeType && ['SCHOOL', 'ALL'].includes(me.user.scopeType)
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.assign-form {
  margin-bottom: 12px;
}
</style>
