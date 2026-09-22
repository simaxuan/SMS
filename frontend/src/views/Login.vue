<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="brand">📊 考试成绩管理系统</div>
      <div class="subtitle">校园版（老师）/ 个人版（家长）统一登录</div>

      <el-radio-group v-model="role" class="role-switch" size="large">
        <el-radio-button value="TEACHER" :disabled="mode === 'register'">我是老师（校园版）</el-radio-button>
        <el-radio-button value="PARENT">我是家长（个人版）</el-radio-button>
      </el-radio-group>

      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                    autocomplete="current-password" @keyup.enter="onSubmit" />
        </el-form-item>
        <template v-if="mode === 'register'">
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="家长手机号（用于登录与找回）" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" placeholder="请输入昵称（可选）" />
          </el-form-item>
        </template>
        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">
          {{ mode === 'login' ? '登 录' : '注 册' }}
        </el-button>
      </el-form>

      <div class="switch-mode">
        <el-link type="primary" @click="toggleMode">
          {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
        </el-link>
      </div>

      <el-alert v-if="mode === 'login'" type="info" :closable="false" class="hint"
                title="演示账号：老师 admin/admin123，家长 parent1/parent123" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '../api'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const route = useRoute()
const { setSession } = useAuth()
const role = ref('TEACHER')
const mode = ref('login')
const loading = ref(false)
const form = ref({ username: '', password: '', nickname: '', phone: '' })

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.value.password = ''
  // 老师账号由管理员创建，不开放自助注册，进入注册一律切到家长角色
  if (mode.value === 'register' && role.value === 'TEACHER') {
    role.value = 'PARENT'
    ElMessage.info('老师账号由系统管理员创建，注册请使用家长角色')
  }
}

async function onSubmit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码长度至少 6 位')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      const res = await authApi.login({ username: form.value.username, password: form.value.password, role: role.value })
      // 凭证已由后端写入 HttpOnly Cookie；前端仅缓存用户资料（不含 token）
      setSession({ user: res.user })
      ElMessage.success('登录成功')
      // 按后端返回的真实角色决定默认首页；若存在被守卫记录的 redirect 则回跳原页
      const isTeacher = res.user.role === 'TEACHER'
      const fallback = isTeacher ? '/dashboard' : '/parent/home'
      let dest = fallback
      const target = route.query.redirect
      if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
        dest = target
      }
      router.push(dest)
    } else {
      if (role.value !== 'PARENT') {
        ElMessage.warning('老师账号由系统管理员创建，请使用家长角色注册')
        return
      }
      await authApi.register({
        username: form.value.username,
        password: form.value.password,
        role: role.value,
        nickname: form.value.nickname,
        phone: form.value.phone
      })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
      form.value.password = ''
    }
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b4b6f 100%);
}
.login-card {
  width: 400px;
  max-width: 90vw;
  background: #fff;
  border-radius: 8px;
  padding: 32px 36px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.18);
}
.brand {
  font-size: 20px;
  font-weight: 700;
  text-align: center;
  color: var(--el-text-color-primary);
}
.subtitle {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-align: center;
  margin: 6px 0 20px;
}
.role-switch {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}
:deep(.el-radio-button__inner) {
  font-size: 13px;
}
.submit {
  width: 100%;
  margin-top: 4px;
}
.switch-mode {
  text-align: center;
  margin-top: 14px;
}
.hint {
  margin-top: 16px;
}
</style>
