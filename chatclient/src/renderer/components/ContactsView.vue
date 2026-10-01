<script setup lang="ts">
import { ref } from 'vue'
import { ArrowRight, Check, Close, EditPen, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useContactsStore } from '../stores/contacts'
import type { Friend, FriendRequest, GroupChat } from '../types'
import AddMenuDialog from './AddMenuDialog.vue'

const contactsStore = useContactsStore()
const searchText = ref('')
const selected = ref<Friend | GroupChat | FriendRequest | null>(contactsStore.friends[0] ?? null)
const expanded = ref({ requests: true, groups: true, friends: true })
const editingRemark = ref(false)
const remarkDraft = ref('')

function isFriend(item: typeof selected.value): item is Friend { return Boolean(item && 'signature' in item) }
function isGroup(item: typeof selected.value): item is GroupChat { return Boolean(item && 'memberCount' in item) }
function selectItem(item: Friend | GroupChat | FriendRequest) { selected.value = item; editingRemark.value = false }
async function respond(request: FriendRequest, status: 'accepted' | 'rejected') {
  if (status === 'accepted') await window.chatApi.acceptFriendRequest(request.id)
  else await window.chatApi.rejectFriendRequest(request.id)
  contactsStore.updateRequest(request.id, status)
  ElMessage.success(status === 'accepted' ? '已接受申请' : '已拒绝申请')
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
  await window.chatApi.updateContactRemark(kind, selected.value.id, remarkDraft.value)
  contactsStore.updateRemark(kind, selected.value.id, remarkDraft.value.trim())
  editingRemark.value = false
  ElMessage.success('备注已更新')
}
</script>

<template>
  <section class="contacts-workspace">
    <aside class="contact-panel no-drag">
      <div class="mid-search drag-region"><el-input v-model="searchText" class="search-input no-drag" size="small" placeholder="搜索"><template #prefix><el-icon><Search /></el-icon></template></el-input><AddMenuDialog /></div>
      <div class="friend-list-window">
        <button class="list-category" @click="expanded.requests = !expanded.requests"><el-icon :class="{ expanded: expanded.requests }"><ArrowRight /></el-icon><span>新的朋友</span><small>{{ contactsStore.friendRequests.filter((item) => item.status === 'pending').length }}</small></button>
        <div v-show="expanded.requests"><button v-for="item in contactsStore.friendRequests" :key="item.id" class="contact-item" :class="{ active: selected?.id === item.id }" @click="selectItem(item)"><img :src="item.avatar" alt="avatar" /><span class="contact-item-copy"><strong>{{ item.nickname }}</strong><small>{{ item.message }}</small></span></button></div>
        <button class="list-category" @click="expanded.groups = !expanded.groups"><el-icon :class="{ expanded: expanded.groups }"><ArrowRight /></el-icon><span>群聊</span><small>{{ contactsStore.groups.length }}</small></button>
        <div v-show="expanded.groups"><button v-for="item in contactsStore.groups" :key="item.id" class="contact-item" :class="{ active: selected?.id === item.id }" @click="selectItem(item)"><img :src="item.avatar" alt="avatar" /><span class="contact-simple-name">{{ item.name }}</span></button></div>
        <button class="list-category" @click="expanded.friends = !expanded.friends"><el-icon :class="{ expanded: expanded.friends }"><ArrowRight /></el-icon><span>好友</span><small>{{ contactsStore.friends.length }}</small></button>
        <div v-show="expanded.friends"><button v-for="item in contactsStore.friends" :key="item.id" class="contact-item" :class="{ active: selected?.id === item.id }" @click="selectItem(item)"><img :src="item.avatar" alt="avatar" /><span class="contact-simple-name">{{ item.nickname }}</span></button></div>
      </div>
    </aside>

    <section class="profile-panel">
      <template v-if="selected">
        <div class="profile-content">
          <img :src="selected.avatar" alt="avatar" class="profile-avatar" />
          <template v-if="isFriend(selected)"><h2>{{ selected.remark || selected.nickname }}</h2><div class="profile-fields"><div><span>用户名</span><strong>{{ selected.username }}</strong></div><div><span>好友昵称</span><strong>{{ selected.nickname }}</strong></div><div class="remark-field"><span>备注</span><strong v-if="!editingRemark">{{ selected.remark || '暂无备注' }}</strong><el-input v-else v-model="remarkDraft" size="small" maxlength="30" @keyup.enter="saveRemark" /><span class="remark-actions"><button v-if="!editingRemark" title="修改备注" @click="startRemarkEdit"><el-icon><EditPen /></el-icon></button><template v-else><button title="保存" @click="saveRemark"><el-icon><Check /></el-icon></button><button title="取消" @click="cancelRemarkEdit"><el-icon><Close /></el-icon></button></template></span></div></div><p class="profile-signature">{{ selected.signature }}</p></template>
          <template v-else-if="isGroup(selected)"><h2>{{ selected.remark || selected.name }}</h2><div class="profile-fields"><div><span>群聊号</span><strong>{{ selected.groupNumber }}</strong></div><div><span>群聊名称</span><strong>{{ selected.name }}</strong></div><div class="remark-field"><span>备注</span><strong v-if="!editingRemark">{{ selected.remark || '暂无备注' }}</strong><el-input v-else v-model="remarkDraft" size="small" maxlength="30" @keyup.enter="saveRemark" /><span class="remark-actions"><button v-if="!editingRemark" title="修改备注" @click="startRemarkEdit"><el-icon><EditPen /></el-icon></button><template v-else><button title="保存" @click="saveRemark"><el-icon><Check /></el-icon></button><button title="取消" @click="cancelRemarkEdit"><el-icon><Close /></el-icon></button></template></span></div></div><p class="profile-signature">{{ selected.description }} · {{ selected.memberCount }} 位成员</p></template>
          <template v-else><h2>{{ selected.nickname }}</h2><p>用户名：{{ selected.username }}</p><p>{{ selected.message }}</p></template>
        </div>
        <div class="profile-actions" v-if="isFriend(selected) || isGroup(selected)"><button @click="ElMessage.info('Mock：打开对应会话')">开始聊天</button></div>
        <div class="profile-actions request-buttons" v-else-if="selected.status === 'pending'"><button @click="respond(selected, 'accepted')">接受</button><button class="secondary" @click="respond(selected, 'rejected')">拒绝</button></div>
        <div v-else class="profile-actions"><el-tag>{{ selected.status === 'accepted' ? '已接受' : '已拒绝' }}</el-tag></div>
      </template>
      <div v-else class="blank-chat"><span>暂无联系人</span></div>
    </section>
  </section>
</template>
