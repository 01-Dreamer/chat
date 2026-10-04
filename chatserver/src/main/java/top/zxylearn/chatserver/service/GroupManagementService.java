package top.zxylearn.chatserver.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.dto.group.CreateGroupRequest;
import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.entity.GroupJoinRequest;
import top.zxylearn.chatserver.entity.GroupLeaveRecord;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.entity.UserGroupSetting;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mapper.FileResourceMapper;
import top.zxylearn.chatserver.entity.FileResource;
import top.zxylearn.chatserver.entity.FileResourceAccess;
import top.zxylearn.chatserver.mapper.FileResourceAccessMapper;
import top.zxylearn.chatserver.mapper.GroupJoinRequestMapper;
import top.zxylearn.chatserver.mapper.GroupLeaveRecordMapper;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.UserGroupSettingMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.vo.GroupJoinRequestResponse;
import top.zxylearn.chatserver.vo.GroupMemberResponse;
import top.zxylearn.chatserver.vo.GroupResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupManagementService {

    private static final int GROUP_ACTIVE = 1;
    private static final int ROLE_MEMBER = 0;
    private static final int ROLE_ADMIN = 1;
    private static final int ROLE_OWNER = 2;
    private static final int REQUEST_PENDING = 0;
    private static final int REQUEST_ACCEPTED = 1;
    private static final int REQUEST_REJECTED = 2;
    private static final int EVENT_GROUP_JOIN_REQUEST = 4;
    private static final int EVENT_GROUP_JOIN_REQUEST_HANDLED = 5;

    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final GroupJoinRequestMapper requestMapper;
    private final GroupLeaveRecordMapper leaveRecordMapper;
    private final UserGroupSettingMapper settingMapper;
    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final ReliableEventService reliableEventService;
    private final FileResourceMapper fileResourceMapper;
    private final FileResourceAccessMapper fileResourceAccessMapper;

    public GroupManagementService(
            GroupMapper groupMapper,
            GroupMemberMapper memberMapper,
            GroupJoinRequestMapper requestMapper,
            GroupLeaveRecordMapper leaveRecordMapper,
            UserGroupSettingMapper settingMapper,
            UserMapper userMapper,
            FriendMapper friendMapper,
            FileResourceMapper fileResourceMapper,
            FileResourceAccessMapper fileResourceAccessMapper,
            ReliableEventService reliableEventService) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.requestMapper = requestMapper;
        this.leaveRecordMapper = leaveRecordMapper;
        this.settingMapper = settingMapper;
        this.userMapper = userMapper;
        this.friendMapper = friendMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.fileResourceAccessMapper = fileResourceAccessMapper;
        this.reliableEventService = reliableEventService;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest input) {
        long currentUserId = currentUserId();
        Set<Long> initialMemberIds = parseIds(input.memberIds(), "INVALID_MEMBER_ID", "群成员ID格式不正确");
        initialMemberIds.remove(currentUserId);
        reliableEventService.lockUsers(withOwner(currentUserId, initialMemberIds));
        validateInitialMembersAreFriends(currentUserId, initialMemberIds);

        LocalDateTime now = LocalDateTime.now();
        Group group = new Group();
        group.setName(input.name().trim());
        group.setOwnerId(currentUserId);
        group.setStatus(GROUP_ACTIVE);
        group.setCreatedTime(now);
        group.setUpdatedTime(now);
        groupMapper.insert(group);

        GroupMember owner = insertMember(group.getId(), currentUserId, ROLE_OWNER, now);
        for (Long memberId : initialMemberIds) {
            insertMember(group.getId(), memberId, ROLE_MEMBER, now);
        }
        return response(group, owner, null);
    }

    public List<GroupResponse> listGroups() {
        long currentUserId = currentUserId();
        List<GroupMember> memberships = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, currentUserId));
        if (memberships.isEmpty()) return List.of();
        Map<Long, GroupMember> memberByGroup = memberships.stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, Function.identity()));
        List<Group> groups = groupMapper.selectList(new LambdaQueryWrapper<Group>()
                .in(Group::getId, memberByGroup.keySet())
                .eq(Group::getStatus, GROUP_ACTIVE)
                .orderByDesc(Group::getUpdatedTime));
        Map<Long, UserGroupSetting> settings = settingsByGroup(currentUserId, memberByGroup.keySet());
        return groups.stream()
                .map(group -> response(group, memberByGroup.get(group.getId()), settings.get(group.getId())))
                .toList();
    }

    public GroupResponse searchGroup(String groupNumber) {
        long groupId = parseId(groupNumber, "INVALID_GROUP_ID", "群号格式不正确");
        Group group = requireActiveGroup(groupId);
        long currentUserId = currentUserId();
        GroupMember member = findMember(groupId, currentUserId);
        UserGroupSetting setting = member == null ? null : findSetting(currentUserId, groupId);
        return response(group, member, setting);
    }

    public GroupResponse getGroup(long groupId) {
        long currentUserId = currentUserId();
        Group group = requireActiveGroup(groupId);
        GroupMember member = requireMember(groupId, currentUserId);
        return response(group, member, findSetting(currentUserId, groupId));
    }

    public List<GroupMemberResponse> listMembers(long groupId) {
        requireActiveGroup(groupId);
        requireMember(groupId, currentUserId());
        List<GroupMember> members = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .orderByDesc(GroupMember::getRole)
                .orderByAsc(GroupMember::getCreatedTime));
        if (members.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectByIds(members.stream()
                        .map(GroupMember::getUserId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return members.stream()
                .filter(member -> users.containsKey(member.getUserId()))
                .map(member -> GroupMemberResponse.from(member, users.get(member.getUserId())))
                .toList();
    }

    @Transactional
    public GroupResponse updateProfile(long groupId, String name) {
        long currentUserId = currentUserId();
        Group group = requireActiveGroupForUpdate(groupId);
        GroupMember actor = requireMember(groupId, currentUserId);
        requireManager(actor);
        if (name != null) {
            String normalized = name.trim();
            if (normalized.isEmpty()) {
                throw new BusinessException("INVALID_GROUP_NAME", "群名称不能为空", HttpStatus.BAD_REQUEST);
            }
            group.setName(normalized);
        }
        group.setUpdatedTime(LocalDateTime.now());
        groupMapper.updateById(group);
        return response(group, actor, findSetting(currentUserId, groupId));
    }

    @Transactional
    public GroupResponse updateAvatar(long groupId, String resourceId) {
        long currentUserId = currentUserId();
        Group group = requireActiveGroupForUpdate(groupId);
        GroupMember actor = requireMember(groupId, currentUserId);
        requireManager(actor);
        long id;
        try { id = Long.parseLong(resourceId); }
        catch (NumberFormatException exception) { throw new BusinessException("INVALID_RESOURCE_ID", "文件资源ID格式不正确", HttpStatus.BAD_REQUEST); }
        FileResource resource = fileResourceMapper.selectById(id);
        if (resource == null || fileResourceAccessMapper.selectCount(new LambdaQueryWrapper<FileResourceAccess>()
                .eq(FileResourceAccess::getResourceId, id).eq(FileResourceAccess::getUserId, currentUserId)) == 0
                || resource.getResourceType() != 0
                || resource.getMimeType() == null || !resource.getMimeType().startsWith("image/")) {
            throw new BusinessException("INVALID_GROUP_AVATAR_RESOURCE", "群头像必须使用当前用户上传的图片资源", HttpStatus.BAD_REQUEST);
        }
        group.setAvatarUrl(resource.getFileUrl());
        group.setUpdatedTime(LocalDateTime.now());
        groupMapper.updateById(group);
        return response(group, actor, findSetting(currentUserId, groupId));
    }

    @Transactional
    public GroupMemberResponse updateMyNickname(long groupId, String nickname) {
        long currentUserId = currentUserId();
        requireActiveGroupForUpdate(groupId);
        GroupMember member = memberMapper.selectForUpdate(groupId, currentUserId);
        if (member == null) throw notMember();
        member.setNickname(trimToNull(nickname));
        member.setUpdatedTime(LocalDateTime.now());
        memberMapper.updateById(member);
        return GroupMemberResponse.from(member, userMapper.selectById(currentUserId));
    }

    @Transactional
    public GroupResponse updateMyRemark(long groupId, String remark) {
        long currentUserId = currentUserId();
        Group group = requireActiveGroupForUpdate(groupId);
        GroupMember member = requireMember(groupId, currentUserId);
        UserGroupSetting setting = findSetting(currentUserId, groupId);
        LocalDateTime now = LocalDateTime.now();
        if (setting == null) {
            setting = new UserGroupSetting();
            setting.setUserId(currentUserId);
            setting.setGroupId(groupId);
            setting.setCreatedTime(now);
        }
        setting.setRemark(trimToNull(remark));
        setting.setUpdatedTime(now);
        if (setting.getId() == null) settingMapper.insert(setting); else settingMapper.updateById(setting);
        return response(group, member, setting);
    }

    @Transactional
    public GroupMemberResponse updateRole(long groupId, long memberUserId, int role) {
        long currentUserId = currentUserId();
        requireActiveGroupForUpdate(groupId);
        GroupMember actor = requireMember(groupId, currentUserId);
        if (actor.getRole() != ROLE_OWNER) {
            throw new BusinessException("GROUP_OWNER_REQUIRED", "只有群主可以设置管理员", HttpStatus.FORBIDDEN);
        }
        GroupMember target = memberMapper.selectForUpdate(groupId, memberUserId);
        if (target == null) throw notMember();
        if (target.getRole() == ROLE_OWNER) {
            throw new BusinessException("GROUP_OWNER_ROLE_IMMUTABLE", "不能修改群主角色", HttpStatus.CONFLICT);
        }
        target.setRole(role);
        target.setUpdatedTime(LocalDateTime.now());
        memberMapper.updateById(target);
        return GroupMemberResponse.from(target, userMapper.selectById(memberUserId));
    }

    @Transactional
    public GroupJoinRequestResponse createJoinRequest(long groupId, String message) {
        long currentUserId = currentUserId();
        Group group = requireActiveGroupForUpdate(groupId);
        if (findMember(groupId, currentUserId) != null) {
            throw new BusinessException("ALREADY_GROUP_MEMBER", "你已经是群成员", HttpStatus.CONFLICT);
        }
        if (requestMapper.selectCount(new LambdaQueryWrapper<GroupJoinRequest>()
                .eq(GroupJoinRequest::getGroupId, groupId)
                .eq(GroupJoinRequest::getUserId, currentUserId)
                .eq(GroupJoinRequest::getStatus, REQUEST_PENDING)) > 0) {
            throw new BusinessException("GROUP_JOIN_REQUEST_EXISTS", "已有待处理的入群申请", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        GroupJoinRequest request = new GroupJoinRequest();
        request.setGroupId(groupId);
        request.setUserId(currentUserId);
        request.setMessage(trimToNull(message));
        request.setStatus(REQUEST_PENDING);
        request.setCreatedTime(now);
        request.setUpdatedTime(now);
        requestMapper.insert(request);
        List<Long> managers = managerIds(groupId);
        reliableEventService.append(EVENT_GROUP_JOIN_REQUEST, request.getId(), managers, now);
        return GroupJoinRequestResponse.from(request, group, userMapper.selectById(currentUserId));
    }

    public List<GroupJoinRequestResponse> listJoinRequests() {
        long currentUserId = currentUserId();
        Set<Long> managedGroups = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getUserId, currentUserId)
                        .in(GroupMember::getRole, ROLE_ADMIN, ROLE_OWNER))
                .stream().map(GroupMember::getGroupId).collect(Collectors.toSet());
        LambdaQueryWrapper<GroupJoinRequest> query = new LambdaQueryWrapper<GroupJoinRequest>()
                .eq(GroupJoinRequest::getUserId, currentUserId);
        if (!managedGroups.isEmpty()) {
            query.or(wrapper -> wrapper.in(GroupJoinRequest::getGroupId, managedGroups));
        }
        List<GroupJoinRequest> requests = requestMapper.selectList(query.orderByDesc(GroupJoinRequest::getCreatedTime));
        return mapJoinRequests(requests);
    }

    @Transactional
    public GroupJoinRequestResponse reviewJoinRequest(long requestId, boolean accept) {
        long currentUserId = currentUserId();
        GroupJoinRequest request = requestMapper.selectByIdForUpdate(requestId);
        if (request == null) {
            throw new BusinessException("GROUP_JOIN_REQUEST_NOT_FOUND", "入群申请不存在", HttpStatus.NOT_FOUND);
        }
        Group group = requireActiveGroupForUpdate(request.getGroupId());
        GroupMember reviewer = requireMember(request.getGroupId(), currentUserId);
        requireManager(reviewer);
        if (request.getStatus() != REQUEST_PENDING) {
            throw new BusinessException("GROUP_JOIN_REQUEST_HANDLED", "入群申请已经处理", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(accept ? REQUEST_ACCEPTED : REQUEST_REJECTED);
        request.setReviewerId(currentUserId);
        request.setUpdatedTime(now);
        requestMapper.updateById(request);
        if (accept && findMember(request.getGroupId(), request.getUserId()) == null) {
            insertMember(request.getGroupId(), request.getUserId(), ROLE_MEMBER, now);
        }
        reliableEventService.append(
                EVENT_GROUP_JOIN_REQUEST_HANDLED,
                request.getId(),
                List.of(request.getUserId(), currentUserId),
                now);
        return GroupJoinRequestResponse.from(request, group, userMapper.selectById(request.getUserId()));
    }

    @Transactional
    public void leaveGroup(long groupId) {
        long currentUserId = currentUserId();
        requireActiveGroupForUpdate(groupId);
        GroupMember member = memberMapper.selectForUpdate(groupId, currentUserId);
        if (member == null) throw notMember();
        if (member.getRole() == ROLE_OWNER) {
            throw new BusinessException("GROUP_OWNER_CANNOT_LEAVE", "群主需要先解散群聊", HttpStatus.CONFLICT);
        }
        removeMember(member, currentUserId, currentUserId, LocalDateTime.now());
    }

    @Transactional
    public void kickMember(long groupId, long memberUserId) {
        long currentUserId = currentUserId();
        requireActiveGroupForUpdate(groupId);
        GroupMember actor = requireMember(groupId, currentUserId);
        requireManager(actor);
        GroupMember target = memberMapper.selectForUpdate(groupId, memberUserId);
        if (target == null) throw notMember();
        if (target.getRole() == ROLE_OWNER || currentUserId == memberUserId) {
            throw new BusinessException("GROUP_MEMBER_CANNOT_KICK", "不能移除该群成员", HttpStatus.CONFLICT);
        }
        if (actor.getRole() == ROLE_ADMIN && target.getRole() >= ROLE_ADMIN) {
            throw new BusinessException("GROUP_PERMISSION_DENIED", "管理员不能移除群主或其他管理员", HttpStatus.FORBIDDEN);
        }
        removeMember(target, memberUserId, currentUserId, LocalDateTime.now());
    }

    @Transactional
    public void dissolveGroup(long groupId) {
        long currentUserId = currentUserId();
        Group group = requireActiveGroupForUpdate(groupId);
        GroupMember owner = requireMember(groupId, currentUserId);
        if (owner.getRole() != ROLE_OWNER || group.getOwnerId() != currentUserId) {
            throw new BusinessException("GROUP_OWNER_REQUIRED", "只有群主可以解散群聊", HttpStatus.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        group.setStatus(0);
        group.setUpdatedTime(now);
        groupMapper.updateById(group);
        List<GroupMember> members = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId));
        for (GroupMember member : members) {
            insertLeaveRecord(groupId, member.getUserId(), currentUserId, now);
        }
        memberMapper.delete(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        settingMapper.delete(new LambdaQueryWrapper<UserGroupSetting>().eq(UserGroupSetting::getGroupId, groupId));
    }

    private List<GroupJoinRequestResponse> mapJoinRequests(List<GroupJoinRequest> requests) {
        if (requests.isEmpty()) return List.of();
        Map<Long, Group> groups = groupMapper.selectByIds(requests.stream()
                        .map(GroupJoinRequest::getGroupId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Group::getId, Function.identity()));
        Map<Long, User> users = userMapper.selectByIds(requests.stream()
                        .map(GroupJoinRequest::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return requests.stream()
                .filter(request -> groups.containsKey(request.getGroupId()) && users.containsKey(request.getUserId()))
                .map(request -> GroupJoinRequestResponse.from(
                        request, groups.get(request.getGroupId()), users.get(request.getUserId())))
                .toList();
    }

    private GroupResponse response(Group group, GroupMember member, UserGroupSetting setting) {
        User owner = userMapper.selectById(group.getOwnerId());
        long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, group.getId()));
        return GroupResponse.from(
                group,
                member,
                setting,
                owner == null ? "群主" : owner.getNickname(),
                Math.toIntExact(memberCount));
    }

    private void validateInitialMembersAreFriends(long ownerId, Set<Long> memberIds) {
        for (Long memberId : memberIds) {
            if (friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                    .eq(Friend::getUserId, ownerId)
                    .eq(Friend::getFriendId, memberId)) == 0) {
                throw new BusinessException("GROUP_INITIAL_MEMBER_NOT_FRIEND", "只能邀请好友加入新群聊", HttpStatus.CONFLICT);
            }
        }
    }

    private List<Long> managerIds(long groupId) {
        List<Long> result = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .in(GroupMember::getRole, ROLE_ADMIN, ROLE_OWNER))
                .stream().map(GroupMember::getUserId).toList();
        if (result.isEmpty()) {
            throw new BusinessException("GROUP_HAS_NO_MANAGER", "群聊没有可处理申请的管理员", HttpStatus.CONFLICT);
        }
        return result;
    }

    private GroupMember insertMember(long groupId, long userId, int role, LocalDateTime now) {
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role);
        member.setCreatedTime(now);
        member.setUpdatedTime(now);
        memberMapper.insert(member);
        return member;
    }

    private void removeMember(
            GroupMember member,
            long leavingUserId,
            long operatorId,
            LocalDateTime now) {
        memberMapper.deleteById(member.getId());
        settingMapper.delete(new LambdaQueryWrapper<UserGroupSetting>()
                .eq(UserGroupSetting::getUserId, leavingUserId)
                .eq(UserGroupSetting::getGroupId, member.getGroupId()));
        insertLeaveRecord(member.getGroupId(), leavingUserId, operatorId, now);
    }

    private void insertLeaveRecord(long groupId, long userId, long operatorId, LocalDateTime now) {
        GroupLeaveRecord record = new GroupLeaveRecord();
        record.setGroupId(groupId);
        record.setUserId(userId);
        record.setOperatorId(operatorId);
        record.setCreatedTime(now);
        record.setUpdatedTime(now);
        leaveRecordMapper.insert(record);
    }

    private Map<Long, UserGroupSetting> settingsByGroup(long userId, Collection<Long> groupIds) {
        if (groupIds.isEmpty()) return Map.of();
        return settingMapper.selectList(new LambdaQueryWrapper<UserGroupSetting>()
                        .eq(UserGroupSetting::getUserId, userId)
                        .in(UserGroupSetting::getGroupId, groupIds))
                .stream().collect(Collectors.toMap(UserGroupSetting::getGroupId, Function.identity()));
    }

    private UserGroupSetting findSetting(long userId, long groupId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<UserGroupSetting>()
                .eq(UserGroupSetting::getUserId, userId)
                .eq(UserGroupSetting::getGroupId, groupId)
                .last("LIMIT 1"));
    }

    private Group requireActiveGroup(long groupId) {
        Group group = groupMapper.selectById(groupId);
        return validateActiveGroup(group);
    }

    private Group requireActiveGroupForUpdate(long groupId) {
        Group group = groupMapper.selectByIdForUpdate(groupId);
        return validateActiveGroup(group);
    }

    private Group validateActiveGroup(Group group) {
        if (group == null || group.getStatus() == null || group.getStatus() != GROUP_ACTIVE) {
            throw new BusinessException("GROUP_NOT_FOUND", "群聊不存在或已解散", HttpStatus.NOT_FOUND);
        }
        return group;
    }

    private GroupMember findMember(long groupId, long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .last("LIMIT 1"));
    }

    private GroupMember requireMember(long groupId, long userId) {
        GroupMember member = findMember(groupId, userId);
        if (member == null) throw notMember();
        return member;
    }

    private void requireManager(GroupMember member) {
        if (member.getRole() < ROLE_ADMIN) {
            throw new BusinessException("GROUP_PERMISSION_DENIED", "需要群主或管理员权限", HttpStatus.FORBIDDEN);
        }
    }

    private BusinessException notMember() {
        return new BusinessException("GROUP_NOT_MEMBER", "你已不在该群聊中", HttpStatus.FORBIDDEN);
    }

    private long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    private Set<Long> parseIds(List<String> values, String code, String message) {
        if (values == null || values.isEmpty()) return new LinkedHashSet<>();
        Set<Long> result = new LinkedHashSet<>();
        for (String value : values) result.add(parseId(value, code, message));
        return result;
    }

    private long parseId(String value, String code, String message) {
        try {
            if (value == null || value.isBlank()) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
    }

    private List<Long> withOwner(long ownerId, Set<Long> memberIds) {
        List<Long> ids = new ArrayList<>(memberIds.size() + 1);
        ids.add(ownerId);
        ids.addAll(memberIds);
        return ids;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
