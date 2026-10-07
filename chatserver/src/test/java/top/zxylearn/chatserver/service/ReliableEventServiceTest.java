package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.mapper.EventMapper;
import top.zxylearn.chatserver.mapper.UserInboxMapper;
import top.zxylearn.chatserver.mapper.UserMapper;
import top.zxylearn.chatserver.mq.RealtimeEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReliableEventServiceTest {

    @Test
    void repeatedDirectoryChangesUseDifferentUniqueReferences() {
        EventMapper eventMapper = mock(EventMapper.class);
        UserInboxMapper inboxMapper = mock(UserInboxMapper.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(identifierGenerator.nextId(any())).thenReturn(101L, 201L, 102L, 202L);
        when(inboxMapper.selectMaxSequences(any())).thenReturn(List.of());

        ReliableEventService service = new ReliableEventService(
                eventMapper,
                inboxMapper,
                mock(UserMapper.class),
                identifierGenerator,
                mock(RealtimeEventPublisher.class));
        LocalDateTime now = LocalDateTime.now();

        service.appendForLockedUsers(6, 77L, List.of(1L), now);
        service.appendForLockedUsers(6, 77L, List.of(1L), now.plusSeconds(1));

        ArgumentCaptor<Event> events = ArgumentCaptor.forClass(Event.class);
        verify(eventMapper, times(2)).insert(events.capture());
        Event first = events.getAllValues().get(0);
        Event second = events.getAllValues().get(1);
        assertEquals(first.getId(), first.getReferenceId());
        assertEquals(second.getId(), second.getReferenceId());
        assertNotEquals(first.getReferenceId(), second.getReferenceId());
    }
}
