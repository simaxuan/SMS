<template>
  <el-card shadow="never">
    <template #header>系统设置</template>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
              title="排名开关控制在教师端。家长端是否展示孩子排名由老师统一决定。" />

    <el-form label-width="220px" style="max-width: 640px">
      <el-form-item label="家长端展示孩子排名">
        <el-switch v-model="parentRankVisible" :loading="saving" @change="(v)=>save('parent_rank_visible', v)" />
        <span class="tip">开启后，家长端「孩子成绩趋势」将展示孩子在本班的单科名次；关闭则隐藏。</span>
      </el-form-item>

      <el-form-item label="家长排名档位">
        <el-select v-model="parentRankDetail" style="width: 200px" :loading="saving" @change="(v)=>save('parent_rank_detail', v)">
          <el-option label="仅本人+聚合(aggregate)" value="aggregate" />
          <el-option label="受限明细(limited)" value="limited" />
          <el-option label="全部明细(full)" value="full" />
        </el-select>
        <span class="tip">aggregate：家长仅看本人名次与班级/级部聚合，不返回他人明细。</span>
      </el-form-item>

      <el-form-item label="标准分权重模式">
        <el-select v-model="stdWeightMode" style="width: 200px" :loading="saving" @change="onStdWeightModeChange">
          <el-option label="等权(equal)" value="equal" />
          <el-option label="按满分加权(full-weighted)" value="full-weighted" />
          <el-option label="自定义(custom-weighted)" value="custom-weighted" />
        </el-select>
        <span class="tip">custom-weighted 时需在下方的自定义权重(JSON)中维护各科目权重。</span>
      </el-form-item>

      <el-form-item v-if="stdWeightMode === 'custom-weighted'" label="自定义权重(JSON)">
        <el-input v-model="stdWeights" type="textarea" :rows="4" style="width: 100%"
                  placeholder='如 {"语文":1,"数学":1.2}' @blur="save('std_weights', parseStdWeights())" />
        <span class="tip">合法的 JSON 对象，键为科目名，值为权重数字。</span>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { settingsApi } from '../api'

const parentRankVisible = ref(true)
const parentRankDetail = ref('aggregate')
const stdWeightMode = ref('equal')
const stdWeights = ref('')
const saving = ref(false)

async function load() {
  const s = await settingsApi.list()
  parentRankVisible.value = s.parent_rank_visible !== false
  // 第六轮新增设置项（读不到时回退默认值）
  parentRankDetail.value = s.parent_rank_detail || 'aggregate'
  stdWeightMode.value = s.std_weight_mode || 'equal'
  stdWeights.value = typeof s.std_weights === 'string' ? s.std_weights : (s.std_weights ? JSON.stringify(s.std_weights) : '')
}

function parseStdWeights() {
  const raw = stdWeights.value.trim()
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    ElMessage.warning('自定义权重不是合法 JSON')
    return undefined
  }
}

async function save(key, value) {
  if (value === undefined) return
  saving.value = true
  try {
    await settingsApi.update(key, value)
    ElMessage.success('设置已保存')
  } catch (e) {
    // 回滚
    load()
  } finally {
    saving.value = false
  }
}

async function onStdWeightModeChange() {
  await save('std_weight_mode', stdWeightMode.value)
  if (stdWeightMode.value !== 'custom-weighted') stdWeights.value = ''
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
