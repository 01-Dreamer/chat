package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Message;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.entity.GroupMember;

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
        String recallOperatorName,
        Long recalledTime,
        long createdTime,
        long updatedTime,
        String senderUsername,
        String senderName,
        String senderAvatarUrl,
        long senderUpdatedTime,
        String groupMemberId,
        String groupMemberNickname,
        long groupMemberUpdatedTime) {

    public static MessageResponse from(Message message) {
        return from(message, null);
    }

    public static MessageResponse from(Message message, String recallOperatorName) {
        return from(message, recallOperatorName, null, null);
    }

    public static MessageResponse from(
            Message message,
            String recallOperatorName,
            User sender,
            GroupMember groupMember) {
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
                recallOperatorName,
                message.getRecalledTime() == null ? null : toEpochMilli(message.getRecalledTime()),
                toEpochMilli(message.getCreatedTime()),
                toEpochMilli(message.getUpdatedTime()),
                sender == null ? null : sender.getUsername(),
                sender == null ? null : sender.getNickname(),
                sender == null ? null : sender.getAvatarUrl(),
                sender == null ? 0 : toEpochMilli(sender.getUpdatedTime()),
                groupMember == null ? null : stringId(groupMember.getId()),
                groupMember == null ? null : groupMember.getNickname(),
                groupMember == null ? 0 : toEpochMilli(groupMember.getUpdatedTime()));
    }

    private static String stringId(Long value) {
        return value == null ? null : value.toString();
    }
}
