package top.zxylearn.chatserver.dto.group;

import jakarta.validation.constraints.Size;

public record UpdateGroupTextRequest(
        @Size(max = 64, message = "内容不能超过64个字符") String value) {
}
