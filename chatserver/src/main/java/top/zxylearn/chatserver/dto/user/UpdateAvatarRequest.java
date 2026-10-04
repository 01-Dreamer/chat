package top.zxylearn.chatserver.dto.user;

import jakarta.validation.constraints.NotBlank;

public record UpdateAvatarRequest(@NotBlank String resourceId) {
}
