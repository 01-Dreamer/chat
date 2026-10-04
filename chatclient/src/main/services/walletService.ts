import { randomUUID } from 'node:crypto'
import type { RedPacket, TransferResult, WalletAccount } from '../types'
import { apiClient } from './apiClient'

class WalletService {
  account() {
    return apiClient.get<WalletAccount>('/wallet')
  }

  setPayPassword(oldPassword: string, newPassword: string) {
    return apiClient.patch<WalletAccount>('/wallet/pay-password', { oldPassword: oldPassword || null, newPassword })
  }

  transfer(recipientUserId: string, amount: string, payPassword: string) {
    return apiClient.post<TransferResult>('/wallet/transfers', {
      clientTransactionId: randomUUID(), recipientUserId, amount, payPassword,
    })
  }

  createRedPacket(input: {
    chatType: number
    targetId: string
    packetType: number
    totalAmount: string
    totalCount: number
    message: string
    payPassword: string
  }) {
    return apiClient.post<RedPacket>('/red-packets', input)
  }

  getRedPacket(packetId: string) {
    return apiClient.get<RedPacket>(`/red-packets/${packetId}`)
  }

  claim(packetId: string) {
    return apiClient.post<{ redPacket: RedPacket, amount: string, alreadyReceived: boolean, expired: boolean }>(`/red-packets/${packetId}/claim`)
  }
}

export const walletService = new WalletService()
