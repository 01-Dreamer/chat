<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Check, Close, Lock, Postcard, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'

const emit = defineEmits<{ authenticated: [] }>()
const appStore = useAppStore()
const mode = ref<'login' | 'register'>('login')
const busy = ref(false)
const form = reactive({ nickname: '', username: '', password: '', confirmPassword: '' })
const disabled = computed(() => mode.value === 'login'
  ? !form.username || !form.password
  : !form.nickname || !form.username || !form.password || !form.confirmPassword)

async function closeWindow() {
  await window.chatApi.closeWindow()
}

function switchMode(next: 'login' | 'register') {
  mode.value = next
  window.chatApi.setWindowMode(next)
}

async function submit() {
  if (disabled.value) return
  if (mode.value === 'register' && form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  busy.value = true
  try {
    if (mode.value === 'register') {
      await window.chatApi.register(form.nickname, form.username, form.password)
      form.password = ''
      form.confirmPassword = ''
      mode.value = 'login'
      window.chatApi.setWindowMode('login')
      ElMessage.success('注册成功，请使用新账号登录')
    } else {
      const user = await window.chatApi.login(form.username, form.password)
      appStore.setUser(user)
      emit('authenticated')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="account-panel">
    <header class="account-title drag-region">
      <span>Chat</span>
      <button type="button" class="account-close no-drag" title="关闭" @mousedown.stop @click.stop="closeWindow"><el-icon><Close /></el-icon></button>
    </header>

    <el-form class="account-form no-drag" @submit.prevent="submit">
      <el-form-item v-if="mode === 'register'">
        <el-input v-model="form.nickname" size="large" placeholder="请输入昵称"><template #prefix><el-icon><Postcard /></el-icon></template></el-input>
      </el-form-item>
      <el-form-item>
        <el-input v-model="form.username" size="large" clearable placeholder="请输入用户名"><template #prefix><el-icon><User /></el-icon></template></el-input>
      </el-form-item>
      <el-form-item>
        <el-input v-model="form.password" size="large" type="password" show-password clearable placeholder="请输入密码" @keyup.enter="submit"><template #prefix><el-icon><Lock /></el-icon></template></el-input>
      </el-form-item>
      <el-form-item v-if="mode === 'register'">
        <el-input v-model="form.confirmPassword" size="large" type="password" show-password placeholder="请确认密码" @keyup.enter="submit"><template #prefix><el-icon><Check /></el-icon></template></el-input>
      </el-form-item>
      <el-form-item><el-button class="account-submit" type="success" native-type="submit" :loading="busy" :disabled="disabled" @click="submit">{{ mode === 'login' ? '登录' : '注册' }}</el-button></el-form-item>
      <el-form-item><el-button class="account-switch" type="primary" @click="switchMode(mode === 'login' ? 'register' : 'login')">{{ mode === 'login' ? '去注册' : '去登录' }}</el-button></el-form-item>
    </el-form>
  </main>
</template>
