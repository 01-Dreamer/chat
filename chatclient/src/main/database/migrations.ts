import { DatabaseSync } from 'node:sqlite'

const INITIAL_SCHEMA = `
CREATE TABLE IF NOT EXISTS user (
  id TEXT PRIMARY KEY NOT NULL,
  username TEXT,
  nickname TEXT NOT NULL,
  avatar_url TEXT,
  avatar_local_path TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_user_updated_time ON user (updated_time);

CREATE TABLE IF NOT EXISTS friend (
  id TEXT PRIMARY KEY NOT NULL,
  friend_id TEXT NOT NULL UNIQUE,
  remark TEXT,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_friend_updated_time ON friend (updated_time);

CREATE TABLE IF NOT EXISTS friend_add_request (
  id TEXT PRIMARY KEY NOT NULL,
  sender_id TEXT NOT NULL,
  receiver_id TEXT NOT NULL,
  message TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_friend_request_time ON friend_add_request (created_time DESC);

CREATE TABLE IF NOT EXISTS \`group\` (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  avatar_url TEXT,
  avatar_local_path TEXT,
  owner_id TEXT NOT NULL,
  owner_name TEXT NOT NULL DEFAULT '群主',
  member_count INTEGER NOT NULL DEFAULT 0,
  current_user_role INTEGER NOT NULL DEFAULT 0,
  current_user_nickname TEXT,
  status INTEGER NOT NULL DEFAULT 1,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_group_updated_time ON \`group\` (updated_time);

CREATE TABLE IF NOT EXISTS group_member (
  id TEXT PRIMARY KEY NOT NULL,
  group_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  role INTEGER NOT NULL DEFAULT 0,
  nickname TEXT,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL,
  UNIQUE (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_group_member_group ON group_member (group_id);
CREATE INDEX IF NOT EXISTS idx_group_member_user ON group_member (user_id);
CREATE INDEX IF NOT EXISTS idx_group_member_updated ON group_member (group_id, updated_time);

CREATE TABLE IF NOT EXISTS group_join_request (
  id TEXT PRIMARY KEY NOT NULL,
  group_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  message TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  reviewer_id TEXT,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_group_join_request_time ON group_join_request (created_time DESC);

CREATE TABLE IF NOT EXISTS user_group_setting (
  id TEXT PRIMARY KEY NOT NULL,
  group_id TEXT NOT NULL UNIQUE,
  remark TEXT,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS session (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  chat_key TEXT NOT NULL UNIQUE,
  chat_type INTEGER NOT NULL,
  target_id TEXT NOT NULL,
  last_message_id TEXT,
  unread_count INTEGER NOT NULL DEFAULT 0,
  last_read_time INTEGER NOT NULL DEFAULT 0,
  is_top INTEGER NOT NULL DEFAULT 0,
  draft TEXT,
  last_active_time INTEGER NOT NULL,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_session_order ON session (is_top DESC, created_time DESC);
CREATE INDEX IF NOT EXISTS idx_session_target ON session (chat_type, target_id);

CREATE TABLE IF NOT EXISTS message (
  local_id INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT UNIQUE,
  client_message_id TEXT NOT NULL,
  chat_key TEXT NOT NULL,
  sender_id TEXT NOT NULL,
  chat_type INTEGER NOT NULL,
  target_id TEXT NOT NULL,
  message_type INTEGER NOT NULL,
  content TEXT,
  reference_id TEXT,
  reply_message_id TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  recall_operator_id TEXT,
  recall_operator_name TEXT,
  recalled_time INTEGER,
  send_status INTEGER NOT NULL DEFAULT 0,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL,
  UNIQUE (sender_id, client_message_id)
);
CREATE INDEX IF NOT EXISTS idx_message_chat_time ON message (chat_key, created_time DESC);
CREATE INDEX IF NOT EXISTS idx_message_sender ON message (sender_id);
CREATE INDEX IF NOT EXISTS idx_message_reply ON message (reply_message_id);

CREATE TABLE IF NOT EXISTS file_resource (
  id TEXT PRIMARY KEY NOT NULL,
  uploader_id TEXT NOT NULL,
  resource_type INTEGER NOT NULL,
  file_name TEXT NOT NULL,
  file_size INTEGER NOT NULL,
  mime_type TEXT NOT NULL,
  file_hash TEXT NOT NULL UNIQUE,
  file_url TEXT,
  duration INTEGER,
  local_path TEXT,
  download_status INTEGER NOT NULL DEFAULT 0,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_file_resource_uploader ON file_resource (uploader_id);
CREATE INDEX IF NOT EXISTS idx_file_download_status ON file_resource (download_status);

CREATE TABLE IF NOT EXISTS call_record (
  id TEXT PRIMARY KEY NOT NULL,
  caller_id TEXT NOT NULL,
  callee_id TEXT NOT NULL,
  call_type INTEGER NOT NULL,
  status INTEGER NOT NULL DEFAULT 0,
  start_time INTEGER,
  end_time INTEGER,
  duration INTEGER NOT NULL DEFAULT 0,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_call_record_time ON call_record (created_time DESC);

CREATE TABLE IF NOT EXISTS red_packet (
  id TEXT PRIMARY KEY NOT NULL,
  sender_id TEXT NOT NULL,
  chat_type INTEGER NOT NULL,
  target_id TEXT NOT NULL,
  packet_type INTEGER NOT NULL,
  total_amount TEXT NOT NULL,
  total_count INTEGER NOT NULL,
  remain_amount TEXT NOT NULL,
  remain_count INTEGER NOT NULL,
  message TEXT,
  status INTEGER NOT NULL DEFAULT 0,
  expire_time INTEGER NOT NULL,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS red_packet_receive (
  id TEXT PRIMARY KEY NOT NULL,
  red_packet_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  amount TEXT NOT NULL,
  created_time INTEGER NOT NULL,
  UNIQUE (red_packet_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_red_packet_receive_packet ON red_packet_receive (red_packet_id);

CREATE TABLE IF NOT EXISTS notification (
  id TEXT PRIMARY KEY NOT NULL,
  notification_type INTEGER NOT NULL,
  reference_id TEXT,
  title TEXT,
  content TEXT,
  is_read INTEGER NOT NULL DEFAULT 0,
  read_time INTEGER,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notification_read ON notification (is_read);
CREATE INDEX IF NOT EXISTS idx_notification_time ON notification (created_time DESC);

CREATE TABLE IF NOT EXISTS inbox_sync_state (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  last_sequence INTEGER NOT NULL DEFAULT 0,
  updated_time INTEGER NOT NULL
);
INSERT OR IGNORE INTO inbox_sync_state (id, last_sequence, updated_time) VALUES (1, 0, 0);

CREATE TABLE IF NOT EXISTS directory_sync_state (
  sync_key TEXT PRIMARY KEY NOT NULL,
  version TEXT NOT NULL,
  updated_time INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS translation_cache (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  text_hash TEXT NOT NULL,
  target_language TEXT NOT NULL,
  translated_text TEXT NOT NULL,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL,
  UNIQUE (text_hash, target_language)
);
CREATE INDEX IF NOT EXISTS idx_translation_text_hash ON translation_cache (text_hash);

CREATE TABLE IF NOT EXISTS asr_cache (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_hash TEXT NOT NULL UNIQUE,
  recognized_text TEXT NOT NULL,
  created_time INTEGER NOT NULL,
  updated_time INTEGER NOT NULL
);
`

export function initializeDatabase(database: DatabaseSync) {
  database.exec('PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;')
  database.exec(INITIAL_SCHEMA)
  applyMigrations(database)
}

function applyMigrations(database: DatabaseSync) {
  const versionRow = database.prepare('PRAGMA user_version').get() as { user_version?: number } | undefined
  const previousVersion = Number(versionRow?.user_version ?? 0)
  const columns = database.prepare('PRAGMA table_info(`group`)').all() as Array<{ name: string }>
  if (!columns.some((column) => column.name === 'avatar_local_path')) {
    database.exec('ALTER TABLE `group` ADD COLUMN avatar_local_path TEXT DEFAULT NULL')
  }
  if (!columns.some((column) => column.name === 'owner_name')) {
    database.exec("ALTER TABLE `group` ADD COLUMN owner_name TEXT NOT NULL DEFAULT '群主'")
  }
  if (!columns.some((column) => column.name === 'member_count')) {
    database.exec('ALTER TABLE `group` ADD COLUMN member_count INTEGER NOT NULL DEFAULT 0')
  }
  if (!columns.some((column) => column.name === 'current_user_role')) {
    database.exec('ALTER TABLE `group` ADD COLUMN current_user_role INTEGER NOT NULL DEFAULT 0')
  }
  if (!columns.some((column) => column.name === 'current_user_nickname')) {
    database.exec('ALTER TABLE `group` ADD COLUMN current_user_nickname TEXT DEFAULT NULL')
  }
  const sessionColumns = database.prepare('PRAGMA table_info(session)').all() as Array<{ name: string }>
  if (!sessionColumns.some((column) => column.name === 'last_read_time')) {
    database.exec('ALTER TABLE session ADD COLUMN last_read_time INTEGER NOT NULL DEFAULT 0')
  }
  const messageColumns = database.prepare('PRAGMA table_info(message)').all() as Array<{ name: string }>
  if (!messageColumns.some((column) => column.name === 'recall_operator_name')) {
    database.exec('ALTER TABLE message ADD COLUMN recall_operator_name TEXT DEFAULT NULL')
  }
  const now = Date.now()
  if (previousVersion < 4) {
    // Versions before v4 can contain counters polluted by inbox replay. v3
    // introduced the watermark, but some databases were already marked v3
    // before the legacy counters were cleaned, so v4 resets them once.
    database.prepare('UPDATE session SET unread_count = 0, last_read_time = ?').run(now)
  } else {
    database.prepare(`
      UPDATE session SET last_read_time = ? WHERE last_read_time = 0 AND unread_count = 0
    `).run(now)
  }
  // Older databases indexed session activity time. Rebuild the index so
  // existing sessions use the same creation-time ordering as new databases.
  database.exec('DROP INDEX IF EXISTS idx_session_order; CREATE INDEX idx_session_order ON session (is_top DESC, created_time DESC);')
  // Recalled content must never survive locally, including rows written by
  // older client versions before content scrubbing was introduced.
  database.exec('UPDATE message SET content = NULL WHERE status = 1;')
  database.exec(`
    CREATE TABLE IF NOT EXISTS directory_sync_state (
      sync_key TEXT PRIMARY KEY NOT NULL,
      version TEXT NOT NULL,
      updated_time INTEGER NOT NULL
    )
  `)
  database.exec('PRAGMA user_version = 7')
}
