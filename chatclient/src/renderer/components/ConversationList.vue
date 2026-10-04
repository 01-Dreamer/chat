<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ChatDotSquare, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '../stores/chat'
import type { Conversation } from '../types'
import AddMenuDialog from './AddMenuDialog.vue'
import AvatarDisplay from './AvatarDisplay.vue'

const chatStore = useChatStore()
const searchText = ref('')
const contextConversation = ref<Conversation | null>(null)
const contextMenuX = ref(0)
const contextMenuY = ref(0)

function openContextMenu(event: MouseEvent, conversation: Conversation) {
  contextConversation.value = conversation
  contextMenuX.value = Math.min(event.clientX, window.innerWidth - 134)
  contextMenuY.value = Math.min(event.clientY, window.innerHeight - 88)
}

function closeContextMenu() {
  contextConversation.value = null
}

async function selectConversation(conversation: Conversation) {
  chatStore.selectConversation(conversation.id)
  try {
    await window.chatApi.setActiveSession(conversation.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '未读状态保存失败')
  }
}

async function togglePinned() {
  const conversation = contextConversation.value
  if (!conversation) return
  closeContextMenu()
  try {
    const items = await window.chatApi.setSessionPinned(conversation.id, !conversation.pinned)
    chatStore.replaceConversations(items)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会话置顶设置失败')
  }
}

async function hideConversation() {
  const conversation = contextConversation.value
  if (!conversation) return
  closeContextMenu()
  try {
    await window.chatApi.hideSession(conversation.id)
    chatStore.removeConversation(conversation.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '隐藏会话失败')
  }
}

onMounted(() => {
  document.addEventListener('click', closeContextMenu)
  window.addEventListener('blur', closeContextMenu)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', closeContextMenu)
  window.removeEventListener('blur', closeContextMenu)
})
</script>

<template>
  <aside class="conversation-panel no-drag">
    <div class="mid-search drag-region">
      <el-input v-model="searchText" class="search-input no-drag" size="small" placeholder="搜索">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <AddMenuDialog />
    </div>
    <div class="session-window">
      <button v-for="conversation in chatStore.conversations" :key="conversation.id" class="session-item" :class="{ active: conversation.id === chatStore.currentConversationId, pinned: conversation.pinned }" @click="selectConversation(conversation)" @contextmenu.prevent.stop="openContextMenu($event, conversation)">
        <el-badge :hidden="!conversation.unread" :value="conversation.unread" :max="99" class="session-avatar"><AvatarDisplay :src="conversation.avatar" :name="conversation.name" :size="38" :radius="4" /></el-badge>
        <span class="session-copy">
          <span class="session-top"><span class="session-name-line"><span class="session-name">{{ conversation.name }}</span><el-icon v-if="conversation.type === 'group'" class="group-mark session-type-mark"><ChatDotSquare /></el-icon></span><time>{{ conversation.time }}</time></span>
          <span class="session-preview">{{ conversation.preview }}</span>
        </span>
      </button>
    </div>
    <Teleport to="body">
      <div v-if="contextConversation" class="session-context-menu no-drag" :style="{ left: `${contextMenuX}px`, top: `${contextMenuY}px` }" @click.stop @contextmenu.prevent>
        <button @click="togglePinned">{{ contextConversation.pinned ? '取消置顶' : '置顶' }}</button>
        <button @click="hideConversation">不显示</button>
      </div>
    </Teleport>
  </aside>
</template>
