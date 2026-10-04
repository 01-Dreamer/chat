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
  const signals = ref<CallSignal[]>([])
  const signalVersion = ref(0)

  async function start(calleeId: string, callType: number) {
    if (current.value) throw new Error('当前已有正在进行的通话')
    current.value = await window.chatApi.createCall(calleeId, callType)
    incoming.value = false
  }
  function receive(eventType: string, data: unknown) {
    if (eventType === 'CALL_INVITE') {
      const call = data as CallRecord
      if (!current.value || current.value.id === call.id) {
        current.value = call
        incoming.value = true
      }
    } else if (eventType === 'CALL_STATUS') {
      const call = data as CallRecord
      if (current.value?.id === call.id) current.value = call
    } else if (eventType === 'WEBRTC_SIGNAL') {
      signals.value.push(data as CallSignal)
      signalVersion.value++
    }
  }
  function takeSignals(callId: string) {
    const matching = signals.value.filter((item) => item.callId === callId)
    signals.value = signals.value.filter((item) => item.callId !== callId)
    return matching
  }
  function clear() {
    current.value = null
    incoming.value = false
    signals.value = []
  }
  return { current, incoming, signalVersion, start, receive, takeSignals, clear }
})
