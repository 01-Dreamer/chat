package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.UserGroupSetting;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record GroupResponse(
        String id,
        String name,
        String avatarUrl,
        String ownerId,
        String ownerName,
        int status,
        int memberCount,
        int currentUserRole,
        String currentUserNickname,
        String remark,
        long createdTime,
        long updatedTime) {

    public static GroupResponse from(
            Group group,
            GroupMember currentMember,
            UserGroupSetting setting,
            String ownerName,
            int memberCount) {
        return new GroupResponse(
                group.getId().toString(),
                group.getName(),
                group.getAvatarUrl(),
                group.getOwnerId().toString(),
                ownerName,
                group.getStatus(),
                memberCount,
                currentMember == null ? -1 : currentMember.getRole(),
                currentMember == null ? null : currentMember.getNickname(),
                setting == null ? null : setting.getRemark(),
                toEpochMilli(group.getCreatedTime()),
                toEpochMilli(group.getUpdatedTime()));
    }
}
