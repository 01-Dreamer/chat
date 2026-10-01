<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { Document, Money, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { Message } from '../types'

const props = defineProps<{ message: Message; own: boolean }>()
const showTranscript = ref(false)
const voicePlaying = ref(false)
const voiceUnread = ref(!props.own && props.message.read === false)
const downloadingVideo = ref(false)
const videoUrl = ref('')
let voiceTimer: number | undefined

function downloadDocument() {
  if (!props.message.content) return
  const anchor = document.createElement('a')
  anchor.href = props.message.content
  anchor.download = props.message.fileName ?? '下载文件'
  anchor.click()
  ElMessage.success('文件已开始下载')
}

function playVoice() {
  voiceUnread.value = false
  voicePlaying.value = true
  window.clearTimeout(voiceTimer)
  voiceTimer = window.setTimeout(() => { voicePlaying.value = false }, (props.message.duration ?? 1) * 1000)
}

function toggleTranscript() {
  voiceUnread.value = false
  showTranscript.value = !showTranscript.value
}

async function createMockVideo() {
  const canvas = document.createElement('canvas')
  canvas.width = 640
  canvas.height = 360
  const context = canvas.getContext('2d')
  if (!context || typeof canvas.captureStream !== 'function' || typeof MediaRecorder === 'undefined') throw new Error('当前环境不支持视频预览')

  const stream = canvas.captureStream(24)
  const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp8') ? 'video/webm;codecs=vp8' : 'video/webm'
  const recorder = new MediaRecorder(stream, { mimeType })
  const chunks: BlobPart[] = []
  recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data) }
  const completed = new Promise<Blob>((resolve) => {
    recorder.onstop = () => resolve(new Blob(chunks, { type: mimeType }))
  })

  recorder.start()
  const startedAt = performance.now()
  await new Promise<void>((resolve) => {
    const draw = (time: number) => {
      const progress = Math.min((time - startedAt) / 2200, 1)
      const gradient = context.createLinearGradient(0, 0, canvas.width, canvas.height)
      gradient.addColorStop(0, '#415b78')
      gradient.addColorStop(1, '#172333')
      context.fillStyle = gradient
      context.fillRect(0, 0, canvas.width, canvas.height)
      context.fillStyle = 'rgba(7, 193, 96, .75)'
      context.beginPath()
      context.arc(110 + progress * 420, 180, 42, 0, Math.PI * 2)
      context.fill()
      context.fillStyle = '#ffffff'
      context.font = '30px sans-serif'
      context.fillText('ChatClient Mock Video', 155, 172)
      context.font = '18px sans-serif'
      context.fillStyle = 'rgba(255,255,255,.72)'
      context.fillText('周会演示视频预览', 225, 210)
      if (progress < 1) requestAnimationFrame(draw)
      else resolve()
    }
    requestAnimationFrame(draw)
  })
  recorder.stop()
  const blob = await completed
  stream.getTracks().forEach((track) => track.stop())
  return blob
}

async function downloadAndPlayVideo() {
  if (videoUrl.value || downloadingVideo.value) return
  downloadingVideo.value = true
  try {
    const blob = await createMockVideo()
    videoUrl.value = URL.createObjectURL(blob)
    ElMessage.success('视频下载完成，可以播放')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '视频下载失败')
  } finally {
    downloadingVideo.value = false
  }
}

onBeforeUnmount(() => {
  window.clearTimeout(voiceTimer)
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
})
</script>

<template>
  <div class="message-body" :class="{ 'self-message': own }">
    <img :src="message.senderAvatar" alt="avatar" class="message-avatar" />
    <div class="message-content">
      <div v-if="!own" class="nickname">{{ message.senderName }}</div>
      <div v-if="message.type === 'text'" class="text-msg">{{ message.content }}</div>

      <template v-else-if="message.type === 'file'">
        <div v-if="message.fileKind === 'image'" class="img-msg">
          <img :src="message.content" :alt="message.fileName || '聊天图片'" class="image-content" />
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

      <div v-else-if="message.type === 'red_packet'" class="money-msg"><el-icon :size="32"><Money /></el-icon><div class="money-details"><strong>¥ {{ message.amount?.toFixed(2) }}</strong><span>{{ message.content || '红包' }}</span></div></div>

      <template v-else-if="message.type === 'voice'">
        <div class="voice-line">
          <button class="voice-msg" :class="{ playing: voicePlaying }" @click="playVoice">
            <svg class="voice-wave-icon" viewBox="0 0 28 28" aria-hidden="true"><path d="M5 11.5a4 4 0 010 5M10 8a8.5 8.5 0 010 12M15 4.5a13 13 0 010 19" /></svg>
            <span>{{ message.duration }}&quot;</span>
          </button>
          <i v-if="voiceUnread" class="voice-unread" />
          <button class="voice-to-text" @click.stop="toggleTranscript">{{ showTranscript ? '收起' : '转文字' }}</button>
        </div>
        <div v-if="showTranscript" class="voice-transcript">{{ message.transcript || '暂时无法识别该语音消息' }}</div>
      </template>
    </div>
  </div>
</template>
