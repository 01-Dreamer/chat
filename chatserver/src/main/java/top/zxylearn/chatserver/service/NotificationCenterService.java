package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.entity.Notification;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.NotificationMapper;
import top.zxylearn.chatserver.vo.NotificationResponse;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationCenterService {
    private final NotificationMapper mapper;
    public NotificationCenterService(NotificationMapper mapper) { this.mapper = mapper; }

    public List<NotificationResponse> list(long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return mapper.selectList(new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedTime)
                        .last("LIMIT " + safeLimit))
                .stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse markRead(long userId, long id) {
        Notification value = mapper.selectById(id);
        if (value == null || value.getUserId() != userId) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在", HttpStatus.NOT_FOUND);
        }
        if (value.getIsRead() == 0) {
            LocalDateTime now = LocalDateTime.now();
            value.setIsRead(1);
            value.setReadTime(now);
            value.setUpdatedTime(now);
            mapper.updateById(value);
        }
        return NotificationResponse.from(value);
    }
}
