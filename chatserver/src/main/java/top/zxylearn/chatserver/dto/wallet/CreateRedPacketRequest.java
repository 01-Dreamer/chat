package top.zxylearn.chatserver.dto.wallet;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRedPacketRequest(
        @NotNull @Min(0) @Max(1) Integer chatType,
        @NotBlank String targetId,
        @NotNull @Min(0) @Max(1) Integer packetType,
        @NotBlank @Pattern(regexp = "^(0|[1-9]\\d{0,9})\\.\\d{2}$") String totalAmount,
        @NotNull @Min(1) @Max(500) Integer totalCount,
        @Size(max = 128) String message,
        @Pattern(regexp = "^\\d{6}$", message = "支付密码必须是6位数字") String payPassword) {
}
