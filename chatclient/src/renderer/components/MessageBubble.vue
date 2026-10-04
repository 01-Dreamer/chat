<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Document, Money, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { Message } from '../types'
import AvatarDisplay from './AvatarDisplay.vue'

const props = defineProps<{ message: Message; own: boolean; canRecall: boolean; replyMessage?: Message }>()
const emit = defineEmits<{ recall: [message: Message]; quote: [message: Message] }>()
const showTranscript = ref(false)
const translatedText = ref('')
const translating = ref(false)
const transcribing = ref(false)
const voicePlaying = ref(false)
const voiceUnread = ref(!props.own && props.message.read === false)
const downloadingVideo = ref(false)
const videoUrl = ref('')
const resourceUrl = ref('')
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
let voiceTimer: number | undefined
let audio: HTMLAudioElement | undefined

async function openResource() {
  if (!props.message.referenceId) return
  const url = await window.chatApi.openFile(props.message.referenceId)
  resourceUrl.value = url
  return url
}

async function downloadDocument() {
  const url = await openResource()
  if (!url) return
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = props.message.fileName ?? '下载文件'
  anchor.click()
  ElMessage.success('文件已开始下载')
}

async function playVoice() {
  voiceUnread.value = false
  try {
    const url = await openResource()
    if (!url) return
    audio?.pause()
    audio = new Audio(url)
    voicePlaying.value = true
    audio.onended = () => { voicePlaying.value = false }
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
  document.removeEventListener('click', closeContextMenu)
  window.removeEventListener('blur', closeContextMenu)
})

onMounted(() => {
  if (props.message.fileKind === 'image') void openResource().catch(() => undefined)
  document.addEventListener('click', closeContextMenu)
  window.addEventListener('blur', closeContextMenu)
})
</script>

<template>
  <div class="message-body" :class="{ 'self-message': own }">
    <AvatarDisplay :src="message.senderAvatar" :name="message.senderName" :size="35" class="message-avatar" />
    <div class="message-content" @contextmenu.prevent.stop="openContextMenu">
      <div v-if="!own" class="nickname">{{ message.senderName }}</div>
      <div v-if="message.status === 1" class="text-msg recalled-message">该消息已撤回</div>
      <template v-else-if="message.type === 'text'">
        <div v-if="message.replyMessageId" class="quoted-message"><strong>{{ replyMessage?.senderName || '引用消息' }}</strong><span>{{ replyMessage?.content || '原消息暂不可用' }}</span></div>
        <div class="text-msg">{{ message.content }}</div>
        <div v-if="translating" class="translation-loading">翻译中…</div>
        <div v-if="translatedText" class="voice-transcript">{{ translatedText }}</div>
      </template>

      <template v-else-if="message.type === 'file'">
        <div v-if="message.fileKind === 'image'" class="img-msg">
          <img :src="resourceUrl" :alt="message.fileName || '聊天图片'" class="image-content" />
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
        <button v-else class="file-msg" @click="downloadDocument">
          <span class="file-copy"><strong>{{ message.fileName }}</strong><small>{{ message.fileSize }}</small></span>
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
    <Teleport to="body">
      <div v-if="contextMenuVisible" class="message-context-menu no-drag" :style="{ left: `${contextMenuX}px`, top: `${contextMenuY}px` }" @click.stop @contextmenu.prevent>
        <button v-if="message.type === 'text'" :disabled="translating" @click="runContextAction('translate')">{{ translatedText ? '收起翻译' : '翻译' }}</button>
        <button v-if="canRecall" @click="runContextAction('recall')">撤回</button>
        <button @click="runContextAction('quote')">引用</button>
      </div>
    </Teleport>
  </div>
</template>
