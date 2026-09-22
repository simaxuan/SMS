<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索学号/姓名" clearable style="width: 240px" @keyup.enter="load" @clear="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-button type="success" @click="openDialog()">新增学生</el-button>
    </div>

    <el-table :data="list" border stripe v-loading="loading">
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="gender" label="性别" width="80" />
      <el-table-column label="班级" width="100">
        <template #default="{ row }">{{ className(row.classId) }}</template>
      </el-table-column>
      <el-table-column prop="birthDate" label="出生日期" width="130" />
      <el-table-column prop="phone" label="联系电话" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, prev, pager, next, sizes"
      :total="total"
      :current-page="page + 1"
      :page-size="size"
      :page-sizes="[10, 20, 50]"
      @current-change="(p) => { page = p - 1; load() }"
      @size-change="(s) => { size = s; page = 0; load() }"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑学生' : '新增学生'" width="520px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学号" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" style="width: 100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" prop="classId">
          <el-select v-model="form.classId" style="width: 100%" placeholder="请选择班级">
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" placeholder="18 位身份证号（选填）" />
        </el-form-item>
        <el-form-item label="父手机号" prop="fatherPhone">
          <el-input v-model="form.fatherPhone" placeholder="父亲手机号（选填）" />
        </el-form-item>
        <el-form-item label="母手机号" prop="motherPhone">
          <el-input v-model="form.motherPhone" placeholder="母亲手机号（选填）" />
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
import { ref, reactive, onMounted } from 'vue'
import { studentApi, dictApi } from '../api'

const list = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)
const classes = ref([])

const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, studentNo: '', name: '', gender: '', classId: null, birthDate: '', phone: '', idCard: '', fatherPhone: '', motherPhone: '' })

const idCardValidate = (rule, value, callback) => {
  if (!value) return callback()
  // 18 位：前 17 位数字 + 末位数字或 X
  if (!/^\d{17}[\dX]$/.test(value)) return callback(new Error('身份证号格式不正确（应为 18 位，末位为数字或 X）'))
  callback()
}

const rules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idCard: [{ validator: idCardValidate, trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await studentApi.list({ keyword: keyword.value, page: page.value, size: size.value })
    list.value = res.content
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function className(id) {
  const c = classes.value.find((x) => x.id === id)
  return c ? c.name : '-'
}

/** 清空搜索关键字并回到第一页全量数据。 */
function reset() {
  keyword.value = ''
  page.value = 0
  load()
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { id: row.id, studentNo: row.studentNo, name: row.name, gender: row.gender, classId: row.classId, birthDate: row.birthDate, phone: row.phone, idCard: row.idCard || '', fatherPhone: row.fatherPhone || '', motherPhone: row.motherPhone || '' })
  } else {
    Object.assign(form, { id: null, studentNo: '', name: '', gender: '', classId: null, birthDate: '', phone: '', idCard: '', fatherPhone: '', motherPhone: '' })
  }
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  // 仅在有值时携带可选字段，避免编辑时把后端已存值清空为 null
  const payload = { ...form }
  for (const k of ['idCard', 'fatherPhone', 'motherPhone']) {
    if (payload[k] === '' || payload[k] == null) delete payload[k]
  }
  if (form.id) {
    await studentApi.update(form.id, payload)
    ElMessage.success('更新成功')
  } else {
    await studentApi.create(payload)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除学生「${row.name}」？`, '提示', { type: 'warning' })
  await studentApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(async () => {
  classes.value = await dictApi.classes()
  load()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
