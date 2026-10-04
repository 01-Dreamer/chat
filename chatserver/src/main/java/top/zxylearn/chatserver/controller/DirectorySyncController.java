package top.zxylearn.chatserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.service.DirectorySnapshotCache;
import top.zxylearn.chatserver.service.FriendRelationshipService;
import top.zxylearn.chatserver.service.GroupManagementService;
import top.zxylearn.chatserver.vo.FriendRequestResponse;
import top.zxylearn.chatserver.vo.FriendResponse;
import top.zxylearn.chatserver.vo.GroupJoinRequestResponse;
import top.zxylearn.chatserver.vo.GroupMemberResponse;
import top.zxylearn.chatserver.vo.GroupResponse;
import top.zxylearn.chatserver.vo.VersionedSyncResponse;

@RestController
@RequestMapping("/api/sync")
public class DirectorySyncController {

    private final DirectorySnapshotCache cache;
    private final FriendRelationshipService friendService;
    private final GroupManagementService groupService;

    public DirectorySyncController(
            DirectorySnapshotCache cache,
            FriendRelationshipService friendService,
            GroupManagementService groupService) {
        this.cache = cache;
        this.friendService = friendService;
        this.groupService = groupService;
    }

    @GetMapping("/friends")
    public ApiResponse<VersionedSyncResponse<FriendResponse>> friends(
            @RequestParam(required = false) String version) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(cache.sync(
                cache.friendsKey(userId), version, FriendResponse.class, friendService::listFriends));
    }

    @GetMapping("/friend-requests")
    public ApiResponse<VersionedSyncResponse<FriendRequestResponse>> friendRequests(
            @RequestParam(required = false) String version) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(cache.sync(
                cache.friendRequestsKey(userId), version, FriendRequestResponse.class, friendService::listRequests));
    }

    @GetMapping("/groups")
    public ApiResponse<VersionedSyncResponse<GroupResponse>> groups(
            @RequestParam(required = false) String version) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(cache.sync(
                cache.groupsKey(userId), version, GroupResponse.class, groupService::listGroups));
    }

    @GetMapping("/group-requests")
    public ApiResponse<VersionedSyncResponse<GroupJoinRequestResponse>> groupRequests(
            @RequestParam(required = false) String version) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(cache.sync(
                cache.groupRequestsKey(userId), version, GroupJoinRequestResponse.class,
                groupService::listJoinRequests));
    }

    @GetMapping("/groups/{groupId}/members")
    public ApiResponse<VersionedSyncResponse<GroupMemberResponse>> groupMembers(
            @PathVariable long groupId,
            @RequestParam(required = false) String version) {
        groupService.getGroup(groupId);
        return ApiResponse.success(cache.sync(
                cache.groupMembersKey(groupId), version, GroupMemberResponse.class,
                () -> groupService.listMembers(groupId)));
    }
}
