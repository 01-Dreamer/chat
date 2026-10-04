package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.Notification;
import top.zxylearn.chatserver.mapper.NotificationMapper;
import top.zxylearn.chatserver.service.NotificationService;

@Service
public class NotificationServiceImpl
        extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {
}
