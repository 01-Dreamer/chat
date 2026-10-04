package top.zxylearn.chatserver.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名应为 3-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 72, message = "密码长度应为 8-72 位")
        String password) {
}
