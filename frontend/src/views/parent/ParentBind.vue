<template>
  <div>
    <el-card shadow="never">
      <template #header>孩子绑定</template>

      <el-tabs v-model="activeTab">
        <!-- ① 绑定在校孩子 -->
        <el-tab-pane label="绑定在校孩子" name="bind">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
                    title="使用孩子的学号 + 姓名（与系统登记一致）精确匹配，并提交家长手机号、身份证后 8 位完成身份校验。绑定后即可查看在校成绩趋势、录入小测成绩。" />
          <el-form :inline="true" label-width="90px" @submit.prevent>
            <el-form-item label="学号">
              <el-input v-model="form.studentNo" placeholder="如 S0045" style="width: 150px" />
            </el-form-item>
            <el-form-item label="学生姓名">
              <el-input v-model="form.name" placeholder="孩子姓名" style="width: 130px" />
            </el-form-item>
            <el-form-item label="家长手机号">
              <el-input v-model="form.phone" placeholder="家长手机号" style="width: 150px" />
            </el-form-item>
            <el-form-item label="身份证后8位">
              <el-input v-model="form.idCardLast8" placeholder="身份证末 8 位" style="width: 150px" maxlength="8" />
            </el-form-item>
            <el-form-item label="关系">
              <el-select v-model="form.relation" style="width: 110px" placeholder="选关系">
                <el-option v-for="r in relations" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="binding" @click="doBind">绑定</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ② 自建孩子（个人版未入学自测） -->
        <el-tab-pane label="自建孩子" name="createSelf">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px"
                    title="个人版：可先自建一个未入学的孩子（仅需姓名），录入在家小测成绩。等孩子在校建档后，再到「认领合并」用四要素将自测成绩迁移到在校学生。" />
          <el-form :inline="true" label-width="90px" @submit.prevent>
            <el-form-item label="孩子姓名">
              <el-input v-model="selfForm.name" placeholder="孩子姓名" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="selfCreating" @click="doCreateSelf">自建</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ③ 认领合并（自建→校园） -->
        <el-tab-pane label="认领合并" name="claim">
          <el-alert type="success" :closable="false" show-icon style="margin-bottom: 16px"
                    title="孩子在校建档后，用 学号 + 姓名 + 家长手机号 + 身份证后 8 位 命中在校学生，把自建期间的小测成绩一并迁移并转为正式绑定。" />
          <el-form :inline="true" label-width="90px" @submit.prevent>
            <el-form-item label="学号">
              <el-input v-model="claimForm.studentNo" placeholder="在校学号" style="width: 150px" />
            </el-form-item>
            <el-form-item label="学生姓名">
              <el-input v-model="claimForm.name" placeholder="孩子姓名" style="width: 130px" />
            </el-form-item>
            <el-form-item label="家长手机号">
              <el-input v-model="claimForm.phone" placeholder="家长手机号" style="width: 150px" />
            </el-form-item>
            <el-form-item label="身份证后8位">
              <el-input v-model="claimForm.idCardLast8" placeholder="身份证末 8 位" style="width: 150px" maxlength="8" />
            </el-form-item>
            <el-form-item>
              <el-button type="success" :loading="claiming" @click="doClaim">认领合并</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <el-divider />

      <el-table :data="binds" v-loading="loading" border stripe>
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="name" label="姓名" width="140" />
        <el-table-column prop="className" label="班级" width="140" />
        <el-table-column prop="originLabel" label="来源" width="100" />
        <el-table-column prop="relation" label="关系" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-popconfirm title="确认解除该绑定？" @confirm="doUnbind(row.studentId)">
              <template #reference>
                <el-button size="small" type="danger" link>解绑</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && binds.length === 0" description="暂无绑定，请在下方选择「绑定在校孩子」「自建孩子」或「认领合并」" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { parentApi } from '../../api'

const activeTab = ref('bind')
const binds = ref([])
const loading = ref(false)
const binding = ref(false)
const selfCreating = ref(false)
const claiming = ref(false)
const form = ref({ studentNo: '', name: '', phone: '', idCardLast8: '', relation: '' })
const selfForm = ref({ name: '' })
const claimForm = ref({ studentNo: '', name: '', phone: '', idCardLast8: '' })
const relations = [
  { label: '父亲', value: 'father' },
  { label: '母亲', value: 'mother' },
  { label: '其他', value: 'other' }
]

async function load() {
  loading.value = true
  try {
    binds.value = (await parentApi.binds() || []).map((b) => ({
      ...b,
      originLabel: b.origin === 'self' ? '家长自建' : '在校学生'
    }))
  } finally {
    loading.value = false
  }
}

async function doBind() {
  if (!form.value.studentNo || !form.value.name) {
    ElMessage.warning('请填写学号和姓名')
    return
  }
  if (!form.value.phone || !form.value.idCardLast8 || !form.value.relation) {
    ElMessage.warning('请完整填写手机号、身份证后 8 位与关系')
    return
  }
  binding.value = true
  try {
    const idCardLast8 = form.value.idCardLast8
    // 后端契约：{ studentNo, name, phone, idCardLast8, relation, password }
    // 默认密码取身份证后 8 位
    await parentApi.bind({
      studentNo: form.value.studentNo,
      name: form.value.name,
      phone: form.value.phone,
      idCardLast8,
      relation: form.value.relation,
      password: idCardLast8
    })
    ElMessage.success('绑定成功')
    form.value = { studentNo: '', name: '', phone: '', idCardLast8: '', relation: '' }
    load()
  } catch (e) {
    // 错误已提示
  } finally {
    binding.value = false
  }
}

async function doCreateSelf() {
  if (!selfForm.value.name || !selfForm.value.name.trim()) {
    ElMessage.warning('请填写孩子姓名')
    return
  }
  selfCreating.value = true
  try {
    await parentApi.createSelf({ name: selfForm.value.name.trim() })
    ElMessage.success('自建成功，可录入在家小测成绩')
    selfForm.value = { name: '' }
    load()
  } catch (e) {
    // 错误已提示
  } finally {
    selfCreating.value = false
  }
}

async function doClaim() {
  if (!claimForm.value.studentNo || !claimForm.value.name) {
    ElMessage.warning('请填写学号和姓名')
    return
  }
  if (!claimForm.value.phone || !claimForm.value.idCardLast8) {
    ElMessage.warning('请填写家长手机号与身份证后 8 位')
    return
  }
  claiming.value = true
  try {
    const idCardLast8 = claimForm.value.idCardLast8
    await parentApi.claim({
      studentNo: claimForm.value.studentNo,
      name: claimForm.value.name,
      phone: claimForm.value.phone,
      idCardLast8,
      password: idCardLast8
    })
    ElMessage.success('认领成功，自测成绩已迁移并转为正式绑定')
    claimForm.value = { studentNo: '', name: '', phone: '', idCardLast8: '' }
    load()
  } catch (e) {
    // 错误已提示
  } finally {
    claiming.value = false
  }
}

async function doUnbind(studentId) {
  try {
    await parentApi.unbind(studentId)
    ElMessage.success('已解除绑定')
    load()
  } catch (e) {
    // 错误已提示
  }
}

onMounted(load)
</script>
