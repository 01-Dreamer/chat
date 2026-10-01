<script setup lang="ts">
import { ref } from 'vue'
import { ChatDotSquare, Search } from '@element-plus/icons-vue'
import { useChatStore } from '../stores/chat'
import AddMenuDialog from './AddMenuDialog.vue'

const chatStore = useChatStore()
const searchText = ref('')
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
      <button v-for="conversation in chatStore.conversations" :key="conversation.id" class="session-item" :class="{ active: conversation.id === chatStore.currentConversationId }" @click="chatStore.selectConversation(conversation.id)">
        <el-badge :hidden="!conversation.unread" :value="conversation.unread" :max="99" class="session-avatar"><img :src="conversation.avatar" alt="avatar" /></el-badge>
        <span class="session-copy">
          <span class="session-top"><span class="session-name-line"><span class="session-name">{{ conversation.name }}</span><el-icon v-if="conversation.type === 'group'" class="group-mark session-type-mark"><ChatDotSquare /></el-icon></span><time>{{ conversation.time }}</time></span>
          <span class="session-preview">{{ conversation.preview }}</span>
        </span>
      </button>
    </div>
  </aside>
</template>
