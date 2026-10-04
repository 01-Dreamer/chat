import type { BootstrapData, CallRecord, Conversation, Friend, FriendRequest, GroupActionResult, GroupChat, GroupMember, Message, MessagePage, MessagePageCursor, OpenConversationResult, PendingAttachment, ProfilePatch, User, WalletAccount } from '../main/types'

declare global {
  interface Window {
    chatApi: {
      login(username: string, password: string): Promise<User>
      register(nickname: string, username: string, password: string): Promise<User>
      logout(): Promise<boolean>
      bootstrap(): Promise<BootstrapData>
      loadLocalChatState(): Promise<{ conversations: Conversation[], messages: Record<string, Message[]> }>
      loadConversationMessages(chatKey: string, cursor?: MessagePageCursor | null, pageSize?: number): Promise<MessagePage>
      setSessionPinned(chatKey: string, pinned: boolean): Promise<Conversation[]>
      markSessionRead(chatKey: string): Promise<void>
      setActiveSession(chatKey: string | null): Promise<void>
      hideSession(chatKey: string): Promise<void>
      sendMessage(message: Omit<Message, 'id' | 'createdAt'>): Promise<Message>
      selectAttachments(): Promise<PendingAttachment[]>
      stageDroppedFiles(files: File[]): Promise<PendingAttachment[]>
      captureScreen(): Promise<PendingAttachment | null>
      sendAttachment(conversationId: string, attachment: PendingAttachment): Promise<Message>
      sendVoice(conversationId: string, bytes: Uint8Array, mimeType: string, duration: number): Promise<Message>
      openFile(resourceId: string): Promise<string>
      loadVoice(resourceId: string): Promise<{ bytes: Uint8Array, mimeType: string }>
      loadImage(resourceId: string): Promise<{ bytes: Uint8Array, mimeType: string }>
      downloadFile(resourceId: string): Promise<boolean>
      onFileDownloadProgress(callback: (value: { resourceId: string, received: number, total: number, percent: number }) => void): () => void
      openConversation(type: 'direct' | 'group', targetId: string): Promise<OpenConversationResult>
      recallMessage(messageId: string): Promise<Message>
      retryMessage(clientMessageId: string): Promise<Message>
      updateProfile(patch: ProfilePatch): Promise<User>
      updateAvatar(): Promise<User | null>
      resetPayPassword(oldPassword: string, newPassword: string): Promise<WalletAccount>
      getWallet(): Promise<WalletAccount>
      sendRedPacket(conversationId: string, input: { chatType: number, targetId: string, packetType: number, totalAmount: string, totalCount: number, message: string, payPassword: string }): Promise<Message>
      claimRedPacket(packetId: string): Promise<{ amount: string, alreadyReceived: boolean, expired: boolean }>
      translateText(value: string, targetLanguage?: string): Promise<string>
      smartReplies(messages: Array<{ role: 'user' | 'assistant', content: string }>): Promise<string[]>
      transcribeVoice(resourceId: string): Promise<string>
      createCall(calleeId: string, callType: number): Promise<CallRecord>
      updateCall(callId: string, action: 'accept' | 'complete' | 'reject' | 'missed' | 'cancel'): Promise<CallRecord>
      getCall(callId: string): Promise<CallRecord>
      getIceServers(): Promise<{ iceServers: RTCIceServer[] }>
      sendCallSignal(callId: string, targetUserId: string, signalType: 'offer' | 'answer' | 'ice' | 'hangup', payload: unknown): Promise<void>
      acceptFriendRequest(id: string, type: 'friend' | 'group'): Promise<FriendRequest>
      rejectFriendRequest(id: string, type: 'friend' | 'group'): Promise<FriendRequest>
      updateContactRemark(kind: 'friend' | 'group', id: string, remark: string): Promise<Friend | GroupChat>
      searchFriendByUsername(username: string): Promise<Friend>
      applyAddFriend(username: string, reason: string): Promise<FriendRequest>
      listFriends(): Promise<Friend[]>
      listContactRequests(): Promise<FriendRequest[]>
      deleteFriend(id: string): Promise<void>
      searchGroupByNumber(groupNumber: string): Promise<GroupChat>
      applyJoinGroup(groupNumber: string, reason: string): Promise<FriendRequest>
      listGroups(): Promise<GroupChat[]>
      listGroupMembers(groupId: string): Promise<GroupMember[]>
      updateGroupProfile(groupId: string, name: string): Promise<GroupChat>
      updateGroupAvatar(groupId: string): Promise<GroupChat | null>
      updateMyGroupNickname(groupId: string, value: string): Promise<GroupMember>
      updateGroupRole(groupId: string, userId: string, role: number): Promise<GroupMember>
      kickGroupMember(groupId: string, userId: string): Promise<GroupMember[]>
      leaveGroup(groupId: string): Promise<void>
      dissolveGroup(groupId: string): Promise<void>
      createGroup(name: string): Promise<GroupActionResult>
      refreshContacts(): Promise<{ friends: Friend[], friendRequests: FriendRequest[], groups: GroupChat[], conversations: Conversation[] }>
      markNotificationRead(id: string): Promise<unknown>
      listNotifications(): Promise<import('../main/types').AppNotification[]>
      copyText(value: string): Promise<boolean>
      setWindowMode(mode: 'login' | 'register' | 'main'): void
      minimizeWindow(): Promise<boolean>
      toggleMaximize(): Promise<boolean>
      closeWindow(): Promise<boolean>
      isWindowMaximized(): Promise<boolean>
      onWindowMaximizedChanged(callback: (value: boolean) => void): () => void
      onRealtimeEvent(callback: (value: unknown) => void): () => void
    }
  }
}

export {}
