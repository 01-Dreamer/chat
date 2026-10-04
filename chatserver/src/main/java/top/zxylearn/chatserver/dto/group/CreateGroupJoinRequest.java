package top.zxylearn.chatserver.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupJoinRequest(
        @NotBlank(message = "群ID不能为空") String groupId,
        @Size(max = 255, message = "申请备注不能超过255个字符") String message) {
}
