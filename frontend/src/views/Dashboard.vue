<template>
  <div>
    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="12" :sm="12" :md="6" v-for="card in cards" :key="card.title">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="value" :title="String(card.value)">{{ card.value }}</div>
            <div class="label">{{ card.title }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px" shadow="never">
      <template #header>系统简介</template>
      <p style="line-height: 1.8; color: var(--el-text-color-regular)">
        考试成绩管理系统（SMS）是一套前后端分离的考试成绩管理平台，覆盖
        <b>学生信息管理</b>、<b>成绩录入</b>、<b>成绩查询</b> 与 <b>成绩统计</b>
        四大核心模块。系统采用 Spring Boot + Vue 3 技术栈，演示环境内置 H2 内存库与种子数据，可开箱即用。
      </p>
      <p style="color: var(--el-text-color-secondary)">请在左侧菜单中进入各功能模块进行操作。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { studentApi, gradeApi, dictApi } from '../api'

const cards = ref([
  { title: '学生总数', value: '-' },
  { title: '班级数', value: '-' },
  { title: '课程数', value: '-' },
  { title: '成绩记录', value: '-' }
])

const loading = ref(true)

onMounted(async () => {
  loading.value = true
  try {
    const [studentRes, gradeRes, classes, courses] = await Promise.all([
      studentApi.list({ page: 0, size: 1 }),
      gradeApi.list({}),
      dictApi.classes(),
      dictApi.courses()
    ])
    const sTotal = studentRes?.total ?? 0
    const gTotal = Array.isArray(gradeRes) ? gradeRes.length : 0
    cards.value[0].value = sTotal
    cards.value[1].value = Array.isArray(classes) ? classes.length : (classes?.total ?? 0)
    cards.value[2].value = Array.isArray(courses) ? courses.length : (courses?.total ?? 0)
    cards.value[3].value = gTotal
  } catch (e) {
    // P2-10 错误已由拦截器统一提示，失败保持占位 '-'，避免未处理拒绝
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 8px 0;
  overflow: hidden;
}
.stat-card .value {
  font-size: 30px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.2;
  word-break: break-all;
  overflow-wrap: anywhere;
}
.stat-card .label {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
