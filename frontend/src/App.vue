<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">📊 考试成绩管理系统</div>
      <el-menu :default-active="$route.path" router background-color="#001529" text-color="#c0c4cc" active-text-color="#409eff">
        <el-menu-item index="/dashboard"><el-icon><Odometer /></el-icon><span>首页</span></el-menu-item>
        <el-menu-item index="/students"><el-icon><User /></el-icon><span>学生信息管理</span></el-menu-item>
        <el-menu-item index="/grade-entry"><el-icon><EditPen /></el-icon><span>成绩录入</span></el-menu-item>
        <el-menu-item index="/grade-query"><el-icon><Search /></el-icon><span>成绩查询</span></el-menu-item>
        <el-menu-item index="/statistics"><el-icon><TrendCharts /></el-icon><span>成绩统计</span></el-menu-item>
        <el-menu-item index="/dictionary"><el-icon><Collection /></el-icon><span>基础数据</span></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">{{ $route.meta?.title || '首页' }}</span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
</script>

<style>
html, body, #app {
  margin: 0;
  height: 100%;
}
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
}
.aside .logo {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  padding: 18px 0;
  letter-spacing: 1px;
}
.aside .el-menu {
  border-right: none;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
}
.header .title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.main {
  background: #f0f2f5;
  padding: 20px;
}
</style>
