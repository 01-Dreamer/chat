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
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mapper.FriendAddRequestMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.GroupJoinRequestMapper;
import top.zxylearn.chatserver.vo.UserProfileResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserProfileService {

    private final UserMapper userMapper;
    private final AccountMapper accountMapper;
    private final FileResourceMapper fileResourceMapper;
    private final FileResourceAccessMapper fileResourceAccessMapper;
    private final FriendMapper friendMapper;
    private final FriendAddRequestMapper friendAddRequestMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final DirectorySnapshotCache directoryCache;
    private final ReliableEventService reliableEventService;

    public UserProfileService(
            UserMapper userMapper,
            AccountMapper accountMapper,
            FileResourceMapper fileResourceMapper,
            FileResourceAccessMapper fileResourceAccessMapper,
            FriendMapper friendMapper,
            FriendAddRequestMapper friendAddRequestMapper,
            GroupMemberMapper groupMemberMapper,
            GroupJoinRequestMapper groupJoinRequestMapper,
            DirectorySnapshotCache directoryCache,
            ReliableEventService reliableEventService) {
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.fileResourceAccessMapper = fileResourceAccessMapper;
        this.friendMapper = friendMapper;
        this.friendAddRequestMapper = friendAddRequestMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.directoryCache = directoryCache;
        this.reliableEventService = reliableEventService;
    }

    @Transactional
    public UserProfileResponse updateNickname(String nickname) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.UNAUTHORIZED);
        }
        user.setNickname(nickname.trim());
        LocalDateTime now = LocalDateTime.now();
        user.setUpdatedTime(now);
        userMapper.updateById(user);
        notifyProfileChanged(currentUserId, now);
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
        LocalDateTime now = LocalDateTime.now();
        user.setUpdatedTime(now);
        userMapper.updateById(user);
        notifyProfileChanged(currentUserId, now);
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, currentUserId).last("LIMIT 1"));
        return UserProfileResponse.from(user, account);
    }

    private void notifyProfileChanged(long userId, LocalDateTime now) {
        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(userId);
        friendMapper.selectList(new LambdaQueryWrapper<top.zxylearn.chatserver.entity.Friend>()
                        .eq(top.zxylearn.chatserver.entity.Friend::getFriendId, userId))
                .forEach(friend -> recipients.add(friend.getUserId()));
        friendAddRequestMapper.selectList(
                        new LambdaQueryWrapper<top.zxylearn.chatserver.entity.FriendAddRequest>()
                                .and(query -> query
                                        .eq(top.zxylearn.chatserver.entity.FriendAddRequest::getSenderId, userId)
                                        .or()
                                        .eq(top.zxylearn.chatserver.entity.FriendAddRequest::getReceiverId, userId)))
                .forEach(request -> recipients.add(
                        request.getSenderId() == userId ? request.getReceiverId() : request.getSenderId()));
        List<Long> groupIds = groupMemberMapper.selectList(
                        new LambdaQueryWrapper<top.zxylearn.chatserver.entity.GroupMember>()
                                .eq(top.zxylearn.chatserver.entity.GroupMember::getUserId, userId))
                .stream().map(top.zxylearn.chatserver.entity.GroupMember::getGroupId).toList();
        for (Long groupId : groupIds) {
            directoryCache.evict(directoryCache.groupMembersKey(groupId));
            groupMemberMapper.selectList(new LambdaQueryWrapper<top.zxylearn.chatserver.entity.GroupMember>()
                            .eq(top.zxylearn.chatserver.entity.GroupMember::getGroupId, groupId))
                    .forEach(member -> {
                        recipients.add(member.getUserId());
                        directoryCache.evict(directoryCache.groupsKey(member.getUserId()));
                    });
        }
        groupJoinRequestMapper.selectList(
                        new LambdaQueryWrapper<top.zxylearn.chatserver.entity.GroupJoinRequest>()
                                .eq(top.zxylearn.chatserver.entity.GroupJoinRequest::getUserId, userId))
                .forEach(request -> groupMemberMapper.selectList(
                                new LambdaQueryWrapper<top.zxylearn.chatserver.entity.GroupMember>()
                                        .eq(top.zxylearn.chatserver.entity.GroupMember::getGroupId, request.getGroupId())
                                        .in(top.zxylearn.chatserver.entity.GroupMember::getRole, 1, 2))
                        .forEach(manager -> {
                            recipients.add(manager.getUserId());
                            directoryCache.evict(directoryCache.groupRequestsKey(manager.getUserId()));
                        }));
        directoryCache.evict(directoryCache.groupRequestsKey(userId));
        List<String> friendKeys = new ArrayList<>();
        for (Long recipient : recipients) {
            friendKeys.add(directoryCache.friendsKey(recipient));
            friendKeys.add(directoryCache.friendRequestsKey(recipient));
        }
        directoryCache.evict(friendKeys.toArray(String[]::new));
        reliableEventService.append(6, userId, recipients, now);
    }
}
