<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Check, Close, EditPen } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import AvatarDisplay from './AvatarDisplay.vue'
const appStore = useAppStore()
const form = reactive<{ nickname: string; avatar: string | null }>({ nickname: '', avatar: null })
const editingNickname = ref(false)
const nicknameDraft = ref('')
const passwordVisible = ref(false)
const passwordBusy = ref(false)
const logoutBusy = ref(false)
const payPasswordSet = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
watch(() => appStore.showSettings, async (open) => {
  if (open && appStore.currentUser) {
    form.nickname = appStore.currentUser.nickname
    form.avatar = appStore.currentUser.avatar
    editingNickname.value = false
    try {
      const wallet = await window.chatApi.getWallet()
      payPasswordSet.value = wallet.payPasswordSet
      appStore.currentUser.balance = wallet.balance
    } catch { /* Keep the last cached balance while offline. */ }
  }
})
function startNicknameEdit() { nicknameDraft.value = form.nickname; editingNickname.value = true }
function cancelNicknameEdit() { nicknameDraft.value = form.nickname; editingNickname.value = false }
async function saveNickname() {
  const nickname = nicknameDraft.value.trim()
  if (!nickname) { ElMessage.warning('昵称不能为空'); return }
  const user = await window.chatApi.updateProfile({ nickname })
  appStore.setUser(user)
  form.nickname = user.nickname
  editingNickname.value = false
  ElMessage.success('昵称已更新')
}
async function selectAvatar() {
  try {
    const user = await window.chatApi.updateAvatar()
    if (!user) return
    form.avatar = user.avatar
    appStore.setUser(user)
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '头像更新失败')
  }
}
function openPasswordReset() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}
async function resetTransferPassword() {
  if ((payPasswordSet.value && !/^\d{6}$/.test(passwordForm.oldPassword)) || !/^\d{6}$/.test(passwordForm.newPassword)) { ElMessage.warning('转账密码必须是 6 位数字'); return }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) { ElMessage.warning('两次输入的新密码不一致'); return }
  passwordBusy.value = true
  try {
    await window.chatApi.resetTransferPassword(passwordForm.oldPassword, passwordForm.newPassword)
    payPasswordSet.value = true
    passwordVisible.value = false
    ElMessage.success('转账密码已重新设置')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '转账密码设置失败')
  } finally {
    passwordBusy.value = false
  }
}
async function logout() {
  logoutBusy.value = true
  try {
    await window.chatApi.closeWindow()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出登录失败')
  } finally {
    logoutBusy.value = false
  }
}
</script>

<template>
  <el-drawer v-model="appStore.showSettings" direction="rtl" size="500px" title="设置" class="side-drawer settings-drawer no-drag" destroy-on-close>
    <div class="settings-profile"><button class="avatar-editor" title="点击更换头像" @click="selectAvatar"><AvatarDisplay :src="form.avatar" :name="form.nickname" :size="64" :radius="8" /><span class="avatar-overlay">点击更换</span></button><div class="settings-user-info"><div class="settings-nickname-row"><h3 v-if="!editingNickname">{{ form.nickname }}</h3><el-input v-else v-model="nicknameDraft" size="small" maxlength="25" @keyup.enter="saveNickname" /><span class="nickname-actions"><button v-if="!editingNickname" title="修改昵称" @click="startNicknameEdit"><el-icon><EditPen /></el-icon></button><template v-else><button title="保存" @click="saveNickname"><el-icon><Check /></el-icon></button><button title="取消" @click="cancelNicknameEdit"><el-icon><Close /></el-icon></button></template></span></div><span>用户名：{{ appStore.currentUser?.username }}</span></div></div>
    <div class="settings-menu"><div class="settings-menu-row"><span>余额</span><strong>¥ {{ appStore.currentUser?.balance }}</strong></div><div class="settings-menu-row"><span>支付密码</span><div class="password-setting"><span>{{ payPasswordSet ? '已设置，密码不可查看' : '尚未设置' }}</span><el-button size="small" @click="openPasswordReset">{{ payPasswordSet ? '重新设置' : '立即设置' }}</el-button></div></div><div class="settings-logout-row"><el-button type="danger" plain :loading="logoutBusy" @click="logout">退出登录</el-button></div></div>
  </el-drawer>
  <el-dialog v-model="passwordVisible" title="重新设置转账密码" width="380px" class="password-dialog no-drag" append-to-body>
    <div class="password-form"><label v-if="payPasswordSet"><span>旧密码</span><el-input v-model="passwordForm.oldPassword" type="password" maxlength="6" inputmode="numeric" placeholder="请输入原 6 位数字密码" /></label><label><span>新密码</span><el-input v-model="passwordForm.newPassword" type="password" maxlength="6" inputmode="numeric" placeholder="请输入新的 6 位数字密码" /></label><label><span>确认新密码</span><el-input v-model="passwordForm.confirmPassword" type="password" maxlength="6" inputmode="numeric" placeholder="请再次输入新密码" @keyup.enter="resetTransferPassword" /></label><p>支付密码只能设置为 6 位数字，设置后无法查看。</p></div>
    <template #footer><el-button @click="passwordVisible = false">取消</el-button><el-button type="success" :loading="passwordBusy" @click="resetTransferPassword">确认修改</el-button></template>
  </el-dialog>
</template>
