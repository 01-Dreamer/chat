import type { CallRecord } from '../types'
import { apiClient } from './apiClient'
import { realtimeService } from './realtimeService'

class CallService {
  create(calleeId: string, callType: number) {
    return apiClient.post<CallRecord>('/calls', { calleeId, callType })
  }

  update(callId: string, action: 'accept' | 'complete' | 'reject' | 'missed' | 'cancel') {
    return apiClient.patch<CallRecord>(`/calls/${callId}`, { action })
  }

  iceServers() {
    return apiClient.get<{ iceServers: RTCIceServer[] }>('/calls/ice-servers')
  }

  signal(callId: string, targetUserId: string, signalType: 'offer' | 'answer' | 'ice' | 'hangup', payload: unknown) {
    realtimeService.sendSignal({ callId, targetUserId, signalType, payload })
  }
}

export const callService = new CallService()
