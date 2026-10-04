package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.RedPacket;
import top.zxylearn.chatserver.mapper.RedPacketMapper;
import top.zxylearn.chatserver.service.RedPacketService;

@Service
public class RedPacketServiceImpl
        extends ServiceImpl<RedPacketMapper, RedPacket>
        implements RedPacketService {
}
