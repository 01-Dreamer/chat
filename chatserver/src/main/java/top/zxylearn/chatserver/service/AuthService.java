package top.zxylearn.chatserver.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.dto.auth.LoginRequest;
import top.zxylearn.chatserver.dto.auth.RegisterRequest;
import top.zxylearn.chatserver.entity.Account;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.AccountMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.vo.AuthSessionResponse;
import top.zxylearn.chatserver.vo.UserProfileResponse;

@Service
public class AuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(
            UserService userService,
            UserMapper userMapper,
            AccountMapper accountMapper,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter loginRateLimiter) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    public AuthSessionResponse register(RegisterRequest request) {
        User user = userService.register(request.username(), request.password(), request.nickname());
        return createSession(user);
    }

    public AuthSessionResponse login(LoginRequest request, String clientAddress) {
        String username = request.username().trim();
        loginRateLimiter.checkAndRecord(clientAddress, username);
        User user = userService.findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(
                    "INVALID_CREDENTIALS",
                    "用户名或密码错误",
                    HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(
                    "USER_DISABLED",
                    "账号已被禁用",
                    HttpStatus.FORBIDDEN);
        }
        loginRateLimiter.clear(clientAddress, username);
        return createSession(user);
    }

    public UserProfileResponse currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            StpUtil.logout();
            throw new BusinessException(
                    "USER_NOT_FOUND",
                    "用户不存在",
                    HttpStatus.UNAUTHORIZED);
        }
        return profileOf(user);
    }

    public void logout() {
        StpUtil.logout();
    }

    private AuthSessionResponse createSession(User user) {
        StpUtil.login(user.getId(), new SaLoginParameter().setDeviceType("desktop"));
        return new AuthSessionResponse(
                StpUtil.getTokenName(),
                StpUtil.getTokenValue(),
                profileOf(user));
    }

    private UserProfileResponse profileOf(User user) {
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, user.getId())
                .last("LIMIT 1"));
        return UserProfileResponse.from(user, account);
    }
}
