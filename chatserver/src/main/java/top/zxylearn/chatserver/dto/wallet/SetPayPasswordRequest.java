package top.zxylearn.chatserver.dto.wallet;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record SetPayPasswordRequest(
        String oldPassword,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "支付密码必须是6位数字") String newPassword) {
}
