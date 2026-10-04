package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.FriendAddRequest;
import top.zxylearn.chatserver.mapper.FriendAddRequestMapper;
import top.zxylearn.chatserver.service.FriendAddRequestService;

@Service
public class FriendAddRequestServiceImpl
        extends ServiceImpl<FriendAddRequestMapper, FriendAddRequest>
        implements FriendAddRequestService {
}
