package top.zxylearn.chatserver.dto.wallet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TransferRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String clientTransactionId,
        @NotBlank String recipientUserId,
        @NotBlank @Pattern(regexp = "^(0|[1-9]\\d{0,9})\\.\\d{2}$", message = "金额格式必须保留两位小数") String amount,
        @Pattern(regexp = "^\\d{6}$", message = "支付密码必须是6位数字") String payPassword) {
}
