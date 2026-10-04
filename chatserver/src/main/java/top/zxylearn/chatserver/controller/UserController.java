package top.zxylearn.chatserver.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.chatserver.common.ApiResponse;
import top.zxylearn.chatserver.dto.user.UpdateProfileRequest;
import top.zxylearn.chatserver.dto.user.UpdateAvatarRequest;
import top.zxylearn.chatserver.service.UserProfileService;
import top.zxylearn.chatserver.service.UserQueryService;
import top.zxylearn.chatserver.vo.UserProfileResponse;
import top.zxylearn.chatserver.vo.UserSummaryResponse;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserProfileService userProfileService;

    public UserController(
            UserQueryService userQueryService,
            UserProfileService userProfileService) {
        this.userQueryService = userQueryService;
        this.userProfileService = userProfileService;
    }

    @GetMapping("/search")
    public ApiResponse<UserSummaryResponse> search(@RequestParam String username) {
        return ApiResponse.success(userQueryService.searchByUsername(username));
    }

    @PatchMapping("/me/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userProfileService.updateNickname(request.nickname()));
    }

    @PatchMapping("/me/avatar")
    public ApiResponse<UserProfileResponse> updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        return ApiResponse.success(userProfileService.updateAvatar(request.resourceId()));
    }
}
