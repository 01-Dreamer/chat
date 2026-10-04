import { app } from 'electron'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { DatabaseSync } from 'node:sqlite'
import type { AppNotification, Conversation, FileResource, Friend, FriendRequest, GroupChat, GroupMember, Message, RedPacket, ServerMessage, User } from '../types'
import { initializeDatabase } from './migrations'

class DatabaseManager {
  private database: DatabaseSync | null = null
  private currentUserId = ''
  private activeChatKey: string | null = null

  openForUser(userId: string) {
    if (!/^\d+$/.test(userId)) throw new Error('用户 ID 格式不正确')
    if (this.database && this.currentUserId === userId) return
    this.close()

    const databaseDirectory = join(app.getPath('userData'), 'databases', userId)
    mkdirSync(databaseDirectory, { recursive: true })
    const database = new DatabaseSync(join(databaseDirectory, 'chat.sqlite'))
    initializeDatabase(database)
    // A process restart loses the in-memory WebSocket acknowledgement tracker.
    // Leave these messages retryable instead of displaying an endless spinner.
    database.prepare('UPDATE message SET send_status = 2 WHERE send_status = 0').run()
    this.database = database
    this.currentUserId = userId
  }

  upsertCurrentUser(user: User) {
    const database = this.requireDatabase()
    const now = Date.now()
    database.prepare(`
      INSERT INTO user (
        id, username, nickname, avatar_url, status, created_time, updated_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        username = excluded.username,
        nickname = excluded.nickname,
        avatar_local_path = CASE WHEN avatar_url IS excluded.avatar_url THEN avatar_local_path ELSE NULL END,
        avatar_url = excluded.avatar_url,
        status = excluded.status,
        updated_time = excluded.updated_time
    `).run(
      user.id,
      user.username,
      user.nickname,
      user.avatar,
      user.status ?? 1,
      user.createdTime || now,
      user.updatedTime || now,
    )
  }

  syncFriendData(friends: Friend[], requests: FriendRequest[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.replaceFriends(database, friends)
      this.replaceFriendRequests(database, requests)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  syncFriends(friends: Friend[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.replaceFriends(database, friends)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  syncFriendRequests(requests: FriendRequest[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.replaceFriendRequests(database, requests)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  syncGroupData(groups: GroupChat[], requests: FriendRequest[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      database.exec('UPDATE `group` SET status = 0')
      for (const group of groups) this.upsertGroupRecord(database, group)
      this.replaceGroupJoinRequests(database, requests)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  loadCachedFriends(): Friend[] {
    const rows = this.requireDatabase().prepare(`
      SELECT f.*, u.username, u.nickname, COALESCE(u.avatar_local_path, u.avatar_url) AS avatar_url, u.updated_time AS user_updated_time
      FROM friend f JOIN user u ON u.id = f.friend_id ORDER BY COALESCE(f.remark, u.nickname)
    `).all() as Array<Record<string, unknown>>
    return rows.map((row) => ({
      id: String(row.friend_id), relationId: String(row.id), username: String(row.username ?? ''), nickname: String(row.nickname),
      avatar: row.avatar_url as string | null, signature: '', status: 'offline', remark: String(row.remark ?? ''),
      createdTime: Number(row.created_time), updatedTime: Number(row.updated_time), userUpdatedTime: Number(row.user_updated_time),
    }))
  }

  loadCachedFriendRequests(): FriendRequest[] {
    const rows = this.requireDatabase().prepare(`
      SELECT r.*, u.username, u.nickname, COALESCE(u.avatar_local_path, u.avatar_url) AS avatar_url FROM friend_add_request r
      LEFT JOIN user u ON u.id = CASE WHEN r.sender_id = ? THEN r.receiver_id ELSE r.sender_id END
      ORDER BY r.created_time DESC
    `).all(this.currentUserId) as Array<Record<string, unknown>>
    return rows.map((row) => ({
      id: String(row.id), requestType: 'friend', direction: String(row.sender_id) === this.currentUserId ? 'outgoing' : 'incoming',
      nickname: String(row.nickname ?? '用户'), username: String(row.username ?? ''), avatar: row.avatar_url as string | null,
      message: String(row.message ?? ''), time: new Date(Number(row.created_time)).toLocaleString('zh-CN', { hour12: false }),
      status: Number(row.status) === 0 ? 'pending' : Number(row.status) === 1 ? 'accepted' : 'rejected',
      senderId: String(row.sender_id), receiverId: String(row.receiver_id), createdTime: Number(row.created_time), updatedTime: Number(row.updated_time),
    }))
  }

  loadCachedGroups(): GroupChat[] {
    const rows = this.requireDatabase().prepare(`
      SELECT g.*, COALESCE(g.avatar_local_path, g.avatar_url) AS display_avatar, s.remark, gm.role, gm.nickname AS my_nickname,
        (SELECT COUNT(*) FROM group_member x WHERE x.group_id = g.id) AS member_count,
        owner.nickname AS owner_name
      FROM \`group\` g
      LEFT JOIN user_group_setting s ON s.group_id = g.id
      LEFT JOIN group_member gm ON gm.group_id = g.id AND gm.user_id = ?
      LEFT JOIN user owner ON owner.id = g.owner_id
      WHERE g.status = 1 ORDER BY g.updated_time DESC
    `).all(this.currentUserId) as Array<Record<string, unknown>>
    return rows.map((row) => ({
      id: String(row.id), groupNumber: String(row.id), name: String(row.name), avatar: row.display_avatar as string | null,
      memberCount: Number(row.member_count), description: '', owner: String(row.owner_name ?? '群主'), ownerId: String(row.owner_id),
      remark: String(row.remark ?? ''), status: Number(row.status), currentUserRole: Number(row.role ?? 0),
      currentUserNickname: String(row.my_nickname ?? ''), createdTime: Number(row.created_time), updatedTime: Number(row.updated_time),
    }))
  }

  replaceNotifications(items: AppNotification[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      database.exec('DELETE FROM notification')
      const statement = database.prepare(`INSERT INTO notification (id, notification_type, title, content, is_read, created_time, updated_time) VALUES (?, ?, ?, ?, ?, ?, ?)`)
      const types: Record<AppNotification['type'], number> = { friend_request: 0, friend_accepted: 0, group_joined: 1, red_packet: 2, transfer: 3, system: 3 }
      for (const item of items) {
        const time = Date.parse(item.time) || Date.now()
        statement.run(item.id, types[item.type], item.title, item.content, item.read ? 1 : 0, time, time)
      }
      database.exec('COMMIT')
    } catch (error) { database.exec('ROLLBACK'); throw error }
  }

  loadCachedNotifications(): AppNotification[] {
    const types: AppNotification['type'][] = ['friend_request', 'group_joined', 'red_packet', 'system']
    const rows = this.requireDatabase().prepare(`SELECT * FROM notification ORDER BY created_time DESC`).all() as Array<Record<string, unknown>>
    return rows.map((row) => {
      const title = String(row.title ?? '通知')
      return { id: String(row.id), type: Number(row.notification_type) === 3 && title.includes('转账') ? 'transfer' : types[Number(row.notification_type)] ?? 'system', title, content: String(row.content ?? ''), time: new Date(Number(row.created_time)).toLocaleString('zh-CN', { hour12: false }), read: Number(row.is_read) === 1 }
    })
  }

  setUserAvatarLocal(userId: string, remoteUrl: string, localUrl: string) {
    this.requireDatabase().prepare(`UPDATE user SET avatar_local_path = ? WHERE id = ? AND avatar_url = ?`).run(localUrl, userId, remoteUrl)
  }

  setGroupAvatarLocal(groupId: string, remoteUrl: string, localUrl: string) {
    this.requireDatabase().prepare(`UPDATE \`group\` SET avatar_local_path = ? WHERE id = ? AND avatar_url = ?`).run(localUrl, groupId, remoteUrl)
  }

  getDisplayAvatar(kind: 'user' | 'group', id: string) {
    const table = kind === 'user' ? 'user' : '`group`'
    const row = this.requireDatabase().prepare(`SELECT COALESCE(avatar_local_path, avatar_url) AS avatar FROM ${table} WHERE id = ?`).get(id) as { avatar?: string | null } | undefined
    return row?.avatar ?? null
  }

  syncGroupJoinRequests(requests: FriendRequest[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.replaceGroupJoinRequests(database, requests)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  syncGroupMembers(groupId: string, members: GroupMember[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      database.prepare('DELETE FROM group_member WHERE group_id = ?').run(groupId)
      const upsertUser = database.prepare(`
        INSERT INTO user (id, username, nickname, avatar_url, status, created_time, updated_time)
        VALUES (?, ?, ?, ?, 1, ?, ?)
        ON CONFLICT(id) DO UPDATE SET username=excluded.username, nickname=excluded.nickname,
          avatar_local_path=CASE WHEN avatar_url IS excluded.avatar_url THEN avatar_local_path ELSE NULL END,
          avatar_url=excluded.avatar_url, updated_time=excluded.updated_time
      `)
      const insertMember = database.prepare(`
        INSERT INTO group_member (id, group_id, user_id, role, nickname, created_time, updated_time)
        VALUES (?, ?, ?, ?, ?, ?, ?)
      `)
      for (const member of members) {
        upsertUser.run(member.userId, member.username, member.nickname, member.avatar, member.createdTime, member.updatedTime)
        insertMember.run(member.id, member.groupId, member.userId, member.role, member.groupNickname || null, member.createdTime, member.updatedTime)
      }
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  upsertGroup(group: GroupChat) {
    this.upsertGroupRecord(this.requireDatabase(), group)
  }

  saveCreatedGroup(group: GroupChat, conversation: Conversation) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.upsertGroupRecord(database, group)
      const now = Date.now()
      database.prepare(`
        INSERT INTO session (chat_key, chat_type, target_id, unread_count, is_top, last_active_time, created_time, updated_time)
        VALUES (?, 1, ?, 0, 0, ?, ?, ?)
        ON CONFLICT(chat_key) DO UPDATE SET target_id=excluded.target_id, last_active_time=excluded.last_active_time, updated_time=excluded.updated_time
      `).run(`G:${group.id}`, conversation.targetId, now, now, now)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  openConversation(type: 'direct' | 'group', targetId: string) {
    const database = this.requireDatabase()
    const chatKey = type === 'group'
      ? `G:${targetId}`
      : this.directChatKey(this.currentUserId, targetId)
    const now = Date.now()
    database.prepare(`
      INSERT INTO session (chat_key, chat_type, target_id, unread_count, is_top, last_active_time, created_time, updated_time)
      VALUES (?, ?, ?, 0, 0, ?, ?, ?)
      ON CONFLICT(chat_key) DO UPDATE SET target_id=excluded.target_id, updated_time=excluded.updated_time
    `).run(chatKey, type === 'group' ? 1 : 0, targetId, now, now, now)
    return this.loadConversation(chatKey)
  }

  setSessionPinned(chatKey: string, pinned: boolean) {
    this.requireDatabase().prepare(`
      UPDATE session SET is_top = ?, updated_time = ? WHERE chat_key = ?
    `).run(pinned ? 1 : 0, Date.now(), chatKey)
  }

  markSessionRead(chatKey: string) {
    const now = Date.now()
    this.requireDatabase().prepare(`
      UPDATE session SET unread_count = 0, last_read_time = MAX(last_read_time, ?), updated_time = ? WHERE chat_key = ?
    `).run(now, now, chatKey)
  }

  setActiveChat(chatKey: string | null) {
    this.activeChatKey = chatKey
    if (chatKey) this.markSessionRead(chatKey)
  }

  hideSession(chatKey: string) {
    this.requireDatabase().prepare('DELETE FROM session WHERE chat_key = ?').run(chatKey)
  }

  saveOutgoingMessage(message: Message, chatType: number, targetId: string) {
    if (!message.clientMessageId || !message.chatKey) throw new Error('待发送消息缺少幂等ID或会话标识')
    const database = this.requireDatabase()
    const now = message.createdAt
    database.exec('BEGIN IMMEDIATE')
    try {
      database.prepare(`
        INSERT INTO message (
          id, client_message_id, chat_key, sender_id, chat_type, target_id,
          message_type, content, reference_id, reply_message_id, status, send_status, created_time, updated_time
        ) VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
        ON CONFLICT(sender_id, client_message_id) DO NOTHING
      `).run(
        message.clientMessageId, message.chatKey, message.senderId, chatType, targetId,
        message.type === 'text' ? 0 : message.type === 'file' || message.type === 'voice' ? 1 : message.type === 'red_packet' ? 2 : 3,
        message.content, message.referenceId ?? null, message.replyMessageId ?? null, now, now,
      )
      this.upsertSessionForMessage(database, message.chatKey!, chatType, targetId, now, false, message.clientMessageId)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  applyRealtimeMessage(message: ServerMessage, forceIncomingDelivery = false) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.upsertServerMessage(database, message, forceIncomingDelivery)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
    return this.toUiMessage(message)
  }

  applyInboxMessage(message: ServerMessage, sequence: number) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.upsertServerMessage(database, message)
      this.setLastSequence(database, sequence)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
    return this.toUiMessage(message)
  }

  applyHistory(messages: ServerMessage[]) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      // Opening history must never turn old messages into unread messages.
      for (const message of messages) this.upsertServerMessage(database, message, false, false)
      database.exec('COMMIT')
    } catch (error) { database.exec('ROLLBACK'); throw error }
  }

  loadConversationMessages(chatKey: string) {
    const rows = this.requireDatabase().prepare(`
      SELECT m.*, u.nickname AS sender_name, u.avatar_url AS sender_avatar,
             recaller.nickname AS recall_operator_name
      FROM message m
      LEFT JOIN user u ON u.id = m.sender_id
      LEFT JOIN user recaller ON recaller.id = m.recall_operator_id
      WHERE m.chat_key = ? ORDER BY m.created_time ASC, m.local_id ASC
    `).all(chatKey) as Array<Record<string, unknown>>
    return rows.map((row) => this.localRowToUiMessage(row))
  }

  markMessageFailed(clientMessageId: string) {
    this.requireDatabase().prepare(`
      UPDATE message SET send_status = 2, updated_time = ? WHERE client_message_id = ?
    `).run(Date.now(), clientMessageId)
  }

  prepareMessageRetry(clientMessageId: string) {
    const database = this.requireDatabase()
    const row = database.prepare(`
      SELECT m.*, u.nickname AS sender_name, u.avatar_url AS sender_avatar,
             recaller.nickname AS recall_operator_name
      FROM message m
      LEFT JOIN user u ON u.id = m.sender_id
      LEFT JOIN user recaller ON recaller.id = m.recall_operator_id
      WHERE m.client_message_id = ? AND m.sender_id = ? LIMIT 1
    `).get(clientMessageId, this.currentUserId) as Record<string, unknown> | undefined
    if (!row) throw new Error('没有找到需要重发的消息')
    database.prepare('UPDATE message SET send_status = 0, updated_time = ? WHERE local_id = ?')
      .run(Date.now(), Number(row.local_id))
    row.send_status = 0
    return {
      message: this.localRowToUiMessage(row),
      payload: {
        clientMessageId: String(row.client_message_id),
        chatType: Number(row.chat_type),
        targetId: String(row.target_id),
        messageType: Number(row.message_type),
        content: row.content == null ? null : String(row.content),
        referenceId: row.reference_id == null ? null : String(row.reference_id),
        replyMessageId: row.reply_message_id == null ? null : String(row.reply_message_id),
      },
    }
  }

  getLastSequence() {
    const row = this.requireDatabase().prepare(`
      SELECT last_sequence FROM inbox_sync_state WHERE id = 1
    `).get() as { last_sequence?: number } | undefined
    return Number(row?.last_sequence ?? 0)
  }

  advanceLastSequence(sequence: number) {
    const database = this.requireDatabase()
    database.exec('BEGIN IMMEDIATE')
    try {
      this.setLastSequence(database, sequence)
      database.exec('COMMIT')
    } catch (error) {
      database.exec('ROLLBACK')
      throw error
    }
  }

  loadChatState(): { conversations: Conversation[], messages: Record<string, Message[]> } {
    const database = this.requireDatabase()
    const conversations = this.loadConversations()
    const messages: Record<string, Message[]> = {}
    const statement = database.prepare(`
      SELECT m.*, u.nickname AS sender_name, u.avatar_url AS sender_avatar,
             recaller.nickname AS recall_operator_name
      FROM message m
      LEFT JOIN user u ON u.id = m.sender_id
      LEFT JOIN user recaller ON recaller.id = m.recall_operator_id
      WHERE m.chat_key = ? ORDER BY m.created_time ASC, m.local_id ASC
    `)
    for (const conversation of conversations) {
      const messageRows = statement.all(conversation.id) as Array<Record<string, unknown>>
      messages[conversation.id] = messageRows.map((row) => this.localRowToUiMessage(row))
    }
    return { conversations, messages }
  }

  loadSessionState(): { conversations: Conversation[], messages: Record<string, Message[]> } {
    return { conversations: this.loadConversations(), messages: {} }
  }

  loadConversations() {
    const rows = this.requireDatabase().prepare(`
      SELECT chat_key FROM session ORDER BY is_top DESC, created_time DESC
    `).all() as Array<{ chat_key: string }>
    return rows.map((row) => this.loadConversation(row.chat_key)).filter(Boolean) as Conversation[]
  }

  getConversation(chatKey: string) {
    return this.loadConversation(chatKey)
  }

  upsertFileResource(resource: FileResource, downloadStatus: number) {
    this.requireDatabase().prepare(`
      INSERT INTO file_resource (
        id, uploader_id, resource_type, file_name, file_size, mime_type, file_hash,
        file_url, duration, local_path, download_status, created_time, updated_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET file_url=excluded.file_url, local_path=COALESCE(excluded.local_path, file_resource.local_path),
        download_status=excluded.download_status, updated_time=excluded.updated_time
    `).run(
      resource.id, resource.uploaderId, resource.resourceType, resource.fileName, resource.fileSize,
      resource.mimeType, resource.fileHash, resource.fileUrl, resource.duration, resource.localPath ?? null,
      downloadStatus, resource.createdTime, resource.updatedTime,
    )
  }

  getFileResource(resourceId: string): FileResource | null {
    const row = this.requireDatabase().prepare(`SELECT * FROM file_resource WHERE id = ?`).get(resourceId) as Record<string, unknown> | undefined
    if (!row) return null
    return {
      id: String(row.id), uploaderId: String(row.uploader_id), resourceType: Number(row.resource_type),
      fileName: String(row.file_name), fileSize: Number(row.file_size), mimeType: String(row.mime_type),
      fileHash: String(row.file_hash), fileUrl: String(row.file_url), duration: row.duration == null ? null : Number(row.duration),
      localPath: row.local_path as string | null, createdTime: Number(row.created_time), updatedTime: Number(row.updated_time),
    }
  }

  upsertRedPacket(packet: RedPacket) {
    this.requireDatabase().prepare(`
      INSERT INTO red_packet (
        id, sender_id, chat_type, target_id, packet_type, total_amount, total_count,
        remain_amount, remain_count, message, status, expire_time, created_time, updated_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET remain_amount=excluded.remain_amount, remain_count=excluded.remain_count,
        status=excluded.status, updated_time=excluded.updated_time
    `).run(
      packet.id, packet.senderId, packet.chatType, packet.targetId, packet.packetType,
      packet.totalAmount, packet.totalCount, packet.remainAmount, packet.remainCount,
      packet.message, packet.status, packet.expireTime, packet.createdTime, packet.updatedTime,
    )
  }

  getRedPacket(packetId: string): RedPacket | null {
    const row = this.requireDatabase().prepare(`SELECT * FROM red_packet WHERE id = ?`).get(packetId) as Record<string, unknown> | undefined
    if (!row) return null
    return {
      id: String(row.id), senderId: String(row.sender_id), chatType: Number(row.chat_type), targetId: String(row.target_id),
      packetType: Number(row.packet_type), totalAmount: String(row.total_amount), totalCount: Number(row.total_count),
      remainAmount: String(row.remain_amount), remainCount: Number(row.remain_count), message: String(row.message ?? ''),
      status: Number(row.status), expireTime: Number(row.expire_time), createdTime: Number(row.created_time), updatedTime: Number(row.updated_time),
    }
  }

  getTranslation(textHash: string, targetLanguage: string) {
    const row = this.requireDatabase().prepare(`
      SELECT translated_text FROM translation_cache WHERE text_hash = ? AND target_language = ?
    `).get(textHash, targetLanguage) as { translated_text?: string } | undefined
    return row?.translated_text ?? null
  }

  saveTranslation(textHash: string, targetLanguage: string, translatedText: string) {
    const now = Date.now()
    this.requireDatabase().prepare(`
      INSERT INTO translation_cache (text_hash, target_language, translated_text, created_time, updated_time)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(text_hash, target_language) DO UPDATE SET translated_text=excluded.translated_text, updated_time=excluded.updated_time
    `).run(textHash, targetLanguage, translatedText, now, now)
  }

  getAsr(fileHash: string) {
    const row = this.requireDatabase().prepare(`SELECT recognized_text FROM asr_cache WHERE file_hash = ?`).get(fileHash) as { recognized_text?: string } | undefined
    return row?.recognized_text ?? null
  }

  saveAsr(fileHash: string, text: string) {
    const now = Date.now()
    this.requireDatabase().prepare(`
      INSERT INTO asr_cache (file_hash, recognized_text, created_time, updated_time)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(file_hash) DO UPDATE SET recognized_text=excluded.recognized_text, updated_time=excluded.updated_time
    `).run(fileHash, text, now, now)
  }

  close() {
    this.database?.close()
    this.database = null
    this.currentUserId = ''
    this.activeChatKey = null
  }

  private requireDatabase() {
    if (!this.database) throw new Error('本地数据库尚未初始化')
    return this.database
  }

  private replaceFriends(database: DatabaseSync, friends: Friend[]) {
    database.exec('DELETE FROM friend')
    const upsertUser = database.prepare(`
      INSERT INTO user (id, username, nickname, avatar_url, status, created_time, updated_time)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        username = excluded.username,
        nickname = excluded.nickname,
        avatar_local_path = CASE WHEN avatar_url IS excluded.avatar_url THEN avatar_local_path ELSE NULL END,
        avatar_url = excluded.avatar_url,
        status = excluded.status,
        updated_time = excluded.updated_time
    `)
    const insertFriend = database.prepare(`
      INSERT INTO friend (id, friend_id, remark, created_time, updated_time)
      VALUES (?, ?, ?, ?, ?)
    `)
    const now = Date.now()
    for (const friend of friends) {
      upsertUser.run(
        friend.id,
        friend.username,
        friend.nickname,
        friend.avatar,
        1,
        friend.createdTime || now,
        friend.userUpdatedTime || now,
      )
      insertFriend.run(
        friend.relationId ?? friend.id,
        friend.id,
        friend.remark || null,
        friend.createdTime || now,
        friend.updatedTime || now,
      )
    }
  }

  private replaceFriendRequests(database: DatabaseSync, requests: FriendRequest[]) {
    database.exec('DELETE FROM friend_add_request')
    const upsertUser = database.prepare(`
      INSERT INTO user (id, username, nickname, avatar_url, status, created_time, updated_time)
      VALUES (?, ?, ?, ?, 1, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        username = excluded.username,
        nickname = excluded.nickname,
        avatar_local_path = CASE WHEN avatar_url IS excluded.avatar_url THEN avatar_local_path ELSE NULL END,
        avatar_url = excluded.avatar_url,
        updated_time = excluded.updated_time
    `)
    const insertRequest = database.prepare(`
      INSERT INTO friend_add_request (
        id, sender_id, receiver_id, message, status, created_time, updated_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?)
    `)
    const now = Date.now()
    for (const request of requests) {
      if (!request.senderId || !request.receiverId) throw new Error('好友申请缺少用户标识')
      const otherUserId = request.direction === 'incoming' ? request.senderId : request.receiverId
      if (otherUserId) {
        upsertUser.run(
          otherUserId,
          request.username,
          request.nickname,
          request.avatar,
          request.createdTime || now,
          request.updatedTime || now,
        )
      }
      insertRequest.run(
        request.id,
        request.senderId,
        request.receiverId,
        request.message || null,
        request.status === 'pending' ? 0 : request.status === 'accepted' ? 1 : 2,
        request.createdTime || now,
        request.updatedTime || now,
      )
    }
  }

  private upsertGroupRecord(database: DatabaseSync, group: GroupChat) {
    database.prepare(`
      INSERT INTO \`group\` (id, name, avatar_url, owner_id, status, created_time, updated_time)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET name=excluded.name,
        avatar_local_path=CASE WHEN avatar_url IS excluded.avatar_url THEN avatar_local_path ELSE NULL END,
        avatar_url=excluded.avatar_url,
        owner_id=excluded.owner_id, status=excluded.status, updated_time=excluded.updated_time
    `).run(group.id, group.name, group.avatar, group.ownerId, group.status, group.createdTime, group.updatedTime)
    database.prepare(`
      INSERT INTO user_group_setting (id, group_id, remark, created_time, updated_time)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(group_id) DO UPDATE SET remark=excluded.remark, updated_time=excluded.updated_time
    `).run(`setting:${group.id}`, group.id, group.remark || null, group.createdTime, group.updatedTime)
  }

  private replaceGroupJoinRequests(database: DatabaseSync, requests: FriendRequest[]) {
    database.exec('DELETE FROM group_join_request')
    const statement = database.prepare(`
      INSERT INTO group_join_request (id, group_id, user_id, message, status, reviewer_id, created_time, updated_time)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `)
    for (const request of requests.filter((item) => item.requestType === 'group')) {
      if (!request.groupNumber || !request.senderId) throw new Error('入群申请缺少群聊或用户标识')
      statement.run(
        request.id,
        request.groupNumber,
        request.senderId,
        request.message || null,
        request.status === 'pending' ? 0 : request.status === 'accepted' ? 1 : 2,
        request.receiverId ?? null,
        request.createdTime ?? Date.now(),
        request.updatedTime ?? Date.now(),
      )
    }
  }

  private upsertServerMessage(
    database: DatabaseSync,
    message: ServerMessage,
    forceIncomingDelivery = false,
    countUnread = true,
  ) {
    const existing = database.prepare(`
      SELECT 1 FROM message WHERE id = ? OR (sender_id = ? AND client_message_id = ?) LIMIT 1
    `).get(message.id, message.senderId, message.clientMessageId)
    const sessionState = database.prepare(`
      SELECT last_read_time FROM session WHERE chat_key = ? LIMIT 1
    `).get(message.chatKey) as { last_read_time?: number } | undefined
    database.prepare(`
      INSERT INTO message (
        id, client_message_id, chat_key, sender_id, chat_type, target_id, message_type,
        content, reference_id, reply_message_id, status, recall_operator_id, recalled_time,
        send_status, created_time, updated_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
      ON CONFLICT(sender_id, client_message_id) DO UPDATE SET
        id=excluded.id,
        content=CASE WHEN excluded.status = 1 THEN NULL ELSE excluded.content END,
        reference_id=excluded.reference_id,
        reply_message_id=excluded.reply_message_id, status=excluded.status,
        recall_operator_id=excluded.recall_operator_id, recalled_time=excluded.recalled_time,
        send_status=1, updated_time=excluded.updated_time
    `).run(
      message.id, message.clientMessageId, message.chatKey, message.senderId,
      message.chatType, message.targetId, message.messageType, message.status === 1 ? null : message.content,
      message.referenceId, message.replyMessageId, message.status,
      message.recallOperatorId, message.recalledTime, message.createdTime, message.updatedTime,
    )
    const targetId = message.chatType === 0
      ? (message.senderId === this.currentUserId ? message.targetId : message.senderId)
      : message.targetId
    // Replayed websocket/inbox events must not recreate a session the user
    // explicitly hid. A genuinely new message is allowed to bring it back.
    if (sessionState || !existing || forceIncomingDelivery) {
      const incoming = countUnread
        && message.chatKey !== this.activeChatKey
        && message.createdTime > Number(sessionState?.last_read_time ?? 0)
        && (forceIncomingDelivery || (!existing && message.senderId !== this.currentUserId))
      this.upsertSessionForMessage(
        database,
        message.chatKey,
        message.chatType,
        targetId,
        message.createdTime,
        incoming,
        message.id,
      )
    }
  }

  private upsertSessionForMessage(
    database: DatabaseSync,
    chatKey: string,
    chatType: number,
    targetId: string,
    time: number,
    incoming: boolean,
    lastMessageId: string | null = null,
  ) {
    const now = Date.now()
    database.prepare(`
      INSERT INTO session (
        chat_key, chat_type, target_id, last_message_id, unread_count,
        is_top, last_active_time, created_time, updated_time
      ) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)
      ON CONFLICT(chat_key) DO UPDATE SET
        target_id=excluded.target_id,
        last_message_id=CASE
          WHEN excluded.last_active_time >= session.last_active_time
          THEN COALESCE(excluded.last_message_id, session.last_message_id)
          ELSE session.last_message_id
        END,
        unread_count=session.unread_count + ?,
        last_active_time=MAX(session.last_active_time, excluded.last_active_time),
        updated_time=excluded.updated_time
    `).run(chatKey, chatType, targetId, lastMessageId, incoming ? 1 : 0, time, now, now, incoming ? 1 : 0)
  }

  private loadConversation(chatKey: string): Conversation | null {
    const row = this.requireDatabase().prepare(`
      SELECT s.*, u.nickname AS user_name, u.avatar_url AS user_avatar,
             g.name AS group_name, g.avatar_url AS group_avatar,
             m.content AS last_content, m.message_type AS last_type, m.status AS last_status
      FROM session s
      LEFT JOIN user u ON s.chat_type = 0 AND u.id = s.target_id
      LEFT JOIN \`group\` g ON s.chat_type = 1 AND g.id = s.target_id
      LEFT JOIN message m ON m.id = s.last_message_id
        OR (m.id IS NULL AND m.client_message_id = s.last_message_id)
      WHERE s.chat_key = ?
    `).get(chatKey) as Record<string, unknown> | undefined
    if (!row) return null
    const isGroup = Number(row.chat_type) === 1
    const name = String((isGroup ? row.group_name : row.user_name) ?? (isGroup ? '群聊' : '用户'))
    return {
      id: String(row.chat_key),
      type: isGroup ? 'group' : 'direct',
      name,
      avatar: (isGroup ? row.group_avatar : row.user_avatar) as string | null,
      preview: this.preview(Number(row.last_type), row.last_content as string | null, Number(row.last_status)),
      time: this.formatConversationTime(Number(row.last_active_time)),
      unread: Number(row.unread_count),
      targetId: String(row.target_id),
      pinned: Number(row.is_top) === 1,
      lastActiveTime: Number(row.last_active_time),
      createdTime: Number(row.created_time),
    }
  }

  private toUiMessage(message: ServerMessage): Message {
    const user = this.requireDatabase().prepare(`SELECT nickname, avatar_url FROM user WHERE id = ?`).get(message.senderId) as { nickname?: string, avatar_url?: string | null } | undefined
    const operator = message.recallOperatorId
      ? this.requireDatabase().prepare('SELECT nickname FROM user WHERE id = ?').get(message.recallOperatorId) as { nickname?: string } | undefined
      : undefined
    const resource = message.referenceId && message.messageType === 1 ? this.getFileResource(message.referenceId) : null
    const packet = message.referenceId && message.messageType === 2 ? this.getRedPacket(message.referenceId) : null
    return {
      id: message.id,
      clientMessageId: message.clientMessageId,
      chatKey: message.chatKey,
      conversationId: message.chatKey,
      senderId: message.senderId,
      senderName: user?.nickname ?? '用户',
      senderAvatar: user?.avatar_url ?? null,
      type: message.messageType === 0 ? 'text' : message.messageType === 1 ? (resource?.resourceType === 2 ? 'voice' : 'file') : message.messageType === 2 ? 'red_packet' : 'text',
      content: message.status === 1 ? '该消息已撤回' : message.content ?? '',
      referenceId: message.referenceId,
      replyMessageId: message.replyMessageId,
      fileKind: resource ? this.fileKind(resource.resourceType) : undefined,
      fileName: resource?.fileName,
      fileSize: resource ? this.formatFileSize(resource.fileSize) : undefined,
      duration: resource?.duration ?? undefined,
      amount: packet?.totalAmount,
      createdAt: message.createdTime,
      status: message.status,
      recallOperatorId: message.recallOperatorId,
      recallOperatorName: operator?.nickname ?? null,
      sendStatus: 'success',
    }
  }

  private localRowToUiMessage(row: Record<string, unknown>): Message {
    const resource = row.reference_id ? this.getFileResource(String(row.reference_id)) : null
    const packet = row.reference_id && Number(row.message_type) === 2 ? this.getRedPacket(String(row.reference_id)) : null
    return {
      id: String(row.id ?? `local:${row.local_id}`),
      clientMessageId: String(row.client_message_id),
      chatKey: String(row.chat_key),
      conversationId: String(row.chat_key),
      senderId: String(row.sender_id),
      senderName: String(row.sender_name ?? (String(row.sender_id) === this.currentUserId ? '我' : '用户')),
      senderAvatar: row.sender_avatar as string | null,
      type: Number(row.message_type) === 0 ? 'text' : Number(row.message_type) === 1 ? (resource?.resourceType === 2 ? 'voice' : 'file') : Number(row.message_type) === 2 ? 'red_packet' : 'text',
      content: Number(row.status) === 1 ? '该消息已撤回' : String(row.content ?? ''),
      referenceId: row.reference_id == null ? null : String(row.reference_id),
      replyMessageId: row.reply_message_id == null ? null : String(row.reply_message_id),
      fileKind: resource ? this.fileKind(resource.resourceType) : undefined,
      fileName: resource?.fileName,
      fileSize: resource ? this.formatFileSize(resource.fileSize) : undefined,
      duration: resource?.duration ?? undefined,
      amount: packet?.totalAmount,
      createdAt: Number(row.created_time),
      status: Number(row.status),
      recallOperatorId: row.recall_operator_id == null ? null : String(row.recall_operator_id),
      recallOperatorName: row.recall_operator_name == null ? null : String(row.recall_operator_name),
      sendStatus: Number(row.send_status) === 0 ? 'sending' : Number(row.send_status) === 1 ? 'success' : 'failed',
    }
  }

  private fileKind(resourceType: number) {
    return resourceType === 0 ? 'image' as const : resourceType === 1 ? 'video' as const : 'document' as const
  }

  private formatFileSize(bytes: number) {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  }

  private setLastSequence(database: DatabaseSync, sequence: number) {
    database.prepare(`
      UPDATE inbox_sync_state SET last_sequence = MAX(last_sequence, ?), updated_time = ? WHERE id = 1
    `).run(sequence, Date.now())
  }

  private directChatKey(firstId: string, secondId: string) {
    return BigInt(firstId) < BigInt(secondId)
      ? `P:${firstId}:${secondId}`
      : `P:${secondId}:${firstId}`
  }

  private preview(messageType: number, content: string | null, status = 0) {
    if (status === 1) return '[消息已撤回]'
    if (!Number.isFinite(messageType)) return ''
    if (messageType === 0) return content ?? ''
    if (messageType === 1) return '[文件]'
    if (messageType === 2) return '[红包]'
    return '[通话]'
  }

  private formatConversationTime(value: number) {
    if (!value) return ''
    return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
}

export const databaseManager = new DatabaseManager()
