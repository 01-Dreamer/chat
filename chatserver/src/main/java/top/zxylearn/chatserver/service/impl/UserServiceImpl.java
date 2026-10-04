package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.entity.Account;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.AccountMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.service.UserService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(AccountMapper accountMapper, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User register(String username, String password, String nickname) {
        String normalizedUsername = username.trim();
        String normalizedNickname = nickname.trim();
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException(
                    "INVALID_PASSWORD",
                    "密码的 UTF-8 编码不能超过 72 字节",
                    HttpStatus.BAD_REQUEST);
        }
        if (findByUsername(normalizedUsername) != null) {
            throw new BusinessException(
                    "USERNAME_EXISTS",
                    "用户名已被使用",
                    HttpStatus.CONFLICT);
        }

        User user = new User();
        LocalDateTime now = LocalDateTime.now();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(normalizedNickname);
        user.setStatus(1);
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        baseMapper.insert(user);

        Account account = new Account();
        account.setUserId(user.getId());
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(1);
        account.setCreatedTime(now);
        account.setUpdatedTime(now);
        accountMapper.insert(account);
        return user;
    }

    @Override
    public User findByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }
}
