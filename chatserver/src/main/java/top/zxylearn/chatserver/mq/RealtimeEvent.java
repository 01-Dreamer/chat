package top.zxylearn.chatserver.mq;

import java.util.Map;

public record RealtimeEvent(
        String type,
        Map<Long, Long> targetSequences,
        Object data,
        String errorCode,
        String errorMessage,
        String clientMessageId) {

    public static RealtimeEvent businessEvent(String type, Map<Long, Long> targets, Object data) {
        return new RealtimeEvent(type, targets, data, null, null, null);
    }

    public static RealtimeEvent sendFailed(long senderId, String clientMessageId, String code, String message) {
        return new RealtimeEvent("SEND_FAILED", Map.of(senderId, 0L), null, code, message, clientMessageId);
    }
}
