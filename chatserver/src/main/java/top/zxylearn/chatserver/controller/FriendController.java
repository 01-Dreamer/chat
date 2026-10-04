package top.zxylearn.chatserver.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.dto.friend.CreateFriendRequest;
import top.zxylearn.chatserver.dto.friend.UpdateFriendRemarkRequest;
import top.zxylearn.chatserver.service.FriendRelationshipService;
import top.zxylearn.chatserver.vo.FriendRequestResponse;
import top.zxylearn.chatserver.vo.FriendResponse;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FriendController {

    private final FriendRelationshipService friendService;

    public FriendController(FriendRelationshipService friendService) {
        this.friendService = friendService;
    }

    @GetMapping("/friends")
    public ApiResponse<List<FriendResponse>> listFriends() {
        return ApiResponse.success(friendService.listFriends());
    }

    @PatchMapping("/friends/{friendUserId}/remark")
    public ApiResponse<FriendResponse> updateRemark(
            @PathVariable long friendUserId,
            @Valid @RequestBody UpdateFriendRemarkRequest request) {
        return ApiResponse.success(friendService.updateRemark(friendUserId, request.remark()));
    }

    @DeleteMapping("/friends/{friendUserId}")
    public ApiResponse<Void> deleteFriend(@PathVariable long friendUserId) {
        friendService.deleteFriend(friendUserId);
        return ApiResponse.success(null);
    }

    @GetMapping("/friend-requests")
    public ApiResponse<List<FriendRequestResponse>> listRequests() {
        return ApiResponse.success(friendService.listRequests());
    }

    @PostMapping("/friend-requests")
    public ApiResponse<FriendRequestResponse> createRequest(
            @Valid @RequestBody CreateFriendRequest request) {
        return ApiResponse.success(friendService.createRequest(request));
    }

    @PostMapping("/friend-requests/{requestId}/accept")
    public ApiResponse<FriendRequestResponse> acceptRequest(@PathVariable long requestId) {
        return ApiResponse.success(friendService.acceptRequest(requestId));
    }

    @PostMapping("/friend-requests/{requestId}/reject")
    public ApiResponse<FriendRequestResponse> rejectRequest(@PathVariable long requestId) {
        return ApiResponse.success(friendService.rejectRequest(requestId));
    }
}
