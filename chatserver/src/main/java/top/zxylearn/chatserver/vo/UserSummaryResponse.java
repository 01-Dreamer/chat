package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.User;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record UserSummaryResponse(
        String id,
        String username,
        String nickname,
        String avatarUrl,
        int status,
        long createdTime,
        long updatedTime) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getStatus(),
                toEpochMilli(user.getCreatedTime()),
                toEpochMilli(user.getUpdatedTime()));
    }
}
