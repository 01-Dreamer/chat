package top.zxylearn.chatserver.dto.call;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCallRequest(
        @NotBlank String calleeId,
        @NotNull @Min(0) @Max(1) Integer callType) {
}
