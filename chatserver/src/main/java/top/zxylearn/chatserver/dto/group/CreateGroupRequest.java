package top.zxylearn.chatserver.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "群名称不能为空")
        @Size(max = 64, message = "群名称不能超过64个字符")
        String name,
        @Size(max = 200, message = "初始群成员不能超过200人")
        List<String> memberIds) {
}
