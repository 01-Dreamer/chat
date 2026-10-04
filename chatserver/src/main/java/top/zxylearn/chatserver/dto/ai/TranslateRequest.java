package top.zxylearn.chatserver.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslateRequest(
        @NotBlank @Size(max = 10_000) String text,
        @NotBlank @Size(max = 32) String targetLanguage) {
}
