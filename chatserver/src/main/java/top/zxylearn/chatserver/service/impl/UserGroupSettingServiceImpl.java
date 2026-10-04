package top.zxylearn.chatserver.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.zxylearn.chatserver.entity.UserGroupSetting;
import top.zxylearn.chatserver.mapper.UserGroupSettingMapper;
import top.zxylearn.chatserver.service.UserGroupSettingService;

@Service
public class UserGroupSettingServiceImpl
        extends ServiceImpl<UserGroupSettingMapper, UserGroupSetting>
        implements UserGroupSettingService {
}
