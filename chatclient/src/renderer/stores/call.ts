import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { CallRecord } from '../types'

export interface CallSignal {
  callId: string
  fromUserId: string
  signalType: 'offer' | 'answer' | 'ice' | 'hangup'
  payload: unknown
}

export const useCallStore = defineStore('call', () => {
  const current = ref<CallRecord | null>(null)
  const incoming = ref(false)
  const signal = ref<CallSignal | null>(null)
  const signalVersion = ref(0)

  async function start(calleeId: string, callType: number) {
    current.value = await window.chatApi.createCall(calleeId, callType)
    incoming.value = false
  }
  function receive(eventType: string, data: unknown) {
    if (eventType === 'CALL_INVITE') {
      if (!current.value) {
        current.value = data as CallRecord
        incoming.value = true
      }
    } else if (eventType === 'CALL_STATUS') {
      const call = data as CallRecord
      if (current.value?.id === call.id) current.value = call
    } else if (eventType === 'WEBRTC_SIGNAL') {
      signal.value = data as CallSignal
      signalVersion.value++
    }
  }
  function clear() {
    current.value = null
    incoming.value = false
    signal.value = null
  }
  return { current, incoming, signal, signalVersion, start, receive, clear }
})
