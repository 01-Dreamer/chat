import type { BootstrapData, Friend, FriendRequest, GroupActionResult, GroupChat, Message, ProfilePatch, User } from '../main/types'

declare global {
  interface Window {
    chatApi: {
      login(username: string, password: string): Promise<User>
      register(nickname: string, username: string, password: string): Promise<User>
      bootstrap(): Promise<BootstrapData>
      sendMessage(message: Omit<Message, 'id' | 'createdAt'>): Promise<Message>
      updateProfile(patch: ProfilePatch): Promise<User>
      resetTransferPassword(oldPassword: string, newPassword: string): Promise<boolean>
      acceptFriendRequest(id: string): Promise<FriendRequest>
      rejectFriendRequest(id: string): Promise<FriendRequest>
      updateContactRemark(kind: 'friend' | 'group', id: string, remark: string): Promise<Friend | GroupChat>
      searchFriendByUsername(username: string): Promise<Friend>
      applyAddFriend(username: string, reason: string): Promise<boolean>
      searchGroupByNumber(groupNumber: string): Promise<GroupChat>
      applyJoinGroup(groupNumber: string, reason: string): Promise<boolean>
      addFriendByUsername(username: string): Promise<Friend>
      joinGroupByNumber(groupNumber: string): Promise<GroupActionResult>
      createGroup(name: string): Promise<GroupActionResult>
      markNotificationRead(id: string): Promise<unknown>
      setWindowMode(mode: 'login' | 'register' | 'main'): void
      minimizeWindow(): Promise<boolean>
      toggleMaximize(): Promise<boolean>
      closeWindow(): Promise<boolean>
      isWindowMaximized(): Promise<boolean>
      onWindowMaximizedChanged(callback: (value: boolean) => void): () => void
    }
  }
}

export {}
