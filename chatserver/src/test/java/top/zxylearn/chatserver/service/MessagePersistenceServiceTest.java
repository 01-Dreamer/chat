package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.Message;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.CallRecordMapper;
import top.zxylearn.chatserver.mapper.FileResourceAccessMapper;
import top.zxylearn.chatserver.mapper.FileResourceMapper;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.MessageMapper;
import top.zxylearn.chatserver.mapper.RedPacketMapper;
import top.zxylearn.chatserver.mapper.UserMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagePersistenceServiceTest {

    private MessageMapper messageMapper;
    private GroupMemberMapper memberMapper;
    private ReliableEventService reliableEventService;
    private ChatAccessCache chatAccessCache;
    private UserMapper userMapper;
    private MessagePersistenceService service;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        memberMapper = mock(GroupMemberMapper.class);
        reliableEventService = mock(ReliableEventService.class);
        chatAccessCache = mock(ChatAccessCache.class);
        userMapper = mock(UserMapper.class);
        service = new MessagePersistenceService(
                messageMapper,
                mock(FriendMapper.class),
                mock(GroupMapper.class),
                memberMapper,
                mock(FileResourceMapper.class),
                mock(RedPacketMapper.class),
                mock(CallRecordMapper.class),
                mock(FileResourceAccessMapper.class),
                userMapper,
                reliableEventService,
                chatAccessCache,
                mock(IdentifierGenerator.class));
    }

    @Test
    void senderCannotRecallOwnMessageAfterFiveMinutes() {
        Message message = directMessage(LocalDateTime.now().minusMinutes(6));
        when(messageMapper.selectByIdForUpdate(100L)).thenReturn(message);
        when(chatAccessCache.getDirectAccess(1L, 2L)).thenReturn(Optional.of(true));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.recall(1L, 100L));

        assertEquals("MESSAGE_RECALL_EXPIRED", exception.getCode());
        assertEquals("消息发送超过5分钟，无法撤回", exception.getMessage());
        verify(messageMapper, never()).updateById(any(Message.class));
    }

    @Test
    void senderCanRecallOwnMessageWithinFiveMinutes() {
        Message message = directMessage(LocalDateTime.now().minusMinutes(4));
        when(messageMapper.selectByIdForUpdate(100L)).thenReturn(message);
        when(chatAccessCache.getDirectAccess(1L, 2L)).thenReturn(Optional.of(true));
        stubRecallEvent();

        MessagePersistenceService.PersistedMessage result = service.recall(1L, 100L);

        assertEquals(1, result.message().status());
        assertEquals("1", result.message().recallOperatorId());
        verify(messageMapper).updateById(message);
    }

    @Test
    void administratorCanRecallMemberMessageWithoutTimeLimit() {
        Message message = groupMessage(LocalDateTime.now().minusDays(2));
        GroupMember administrator = member(2L, 1);
        GroupMember sender = member(1L, 0);
        when(messageMapper.selectByIdForUpdate(100L)).thenReturn(message);
        when(chatAccessCache.getGroupRecipients(99L)).thenReturn(Optional.of(List.of(1L, 2L)));
        when(memberMapper.selectOne(any())).thenReturn(administrator, sender);
        when(userMapper.selectById(2L)).thenReturn(user(2L, "管理员A"));
        stubRecallEvent();

        MessagePersistenceService.PersistedMessage result = service.recall(2L, 100L);

        assertEquals(1, result.message().status());
        assertEquals("2", result.message().recallOperatorId());
        assertEquals("管理员A", result.message().recallOperatorName());
        verify(messageMapper).updateById(message);
    }

    private void stubRecallEvent() {
        when(reliableEventService.append(anyInt(), anyLong(), any(), any(LocalDateTime.class)))
                .thenReturn(new ReliableEventService.EventDelivery(new Event(), Map.of(1L, 1L, 2L, 1L)));
    }

    private Message directMessage(LocalDateTime createdTime) {
        Message message = baseMessage(createdTime);
        message.setChatType(0);
        message.setTargetId(2L);
        message.setChatKey("P:1:2");
        return message;
    }

    private Message groupMessage(LocalDateTime createdTime) {
        Message message = baseMessage(createdTime);
        message.setChatType(1);
        message.setTargetId(99L);
        message.setChatKey("G:99");
        return message;
    }

    private Message baseMessage(LocalDateTime createdTime) {
        Message message = new Message();
        message.setId(100L);
        message.setClientMessageId("00000000-0000-0000-0000-000000000001");
        message.setSenderId(1L);
        message.setMessageType(0);
        message.setContent("hello");
        message.setStatus(0);
        message.setCreatedTime(createdTime);
        message.setUpdatedTime(createdTime);
        return message;
    }

    private GroupMember member(long userId, int role) {
        GroupMember member = new GroupMember();
        member.setGroupId(99L);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private User user(long userId, String nickname) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        return user;
    }
}
