package top.zxylearn.chatserver.service;

import org.junit.jupiter.api.Test;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.entity.Message;
import top.zxylearn.chatserver.entity.User;
import top.zxylearn.chatserver.entity.UserInbox;
import top.zxylearn.chatserver.mapper.EventMapper;
import top.zxylearn.chatserver.mapper.FriendAddRequestMapper;
import top.zxylearn.chatserver.mapper.GroupJoinRequestMapper;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.MessageMapper;
import top.zxylearn.chatserver.mapper.UserInboxMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.vo.InboxSyncResponse;
import top.zxylearn.chatserver.vo.MessageResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboxSyncServiceTest {

    @Test
    void normalMessageWithoutRecallOperatorCanBeSynchronized() {
        UserInboxMapper inboxMapper = mock(UserInboxMapper.class);
        EventMapper eventMapper = mock(EventMapper.class);
        MessageMapper messageMapper = mock(MessageMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        LocalDateTime now = LocalDateTime.now();

        UserInbox inbox = new UserInbox();
        inbox.setId(10L);
        inbox.setUserId(1L);
        inbox.setEventId(20L);
        inbox.setSequence(1L);
        inbox.setCreatedTime(now);

        Event event = new Event();
        event.setId(20L);
        event.setEventType(0);
        event.setReferenceId(30L);
        event.setCreatedTime(now);

        Message message = new Message();
        message.setId(30L);
        message.setClientMessageId("client-message-id");
        message.setChatKey("P:1:2");
        message.setSenderId(2L);
        message.setChatType(0);
        message.setTargetId(1L);
        message.setMessageType(0);
        message.setContent("hello");
        message.setStatus(0);
        message.setCreatedTime(now);
        message.setUpdatedTime(now);

        User sender = new User();
        sender.setId(2L);
        sender.setUsername("sender");
        sender.setNickname("Sender");
        sender.setUpdatedTime(now);

        when(inboxMapper.selectList(any())).thenReturn(List.of(inbox));
        when(eventMapper.selectBatchIds(any())).thenReturn(List.of(event));
        when(messageMapper.selectBatchIds(any())).thenReturn(List.of(message));
        when(userMapper.selectById(2L)).thenReturn(sender);

        InboxSyncService service = new InboxSyncService(
                inboxMapper,
                eventMapper,
                messageMapper,
                mock(FriendAddRequestMapper.class),
                mock(GroupJoinRequestMapper.class),
                mock(GroupMapper.class),
                userMapper,
                mock(GroupMemberMapper.class));

        InboxSyncResponse response = service.sync(1L, 0L, 100);

        assertEquals(1, response.items().size());
        assertTrue(response.items().getFirst().data() instanceof MessageResponse);
        MessageResponse data = (MessageResponse) response.items().getFirst().data();
        assertEquals("hello", data.content());
        assertNull(data.recallOperatorId());
        assertNull(data.recallOperatorName());
    }
}
