package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.service.GroupMemberService;

@Service
public class GroupMemberServiceImpl
        extends ServiceImpl<GroupMemberMapper, GroupMember>
        implements GroupMemberService {
}
