<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in cards" :key="card.title">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="value">{{ card.value }}</div>
            <div class="label">{{ card.title }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px" shadow="never">
      <template #header>系统简介</template>
      <p style="line-height: 1.8; color: #606266">
        考试成绩管理系统（SMS）是一套前后端分离的考试成绩管理平台，覆盖
        <b>学生信息管理</b>、<b>成绩录入</b>、<b>成绩查询</b> 与 <b>成绩统计</b>
        四大核心模块。系统采用 Spring Boot + Vue 3 技术栈，演示环境内置 H2 内存库与种子数据，可开箱即用。
      </p>
      <p style="color: #909399">请在左侧菜单中进入各功能模块进行操作。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { studentApi, gradeApi } from '../api'

const cards = ref([
  { title: '学生总数', value: '-' },
  { title: '班级数', value: '-' },
  { title: '课程数', value: '-' },
  { title: '成绩记录', value: '-' }
])

onMounted(async () => {
  const [studentRes, gradeRes] = await Promise.all([
    studentApi.list({ page: 0, size: 1 }),
    gradeApi.list({})
  ])
  const sTotal = studentRes?.total ?? 0
  const gTotal = Array.isArray(gradeRes) ? gradeRes.length : 0
  cards.value[0].value = sTotal
  cards.value[1].value = '-'
  cards.value[2].value = '-'
  cards.value[3].value = gTotal
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-card .value {
  font-size: 34px;
  font-weight: 700;
  color: #409eff;
}
.stat-card .label {
  margin-top: 6px;
  color: #909399;
  font-size: 14px;
}
</style>
