package top.zxylearn.chatserver.dto.friend;

import jakarta.validation.constraints.Size;

public record UpdateFriendRemarkRequest(
        @Size(max = 64, message = "好友备注不能超过 64 个字符")
        String remark) {
}
