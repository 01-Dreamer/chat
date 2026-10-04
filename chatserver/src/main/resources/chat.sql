CREATE TABLE `user` (
    id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(32) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态：0-禁用，1-正常',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';

CREATE TABLE friend (
    id BIGINT NOT NULL COMMENT '好友关系ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    friend_id BIGINT NOT NULL COMMENT '好友用户ID',
    remark VARCHAR(64) DEFAULT NULL COMMENT '好友备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_friend (user_id, friend_id),
    KEY idx_friend_id (friend_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='好友关系表';

CREATE TABLE friend_add_request (
    id BIGINT NOT NULL COMMENT '好友申请ID',
    sender_id BIGINT NOT NULL COMMENT '申请人用户ID',
    receiver_id BIGINT NOT NULL COMMENT '接收人用户ID',
    message VARCHAR(255) DEFAULT NULL COMMENT '好友申请备注',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_receiver_status (receiver_id, status),
    KEY idx_sender_id (sender_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='好友申请表';

CREATE TABLE friend_delete_record (
    id BIGINT NOT NULL COMMENT '好友删除记录ID',
    operator_id BIGINT NOT NULL COMMENT '删除操作人用户ID',
    friend_id BIGINT NOT NULL COMMENT '被删除好友用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_operator_id (operator_id),
    KEY idx_friend_id (friend_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='好友删除记录表';

CREATE TABLE `group` (
    id BIGINT NOT NULL COMMENT '群聊ID',
    name VARCHAR(64) NOT NULL COMMENT '群聊名称',
    avatar_url VARCHAR(512) DEFAULT NULL COMMENT '群头像URL',
    owner_id BIGINT NOT NULL COMMENT '群主用户ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '群状态：0-已解散，1-正常',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_owner_id (owner_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='群聊表';

CREATE TABLE user_group_setting (
    id BIGINT NOT NULL COMMENT '用户群聊设置ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    group_id BIGINT NOT NULL COMMENT '群聊ID',
    remark VARCHAR(64) DEFAULT NULL COMMENT '用户对群聊的备注',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_group (user_id, group_id),
    KEY idx_group_id (group_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户群聊设置表';

CREATE TABLE group_member (
    id BIGINT NOT NULL COMMENT '群成员关系ID',
    group_id BIGINT NOT NULL COMMENT '群聊ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通成员，1-管理员，2-群主',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '群内昵称',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入群聊时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_group_user (group_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='群成员表';

CREATE TABLE group_join_request (
    id BIGINT NOT NULL COMMENT '入群申请ID',
    group_id BIGINT NOT NULL COMMENT '群聊ID',
    user_id BIGINT NOT NULL COMMENT '申请用户ID',
    message VARCHAR(255) DEFAULT NULL COMMENT '申请备注',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
    reviewer_id BIGINT DEFAULT NULL COMMENT '审核人用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_group_status (group_id, status),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='群聊加入申请表';

CREATE TABLE group_leave_record (
    id BIGINT NOT NULL COMMENT '群成员退出记录ID',
    group_id BIGINT NOT NULL COMMENT '群聊ID',
    user_id BIGINT NOT NULL COMMENT '退出群聊的用户ID',
    operator_id BIGINT NOT NULL COMMENT '操作人用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_group_id (group_id),
    KEY idx_user_id (user_id),
    KEY idx_operator_id (operator_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='群成员退出记录表';

CREATE TABLE `message` (
    id BIGINT NOT NULL COMMENT '消息ID',
    client_message_id VARCHAR(64) NOT NULL COMMENT '客户端消息唯一ID',
    chat_key VARCHAR(64) NOT NULL COMMENT '聊天标识',
    sender_id BIGINT NOT NULL COMMENT '发送者用户ID',
    chat_type TINYINT NOT NULL COMMENT '聊天类型：0-单聊，1-群聊',
    target_id BIGINT NOT NULL COMMENT '目标ID：单聊为接收用户ID，群聊为群ID',
    message_type TINYINT NOT NULL COMMENT '消息类型：0-文本，1-文件，2-红包，3-音视频通话提示',
    content TEXT DEFAULT NULL COMMENT '文本消息内容',
    reference_id BIGINT DEFAULT NULL COMMENT '关联业务ID',
    reply_message_id BIGINT DEFAULT NULL COMMENT '引用的消息ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '消息状态：0-正常，1-已撤回',
    recall_operator_id BIGINT DEFAULT NULL COMMENT '撤回操作者用户ID',
    recalled_time DATETIME DEFAULT NULL COMMENT '撤回时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sender_client_msg (sender_id, client_message_id),
    KEY idx_chat_key_id (chat_key, id),
    KEY idx_sender_id (sender_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='消息表';

CREATE TABLE file_resource (
    id BIGINT NOT NULL COMMENT '文件资源ID',
    uploader_id BIGINT NOT NULL COMMENT '上传用户ID',
    resource_type TINYINT NOT NULL COMMENT '资源类型：0-图片，1-视频，2-语音，3-普通文件',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
    mime_type VARCHAR(128) NOT NULL COMMENT '文件MIME类型',
    file_hash CHAR(64) NOT NULL COMMENT '文件SHA-256哈希',
    file_url VARCHAR(1024) DEFAULT NULL COMMENT '文件访问地址',
    duration INT DEFAULT NULL COMMENT '音频或视频时长，单位秒',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_file_hash (file_hash),
    KEY idx_uploader_id (uploader_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='文件资源表';

CREATE TABLE file_resource_access (
    id BIGINT NOT NULL COMMENT '资源授权ID',
    resource_id BIGINT NOT NULL COMMENT '文件资源ID',
    user_id BIGINT NOT NULL COMMENT '获授权用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_user (resource_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='文件资源用户授权表';

CREATE TABLE call_record (
    id BIGINT NOT NULL COMMENT '通话记录ID',
    caller_id BIGINT NOT NULL COMMENT '发起者用户ID',
    callee_id BIGINT NOT NULL COMMENT '接收者用户ID',
    call_type TINYINT NOT NULL COMMENT '通话类型：0-语音，1-视频',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '通话状态：0-呼叫中，1-通话完成，2-已拒绝，3-未接听，4-已取消',
    start_time DATETIME DEFAULT NULL COMMENT '通话开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '通话结束时间',
    duration INT NOT NULL DEFAULT 0 COMMENT '通话时长，单位秒',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '呼叫发起时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_caller_id (caller_id),
    KEY idx_callee_id (callee_id),
    KEY idx_created_time (created_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='音视频通话记录表';

CREATE TABLE `account` (
    id BIGINT NOT NULL COMMENT '账户ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '账户余额',
    pay_password_hash VARCHAR(255) DEFAULT NULL COMMENT '支付密码哈希',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '账户状态：0-禁用，1-正常',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户账户表';

CREATE TABLE account_transaction (
    id BIGINT NOT NULL COMMENT '资金流水ID',
    client_transaction_id VARCHAR(64) DEFAULT NULL COMMENT '客户端幂等ID',
    from_user_id BIGINT DEFAULT NULL COMMENT '出账用户ID',
    to_user_id BIGINT DEFAULT NULL COMMENT '入账用户ID',
    transaction_type TINYINT NOT NULL COMMENT '类型：0-转账，1-发红包，2-领红包，3-红包退款',
    amount DECIMAL(12,2) NOT NULL COMMENT '金额',
    reference_id BIGINT DEFAULT NULL COMMENT '关联业务ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-处理中，1-成功，2-失败',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_from_client (from_user_id, client_transaction_id),
    KEY idx_to_user_time (to_user_id, created_time),
    KEY idx_reference (reference_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='账户资金流水表';

CREATE TABLE red_packet (
    id BIGINT NOT NULL COMMENT '红包ID',
    sender_id BIGINT NOT NULL COMMENT '发送者用户ID',
    chat_type TINYINT NOT NULL COMMENT '红包场景：0-单聊，1-群聊',
    target_id BIGINT NOT NULL COMMENT '目标ID：单聊为接收用户ID，群聊为群ID',
    packet_type TINYINT NOT NULL COMMENT '红包类型：0-均等红包，1-随机红包',
    total_amount DECIMAL(12,2) NOT NULL COMMENT '红包总金额',
    total_count INT NOT NULL COMMENT '红包总份数',
    remain_amount DECIMAL(12,2) NOT NULL COMMENT '剩余金额',
    remain_count INT NOT NULL COMMENT '剩余份数',
    message VARCHAR(128) DEFAULT NULL COMMENT '红包祝福语',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '红包状态：0-可领取，1-已抢完，2-已过期',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_sender_id (sender_id),
    KEY idx_target (chat_type, target_id),
    KEY idx_status_expire (status, expire_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='红包表';

CREATE TABLE red_packet_receive (
    id BIGINT NOT NULL COMMENT '红包领取记录ID',
    red_packet_id BIGINT NOT NULL COMMENT '红包ID',
    user_id BIGINT NOT NULL COMMENT '领取用户ID',
    amount DECIMAL(12,2) NOT NULL COMMENT '领取金额',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_red_packet_user (red_packet_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='红包领取记录表';

CREATE TABLE `notification` (
    id BIGINT NOT NULL COMMENT '通知ID',
    user_id BIGINT NOT NULL COMMENT '接收通知的用户ID',
    notification_type TINYINT NOT NULL COMMENT '通知类型：0-好友通知，1-群聊通知，2-红包通知，3-系统通知',
    reference_id BIGINT DEFAULT NULL COMMENT '关联业务ID',
    title VARCHAR(128) DEFAULT NULL COMMENT '通知标题',
    content VARCHAR(512) DEFAULT NULL COMMENT '通知内容',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    read_time DATETIME DEFAULT NULL COMMENT '阅读时间',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_user_read (user_id, is_read),
    KEY idx_user_time (user_id, created_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='通知表';

CREATE TABLE `event` (
    id BIGINT NOT NULL COMMENT '事件ID',
    event_type TINYINT NOT NULL COMMENT '事件类型：0-消息发送，1-消息撤回，2-好友申请，3-好友申请处理，4-入群申请，5-入群申请处理',
    reference_id BIGINT NOT NULL COMMENT '关联业务记录ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_type_reference (event_type, reference_id),
    KEY idx_created_time (created_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='系统事件表';

CREATE TABLE user_inbox (
    id BIGINT NOT NULL COMMENT '用户信箱记录ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    event_id BIGINT NOT NULL COMMENT '事件ID',
    sequence BIGINT NOT NULL COMMENT '用户信箱序列号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_event (user_id, event_id),
    UNIQUE KEY uk_user_sequence (user_id, sequence),
    KEY idx_event_id (event_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户事件信箱表';
