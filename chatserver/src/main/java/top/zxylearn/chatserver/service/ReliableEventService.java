package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.entity.UserInbox;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.EventMapper;
import top.zxylearn.chatserver.mapper.UserInboxMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.mapper.projection.UserInboxSequence;
import top.zxylearn.chatserver.mq.RealtimeEvent;
import top.zxylearn.chatserver.mq.RealtimeEventPublisher;

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
    private final IdentifierGenerator identifierGenerator;
    private final RealtimeEventPublisher realtimePublisher;

    public ReliableEventService(
            EventMapper eventMapper,
            UserInboxMapper inboxMapper,
            UserMapper userMapper,
            IdentifierGenerator identifierGenerator,
            RealtimeEventPublisher realtimePublisher) {
        this.eventMapper = eventMapper;
        this.inboxMapper = inboxMapper;
        this.userMapper = userMapper;
        this.identifierGenerator = identifierGenerator;
        this.realtimePublisher = realtimePublisher;
    }

    public EventDelivery append(
            int eventType,
            long referenceId,
            Collection<Long> recipientIds,
            LocalDateTime now) {
        lockUsers(recipientIds);
        return appendForLockedUsers(eventType, referenceId, recipientIds, now);
    }

    public EventDelivery appendForLockedUsers(
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

        List<Long> recipients = orderedUnique(recipientIds);
        Map<Long, Long> currentSequences = new LinkedHashMap<>();
        for (UserInboxSequence row : inboxMapper.selectMaxSequences(recipients)) {
            if (row.getUserId() != null && row.getSequence() != null) {
                currentSequences.put(row.getUserId(), row.getSequence());
            }
        }
        Map<Long, Long> sequences = new LinkedHashMap<>();
        List<UserInbox> inboxRows = new ArrayList<>(recipients.size());
        for (Long userId : recipients) {
            long nextSequence = currentSequences.getOrDefault(userId, 0L) + 1;
            UserInbox inbox = new UserInbox();
            inbox.setId(identifierGenerator.nextId(inbox).longValue());
            inbox.setUserId(userId);
            inbox.setEventId(event.getId());
            inbox.setSequence(nextSequence);
            inbox.setCreatedTime(now);
            inbox.setUpdatedTime(now);
            inboxRows.add(inbox);
            sequences.put(userId, nextSequence);
        }
        inboxMapper.insertBatch(inboxRows);
        if (eventType >= 2 && eventType <= 6 && !sequences.isEmpty()) {
            Runnable publish = () -> realtimePublisher.publish(RealtimeEvent.businessEvent(
                    "DIRECTORY_CHANGED",
                    sequences,
                    Map.of("eventType", eventType, "referenceId", Long.toString(referenceId))));
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish.run();
                    }
                });
            } else {
                publish.run();
            }
        }
        return new EventDelivery(event, sequences);
    }

    public void lockUsers(Collection<Long> userIds) {
        List<Long> userIdsToLock = orderedUnique(userIds);
        if (userIdsToLock.isEmpty() || userMapper.lockByIds(userIdsToLock).size() != userIdsToLock.size()) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.CONFLICT);
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
