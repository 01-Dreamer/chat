package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.GroupLeaveRecord;
import top.zxylearn.chatserver.mapper.GroupLeaveRecordMapper;
import top.zxylearn.chatserver.service.GroupLeaveRecordService;

@Service
public class GroupLeaveRecordServiceImpl
        extends ServiceImpl<GroupLeaveRecordMapper, GroupLeaveRecord>
        implements GroupLeaveRecordService {
}
