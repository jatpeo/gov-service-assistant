<template>
  <div class="login-view">
    <section class="login-main">
      <div class="brand-panel">
        <div class="brand-mark">
          <el-icon><Service /></el-icon>
        </div>
        <h1>智能客服系统</h1>
        <p>登录后可进入管理后台，处理会话、维护知识库并查看服务统计。</p>
        <div class="capabilities">
          <span>会话管理</span>
          <span>知识库维护</span>
          <span>人工接入</span>
          <span>统计分析</span>
        </div>
      </div>

      <div class="login-panel">
        <h2>管理后台登录</h2>
        <p class="login-subtitle">请输入管理员或客服账号</p>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              autocomplete="username"
              @keydown.enter="submitLogin"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              @keydown.enter="submitLogin"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-button class="login-button" type="primary" :loading="loading" @click="submitLogin">
            登录
          </el-button>
        </el-form>

        <div class="login-hint">
          默认账号：admin / admin123，普通用户：user / user123。生产环境请通过环境变量修改初始密码。
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Service, User, Lock } from '@element-plus/icons-vue'
import { login, saveAuth } from '@/api/chat.js'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submitLogin() {
  try {
    await formRef.value?.validate()
  } catch (error) {
    return
  }

  loading.value = true

  try {
    const res = await login(form.username.trim(), form.password)
    saveAuth(res.data.token, res.data.user)
    ElMessage.success('登录成功')
    const fallbackPath = res.data.user?.role === 'USER' ? '/' : '/admin'
    router.replace(route.query.redirect || fallbackPath)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-view {
  min-height: 100vh;
  background: #eef3f8;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.login-main {
  width: min(960px, 100%);
  min-height: 520px;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  background: #fff;
  border: 1px solid #dfe7f0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 18px 45px rgba(31, 55, 83, 0.12);
}

.brand-panel {
  padding: 52px;
  background: #134f94;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;

  .brand-mark {
    width: 56px;
    height: 56px;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.14);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 30px;
    margin-bottom: 28px;
  }

  h1 {
    font-size: 32px;
    line-height: 1.25;
    margin: 0 0 16px;
    font-weight: 650;
  }

  p {
    max-width: 420px;
    color: rgba(255, 255, 255, 0.82);
    line-height: 1.8;
    margin: 0;
  }
}

.capabilities {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 34px;

  span {
    border: 1px solid rgba(255, 255, 255, 0.28);
    border-radius: 6px;
    padding: 7px 10px;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.9);
  }
}

.login-panel {
  padding: 52px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;

  h2 {
    font-size: 24px;
    color: #1f2d3d;
    margin: 0 0 8px;
  }

  .login-subtitle {
    color: #667085;
    margin: 0 0 30px;
  }
}

.login-button {
  width: 100%;
  height: 42px;
  margin-top: 4px;
}

.login-hint {
  margin-top: 18px;
  color: #8a94a6;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 760px) {
  .login-view {
    padding: 16px;
  }

  .login-main {
    grid-template-columns: 1fr;
  }

  .brand-panel,
  .login-panel {
    padding: 32px 24px;
  }

  .brand-panel h1 {
    font-size: 26px;
  }
}
</style>
