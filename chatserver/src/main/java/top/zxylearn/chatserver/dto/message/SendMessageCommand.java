package top.zxylearn.chatserver.dto.message;

public record SendMessageCommand(
        long senderId,
        String clientMessageId,
        int chatType,
        String targetId,
        int messageType,
        String content,
        String referenceId,
        String replyMessageId) {
}
