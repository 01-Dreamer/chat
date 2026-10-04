package top.zxylearn.chatserver.dto.group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateGroupRoleRequest(
        @NotNull(message = "角色不能为空")
        @Min(value = 0, message = "角色只能是普通成员或管理员")
        @Max(value = 1, message = "角色只能是普通成员或管理员")
        Integer role) {
}
