package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.CallRecord;
import top.zxylearn.chatserver.mapper.CallRecordMapper;
import top.zxylearn.chatserver.service.CallRecordService;

@Service
public class CallRecordServiceImpl
        extends ServiceImpl<CallRecordMapper, CallRecord>
        implements CallRecordService {
}
