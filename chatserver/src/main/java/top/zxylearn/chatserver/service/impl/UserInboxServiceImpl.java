package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.UserInbox;
import top.zxylearn.chatserver.mapper.UserInboxMapper;
import top.zxylearn.chatserver.service.UserInboxService;

@Service
public class UserInboxServiceImpl
        extends ServiceImpl<UserInboxMapper, UserInbox>
        implements UserInboxService {
}
