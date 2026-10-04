package top.zxylearn.chatserver.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.entity.UserInbox;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.EventMapper;
import top.zxylearn.chatserver.mapper.UserInboxMapper;
import top.zxylearn.chatserver.mapper.UserMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReliableEventService {

    private final EventMapper eventMapper;
    private final UserInboxMapper inboxMapper;
    private final UserMapper userMapper;

    public ReliableEventService(
            EventMapper eventMapper,
            UserInboxMapper inboxMapper,
            UserMapper userMapper) {
        this.eventMapper = eventMapper;
        this.inboxMapper = inboxMapper;
        this.userMapper = userMapper;
    }

    public EventDelivery append(
            int eventType,
            long referenceId,
            Collection<Long> recipientIds,
            LocalDateTime now) {
        Event event = new Event();
        event.setEventType(eventType);
        event.setReferenceId(referenceId);
        event.setCreatedTime(now);
        event.setUpdatedTime(now);
        eventMapper.insert(event);

        Map<Long, Long> sequences = new LinkedHashMap<>();
        for (Long userId : orderedUnique(recipientIds)) {
            if (userMapper.lockById(userId) == null) {
                throw new BusinessException("USER_NOT_FOUND", "事件接收用户不存在", HttpStatus.CONFLICT);
            }
            long nextSequence = inboxMapper.selectMaxSequence(userId) + 1;
            UserInbox inbox = new UserInbox();
            inbox.setUserId(userId);
            inbox.setEventId(event.getId());
            inbox.setSequence(nextSequence);
            inbox.setCreatedTime(now);
            inbox.setUpdatedTime(now);
            inboxMapper.insert(inbox);
            sequences.put(userId, nextSequence);
        }
        return new EventDelivery(event, sequences);
    }

    public void lockUsers(Collection<Long> userIds) {
        for (Long userId : orderedUnique(userIds)) {
            if (userMapper.lockById(userId) == null) {
                throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.CONFLICT);
            }
        }
    }

    private List<Long> orderedUnique(Collection<Long> ids) {
        List<Long> ordered = new ArrayList<>(new LinkedHashSet<>(ids));
        ordered.sort(Comparator.naturalOrder());
        return ordered;
    }

    public record EventDelivery(Event event, Map<Long, Long> sequences) {
    }
}
