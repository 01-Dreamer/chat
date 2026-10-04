package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.RedPacketReceive;
import top.zxylearn.chatserver.mapper.RedPacketReceiveMapper;
import top.zxylearn.chatserver.service.RedPacketReceiveService;

@Service
public class RedPacketReceiveServiceImpl
        extends ServiceImpl<RedPacketReceiveMapper, RedPacketReceive>
        implements RedPacketReceiveService {
}
