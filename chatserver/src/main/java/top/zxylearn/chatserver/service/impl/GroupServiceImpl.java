package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.service.GroupService;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {
}
