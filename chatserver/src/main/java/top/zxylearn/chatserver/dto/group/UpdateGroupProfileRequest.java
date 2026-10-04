package top.zxylearn.chatserver.dto.group;

import jakarta.validation.constraints.Size;

public record UpdateGroupProfileRequest(
        @Size(min = 1, max = 64, message = "群名称长度应为1到64个字符") String name) {
}
