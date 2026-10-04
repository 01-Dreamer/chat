package top.zxylearn.chatserver.vo;

public record InboxEventResponse(
        String inboxId,
        long sequence,
        String eventId,
        int eventType,
        String referenceId,
        Object data,
        long createdTime) {
}
