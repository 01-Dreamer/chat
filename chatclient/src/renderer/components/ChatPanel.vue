<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ChatDotSquare, ChatLineSquare, Clock, Close, FolderOpened, MagicStick, Microphone, Money, Phone, Scissor, Search, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useChatStore } from '../stores/chat'
import { useCallStore } from '../stores/call'
import { useContactsStore } from '../stores/contacts'
import type { Message } from '../types'
import { formatMessageTime, shouldShowMessageTime } from '../utils/messageTime'
import MessageBubble from './MessageBubble.vue'

const appStore = useAppStore()
const chatStore = useChatStore()
const callStore = useCallStore()
const contactsStore = useContactsStore()
const draft = ref('')
const chatWindowRef = ref<HTMLElement | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const hasScrollableContent = ref(false)
const isScrollThumbDragging = ref(false)
const fileUploading = ref(false)
const walletVisible = ref(false)
const walletBusy = ref(false)
const walletMode = ref<'red_packet' | 'transfer'>('red_packet')
const walletForm = reactive({ amount: '', count: 1, greeting: '恭喜发财，大吉大利', payPassword: '', packetType: 1 })
const smartReplyBusy = ref(false)
const smartReplies = ref<string[]>([])
const quoteTarget = ref<Message | null>(null)
const historyVisible = ref(false)
const historyKeyword = ref('')
const scrollThumbHeight = ref(0)
const scrollThumbTop = ref(0)
let resizeObserver: ResizeObserver | undefined
let dragStartY = 0
let dragStartScrollTop = 0

const currentGroupRole = computed(() => {
  const conversation = chatStore.currentConversation
  if (!conversation || conversation.type !== 'group') return 0
  return contactsStore.groups.find((item) => item.id === conversation.targetId)?.currentUserRole ?? 0
})

const historyMessages = computed(() => {
  const keyword = historyKeyword.value.trim().toLocaleLowerCase()
  if (!keyword) return chatStore.currentMessages
  return chatStore.currentMessages.filter((message) => `${message.senderName} ${message.content} ${message.fileName ?? ''}`.toLocaleLowerCase().includes(keyword))
})

function isOwnMessage(message: Message) {
  return message.senderId === appStore.currentUser?.id || message.senderId === 'me'
}

function canRecallMessage(message: Message) {
  if (message.status === 1 || message.id.startsWith('local:')) return false
  if (isOwnMessage(message)) return true
  return chatStore.currentConversation?.type === 'group' && currentGroupRole.value >= 1
}

function findReplyMessage(message: Message) {
  return message.replyMessageId ? chatStore.currentMessages.find((item) => item.id === message.replyMessageId) : undefined
}

function historyPreview(message: Message) {
  if (message.status === 1) return '该消息已撤回'
  if (message.type === 'voice') return '[语音]'
  if (message.type === 'red_packet') return '[红包]'
  if (message.type === 'file') return `[${message.fileKind === 'image' ? '图片' : message.fileKind === 'video' ? '视频' : '文件'}] ${message.fileName ?? ''}`
  return message.content
}

function recallNotice(message: Message) {
  const operatorId = message.recallOperatorId ?? message.senderId
  if (operatorId === appStore.currentUser?.id || operatorId === 'me') return '你撤回了一条消息'
  const operatorName = message.recallOperatorName || (operatorId === message.senderId ? message.senderName : '管理员')
  return `${operatorName}撤回了一条消息`
}

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
  const message = await window.chatApi.sendMessage({ conversationId: chatStore.currentConversation.id, senderId: appStore.currentUser?.id ?? 'me', senderName: appStore.currentUser?.nickname ?? '我', senderAvatar: appStore.currentUser?.avatar ?? '', type: 'text', content, replyMessageId: quoteTarget.value?.id ?? null })
  chatStore.appendMessage(message)
  draft.value = ''
  quoteTarget.value = null
  await nextTick()
  chatWindowRef.value?.scrollTo({ top: chatWindowRef.value.scrollHeight, behavior: 'smooth' })
  updateScrollThumb()
}

async function sendFile(resourceType?: number) {
  if (!chatStore.currentConversation || fileUploading.value) return
  fileUploading.value = true
  try {
    const message = await window.chatApi.selectAndSendFile(chatStore.currentConversation.id, resourceType)
    if (message) {
      chatStore.appendMessage(message)
      await nextTick()
      chatWindowRef.value?.scrollTo({ top: chatWindowRef.value.scrollHeight, behavior: 'smooth' })
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件发送失败')
  } finally {
    fileUploading.value = false
  }
}

async function captureAndSend() {
  if (!chatStore.currentConversation || fileUploading.value) return
  fileUploading.value = true
  try { chatStore.appendMessage(await window.chatApi.captureAndSend(chatStore.currentConversation.id)) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '截图发送失败') }
  finally { fileUploading.value = false }
}

function openWallet() {
  walletMode.value = 'red_packet'
  walletForm.amount = ''
  walletForm.count = chatStore.currentConversation?.type === 'group' ? 2 : 1
  walletForm.greeting = '恭喜发财，大吉大利'
  walletForm.payPassword = ''
  walletVisible.value = true
}

async function submitWalletAction() {
  const conversation = chatStore.currentConversation
  if (!conversation || walletBusy.value) return
  const rawAmount = walletForm.amount.trim()
  if (!/^(0|[1-9]\d{0,9})(\.\d{1,2})?$/.test(rawAmount) || /^0(?:\.0{1,2})?$/.test(rawAmount)) return ElMessage.warning('请输入正确金额')
  if (!/^\d{6}$/.test(walletForm.payPassword)) return ElMessage.warning('请输入 6 位支付密码')
  const amount = rawAmount.includes('.') ? rawAmount.padEnd(rawAmount.indexOf('.') + 3, '0') : `${rawAmount}.00`
  walletBusy.value = true
  try {
    if (walletMode.value === 'transfer') {
      if (conversation.type !== 'direct') throw new Error('只能向好友转账')
      await window.chatApi.transfer(conversation.targetId, amount, walletForm.payPassword)
      ElMessage.success('转账成功')
    } else {
      const message = await window.chatApi.sendRedPacket(conversation.id, {
        chatType: conversation.type === 'group' ? 1 : 0,
        targetId: conversation.targetId,
        packetType: conversation.type === 'group' ? walletForm.packetType : 0,
        totalAmount: amount,
        totalCount: conversation.type === 'group' ? Math.max(1, Math.trunc(walletForm.count)) : 1,
        message: walletForm.greeting.trim(),
        payPassword: walletForm.payPassword,
      })
      chatStore.appendMessage(message)
      ElMessage.success('红包已发送')
    }
    const wallet = await window.chatApi.getWallet()
    if (appStore.currentUser) appStore.currentUser.balance = wallet.balance
    walletVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    walletBusy.value = false
  }
}

async function loadSmartReplies() {
  if (smartReplyBusy.value) return
  const messages = chatStore.currentMessages
    .filter((item) => item.type === 'text' && item.status !== 1)
    .slice(-12)
    .map((item) => ({
      role: item.senderId === appStore.currentUser?.id ? 'assistant' as const : 'user' as const,
      content: item.content,
    }))
  if (!messages.length) return ElMessage.info('当前没有可用于生成回复的聊天内容')
  smartReplyBusy.value = true
  try { smartReplies.value = await window.chatApi.smartReplies(messages) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '智能回复生成失败') }
  finally { smartReplyBusy.value = false }
}

async function startCall(callType: number) {
  const conversation = chatStore.currentConversation
  if (!conversation || conversation.type !== 'direct') return
  try { await callStore.start(conversation.targetId, callType) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '发起通话失败') }
}

async function recallMessage(message: import('../types').Message) {
  try { chatStore.appendMessage(await window.chatApi.recallMessage(message.id)) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '撤回失败') }
}

function quoteMessage(message: Message) {
  quoteTarget.value = message
}

watch(() => chatStore.currentConversationId, async () => { quoteTarget.value = null; historyKeyword.value = ''; await nextTick(); chatWindowRef.value?.scrollTo({ top: chatWindowRef.value.scrollHeight }); updateScrollThumb() })
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
      </header>
      <div class="chat-scroll-area no-drag">
        <div ref="chatWindowRef" class="chat-window no-drag" @scroll="updateScrollThumb">
          <div ref="messageListRef" class="message-list">
            <template v-for="(message, index) in chatStore.currentMessages" :key="message.id">
              <div v-if="shouldShowMessageTime(chatStore.currentMessages, index)" class="message-time">{{ formatMessageTime(message.createdAt) }}</div>
              <div v-if="message.status === 1" class="message-recall-notice">{{ recallNotice(message) }}</div>
              <MessageBubble v-else :message="message" :own="isOwnMessage(message)" :can-recall="canRecallMessage(message)" :reply-message="findReplyMessage(message)" @recall="recallMessage" @quote="quoteMessage" />
            </template>
          </div>
        </div>
        <div v-show="hasScrollableContent" class="chat-scroll-thumb" :class="{ dragging: isScrollThumbDragging }" :style="{ height: `${scrollThumbHeight}px`, transform: `translateY(${scrollThumbTop}px)` }" @pointerdown="startScrollThumbDrag" />
      </div>
      <footer class="input-area no-drag">
        <div class="input-toolbar">
          <button title="发送文件" :disabled="fileUploading" @click="sendFile()"><el-icon><FolderOpened /></el-icon></button>
          <button title="截图" :disabled="fileUploading" @click="captureAndSend"><el-icon><Scissor /></el-icon></button>
          <button title="发送语音" :disabled="fileUploading" @click="sendFile(2)"><el-icon><Microphone /></el-icon></button>
          <button title="发送红包" @click="openWallet"><el-icon><Money /></el-icon></button>
          <button title="智能回复" :disabled="smartReplyBusy" @click="loadSmartReplies"><el-icon><MagicStick /></el-icon></button>
          <button v-if="chatStore.currentConversation.type === 'direct'" title="语音聊天" @click="startCall(0)"><el-icon><Phone /></el-icon></button>
          <button v-if="chatStore.currentConversation.type === 'direct'" title="视频聊天" @click="startCall(1)"><el-icon><VideoCamera /></el-icon></button>
          <button title="聊天记录" @click="historyVisible = true"><el-icon><ChatLineSquare /></el-icon></button>
        </div>
        <div v-if="smartReplies.length" class="smart-replies"><button v-for="reply in smartReplies" :key="reply" @click="draft = reply; smartReplies = []">{{ reply }}</button></div>
        <div v-if="quoteTarget" class="quote-composer"><span><strong>{{ quoteTarget.senderName }}</strong>：{{ historyPreview(quoteTarget) }}</span><button title="取消引用" @click="quoteTarget = null"><el-icon><Close /></el-icon></button></div>
        <el-input v-model="draft" type="textarea" resize="none" class="input-textarea" placeholder="输入消息，按 Enter 发送" @keydown.enter.exact.prevent="sendText" />
        <div class="send-row"><el-button size="small" :disabled="!draft.trim()" @click="sendText">发送</el-button></div>
      </footer>
    </template>
    <div v-else class="blank-chat"><el-icon><ChatDotSquare /></el-icon></div>
    <el-dialog v-model="walletVisible" title="红包与转账" width="380px" append-to-body>
      <el-radio-group v-if="chatStore.currentConversation?.type === 'direct'" v-model="walletMode">
        <el-radio-button value="red_packet">发红包</el-radio-button>
        <el-radio-button value="transfer">转账</el-radio-button>
      </el-radio-group>
      <el-form label-position="top" class="wallet-form">
        <el-form-item label="金额"><el-input v-model="walletForm.amount" inputmode="decimal" placeholder="0.00"><template #prepend>¥</template></el-input></el-form-item>
        <el-form-item v-if="walletMode === 'red_packet' && chatStore.currentConversation?.type === 'group'" label="红包个数"><el-input-number v-model="walletForm.count" :min="1" :max="500" /></el-form-item>
        <el-form-item v-if="walletMode === 'red_packet' && chatStore.currentConversation?.type === 'group'" label="红包类型"><el-radio-group v-model="walletForm.packetType"><el-radio :value="1">拼手气</el-radio><el-radio :value="0">普通</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="walletMode === 'red_packet'" label="祝福语"><el-input v-model="walletForm.greeting" maxlength="128" /></el-form-item>
        <el-form-item label="支付密码"><el-input v-model="walletForm.payPassword" type="password" maxlength="6" inputmode="numeric" show-password /></el-form-item>
      </el-form>
      <template #footer><el-button @click="walletVisible = false">取消</el-button><el-button type="success" :loading="walletBusy" @click="submitWalletAction">确认</el-button></template>
    </el-dialog>
    <el-drawer v-model="historyVisible" direction="rtl" size="380px" title="聊天记录" append-to-body class="history-drawer no-drag">
      <div class="history-search"><el-input v-model="historyKeyword" clearable placeholder="搜索聊天记录"><template #prefix><el-icon><Search /></el-icon></template></el-input></div>
      <div class="history-list">
        <div v-for="message in historyMessages" :key="`history-${message.id}`" class="history-item">
          <div><strong>{{ isOwnMessage(message) ? '我' : message.senderName }}</strong><time><el-icon><Clock /></el-icon>{{ formatMessageTime(message.createdAt) }}</time></div>
          <p>{{ historyPreview(message) }}</p>
        </div>
        <div v-if="!historyMessages.length" class="history-empty">没有找到聊天记录</div>
      </div>
    </el-drawer>
  </section>
</template>
