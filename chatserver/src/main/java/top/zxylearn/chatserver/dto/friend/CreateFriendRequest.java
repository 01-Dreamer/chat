package top.zxylearn.chatserver.dto.friend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFriendRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名格式不正确")
        String username,

        @Size(max = 255, message = "申请理由不能超过 255 个字符")
        String message) {
}
