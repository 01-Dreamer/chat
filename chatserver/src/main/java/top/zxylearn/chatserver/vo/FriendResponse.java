package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.entity.User;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record FriendResponse(
        String relationId,
        String userId,
        String username,
        String nickname,
        String avatarUrl,
        String remark,
        int status,
        long createdTime,
        long updatedTime,
        long userUpdatedTime) {

    public static FriendResponse from(Friend friend, User user) {
        return new FriendResponse(
                friend.getId().toString(),
                user.getId().toString(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                friend.getRemark(),
                user.getStatus(),
                toEpochMilli(friend.getCreatedTime()),
                toEpochMilli(friend.getUpdatedTime()),
                toEpochMilli(user.getUpdatedTime()));
    }
}
