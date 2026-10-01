<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChatDotSquare, ChatLineSquare, FolderOpened, Microphone, Money, MoreFilled, Scissor } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useChatStore } from '../stores/chat'
import { formatMessageTime, shouldShowMessageTime } from '../utils/messageTime'
import MessageBubble from './MessageBubble.vue'

const appStore = useAppStore()
const chatStore = useChatStore()
const draft = ref('')
const chatWindowRef = ref<HTMLElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const hasScrollableContent = ref(false)
const isScrollThumbDragging = ref(false)
const scrollThumbHeight = ref(0)
const scrollThumbTop = ref(0)
let resizeObserver: ResizeObserver | undefined
let dragStartY = 0
let dragStartScrollTop = 0

function updateScrollThumb() {
  const element = chatWindowRef.value
  if (!element) return
  const trackHeight = Math.max(element.clientHeight - 8, 0)
  const maxScrollTop = element.scrollHeight - element.clientHeight
  hasScrollableContent.value = maxScrollTop > 1
  if (!hasScrollableContent.value || trackHeight === 0) return
  scrollThumbHeight.value = Math.max(34, trackHeight * (element.clientHeight / element.scrollHeight))
  const movableDistance = Math.max(trackHeight - scrollThumbHeight.value, 0)
  scrollThumbTop.value = 4 + (element.scrollTop / maxScrollTop) * movableDistance
}

function stopScrollThumbDrag() {
  isScrollThumbDragging.value = false
  window.removeEventListener('pointermove', handleScrollThumbDrag)
  window.removeEventListener('pointerup', stopScrollThumbDrag)
}

function handleScrollThumbDrag(event: PointerEvent) {
  const element = chatWindowRef.value
  if (!element) return
  const trackHeight = Math.max(element.clientHeight - 8, 0)
  const movableDistance = trackHeight - scrollThumbHeight.value
  const maxScrollTop = element.scrollHeight - element.clientHeight
  if (movableDistance <= 0 || maxScrollTop <= 0) return
  element.scrollTop = dragStartScrollTop + (event.clientY - dragStartY) * (maxScrollTop / movableDistance)
}

function startScrollThumbDrag(event: PointerEvent) {
  event.preventDefault()
  event.stopPropagation()
  isScrollThumbDragging.value = true
  dragStartY = event.clientY
  dragStartScrollTop = chatWindowRef.value?.scrollTop ?? 0
  window.addEventListener('pointermove', handleScrollThumbDrag)
  window.addEventListener('pointerup', stopScrollThumbDrag, { once: true })
}

async function sendText() {
  const content = draft.value.trim()
  if (!content || !chatStore.currentConversation) return
  const message = await window.chatApi.sendMessage({ conversationId: chatStore.currentConversation.id, senderId: appStore.currentUser?.id ?? 'me', senderName: appStore.currentUser?.nickname ?? '我', senderAvatar: appStore.currentUser?.avatar ?? '', type: 'text', content })
  chatStore.appendMessage(message)
  draft.value = ''
  await nextTick()
  chatWindowRef.value?.scrollTo({ top: chatWindowRef.value.scrollHeight, behavior: 'smooth' })
  updateScrollThumb()
}

function mockAction(label: string) { ElMessage.info(`${label}功能当前为 Mock 交互`) }
watch(() => chatStore.currentConversationId, async () => { await nextTick(); chatWindowRef.value?.scrollTo({ top: chatWindowRef.value.scrollHeight }); updateScrollThumb() })
watch(() => chatStore.currentMessages.length, async () => { await nextTick(); updateScrollThumb() })
onMounted(async () => {
  await nextTick()
  resizeObserver = new ResizeObserver(updateScrollThumb)
  if (chatWindowRef.value) resizeObserver.observe(chatWindowRef.value)
  if (messageListRef.value) resizeObserver.observe(messageListRef.value)
  updateScrollThumb()
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  stopScrollThumbDrag()
})
</script>

<template>
  <section class="chat-panel">
    <template v-if="chatStore.currentConversation">
      <header class="session-info no-drag">
        <div class="session-drag-strip" aria-hidden="true" />
        <div class="current-session-name no-drag">{{ chatStore.currentConversation.name }} <el-icon v-if="chatStore.currentConversation.type === 'group'" class="group-mark"><ChatDotSquare /></el-icon></div>
        <button type="button" class="more-button no-drag"><el-icon><MoreFilled /></el-icon></button>
      </header>
      <div class="chat-scroll-area no-drag">
        <div ref="chatWindowRef" class="chat-window no-drag" @scroll="updateScrollThumb">
          <div ref="messageListRef" class="message-list">
            <template v-for="(message, index) in chatStore.currentMessages" :key="message.id">
              <div v-if="shouldShowMessageTime(chatStore.currentMessages, index)" class="message-time">{{ formatMessageTime(message.createdAt) }}</div>
              <MessageBubble :message="message" :own="message.senderId === appStore.currentUser?.id || message.senderId === 'me'" />
            </template>
          </div>
        </div>
        <div v-show="hasScrollableContent" class="chat-scroll-thumb" :class="{ dragging: isScrollThumbDragging }" :style="{ height: `${scrollThumbHeight}px`, transform: `translateY(${scrollThumbTop}px)` }" @pointerdown="startScrollThumbDrag" />
      </div>
      <footer class="input-area no-drag">
        <div class="input-toolbar"><button title="发送文件（包含图片）" @click="mockAction('文件')"><el-icon><FolderOpened /></el-icon></button><button title="截图" @click="mockAction('截图')"><el-icon><Scissor /></el-icon></button><button title="语音" @click="mockAction('语音')"><el-icon><Microphone /></el-icon></button><button title="红包" @click="mockAction('红包')"><el-icon><Money /></el-icon></button><button title="聊天记录"><el-icon><ChatLineSquare /></el-icon></button></div>
        <el-input v-model="draft" type="textarea" resize="none" class="input-textarea" placeholder="输入消息，按 Enter 发送" @keydown.enter.exact.prevent="sendText" />
        <div class="send-row"><el-button size="small" :disabled="!draft.trim()" @click="sendText">发送</el-button></div>
      </footer>
    </template>
    <div v-else class="blank-chat"><el-icon><ChatDotSquare /></el-icon></div>
  </section>
</template>
