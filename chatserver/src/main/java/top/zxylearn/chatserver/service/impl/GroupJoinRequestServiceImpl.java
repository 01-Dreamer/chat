package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.GroupJoinRequest;
import top.zxylearn.chatserver.mapper.GroupJoinRequestMapper;
import top.zxylearn.chatserver.service.GroupJoinRequestService;

@Service
public class GroupJoinRequestServiceImpl
        extends ServiceImpl<GroupJoinRequestMapper, GroupJoinRequest>
        implements GroupJoinRequestService {
}
