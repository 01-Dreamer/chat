package top.zxylearn.chatserver.dto.message;

import java.util.List;

public record SendMessageCommand(
        long senderId,
        String clientMessageId,
        int chatType,
        String targetId,
        int messageType,
        String content,
        String referenceId,
        String replyMessageId,
        Long messageId,
        Long acceptedTime,
        List<Long> previewRecipientIds,
        String previewChatKey) {

    public SendMessageCommand(
            long senderId,
            String clientMessageId,
            int chatType,
            String targetId,
            int messageType,
            String content,
            String referenceId,
            String replyMessageId) {
        this(senderId, clientMessageId, chatType, targetId, messageType, content,
                referenceId, replyMessageId, null, null, List.of(), null);
    }

    public SendMessageCommand withFastDelivery(
            long resolvedMessageId,
            long resolvedAcceptedTime,
            List<Long> recipientIds,
            String chatKey) {
        return new SendMessageCommand(
                senderId, clientMessageId, chatType, targetId, messageType, content,
                referenceId, replyMessageId, resolvedMessageId, resolvedAcceptedTime,
                List.copyOf(recipientIds), chatKey);
    }
}
