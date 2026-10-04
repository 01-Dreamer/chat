<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ChatDotSquare, ChatLineSquare, Clock, Close, FolderOpened, MagicStick, Microphone, Money, MoreFilled, Phone, Scissor, Search, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useChatStore } from '../stores/chat'
import { useCallStore } from '../stores/call'
import { useContactsStore } from '../stores/contacts'
import type { GroupMember, Message, PendingAttachment } from '../types'
import { formatMessageTime, shouldShowMessageTime } from '../utils/messageTime'
import AvatarDisplay from './AvatarDisplay.vue'
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
const composerBusy = ref(false)
const pendingAttachments = ref<PendingAttachment[]>([])
const draggingFiles = ref(false)
const recording = ref(false)
const recordingSeconds = ref(0)
const recordingBusy = ref(false)
const walletVisible = ref(false)
const walletBusy = ref(false)
const walletForm = reactive({ amount: '', count: 1, greeting: '恭喜发财，大吉大利', payPassword: '', packetType: 1 })
const smartReplyBusy = ref(false)
const smartReplies = ref<string[]>([])
const quoteTarget = ref<Message | null>(null)
const historyVisible = ref(false)
const historyKeyword = ref('')
const groupMembersVisible = ref(false)
const groupMembersBusy = ref(false)
const groupMembers = ref<GroupMember[]>([])
const loadingOlderMessages = ref(false)
const scrollThumbHeight = ref(0)
const scrollThumbTop = ref(0)
let resizeObserver: ResizeObserver | undefined
let dragStartY = 0
let dragStartScrollTop = 0
let shouldStickToBottom = true
let mediaRecorder: MediaRecorder | null = null
let recordingStream: MediaStream | null = null
let recordingTimer: number | undefined
let recordingChunks: Blob[] = []
let discardRecording = false
let recordingConversationId = ''
let jumpHighlightTimer: number | undefined
let recallClockTimer: number | undefined
const recallClock = ref(Date.now())
const SELF_RECALL_WINDOW_MS = 5 * 60 * 1000

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
  if (message.type === 'red_packet' || message.status === 1 || message.id.startsWith('local:')) return false
  if (isOwnMessage(message)) return recallClock.value - message.createdAt <= SELF_RECALL_WINDOW_MS
  return chatStore.currentConversation?.type === 'group' && currentGroupRole.value >= 1
}

function findReplyMessage(message: Message) {
  return message.replyMessageId ? chatStore.currentMessages.find((item) => item.id === message.replyMessageId) : undefined
}

function messageSenderDisplayName(message?: Message | null) {
  if (!message) return '用户'
  if (message.senderId === appStore.currentUser?.id || message.senderId === 'me') {
    return appStore.currentUser?.nickname || message.senderName || '我'
  }
  const friend = contactsStore.friends.find((item) => item.id === message.senderId)
  return friend?.remark?.trim() || friend?.nickname || message.senderName || '用户'
}

function quotePreview(message: Message) {
  if (message.status === 1) return '[消息已撤回]'
  if (message.type === 'red_packet') return '[红包]'
  if (message.type === 'voice') return '[语音]'
  if (message.type === 'file') {
    if (message.fileKind === 'image') return '[图片]'
    if (message.fileKind === 'video') return '[视频]'
    return '[文件]'
  }
  return message.content || '[消息]'
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
  if (chatStore.currentConversation?.type === 'group') {
    if (operatorId === message.senderId) return `${messageSenderDisplayName(message)}撤回了一条消息`
    const operatorName = message.recallOperatorName
      || (operatorId === appStore.currentUser?.id || operatorId === 'me' ? appStore.currentUser?.nickname : '')
    return `管理员${operatorName || ''}撤回了一条消息`
  }
  if (operatorId === appStore.currentUser?.id || operatorId === 'me') return '你撤回了一条消息'
  return `${message.recallOperatorName || message.senderName}撤回了一条消息`
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

function isAtBottom(element: HTMLElement) {
  return element.scrollHeight - element.clientHeight - element.scrollTop <= 8
}

function scrollToBottom(behavior: ScrollBehavior = 'auto') {
  const element = chatWindowRef.value
  if (!element) return
  shouldStickToBottom = true
  const bottom = Math.max(0, element.scrollHeight - element.clientHeight)
  if (behavior === 'auto') element.scrollTop = bottom
  else element.scrollTo({ top: bottom, behavior })
  updateScrollThumb()
}

async function settleScrollToBottom(conversationId: string | null) {
  await nextTick()
  // Message bubbles and their resource metadata may render over several layout
  // frames. Recalculate the actual maximum instead of relying on the first frame.
  for (let frame = 0; frame < 3; frame++) {
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    if (conversationId !== chatStore.currentConversationId) return
    scrollToBottom()
  }
}

function handleMessageContentResized() {
  requestAnimationFrame(() => {
    if (shouldStickToBottom) scrollToBottom()
    else updateScrollThumb()
  })
}

async function loadOlderMessages() {
  const conversationId = chatStore.currentConversationId
  const element = chatWindowRef.value
  if (!conversationId || !element || loadingOlderMessages.value) return
  const pageState = chatStore.getMessagePageState(conversationId)
  if (!pageState.hasMore || !pageState.nextCursor) return
  loadingOlderMessages.value = true
  shouldStickToBottom = false
  const previousHeight = element.scrollHeight
  const previousTop = element.scrollTop
  try {
    const page = await window.chatApi.loadConversationMessages(conversationId, pageState.nextCursor)
    if (conversationId !== chatStore.currentConversationId) return
    chatStore.prependConversationMessagePage(conversationId, page)
    await nextTick()
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
    element.scrollTop = previousTop + element.scrollHeight - previousHeight
    updateScrollThumb()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '聊天记录加载失败')
  } finally {
    loadingOlderMessages.value = false
  }
}

function handleChatScroll() {
  const element = chatWindowRef.value
  if (element) {
    shouldStickToBottom = isAtBottom(element)
    if (element.scrollTop <= 48) void loadOlderMessages()
  }
  updateScrollThumb()
}

async function openGroupMembers() {
  const conversation = chatStore.currentConversation
  if (!conversation || conversation.type !== 'group') return
  groupMembersVisible.value = true
  groupMembersBusy.value = true
  try {
    groupMembers.value = await window.chatApi.listGroupMembers(conversation.targetId)
    chatStore.applyGroupMemberProfiles(conversation.targetId, groupMembers.value)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '群成员加载失败')
  } finally {
    groupMembersBusy.value = false
  }
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

function attachmentSnapshot(attachment: PendingAttachment): PendingAttachment {
  // Items stored in a Vue ref are reactive proxies. Electron cannot structured-clone
  // a Proxy across contextBridge/IPC, so only send a plain data object.
  return {
    id: attachment.id,
    filePath: attachment.filePath,
    fileName: attachment.fileName,
    fileSize: attachment.fileSize,
    mimeType: attachment.mimeType,
    resourceType: attachment.resourceType,
    previewUrl: attachment.previewUrl,
  }
}

function addAttachments(attachments: PendingAttachment[]) {
  if (!attachments.length) return
  pendingAttachments.value.push(...attachments)
}

async function selectFiles() {
  try { addAttachments(await window.chatApi.selectAttachments()) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '选择文件失败') }
}

async function captureScreen() {
  try {
    const attachment = await window.chatApi.captureScreen()
    if (attachment) addAttachments([attachment])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '截图失败')
  }
}

async function handleFileDrop(event: DragEvent) {
  draggingFiles.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (!files.length) return
  try { addAttachments(await window.chatApi.stageDroppedFiles(files)) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '读取拖入文件失败') }
}

function removeAttachment(index: number) {
  pendingAttachments.value.splice(index, 1)
}

type ComposerAction = { type: 'text', content: string } | { type: 'attachment', attachment: PendingAttachment }

function composerActions(text: string, attachments: PendingAttachment[]) {
  const actions: ComposerAction[] = []
  const content = text.trim()
  if (content) actions.push({ type: 'text', content })
  for (const attachment of attachments) actions.push({ type: 'attachment', attachment })
  return actions
}

function restoreActions(actions: ComposerAction[]) {
  pendingAttachments.value = actions.flatMap((action) => action.type === 'attachment' ? [action.attachment] : [])
  draft.value = actions.flatMap((action) => action.type === 'text' ? [action.content] : []).join('\n')
}

async function sendComposer() {
  const conversation = chatStore.currentConversation
  if (!conversation || composerBusy.value) return
  const actions = composerActions(draft.value, [...pendingAttachments.value])
  if (!actions.length) return
  const quotedMessageId = quoteTarget.value?.id ?? null
  draft.value = ''
  pendingAttachments.value = []
  quoteTarget.value = null
  composerBusy.value = true
  let index = 0
  try {
    for (; index < actions.length; index++) {
      const action = actions[index]
      const message = action.type === 'text'
        ? await window.chatApi.sendMessage({ conversationId: conversation.id, senderId: appStore.currentUser?.id ?? 'me', senderName: appStore.currentUser?.nickname ?? '我', senderAvatar: appStore.currentUser?.avatar ?? '', type: 'text', content: action.content, replyMessageId: index === 0 ? quotedMessageId : null })
        : await window.chatApi.sendAttachment(conversation.id, attachmentSnapshot(action.attachment))
      chatStore.appendMessage(message)
      await nextTick()
      scrollToBottom('smooth')
    }
  } catch (error) {
    restoreActions(actions.slice(index))
    ElMessage.error(error instanceof Error ? error.message : '消息发送失败')
  } finally {
    composerBusy.value = false
  }
}

function stopRecordingTracks() {
  recordingStream?.getTracks().forEach((track) => track.stop())
  recordingStream = null
  window.clearInterval(recordingTimer)
  recordingTimer = undefined
  recording.value = false
}

async function startVoiceRecording() {
  if (recordingBusy.value) return
  if (recording.value) {
    if (mediaRecorder?.state === 'recording') mediaRecorder.stop()
    return
  }
  try {
    recordingStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const preferredType = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/webm']
      .find((type) => MediaRecorder.isTypeSupported(type))
    mediaRecorder = preferredType
      ? new MediaRecorder(recordingStream, { mimeType: preferredType })
      : new MediaRecorder(recordingStream)
    recordingChunks = []
    discardRecording = false
    recordingConversationId = chatStore.currentConversation?.id ?? ''
    recordingSeconds.value = 0
    const startedAt = Date.now()
    mediaRecorder.ondataavailable = (event) => { if (event.data.size) recordingChunks.push(event.data) }
    mediaRecorder.onstop = async () => {
      const recorderMimeType = mediaRecorder?.mimeType || preferredType || 'audio/webm'
      const duration = Math.max(1, Math.round((Date.now() - startedAt) / 1000))
      stopRecordingTracks()
      if (discardRecording) return
      const conversationId = recordingConversationId
      if (!conversationId || !recordingChunks.length) return
      recordingBusy.value = true
      try {
        const blob = new Blob(recordingChunks, { type: recorderMimeType })
        const message = await window.chatApi.sendVoice(conversationId, new Uint8Array(await blob.arrayBuffer()), recorderMimeType, duration)
        chatStore.appendMessage(message)
        await nextTick()
        scrollToBottom('smooth')
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '语音发送失败')
      } finally {
        recordingBusy.value = false
        recordingChunks = []
        mediaRecorder = null
      }
    }
    mediaRecorder.start(250)
    recording.value = true
    recordingTimer = window.setInterval(() => {
      recordingSeconds.value = Math.floor((Date.now() - startedAt) / 1000)
      if (recordingSeconds.value >= 60 && mediaRecorder?.state === 'recording') mediaRecorder.stop()
    }, 250)
  } catch (error) {
    stopRecordingTracks()
    ElMessage.error(error instanceof Error ? error.message : '无法使用麦克风')
  }
}

function openWallet() {
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

async function retryMessage(message: Message) {
  if (!message.clientMessageId) return
  try { chatStore.appendMessage(await window.chatApi.retryMessage(message.clientMessageId)) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '消息重发失败') }
}

function quoteMessage(message: Message) {
  quoteTarget.value = message
}

function jumpToMessage(messageId: string) {
  const target = Array.from(messageListRef.value?.querySelectorAll<HTMLElement>('[data-message-id]') ?? [])
    .find((element) => element.dataset.messageId === messageId)
  if (!target) {
    ElMessage.info('原消息不在当前聊天记录中')
    return
  }
  shouldStickToBottom = false
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  window.clearTimeout(jumpHighlightTimer)
  target.classList.remove('message-jump-highlight')
  requestAnimationFrame(() => target.classList.add('message-jump-highlight'))
  jumpHighlightTimer = window.setTimeout(() => target.classList.remove('message-jump-highlight'), 1600)
}

watch(() => chatStore.currentConversationId, (conversationId) => {
  quoteTarget.value = null
  historyKeyword.value = ''
  pendingAttachments.value = []
  draft.value = ''
  shouldStickToBottom = true
  void settleScrollToBottom(conversationId)
})
watch(() => chatStore.currentMessages.length, async (length, previousLength) => {
  const conversationId = chatStore.currentConversationId
  const element = chatWindowRef.value
  const followNewMessage = length > previousLength && shouldStickToBottom && (!element || isAtBottom(element))
  await nextTick()
  if (conversationId !== chatStore.currentConversationId) return
  if (followNewMessage) scrollToBottom()
  else updateScrollThumb()
})
onMounted(async () => {
  recallClockTimer = window.setInterval(() => { recallClock.value = Date.now() }, 15_000)
  await nextTick()
  resizeObserver = new ResizeObserver(() => {
    if (shouldStickToBottom) scrollToBottom()
    else updateScrollThumb()
  })
  if (chatWindowRef.value) resizeObserver.observe(chatWindowRef.value)
  if (messageListRef.value) resizeObserver.observe(messageListRef.value)
  void settleScrollToBottom(chatStore.currentConversationId)
})
onBeforeUnmount(() => {
  window.clearInterval(recallClockTimer)
  resizeObserver?.disconnect()
  stopScrollThumbDrag()
  window.clearTimeout(jumpHighlightTimer)
  discardRecording = true
  if (mediaRecorder?.state === 'recording') mediaRecorder.stop()
  else stopRecordingTracks()
})
</script>

<template>
  <section class="chat-panel">
    <template v-if="chatStore.currentConversation">
      <header class="session-info no-drag">
        <div class="session-drag-strip" aria-hidden="true" />
        <div class="current-session-name no-drag">{{ chatStore.currentConversation.name }} <el-icon v-if="chatStore.currentConversation.type === 'group'" class="group-mark"><ChatDotSquare /></el-icon></div>
        <button v-if="chatStore.currentConversation.type === 'group'" class="more-button group-members-button" title="查看群成员" @click="openGroupMembers"><el-icon><MoreFilled /></el-icon></button>
      </header>
      <div class="chat-scroll-area no-drag">
        <div ref="chatWindowRef" class="chat-window no-drag" @scroll="handleChatScroll">
          <div ref="messageListRef" class="message-list">
            <div v-if="loadingOlderMessages" class="older-messages-loading">正在加载更早的消息...</div>
            <template v-for="(message, index) in chatStore.currentMessages" :key="message.id">
              <div v-if="shouldShowMessageTime(chatStore.currentMessages, index)" class="message-time">{{ formatMessageTime(message.createdAt) }}</div>
              <div v-if="message.status === 1" class="message-recall-notice" :data-message-id="message.id">{{ recallNotice(message) }}</div>
              <MessageBubble v-else :message="message" :own="isOwnMessage(message)" :can-recall="canRecallMessage(message)" :reply-message="findReplyMessage(message)" :reply-sender-name="messageSenderDisplayName(findReplyMessage(message))" @recall="recallMessage" @quote="quoteMessage" @retry="retryMessage" @jump-to-message="jumpToMessage" @content-resized="handleMessageContentResized" />
            </template>
          </div>
        </div>
        <div v-show="hasScrollableContent" class="chat-scroll-thumb" :class="{ dragging: isScrollThumbDragging }" :style="{ height: `${scrollThumbHeight}px`, transform: `translateY(${scrollThumbTop}px)` }" @pointerdown="startScrollThumbDrag" />
      </div>
      <footer class="input-area no-drag" :class="{ 'dragging-files': draggingFiles }" @dragenter.prevent="draggingFiles = true" @dragover.prevent @dragleave.self="draggingFiles = false" @drop.prevent="handleFileDrop">
        <div class="input-toolbar">
          <button title="发送文件" :disabled="composerBusy" @click="selectFiles"><el-icon><FolderOpened /></el-icon></button>
          <button title="截图" :disabled="composerBusy" @click="captureScreen"><el-icon><Scissor /></el-icon></button>
          <button :title="recording ? '结束录音并发送' : '发送语音'" :class="{ recording }" :disabled="recordingBusy" @click="startVoiceRecording"><el-icon><Microphone /></el-icon></button>
          <button title="发送红包" @click="openWallet"><el-icon><Money /></el-icon></button>
          <button title="智能回复" :disabled="smartReplyBusy" @click="loadSmartReplies"><el-icon><MagicStick /></el-icon></button>
          <button v-if="chatStore.currentConversation.type === 'direct'" title="语音聊天" @click="startCall(0)"><el-icon><Phone /></el-icon></button>
          <button v-if="chatStore.currentConversation.type === 'direct'" title="视频聊天" @click="startCall(1)"><el-icon><VideoCamera /></el-icon></button>
          <button title="聊天记录" @click="historyVisible = true"><el-icon><ChatLineSquare /></el-icon></button>
        </div>
        <div v-if="smartReplies.length" class="smart-replies"><button v-for="reply in smartReplies" :key="reply" @click="draft = reply; smartReplies = []">{{ reply }}</button></div>
        <div v-if="recording" class="recording-status"><i />正在录音 {{ recordingSeconds }} 秒，再次点击麦克风发送（最长 60 秒）</div>
        <div v-if="pendingAttachments.length" class="pending-attachments">
          <div v-for="(attachment, index) in pendingAttachments" :key="attachment.id" class="pending-attachment">
            <img v-if="attachment.previewUrl" :src="attachment.previewUrl" :alt="attachment.fileName" />
            <el-icon v-else><FolderOpened /></el-icon>
            <span :title="attachment.fileName">{{ attachment.fileName }}</span>
            <button title="移除" @click="removeAttachment(index)"><el-icon><Close /></el-icon></button>
          </div>
        </div>
        <el-input v-model="draft" type="textarea" resize="none" class="input-textarea" placeholder="输入消息，按 Enter 发送；也可以拖入文件" @keydown.enter.exact.prevent="sendComposer" />
        <div v-if="quoteTarget" class="quote-composer" title="跳转到原消息" @click="jumpToMessage(quoteTarget.id)">
          <span><strong>{{ messageSenderDisplayName(quoteTarget) }}</strong><small>{{ quotePreview(quoteTarget) }}</small></span>
          <button title="取消引用" @click.stop="quoteTarget = null"><el-icon><Close /></el-icon></button>
        </div>
        <div class="send-row"><el-button size="small" :loading="composerBusy" :disabled="!draft.trim() && !pendingAttachments.length" @click="sendComposer">发送</el-button></div>
      </footer>
    </template>
    <div v-else class="blank-chat"><el-icon><ChatDotSquare /></el-icon></div>
    <el-dialog v-model="walletVisible" title="发红包" width="380px" append-to-body>
      <el-form label-position="top" class="wallet-form">
        <el-form-item label="金额"><el-input v-model="walletForm.amount" inputmode="decimal" placeholder="0.00"><template #prepend>¥</template></el-input></el-form-item>
        <el-form-item v-if="chatStore.currentConversation?.type === 'group'" label="红包个数"><el-input-number v-model="walletForm.count" :min="1" :max="500" /></el-form-item>
        <el-form-item v-if="chatStore.currentConversation?.type === 'group'" label="红包类型"><el-radio-group v-model="walletForm.packetType"><el-radio :value="1">拼手气</el-radio><el-radio :value="0">普通</el-radio></el-radio-group></el-form-item>
        <el-form-item label="祝福语"><el-input v-model="walletForm.greeting" maxlength="128" /></el-form-item>
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
    <el-drawer v-model="groupMembersVisible" direction="rtl" size="360px" title="群成员" append-to-body class="side-drawer group-members-drawer no-drag">
      <div class="readonly-group-summary">{{ chatStore.currentConversation?.name }} · {{ groupMembers.length }} 位成员</div>
      <div class="readonly-group-members" v-loading="groupMembersBusy">
        <div v-for="member in groupMembers" :key="member.id" class="group-member-row">
          <AvatarDisplay :src="member.avatar" :name="member.groupNickname || member.nickname" :size="36" />
          <span class="group-member-copy"><strong>{{ member.groupNickname || member.nickname }}</strong><small>{{ member.role === 2 ? '群主' : member.role === 1 ? '管理员' : '成员' }}</small></span>
        </div>
        <div v-if="!groupMembersBusy && !groupMembers.length" class="history-empty">暂无可显示的群成员</div>
      </div>
      <p class="readonly-group-tip">此处仅供查看；群主和管理员请前往通讯录进行群管理。</p>
    </el-drawer>
  </section>
</template>
