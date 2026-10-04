package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.RedPacket;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record RedPacketResponse(
        String id, String senderId, int chatType, String targetId, int packetType,
        String totalAmount, int totalCount, String remainAmount, int remainCount,
        String message, int status, long expireTime, long createdTime, long updatedTime) {
    public static RedPacketResponse from(RedPacket value) {
        return new RedPacketResponse(
                value.getId().toString(), value.getSenderId().toString(), value.getChatType(), value.getTargetId().toString(),
                value.getPacketType(), value.getTotalAmount().toPlainString(), value.getTotalCount(),
                value.getRemainAmount().toPlainString(), value.getRemainCount(), value.getMessage(), value.getStatus(),
                toEpochMilli(value.getExpireTime()), toEpochMilli(value.getCreatedTime()), toEpochMilli(value.getUpdatedTime()));
    }
}
