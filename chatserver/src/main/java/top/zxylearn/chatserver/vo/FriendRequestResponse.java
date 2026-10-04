package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.FriendAddRequest;
import top.zxylearn.chatserver.entity.User;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record FriendRequestResponse(
        String id,
        String senderId,
        String receiverId,
        String direction,
        UserSummaryResponse user,
        String message,
        int status,
        long createdTime,
        long updatedTime) {

    public static FriendRequestResponse from(
            FriendAddRequest request,
            User otherUser,
            long currentUserId) {
        return new FriendRequestResponse(
                request.getId().toString(),
                request.getSenderId().toString(),
                request.getReceiverId().toString(),
                request.getSenderId() == currentUserId ? "outgoing" : "incoming",
                UserSummaryResponse.from(otherUser),
                request.getMessage(),
                request.getStatus(),
                toEpochMilli(request.getCreatedTime()),
                toEpochMilli(request.getUpdatedTime()));
    }
}
