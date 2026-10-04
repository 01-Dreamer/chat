package top.zxylearn.chatserver.service;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.vo.UserSummaryResponse;

@Service
public class UserQueryService {

    private final UserService userService;

    public UserQueryService(UserService userService) {
        this.userService = userService;
    }

    public UserSummaryResponse searchByUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!normalized.matches("^[A-Za-z0-9_]{3,32}$")) {
            throw new BusinessException("INVALID_USERNAME", "用户名格式不正确", HttpStatus.BAD_REQUEST);
        }
        User user = userService.findByUsername(normalized);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("USER_NOT_FOUND", "没有找到该用户", HttpStatus.NOT_FOUND);
        }
        if (user.getId().equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("CANNOT_ADD_SELF", "不能添加自己为好友", HttpStatus.BAD_REQUEST);
        }
        return UserSummaryResponse.from(user);
    }
}
