package top.zxylearn.chatserver.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.dto.group.CreateGroupJoinRequest;
import top.zxylearn.chatserver.dto.group.CreateGroupRequest;
import top.zxylearn.chatserver.dto.group.UpdateGroupProfileRequest;
import top.zxylearn.chatserver.dto.group.UpdateGroupRoleRequest;
import top.zxylearn.chatserver.dto.group.UpdateGroupTextRequest;
import top.zxylearn.chatserver.dto.user.UpdateAvatarRequest;
import top.zxylearn.chatserver.service.GroupManagementService;
import top.zxylearn.chatserver.vo.GroupJoinRequestResponse;
import top.zxylearn.chatserver.vo.GroupMemberResponse;
import top.zxylearn.chatserver.vo.GroupResponse;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GroupController {

    private final GroupManagementService groupService;

    public GroupController(GroupManagementService groupService) {
        this.groupService = groupService;
    }

    @PostMapping("/groups")
    public ApiResponse<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ApiResponse.success(groupService.createGroup(request));
    }

    @GetMapping("/groups")
    public ApiResponse<List<GroupResponse>> listGroups() {
        return ApiResponse.success(groupService.listGroups());
    }

    @GetMapping("/groups/search")
    public ApiResponse<GroupResponse> searchGroup(@RequestParam String groupNumber) {
        return ApiResponse.success(groupService.searchGroup(groupNumber));
    }

    @GetMapping("/groups/{groupId}")
    public ApiResponse<GroupResponse> getGroup(@PathVariable long groupId) {
        return ApiResponse.success(groupService.getGroup(groupId));
    }

    @PatchMapping("/groups/{groupId}")
    public ApiResponse<GroupResponse> updateProfile(
            @PathVariable long groupId,
            @Valid @RequestBody UpdateGroupProfileRequest request) {
        return ApiResponse.success(groupService.updateProfile(groupId, request.name()));
    }

    @PatchMapping("/groups/{groupId}/avatar")
    public ApiResponse<GroupResponse> updateAvatar(@PathVariable long groupId, @Valid @RequestBody UpdateAvatarRequest request) {
        return ApiResponse.success(groupService.updateAvatar(groupId, request.resourceId()));
    }

    @GetMapping("/groups/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> listMembers(@PathVariable long groupId) {
        return ApiResponse.success(groupService.listMembers(groupId));
    }

    @PatchMapping("/groups/{groupId}/members/me/nickname")
    public ApiResponse<GroupMemberResponse> updateMyNickname(
            @PathVariable long groupId,
            @Valid @RequestBody UpdateGroupTextRequest request) {
        return ApiResponse.success(groupService.updateMyNickname(groupId, request.value()));
    }

    @PatchMapping("/groups/{groupId}/remark")
    public ApiResponse<GroupResponse> updateMyRemark(
            @PathVariable long groupId,
            @Valid @RequestBody UpdateGroupTextRequest request) {
        return ApiResponse.success(groupService.updateMyRemark(groupId, request.value()));
    }

    @PatchMapping("/groups/{groupId}/members/{memberUserId}/role")
    public ApiResponse<GroupMemberResponse> updateRole(
            @PathVariable long groupId,
            @PathVariable long memberUserId,
            @Valid @RequestBody UpdateGroupRoleRequest request) {
        return ApiResponse.success(groupService.updateRole(groupId, memberUserId, request.role()));
    }

    @DeleteMapping("/groups/{groupId}/members/{memberUserId}")
    public ApiResponse<Void> kickMember(@PathVariable long groupId, @PathVariable long memberUserId) {
        groupService.kickMember(groupId, memberUserId);
        return ApiResponse.success(null);
    }

    @PostMapping("/groups/{groupId}/leave")
    public ApiResponse<Void> leaveGroup(@PathVariable long groupId) {
        groupService.leaveGroup(groupId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/groups/{groupId}")
    public ApiResponse<Void> dissolveGroup(@PathVariable long groupId) {
        groupService.dissolveGroup(groupId);
        return ApiResponse.success(null);
    }

    @PostMapping("/group-join-requests")
    public ApiResponse<GroupJoinRequestResponse> createJoinRequest(
            @Valid @RequestBody CreateGroupJoinRequest request) {
        return ApiResponse.success(groupService.createJoinRequest(
                parseGroupId(request.groupId()), request.message()));
    }

    @GetMapping("/group-join-requests")
    public ApiResponse<List<GroupJoinRequestResponse>> listJoinRequests() {
        return ApiResponse.success(groupService.listJoinRequests());
    }

    @PostMapping("/group-join-requests/{requestId}/accept")
    public ApiResponse<GroupJoinRequestResponse> acceptJoinRequest(@PathVariable long requestId) {
        return ApiResponse.success(groupService.reviewJoinRequest(requestId, true));
    }

    @PostMapping("/group-join-requests/{requestId}/reject")
    public ApiResponse<GroupJoinRequestResponse> rejectJoinRequest(@PathVariable long requestId) {
        return ApiResponse.success(groupService.reviewJoinRequest(requestId, false));
    }

    private long parseGroupId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new top.zxylearn.chatserver.exception.BusinessException(
                    "INVALID_GROUP_ID", "群号格式不正确", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }
}
