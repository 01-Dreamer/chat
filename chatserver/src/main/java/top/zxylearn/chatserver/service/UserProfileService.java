package top.zxylearn.chatserver.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.entity.Account;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.entity.FileResourceAccess;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.AccountMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.mapper.FileResourceMapper;
import top.zxylearn.chatserver.mapper.FileResourceAccessMapper;
import top.zxylearn.chatserver.vo.UserProfileResponse;

import java.time.LocalDateTime;

@Service
public class UserProfileService {

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final FileResourceMapper fileResourceMapper;
    private final FileResourceAccessMapper fileResourceAccessMapper;

    public UserProfileService(UserMapper userMapper, AccountMapper accountMapper, FileResourceMapper fileResourceMapper,
                              FileResourceAccessMapper fileResourceAccessMapper) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.fileResourceAccessMapper = fileResourceAccessMapper;
    }

    @Transactional
    public UserProfileResponse updateNickname(String nickname) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.UNAUTHORIZED);
        }
        user.setNickname(nickname.trim());
        user.setUpdatedTime(LocalDateTime.now());
        userMapper.updateById(user);
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, currentUserId)
                .last("LIMIT 1"));
        return UserProfileResponse.from(user, account);
    }

    @Transactional
    public UserProfileResponse updateAvatar(String resourceId) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        long id;
        try { id = Long.parseLong(resourceId); }
        catch (NumberFormatException exception) {
            throw new BusinessException("INVALID_RESOURCE_ID", "文件资源ID格式不正确", HttpStatus.BAD_REQUEST);
        }
        FileResource resource = fileResourceMapper.selectById(id);
        if (resource == null || fileResourceAccessMapper.selectCount(new LambdaQueryWrapper<FileResourceAccess>()
                .eq(FileResourceAccess::getResourceId, id).eq(FileResourceAccess::getUserId, currentUserId)) == 0
                || resource.getResourceType() != 0
                || resource.getMimeType() == null || !resource.getMimeType().startsWith("image/")) {
            throw new BusinessException("INVALID_AVATAR_RESOURCE", "头像必须使用当前用户上传的图片资源", HttpStatus.BAD_REQUEST);
        }
        User user = userMapper.selectById(currentUserId);
        if (user == null) throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.UNAUTHORIZED);
        user.setAvatarUrl(resource.getFileUrl());
        user.setUpdatedTime(LocalDateTime.now());
        userMapper.updateById(user);
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, currentUserId).last("LIMIT 1"));
        return UserProfileResponse.from(user, account);
    }
}
