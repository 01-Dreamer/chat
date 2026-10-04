package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Notification;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record NotificationResponse(
        String id, int notificationType, String referenceId, String title, String content,
        boolean read, Long readTime, long createdTime, long updatedTime) {
    public static NotificationResponse from(Notification value) {
        return new NotificationResponse(
                value.getId().toString(), value.getNotificationType(),
                value.getReferenceId() == null ? null : value.getReferenceId().toString(),
                value.getTitle(), value.getContent(), value.getIsRead() == 1,
                value.getReadTime() == null ? null : toEpochMilli(value.getReadTime()),
                toEpochMilli(value.getCreatedTime()), toEpochMilli(value.getUpdatedTime()));
    }
}
