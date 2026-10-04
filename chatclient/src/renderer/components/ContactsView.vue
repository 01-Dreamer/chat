<script setup lang="ts">
import { ref } from 'vue'
import { ArrowRight, ChatDotSquare, Check, Close, CopyDocument, EditPen, Search, TopRight, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useContactsStore } from '../stores/contacts'
import { useAppStore } from '../stores/app'
import { useChatStore } from '../stores/chat'
import type { Friend, FriendRequest, GroupChat, GroupMember } from '../types'
import AddMenuDialog from './AddMenuDialog.vue'
import AvatarDisplay from './AvatarDisplay.vue'

const contactsStore = useContactsStore()
const appStore = useAppStore()
const chatStore = useChatStore()
const searchText = ref('')
const selected = ref<Friend | GroupChat | FriendRequest | null>(contactsStore.friends[0] ?? null)
const expanded = ref({ requests: false, groups: false, friends: false })
const editingRemark = ref(false)
const remarkDraft = ref('')
const groupManageVisible = ref(false)
const groupManageBusy = ref(false)
const roleUpdatingUserId = ref('')
const groupMembers = ref<GroupMember[]>([])
const groupNameDraft = ref('')
const groupNicknameDraft = ref('')
let selectionEpoch = 0

function isFriend(item: typeof selected.value): item is Friend { return Boolean(item && 'signature' in item) }
function isGroup(item: typeof selected.value): item is GroupChat { return Boolean(item && 'memberCount' in item) }
function requestTitle(item: FriendRequest) { return item.requestType === 'group' ? item.groupName || '群聊申请' : item.nickname }
function requestIdentifier(item: FriendRequest) { return item.requestType === 'group' ? `群聊号：${item.groupNumber}` : `用户名：${item.username}` }
function requestStatusText(item: FriendRequest) {
  if (item.status === 'pending') return '待审核'
  if (item.status === 'rejected') return '已拒绝'
  return item.direction === 'incoming' ? '已同意' : '已添加'
}
async function selectItem(item: Friend | GroupChat | FriendRequest) {
  selected.value = item
  editingRemark.value = false
  const epoch = ++selectionEpoch
  try {
    if (isFriend(item)) {
      contactsStore.setFriends(await window.chatApi.listFriends())
      if (epoch === selectionEpoch) selected.value = contactsStore.friends.find((value) => value.id === item.id) ?? null
    } else if (isGroup(item)) {
      contactsStore.setGroups(await window.chatApi.listGroups())
      if (epoch === selectionEpoch) selected.value = contactsStore.groups.find((value) => value.id === item.id) ?? null
    } else {
      contactsStore.setRequests(await window.chatApi.listContactRequests())
      if (epoch === selectionEpoch) selected.value = contactsStore.friendRequests.find((value) => value.id === item.id) ?? null
    }
  } catch (error) {
    if (epoch === selectionEpoch) ElMessage.error(error instanceof Error ? error.message : '联系人同步失败')
  }
}

async function toggleCategory(kind: 'requests' | 'groups' | 'friends') {
  expanded.value[kind] = !expanded.value[kind]
  if (!expanded.value[kind]) return
  try {
    if (kind === 'friends') contactsStore.setFriends(await window.chatApi.listFriends())
    else if (kind === 'groups') contactsStore.setGroups(await window.chatApi.listGroups())
    else contactsStore.setRequests(await window.chatApi.listContactRequests())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通讯录同步失败')
  }
}
async function respond(request: FriendRequest, status: 'accepted' | 'rejected') {
  try {
    if (status === 'accepted') {
      await window.chatApi.acceptFriendRequest(request.id, request.requestType)
      contactsStore.updateRequest(request.id, 'accepted')
      if (request.requestType === 'friend') contactsStore.setFriends(await window.chatApi.listFriends())
      else contactsStore.setGroups(await window.chatApi.listGroups())
    } else {
      await window.chatApi.rejectFriendRequest(request.id, request.requestType)
      contactsStore.updateRequest(request.id, 'rejected')
    }
    ElMessage.success(status === 'accepted' ? '已接受申请' : '已拒绝申请')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '申请处理失败')
  }
}
function startRemarkEdit() {
  if (!selected.value || (!isFriend(selected.value) && !isGroup(selected.value))) return
  remarkDraft.value = selected.value.remark
  editingRemark.value = true
}
function cancelRemarkEdit() { editingRemark.value = false }
async function saveRemark() {
  if (!selected.value || (!isFriend(selected.value) && !isGroup(selected.value))) return
  const kind = isFriend(selected.value) ? 'friend' : 'group'
  try {
    await window.chatApi.updateContactRemark(kind, selected.value.id, remarkDraft.value)
    contactsStore.updateRemark(kind, selected.value.id, remarkDraft.value.trim())
    editingRemark.value = false
    ElMessage.success('备注已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '备注更新失败')
  }
}
async function copyGroupValue(label: string, value: string) {
  try {
    await window.chatApi.copyText(value)
    ElMessage.success(`${label}已复制`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复制失败')
  }
}
async function deleteSelectedFriend() {
  if (!isFriend(selected.value)) return
  const friend = selected.value
  try {
    await ElMessageBox.confirm(`确定删除好友「${friend.remark || friend.nickname}」吗？历史聊天记录会保留。`, '删除好友', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await window.chatApi.deleteFriend(friend.id)
    contactsStore.removeFriend(friend.id)
    selected.value = contactsStore.friends[0] ?? contactsStore.groups[0] ?? null
    ElMessage.success('好友已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '删除好友失败')
  }
}
async function startChat() {
  if (!selected.value || (!isFriend(selected.value) && !isGroup(selected.value))) return
  try {
    const result = await window.chatApi.openConversation(
      isGroup(selected.value) ? 'group' : 'direct',
      selected.value.id,
    )
    chatStore.addConversation(result.conversation)
    if (!chatStore.hasLoadedMessages(result.conversation.id)) {
      chatStore.setConversationMessagePage(result.conversation.id, result)
    }
    chatStore.selectConversation(result.conversation.id)
    appStore.setModule('chat')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法打开会话')
  }
}

async function openGroupManagement() {
  if (!isGroup(selected.value)) return
  groupManageBusy.value = true
  groupNameDraft.value = selected.value.name
  groupNicknameDraft.value = selected.value.currentUserNickname
  groupManageVisible.value = true
  try {
    groupMembers.value = await window.chatApi.listGroupMembers(selected.value.id)
    chatStore.applyGroupMemberProfiles(selected.value.id, groupMembers.value)
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '群成员加载失败') }
  finally { groupManageBusy.value = false }
}

async function saveGroupSettings() {
  if (!isGroup(selected.value)) return
  groupManageBusy.value = true
  try {
    if (selected.value.currentUserRole === 2 && groupNameDraft.value.trim() !== selected.value.name) {
      const group = await window.chatApi.updateGroupProfile(selected.value.id, groupNameDraft.value.trim())
      contactsStore.updateGroup(group)
      selected.value = group
    }
    await window.chatApi.updateMyGroupNickname(selected.value.id, groupNicknameDraft.value.trim())
    groupMembers.value = await window.chatApi.listGroupMembers(selected.value.id)
    ElMessage.success('群设置已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '群设置更新失败') }
  finally { groupManageBusy.value = false }
}

async function updateGroupAvatar() {
  if (!isGroup(selected.value)) return
  try {
    const group = await window.chatApi.updateGroupAvatar(selected.value.id)
    if (!group) return
    contactsStore.updateGroup(group)
    selected.value = group
    ElMessage.success('群头像已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '群头像更新失败') }
}

async function toggleAdmin(member: GroupMember) {
  if (!isGroup(selected.value)) return
  const nextRole = member.role === 1 ? 0 : 1
  const memberName = member.groupNickname || member.nickname
  try {
    await ElMessageBox.confirm(
      nextRole === 1 ? `确定将「${memberName}」设为管理员吗？` : `确定取消「${memberName}」的管理员身份吗？`,
      nextRole === 1 ? '设置管理员' : '取消管理员',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    roleUpdatingUserId.value = member.userId
    await window.chatApi.updateGroupRole(selected.value.id, member.userId, nextRole)
    groupMembers.value = await window.chatApi.listGroupMembers(selected.value.id)
    ElMessage.success(nextRole === 1 ? '已设为管理员' : '已取消管理员')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '角色修改失败')
  } finally {
    roleUpdatingUserId.value = ''
  }
}

async function kickMember(member: GroupMember) {
  if (!isGroup(selected.value)) return
  try {
    await ElMessageBox.confirm(`确定移出「${member.groupNickname || member.nickname}」吗？`, '移出群成员', { type: 'warning' })
    groupMembers.value = await window.chatApi.kickGroupMember(selected.value.id, member.userId)
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '移出失败') }
}

async function exitGroup() {
  if (!isGroup(selected.value)) return
  const group = selected.value
  const dissolve = group.currentUserRole === 2
  try {
    await ElMessageBox.confirm(dissolve ? '解散后所有成员将无法继续发送消息，确定解散吗？' : '确定退出该群聊吗？本地历史消息会保留。', dissolve ? '解散群聊' : '退出群聊', { type: 'warning' })
    if (dissolve) await window.chatApi.dissolveGroup(group.id)
    else await window.chatApi.leaveGroup(group.id)
    contactsStore.removeGroup(group.id)
    groupManageVisible.value = false
    selected.value = contactsStore.groups[0] ?? contactsStore.friends[0] ?? null
    ElMessage.success(dissolve ? '群聊已解散' : '已退出群聊')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}
</script>

<template>
  <section class="contacts-workspace">
    <aside class="contact-panel no-drag">
      <div class="mid-search drag-region"><el-input v-model="searchText" class="search-input no-drag" size="small" placeholder="搜索"><template #prefix><el-icon><Search /></el-icon></template></el-input><AddMenuDialog /></div>
      <div class="friend-list-window">
        <button class="list-category" @click="toggleCategory('requests')"><el-icon :class="{ expanded: expanded.requests }"><ArrowRight /></el-icon><span>新的朋友</span><small>{{ contactsStore.friendRequests.filter((item) => item.status === 'pending').length }}</small></button>
        <div v-show="expanded.requests">
          <button v-for="item in contactsStore.friendRequests" :key="item.id" class="contact-item request-contact-item" :class="{ active: selected?.id === item.id }" @click="selectItem(item)">
            <span class="request-avatar"><AvatarDisplay :src="item.avatar" :name="requestTitle(item)" :size="36" /><i class="request-kind-mark" :class="item.requestType"><el-icon><ChatDotSquare v-if="item.requestType === 'group'" /><UserFilled v-else /></el-icon></i></span>
            <span class="contact-item-copy"><strong>{{ requestTitle(item) }}</strong><small>{{ item.message }}</small></span>
            <span class="request-state" :class="item.status"><el-icon v-if="item.direction === 'outgoing'" class="request-outgoing-mark"><TopRight /></el-icon><span>{{ requestStatusText(item) }}</span></span>
          </button>
        </div>
        <button class="list-category" @click="toggleCategory('groups')"><el-icon :class="{ expanded: expanded.groups }"><ArrowRight /></el-icon><span>群聊</span><small>{{ contactsStore.groups.length }}</small></button>
        <div v-show="expanded.groups"><button v-for="item in contactsStore.groups" :key="item.id" class="contact-item" :class="{ active: selected?.id === item.id }" @click="selectItem(item)"><AvatarDisplay class="contact-avatar" :src="item.avatar" :name="item.name" :size="36" /><span class="contact-simple-name">{{ item.name }}</span></button></div>
        <button class="list-category" @click="toggleCategory('friends')"><el-icon :class="{ expanded: expanded.friends }"><ArrowRight /></el-icon><span>好友</span><small>{{ contactsStore.friends.length }}</small></button>
        <div v-show="expanded.friends"><button v-for="item in contactsStore.friends" :key="item.id" class="contact-item" :class="{ active: selected?.id === item.id }" @click="selectItem(item)"><AvatarDisplay class="contact-avatar" :src="item.avatar" :name="item.nickname" :size="36" /><span class="contact-simple-name">{{ item.nickname }}</span></button></div>
      </div>
    </aside>

    <section class="profile-panel">
      <div class="profile-drag-surface" aria-hidden="true" />
      <template v-if="selected">
        <div class="profile-content">
          <AvatarDisplay :src="selected.avatar" :name="isGroup(selected) ? selected.name : isFriend(selected) ? selected.nickname : requestTitle(selected)" :size="70" :radius="8" class="profile-avatar" />
          <template v-if="isFriend(selected)"><h2>{{ selected.remark || selected.nickname }}</h2><div class="profile-fields"><div><span>用户名</span><strong>{{ selected.username }}</strong></div><div><span>好友昵称</span><strong>{{ selected.nickname }}</strong></div><div class="remark-field"><span>备注</span><strong v-if="!editingRemark">{{ selected.remark || '暂无备注' }}</strong><el-input v-else v-model="remarkDraft" size="small" maxlength="30" @keyup.enter="saveRemark" /><span class="remark-actions"><button v-if="!editingRemark" title="修改备注" @click="startRemarkEdit"><el-icon><EditPen /></el-icon></button><template v-else><button title="保存" @click="saveRemark"><el-icon><Check /></el-icon></button><button title="取消" @click="cancelRemarkEdit"><el-icon><Close /></el-icon></button></template></span></div></div><p class="profile-signature">{{ selected.signature }}</p></template>
          <template v-else-if="isGroup(selected)"><h2>{{ selected.remark || selected.name }}</h2><div class="profile-fields"><div><span>群聊号</span><strong>{{ selected.groupNumber }}</strong><button class="profile-copy-button" title="复制群聊号" @click="copyGroupValue('群聊号', selected.groupNumber)"><el-icon><CopyDocument /></el-icon></button></div><div><span>群聊名称</span><strong>{{ selected.name }}</strong><button class="profile-copy-button" title="复制群聊名称" @click="copyGroupValue('群聊名称', selected.name)"><el-icon><CopyDocument /></el-icon></button></div><div class="remark-field"><span>备注</span><strong v-if="!editingRemark">{{ selected.remark || '暂无备注' }}</strong><el-input v-else v-model="remarkDraft" size="small" maxlength="30" @keyup.enter="saveRemark" /><span class="remark-actions"><button v-if="!editingRemark" title="修改备注" @click="startRemarkEdit"><el-icon><EditPen /></el-icon></button><template v-else><button title="保存" @click="saveRemark"><el-icon><Check /></el-icon></button><button title="取消" @click="cancelRemarkEdit"><el-icon><Close /></el-icon></button></template></span></div></div><p class="profile-signature">{{ selected.description }} · {{ selected.memberCount }} 位成员</p></template>
          <template v-else>
            <h2>{{ requestTitle(selected) }}</h2>
            <p>{{ requestIdentifier(selected) }}</p>
            <div v-if="selected.requestType === 'group' && selected.direction === 'incoming'" class="group-applicant-card">
              <AvatarDisplay :src="selected.applicantAvatar" :name="selected.applicantNickname || selected.nickname" :size="52" :radius="6" class="group-applicant-avatar" />
              <div class="group-applicant-info"><div><span>用户名</span><strong>{{ selected.username }}</strong></div><div><span>昵称</span><strong>{{ selected.applicantNickname }}</strong></div></div>
              <div class="group-application-reason"><span>申请理由</span><p>{{ selected.message }}</p></div>
            </div>
            <p v-else>{{ selected.message }}</p>
          </template>
        </div>
        <div class="profile-actions" v-if="isFriend(selected)"><button @click="startChat">开始聊天</button><button class="secondary" @click="deleteSelectedFriend">删除好友</button></div>
        <div class="profile-actions" v-else-if="isGroup(selected)"><button @click="startChat">开始聊天</button><button class="secondary" @click="openGroupManagement">群管理</button></div>
        <div class="profile-actions request-buttons" v-else-if="selected.status === 'pending' && selected.direction === 'incoming'"><button @click="respond(selected, 'accepted')">接受</button><button class="secondary" @click="respond(selected, 'rejected')">拒绝</button></div>
        <div v-else class="profile-actions request-status-footer"><el-tag :type="selected.status === 'pending' ? 'warning' : selected.status === 'accepted' ? 'success' : 'info'"><el-icon v-if="selected.direction === 'outgoing'"><TopRight /></el-icon>{{ requestStatusText(selected) }}</el-tag></div>
      </template>
      <div v-else class="blank-chat"><span>暂无联系人</span></div>
    </section>
    <el-dialog v-model="groupManageVisible" title="群管理" width="520px" append-to-body>
      <template v-if="isGroup(selected)">
        <el-form label-position="top">
          <el-form-item label="群头像"><div class="group-avatar-editor"><AvatarDisplay :src="selected.avatar" :name="selected.name" :size="48" /><el-button :disabled="selected.currentUserRole < 1" @click="updateGroupAvatar">更换头像</el-button></div></el-form-item>
          <el-form-item label="群名称"><el-input v-model="groupNameDraft" maxlength="64" :disabled="selected.currentUserRole !== 2" /></el-form-item>
          <el-form-item label="我的群昵称"><el-input v-model="groupNicknameDraft" maxlength="64" /></el-form-item>
        </el-form>
        <div class="group-member-header">
          <strong>群成员（{{ groupMembers.length }}）</strong>
          <span v-if="selected.currentUserRole === 2">群主可以设置或取消管理员</span>
        </div>
        <div class="group-member-list" v-loading="groupManageBusy">
          <div v-for="member in groupMembers" :key="member.id" class="group-member-row">
            <AvatarDisplay :src="member.avatar" :name="member.groupNickname || member.nickname" :size="34" />
            <span class="group-member-copy"><strong>{{ member.groupNickname || member.nickname }}</strong><small>{{ member.role === 2 ? '群主' : member.role === 1 ? '管理员' : '成员' }}</small></span>
            <template v-if="member.userId !== appStore.currentUser?.id">
              <el-button v-if="selected.currentUserRole === 2 && member.role !== 2" size="small" :type="member.role === 1 ? 'default' : 'primary'" plain :loading="roleUpdatingUserId === member.userId" @click="toggleAdmin(member)">{{ member.role === 1 ? '取消管理员' : '设为管理员' }}</el-button>
              <el-button v-if="selected.currentUserRole >= 1 && member.role < selected.currentUserRole" size="small" type="danger" plain @click="kickMember(member)">移出</el-button>
            </template>
          </div>
        </div>
      </template>
      <template #footer><el-button type="danger" plain @click="exitGroup">{{ isGroup(selected) && selected.currentUserRole === 2 ? '解散群聊' : '退出群聊' }}</el-button><el-button @click="groupManageVisible = false">关闭</el-button><el-button type="success" :loading="groupManageBusy" @click="saveGroupSettings">保存</el-button></template>
    </el-dialog>
  </section>
</template>
