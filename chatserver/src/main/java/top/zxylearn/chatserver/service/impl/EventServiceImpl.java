package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.Event;
import top.zxylearn.chatserver.mapper.EventMapper;
import top.zxylearn.chatserver.service.EventService;

@Service
public class EventServiceImpl extends ServiceImpl<EventMapper, Event> implements EventService {
}
