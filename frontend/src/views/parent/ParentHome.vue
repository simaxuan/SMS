<template>
  <div>
    <el-card shadow="never" class="welcome">
      <h2>你好，{{ nickname }}</h2>
      <p class="desc">这里是「个人版·家长端」。您可以绑定孩子，查看孩子的在校成绩趋势，并录入在家的小测成绩。</p>
      <p class="note">说明：老师录入的校内成绩为只读；家长录入的小测成绩仅自己可见，不会进入班级统计，老师端也不查看。</p>
    </el-card>

    <el-row :gutter="16" class="cards">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card" @click="$router.push('/parent/bind')">
          <div class="num">{{ binds.length }}</div>
          <div class="label">已绑定孩子</div>
          <el-button type="primary" link>去绑定 / 管理</el-button>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card" @click="$router.push('/parent/trend')">
          <div class="num">{{ binds.length }}</div>
          <div class="label">可查看趋势</div>
          <el-button type="primary" link>查看孩子成绩趋势</el-button>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="stat-card" @click="$router.push('/parent/entry')">
          <div class="num">∞</div>
          <div class="label">家长录入小测</div>
          <el-button type="primary" link>录入在家小测成绩</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="已绑定孩子" style="margin-top: 20px">
      <el-table :data="binds" v-loading="loading" border stripe>
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="name" label="姓名" width="140" />
        <el-table-column prop="className" label="班级" width="140" />
        <el-table-column prop="relation" label="关系" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="$router.push('/parent/trend')">成绩趋势</el-button>
            <el-button size="small" @click="$router.push('/parent/entry')">录小测</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && binds.length === 0" description="尚未绑定孩子，请先到「孩子绑定」添加" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { parentApi } from '../../api'
import { useAuth } from '../../composables/useAuth'

const { nickname } = useAuth()
const binds = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    binds.value = await parentApi.binds()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.welcome { margin-bottom: 20px; }
.welcome h2 { margin: 0 0 8px; color: var(--el-text-color-primary); }
.welcome .desc { color: var(--el-text-color-regular); margin: 0 0 8px; }
.welcome .note { color: var(--el-color-warning); margin: 0; font-size: 13px; }
.stat-card {
  text-align: center;
  cursor: pointer;
  transition: box-shadow .2s;
}
.stat-card:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, .12); }
.stat-card .num {
  font-size: 34px;
  font-weight: 700;
  color: var(--el-color-primary);
}
.stat-card .label { color: var(--el-text-color-secondary); margin: 6px 0 10px; }
</style>
