package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.User;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record GroupMemberResponse(
        String id,
        String groupId,
        String userId,
        int role,
        String groupNickname,
        String username,
        String nickname,
        String avatarUrl,
        long createdTime,
        long updatedTime,
        long userUpdatedTime) {

    public static GroupMemberResponse from(GroupMember member, User user) {
        return new GroupMemberResponse(
                member.getId().toString(),
                member.getGroupId().toString(),
                member.getUserId().toString(),
                member.getRole(),
                member.getNickname(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                toEpochMilli(member.getCreatedTime()),
                toEpochMilli(member.getUpdatedTime()),
                toEpochMilli(user.getUpdatedTime()));
    }
}
