package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.FriendDeleteRecord;
import top.zxylearn.chatserver.mapper.FriendDeleteRecordMapper;
import top.zxylearn.chatserver.service.FriendDeleteRecordService;

@Service
public class FriendDeleteRecordServiceImpl
        extends ServiceImpl<FriendDeleteRecordMapper, FriendDeleteRecord>
        implements FriendDeleteRecordService {
}
