package top.zxylearn.chatserver.vo;

import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.entity.GroupJoinRequest;
import top.zxylearn.chatserver.entity.User;

import static top.zxylearn.chatserver.util.TimeUtils.toEpochMilli;

public record GroupJoinRequestResponse(
        String id,
        String groupId,
        String groupName,
        String userId,
        String username,
        String nickname,
        String avatarUrl,
        String message,
        int status,
        String reviewerId,
        long createdTime,
        long updatedTime,
        long userUpdatedTime) {

    public static GroupJoinRequestResponse from(
            GroupJoinRequest request,
            Group group,
            User applicant) {
        return new GroupJoinRequestResponse(
                request.getId().toString(),
                request.getGroupId().toString(),
                group.getName(),
                request.getUserId().toString(),
                applicant.getUsername(),
                applicant.getNickname(),
                applicant.getAvatarUrl(),
                request.getMessage(),
                request.getStatus(),
                request.getReviewerId() == null ? null : request.getReviewerId().toString(),
                toEpochMilli(request.getCreatedTime()),
                toEpochMilli(request.getUpdatedTime()),
                toEpochMilli(applicant.getUpdatedTime()));
    }
}
