package top.zxylearn.chatserver.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.dto.friend.CreateFriendRequest;
import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.entity.FriendAddRequest;
import top.zxylearn.chatserver.entity.FriendDeleteRecord;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.FriendAddRequestMapper;
import top.zxylearn.chatserver.mapper.FriendDeleteRecordMapper;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.vo.FriendRequestResponse;
import top.zxylearn.chatserver.vo.FriendResponse;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FriendRelationshipService {

    private static final int REQUEST_PENDING = 0;
    private static final int REQUEST_ACCEPTED = 1;
    private static final int REQUEST_REJECTED = 2;
    private static final int EVENT_FRIEND_REQUEST = 2;
    private static final int EVENT_FRIEND_REQUEST_HANDLED = 3;

    private final UserService userService;
    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final FriendAddRequestMapper requestMapper;
    private final FriendDeleteRecordMapper deleteRecordMapper;
    private final ReliableEventService reliableEventService;
    private final ChatAccessCache chatAccessCache;

    public FriendRelationshipService(
            UserService userService,
            UserMapper userMapper,
            FriendMapper friendMapper,
            FriendAddRequestMapper requestMapper,
            FriendDeleteRecordMapper deleteRecordMapper,
            ReliableEventService reliableEventService,
            ChatAccessCache chatAccessCache) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.friendMapper = friendMapper;
        this.requestMapper = requestMapper;
        this.deleteRecordMapper = deleteRecordMapper;
        this.reliableEventService = reliableEventService;
        this.chatAccessCache = chatAccessCache;
    }

    public List<FriendResponse> listFriends() {
        long currentUserId = StpUtil.getLoginIdAsLong();
        List<Friend> relations = friendMapper.selectList(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, currentUserId)
                .orderByDesc(Friend::getUpdatedTime));
        if (relations.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectByIds(relations.stream()
                        .map(Friend::getFriendId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return relations.stream()
                .filter(relation -> users.containsKey(relation.getFriendId()))
                .map(relation -> FriendResponse.from(relation, users.get(relation.getFriendId())))
                .toList();
    }

    public List<FriendRequestResponse> listRequests() {
        long currentUserId = StpUtil.getLoginIdAsLong();
        List<FriendAddRequest> requests = requestMapper.selectList(
                new LambdaQueryWrapper<FriendAddRequest>()
                        .and(query -> query.eq(FriendAddRequest::getSenderId, currentUserId)
                                .or()
                                .eq(FriendAddRequest::getReceiverId, currentUserId))
                        .orderByDesc(FriendAddRequest::getCreatedTime));
        if (requests.isEmpty()) return List.of();
        Collection<Long> otherUserIds = requests.stream()
                .map(request -> request.getSenderId() == currentUserId
                        ? request.getReceiverId()
                        : request.getSenderId())
                .collect(Collectors.toSet());
        Map<Long, User> users = userMapper.selectByIds(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return requests.stream()
                .map(request -> {
                    long otherUserId = request.getSenderId() == currentUserId
                            ? request.getReceiverId()
                            : request.getSenderId();
                    return FriendRequestResponse.from(request, users.get(otherUserId), currentUserId);
                })
                .toList();
    }

    @Transactional
    public FriendRequestResponse createRequest(CreateFriendRequest input) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        User receiver = userService.findByUsername(input.username().trim());
        if (receiver == null || receiver.getStatus() == null || receiver.getStatus() != 1) {
            throw new BusinessException("USER_NOT_FOUND", "没有找到该用户", HttpStatus.NOT_FOUND);
        }
        if (receiver.getId() == currentUserId) {
            throw new BusinessException("CANNOT_ADD_SELF", "不能添加自己为好友", HttpStatus.BAD_REQUEST);
        }
        reliableEventService.lockUsers(List.of(currentUserId, receiver.getId()));
        if (friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, currentUserId)
                .eq(Friend::getFriendId, receiver.getId())) > 0) {
            throw new BusinessException("ALREADY_FRIEND", "对方已经是你的好友", HttpStatus.CONFLICT);
        }
        if (requestMapper.selectCount(new LambdaQueryWrapper<FriendAddRequest>()
                .eq(FriendAddRequest::getStatus, REQUEST_PENDING)
                .and(query -> query
                        .nested(pair -> pair.eq(FriendAddRequest::getSenderId, currentUserId)
                                .eq(FriendAddRequest::getReceiverId, receiver.getId()))
                        .or(pair -> pair.eq(FriendAddRequest::getSenderId, receiver.getId())
                                .eq(FriendAddRequest::getReceiverId, currentUserId)))) > 0) {
            throw new BusinessException("FRIEND_REQUEST_EXISTS", "双方已有待处理的好友申请", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        FriendAddRequest request = new FriendAddRequest();
        request.setSenderId(currentUserId);
        request.setReceiverId(receiver.getId());
        request.setMessage(trimToNull(input.message()));
        request.setStatus(REQUEST_PENDING);
        request.setCreatedTime(now);
        request.setUpdatedTime(now);
        requestMapper.insert(request);
        reliableEventService.append(EVENT_FRIEND_REQUEST, request.getId(), List.of(receiver.getId()), now);
        return FriendRequestResponse.from(request, receiver, currentUserId);
    }

    @Transactional
    public FriendRequestResponse acceptRequest(long requestId) {
        return handleRequest(requestId, REQUEST_ACCEPTED);
    }

    @Transactional
    public FriendRequestResponse rejectRequest(long requestId) {
        return handleRequest(requestId, REQUEST_REJECTED);
    }

    @Transactional
    public FriendResponse updateRemark(long friendUserId, String remark) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        Friend relation = findRelation(currentUserId, friendUserId);
        LocalDateTime now = LocalDateTime.now();
        relation.setRemark(trimToNull(remark));
        relation.setUpdatedTime(now);
        friendMapper.updateById(relation);
        User friendUser = userMapper.selectById(friendUserId);
        return FriendResponse.from(relation, friendUser);
    }

    @Transactional
    public void deleteFriend(long friendUserId) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        findRelation(currentUserId, friendUserId);
        friendMapper.delete(new LambdaQueryWrapper<Friend>()
                .and(query -> query
                        .nested(pair -> pair.eq(Friend::getUserId, currentUserId)
                                .eq(Friend::getFriendId, friendUserId))
                        .or(pair -> pair.eq(Friend::getUserId, friendUserId)
                                .eq(Friend::getFriendId, currentUserId))));
        FriendDeleteRecord record = new FriendDeleteRecord();
        record.setOperatorId(currentUserId);
        record.setFriendId(friendUserId);
        LocalDateTime now = LocalDateTime.now();
        record.setCreatedTime(now);
        record.setUpdatedTime(now);
        deleteRecordMapper.insert(record);
        chatAccessCache.evictDirect(currentUserId, friendUserId);
    }

    private FriendRequestResponse handleRequest(long requestId, int resultStatus) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        FriendAddRequest request = requestMapper.selectByIdForUpdate(requestId);
        if (request == null) {
            throw new BusinessException("FRIEND_REQUEST_NOT_FOUND", "好友申请不存在", HttpStatus.NOT_FOUND);
        }
        if (request.getReceiverId() != currentUserId) {
            throw new BusinessException("FORBIDDEN", "无权处理该好友申请", HttpStatus.FORBIDDEN);
        }
        if (request.getStatus() != REQUEST_PENDING) {
            throw new BusinessException("FRIEND_REQUEST_HANDLED", "好友申请已经处理", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(resultStatus);
        request.setUpdatedTime(now);
        requestMapper.updateById(request);
        if (resultStatus == REQUEST_ACCEPTED) {
            createFriendRelationIfMissing(request.getSenderId(), request.getReceiverId(), now);
            createFriendRelationIfMissing(request.getReceiverId(), request.getSenderId(), now);
            chatAccessCache.evictDirect(request.getSenderId(), request.getReceiverId());
        }
        reliableEventService.append(
                EVENT_FRIEND_REQUEST_HANDLED,
                request.getId(),
                List.of(request.getSenderId(), request.getReceiverId()),
                now);
        User sender = userMapper.selectById(request.getSenderId());
        return FriendRequestResponse.from(request, sender, currentUserId);
    }

    private void createFriendRelationIfMissing(long userId, long friendId, LocalDateTime now) {
        if (friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)) > 0) return;
        Friend relation = new Friend();
        relation.setUserId(userId);
        relation.setFriendId(friendId);
        relation.setCreatedTime(now);
        relation.setUpdatedTime(now);
        friendMapper.insert(relation);
    }

    private Friend findRelation(long currentUserId, long friendUserId) {
        Friend relation = friendMapper.selectOne(new LambdaQueryWrapper<Friend>()
                .eq(Friend::getUserId, currentUserId)
                .eq(Friend::getFriendId, friendUserId)
                .last("LIMIT 1"));
        if (relation == null) {
            throw new BusinessException("NOT_FRIEND", "当前已经不是好友关系", HttpStatus.CONFLICT);
        }
        return relation;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
