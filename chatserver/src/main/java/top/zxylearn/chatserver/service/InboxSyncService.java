package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.entity.FriendAddRequest;
import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.entity.GroupJoinRequest;
import top.zxylearn.chatserver.entity.Message;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.entity.UserInbox;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.mapper.EventMapper;
import top.zxylearn.chatserver.mapper.FriendAddRequestMapper;
import top.zxylearn.chatserver.mapper.GroupJoinRequestMapper;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.mapper.MessageMapper;
import top.zxylearn.chatserver.mapper.UserInboxMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.vo.FriendRequestResponse;
import top.zxylearn.chatserver.vo.GroupJoinRequestResponse;
import top.zxylearn.chatserver.vo.InboxEventResponse;
import top.zxylearn.chatserver.vo.InboxSyncResponse;
import top.zxylearn.chatserver.vo.MessageResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

@Service
public class InboxSyncService {

    private final UserInboxMapper inboxMapper;
    private final EventMapper eventMapper;
    private final MessageMapper messageMapper;
    private final FriendAddRequestMapper friendRequestMapper;
    private final GroupJoinRequestMapper groupRequestMapper;
    private final GroupMapper groupMapper;
    private final UserMapper userMapper;
    private final GroupMemberMapper groupMemberMapper;

    public InboxSyncService(
            UserInboxMapper inboxMapper,
            EventMapper eventMapper,
            MessageMapper messageMapper,
            FriendAddRequestMapper friendRequestMapper,
            GroupJoinRequestMapper groupRequestMapper,
            GroupMapper groupMapper,
            UserMapper userMapper,
            GroupMemberMapper groupMemberMapper) {
        this.inboxMapper = inboxMapper;
        this.eventMapper = eventMapper;
        this.messageMapper = messageMapper;
        this.friendRequestMapper = friendRequestMapper;
        this.groupRequestMapper = groupRequestMapper;
        this.groupMapper = groupMapper;
        this.userMapper = userMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    public InboxSyncResponse sync(long userId, long afterSequence, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        List<UserInbox> rows = inboxMapper.selectList(new LambdaQueryWrapper<UserInbox>()
                .eq(UserInbox::getUserId, userId)
                .gt(UserInbox::getSequence, Math.max(0, afterSequence))
                .orderByAsc(UserInbox::getSequence)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = new ArrayList<>(rows.subList(0, limit));
        List<Long> eventIds = rows.stream().map(UserInbox::getEventId).distinct().toList();
        Map<Long, Event> events = eventIds.isEmpty()
                ? Map.of()
                : mapById(eventMapper.selectBatchIds(eventIds));
        List<Long> messageIds = events.values().stream()
                .filter(event -> event.getEventType() == 0 || event.getEventType() == 1)
                .map(Event::getReferenceId)
                .distinct()
                .toList();
        Map<Long, Message> messages = messageIds.isEmpty()
                ? Map.of()
                : mapById(messageMapper.selectBatchIds(messageIds));
        List<Long> recallOperatorIds = messages.values().stream()
                .map(Message::getRecallOperatorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> recallOperators = recallOperatorIds.isEmpty()
                ? Map.of()
                : mapById(userMapper.selectByIds(recallOperatorIds));
        List<InboxEventResponse> items = new ArrayList<>(rows.size());
        for (UserInbox inbox : rows) {
            Event event = events.get(inbox.getEventId());
            if (event == null) {
                items.add(new InboxEventResponse(
                        inbox.getId().toString(), inbox.getSequence(), inbox.getEventId().toString(),
                        -1, "0", Map.of("available", false), toEpochMilli(inbox.getCreatedTime())));
                continue;
            }
            items.add(new InboxEventResponse(
                    inbox.getId().toString(),
                    inbox.getSequence(),
                    event.getId().toString(),
                    event.getEventType(),
                    event.getReferenceId().toString(),
                    resolvePayload(userId, event, messages, recallOperators),
                    toEpochMilli(event.getCreatedTime())));
        }
        long lastSequence = items.isEmpty() ? Math.max(0, afterSequence) : items.get(items.size() - 1).sequence();
        return new InboxSyncResponse(items, lastSequence, hasMore);
    }

    private Object resolvePayload(
            long userId,
            Event event,
            Map<Long, Message> messages,
            Map<Long, User> recallOperators) {
        if (event.getEventType() == 0 || event.getEventType() == 1) {
            Message message = messages.get(event.getReferenceId());
            if (message != null) {
                User operator = recallOperators.get(message.getRecallOperatorId());
                User sender = userMapper.selectById(message.getSenderId());
                GroupMember senderMember = message.getChatType() == 1
                        ? groupMemberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                                .eq(GroupMember::getGroupId, message.getTargetId())
                                .eq(GroupMember::getUserId, message.getSenderId())
                                .last("LIMIT 1"))
                        : null;
                return MessageResponse.from(
                        message,
                        operator == null ? null : operator.getNickname(),
                        sender,
                        senderMember);
            }
        } else if (event.getEventType() == 2 || event.getEventType() == 3) {
            FriendAddRequest request = friendRequestMapper.selectById(event.getReferenceId());
            if (request != null) {
                long otherUserId = request.getSenderId() == userId ? request.getReceiverId() : request.getSenderId();
                User otherUser = userMapper.selectById(otherUserId);
                if (otherUser != null) return FriendRequestResponse.from(request, otherUser, userId);
            }
        } else if (event.getEventType() == 4 || event.getEventType() == 5) {
            GroupJoinRequest request = groupRequestMapper.selectById(event.getReferenceId());
            if (request != null) {
                Group group = groupMapper.selectById(request.getGroupId());
                User applicant = userMapper.selectById(request.getUserId());
                if (group != null && applicant != null) return GroupJoinRequestResponse.from(request, group, applicant);
            }
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("referenceId", event.getReferenceId().toString());
        fallback.put("available", false);
        return fallback;
    }

    private <T extends top.zxylearn.chatserver.entity.BaseEntity> Map<Long, T> mapById(Collection<T> values) {
        Map<Long, T> result = new HashMap<>();
        for (T value : values) result.put(value.getId(), value);
        return result;
    }
}
