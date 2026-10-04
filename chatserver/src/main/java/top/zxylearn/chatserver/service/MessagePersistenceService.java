package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.dto.message.SendMessageCommand;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.Message;
import top.zxylearn.chatserver.entity.RedPacket;
import top.zxylearn.chatserver.entity.CallRecord;
import top.zxylearn.chatserver.entity.FileResourceAccess;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.FileResourceMapper;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.MessageMapper;
import top.zxylearn.chatserver.mapper.RedPacketMapper;
import top.zxylearn.chatserver.mapper.CallRecordMapper;
import top.zxylearn.chatserver.mapper.FileResourceAccessMapper;
import top.zxylearn.chatserver.vo.MessageResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MessagePersistenceService {

    private static final int CHAT_DIRECT = 0;
    private static final int CHAT_GROUP = 1;
    private static final int MESSAGE_TEXT = 0;
    private static final int MESSAGE_RED_PACKET = 2;
    private static final int MESSAGE_FILE = 1;
    private static final int MESSAGE_RECALLED = 1;
    private static final int ROLE_ADMIN = 1;
    private static final int ROLE_OWNER = 2;
    private static final int EVENT_MESSAGE = 0;
    private static final int EVENT_RECALL = 1;

    private final MessageMapper messageMapper;
    private final FriendMapper friendMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final FileResourceMapper fileResourceMapper;
    private final ReliableEventService reliableEventService;
    private final RedPacketMapper redPacketMapper;
    private final CallRecordMapper callRecordMapper;
    private final FileResourceAccessMapper fileResourceAccessMapper;
    private final ChatAccessCache chatAccessCache;

    public MessagePersistenceService(
            MessageMapper messageMapper,
            FriendMapper friendMapper,
            GroupMapper groupMapper,
            GroupMemberMapper memberMapper,
            FileResourceMapper fileResourceMapper,
            RedPacketMapper redPacketMapper,
            CallRecordMapper callRecordMapper,
            FileResourceAccessMapper fileResourceAccessMapper,
            ReliableEventService reliableEventService,
            ChatAccessCache chatAccessCache) {
        this.messageMapper = messageMapper;
        this.friendMapper = friendMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.redPacketMapper = redPacketMapper;
        this.callRecordMapper = callRecordMapper;
        this.fileResourceAccessMapper = fileResourceAccessMapper;
        this.reliableEventService = reliableEventService;
        this.chatAccessCache = chatAccessCache;
    }

    @Transactional
    public PersistedMessage persist(SendMessageCommand command) {
        validateCommand(command);
        long targetId = parseId(command.targetId(), "INVALID_TARGET_ID", "消息目标ID格式不正确");
        List<Long> recipients = resolveRecipients(command.senderId(), command.chatType(), targetId);
        reliableEventService.lockUsers(recipients);

        Message existing = messageMapper.selectByClientMessageId(command.senderId(), command.clientMessageId());
        if (existing != null) {
            return new PersistedMessage(MessageResponse.from(existing), Map.of(command.senderId(), 0L), true);
        }

        Long referenceId = nullableId(command.referenceId(), "INVALID_REFERENCE_ID", "关联业务ID格式不正确");
        Long replyMessageId = nullableId(command.replyMessageId(), "INVALID_REPLY_MESSAGE_ID", "引用消息ID格式不正确");
        validateReferences(command.messageType(), referenceId, replyMessageId, command.chatType(), targetId, command.senderId());

        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setClientMessageId(command.clientMessageId());
        message.setChatType(command.chatType());
        message.setTargetId(targetId);
        message.setSenderId(command.senderId());
        message.setChatKey(chatKey(command.chatType(), command.senderId(), targetId));
        message.setMessageType(command.messageType());
        message.setContent(normalizeContent(command.content()));
        message.setReferenceId(referenceId);
        message.setReplyMessageId(replyMessageId);
        message.setStatus(0);
        message.setCreatedTime(now);
        message.setUpdatedTime(now);
        messageMapper.insert(message);
        ReliableEventService.EventDelivery delivery = reliableEventService.appendForLockedUsers(
                EVENT_MESSAGE, message.getId(), recipients, now);
        return new PersistedMessage(MessageResponse.from(message), delivery.sequences(), false);
    }

    @Transactional
    public PersistedMessage recall(long currentUserId, long messageId) {
        Message message = messageMapper.selectByIdForUpdate(messageId);
        if (message == null) {
            throw new BusinessException("MESSAGE_NOT_FOUND", "消息不存在", HttpStatus.NOT_FOUND);
        }
        if (message.getStatus() == MESSAGE_RECALLED) {
            return new PersistedMessage(MessageResponse.from(message), Map.of(currentUserId, 0L), true);
        }
        List<Long> recipients = resolveRecipients(message.getSenderId(), message.getChatType(), message.getTargetId());
        checkRecallPermission(currentUserId, message);
        LocalDateTime now = LocalDateTime.now();
        message.setStatus(MESSAGE_RECALLED);
        message.setRecallOperatorId(currentUserId);
        message.setRecalledTime(now);
        message.setUpdatedTime(now);
        messageMapper.updateById(message);
        ReliableEventService.EventDelivery delivery = reliableEventService.append(
                EVENT_RECALL, message.getId(), recipients, now);
        return new PersistedMessage(MessageResponse.from(message), delivery.sequences(), false);
    }

    public List<MessageResponse> history(long currentUserId, String chatKey, Long beforeId, int limit) {
        validateChatAccess(currentUserId, chatKey);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<Message> query = new LambdaQueryWrapper<Message>()
                .eq(Message::getChatKey, chatKey)
                .orderByDesc(Message::getId)
                .last("LIMIT " + safeLimit);
        if (beforeId != null) query.lt(Message::getId, beforeId);
        return messageMapper.selectList(query).stream().map(MessageResponse::from).toList();
    }

    private List<Long> resolveRecipients(long senderId, int chatType, long targetId) {
        if (chatType == CHAT_DIRECT) {
            Optional<Boolean> cached = chatAccessCache.getDirectAccess(senderId, targetId);
            boolean allowed;
            if (cached.isPresent()) {
                allowed = cached.get();
            } else {
                allowed = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                        .and(wrapper -> wrapper
                                .nested(pair -> pair.eq(Friend::getUserId, senderId).eq(Friend::getFriendId, targetId))
                                .or(pair -> pair.eq(Friend::getUserId, targetId).eq(Friend::getFriendId, senderId)))) == 2;
                chatAccessCache.cacheDirectAccess(senderId, targetId, allowed);
            }
            if (!allowed) {
                throw new BusinessException("NOT_FRIEND", "当前已经不是好友关系", HttpStatus.CONFLICT);
            }
            return senderId == targetId ? List.of(senderId) : orderedUnique(List.of(senderId, targetId));
        }
        if (chatType != CHAT_GROUP) {
            throw new BusinessException("INVALID_CHAT_TYPE", "聊天类型不正确", HttpStatus.BAD_REQUEST);
        }
        List<Long> recipients = chatAccessCache.getGroupRecipients(targetId).orElseGet(() -> {
            Group group = groupMapper.selectById(targetId);
            if (group == null || group.getStatus() == null || group.getStatus() != 1) {
                throw new BusinessException("GROUP_NOT_FOUND", "群聊不存在或已解散", HttpStatus.NOT_FOUND);
            }
            List<Long> memberIds = orderedUnique(memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupId, targetId))
                    .stream().map(GroupMember::getUserId).toList());
            chatAccessCache.cacheGroupRecipients(targetId, memberIds);
            return memberIds;
        });
        if (!recipients.contains(senderId)) {
            throw new BusinessException("GROUP_NOT_MEMBER", "你已不在该群聊中", HttpStatus.FORBIDDEN);
        }
        return recipients;
    }

    private void validateCommand(SendMessageCommand command) {
        if (command.clientMessageId() == null
                || !command.clientMessageId().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,55}$")) {
            throw new BusinessException("INVALID_CLIENT_MESSAGE_ID", "client_message_id 必须是 UUID", HttpStatus.BAD_REQUEST);
        }
        if (command.messageType() < 0 || command.messageType() > 3) {
            throw new BusinessException("INVALID_MESSAGE_TYPE", "消息类型不正确", HttpStatus.BAD_REQUEST);
        }
        if (command.messageType() == MESSAGE_TEXT) {
            String content = normalizeContent(command.content());
            if (content == null) {
                throw new BusinessException("MESSAGE_CONTENT_REQUIRED", "文本消息不能为空", HttpStatus.BAD_REQUEST);
            }
            if (content.length() > 20_000) {
                throw new BusinessException("MESSAGE_CONTENT_TOO_LONG", "文本消息不能超过20000个字符", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void validateReferences(
            int messageType,
            Long referenceId,
            Long replyMessageId,
            int chatType,
            long targetId,
            long senderId) {
        if (messageType == MESSAGE_FILE) {
            if (referenceId == null) {
                throw new BusinessException("FILE_REFERENCE_REQUIRED", "文件消息缺少资源ID", HttpStatus.BAD_REQUEST);
            }
            FileResource resource = fileResourceMapper.selectById(referenceId);
            if (resource == null || fileResourceAccessMapper.selectCount(new LambdaQueryWrapper<FileResourceAccess>()
                    .eq(FileResourceAccess::getResourceId, referenceId)
                    .eq(FileResourceAccess::getUserId, senderId)) == 0) {
                throw new BusinessException("FILE_NOT_FOUND", "文件资源不存在", HttpStatus.NOT_FOUND);
            }
        }
        if (messageType == 2) {
            if (referenceId == null) throw new BusinessException("RED_PACKET_REFERENCE_REQUIRED", "红包消息缺少红包ID", HttpStatus.BAD_REQUEST);
            RedPacket packet = redPacketMapper.selectById(referenceId);
            if (packet == null || packet.getSenderId() != senderId || packet.getChatType() != chatType || packet.getTargetId() != targetId) {
                throw new BusinessException("RED_PACKET_REFERENCE_INVALID", "红包不属于当前会话", HttpStatus.CONFLICT);
            }
        }
        if (messageType == 3) {
            if (referenceId == null) throw new BusinessException("CALL_REFERENCE_REQUIRED", "通话提示缺少通话记录ID", HttpStatus.BAD_REQUEST);
            CallRecord call = callRecordMapper.selectById(referenceId);
            boolean participantsMatch = call != null
                    && ((call.getCallerId() == senderId && call.getCalleeId() == targetId)
                    || (call.getCalleeId() == senderId && call.getCallerId() == targetId));
            if (!participantsMatch) throw new BusinessException("CALL_REFERENCE_INVALID", "通话记录不属于当前会话", HttpStatus.CONFLICT);
        }
        if (replyMessageId != null) {
            Message reply = messageMapper.selectById(replyMessageId);
            String expectedChatKey = chatKey(chatType, senderId, targetId);
            if (reply == null || !expectedChatKey.equals(reply.getChatKey())) {
                throw new BusinessException("REPLY_MESSAGE_INVALID", "引用消息不属于当前会话", HttpStatus.CONFLICT);
            }
        }
    }

    private void checkRecallPermission(long currentUserId, Message message) {
        if (message.getMessageType() == MESSAGE_RED_PACKET) {
            throw new BusinessException("RED_PACKET_RECALL_FORBIDDEN", "红包消息不能撤回", HttpStatus.CONFLICT);
        }
        if (message.getSenderId() == currentUserId) {
            return;
        }
        if (message.getChatType() != CHAT_GROUP) {
            throw new BusinessException("MESSAGE_RECALL_FORBIDDEN", "只能撤回自己发送的消息", HttpStatus.FORBIDDEN);
        }
        GroupMember operator = findMember(message.getTargetId(), currentUserId);
        GroupMember sender = findMember(message.getTargetId(), message.getSenderId());
        if (operator == null || sender == null) throw notGroupMember();
        if (operator.getRole() == ROLE_OWNER && sender.getRole() < ROLE_OWNER) return;
        if (operator.getRole() == ROLE_ADMIN && sender.getRole() < ROLE_ADMIN) return;
        throw new BusinessException("MESSAGE_RECALL_FORBIDDEN", "没有管理撤回该消息的权限", HttpStatus.FORBIDDEN);
    }

    private void validateChatAccess(long currentUserId, String value) {
        if (value == null) throw new BusinessException("INVALID_CHAT_KEY", "会话标识不正确", HttpStatus.BAD_REQUEST);
        if (value.startsWith("P:")) {
            String[] parts = value.split(":", -1);
            if (parts.length != 3) throw invalidChatKey();
            long first = parseId(parts[1], "INVALID_CHAT_KEY", "会话标识不正确");
            long second = parseId(parts[2], "INVALID_CHAT_KEY", "会话标识不正确");
            if (currentUserId != first && currentUserId != second) {
                throw new BusinessException("FORBIDDEN", "无权读取该会话", HttpStatus.FORBIDDEN);
            }
            return;
        }
        if (value.startsWith("G:")) {
            long groupId = parseId(value.substring(2), "INVALID_CHAT_KEY", "会话标识不正确");
            resolveRecipients(currentUserId, CHAT_GROUP, groupId);
            return;
        }
        throw invalidChatKey();
    }

    private GroupMember findMember(long groupId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private BusinessException invalidChatKey() {
        return new BusinessException("INVALID_CHAT_KEY", "会话标识不正确", HttpStatus.BAD_REQUEST);
    }

    private BusinessException notGroupMember() {
        return new BusinessException("GROUP_NOT_MEMBER", "你已不在该群聊中", HttpStatus.FORBIDDEN);
    }

    private String chatKey(int chatType, long senderId, long targetId) {
        if (chatType == CHAT_GROUP) return "G:" + targetId;
        long min = Math.min(senderId, targetId);
        long max = Math.max(senderId, targetId);
        return "P:" + min + ":" + max;
    }

    private Long nullableId(String value, String code, String message) {
        if (value == null || value.isBlank()) return null;
        return parseId(value, code, message);
    }

    private long parseId(String value, String code, String message) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (RuntimeException exception) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeContent(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<Long> orderedUnique(List<Long> userIds) {
        Set<Long> unique = new LinkedHashSet<>(userIds);
        List<Long> result = new ArrayList<>(unique);
        result.sort(Long::compareTo);
        return result;
    }

    public record PersistedMessage(
            MessageResponse message,
            Map<Long, Long> targetSequences,
            boolean duplicate) {
    }
}
