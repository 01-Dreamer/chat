<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Document, Loading, Money, WarningFilled, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import type { Message } from '../types'
import AvatarDisplay from './AvatarDisplay.vue'

const props = defineProps<{ message: Message; own: boolean; canRecall: boolean; replyMessage?: Message; replySenderName?: string }>()
const appStore = useAppStore()
const emit = defineEmits<{
  recall: [message: Message]
  quote: [message: Message]
  retry: [message: Message]
  'jump-to-message': [messageId: string]
  'content-resized': []
}>()
const showTranscript = ref(false)
const translatedText = ref('')
const translating = ref(false)
const transcribing = ref(false)
const voicePlaying = ref(false)
const voiceUnread = ref(!props.own && props.message.read === false)
const downloadingVideo = ref(false)
const videoUrl = ref('')
const resourceUrl = ref('')
const imageLoading = ref(false)
const imageLoadFailed = ref(false)
const downloadingDocument = ref(false)
const documentDownloadProgress = ref(0)
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
let voiceTimer: number | undefined
let audio: HTMLAudioElement | undefined
let voiceObjectUrl = ''
let imageObjectUrl = ''
let removeDownloadProgressListener: (() => void) | undefined

const displayAvatar = computed(() => props.own ? appStore.currentUser?.avatar : props.message.senderAvatar)
const displayName = computed(() => props.own
  ? (appStore.currentUser?.nickname || props.message.senderName)
  : props.message.senderName)

const replyPreview = computed(() => {
  const message = props.replyMessage
  if (!message) return '原消息暂不可用'
  if (message.status === 1) return '[消息已撤回]'
  if (message.type === 'red_packet') return '[红包]'
  if (message.type === 'voice') return '[语音]'
  if (message.type === 'file') {
    if (message.fileKind === 'image') return '[图片]'
    if (message.fileKind === 'video') return '[视频]'
    return '[文件]'
  }
  return message.content || '[消息]'
})

async function openResource() {
  if (!props.message.referenceId) return
  const url = await window.chatApi.openFile(props.message.referenceId)
  resourceUrl.value = url
  return url
}

async function downloadDocument() {
  if (!props.message.referenceId || downloadingDocument.value) return
  downloadingDocument.value = true
  documentDownloadProgress.value = 0
  try {
    await window.chatApi.downloadFile(props.message.referenceId)
    documentDownloadProgress.value = 100
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件下载失败')
  } finally {
    downloadingDocument.value = false
  }
}

async function loadImage() {
  if (!props.message.referenceId || imageLoading.value || resourceUrl.value) return
  imageLoading.value = true
  imageLoadFailed.value = false
  try {
    const source = await window.chatApi.loadImage(props.message.referenceId)
    const bytes = source.bytes instanceof Uint8Array ? source.bytes : new Uint8Array(source.bytes)
    const blob = new Blob([bytes.slice().buffer], { type: source.mimeType })
    if (imageObjectUrl) URL.revokeObjectURL(imageObjectUrl)
    imageObjectUrl = URL.createObjectURL(blob)
    resourceUrl.value = imageObjectUrl
  } catch {
    imageLoadFailed.value = true
  } finally {
    imageLoading.value = false
  }
}

function handleImageLoaded() {
  imageLoadFailed.value = false
  emit('content-resized')
}

async function playVoice() {
  voiceUnread.value = false
  try {
    if (!props.message.referenceId) return
    const source = await window.chatApi.loadVoice(props.message.referenceId)
    const bytes = source.bytes instanceof Uint8Array ? source.bytes : new Uint8Array(source.bytes)
    const blob = new Blob([bytes.slice().buffer], { type: source.mimeType })
    audio?.pause()
    if (voiceObjectUrl) URL.revokeObjectURL(voiceObjectUrl)
    voiceObjectUrl = URL.createObjectURL(blob)
    audio = new Audio(voiceObjectUrl)
    voicePlaying.value = true
    audio.onended = () => { voicePlaying.value = false }
    audio.onerror = () => { voicePlaying.value = false }
    await audio.play()
  } catch (error) {
    voicePlaying.value = false
    ElMessage.error(error instanceof Error ? error.message : '语音播放失败')
  }
}

async function toggleTranscript() {
  voiceUnread.value = false
  if (showTranscript.value) { showTranscript.value = false; return }
  if (!props.message.transcript && props.message.referenceId) {
    transcribing.value = true
    try { props.message.transcript = await window.chatApi.transcribeVoice(props.message.referenceId) }
    catch (error) { ElMessage.error(error instanceof Error ? error.message : '语音识别失败') }
    finally { transcribing.value = false }
  }
  showTranscript.value = true
}

async function translate() {
  if (translatedText.value) { translatedText.value = ''; return }
  translating.value = true
  try { translatedText.value = await window.chatApi.translateText(props.message.content) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '翻译失败') }
  finally { translating.value = false }
}

function openContextMenu(event: MouseEvent) {
  if (props.message.status === 1 || props.message.id.startsWith('local:')) return
  contextMenuX.value = Math.min(event.clientX, window.innerWidth - 126)
  contextMenuY.value = Math.min(event.clientY, window.innerHeight - 126)
  contextMenuVisible.value = true
}

function closeContextMenu() {
  contextMenuVisible.value = false
}

async function runContextAction(action: 'translate' | 'recall' | 'quote') {
  closeContextMenu()
  if (action === 'translate') await translate()
  else if (action === 'recall') emit('recall', props.message)
  else emit('quote', props.message)
}

async function downloadAndPlayVideo() {
  if (videoUrl.value || downloadingVideo.value) return
  downloadingVideo.value = true
  try {
    videoUrl.value = await openResource() ?? ''
    ElMessage.success('视频下载完成，可以播放')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '视频下载失败')
  } finally {
    downloadingVideo.value = false
  }
}

onBeforeUnmount(() => {
  window.clearTimeout(voiceTimer)
  audio?.pause()
  if (voiceObjectUrl) URL.revokeObjectURL(voiceObjectUrl)
  if (imageObjectUrl) URL.revokeObjectURL(imageObjectUrl)
  removeDownloadProgressListener?.()
  document.removeEventListener('click', closeContextMenu)
  window.removeEventListener('blur', closeContextMenu)
})

onMounted(() => {
  removeDownloadProgressListener = window.chatApi.onFileDownloadProgress((progress) => {
    if (progress.resourceId !== props.message.referenceId) return
    documentDownloadProgress.value = Math.max(0, Math.min(100, progress.percent))
  })
  document.addEventListener('click', closeContextMenu)
  window.addEventListener('blur', closeContextMenu)
})

watch(
  () => [props.message.fileKind, props.message.referenceId] as const,
  ([fileKind, referenceId], previous) => {
    if (fileKind !== 'image' || !referenceId) return
    if (previous && previous[1] !== referenceId) {
      if (imageObjectUrl) URL.revokeObjectURL(imageObjectUrl)
      imageObjectUrl = ''
      resourceUrl.value = ''
      imageLoadFailed.value = false
    }
    void loadImage()
  },
  { immediate: true },
)
</script>

<template>
  <div class="message-body" :class="{ 'self-message': own }" :data-message-id="message.id">
    <AvatarDisplay :src="displayAvatar" :name="displayName" :size="35" class="message-avatar" />
    <div class="message-content" @contextmenu.prevent.stop="openContextMenu">
      <div v-if="!own" class="nickname">{{ message.senderName }}</div>
      <div v-if="message.status === 1" class="text-msg recalled-message">该消息已撤回</div>
      <template v-else-if="message.type === 'text'">
        <div class="text-msg">{{ message.content }}</div>
        <div v-if="translating" class="translation-loading">翻译中…</div>
        <div v-if="translatedText" class="voice-transcript">{{ translatedText }}</div>
        <button v-if="message.replyMessageId" class="quoted-message" title="跳转到原消息" @click.stop="emit('jump-to-message', message.replyMessageId)">
          <strong>{{ replySenderName || replyMessage?.senderName || '引用消息' }}:</strong>
          <span>{{ replyPreview }}</span>
        </button>
      </template>

      <template v-else-if="message.type === 'file'">
        <div v-if="message.fileKind === 'image'" class="img-msg">
          <img v-if="resourceUrl" :src="resourceUrl" :alt="message.fileName || '聊天图片'" class="image-content" @load="handleImageLoaded" @error="imageLoadFailed = true; resourceUrl = ''" />
          <span v-else-if="imageLoadFailed" class="image-placeholder failed" @click="loadImage">图片加载失败，点击重试</span>
          <span v-else class="image-placeholder">图片加载中…</span>
        </div>
        <div v-else-if="message.fileKind === 'video'" class="video-msg">
          <video v-if="videoUrl" :src="videoUrl" class="video-player" controls autoplay muted />
          <button v-else class="video-preview" :disabled="downloadingVideo" @click="downloadAndPlayVideo">
            <img :src="message.thumbnail" :alt="message.fileName || '视频预览'" />
            <span class="video-play"><el-icon><VideoPlay /></el-icon></span>
            <span v-if="downloadingVideo" class="video-downloading">正在下载…</span>
          </button>
          <span class="video-meta">{{ message.fileName }} · {{ message.fileSize }}</span>
        </div>
        <button v-else class="file-msg" :disabled="downloadingDocument" @click="downloadDocument">
          <span class="file-copy">
            <strong>{{ message.fileName }}</strong>
            <small>{{ downloadingDocument ? `正在下载 ${documentDownloadProgress}%` : message.fileSize }}</small>
            <span v-if="downloadingDocument" class="file-download-track"><i :style="{ width: `${documentDownloadProgress}%` }" /></span>
          </span>
          <el-icon :size="34"><Document /></el-icon>
        </button>
      </template>

      <button v-else-if="message.type === 'red_packet'" class="money-msg" @click="message.referenceId && window.chatApi.claimRedPacket(message.referenceId).then((result) => ElMessage.success(result.alreadyReceived ? `已领取 ¥${result.amount}` : `领取成功 ¥${result.amount}`)).catch((error) => ElMessage.error(error instanceof Error ? error.message : '领取失败'))"><el-icon :size="32"><Money /></el-icon><div class="money-details"><strong>¥ {{ message.amount || '--' }}</strong><span>{{ message.content || '红包' }}</span></div></button>

      <template v-else-if="message.type === 'voice'">
        <div class="voice-line">
          <button class="voice-msg" :class="{ playing: voicePlaying }" @click="playVoice">
            <svg class="voice-wave-icon" viewBox="0 0 28 28" aria-hidden="true"><path d="M5 11.5a4 4 0 010 5M10 8a8.5 8.5 0 010 12M15 4.5a13 13 0 010 19" /></svg>
            <span>{{ message.duration }}&quot;</span>
          </button>
          <i v-if="voiceUnread" class="voice-unread" />
          <button class="voice-to-text" :disabled="transcribing" @click.stop="toggleTranscript">{{ transcribing ? '识别中…' : showTranscript ? '收起' : '转文字' }}</button>
        </div>
        <div v-if="showTranscript" class="voice-transcript">{{ message.transcript || '暂时无法识别该语音消息' }}</div>
      </template>
    </div>
    <span v-if="own && message.sendStatus === 'sending'" class="message-send-state sending" title="发送中"><el-icon><Loading /></el-icon></span>
    <button v-else-if="own && message.sendStatus === 'failed'" class="message-send-state failed" title="发送失败，点击重试" @click="emit('retry', message)"><el-icon><WarningFilled /></el-icon></button>
    <Teleport to="body">
      <div v-if="contextMenuVisible" class="message-context-menu no-drag" :style="{ left: `${contextMenuX}px`, top: `${contextMenuY}px` }" @click.stop @contextmenu.prevent>
        <button v-if="message.type === 'text'" :disabled="translating" @click="runContextAction('translate')">{{ translatedText ? '收起翻译' : '翻译' }}</button>
        <button v-if="canRecall" @click="runContextAction('recall')">撤回</button>
        <button @click="runContextAction('quote')">引用</button>
      </div>
    </Teleport>
  </div>
</template>
