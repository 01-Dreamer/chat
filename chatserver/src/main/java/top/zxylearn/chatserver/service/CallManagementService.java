package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.entity.CallRecord;
import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.CallRecordMapper;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mq.RealtimeEvent;
import top.zxylearn.chatserver.mq.RealtimeEventPublisher;
import top.zxylearn.chatserver.vo.CallRecordResponse;
import top.zxylearn.chatserver.dto.message.SendMessageCommand;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CallManagementService {
    private final CallRecordMapper callMapper;
    private final FriendMapper friendMapper;
    private final RealtimeEventPublisher publisher;
    private final String stunUrls;
    private final String turnUrls;
    private final String turnUsername;
    private final String turnCredential;
    private final MessagePersistenceService messagePersistenceService;

    public CallManagementService(
            CallRecordMapper callMapper,
            FriendMapper friendMapper,
            RealtimeEventPublisher publisher,
            @Value("${chat.webrtc.stun-urls}") String stunUrls,
            @Value("${chat.webrtc.turn-urls}") String turnUrls,
            @Value("${chat.webrtc.turn-username}") String turnUsername,
            @Value("${chat.webrtc.turn-credential}") String turnCredential,
            MessagePersistenceService messagePersistenceService) {
        this.callMapper = callMapper;
        this.friendMapper = friendMapper;
        this.publisher = publisher;
        this.stunUrls = stunUrls;
        this.turnUrls = turnUrls;
        this.turnUsername = turnUsername;
        this.turnCredential = turnCredential;
        this.messagePersistenceService = messagePersistenceService;
    }

    @Transactional
    public CallRecordResponse create(long callerId, long calleeId, int callType) {
        if (callerId == calleeId) throw new BusinessException("CANNOT_CALL_SELF", "不能呼叫自己", HttpStatus.BAD_REQUEST);
        requireFriends(callerId, calleeId);
        LocalDateTime now = LocalDateTime.now();
        CallRecord call = new CallRecord();
        call.setCallerId(callerId);
        call.setCalleeId(calleeId);
        call.setCallType(callType);
        call.setStatus(0);
        call.setDuration(0);
        call.setCreatedTime(now);
        call.setUpdatedTime(now);
        callMapper.insert(call);
        CallRecordResponse response = CallRecordResponse.from(call);
        afterCommit(() -> publisher.publish(RealtimeEvent.businessEvent("CALL_INVITE", Map.of(calleeId, 0L), response)));
        return response;
    }

    @Transactional
    public CallRecordResponse update(long userId, long callId, String action) {
        CallRecord call = requireParticipantForUpdate(userId, callId);
        LocalDateTime now = LocalDateTime.now();
        if ("accept".equals(action)) {
            if (userId != call.getCalleeId() || call.getStatus() != 0 || call.getStartTime() != null) throw invalidState();
            call.setStartTime(now);
        } else {
            if (call.getEndTime() != null || call.getStatus() != 0) throw invalidState();
            if ("complete".equals(action) && call.getStartTime() == null) throw invalidState();
            if (("reject".equals(action) || "cancel".equals(action) || "missed".equals(action))
                    && call.getStartTime() != null) throw invalidState();
            if ("reject".equals(action) && userId != call.getCalleeId()) throw invalidState();
            if ("cancel".equals(action) && userId != call.getCallerId()) throw invalidState();
            call.setEndTime(now);
            call.setStatus(switch (action) {
                case "complete" -> 1;
                case "reject" -> 2;
                case "missed" -> 3;
                case "cancel" -> 4;
                default -> throw invalidState();
            });
            if (call.getStartTime() != null) {
                call.setDuration(Math.toIntExact(Math.max(0, Duration.between(call.getStartTime(), now).toSeconds())));
            }
        }
        call.setUpdatedTime(now);
        callMapper.updateById(call);
        long other = userId == call.getCallerId() ? call.getCalleeId() : call.getCallerId();
        CallRecordResponse response = CallRecordResponse.from(call);
        MessagePersistenceService.PersistedMessage prompt = null;
        if (!"accept".equals(action)) {
            String content = switch (action) {
                case "complete" -> call.getCallType() == 1 ? "视频通话已结束" : "语音通话已结束";
                case "reject" -> "通话已拒绝";
                case "missed" -> "未接通话";
                case "cancel" -> "通话已取消";
                default -> "通话已结束";
            };
            prompt = messagePersistenceService.persist(new SendMessageCommand(
                    userId, UUID.randomUUID().toString(), 0, Long.toString(other), 3,
                    content, Long.toString(callId), null));
        }
        MessagePersistenceService.PersistedMessage finalPrompt = prompt;
        afterCommit(() -> {
            publisher.publish(RealtimeEvent.businessEvent("CALL_STATUS", Map.of(other, 0L), response));
            if (finalPrompt != null) publisher.publish(RealtimeEvent.businessEvent(
                    "MESSAGE", finalPrompt.targetSequences(), finalPrompt.message()));
        });
        return response;
    }

    public void relaySignal(long userId, long callId, long targetUserId, String signalType, Object payload) {
        CallRecord call = callMapper.selectById(callId);
        if (call == null || !isParticipant(call, userId) || !isParticipant(call, targetUserId) || userId == targetUserId) {
            throw new BusinessException("CALL_ACCESS_DENIED", "无权发送该通话信令", HttpStatus.FORBIDDEN);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("callId", Long.toString(callId));
        data.put("fromUserId", Long.toString(userId));
        data.put("signalType", signalType);
        data.put("payload", payload);
        publisher.publish(RealtimeEvent.businessEvent("WEBRTC_SIGNAL", Map.of(targetUserId, 0L), data));
    }

    public Map<String, Object> iceServers() {
        java.util.List<Map<String, String>> servers = new java.util.ArrayList<>();
        if (!stunUrls.isBlank()) servers.add(Map.of("urls", stunUrls));
        if (!turnUrls.isBlank()) servers.add(Map.of(
                "urls", turnUrls, "username", turnUsername, "credential", turnCredential));
        return Map.of("iceServers", servers);
    }

    private CallRecord requireParticipantForUpdate(long userId, long callId) {
        CallRecord call = callMapper.selectByIdForUpdate(callId);
        if (call == null) throw new BusinessException("CALL_NOT_FOUND", "通话记录不存在", HttpStatus.NOT_FOUND);
        if (!isParticipant(call, userId)) throw new BusinessException("CALL_ACCESS_DENIED", "无权操作该通话", HttpStatus.FORBIDDEN);
        return call;
    }

    private boolean isParticipant(CallRecord call, long userId) {
        return call.getCallerId() == userId || call.getCalleeId() == userId;
    }

    private void requireFriends(long first, long second) {
        if (friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .and(wrapper -> wrapper
                        .nested(pair -> pair.eq(Friend::getUserId, first).eq(Friend::getFriendId, second))
                        .or(pair -> pair.eq(Friend::getUserId, second).eq(Friend::getFriendId, first)))) != 2) {
            throw new BusinessException("NOT_FRIEND", "当前已经不是好友关系", HttpStatus.CONFLICT);
        }
    }

    private BusinessException invalidState() {
        return new BusinessException("CALL_STATE_INVALID", "通话状态不允许该操作", HttpStatus.CONFLICT);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
