<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ChatDotSquare, Plus, Search, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '../stores/chat'
import { useContactsStore } from '../stores/contacts'
import type { Friend, GroupChat } from '../types'
import AvatarDisplay from './AvatarDisplay.vue'

type Action = 'friend' | 'join' | 'create'
const contactsStore = useContactsStore()
const chatStore = useChatStore()
const visible = ref(false)
const busy = ref(false)
const action = ref<Action>('friend')
const form = reactive({ username: '', groupNumber: '', groupName: '', reason: '' })
const friendResult = ref<Friend | null>(null)
const groupResult = ref<GroupChat | null>(null)
const titles: Record<Action, string> = { friend: '添加好友', join: '添加群聊', create: '创建群聊' }
const title = computed(() => titles[action.value])
const hasSearchResult = computed(() => action.value === 'friend' ? Boolean(friendResult.value) : Boolean(groupResult.value))

function open(command: Action) {
  reset()
  action.value = command
  visible.value = true
}
function resetSearchResult() { friendResult.value = null; groupResult.value = null; form.reason = '' }
function reset() {
  form.username = ''
  form.groupNumber = ''
  form.groupName = ''
  form.reason = ''
  friendResult.value = null
  groupResult.value = null
}

async function searchTarget() {
  if (action.value === 'friend' && !/^[a-zA-Z0-9_]+$/.test(form.username.trim())) { ElMessage.warning('请输入正确的用户名'); return }
  if (action.value === 'join' && !/^\d+$/.test(form.groupNumber.trim())) { ElMessage.warning('群聊号只能包含数字'); return }
  busy.value = true
  try {
    if (action.value === 'friend') friendResult.value = await window.chatApi.searchFriendByUsername(form.username)
    else groupResult.value = await window.chatApi.searchGroupByNumber(form.groupNumber)
  } catch (error) {
    resetSearchResult()
    ElMessage.error(error instanceof Error ? error.message : '没有找到对应结果')
  } finally {
    busy.value = false
  }
}

async function sendApplication() {
  if (!hasSearchResult.value) return
  busy.value = true
  try {
    if (action.value === 'friend') {
      const request = await window.chatApi.applyAddFriend(friendResult.value!.username, form.reason)
      contactsStore.addRequest(request)
      ElMessage.success('好友申请已发送')
    } else {
      contactsStore.addRequest(await window.chatApi.applyJoinGroup(groupResult.value!.groupNumber, form.reason))
      ElMessage.success('入群申请已发送')
    }
    visible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '申请发送失败')
  } finally {
    busy.value = false
  }
}

async function submitSimpleAction() {
  if (action.value === 'create' && !form.groupName.trim()) { ElMessage.warning('请输入群聊名称'); return }
  busy.value = true
  try {
    if (action.value === 'create') {
      const result = await window.chatApi.createGroup(form.groupName)
      contactsStore.addGroup(result.group)
      chatStore.addConversation(result.conversation)
      ElMessage.success(`群聊创建成功，群聊号：${result.group.groupNumber}`)
    }
    visible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="open">
    <button class="plus-button no-drag" title="更多功能"><el-icon><Plus /></el-icon></button>
    <template #dropdown><el-dropdown-menu class="quick-add-menu"><el-dropdown-item command="friend"><el-icon><UserFilled /></el-icon>添加好友</el-dropdown-item><el-dropdown-item command="join"><el-icon><ChatDotSquare /></el-icon>添加群聊</el-dropdown-item><el-dropdown-item command="create"><el-icon><Plus /></el-icon>创建群聊</el-dropdown-item></el-dropdown-menu></template>
  </el-dropdown>

  <el-dialog v-model="visible" :title="title" width="380px" class="action-dialog no-drag" append-to-body @closed="reset">
    <div v-if="action === 'friend'" class="action-form">
      <label>用户名</label><el-input v-model="form.username" placeholder="输入对方的用户名" maxlength="30" @input="resetSearchResult" @keyup.enter="searchTarget"><template #prefix><el-icon><Search /></el-icon></template><template #append><el-button :loading="busy" @click="searchTarget">搜索</el-button></template></el-input>
      <div v-if="friendResult" class="search-result-card"><AvatarDisplay :src="friendResult.avatar" :name="friendResult.nickname" :size="44" /><span><strong>{{ friendResult.nickname }}</strong><small>用户名：{{ friendResult.username }}</small></span></div>
      <div v-if="friendResult" class="application-reason"><label>申请理由（选填）</label><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="例如：你好，想和你成为好友" /></div>
    </div>
    <div v-else-if="action === 'join'" class="action-form">
      <label>群聊号</label><el-input v-model="form.groupNumber" placeholder="输入纯数字群聊号" maxlength="20" inputmode="numeric" @input="resetSearchResult" @keyup.enter="searchTarget"><template #prefix><el-icon><Search /></el-icon></template><template #append><el-button :loading="busy" @click="searchTarget">搜索</el-button></template></el-input><p>只能通过群聊号搜索群聊。</p>
      <div v-if="groupResult" class="search-result-card"><AvatarDisplay :src="groupResult.avatar" :name="groupResult.name" :size="44" /><span><strong>{{ groupResult.name }}</strong><small>群聊号：{{ groupResult.groupNumber }} · {{ groupResult.memberCount }} 人</small></span></div>
      <div v-if="groupResult" class="application-reason"><label>申请理由（选填）</label><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="100" show-word-limit placeholder="填写入群申请理由" /></div>
    </div>
    <div v-else-if="action === 'create'" class="action-form"><label>群聊名称</label><el-input v-model="form.groupName" placeholder="设置群聊名称" maxlength="30" show-word-limit /><p>创建成功后会自动生成群聊号。</p></div>
    <template #footer><el-button @click="visible = false">取消</el-button><el-button v-if="action === 'friend' || action === 'join'" type="success" :disabled="!hasSearchResult" :loading="busy" @click="sendApplication">发送申请</el-button><el-button v-else type="success" :loading="busy" @click="submitSimpleAction">确定</el-button></template>
  </el-dialog>
</template>
