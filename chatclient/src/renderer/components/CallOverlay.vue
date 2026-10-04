<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useCallStore } from '../stores/call'

const appStore = useAppStore()
const callStore = useCallStore()
const localVideo = ref<HTMLVideoElement | null>(null)
const remoteVideo = ref<HTMLVideoElement | null>(null)
const remoteAudio = ref<HTMLAudioElement | null>(null)
const active = ref(false)
const busy = ref(false)
const mediaNotice = ref('')
let peer: RTCPeerConnection | null = null
let localStream: MediaStream | null = null
let ringTimeout: number | undefined
let statePollTimer: number | undefined
let processingSignals = false
let pollingState = false
let offerSent = false
const pendingIceCandidates: RTCIceCandidateInit[] = []

const otherUserId = computed(() => {
  const call = callStore.current
  if (!call || !appStore.currentUser) return ''
  return call.callerId === appStore.currentUser.id ? call.calleeId : call.callerId
})

function mediaErrorMessage(error: unknown) {
  const detail = error instanceof Error ? `${error.name} ${error.message}` : ''
  if (/NotAllowed|PermissionDenied|denied permission/i.test(detail)) return '未获得摄像头或麦克风权限'
  if (/NotFound|DevicesNotFound|Requested device not found/i.test(detail)) return '未找到可用的摄像头或麦克风'
  if (/NotReadable|TrackStart|Could not start|source|AbortError/i.test(detail)) return '摄像头或麦克风正被其他程序占用'
  if (/Overconstrained|ConstraintNotSatisfied/i.test(detail)) return '当前设备不支持所需的音视频参数'
  return '无法打开摄像头或麦克风'
}

function callErrorMessage(error: unknown, fallback: string) {
  const detail = error instanceof Error ? `${error.name} ${error.message}` : ''
  if (/NotAllowed|PermissionDenied|NotFound|DevicesNotFound|NotReadable|TrackStart|Could not start|source|AbortError|Overconstrained|ConstraintNotSatisfied/i.test(detail)) {
    return mediaErrorMessage(error)
  }
  return fallback
}

async function acquireLocalMedia(callType: number) {
  mediaNotice.value = ''
  if (callType === 1) {
    try {
      return await navigator.mediaDevices.getUserMedia({ audio: true, video: true })
    } catch (videoError) {
      try {
        const audioStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
        mediaNotice.value = '摄像头不可用，已切换为语音并继续连接'
        ElMessage.warning(`${mediaErrorMessage(videoError)}，已切换为语音`)
        return audioStream
      } catch {
        mediaNotice.value = '本机摄像头和麦克风不可用，只能接收对方音视频'
        ElMessage.warning(`${mediaErrorMessage(videoError)}，将以仅接收模式继续连接`)
        return new MediaStream()
      }
    }
  }
  try {
    return await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
  } catch (error) {
    mediaNotice.value = '本机麦克风不可用，只能接收对方语音'
    ElMessage.warning(`${mediaErrorMessage(error)}，将以仅接收模式继续连接`)
    return new MediaStream()
  }
}

async function ensurePeer() {
  if (peer) return peer
  const config = await window.chatApi.getIceServers()
  peer = new RTCPeerConnection(config)
  peer.onicecandidate = (event) => {
    if (event.candidate && callStore.current) void window.chatApi.sendCallSignal(callStore.current.id, otherUserId.value, 'ice', event.candidate.toJSON())
  }
  peer.ontrack = (event) => {
    const stream = event.streams[0]
    if (callStore.current?.callType === 1 && remoteVideo.value) remoteVideo.value.srcObject = stream
    else if (remoteAudio.value) remoteAudio.value.srcObject = stream
  }
  const callType = callStore.current?.callType ?? 0
  localStream = await acquireLocalMedia(callType)
  localStream.getTracks().forEach((track) => peer?.addTrack(track, localStream!))
  if (!localStream.getAudioTracks().length) peer.addTransceiver('audio', { direction: 'recvonly' })
  if (callType === 1 && !localStream.getVideoTracks().length) peer.addTransceiver('video', { direction: 'recvonly' })
  await nextTick()
  if (localVideo.value && localStream.getVideoTracks().length) localVideo.value.srcObject = localStream
  active.value = true
  return peer
}

async function accept() {
  if (!callStore.current) return
  busy.value = true
  try {
    await ensurePeer()
    callStore.current = await window.chatApi.updateCall(callStore.current.id, 'accept')
    callStore.incoming = false
    window.clearTimeout(ringTimeout)
  } catch (error) {
    ElMessage.error(callErrorMessage(error, '无法接听通话，请稍后重试'))
    closeMedia()
  } finally { busy.value = false }
}

async function reject() {
  if (!callStore.current) return
  try { await window.chatApi.updateCall(callStore.current.id, 'reject') } finally { window.clearTimeout(ringTimeout); closeMedia(); callStore.clear() }
}

async function hangup() {
  const call = callStore.current
  if (!call) return
  try {
    await window.chatApi.sendCallSignal(call.id, otherUserId.value, 'hangup', {})
    await window.chatApi.updateCall(call.id, call.startTime ? 'complete' : 'cancel')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '挂断失败')
  } finally { window.clearTimeout(ringTimeout); closeMedia(); callStore.clear() }
}

async function beginOffer() {
  const call = callStore.current
  if (!call || call.callerId !== appStore.currentUser?.id || !call.startTime || offerSent) return
  offerSent = true
  try {
    const connection = await ensurePeer()
    const offer = await connection.createOffer()
    await connection.setLocalDescription(offer)
    await window.chatApi.sendCallSignal(call.id, otherUserId.value, 'offer', offer)
  } catch (error) {
    offerSent = false
    throw error
  }
}

async function flushPendingIce(connection: RTCPeerConnection) {
  if (!connection.remoteDescription) return
  while (pendingIceCandidates.length) await connection.addIceCandidate(pendingIceCandidates.shift()!)
}

async function handleSignal(value: import('../stores/call').CallSignal) {
  if (value.callId !== callStore.current?.id) return
  if (value.signalType === 'hangup') { closeMedia(); callStore.clear(); return }
  const connection = await ensurePeer()
  if (value.signalType === 'offer') {
    await connection.setRemoteDescription(value.payload as RTCSessionDescriptionInit)
    await flushPendingIce(connection)
    const answer = await connection.createAnswer()
    await connection.setLocalDescription(answer)
    await window.chatApi.sendCallSignal(value.callId, value.fromUserId, 'answer', answer)
  } else if (value.signalType === 'answer') {
    await connection.setRemoteDescription(value.payload as RTCSessionDescriptionInit)
    await flushPendingIce(connection)
  } else if (value.signalType === 'ice') {
    const candidate = value.payload as RTCIceCandidateInit
    if (connection.remoteDescription) await connection.addIceCandidate(candidate)
    else pendingIceCandidates.push(candidate)
  }
}

async function drainSignals() {
  if (processingSignals) return
  processingSignals = true
  try {
    while (callStore.current) {
      const signals = callStore.takeSignals(callStore.current.id)
      if (!signals.length) break
      for (const signal of signals) await handleSignal(signal)
    }
  } finally {
    processingSignals = false
  }
}

function closeMedia() {
  peer?.close()
  peer = null
  localStream?.getTracks().forEach((track) => track.stop())
  localStream = null
  pendingIceCandidates.splice(0)
  active.value = false
  offerSent = false
  mediaNotice.value = ''
}

async function refreshCallState() {
  const call = callStore.current
  if (!call || pollingState) return
  pollingState = true
  try {
    const latest = await window.chatApi.getCall(call.id)
    if (callStore.current?.id === latest.id) callStore.current = latest
  } catch {
    // WebSocket remains the primary path; polling only recovers a missed status event.
  } finally {
    pollingState = false
  }
}

function startStatePolling() {
  window.clearInterval(statePollTimer)
  statePollTimer = undefined
  if (!callStore.current) return
  void refreshCallState()
  statePollTimer = window.setInterval(() => void refreshCallState(), 1_000)
}

function scheduleRingTimeout() {
  window.clearTimeout(ringTimeout)
  const call = callStore.current
  if (!call || call.startTime || call.status !== 0) return
  const delay = Math.max(0, call.createdTime + 60_000 - Date.now())
  ringTimeout = window.setTimeout(async () => {
    const current = callStore.current
    if (!current || current.id !== call.id || current.startTime || current.status !== 0) return
    try { await window.chatApi.updateCall(current.id, 'missed') } catch { /* The other participant may have timed it out first. */ }
    finally { closeMedia(); callStore.clear() }
  }, delay)
}

watch(() => callStore.current?.id, scheduleRingTimeout, { immediate: true })
watch(() => callStore.current?.id, startStatePolling, { immediate: true })
watch(() => callStore.current?.startTime, () => { void beginOffer().catch((error) => ElMessage.error(callErrorMessage(error, '建立通话失败，请稍后重试'))) })
watch(() => callStore.current?.status, (status) => {
  if (status != null && status !== 0) {
    window.clearInterval(statePollTimer)
    statePollTimer = undefined
    closeMedia()
    callStore.clear()
  }
})
watch(() => callStore.signalVersion, () => { void drainSignals().catch((error) => ElMessage.error(callErrorMessage(error, '通话连接失败，请稍后重试'))) })
onBeforeUnmount(() => { window.clearTimeout(ringTimeout); window.clearInterval(statePollTimer); closeMedia() })
</script>

<template>
  <div v-if="callStore.current" class="call-overlay no-drag">
    <div class="call-card">
      <h3>{{ callStore.current.callType === 1 ? '视频通话' : '语音通话' }}</h3>
      <p>{{ callStore.incoming ? '收到好友呼叫' : active ? '通话中' : '正在等待对方接听…' }}</p>
      <audio ref="remoteAudio" autoplay />
      <div v-if="callStore.current.callType === 1" class="call-videos">
        <video ref="remoteVideo" autoplay playsinline />
        <video ref="localVideo" autoplay muted playsinline />
        <span v-if="mediaNotice" class="call-media-notice">{{ mediaNotice }}</span>
      </div>
      <p v-else-if="mediaNotice" class="call-media-notice call-media-notice-audio">{{ mediaNotice }}</p>
      <div class="call-actions">
        <el-button v-if="callStore.incoming" type="success" :loading="busy" @click="accept">接听</el-button>
        <el-button v-if="callStore.incoming" type="danger" @click="reject">拒绝</el-button>
        <el-button v-else type="danger" @click="hangup">挂断</el-button>
      </div>
    </div>
  </div>
</template>
