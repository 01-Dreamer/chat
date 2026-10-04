package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Message;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record MessageResponse(
        String id,
        String clientMessageId,
        String chatKey,
        String senderId,
        int chatType,
        String targetId,
        int messageType,
        String content,
        String referenceId,
        String replyMessageId,
        int status,
        String recallOperatorId,
        Long recalledTime,
        long createdTime,
        long updatedTime) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId().toString(),
                message.getClientMessageId(),
                message.getChatKey(),
                message.getSenderId().toString(),
                message.getChatType(),
                message.getTargetId().toString(),
                message.getMessageType(),
                message.getContent(),
                stringId(message.getReferenceId()),
                stringId(message.getReplyMessageId()),
                message.getStatus(),
                stringId(message.getRecallOperatorId()),
                message.getRecalledTime() == null ? null : toEpochMilli(message.getRecalledTime()),
                toEpochMilli(message.getCreatedTime()),
                toEpochMilli(message.getUpdatedTime()));
    }

    private static String stringId(Long value) {
        return value == null ? null : value.toString();
    }
}
