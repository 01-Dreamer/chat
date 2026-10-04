PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA user_version = 4;


-- =========================================================
-- 用户表
-- =========================================================

CREATE TABLE IF NOT EXISTS `user` (
    id TEXT NOT NULL,                                  -- 用户ID
    username TEXT DEFAULT NULL,                        -- 用户名
    nickname TEXT NOT NULL,                            -- 昵称
    avatar_url TEXT DEFAULT NULL,                      -- 头像URL
    avatar_local_path TEXT DEFAULT NULL,               -- 头像本地路径
    status INTEGER NOT NULL DEFAULT 1,                 -- 用户状态：0-禁用，1-正常
    created_time INTEGER NOT NULL,                     -- 创建时间，Unix毫秒时间戳
    updated_time INTEGER NOT NULL,                     -- 更新时间，Unix毫秒时间戳

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_user_updated_time
ON `user` (updated_time);


-- =========================================================
-- 好友关系表
-- =========================================================

CREATE TABLE IF NOT EXISTS friend (
    id TEXT NOT NULL,                                  -- 好友关系ID
    friend_id TEXT NOT NULL,                           -- 好友用户ID
    remark TEXT DEFAULT NULL,                          -- 好友备注
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id),
    UNIQUE (friend_id)
);

CREATE INDEX IF NOT EXISTS idx_friend_updated_time
ON friend (updated_time);


-- =========================================================
-- 好友申请表
-- =========================================================

CREATE TABLE IF NOT EXISTS friend_add_request (
    id TEXT NOT NULL,                                  -- 好友申请ID
    sender_id TEXT NOT NULL,                           -- 申请人用户ID
    receiver_id TEXT NOT NULL,                         -- 接收人用户ID
    message TEXT DEFAULT NULL,                         -- 好友申请备注
    status INTEGER NOT NULL DEFAULT 0,                 -- 状态：0-待处理，1-已同意，2-已拒绝
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_friend_request_time
ON friend_add_request (created_time DESC);


-- =========================================================
-- 群聊表
-- =========================================================

CREATE TABLE IF NOT EXISTS `group` (
    id TEXT NOT NULL,                                  -- 群聊ID
    name TEXT NOT NULL,                                -- 群聊名称
    avatar_url TEXT DEFAULT NULL,                      -- 群头像URL
    avatar_local_path TEXT DEFAULT NULL,               -- 群头像本地缓存路径
    owner_id TEXT NOT NULL,                            -- 群主用户ID
    status INTEGER NOT NULL DEFAULT 1,                 -- 群状态：0-已解散，1-正常
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_group_updated_time
ON `group` (updated_time);


-- =========================================================
-- 群成员表
-- =========================================================

CREATE TABLE IF NOT EXISTS group_member (
    id TEXT NOT NULL,                                  -- 群成员关系ID
    group_id TEXT NOT NULL,                            -- 群聊ID
    user_id TEXT NOT NULL,                             -- 用户ID
    role INTEGER NOT NULL DEFAULT 0,                   -- 角色：0-普通成员，1-管理员，2-群主
    nickname TEXT DEFAULT NULL,                        -- 群内昵称
    created_time INTEGER NOT NULL,                     -- 加入群聊时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id),
    UNIQUE (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_group_member_group
ON group_member (group_id);

CREATE INDEX IF NOT EXISTS idx_group_member_user
ON group_member (user_id);

CREATE INDEX IF NOT EXISTS idx_group_member_updated
ON group_member (group_id, updated_time);


-- =========================================================
-- 入群申请表
-- =========================================================

CREATE TABLE IF NOT EXISTS group_join_request (
    id TEXT NOT NULL,                                  -- 入群申请ID
    group_id TEXT NOT NULL,                            -- 群聊ID
    user_id TEXT NOT NULL,                             -- 申请用户ID
    message TEXT DEFAULT NULL,                         -- 申请备注
    status INTEGER NOT NULL DEFAULT 0,                 -- 状态：0-待处理，1-已同意，2-已拒绝
    reviewer_id TEXT DEFAULT NULL,                     -- 审核人用户ID
    created_time INTEGER NOT NULL,                     -- 申请时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_group_join_request_time
ON group_join_request (created_time DESC);


-- =========================================================
-- 用户群聊设置表
-- =========================================================

CREATE TABLE IF NOT EXISTS user_group_setting (
    id TEXT NOT NULL,                                  -- 用户群聊设置ID
    group_id TEXT NOT NULL,                            -- 群聊ID
    remark TEXT DEFAULT NULL,                          -- 用户对群聊的备注
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id),
    UNIQUE (group_id)
);


-- =========================================================
-- 会话表
-- =========================================================

CREATE TABLE IF NOT EXISTS `session` (
    id INTEGER PRIMARY KEY AUTOINCREMENT,              -- 本地会话ID
    chat_key TEXT NOT NULL,                            -- 聊天唯一标识
    chat_type INTEGER NOT NULL,                        -- 聊天类型：0-单聊，1-群聊
    target_id TEXT NOT NULL,                           -- 目标ID：单聊为对方用户ID，群聊为群ID
    last_message_id TEXT DEFAULT NULL,                 -- 最后一条消息ID
    unread_count INTEGER NOT NULL DEFAULT 0,           -- 未读消息数量
    last_read_time INTEGER NOT NULL DEFAULT 0,         -- 最近一次进入会话的时间
    is_top INTEGER NOT NULL DEFAULT 0,                 -- 是否置顶：0-否，1-是
    draft TEXT DEFAULT NULL,                           -- 输入框草稿
    last_active_time INTEGER NOT NULL,                 -- 会话最后活跃时间
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    UNIQUE (chat_key)
);

CREATE INDEX IF NOT EXISTS idx_session_order
ON `session` (is_top DESC, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_session_target
ON `session` (chat_type, target_id);


-- =========================================================
-- 消息表
-- =========================================================

CREATE TABLE IF NOT EXISTS `message` (
    local_id INTEGER PRIMARY KEY AUTOINCREMENT,        -- SQLite本地消息ID

    id TEXT DEFAULT NULL,                              -- 服务端消息ID
    client_message_id TEXT NOT NULL,                   -- 客户端消息唯一ID

    chat_key TEXT NOT NULL,                            -- 聊天标识
    sender_id TEXT NOT NULL,                           -- 发送者用户ID
    chat_type INTEGER NOT NULL,                        -- 聊天类型：0-单聊，1-群聊
    target_id TEXT NOT NULL,                           -- 目标ID：单聊为接收用户ID，群聊为群ID

    message_type INTEGER NOT NULL,                     -- 消息类型：0-文本，1-文件，2-红包，3-音视频通话提示
    content TEXT DEFAULT NULL,                         -- 文本消息内容
    reference_id TEXT DEFAULT NULL,                    -- 关联业务ID
    reply_message_id TEXT DEFAULT NULL,                -- 引用的消息ID

    status INTEGER NOT NULL DEFAULT 0,                 -- 消息状态：0-正常，1-已撤回
    recall_operator_id TEXT DEFAULT NULL,              -- 撤回操作者用户ID
    recalled_time INTEGER DEFAULT NULL,                -- 撤回时间

    send_status INTEGER NOT NULL DEFAULT 0,            -- 发送状态：0-发送中，1-发送成功，2-发送失败

    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    UNIQUE (id),
    UNIQUE (sender_id, client_message_id)
);

CREATE INDEX IF NOT EXISTS idx_message_chat_time
ON `message` (chat_key, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_message_sender
ON `message` (sender_id);

CREATE INDEX IF NOT EXISTS idx_message_reply
ON `message` (reply_message_id);


-- =========================================================
-- 文件资源表
-- =========================================================

CREATE TABLE IF NOT EXISTS file_resource (
    id TEXT NOT NULL,                                  -- 文件资源ID
    uploader_id TEXT NOT NULL,                         -- 上传用户ID
    resource_type INTEGER NOT NULL,                    -- 资源类型：0-图片，1-视频，2-语音，3-普通文件
    file_name TEXT NOT NULL,                           -- 原始文件名
    file_size INTEGER NOT NULL,                        -- 文件大小，单位字节
    mime_type TEXT NOT NULL,                           -- 文件MIME类型
    file_hash TEXT NOT NULL,                           -- 文件SHA-256哈希
    file_url TEXT DEFAULT NULL,                        -- 文件访问地址
    duration INTEGER DEFAULT NULL,                     -- 音频或视频时长，单位秒

    local_path TEXT DEFAULT NULL,                      -- 本地文件路径
    download_status INTEGER NOT NULL DEFAULT 0,        -- 下载状态：0-未下载，1-下载中，2-已下载，3-下载失败

    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id),
    UNIQUE (file_hash)
);

CREATE INDEX IF NOT EXISTS idx_file_resource_uploader
ON file_resource (uploader_id);

CREATE INDEX IF NOT EXISTS idx_file_download_status
ON file_resource (download_status);


-- =========================================================
-- 音视频通话记录表
-- =========================================================

CREATE TABLE IF NOT EXISTS call_record (
    id TEXT NOT NULL,                                  -- 通话记录ID
    caller_id TEXT NOT NULL,                           -- 发起者用户ID
    callee_id TEXT NOT NULL,                           -- 接收者用户ID
    call_type INTEGER NOT NULL,                        -- 通话类型：0-语音，1-视频
    status INTEGER NOT NULL DEFAULT 0,                 -- 状态：0-呼叫中，1-通话完成，2-已拒绝，3-未接听，4-已取消
    start_time INTEGER DEFAULT NULL,                   -- 通话开始时间
    end_time INTEGER DEFAULT NULL,                     -- 通话结束时间
    duration INTEGER NOT NULL DEFAULT 0,               -- 通话时长，单位秒
    created_time INTEGER NOT NULL,                     -- 呼叫发起时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_call_record_time
ON call_record (created_time DESC);


-- =========================================================
-- 红包表
-- =========================================================

CREATE TABLE IF NOT EXISTS red_packet (
    id TEXT NOT NULL,                                  -- 红包ID
    sender_id TEXT NOT NULL,                           -- 发送者用户ID
    chat_type INTEGER NOT NULL,                        -- 红包场景：0-单聊，1-群聊
    target_id TEXT NOT NULL,                           -- 目标ID：单聊为接收用户ID，群聊为群ID
    packet_type INTEGER NOT NULL,                      -- 红包类型：0-均等红包，1-随机红包

    total_amount TEXT NOT NULL,                        -- 红包总金额
    total_count INTEGER NOT NULL,                      -- 红包总份数
    remain_amount TEXT NOT NULL,                       -- 剩余金额
    remain_count INTEGER NOT NULL,                     -- 剩余份数

    message TEXT DEFAULT NULL,                         -- 红包祝福语
    status INTEGER NOT NULL DEFAULT 0,                 -- 红包状态：0-可领取，1-已抢完，2-已过期
    expire_time INTEGER NOT NULL,                      -- 过期时间

    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id)
);


-- =========================================================
-- 红包领取记录表
-- =========================================================

CREATE TABLE IF NOT EXISTS red_packet_receive (
    id TEXT NOT NULL,                                  -- 红包领取记录ID
    red_packet_id TEXT NOT NULL,                       -- 红包ID
    user_id TEXT NOT NULL,                             -- 领取用户ID
    amount TEXT NOT NULL,                              -- 领取金额
    created_time INTEGER NOT NULL,                     -- 领取时间

    PRIMARY KEY (id),
    UNIQUE (red_packet_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_red_packet_receive_packet
ON red_packet_receive (red_packet_id);


-- =========================================================
-- 通知表
-- =========================================================

CREATE TABLE IF NOT EXISTS notification (
    id TEXT NOT NULL,                                  -- 通知ID
    notification_type INTEGER NOT NULL,                -- 通知类型：0-好友，1-群聊，2-红包，3-系统
    reference_id TEXT DEFAULT NULL,                    -- 关联业务ID
    title TEXT DEFAULT NULL,                           -- 通知标题
    content TEXT DEFAULT NULL,                         -- 通知内容
    is_read INTEGER NOT NULL DEFAULT 0,                -- 是否已读：0-未读，1-已读
    read_time INTEGER DEFAULT NULL,                    -- 阅读时间
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_notification_read
ON notification (is_read);

CREATE INDEX IF NOT EXISTS idx_notification_time
ON notification (created_time DESC);


-- =========================================================
-- 用户信箱同步状态表
-- =========================================================

CREATE TABLE IF NOT EXISTS inbox_sync_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),             -- 固定为1，只保存一条记录
    last_sequence INTEGER NOT NULL DEFAULT 0,          -- 已同步到的用户信箱序列号
    updated_time INTEGER NOT NULL                      -- 更新时间
);

INSERT OR IGNORE INTO inbox_sync_state (
    id,
    last_sequence,
    updated_time
) VALUES (
    1,
    0,
    0
);


-- =========================================================
-- 文本翻译缓存表
-- =========================================================

CREATE TABLE IF NOT EXISTS translation_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,              -- 本地缓存ID
    text_hash TEXT NOT NULL,                           -- 原文本SHA-256哈希
    target_language TEXT NOT NULL,                     -- 目标语言
    translated_text TEXT NOT NULL,                     -- 翻译结果
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    UNIQUE (text_hash, target_language)
);

CREATE INDEX IF NOT EXISTS idx_translation_text_hash
ON translation_cache (text_hash);


-- =========================================================
-- 语音识别缓存表
-- =========================================================

CREATE TABLE IF NOT EXISTS asr_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,              -- 本地缓存ID
    file_hash TEXT NOT NULL,                           -- 音频文件SHA-256哈希
    recognized_text TEXT NOT NULL,                     -- 语音识别结果
    created_time INTEGER NOT NULL,                     -- 创建时间
    updated_time INTEGER NOT NULL,                     -- 更新时间

    UNIQUE (file_hash)
);
