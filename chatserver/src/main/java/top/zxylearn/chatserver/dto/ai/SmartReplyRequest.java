package top.zxylearn.chatserver.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SmartReplyRequest(
        @NotEmpty @Size(max = 20) List<@Valid ContextMessage> messages) {

    public record ContextMessage(
            @jakarta.validation.constraints.Pattern(regexp = "user|assistant") String role,
            @jakarta.validation.constraints.NotBlank @Size(max = 2_000) String content) {
    }
}
