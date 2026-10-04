package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.spring.service.IService;
import top.zxylearn.chatserver.entity.User;

public interface UserService extends IService<User> {

    User register(String username, String password, String nickname);

    User findByUsername(String username);
}
