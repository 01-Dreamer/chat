package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.service.FriendService;

@Service
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {
}
