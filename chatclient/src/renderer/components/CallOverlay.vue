<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '../stores/app'
import { useCallStore } from '../stores/call'

const appStore = useAppStore()
const callStore = useCallStore()
const localVideo = ref<HTMLVideoElement | null>(null)
const remoteVideo = ref<HTMLVideoElement | null>(null)
const active = ref(false)
const busy = ref(false)
let peer: RTCPeerConnection | null = null
let localStream: MediaStream | null = null

const otherUserId = computed(() => {
  const call = callStore.current
  if (!call || !appStore.currentUser) return ''
  return call.callerId === appStore.currentUser.id ? call.calleeId : call.callerId
})

async function ensurePeer() {
  if (peer) return peer
  const config = await window.chatApi.getIceServers()
  peer = new RTCPeerConnection(config)
  peer.onicecandidate = (event) => {
    if (event.candidate && callStore.current) void window.chatApi.sendCallSignal(callStore.current.id, otherUserId.value, 'ice', event.candidate.toJSON())
  }
  peer.ontrack = (event) => { if (remoteVideo.value) remoteVideo.value.srcObject = event.streams[0] }
  localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: callStore.current?.callType === 1 })
  localStream.getTracks().forEach((track) => peer?.addTrack(track, localStream!))
  await nextTick()
  if (localVideo.value) localVideo.value.srcObject = localStream
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
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法接听通话')
    closeMedia()
  } finally { busy.value = false }
}

async function reject() {
  if (!callStore.current) return
  try { await window.chatApi.updateCall(callStore.current.id, 'reject') } finally { closeMedia(); callStore.clear() }
}

async function hangup() {
  const call = callStore.current
  if (!call) return
  try {
    await window.chatApi.sendCallSignal(call.id, otherUserId.value, 'hangup', {})
    await window.chatApi.updateCall(call.id, call.startTime ? 'complete' : 'cancel')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '挂断失败')
  } finally { closeMedia(); callStore.clear() }
}

async function beginOffer() {
  const call = callStore.current
  if (!call || call.callerId !== appStore.currentUser?.id || !call.startTime || active.value) return
  const connection = await ensurePeer()
  const offer = await connection.createOffer()
  await connection.setLocalDescription(offer)
  await window.chatApi.sendCallSignal(call.id, otherUserId.value, 'offer', offer)
}

async function handleSignal() {
  const value = callStore.signal
  if (!value || value.callId !== callStore.current?.id) return
  if (value.signalType === 'hangup') { closeMedia(); callStore.clear(); return }
  const connection = await ensurePeer()
  if (value.signalType === 'offer') {
    await connection.setRemoteDescription(value.payload as RTCSessionDescriptionInit)
    const answer = await connection.createAnswer()
    await connection.setLocalDescription(answer)
    await window.chatApi.sendCallSignal(value.callId, value.fromUserId, 'answer', answer)
  } else if (value.signalType === 'answer') {
    await connection.setRemoteDescription(value.payload as RTCSessionDescriptionInit)
  } else if (value.signalType === 'ice') {
    await connection.addIceCandidate(value.payload as RTCIceCandidateInit)
  }
}

function closeMedia() {
  peer?.close()
  peer = null
  localStream?.getTracks().forEach((track) => track.stop())
  localStream = null
  active.value = false
}

watch(() => callStore.current?.startTime, () => { void beginOffer().catch((error) => ElMessage.error(error instanceof Error ? error.message : '建立通话失败')) })
watch(() => callStore.current?.status, (status) => { if (status != null && status !== 0) { closeMedia(); callStore.clear() } })
watch(() => callStore.signalVersion, () => { void handleSignal().catch((error) => ElMessage.error(error instanceof Error ? error.message : '通话信令处理失败')) })
onBeforeUnmount(closeMedia)
</script>

<template>
  <div v-if="callStore.current" class="call-overlay no-drag">
    <div class="call-card">
      <h3>{{ callStore.current.callType === 1 ? '视频通话' : '语音通话' }}</h3>
      <p>{{ callStore.incoming ? '收到好友呼叫' : active ? '通话中' : '正在等待对方接听…' }}</p>
      <div v-if="callStore.current.callType === 1" class="call-videos"><video ref="remoteVideo" autoplay playsinline /><video ref="localVideo" autoplay muted playsinline /></div>
      <div class="call-actions">
        <el-button v-if="callStore.incoming" type="success" :loading="busy" @click="accept">接听</el-button>
        <el-button v-if="callStore.incoming" type="danger" @click="reject">拒绝</el-button>
        <el-button v-else type="danger" @click="hangup">挂断</el-button>
      </div>
    </div>
  </div>
</template>
